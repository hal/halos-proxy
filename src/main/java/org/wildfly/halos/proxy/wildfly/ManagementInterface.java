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
package org.wildfly.halos.proxy.wildfly;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.halos.proxy.ManagedService;
import org.wildfly.halos.proxy.wildfly.dmr.Composite;
import org.wildfly.halos.proxy.wildfly.dmr.CompositeResult;
import org.wildfly.halos.proxy.wildfly.dmr.ModelNodeHelper;
import org.wildfly.halos.proxy.wildfly.dmr.Operation;
import org.wildfly.halos.proxy.wildfly.dmr.ResourceAddress;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;

import com.google.common.net.HostAndPort;

import de.skuzzle.semantic.Version;

import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.ATTRIBUTES_ONLY;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.CHILD_TYPE;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.DEPLOYMENT;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.DISABLED_TIME;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.ENABLED;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.ENABLED_TIME;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.NAME;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.PRODUCT_NAME;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.RECURSIVE;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.RELEASE_VERSION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.RESULT;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.RUNNING_MODE;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.SERVER_STATE;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.STATUS;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.SUSPEND_STATE;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.UUID;

@ApplicationScoped
public class ManagementInterface {

    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int MANAGEMENT_PORT = 9990;
    private static final String REMOTE_HTTP = "remote+http";

    @Inject
    OpenShiftClient oc;

    @Inject
    LaunchMode launchMode;

    Uni<Server> connect(final ManagedService managedService) {
        // TODO Refactor blocking code!
        try {
            HostAndPort hostAndPort = hostAndPort(managedService);
            ModelControllerClient client = connect(managedService, hostAndPort);
            return readServerAndDeployments(managedService, client);
        } catch (Exception e) {
            return Uni.createFrom().failure(e);
        }
    }

    private HostAndPort hostAndPort(final ManagedService managedService) {
        Service service = oc.services().withName(managedService.name()).get();
        if (service != null) {
            if (launchMode.isDevOrTest()) {
                List<Route> routes = oc.routes().withField("spec.to.name", service.getMetadata().getName()).list().getItems();
                for (Route route : routes) {
                    if (route.getSpec().getPort().getTargetPort().getIntVal() == MANAGEMENT_PORT) {
                        int routePort = HTTP_PORT;
                        String routeHost = route.getSpec().getHost();
                        if (route.getSpec().getTls() != null) {
                            routePort = HTTPS_PORT;
                        }
                        return HostAndPort.fromParts(routeHost, routePort);
                    }
                }
            } else {
                for (ServicePort servicePort : service.getSpec().getPorts()) {
                    if (servicePort.getTargetPort().getIntVal() == MANAGEMENT_PORT) {
                        Integer port = servicePort.getPort();
                        return HostAndPort.fromParts(service.getSpec().getClusterIP(), port);
                    }
                }
            }
        } else {
            throw new WildFlyException(String.format("Unable to get host and port for %s. Service %s is unavailable",
                    managedService, managedService.name()));
        }
        throw new WildFlyException("Unable to get host and port for " + managedService);
    }

    private ModelControllerClient connect(final ManagedService managedService, final HostAndPort hostAndPort) {
        return ModelControllerClient.Factory.create(REMOTE_HTTP, hostAndPort.getHost(), hostAndPort.getPort(), callbacks -> {
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

    private Uni<Server> readServerAndDeployments(final ManagedService managedService, final ModelControllerClient client) {
        Operation rootOperation = new Operation.Builder(ResourceAddress.root(), READ_RESOURCE_OPERATION)
                .param(ATTRIBUTES_ONLY, true).param(INCLUDE_RUNTIME, true).build();
        Operation deploymentsOperation = new Operation.Builder(ResourceAddress.root(), READ_CHILDREN_RESOURCES_OPERATION)
                .param(CHILD_TYPE, DEPLOYMENT).param(INCLUDE_RUNTIME, true).param(RECURSIVE, false).build();
        Composite composite = new Composite(rootOperation, deploymentsOperation);
        return Uni.createFrom().future(client.executeAsync(composite)).onFailure().recoverWithItem(throwable -> {
            ModelNode modelNode = new ModelNode().get(FAILURE_DESCRIPTION).set(throwable.getMessage());
            return modelNode;
        }).onItem().transform(Unchecked.function(payload -> {
            CompositeResult compositeResult = new CompositeResult(payload.get(RESULT));
            if (compositeResult.isFailure()) {
                throw new WildFlyException(String.format("Operation %s failed for %s", composite.asCli(), managedService));
            } else if (composite.isEmpty()) {
                throw new WildFlyException(
                        String.format("Operation %s for %s returned an empty result!" + composite.asCli(), managedService));
            } else {
                ModelNode rootNode = compositeResult.step(0).get(RESULT);
                String serverId = rootNode.get(UUID).asString();
                String serverName = rootNode.get(NAME).asString();
                String productName = rootNode.get(PRODUCT_NAME).asString();
                Version productVersion = parseVersion(managedService, PRODUCT_VERSION,
                        rootNode.get(PRODUCT_VERSION).asString());
                Version coreVersion = parseVersion(managedService, RELEASE_VERSION, rootNode.get(RELEASE_VERSION).asString());
                Version managementVersion = parseManagementVersion(rootNode);
                RunningMode runningMode = ModelNodeHelper.asEnumValue(rootNode, RUNNING_MODE, RunningMode::valueOf,
                        RunningMode.UNDEFINED);
                ServerState serverState = ModelNodeHelper.asEnumValue(rootNode, SERVER_STATE, ServerState::valueOf,
                        ServerState.UNDEFINED);
                SuspendState suspendState = ModelNodeHelper.asEnumValue(rootNode, SUSPEND_STATE, SuspendState::valueOf,
                        SuspendState.UNDEFINED);

                Set<Deployment> deployments = compositeResult.step(1).get(RESULT).asPropertyList().stream().map(property -> {
                    String name = property.getName();
                    ModelNode deploymentNode = property.getValue();
                    DeploymentStatus status = ModelNodeHelper.asEnumValue(deploymentNode, STATUS, DeploymentStatus::valueOf,
                            DeploymentStatus.UNDEFINED);
                    boolean enabled = deploymentNode.get(ENABLED).asBoolean();
                    return new Deployment(name, status, enabled,
                            ModelNodeHelper.failSafeLocalDateTime(deploymentNode, ENABLED_TIME),
                            ModelNodeHelper.failSafeLocalDateTime(deploymentNode, DISABLED_TIME));
                }).collect(Collectors.toSet());

                return new Server(managedService, serverId, serverName, productName, productVersion, coreVersion,
                        managementVersion, runningMode, serverState, suspendState, deployments);
            }
        }));
    }

    private Version parseVersion(final ManagedService managedService, final String field, final String version) {
        String safeVersion = version.replace(".Final", "");
        try {
            return Version.parseVersion(safeVersion);
        } catch (Exception e) {
            Log.errorf("Unable to parse %s as version for %s, field %s: %s", safeVersion, managedService, field,
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
}
