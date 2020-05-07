package org.wildfly.halos.proxy;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;

import static org.wildfly.halos.proxy.ManagementResource.DMR_ENCODED;

@Path("/v1/management")
@Consumes(DMR_ENCODED)
@Produces(DMR_ENCODED)
public class ManagementResource {

    static final String DMR_ENCODED = "application/dmr-encoded";

    @Inject
    Instances instances;

    @POST
    public Response execute(InputStream inputStream) {
        if (instances.isEmpty()) {
            return Response.status(Status.NOT_FOUND).entity("No instances registered.").build();
        } else {
            try {
                ModelNode modelNode = ModelNode.fromBase64(inputStream);
                Operation operation = Operation.Factory.create(modelNode);
                ModelNode result = instances.execute(operation);
                return Response.ok(result).build();
            } catch (IOException e) {
                return Response.serverError().entity("Unable to execute operation: " + e.getMessage()).build();
            }
        }
    }

    @POST
    @Path("/{name}")
    public Response executeSingle(@PathParam("name") String name, InputStream inputStream) {
        if (instances.hasInstance(name)) {
            try {
                ModelNode modelNode = ModelNode.fromBase64(inputStream);
                Operation operation = Operation.Factory.create(modelNode);
                ModelNode result = instances.executeSingle(name, operation);
                if (result != null) {
                    return Response.ok(result).build();
                } else {
                    return Response.status(Status.NOT_FOUND).entity("Instance " + name + " not found.").build();
                }
            } catch (IOException e) {
                return Response.serverError().entity("Unable to execute operation: " + e.getMessage()).build();
            }
        } else {
            return Response.status(Status.NOT_FOUND).entity("Instance " + name + " not found").build();
        }
    }
}
