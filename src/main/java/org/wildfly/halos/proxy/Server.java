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

import de.skuzzle.semantic.Version;

public record Server(String id, String serverId, String serverName, String productName, Version productVersion,
        Version coreVersion, Version managementVersion, ConnectionStatus connectionStatus, RunningMode runningMode,
        ServerState serverState, SuspendState suspendState) {

    static Server noPods(final String id, final String name) {
        return new Server(id, "", name, "", Version.ZERO, Version.ZERO, Version.ZERO, ConnectionStatus.NO_PODS,
                RunningMode.UNDEFINED, ServerState.UNDEFINED, SuspendState.UNDEFINED);
    }

    static Server connectionError(final String id, final String name) {
        return new Server(id, "", name, "", Version.ZERO, Version.ZERO, Version.ZERO, ConnectionStatus.ERROR,
                RunningMode.UNDEFINED, ServerState.UNDEFINED, SuspendState.UNDEFINED);
    }

    Server withHostAndPort(final String host, final int port) {
        return new Server(id, serverId, serverName, productName, productVersion, coreVersion, managementVersion,
                connectionStatus, runningMode, serverState, suspendState);
    }
}
