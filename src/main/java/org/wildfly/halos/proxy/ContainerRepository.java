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
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.OpenShiftClient;

@ApplicationScoped
class ContainerRepository {

    private static final String POD_LABELS = "halos.pod.labels.";
    private static final Logger log = Logger.getLogger(ContainerRepository.class);

    @Inject
    Config config;
    @Inject
    OpenShiftClient oc;

    private final Map<String, String> labels;

    ContainerRepository() {
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

    Set<Container> lookup() {
        Set<Container> containers = new HashSet<>();
        log.debugf("Lookup pods using labels %s", labels);

        try {
            PodList pods = oc.pods().withLabels(labels).list();
            log.debugf("Found %d pods", pods.getItems().size());
            for (Pod pod : pods.getItems()) {
                for (io.fabric8.kubernetes.api.model.Container c : pod.getSpec().getContainers()) {
                    for (ContainerPort containerPort : c.getPorts()) {
                        Integer port = containerPort.getContainerPort();
                        if (port == 9990) {
                            Container container = new Container(pod.getMetadata().getUid(), pod.getStatus().getPodIP(), 9990);
                            containers.add(container);
                            log.debugf("Found container %s", container);
                        }
                    }
                }
            }
        } catch (KubernetesClientException e) {
            log.errorf("Unable to read pods: %s", e.getMessage());
        }
        return containers;
    }
}
