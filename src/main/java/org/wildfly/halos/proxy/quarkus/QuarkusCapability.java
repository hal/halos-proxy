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

import org.wildfly.halos.proxy.Capability;
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
public class QuarkusCapability implements Capability {

    public static final String ID = "quarkus";

    @Inject
    OpenShiftClient oc;

    @Inject
    QuarkusServiceRepository repository;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Quarkus";
    }

    @Override
    public Uni<ManagedService> connect(final ManagedService managedService) {
        List<Service> services = oc.services().withField("metadata.name", managedService.name()).list().getItems();
        if (services.isEmpty()) {
            Log.errorf("Unable to connect %s and %s: No service found for %s", managedService, this, managedService.name());
            return Uni.createFrom().item(managedService.withStatus(ManagedService.Status.FAILED));
        } else if (services.size() > 1) {
            Log.errorf("Unable to connect %s and %s: More than one service found for %s", managedService, this,
                    managedService.name());
            return Uni.createFrom().item(managedService.withStatus(ManagedService.Status.FAILED));
        } else {
            Service service = services.get(0);
            List<HostAndPort> hostAndPorts = routes(service);
            QuarkusService quarkusService = new QuarkusService(managedService, hostAndPorts);
            repository.add(quarkusService);
            Log.infof("Connect %s and %s", managedService, this);
            return Uni.createFrom().item(managedService.withStatus(ManagedService.Status.CONNECTED));
        }
    }

    @Override
    public void close(final ManagedService managedService) {
        repository.delete(managedService);
        Log.infof("Close %s and %s", managedService, this);
    }

    @Override
    public String toString() {
        return String.format("Capability[%s]", id());
    }

    private List<HostAndPort> routes(final Service service) {
        return oc.routes().withField("spec.to.name", service.getMetadata().getName()).list().getItems().stream()
                .map(route -> HostAndPort.fromParts(route.getSpec().getHost(),
                        route.getSpec().getTls() != null ? HTTPS_PORT : HTTP_PORT))
                .collect(toList());
    }
}
