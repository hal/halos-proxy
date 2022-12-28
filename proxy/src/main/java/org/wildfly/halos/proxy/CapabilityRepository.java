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

import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.quarkus.arc.All;

import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class CapabilityRepository {

    @Inject
    @All List<CapabilityExtension> extensions;

    List<CapabilityExtension> extensions() {
        return List.copyOf(extensions);
    }

    CapabilityExtension extension(final String name) {
        for (CapabilityExtension extension : extensions) {
            if (name.equals(extension.capability().name())) {
                return extension;
            }
        }
        return null;
    }

    Set<Capability> capabilities() {
        return extensions.stream().map(CapabilityExtension::capability).collect(toSet());
    }
}
