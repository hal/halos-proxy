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

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.halos.proxy.ManagedService;
import org.wildfly.halos.proxy.ManagedServiceRepository;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ManagementInterfaceIT {

    private static final String SERVICE_NAME = "wildfly-thread-racing";

    @Inject
    ManagedServiceRepository managedServiceRepository;

    @Inject
    ManagementInterface managementInterface;

    ManagedService managedService;

    @BeforeEach
    void setUp() {
        managedService = managedServiceRepository.managedServiceByName(SERVICE_NAME);
    }

    @Test
    void connect() {
        assertNotNull(managedService);
        Uni<Server> server = managementInterface.connect(managedService);
        UniAssertSubscriber<Server> subscriber = server.subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitItem().assertCompleted();
    }
}
