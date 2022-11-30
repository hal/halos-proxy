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

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

import static org.wildfly.halos.proxy.dmr.ModelDescriptionConstants.RESULT;

public class Dispatcher {

    private final ModelControllerClient client;

    public Dispatcher(final ModelControllerClient client) {
        this.client = client;
    }

    /**
     * Executes the operation and returns the {@linkplain ModelDescriptionConstants#RESULT} node of the payload. Throws an
     * exception if the operation failed (operation error) or if the model controller client throws an exception (technical
     * error)
     */
    public ModelNode execute(final Operation operation) throws DispatchException {
        try {
            ModelNode payload = client.execute(operation);
            if (ModelNodeHelper.isFailure(payload)) {
                throw new DispatchException(String.format("Operation %s failed: %s", operation.asCli(),
                        ModelNodeHelper.getFailureDescription(payload)));
            }
            return payload.get(RESULT);
        } catch (IOException e) {
            throw new DispatchException(String.format("Error executing operation %s: %s", operation.asCli(), e.getMessage()),
                    e);
        }
    }
}
