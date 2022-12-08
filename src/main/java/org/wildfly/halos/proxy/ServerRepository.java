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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.eclipse.microprofile.config.Config;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.halos.proxy.dmr.Dispatcher;
import org.wildfly.halos.proxy.dmr.ModelNodeHelper;
import org.wildfly.halos.proxy.dmr.Operation;
import org.wildfly.halos.proxy.dmr.ResourceAddress;

import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.api.model.RouteTargetReference;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.smallrye.mutiny.tuples.Tuple2;

import de.skuzzle.semantic.Version;

import static java.util.Collections.synchronizedMap;
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

    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int MANAGEMENT_PORT = 9990;
    private static final String POD_LABELS = "halos.pod.labels.";
    private static final String REMOTE_HTTP = "remote+http";

    @Inject
    Config config;

    @Inject
    OpenShiftClient oc;

    @Inject
    LaunchMode launchMode;

    private final Map<String, String> labels;
    private final Map<String, Server> servers;
    private final Map<String, ModelControllerClient> clients;
    private final UnicastProcessor<ServerModification> processor;
    private final Multi<ServerModification> modifications;
    private Watch serviceWatch;

    ServerRepository() {
        labels = new HashMap<>();
        servers = synchronizedMap(new HashMap<>());
        clients = synchronizedMap(new HashMap<>());
        processor = UnicastProcessor.create();
        modifications = processor.broadcast().toAllSubscribers().onOverflow().dropPreviousItems();
    }

    // ------------------------------------------------------ init

    @PostConstruct
    void init() {
        initLabels();
        initServers();
        initWatch();
    }

    private void initLabels() {
        for (String property : config.getPropertyNames()) {
            if (property.startsWith(POD_LABELS)) {
                String label = property.substring(POD_LABELS.length()).replaceAll("^\"|\"$", "");
                String value = config.getValue(property, String.class);
                labels.put(label, value);
            }
        }
    }

    private void initServers() {
        ServiceList services = oc.services().withLabels(labels).list();
        for (Service service : services.getItems()) {
            add(service);
        }
    }

    private void initWatch() {
        serviceWatch = oc.services().withLabels(labels).watch(new Watcher<>() {
            @Override
            public void eventReceived(final Action action, final Service service) {
                switch (action) {
                    case ADDED -> add(service);
                    case DELETED -> delete(service);
                    case MODIFIED -> update(service);
                    default -> Log.errorf("Unknown service event %s", action.name());
                }
            }

            @Override
            public void onClose(final WatcherException e) {
                Log.errorf("Error in server watch: %s", e.getMessage());
            }
        });
    }

    void onStop(@Observes final ShutdownEvent event) {
        serviceWatch.close();
        Log.infof("Close service watch for labels %s", labels);
    }

    // ------------------------------------------------------ add, update, delete

    private void add(final Service service) {
        close(service);

        Tuple2<ModelControllerClient, Server> tuple = clientServer(service);
        ModelControllerClient client = tuple.getItem1();
        Server server = tuple.getItem2();
        if (server != null) {
            servers.put(server.id(), server);
            if (client != null) {
                clients.put(server.id(), client);
            }
            processor.onNext(ServerModification.from(null, server));
            Log.infof("Add %s", server);
        } else {
            Log.errorf("Unable to add server for service %s", serviceInfo(service));
        }
    }

    private void update(final Service service) {
        close(service);

        Server currentServer = servers.get(service.getMetadata().getUid());
        Tuple2<ModelControllerClient, Server> tuple = clientServer(service);
        ModelControllerClient client = tuple.getItem1();
        Server updatedServer = tuple.getItem2();
        if (updatedServer != null) {
            servers.put(updatedServer.id(), updatedServer);
            if (client != null) {
                clients.put(updatedServer.id(), client);
            }
            processor.onNext(ServerModification.from(currentServer, updatedServer));
            Log.infof("Update %s", updatedServer);
        } else {
            Log.errorf("Unable to update %s for %s", currentServer, serviceInfo(service));
        }
    }

    private void delete(final Service service) {
        close(service);

        Server server = servers.remove(service.getMetadata().getUid());
        if (server != null) {
            processor.onNext(ServerModification.from(server, null));
            Log.infof("Remove %s", server);
        } else {
            Log.errorf("Unable to remove server for %s", serviceInfo(service));
        }
    }

    private void close(final Service service) {
        String id = service.getMetadata().getUid();

        ModelControllerClient client = clients.remove(id);
        if (client != null) {
            try {
                client.close();
                if (Log.isDebugEnabled()) {
                    Server server = servers.get(id);
                    String serverInfo = server != null ? server.toString() : serviceInfo(service);
                    Log.debugf("Close model controller client for %s", serverInfo);
                }
            } catch (IOException e) {
                Server server = servers.get(id);
                String serverInfo = server != null ? server.toString() : serviceInfo(service);
                Log.errorf("Unable to close model controller client for %s: %s", serverInfo, e.getMessage());
            }
        }
    }

    private Tuple2<ModelControllerClient, Server> clientServer(final Service service) {
        Server server = null;
        ModelControllerClient client = null;

        String id = service.getMetadata().getUid();
        String name = service.getMetadata().getName();
        for (ServicePort servicePort : service.getSpec().getPorts()) {
            if (servicePort.getTargetPort().getIntVal() == MANAGEMENT_PORT) {
                Integer port = servicePort.getPort();

                Map<String, String> podSelector = service.getSpec().getSelector();
                PodList pods = oc.pods().withLabels(podSelector).list();
                if (pods.getItems().isEmpty()) {
                    server = Server.noPods(id, name);
                    Log.warnf("No pods found for service %s", name);
                } else {
                    try {
                        Tuple2<String, Integer> hostAndPort = getHostAndPort(service, port);
                        client = connect(hostAndPort.getItem1(), hostAndPort.getItem2());
                        server = readRootResource(id, name, client);
                    } catch (Exception e) {
                        server = Server.connectionError(id, name);
                        Log.errorf("Unable to connect to management interface for service %s: %s",
                                service.getMetadata().getName(), e.getMessage());
                    }
                }
            }
            break;
        }
        return Tuple2.of(client, server);
    }

    private Tuple2<String, Integer> getHostAndPort(final Service service, final Integer port) {
        Tuple2<String, Integer> hostAndPort = Tuple2.of(service.getSpec().getClusterIP(), port);
        if (launchMode == LaunchMode.DEVELOPMENT) {
            RouteList routes = oc.routes().withLabels(labels).list();
            for (Route route : routes.getItems()) {
                RouteTargetReference to = route.getSpec().getTo();
                if (to != null) {
                    if (service.getMetadata().getName().equals(to.getName())) {
                        int routePort = HTTP_PORT;
                        String routeHost = route.getSpec().getHost();
                        if (route.getSpec().getTls() != null) {
                            routePort = HTTPS_PORT;
                        }
                        hostAndPort = Tuple2.of(routeHost, routePort);
                        break;
                    }
                }
            }
        }
        return hostAndPort;
    }

    // ------------------------------------------------------ helper

    private String serviceInfo(final Service service) {
        return String.format("Service[uid=%s, name=%s]", service.getMetadata().getUid(), service.getMetadata().getName());
    }

    // ------------------------------------------------------ management model

    private ModelControllerClient connect(final String host, final int port) {
        return ModelControllerClient.Factory.create(REMOTE_HTTP, host, port, callbacks -> {
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

    private Server readRootResource(final String id, final String serviceName, final ModelControllerClient client) {
        Operation operation = new Operation.Builder(ResourceAddress.root(), READ_RESOURCE_OPERATION)
                .param(ATTRIBUTES_ONLY, true).param(INCLUDE_RUNTIME, true).build();
        ModelNode modelNode = new Dispatcher(client).execute(operation);

        String serverId = modelNode.get(UUID).asString();
        String serverName = modelNode.get(NAME).asString();
        String productName = modelNode.get(PRODUCT_NAME).asString();
        Version productVersion = parseVersion(serviceName, PRODUCT_VERSION, modelNode.get(PRODUCT_VERSION).asString());
        Version coreVersion = parseVersion(serviceName, RELEASE_VERSION, modelNode.get(RELEASE_VERSION).asString());
        Version managementVersion = parseManagementVersion(modelNode);
        RunningMode runningMode = ModelNodeHelper.asEnumValue(modelNode, RUNNING_MODE, RunningMode::valueOf,
                RunningMode.UNDEFINED);
        ServerState serverState = ModelNodeHelper.asEnumValue(modelNode, SERVER_STATE, ServerState::valueOf,
                ServerState.UNDEFINED);
        SuspendState suspendState = ModelNodeHelper.asEnumValue(modelNode, SUSPEND_STATE, SuspendState::valueOf,
                SuspendState.UNDEFINED);

        return new Server(id, serverId, serverName, productName, productVersion, coreVersion, managementVersion,
                ConnectionStatus.CONNECTED, runningMode, serverState, suspendState);
    }

    private Version parseVersion(final String serviceName, final String field, final String version) {
        String safeVersion = version.replace(".Final", "");
        try {
            return Version.parseVersion(safeVersion);
        } catch (Exception e) {
            Log.errorf("Unable to parse %s as version for %s, field %s: %s", safeVersion, serviceName, field, e.getMessage());
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

    // ------------------------------------------------------ properties

    ModelControllerClient client(final String id) {
        return clients.get(id);
    }

    Set<Server> servers() {
        return Set.copyOf(servers.values());
    }

    List<Tuple2<ModelControllerClient, Server>> clientsServers() {
        List<Tuple2<ModelControllerClient, Server>> clientsServers = new ArrayList<>();
        for (Server server : servers.values()) {
            ModelControllerClient client = clients.get(server.id());
            if (client != null) {
                clientsServers.add(Tuple2.of(client, server));
            }
        }
        return clientsServers;
    }

    Multi<ServerModification> modifications() {
        return modifications;
    }
}
