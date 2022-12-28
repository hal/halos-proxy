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
package org.wildfly.halos.capability.wildfly.dmr;

import java.util.ArrayList;
import java.util.List;

import org.jboss.dmr.Property;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResourceAddressTest {

    @Test
    public void fromNull() {
        assertThrows(IllegalArgumentException.class, () -> ResourceAddress.from(null));
    }

    @Test
    public void fromEmpty() {
        ResourceAddress address = ResourceAddress.from("");
        assertEquals(ResourceAddress.root(), address);
    }

    @Test
    public void fromRoot() {
        ResourceAddress address = ResourceAddress.from("/");
        assertEquals(ResourceAddress.root(), address);
    }

    @Test
    public void fromAddressWithSlash() {
        ResourceAddress address = ResourceAddress.from("/subsystem=ee");
        assertFalse(address.isEmpty());
        assertEquals(1, address.size());
        assertArrayEquals(new String[] { "subsystem", "ee" }, segments(address));
    }

    @Test
    public void fromAddressWithoutSlash() {
        ResourceAddress address = ResourceAddress.from("subsystem=ee");
        assertFalse(address.isEmpty());
        assertEquals(1, address.size());
        assertArrayEquals(new String[] { "subsystem", "ee" }, segments(address));
    }

    @Test
    public void fromAddress() {
        ResourceAddress address = ResourceAddress.from("subsystem=ee/context-service=default");
        assertFalse(address.isEmpty());
        assertEquals(2, address.size());
        assertArrayEquals(new String[] { "subsystem", "ee", "context-service", "default" }, segments(address));
    }

    private String[] segments(final ResourceAddress address) {
        List<String> segments = new ArrayList<>();
        for (Property property : address.asPropertyList()) {
            segments.add(property.getName());
            segments.add(property.getValue().asString());
        }
        return segments.toArray(new String[0]);
    }
}
