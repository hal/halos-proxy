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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.eclipse.microprofile.config.Config;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
class Discovery {

    private static final String POD_LABELS = "halos.pod.labels.";
    private static final Logger log = Logger.getLogger(Discovery.class);

    @Inject
    Config config;
    @Inject
    OpenShiftClient oc;

    // @Scheduled(every = "5s")
    void lookup(@Observes StartupEvent ev) {
        // injecting directly as a Map<String,String>
        // does not work when the labels contain "."
        Map<String, String> podLabels = new HashMap<>();
        for (String property : config.getPropertyNames()) {
            if (property.startsWith(POD_LABELS)) {
                String label = property.substring(POD_LABELS.length()).replaceAll("^\"|\"$", "");
                String value = config.getValue(property, String.class);
                podLabels.put(label, value);
            }
        }
        log.infof("Lookup pods using labels %s", podLabels);
        PodList pods = oc.pods().withLabels(podLabels).list();
        log.infof("Found %d pods", pods.getItems().size());
        for (Pod pod : pods.getItems()) {
            for (Container container : pod.getSpec().getContainers()) {
                for (ContainerPort port : container.getPorts()) {
                    log.infof("Pod %s, container %s, port: %d", pod.getMetadata().getUid(), container.getName(),
                            port.getContainerPort());
                    if (port.getContainerPort() == 9990) {
                        ModelControllerClient client = connect(pod.getStatus().getPodIP(), 9990);
                        if (client != null) {
                            ModelNode result = readRootResource(client);
                            if (result != null) {
                                log.info(result.toJSONString(false));
                            }
                        }
                    }
                }
            }
        }
    }

    private ModelControllerClient connect(String ip, int port) {
        log.infof("Connect to management endpoint %s:%d", ip, port);
        try {
            InetAddress address = InetAddress.getByName(ip);
            return ModelControllerClient.Factory.create("remote+http", address, port, callbacks -> {
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback ncb) {
                        ncb.setName("admin");
                    } else if (current instanceof PasswordCallback pcb) {
                        pcb.setPassword("admin".toCharArray());
                    } else if (current instanceof RealmCallback rcb) {
                        rcb.setText(rcb.getDefaultText());
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            });
        } catch (UnknownHostException e) {
            log.errorf("Unable to connect to instance %s:%d: ", ip, port, e.getMessage());
            return null;
        }
    }

    private ModelNode readRootResource(ModelControllerClient client) {
        log.infof("Execute :read-resource for root resource");
        ModelNode operation = Operations.createReadResourceOperation(new ModelNode().setEmptyList());
        try {
            return client.execute(operation);
        } catch (IOException e) {
            log.errorf("Unable to execute operation %s: %s", operation.toJSONString(true), e.getMessage());
            return null;
        }
    }
}
