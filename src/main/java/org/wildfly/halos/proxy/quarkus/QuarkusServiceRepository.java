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
package org.wildfly.halos.proxy.quarkus;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;

import org.wildfly.halos.proxy.ManagedService;

import io.quarkus.logging.Log;

@ApplicationScoped
class QuarkusServiceRepository {

    private final Map<String, QuarkusService> services;

    QuarkusServiceRepository() {
        services = new ConcurrentHashMap<>();
    }

    void add(final QuarkusService quarkusService) {
        services.put(quarkusService.managedService().id(), quarkusService);
        Log.infof("Add %s", quarkusService);
    }

    void delete(final ManagedService managedService) {
        QuarkusService removedService = services.remove(managedService);
        if (removedService != null) {
            Log.infof("Remove %s", removedService);
        }
    }

    Set<QuarkusService> quarkusServices() {
        return Set.copyOf(services.values());
    }
}
