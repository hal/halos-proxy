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
import org.wildfly.halos.proxy.dmr.RunningMode;
import org.wildfly.halos.proxy.dmr.ServerState;
import org.wildfly.halos.proxy.dmr.SuspendState;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;

import de.skuzzle.semantic.Version;

import static java.util.Collections.synchronizedMap;
import static org.wildfly.halos.proxy.InstanceModification.Modification.ADDED;
import static org.wildfly.halos.proxy.InstanceModification.Modification.REMOVED;
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
class InstanceRepository {

    private static final String REMOTE_HTTP = "remote+http";

    @Inject
    ManagementInterfaceRepository managementInterfaceRepository;

    private final Map<ManagementInterface, ModelControllerClient> clients;
    private final Map<ManagementInterface, Instance> instances;
    private final UnicastProcessor<InstanceModification> processor;
    private final Multi<InstanceModification> modifications;

    InstanceRepository() {
        this.clients = synchronizedMap(new HashMap<>());
        this.instances = synchronizedMap(new HashMap<>());
        this.processor = UnicastProcessor.create();
        this.modifications = processor.broadcast().toAllSubscribers().onOverflow().dropPreviousItems();
    }

    void lookup() {
        Set<ManagementInterface> managementInterfaces = managementInterfaceRepository.lookup();
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(clients.keySet(), managementInterfaces);

        Log.debugf("Added management interfaces: %s", diff.added());
        for (ManagementInterface managementInterface : diff.added()) {
            try {
                ModelControllerClient client = modelControllerClient(managementInterface);
                Instance instance = readWildFlyInstance(client, managementInterface);
                clients.put(managementInterface, client);
                instances.put(managementInterface, instance);
                processor.onNext(new InstanceModification(ADDED, instance));
            } catch (Exception e) {
                Log.errorf("Unable to add new management interface %s: %s", managementInterface, e.getMessage());
            }
        }

        Log.debugf("Removed management interfaces: %s", diff.removed());
        for (ManagementInterface managementInterface : diff.removed()) {
            ModelControllerClient client = clients.remove(managementInterface);
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    Log.errorf("Unable to close client for management interface %s: %s", managementInterface, e.getMessage());
                }
            }
            Instance instance = instances.remove(managementInterface);
            processor.onNext(new InstanceModification(REMOVED, instance));
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

    private Instance readWildFlyInstance(final ModelControllerClient client, final ManagementInterface managementInterface) {
        Operation operation = new Operation.Builder(ResourceAddress.root(), READ_RESOURCE_OPERATION)
                .param(ATTRIBUTES_ONLY, true).param(INCLUDE_RUNTIME, true).build();
        ModelNode modelNode = new Dispatcher(client).execute(operation);

        String serverId = modelNode.get(UUID).asString();
        String serverName = modelNode.get(NAME).asString();
        String productName = modelNode.get(PRODUCT_NAME).asString();
        Version productVersion = Version.parseVersion(makeSemantic(modelNode.get(PRODUCT_VERSION).asString()));
        Version coreVersion = Version.parseVersion(makeSemantic(modelNode.get(RELEASE_VERSION).asString()));
        Version managementVersion = parseManagementVersion(modelNode);
        RunningMode runningMode = ModelNodeHelper.asEnumValue(modelNode, RUNNING_MODE, RunningMode::valueOf,
                RunningMode.UNDEFINED);
        ServerState serverState = ModelNodeHelper.asEnumValue(modelNode, SERVER_STATE, ServerState::valueOf,
                ServerState.UNDEFINED);
        SuspendState suspendState = ModelNodeHelper.asEnumValue(modelNode, SUSPEND_STATE, SuspendState::valueOf,
                SuspendState.UNDEFINED);

        return new Instance(managementInterface.id(), serverId, serverName, productName, productVersion, coreVersion,
                managementVersion, runningMode, serverState, suspendState);
    }

    private String makeSemantic(final String version) {
        return version.replace(".Final", "-Final");
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
        return clients.entrySet().stream().filter(entry -> id.equals(entry.getKey().id())).map(Map.Entry::getValue).findAny()
                .orElse(null);
    }

    Multi<InstanceModification> getModifications() {
        return modifications;
    }
}
