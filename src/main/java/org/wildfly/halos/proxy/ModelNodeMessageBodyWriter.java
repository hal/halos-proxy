package org.wildfly.halos.proxy;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.jboss.dmr.ModelNode;

import static org.wildfly.halos.proxy.ManagementResource.DMR_ENCODED;

@Provider
@Produces(DMR_ENCODED)
public class ModelNodeMessageBodyWriter implements MessageBodyWriter<ModelNode> {

    @Override
    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return type == ModelNode.class;
    }

    @Override
    public void writeTo(ModelNode modelNode, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream)
            throws IOException, WebApplicationException {
        modelNode.writeBase64(outputStream);
    }
}
