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

import java.time.Duration;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.wildfly.halos.proxy.BaseCapabilityExtension;
import org.wildfly.halos.proxy.Capability;
import org.wildfly.halos.proxy.CapabilityExtension;
import org.wildfly.halos.proxy.Connection;
import org.wildfly.halos.proxy.ManagedService;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class WildFlyExtension extends BaseCapabilityExtension implements CapabilityExtension {

    private static final long INITIAL_BACK_OFF = 1_000;
    private static final long MAX_BACK_OFF = 5_000;
    private static final long EXPIRE_IN = 60_000;

    @Inject ManagementInterface managementInterface;
    @Inject WildFlyServerRepository wildFlyServerRepository;

    @Override
    public Capability capability() {
        return WildFlyCapability.INSTANCE;
    }

    @Override
    public Uni<Connection> connect(final ManagedService managedService) {
        return managementInterface.connect(managedService).onFailure().retry()
                .withBackOff(Duration.ofMillis(INITIAL_BACK_OFF), Duration.ofMillis(MAX_BACK_OFF)).expireIn(EXPIRE_IN).onItem()
                .transform(tuple -> {
                    wildFlyServerRepository.add(managedService, tuple.getItem1(), tuple.getItem2());
                    Log.infof("Successfully connected to managed service %s", managedService.name());
                    return Connection.connected();
                }).onFailure().recoverWithItem(throwable -> Connection.failed(String
                        .format("Error connecting to managed service %s: %s", managedService.name(), throwable.getMessage())));
    }

    @Override
    public void close(final ManagedService managedService) {
        wildFlyServerRepository.remove(managedService);
        Log.infof("Close connection to managed service %s", managedService.name());
    }
}
