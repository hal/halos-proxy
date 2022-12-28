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
package org.wildfly.halos.proxy.wildfly;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.halos.proxy.wildfly.dmr.Composite;
import org.wildfly.halos.proxy.wildfly.dmr.CompositeResult;
import org.wildfly.halos.proxy.wildfly.dmr.Operation;
import org.wildfly.halos.proxy.wildfly.dmr.ResourceAddress;

import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.ATTRIBUTES_ONLY;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.NAME;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.wildfly.halos.proxy.wildfly.dmr.ModelDescriptionConstants.RESULT;

@ApplicationScoped
class StatusRepository {

    Status status(ModelControllerClient client, WildFlyServer server) {
        ResourceAddress osAddress = ResourceAddress.from("core-service=platform-mbean").add("type", "operating-system");
        ResourceAddress runtimeAddress = ResourceAddress.from("core-service=platform-mbean").add("type", "runtime");
        ResourceAddress memoryAddress = ResourceAddress.from("core-service=platform-mbean").add("type", "memory");
        ResourceAddress threadingAddress = ResourceAddress.from("core-service=platform-mbean").add("type", "threading");

        Operation osOp = new Operation.Builder(osAddress, READ_RESOURCE_OPERATION).param(ATTRIBUTES_ONLY, true)
                .param(INCLUDE_RUNTIME, true).build();
        Operation runtimeOp = new Operation.Builder(runtimeAddress, READ_RESOURCE_OPERATION).param(ATTRIBUTES_ONLY, true)
                .param(INCLUDE_RUNTIME, true).build();
        Operation memoryOp = new Operation.Builder(memoryAddress, READ_RESOURCE_OPERATION).param(ATTRIBUTES_ONLY, true)
                .param(INCLUDE_RUNTIME, true).build();
        Operation threadingOp = new Operation.Builder(threadingAddress, READ_RESOURCE_OPERATION).param(ATTRIBUTES_ONLY, true)
                .param(INCLUDE_RUNTIME, true).build();
        Composite composite = new Composite(osOp, runtimeOp, memoryOp, threadingOp);

        try {
            ModelNode payload = client.execute(composite);
            CompositeResult compositeResult = new CompositeResult(payload.get(RESULT));
            if (compositeResult.isFailure()) {
                throw new ManagementInterfaceException(
                        String.format("Operation %s failed for %s", composite.asCli(), server.name()));
            } else if (composite.isEmpty()) {
                throw new ManagementInterfaceException(
                        String.format("Operation %s for %s returned an empty result!" + composite.asCli(), server.name()));
            } else {
                // os
                ModelNode osNode = compositeResult.step(0).get(RESULT);
                String osName = osNode.get(NAME).asString();
                String osVersion = osNode.get("version").asString();
                int processors = osNode.get("available-processors").asInt();
                Status.OperatingSystem os = new Status.OperatingSystem(osName, osVersion, processors);

                // runtime
                ModelNode runtimeNode = compositeResult.step(1).get(RESULT);
                String jvmName = runtimeNode.get("vm-name").asString();
                String specVersion = runtimeNode.get("spec-version").asString();
                long uptime = runtimeNode.get("uptime").asLong();
                Status.Runtime runtime = new Status.Runtime(jvmName, specVersion, uptime);

                // memory
                ModelNode memoryNode = compositeResult.step(2).get(RESULT);
                ModelNode heapMemoryNode = memoryNode.get("heap-memory-usage");
                long usedHeap = heapMemoryNode.get("used").asLong() / 1024 / 1024;
                long committedHeap = heapMemoryNode.get("committed").asLong() / 1024 / 1024;
                long maxHeap = heapMemoryNode.get("max").asLong() / 1024 / 1024;
                Status.Memory heap = new Status.Memory(usedHeap, committedHeap, maxHeap);

                ModelNode nonHeapMemoryNode = memoryNode.get("non-heap-memory-usage");
                long usedNonHeap = nonHeapMemoryNode.get("used").asLong() / 1024 / 1024;
                long committedNonHeap = nonHeapMemoryNode.get("committed").asLong() / 1024 / 1024;
                long maxNonHeap = nonHeapMemoryNode.get("max").asLong() / 1024 / 1024;
                Status.Memory nonHeap = new Status.Memory(usedNonHeap, committedNonHeap, maxNonHeap);

                // threads
                ModelNode threadsNode = compositeResult.step(3).get(RESULT);
                long threadCount = threadsNode.get("thread-count").asLong();
                long daemonCount = threadsNode.get("daemon-thread-count").asLong();
                Status.Threads threads = new Status.Threads(threadCount, daemonCount);

                return new Status(os, runtime, heap, nonHeap, threads);
            }
        } catch (IOException e) {
            throw new ManagementInterfaceException(
                    String.format("Operation %s failed for %s: %s", composite.asCli(), server.name(), e.getMessage()));
        }
    }
}
