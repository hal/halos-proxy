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

import org.wildfly.halos.proxy.dmr.RunningMode;
import org.wildfly.halos.proxy.dmr.ServerState;
import org.wildfly.halos.proxy.dmr.SuspendState;

import de.skuzzle.semantic.Version;

/**
 * A WildFly instance with a valid connection to the management interface.
 */
public record Instance(String containerId, String serverId, String serverName, String productName, Version productVersion,
        Version coreVersion, Version managementVersion, RunningMode runningMode, ServerState serverState,
        SuspendState suspendState) {
}
