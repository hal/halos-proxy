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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

class Difference<T> {

    private final Set<T> added;
    private final Set<T> removed;

    Difference(final Set<T> current, final Set<T> update, Function<T, String> idFunction) {
        Map<String, T> currentMap = current.stream().collect(toMap(idFunction, identity()));
        Map<String, T> updateMap = update.stream().collect(toMap(idFunction, identity()));
        Set<String> currentIds = new HashSet<>(currentMap.keySet());
        Set<String> updateIds = new HashSet<>(updateMap.keySet());

        updateIds.removeAll(currentMap.keySet());
        added = updateIds.stream().map(updateMap::get).collect(toSet());

        currentIds.removeAll(updateMap.keySet());
        removed = currentIds.stream().map(currentMap::get).collect(toSet());
    }

    Set<T> added() {
        return added;
    }

    Set<T> removed() {
        return removed;
    }
}
