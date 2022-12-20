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

import java.time.LocalDateTime;

public record Connection(Status status, LocalDateTime timestamp, String message) {

    public enum Status {
        PENDING, CONNECTED, FAILED
    }

    public static Connection pending() {
        return new Connection(Status.PENDING, LocalDateTime.now(), null);
    }

    public static Connection connected() {
        return new Connection(Status.CONNECTED, LocalDateTime.now(), null);
    }

    public static Connection failed(final String reason) {
        return new Connection(Status.FAILED, LocalDateTime.now(), reason);
    }
}
