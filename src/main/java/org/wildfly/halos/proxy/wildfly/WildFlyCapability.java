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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.wildfly.halos.proxy.Capability;
import org.wildfly.halos.proxy.ManagedService;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class WildFlyCapability implements Capability {

    public static final String ID = "wildfly";

    @Inject
    ManagementInterface managementInterface;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "WildFly";
    }

    @Override
    public Uni<ManagedService.Status> connect(final ManagedService managedService) {
        Log.warnf("connect is not yet implemented for %s", this);
        return Uni.createFrom().item(managedService.status());
    }

    @Override
    public Uni<Void> close(final ManagedService managedService) {
        Log.warnf("connect is not yet implemented for %s", this);
        return Uni.createFrom().voidItem();
    }

    @Override
    public String toString() {
        return String.format("Capability[%s]", id());
    }
}
