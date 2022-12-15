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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;

@ApplicationScoped
public class ManagedServiceRepository {

    @Inject
    Capabilities capabilities;

    @Inject
    OpenShiftClient oc;

    private final Map<String, ManagedService> services;
    private final UnicastProcessor<ManagedServiceModification> processor;
    private final Multi<ManagedServiceModification> modifications;

    ManagedServiceRepository() {
        services = new ConcurrentHashMap<>();
        processor = UnicastProcessor.create();
        modifications = processor.broadcast().toAllSubscribers().onOverflow().dropPreviousItems();
    }

    // ------------------------------------------------------ init

    void onStart(@Observes final StartupEvent event) {
        initWatches();
    }

    private void initWatches() {
        for (Map.Entry<Capability, String> entry : capabilities.labelSelectors().entrySet()) {
            Capability capability = entry.getKey();
            String labelSelector = entry.getValue();
            Log.infof("Register service watch for %s using label selector %s", capability, labelSelector);
            oc.services().withLabelSelector(labelSelector).watch(new Watcher<>() {
                @Override
                public void eventReceived(final Action action, final Service service) {
                    switch (action) {
                        case ADDED -> add(service, capability);
                        case DELETED -> delete(service);
                    }
                }

                @Override
                public void onClose(final WatcherException e) {
                    if (e != null) {
                        Log.errorf("Error closing server watch for %s: %s", capability, e.getMessage());
                    } else {
                        Log.infof("Close service watch for %s using label selector %s", capability, labelSelector);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------ add, delete

    private void add(final Service service, final Capability capability) {
        Modification modification;
        ManagedService managedService = services.get(service.getMetadata().getUid());
        if (managedService == null) {
            modification = Modification.ADD;
            managedService = ManagedService.fromService(service, capability);
        } else {
            modification = Modification.UPDATE;
            managedService = managedService.addCapability(capability);
        }
        services.put(managedService.id(), managedService);
        publishModification(new ManagedServiceModification(modification, managedService));

        // connect capability
        final ManagedService finalManagedService = managedService;
        capability.connect(managedService).subscribe().with(status -> {
            // ok
            ManagedService connectedManagedService = finalManagedService.withStatus(status);
            services.put(connectedManagedService.id(), connectedManagedService);
            publishModification(new ManagedServiceModification(Modification.UPDATE, connectedManagedService));
            Log.infof("Open %s for %s", capability, connectedManagedService);
        }, throwable -> {
            // failed
            ManagedService ums = finalManagedService.withStatus(ManagedService.Status.FAILED);
            publishModification(new ManagedServiceModification(Modification.UPDATE, ums));
            Log.errorf("Error connecting %s and %s: %s", finalManagedService, capability, throwable.getMessage());
        });
    }

    private void delete(final Service service) {
        ManagedService managedService = services.remove(service.getMetadata().getUid());
        if (managedService != null) {
            publishModification(new ManagedServiceModification(Modification.DELETE, managedService));
            for (Capability capability : managedService.capabilities()) {
                capability.close(managedService).subscribe()
                        .with(unused -> Log.infof("Close %s for %s", capability, managedService), throwable -> Log
                                .errorf("Error closing %s for %s: %s", capability, managedService, throwable.getMessage()));
            }
        }
    }

    private void publishModification(final ManagedServiceModification msm) {
        processor.onNext(msm);
        String modificationName = msm.modification().name().charAt(0) + msm.modification().name().toLowerCase().substring(1);
        Log.infof("%s %s", modificationName, msm.managedService());
    }

    // ------------------------------------------------------ properties

    public Set<ManagedService> managedServices() {
        return Set.copyOf(services.values());
    }

    Multi<ManagedServiceModification> modifications() {
        return modifications;
    }
}