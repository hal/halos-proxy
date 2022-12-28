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

import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.Test;

import static org.jboss.as.controller.client.helpers.ClientConstants.ADD;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OperationTest {

    @Test
    public void fromBuilder() {
        ResourceAddress address = new ResourceAddress().add("subsystem", "datasources").add("data-source", "foo");

        Operation operation = new Operation.Builder(address, ADD).param("jndi-name", "java:/bar").build();

        assertEquals(ADD, operation.getName());
        assertEquals("/subsystem=datasources/data-source=foo", operation.getAddress().toString());

        ModelNode parameter = new ModelNode();
        parameter.get("jndi-name").set("java:/bar");
        assertEquals(parameter, operation.getParameter());
        assertEquals("/subsystem=datasources/data-source=foo:add(jndi-name=java:/bar)", operation.asCli());
    }
}
