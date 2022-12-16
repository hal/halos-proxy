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

import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    @ConfigProperty(name = "halos.label.selector", defaultValue = "managedby=halos") String halOsLabelSelector;

    @Inject CapabilityRepository capabilityRepository;

    @Inject OpenShiftClient oc;

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
        for (CapabilityCollector collector : capabilityRepository.collectors()) {
            Capability capability = collector.capability();
            String labelSelector = halOsAnd(collector.labelSelector());
            Log.infof("Register service watch for capability %s using label selector %s", capability.id(), labelSelector);
            oc.services().withLabelSelector(labelSelector).watch(new Watcher<>() {
                @Override
                public void eventReceived(final Action action, final Service service) {
                    switch (action) {
                        case ADDED -> add(service, collector);
                        case DELETED -> delete(service, collector);
                    }
                }

                @Override
                public void onClose(final WatcherException e) {
                    if (e != null) {
                        Log.errorf("Error closing server watch for capability %s: %s", capability.id(), e.getMessage());
                    } else {
                        Log.infof("Close service watch for capability %s using label selector %s", capability.id(),
                                labelSelector);
                    }
                }
            });
        }
    }

    private String halOsAnd(final String labelSelector) {
        return halOsLabelSelector + "," + labelSelector;
    }

    // ------------------------------------------------------ add, delete

    private void add(final Service service, final CapabilityCollector collector) {
        Modification modification;
        ManagedService managedService = services.get(service.getMetadata().getUid());
        if (managedService == null) {
            modification = Modification.ADD;
            managedService = ManagedService.fromService(service, collector.capability());
        } else {
            modification = Modification.UPDATE;
            managedService = managedService.copy(collector.capability());
        }
        services.put(managedService.id(), managedService);
        publishModification(new ManagedServiceModification(modification, managedService));
        connect(managedService, collector);
    }

    private void connect(final ManagedService managedService, final CapabilityCollector collector) {
        collector.connect(managedService).subscribe().with(connectedManagedService -> {
            services.put(connectedManagedService.id(), connectedManagedService);
            publishModification(new ManagedServiceModification(Modification.UPDATE, connectedManagedService));
        }, throwable -> {
            ManagedService failed = managedService.copy(ManagedService.Status.FAILED);
            services.put(failed.id(), failed);
            publishModification(new ManagedServiceModification(Modification.UPDATE, failed));
        });
    }

    private void delete(final Service service, final CapabilityCollector collector) {
        ManagedService managedService = services.remove(service.getMetadata().getUid());
        if (managedService != null) {
            publishModification(new ManagedServiceModification(Modification.DELETE, managedService));
            collector.close(managedService);
        }
    }

    private void publishModification(final ManagedServiceModification msm) {
        processor.onNext(msm);
        String modificationName = msm.modification().name().charAt(0) + msm.modification().name().toLowerCase().substring(1);
        Log.infof("%s managed service %s", modificationName, msm.managedService().name());
    }

    // ------------------------------------------------------ properties

    public Set<ManagedService> managedServices() {
        return Set.copyOf(services.values());
    }

    Multi<ManagedServiceModification> modifications() {
        return modifications;
    }
}