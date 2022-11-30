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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.halos.proxy.dmr.Dispatcher;
import org.wildfly.halos.proxy.dmr.ModelNodeHelper;
import org.wildfly.halos.proxy.dmr.Operation;
import org.wildfly.halos.proxy.dmr.ResourceAddress;

import io.quarkus.logging.Log;

import static java.util.Collections.synchronizedMap;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.CHILD_TYPE;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.DISABLED_TIME;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.ENABLED;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.ENABLED_TIME;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.STATUS;

@ApplicationScoped
class DeploymentRepository {

    @Inject
    ServerRepository serverRepository;

    private final Map<Server, Deployment> deployments;

    DeploymentRepository() {
        this.deployments = synchronizedMap(new HashMap<>());
    }

    void lookup() {
        Difference<Server> difference = new Difference<>(deployments.keySet(), serverRepository.getInstances(), Server::uid);

        Log.debugf("Added servers: %s", difference.added());
        for (Server server : difference.added()) {
            ModelControllerClient client = serverRepository.getClient(server.uid());
            if (client != null) {
                try {
                    Set<Deployment> deployments = readDeployment(server, client);
                    for (Deployment deployment : deployments) {
                        add(server, deployment);
                    }
                } catch (Exception e) {
                    Log.errorf("Unable to add deployment for server %s: %s", server, e.getMessage());
                }
            }
        }

        Log.debugf("Removed servers: %s", difference.removed());
        for (Server server : difference.removed()) {
            remove(server);
        }
    }

    private void add(final Server server, final Deployment deployment) {
        deployments.put(server, deployment);
        Log.infof("Add deployment %s", deployment);
    }

    private void remove(final Server server) {
        Deployment deployment = deployments.remove(server);
        if (deployment != null) {
            Log.infof("Remove deployment %s", deployment);
        }
    }

    private Set<Deployment> readDeployment(final Server server, final ModelControllerClient client) {
        Set<Deployment> deployments = new HashSet<>();

        Operation operation = new Operation.Builder(ResourceAddress.root(), READ_CHILDREN_RESOURCES_OPERATION)
                .param(CHILD_TYPE, "deployment").param(INCLUDE_RUNTIME, true).build();
        ModelNode modelNode = new Dispatcher(client).execute(operation);

        if (modelNode.isDefined()) {
            for (Property property : modelNode.asPropertyList()) {
                String name = property.getName();
                ModelNode deploymentNode = property.getValue();

                DeploymentStatus status = ModelNodeHelper.asEnumValue(deploymentNode, STATUS, DeploymentStatus::valueOf,
                        DeploymentStatus.UNDEFINED);
                boolean enabled = deploymentNode.get(ENABLED).asBoolean();

                deployments.add(new Deployment(server, name, status, enabled,
                        ModelNodeHelper.failSafeLocalDateTime(deploymentNode, ENABLED_TIME),
                        ModelNodeHelper.failSafeLocalDateTime(deploymentNode, DISABLED_TIME)));
            }
        }
        return deployments;
    }

    Set<Deployment> getDeployments() {
        return Set.copyOf(deployments.values());
    }
}
