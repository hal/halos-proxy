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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import org.jboss.dmr.ModelNode;

@Provider
@Produces(ModelNodeMessageBodyWriter.DMR_ENCODED)
public class ModelNodeMessageBodyWriter implements MessageBodyWriter<ModelNode> {

    public static final String DMR_ENCODED = "application/dmr-encoded";

    @Override
    public boolean isWriteable(final Class<?> aClass, final Type type, final Annotation[] annotations,
            final MediaType mediaType) {
        return type == ModelNode.class;
    }

    @Override
    public void writeTo(final ModelNode modelNode, final Class<?> aClass, final Type type, final Annotation[] annotations,
            final MediaType mediaType, final MultivaluedMap<String, Object> multivaluedMap, final OutputStream outputStream)
            throws IOException, WebApplicationException {
        modelNode.writeBase64(outputStream);
    }
}
