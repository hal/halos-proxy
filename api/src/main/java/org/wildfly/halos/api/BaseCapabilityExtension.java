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
package org.wildfly.halos.api;

import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;

import io.quarkus.logging.Log;

public abstract class BaseCapabilityExtension implements CapabilityExtension {

    private static final String CAPABILITY_LABELS_PREFIX = "halos.capability.";
    private static final String CAPABILITY_LABELS_SUFFIX = ".label.selector";

    @Inject
    Config config;
    private String labelSelector;

    @PostConstruct
    void validate() {
        String propertyName = CAPABILITY_LABELS_PREFIX + capability().name() + CAPABILITY_LABELS_SUFFIX;
        Optional<String> value = config.getOptionalValue(propertyName, String.class);
        if (value.isPresent() && value.get().length() != 0) {
            labelSelector = value.get();
        } else {
            Log.warnf(
                    "No label selector configured for capability %s. No services will be discovered for that capability! Please configure a label selector using the key '%s%s%s'",
                    capability().name(), CAPABILITY_LABELS_PREFIX, capability().name(), CAPABILITY_LABELS_SUFFIX);
        }
    }

    @Override
    public String labelSelector() {
        return labelSelector;
    }
}
