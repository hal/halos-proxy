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

/**
 * String constants frequently used in model descriptions and DMR operations.
 *
 * @author Brian Stansberry
 * @author Harald Pehl
 */
public interface ModelDescriptionConstants {

    // KEEP THESE IN ALPHABETICAL ORDER!
    String ADDRESS = "address";
    String ATTRIBUTES_ONLY = "attributes-only";
    String FAILURE_DESCRIPTION = "failure-description";
    String INCLUDE_RUNTIME = "include-runtime";
    String MANAGEMENT_MAJOR_VERSION = "management-major-version";
    String MANAGEMENT_MICRO_VERSION = "management-micro-version";
    String MANAGEMENT_MINOR_VERSION = "management-minor-version";
    String NAME = "name";
    String OP = "operation";
    String OUTCOME = "outcome";
    String PRODUCT_NAME = "product-name";
    String PRODUCT_VERSION = "product-version";
    String READ_RESOURCE_OPERATION = "read-resource";
    String RELEASE_VERSION = "release-version";
    String RUNNING_MODE = "running-mode";
    String SERVER_STATE = "server-state";
    String SUSPEND_STATE = "suspend-state";
    String UUID = "uuid";
}
