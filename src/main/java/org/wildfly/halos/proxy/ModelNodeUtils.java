package org.wildfly.halos.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.jboss.dmr.ModelNode;

final class ModelNodeUtils {

    static String base64(ModelNode modelNode) {
        String base64 = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            modelNode.writeBase64(out);
            base64 = out.toString();
        } catch (IOException ignored) {
            // ByteArrayOutputStream should not throw exceptions
        }
        return base64;
    }

    private ModelNodeUtils() {
    }
}
