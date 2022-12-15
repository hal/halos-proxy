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

import java.util.HashSet;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Service;

public record ManagedService(String id, String name, Status status, Set<Capability> capabilities) {

    public enum Status {
        PENDING, CONNECTED, FAILED
    }

    static ManagedService fromService(final Service service, final Capability capability) {
        return new ManagedService(service.getMetadata().getUid(), service.getMetadata().getName(),
                ManagedService.Status.PENDING, Set.of(capability));
    }

    ManagedService withStatus(Status status) {
        return new ManagedService(id(), name(), status, capabilities());
    }

    ManagedService addCapability(Capability capability) {
        HashSet<Capability> capabilities = new HashSet<>(capabilities());
        capabilities.add(capability);
        return new ManagedService(id(), name(), status(), capabilities);
    }
}
