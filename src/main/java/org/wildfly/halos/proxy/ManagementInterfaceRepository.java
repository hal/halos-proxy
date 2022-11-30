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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.logging.Log;

@ApplicationScoped
class ManagementInterfaceRepository {

    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int MANAGEMENT_PORT = 9990;
    private static final String POD_LABELS = "halos.pod.labels.";
    private static final String SERVICE_KIND = "Service";

    @Inject
    Config config;
    @Inject
    OpenShiftClient oc;

    private final Map<String, String> labels;

    ManagementInterfaceRepository() {
        labels = new HashMap<>();
    }

    @PostConstruct
    void initLabels() {
        // injecting directly as a Map<String,String>
        // does not work when the labels contain "."
        for (String property : config.getPropertyNames()) {
            if (property.startsWith(POD_LABELS)) {
                String label = property.substring(POD_LABELS.length()).replaceAll("^\"|\"$", "");
                String value = config.getValue(property, String.class);
                labels.put(label, value);
            }
        }
    }

    Set<ManagementInterface> lookup() {
        Set<ManagementInterface> managementInterfaces = new HashSet<>();
        Set<String> serviceNames = new HashSet<>();

        try {
            Log.debugf("Lookup routes using labels %s", labels);
            RouteList routes = oc.routes().withLabels(labels).list();
            Log.debugf("Found %d routes", routes.getItems().size());
            for (Route route : routes.getItems()) {
                String id = route.getMetadata().getUid();
                String name = route.getMetadata().getName();
                int port = HTTP_PORT;
                String host = route.getSpec().getHost();
                if (route.getSpec().getTls() != null) {
                    port = HTTPS_PORT;
                }
                if (SERVICE_KIND.equals(route.getSpec().getTo().getKind())) {
                    // remember service name
                    String serviceName = route.getSpec().getTo().getName();
                    serviceNames.add(serviceName);
                }
                ManagementInterface managementInterface = new ManagementInterface(id, name, host, port);
                managementInterfaces.add(managementInterface);
                Log.infof("Found management interface %s for route %s", managementInterface, name);
            }
        } catch (KubernetesClientException e) {
            Log.errorf("Unable to read routes: %s", e.getMessage());
        }

        try {
            Log.debugf("Lookup services using labels %s", labels);
            ServiceList services = oc.services().withLabels(labels).list();
            Log.debugf("Found %d services", services.getItems().size());
            for (Service service : services.getItems()) {
                String name = service.getMetadata().getName();
                if (serviceNames.contains(name)) {
                    Log.debugf("Skip service %s, since there's already a route for it.", name);
                    continue;
                }
                String id = service.getMetadata().getUid();
                for (ServicePort port : service.getSpec().getPorts()) {
                    if (port.getTargetPort().getIntVal() == MANAGEMENT_PORT) {
                        String host = service.getSpec().getClusterIP();
                        int servicePort = port.getPort();
                        ManagementInterface managementInterface = new ManagementInterface(id, name, host, servicePort);
                        managementInterfaces.add(managementInterface);
                        Log.infof("Found management interface %s for service %s", managementInterface, name);
                    }
                }
            }
        } catch (KubernetesClientException e) {
            Log.errorf("Unable to read services: %s", e.getMessage());
        }
        return managementInterfaces;
    }
}
