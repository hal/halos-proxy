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

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;

import io.quarkus.logging.Log;

public class NoopModelControllerClient implements ModelControllerClient {

    private final String id;
    private final String name;

    public NoopModelControllerClient(final String id, final String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public ModelNode execute(final ModelNode operation) {
        nyi("execute", "ModelNode");
        return new ModelNode();
    }

    @Override
    public ModelNode execute(final Operation operation) {
        nyi("execute", "Operation");
        return new ModelNode();
    }

    @Override
    public ModelNode execute(final ModelNode operation, final OperationMessageHandler messageHandler) {
        nyi("execute", "ModelNode, OperationMessageHandler");
        return new ModelNode();
    }

    @Override
    public ModelNode execute(final Operation operation, final OperationMessageHandler messageHandler) {
        nyi("execute", "Operation, OperationMessageHandler");
        return new ModelNode();
    }

    @Override
    public OperationResponse executeOperation(final Operation operation, final OperationMessageHandler messageHandler) {
        nyi("executeOperation", "ModelNode, OperationMessageHandler");
        return OperationResponse.Factory.createSimple(new ModelNode());
    }

    @Override
    public void close() {
        // noop
    }

    private void nyi(final String operation, final String parameter) {
        Log.errorf("%s(%s) not implemented for %s:%s", operation, parameter, id, name);
    }
}
