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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.arc.All;
import io.quarkus.logging.Log;

import static java.util.Collections.unmodifiableMap;

@ApplicationScoped
public class Capabilities {

    private static final String CAPABILITY_LABELS_PREFIX = "halos.capability.";
    private static final String CAPABILITY_LABELS_SUFFIX = ".label.selector";

    @Inject
    Config config;

    @ConfigProperty(name = "halos.label.selector", defaultValue = "managedby=halos")
    String halOsLabelSelector;

    @Inject
    @All
    List<Capability> capabilities;

    private final Map<Capability, String> labelSelectors;

    public Capabilities() {
        labelSelectors = new HashMap<>();
    }

    @PostConstruct
    void init() {
        for (Capability capability : capabilities) {
            String propertyName = CAPABILITY_LABELS_PREFIX + capability.id() + CAPABILITY_LABELS_SUFFIX;
            Optional<String> value = config.getOptionalValue(propertyName, String.class);
            if (value.isPresent() && value.get().length() != 0) {
                labelSelectors.put(capability, halOsAnd(value.get()));
            } else {
                Log.errorf(
                        "No label selector configured for capability %s. No services will be discovered for that capability! Please configure a label selector using the key '%s%s%s'",
                        capability, CAPABILITY_LABELS_PREFIX, capability.id(), CAPABILITY_LABELS_SUFFIX);
            }
        }
    }

    private String halOsAnd(final String labelSelector) {
        return halOsLabelSelector + "," + labelSelector;
    }

    public Map<Capability, String> labelSelectors() {
        return unmodifiableMap(labelSelectors);
    }
}
