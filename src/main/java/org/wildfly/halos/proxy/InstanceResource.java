package org.wildfly.halos.proxy;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.smallrye.mutiny.Multi;

import static java.util.stream.Collectors.joining;

@Path("/v1/instance")
@Produces(MediaType.TEXT_PLAIN)
public class InstanceResource {

    @Inject Instances instances;

    @GET
    public String instanceNames() {
        return instances.instances().stream()
                .map(instance -> instance.name)
                .collect(joining(","));
    }

    @GET
    @Path("/subscribe")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> modifications() {
        return instances.modifications().map(InstanceModification::toString);
    }

    @POST
    public Response register(Instance instance) {
        if (instances.hasInstance(instance.name)) {
            return Response.status(Status.NOT_MODIFIED).build();
        } else {
            try {
                instances.register(instance);
                return Response.status(Status.CREATED).entity(instance).build();
            } catch (ManagementException e) {
                return Response.serverError().entity(e.getMessage()).build();
            }
        }
    }

    @DELETE
    @Path("/{name}")
    public Response unregister(@PathParam("name") String name) {
        if (instances.hasInstance(name)) {
            try {
                instances.unregister(name);
                return Response.noContent().build();
            } catch (ManagementException e) {
                return Response.serverError().entity(e.getMessage()).build();
            }
        } else {
            return Response.status(Status.NOT_FOUND).entity("No instance found for '" + name + "'.").build();
        }
    }
}
