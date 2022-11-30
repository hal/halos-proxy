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

import java.util.Set;

import org.junit.jupiter.api.Test;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementInterfaceDiffTest {

    @Test
    void empty() {
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(emptySet(), emptySet());

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void unchangedOne() {
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(Set.of(managementInterface(1)),
                Set.of(managementInterface(1)));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void unchangedMany() {
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(Set.of(managementInterface(1), managementInterface(2)),
                Set.of(managementInterface(1), managementInterface(2)));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void addedOne() {
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(emptySet(), Set.of(managementInterface(1)));

        assertEquals(1, diff.added().size());
        assertEquals(managementInterface(1), diff.added().iterator().next());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void addedMany() {
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(Set.of(managementInterface(1), managementInterface(2)),
                Set.of(managementInterface(1), managementInterface(2), managementInterface(3), managementInterface(4)));

        assertEquals(2, diff.added().size());
        assertTrue(diff.added().contains(managementInterface(3)));
        assertTrue(diff.added().contains(managementInterface(4)));
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void removedOne() {
        ManagementInterface managementInterface = managementInterface(1);
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(Set.of(managementInterface), emptySet());

        assertTrue(diff.added().isEmpty());
        assertEquals(1, diff.removed().size());
        assertEquals(managementInterface, diff.removed().iterator().next());
    }

    @Test
    void removedMany() {
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(
                Set.of(managementInterface(1), managementInterface(2), managementInterface(3), managementInterface(4)),
                Set.of(managementInterface(1), managementInterface(2)));

        assertTrue(diff.added().isEmpty());
        assertEquals(2, diff.removed().size());
        assertTrue(diff.removed().contains(managementInterface(3)));
        assertTrue(diff.removed().contains(managementInterface(4)));
    }

    @Test
    void addedRemoved() {
        ManagementInterfaceDiff diff = new ManagementInterfaceDiff(Set.of(managementInterface(1), managementInterface(2)),
                Set.of(managementInterface(3), managementInterface(4)));

        assertEquals(2, diff.added().size());
        assertTrue(diff.added().contains(managementInterface(3)));
        assertTrue(diff.added().contains(managementInterface(4)));
        assertEquals(2, diff.removed().size());
        assertTrue(diff.removed().contains(managementInterface(1)));
        assertTrue(diff.removed().contains(managementInterface(2)));
    }

    ManagementInterface managementInterface(final int data) {
        return new ManagementInterface(String.valueOf(data), String.valueOf(data), String.format("%1$d.%1$d.%1$d.%1$d", data),
                data);
    }
}
