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

import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.ADDRESS;
import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.OP;

import java.util.Iterator;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Represents a DMR operation.
 */
public class Operation extends ModelNode {

    private final String name;
    private final ResourceAddress address;
    private final ModelNode parameter;

    private Operation(final Builder builder) {
        this.name = builder.name;
        this.address = builder.address;
        this.parameter = builder.parameter == null ? new ModelNode() : builder.parameter;

        set(this.parameter.clone());
        get(OP).set(name);
        get(ADDRESS).set(address);
    }

    private boolean hasParameter() {
        return parameter.isDefined() && !parameter.asList().isEmpty();
    }

    /**
     * @return the string representation of the operation as used in the CLI
     */
    @Override
    public String toString() {
        return asCli();
    }

    /**
     * @return the string representation of the operation as used in the CLI
     */
    public String asCli() {
        StringBuilder builder = new StringBuilder();
        if (address.isDefined() && !address.asList().isEmpty()) {
            builder.append(address);
        }
        builder.append(":").append(name);
        if (hasParameter()) {
            builder.append("(");
            for (Iterator<Property> iterator = parameter.asPropertyList().iterator(); iterator.hasNext();) {
                Property p = iterator.next();
                builder.append(p.getName()).append("=").append(p.getValue().asString());
                if (iterator.hasNext()) {
                    builder.append(",");
                }
            }
            builder.append(")");
        }
        return builder.toString();
    }

    /**
     * @return the name of the operation
     */
    public String getName() {
        return get(OP).asString();
    }

    /**
     * @return the address of the operation
     */
    public ResourceAddress getAddress() {
        return address;
    }

    /**
     * @return the parameters of the operation
     */
    public ModelNode getParameter() {
        return parameter;
    }

    /**
     * A builder for operations.
     */
    public static class Builder {

        private final String name;
        private final ResourceAddress address;
        private final ModelNode parameter;

        public Builder(final ResourceAddress address, final String name) {
            this.address = address;
            this.name = name;
            this.parameter = new ModelNode();
        }

        public Builder param(final String name, final boolean value) {
            parameter.get(name).set(value);
            return this;
        }

        public Builder param(final String name, final int value) {
            parameter.get(name).set(value);
            return this;
        }

        public Builder param(final String name, final long value) {
            parameter.get(name).set(value);
            return this;
        }

        public Builder param(final String name, final double value) {
            parameter.get(name).set(value);
            return this;
        }

        public Builder param(final String name, final String value) {
            parameter.get(name).set(value);
            return this;
        }

        public Builder param(final String name, final String[] values) {
            for (String value : values) {
                parameter.get(name).add(value);
            }
            return this;
        }

        public Builder param(final String name, final ModelNode value) {
            parameter.get(name).set(value);
            return this;
        }

        /**
         * @return builds and returns the operation
         */
        public Operation build() {
            return new Operation(this);
        }
    }
}
