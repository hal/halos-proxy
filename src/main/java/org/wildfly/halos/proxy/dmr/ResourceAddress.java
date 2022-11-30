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

import java.util.Iterator;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Represents a fully qualified DMR address ready to be put into a DMR operation. The address consists of 0-n segments with a
 * name and a value for each segment.
 */
public class ResourceAddress extends ModelNode {

    /** @return the empty (root) address */
    public static ResourceAddress root() {
        // Do not replace this with a static constant! In most cases the returned address is modified somehow.
        return new ResourceAddress();
    }

    /** Creates a new resource address from the specified string. */
    public static ResourceAddress from(final String address) {
        if (address == null) {
            throw new IllegalArgumentException("Address must not be null");
        }

        ResourceAddress ra;
        if (address.trim().length() == 0) {
            ra = ResourceAddress.root();
        } else {
            String safeAddress = address.startsWith("/") ? address.substring(1) : address;
            if (safeAddress.length() == 0) {
                ra = ResourceAddress.root();
            } else if (!safeAddress.contains("/")) {
                ra = new ResourceAddress();
                String[] parts = safeAddress.split("=");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Malformed address: " + address);
                }
                ra.add(parts[0], parts[1]);
            } else {
                ra = new ResourceAddress();
                String[] parts = safeAddress.split("/");
                if (parts.length == 0) {
                    throw new IllegalArgumentException("Malformed address: " + address);
                }
                for (String part : parts) {
                    String[] kv = part.split("=");
                    if (kv.length != 2) {
                        throw new IllegalArgumentException("Malformed part '" + part + "' in address: " + address);
                    }
                    ra.add(kv[0], kv[1]);
                }
            }
        }
        return ra;
    }

    public ResourceAddress() {
        setEmptyList();
    }

    public ResourceAddress(final ModelNode address) {
        set(address);
    }

    /**
     * Adds the specified segment to this address.
     *
     * @param propertyName the property name
     * @param propertyValue the property value
     *
     * @return this address with the specified segment added
     */
    public ResourceAddress add(final String propertyName, final String propertyValue) {
        add().set(propertyName, propertyValue);
        return this;
    }

    /**
     * Adds the specified address to this address.
     *
     * @param address The address to add.
     *
     * @return this address with the specified address added
     */
    public ResourceAddress add(final ResourceAddress address) {
        if (address != null) {
            for (Property property : address.asPropertyList()) {
                add(property.getName(), property.getValue().asString());
            }
        }
        return this;
    }

    /** @return the number of segments. */
    public int size() {
        return isDefined() ? asList().size() : 0;
    }

    /** @return whether this address is empty. */
    public boolean isEmpty() {
        return size() == 0;
    }

    /** @return the address as string */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (isDefined()) {
            builder.append("/");
            for (Iterator<Property> iterator = asPropertyList().iterator(); iterator.hasNext();) {
                Property segment = iterator.next();
                builder.append(segment.getName()).append("=").append(segment.getValue().asString());
                if (iterator.hasNext()) {
                    builder.append("/");
                }
            }
        }
        return builder.toString();
    }
}
