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

import org.wildfly.halos.proxy.Capability;
import org.wildfly.halos.proxy.ManagedService;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class WildFlyCapability implements Capability {

    public static final String ID = "wildfly";
    private static final long INITIAL_BACK_OFF = 1_000;
    private static final long MAX_BACK_OFF = 5_000;
    private static final long EXPIRE_IN = 60_000;

    @Inject
    ManagementInterface managementInterface;

    @Inject
    WildFlyServerRepository wildFlyServerRepository;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "WildFly";
    }

    @Override
    public Uni<ManagedService> connect(final ManagedService managedService) {
        return managementInterface.connect(managedService).onFailure().retry()
                .withBackOff(Duration.ofMillis(INITIAL_BACK_OFF), Duration.ofMillis(MAX_BACK_OFF)).expireIn(EXPIRE_IN).onItem()
                .transform(tuple -> {
                    ManagedService connectedManagedService = managedService.withStatus(ManagedService.Status.CONNECTED);
                    Log.infof("Successfully connected to %s", connectedManagedService);
                    wildFlyServerRepository.add(connectedManagedService, tuple.getItem1(), tuple.getItem2());
                    return connectedManagedService;
                }).onFailure().recoverWithItem(throwable -> {
                    ManagedService failedManagedStatus = managedService.withStatus(ManagedService.Status.FAILED);
                    Log.errorf("Error connecting to %s: %s", failedManagedStatus, throwable.getMessage());
                    wildFlyServerRepository.delete(failedManagedStatus);
                    return failedManagedStatus;
                });
    }

    @Override
    public void close(final ManagedService managedService) {
        wildFlyServerRepository.delete(managedService);
        Log.infof("Close %s and %s", managedService, this);
    }

    @Override
    public String toString() {
        return String.format("Capability[%s]", id());
    }
}
