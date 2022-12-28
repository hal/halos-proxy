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
package org.wildfly.halos.capability.wildfly;

import javax.inject.Singleton;
import javax.json.bind.JsonbConfig;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

import io.quarkus.jsonb.JsonbConfigCustomizer;

import de.skuzzle.semantic.Version;

@Singleton
class VersionSerializerRegistrationCustomizer implements JsonbConfigCustomizer {

    static class VersionSerializer implements JsonbSerializer<Version> {

        @Override
        public void serialize(final Version version, final JsonGenerator generator, final SerializationContext ctx) {
            generator.write(version.toString());
        }
    }

    @Override
    public void customize(final JsonbConfig config) {
        config.withSerializers(new VersionSerializer());
    }
}
