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

    @Inject OpenShiftClient oc;
    @Inject CapabilityRepository capabilityRepository;
    @ConfigProperty(name = "halos.label.selector", defaultValue = "managedby=halos") String halOsLabelSelector;

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
        for (CapabilityExtension collector : capabilityRepository.extensions()) {
            Capability capability = collector.capability();
            String labelSelector = halOsAnd(collector.labelSelector());
            Log.infof("Register service watch for capability %s using label selector %s", capability.name(), labelSelector);
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
                        Log.errorf("Error closing server watch for capability %s: %s", capability.name(), e.getMessage());
                    } else {
                        Log.infof("Close service watch for capability %s using label selector %s", capability.name(),
                                labelSelector);
                    }
                }
            });
        }
    }

    private String halOsAnd(final String labelSelector) {
        return halOsLabelSelector + "," + labelSelector;
    }

    // ------------------------------------------------------ connect

    void connect(final ManagedService managedService, final CapabilityExtension collector) {
        collector.connect(managedService).subscribe().with(connectionStatus -> {
            ManagedService connected = managedService.updateStatus(connectionStatus);
            services.put(connected.name(), connected);
            publishModification(new ManagedServiceModification(connected, Modification.UPDATE));
        }, throwable -> {
            ManagedService failed = managedService.updateStatus(Connection.failed(String
                    .format("Unable to connect to managed service %s: %s", managedService.name(), throwable.getMessage())));
            services.put(failed.name(), failed);
            publishModification(new ManagedServiceModification(failed, Modification.UPDATE));
        });
    }

    // ------------------------------------------------------ add, delete

    private void add(final Service service, final CapabilityExtension collector) {
        Modification modification;
        ManagedService managedService = services.get(service.getMetadata().getName());
        if (managedService == null) {
            modification = Modification.ADD;
            managedService = ManagedService.fromService(service, collector.capability());
        } else {
            modification = Modification.UPDATE;
            managedService = managedService.addCapability(collector.capability());
        }
        services.put(managedService.name(), managedService);
        publishModification(new ManagedServiceModification(managedService, modification));
        connect(managedService, collector);
    }

    private void delete(final Service service, final CapabilityExtension collector) {
        ManagedService managedService = services.remove(service.getMetadata().getName());
        if (managedService != null) {
            publishModification(new ManagedServiceModification(managedService, Modification.DELETE));
            collector.close(managedService);
        }
    }

    private void publishModification(final ManagedServiceModification msm) {
        processor.onNext(msm);
        String modificationName = msm.modification().name().charAt(0) + msm.modification().name().toLowerCase().substring(1);
        Log.infof("%s managed service %s", modificationName, msm.managedService().name());
    }

    // ------------------------------------------------------ properties

    ManagedService managedService(final String name) {
        return services.get(name);
    }

    Set<ManagedService> managedServices() {
        return Set.copyOf(services.values());
    }

    Multi<ManagedServiceModification> modifications() {
        return modifications;
    }
}