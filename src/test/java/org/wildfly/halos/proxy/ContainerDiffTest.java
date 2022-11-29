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

class ContainerDiffTest {

    @Test
    void empty() {
        ContainerDiff diff = new ContainerDiff(emptySet(), emptySet());

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void unchangedOne() {
        ContainerDiff diff = new ContainerDiff(Set.of(container(1)), Set.of(container(1)));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void unchangedMany() {
        ContainerDiff diff = new ContainerDiff(Set.of(container(1), container(2)), Set.of(container(1), container(2)));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void addedOne() {
        ContainerDiff diff = new ContainerDiff(emptySet(), Set.of(container(1)));

        assertEquals(1, diff.added().size());
        assertEquals(container(1), diff.added().iterator().next());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void addedMany() {
        ContainerDiff diff = new ContainerDiff(Set.of(container(1), container(2)),
                Set.of(container(1), container(2), container(3), container(4)));

        assertEquals(2, diff.added().size());
        assertTrue(diff.added().contains(container(3)));
        assertTrue(diff.added().contains(container(4)));
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void removedOne() {
        Container container = container(1);
        ContainerDiff diff = new ContainerDiff(Set.of(container), emptySet());

        assertTrue(diff.added().isEmpty());
        assertEquals(1, diff.removed().size());
        assertEquals(container, diff.removed().iterator().next());
    }

    @Test
    void removedMany() {
        ContainerDiff diff = new ContainerDiff(Set.of(container(1), container(2), container(3), container(4)),
                Set.of(container(1), container(2)));

        assertTrue(diff.added().isEmpty());
        assertEquals(2, diff.removed().size());
        assertTrue(diff.removed().contains(container(3)));
        assertTrue(diff.removed().contains(container(4)));
    }

    @Test
    void addedRemoved() {
        ContainerDiff diff = new ContainerDiff(Set.of(container(1), container(2)), Set.of(container(3), container(4)));

        assertEquals(2, diff.added().size());
        assertTrue(diff.added().contains(container(3)));
        assertTrue(diff.added().contains(container(4)));
        assertEquals(2, diff.removed().size());
        assertTrue(diff.removed().contains(container(1)));
        assertTrue(diff.removed().contains(container(2)));
    }

    Container container(int data) {
        return new Container(String.valueOf(data), String.format("%1$d.%1$d.%1$d.%1$d", data), data);
    }
}