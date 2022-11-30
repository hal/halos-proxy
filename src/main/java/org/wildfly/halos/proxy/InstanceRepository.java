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

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.halos.proxy.dmr.Dispatcher;
import org.wildfly.halos.proxy.dmr.ModelNodeHelper;
import org.wildfly.halos.proxy.dmr.Operation;
import org.wildfly.halos.proxy.dmr.ResourceAddress;
import org.wildfly.halos.proxy.dmr.RunningMode;
import org.wildfly.halos.proxy.dmr.ServerState;
import org.wildfly.halos.proxy.dmr.SuspendState;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;

import de.skuzzle.semantic.Version;

@ApplicationScoped
class InstanceRepository {

    private static final int DEFAULT_TIMEOUT = 10; // seconds
    private static final Logger log = Logger.getLogger(InstanceRepository.class);

    @Inject
    ContainerRepository containerRepository;

    @ConfigProperty(name = "halos.mcc.timeout")
    Optional<Integer> timeout;

    private final Map<Container, ModelControllerClient> clients;
    private final Map<Container, Instance> instances;
    private final UnicastProcessor<InstanceModification> processor;
    private final Multi<InstanceModification> modifications;

    InstanceRepository() {
        this.clients = synchronizedMap(new HashMap<>());
        this.instances = synchronizedMap(new HashMap<>());
        this.processor = UnicastProcessor.create();
        this.modifications = processor.broadcast().toAllSubscribers().onOverflow().dropPreviousItems();
    }

    void lookup() {
        Set<Container> containers = containerRepository.lookup();
        ContainerDiff diff = new ContainerDiff(clients.keySet(), containers);

        log.debugf("Added containers: %s", diff.added());
        for (Container container : diff.added()) {
            try {
                ModelControllerClient client = modelControllerClient(container);
                Instance instance = readWildFlyInstance(client, container);
                clients.put(container, client);
                instances.put(container, instance);
                processor.onNext(new InstanceModification(ADDED, instance));
            } catch (Exception e) {
                log.errorf("Unable to add new container %s: %s", container, e.getMessage());
            }
        }

        log.debugf("Removed containers: %s", diff.removed());
        for (Container container : diff.removed()) {
            ModelControllerClient client = clients.remove(container);
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    log.errorf("Unable to close client for %s: %s", container, e.getMessage());
                }
            }
            Instance instance = instances.remove(container);
            processor.onNext(new InstanceModification(REMOVED, instance));
        }
    }

    private ModelControllerClient modelControllerClient(final Container container) {
        ModelControllerClientConfiguration configuration = new ModelControllerClientConfiguration.Builder()
                .setProtocol("remote+http").setHostName(container.ip()).setPort(container.port())
                .setConnectionTimeout(((int) Duration.ofSeconds(timeout.orElse(DEFAULT_TIMEOUT)).toMillis())).build();
        return ModelControllerClient.Factory.create(configuration);
    }

    private Instance readWildFlyInstance(final ModelControllerClient client, final Container container) {
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

        return new Instance(container.id(), serverId, serverName, productName, productVersion, coreVersion, managementVersion,
                runningMode, serverState, suspendState);
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

    public Multi<InstanceModification> getModifications() {
        return modifications;
    }
}
