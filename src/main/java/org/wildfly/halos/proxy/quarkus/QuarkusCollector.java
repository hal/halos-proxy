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

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.wildfly.halos.proxy.BaseCapabilityCollector;
import org.wildfly.halos.proxy.Capability;
import org.wildfly.halos.proxy.CapabilityCollector;
import org.wildfly.halos.proxy.ManagedService;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

import com.google.common.net.HostAndPort;

import static java.util.stream.Collectors.toList;
import static org.wildfly.halos.proxy.Constants.HTTPS_PORT;
import static org.wildfly.halos.proxy.Constants.HTTP_PORT;

@ApplicationScoped
public class QuarkusCollector extends BaseCapabilityCollector implements CapabilityCollector {

    @Inject OpenShiftClient oc;

    @Inject QuarkusServiceRepository quarkusServiceRepository;

    @Override
    public Capability capability() {
        return QuarkusCapability.INSTANCE;
    }

    @Override
    public Uni<ManagedService> connect(final ManagedService managedService) {
        List<Service> services = oc.services().withField("metadata.name", managedService.name()).list().getItems();
        if (services.isEmpty()) {
            Log.errorf("Unable to connect to managed service %s: No service found for %s", managedService.name(),
                    managedService.name());
            return Uni.createFrom().item(managedService.copy(ManagedService.Status.FAILED));
        } else if (services.size() > 1) {
            Log.errorf("Unable to connect to managed service %s: More than one service found for %s", managedService.name(),
                    managedService.name());
            return Uni.createFrom().item(managedService.copy(ManagedService.Status.FAILED));
        } else {
            Service service = services.get(0);
            List<HostAndPort> hostAndPorts = routes(service);
            ManagedService connectedManagedService = managedService.copy(ManagedService.Status.CONNECTED);
            QuarkusService quarkusService = new QuarkusService(connectedManagedService, hostAndPorts);
            quarkusServiceRepository.add(quarkusService);
            Log.infof("Successfully connected to managed service %s", managedService.name());
            return Uni.createFrom().item(connectedManagedService);
        }
    }

    @Override
    public void close(final ManagedService managedService) {
        quarkusServiceRepository.remove(managedService);
        Log.infof("Close connection to managed service %s", managedService.name());
    }

    private List<HostAndPort> routes(final Service service) {
        return oc.routes().withField("spec.to.name", service.getMetadata().getName()).list().getItems().stream()
                .map(route -> HostAndPort.fromParts(route.getSpec().getHost(),
                        route.getSpec().getTls() != null ? HTTPS_PORT : HTTP_PORT))
                .collect(toList());
    }
}
