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
package org.wildfly.halos.proxy.dmr;

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

/**
 * Static helper methods for dealing with {@link ModelNode}s. Some methods accept a path parameter * separated by "." to get a
 * deeply nested data.
 */
public final class ModelNodeHelper {

    private static final char PATH_SEPARATOR = '.';

    /**
     * Tries to get a deeply nested model node from the specified model node. Nested paths must be separated with ".".
     *
     * @param modelNode The model node to read from
     * @param path A path separated with "."
     * @return The nested node or an empty / undefined model node
     */
    public static ModelNode failSafeGet(final ModelNode modelNode, final String path) {
        ModelNode undefined = new ModelNode();

        if (Strings.emptyToNull(path) != null) {
            Iterable<String> keys = Splitter.on(PATH_SEPARATOR).omitEmptyStrings().trimResults().split(path);
            if (!Iterables.isEmpty(keys)) {
                ModelNode context = modelNode;
                for (String key : keys) {
                    if (context.hasDefined(key)) {
                        context = context.get(key);
                    } else {
                        context = undefined;
                        break;
                    }
                }
                return context;
            }
        }

        return undefined;
    }

    /**
     * Tries to get a deeply nested boolean value from the specified model node. Nested paths must be separated with "/".
     *
     * @param modelNode The model node to read from
     * @param path A path separated with "."
     * @return the boolean value or false.
     */
    public static boolean failSafeBoolean(final ModelNode modelNode, final String path) {
        ModelNode attribute = failSafeGet(modelNode, path);
        return attribute.isDefined() && attribute.asBoolean();
    }

    public static List<ModelNode> failSafeList(final ModelNode modelNode, final String path) {
        ModelNode result = failSafeGet(modelNode, path);
        return result.isDefined() ? result.asList() : Collections.emptyList();
    }

    public static List<Property> failSafePropertyList(final ModelNode modelNode, final String path) {
        ModelNode result = failSafeGet(modelNode, path);
        return result.isDefined() ? result.asPropertyList() : Collections.emptyList();
    }

    public static <T> T getOrDefault(final ModelNode modelNode, final String attribute, final Supplier<T> supplier,
            final T defaultValue) {
        T result = defaultValue;
        if (modelNode != null && modelNode.hasDefined(attribute)) {
            try {
                result = supplier.get();
            } catch (Throwable ignored) {
                result = defaultValue;
            }
        }
        return result;
    }

    /**
     * Looks for the specified attribute and tries to convert it to an enum constant using
     * {@code LOWER_HYPHEN.to(UPPER_UNDERSCORE, modelNode.get(attribute).asString())}.
     */
    public static <E extends Enum<E>> E asEnumValue(final ModelNode modelNode, final String attribute,
            final Function<String, E> valueOf, final E defaultValue) {
        if (modelNode.hasDefined(attribute)) {
            return asEnumValue(modelNode.get(attribute), valueOf, defaultValue);
        }
        return defaultValue;
    }

    public static <E extends Enum<E>> E asEnumValue(final ModelNode modelNodeValue, final Function<String, E> valueOf,
            final E defaultValue) {
        E value = defaultValue;
        String convertedValue = LOWER_HYPHEN.to(UPPER_UNDERSCORE, modelNodeValue.asString());
        try {
            value = valueOf.apply(convertedValue);
        } catch (IllegalArgumentException ignored) {
        }
        return value;
    }

    private ModelNodeHelper() {
    }
}
