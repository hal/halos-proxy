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
package org.wildfly.halos.capability.wildfly;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.halos.api.ManagedService;

import io.quarkus.logging.Log;

@ApplicationScoped
class WildFlyServerRepository {

    private final Map<String, String> managedServiceToServer; // key == managed service name, value == WildFly server name
    private final Map<String, WildFlyServer> servers; // key == WildFly server name
    private final Map<String, ModelControllerClient> clients; // key == WildFly server name

    WildFlyServerRepository() {
        managedServiceToServer = new ConcurrentHashMap<>();
        servers = new ConcurrentHashMap<>();
        clients = new ConcurrentHashMap<>();
    }

    void add(final ManagedService managedService, final ModelControllerClient modelControllerClient,
            final WildFlyServer wildFlyServer) {
        String wildFlyServerName = wildFlyServer.name();
        managedServiceToServer.put(managedService.name(), wildFlyServerName);
        clients.put(wildFlyServerName, modelControllerClient);
        servers.put(wildFlyServerName, wildFlyServer);
    }

    void remove(final ManagedService managedService) {
        String wildFlyServerName = managedServiceToServer.remove(managedService.name());
        if (wildFlyServerName != null) {
            servers.remove(wildFlyServerName);
            ModelControllerClient client = clients.remove(wildFlyServerName);
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    Log.errorf("Error closing client for managed service %s: %s", managedService.name(), e.getMessage());
                }
            }
        }
    }

    Set<WildFlyServer> wildFlyServers() {
        return Set.copyOf(servers.values());
    }

    WildFlyServer wildFlyServer(final String serverName) {
        return servers.get(serverName);
    }

    ModelControllerClient client(final String serverName) {
        return clients.get(serverName);
    }
}
