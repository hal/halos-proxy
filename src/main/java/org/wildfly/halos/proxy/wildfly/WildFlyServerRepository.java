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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.halos.proxy.ManagedService;

import io.quarkus.logging.Log;

@ApplicationScoped
class WildFlyServerRepository {

    private final Map<ManagedService, WildFlyServer> servers;
    private final Map<ManagedService, ModelControllerClient> clients;

    WildFlyServerRepository() {
        servers = new ConcurrentHashMap<>();
        clients = new ConcurrentHashMap<>();
    }

    void add(final ManagedService managedService, final ModelControllerClient modelControllerClient,
            final WildFlyServer wildFlyServer) {
        clients.put(managedService, modelControllerClient);
        servers.put(managedService, wildFlyServer);
        Log.infof("Add %s", wildFlyServer);
    }

    void delete(final ManagedService managedService) {
        WildFlyServer removedServer = servers.remove(managedService);
        ModelControllerClient removedClient = clients.remove(managedService);
        if (removedServer != null && removedClient != null) {
            Log.infof("Remove %s", removedServer);
        }
    }

    Set<WildFlyServer> wildFlyServers() {
        return Set.copyOf(servers.values());
    }
}
