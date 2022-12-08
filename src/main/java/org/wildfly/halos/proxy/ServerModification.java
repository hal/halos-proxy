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

public record ServerModification(Modification modification, Server server) {

    static ServerModification from(final Server current, final Server update) {
        if (current == null && update != null) {
            return new ServerModification(Modification.ADDED, update);
        } else if (current != null && update == null) {
            return new ServerModification(Modification.REMOVED, current);
        } else if (current != null && update != null) {
            return switch (current.connectionStatus()) {
                case CONNECTED -> switch (update.connectionStatus()) {
                    case CONNECTED -> new ServerModification(Modification.UPDATED, update);
                    case NO_PODS -> new ServerModification(Modification.DISCONNECTED, update);
                    case ERROR -> new ServerModification(Modification.ERROR, update);
                };
                case NO_PODS -> switch (update.connectionStatus()) {
                    case CONNECTED -> new ServerModification(Modification.CONNECTED, update);
                    case NO_PODS -> new ServerModification(Modification.UPDATED, update);
                    case ERROR -> new ServerModification(Modification.ERROR, update);

                };
                case ERROR -> switch (update.connectionStatus()) {
                    case CONNECTED -> new ServerModification(Modification.CONNECTED, update);
                    case NO_PODS -> new ServerModification(Modification.DISCONNECTED, update);
                    case ERROR -> new ServerModification(Modification.UPDATED, update);
                };

            };
        } else {
            throw new IllegalStateException("Illegal state in ServerModification.from(current: null, update: null)");
        }
    }
}
