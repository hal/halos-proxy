/*
 *  Copyright 2022 Red Hat
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wildfly.halos.proxy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.halos.proxy.dmr.Dispatcher;
import org.wildfly.halos.proxy.dmr.ModelNodeHelper;
import org.wildfly.halos.proxy.dmr.Operation;
import org.wildfly.halos.proxy.dmr.ResourceAddress;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;

import de.skuzzle.semantic.Version;

import static java.util.Collections.synchronizedMap;
import static java.util.stream.Collectors.toSet;
import static org.wildfly.halos.proxy.Modification.ADDED;
import static org.wildfly.halos.proxy.Modification.REMOVED;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.ATTRIBUTES_ONLY;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.NAME;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.PRODUCT_NAME;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.RELEASE_VERSION;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.RUNNING_MODE;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.SERVER_STATE;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.SUSPEND_STATE;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.UUID;

@ApplicationScoped
class ServerRepository {

    private static final String REMOTE_HTTP = "remote+http";

    @Inject
    ManagementInterfaceRepository managementInterfaceRepository;

    private final Map<String, MIC> micById;
    private final Map<ManagementInterface, MIC> micByManagementInterface;
    private final UnicastProcessor<ServerModification> processor;
    private final Multi<ServerModification> modifications;

    ServerRepository() {
        this.micById = synchronizedMap(new HashMap<>());
        this.micByManagementInterface = synchronizedMap(new HashMap<>());
        this.processor = UnicastProcessor.create();
        this.modifications = processor.broadcast().toAllSubscribers().onOverflow().dropPreviousItems();
    }

    void lookup() {
        Difference<ManagementInterface> difference = new Difference<>(micByManagementInterface.keySet(),
                managementInterfaceRepository.lookup(), ManagementInterface::uid);

        Log.debugf("Added management interfaces: %s", difference.added());
        for (ManagementInterface managementInterface : difference.added()) {
            try {
                ModelControllerClient client = modelControllerClient(managementInterface);
                Server server = readInstance(client, managementInterface);
                add(managementInterface, server, client);
            } catch (Exception e) {
                Log.errorf("Unable to add server for management interface %s: %s", managementInterface, e.getMessage());
            }
        }

        Log.debugf("Removed management interfaces: %s", difference.removed());
        for (ManagementInterface managementInterface : difference.removed()) {
            remove(managementInterface);
        }
    }

    private void add(final ManagementInterface managementInterface, final Server server, final ModelControllerClient client) {
        MIC mic = new MIC(managementInterface, server, client);
        micById.put(managementInterface.uid(), mic);
        micByManagementInterface.put(managementInterface, mic);
        processor.onNext(new ServerModification(ADDED, server));
        Log.infof("Add server %s", server);
    }

    private void remove(final ManagementInterface managementInterface) {
        MIC mic = micById.remove(managementInterface.uid());
        MIC mic2 = micByManagementInterface.remove(managementInterface);
        if (mic != null && mic2 != null) {
            // noinspection resource
            if (mic.client() != null) {
                try {
                    mic.client().close();
                } catch (IOException e) {
                    Log.errorf("Unable to close client for management interface %s: %s", managementInterface, e.getMessage());
                }
            }
            processor.onNext(new ServerModification(REMOVED, mic.server()));
            Log.infof("Remove server %s", mic.server());
        } else if (mic != null || mic2 != null) {
            Log.warnf("Unbalanced registries for management interface %s", managementInterface);
        }
    }

    private ModelControllerClient modelControllerClient(final ManagementInterface managementInterface) {
        return ModelControllerClient.Factory.create(REMOTE_HTTP, managementInterface.hostname(), managementInterface.port(),
                callbacks -> {
                    for (Callback current : callbacks) {
                        if (current instanceof NameCallback ncb) {
                            ncb.setName("admin");
                        } else if (current instanceof PasswordCallback pcb) {
                            pcb.setPassword("admin".toCharArray());
                        } else if (current instanceof RealmCallback rcb) {
                            rcb.setText(rcb.getDefaultText());
                        } else {
                            throw new UnsupportedCallbackException(current);
                        }
                    }
                });
    }

    private Server readInstance(final ModelControllerClient client, final ManagementInterface managementInterface) {
        Operation operation = new Operation.Builder(ResourceAddress.root(), READ_RESOURCE_OPERATION)
                .param(ATTRIBUTES_ONLY, true).param(INCLUDE_RUNTIME, true).build();
        ModelNode modelNode = new Dispatcher(client).execute(operation);

        String serverId = modelNode.get(UUID).asString();
        String serverName = modelNode.get(NAME).asString();
        String productName = modelNode.get(PRODUCT_NAME).asString();
        Version productVersion = parseVersion(managementInterface, PRODUCT_VERSION, modelNode.get(PRODUCT_VERSION).asString());
        Version coreVersion = parseVersion(managementInterface, RELEASE_VERSION, modelNode.get(RELEASE_VERSION).asString());
        Version managementVersion = parseManagementVersion(modelNode);
        RunningMode runningMode = ModelNodeHelper.asEnumValue(modelNode, RUNNING_MODE, RunningMode::valueOf,
                RunningMode.UNDEFINED);
        ServerState serverState = ModelNodeHelper.asEnumValue(modelNode, SERVER_STATE, ServerState::valueOf,
                ServerState.UNDEFINED);
        SuspendState suspendState = ModelNodeHelper.asEnumValue(modelNode, SUSPEND_STATE, SuspendState::valueOf,
                SuspendState.UNDEFINED);

        return new Server(managementInterface.uid(), serverId, serverName, productName, productVersion, coreVersion,
                managementVersion, runningMode, serverState, suspendState);
    }

    private Version parseVersion(final ManagementInterface managementInterface, final String field, final String version) {
        String safeVersion = version.replace(".Final", "");
        try {
            return Version.parseVersion(safeVersion);
        } catch (Exception e) {
            Log.errorf("Unable to parse %s as version for %s, field %s: %s", safeVersion, managementInterface, field,
                    e.getMessage());
            return Version.ZERO;
        }
    }

    private Version parseManagementVersion(final ModelNode modelNode) {
        if (modelNode.hasDefined(MANAGEMENT_MAJOR_VERSION) && modelNode.hasDefined(MANAGEMENT_MINOR_VERSION)
                && modelNode.hasDefined(MANAGEMENT_MICRO_VERSION)) {
            int major = modelNode.get(MANAGEMENT_MAJOR_VERSION).asInt();
            int minor = modelNode.get(MANAGEMENT_MINOR_VERSION).asInt();
            int patch = modelNode.get(MANAGEMENT_MICRO_VERSION).asInt();
            return Version.create(major, minor, patch);
        }
        return Version.ZERO;
    }

    ModelControllerClient getClient(final String id) {
        MIC mic = micById.get(id);
        return mic != null ? mic.client() : null;
    }

    Set<Server> getInstances() {
        return micById.values().stream().map(MIC::server).collect(toSet());
    }

    Multi<ServerModification> getModifications() {
        return modifications;
    }
}
