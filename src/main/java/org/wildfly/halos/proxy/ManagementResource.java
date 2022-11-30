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

import static org.wildfly.halos.proxy.dmr.ModelNodeMessageBodyWriter.DMR_ENCODED;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Path("/api/v1/management")
@Consumes(DMR_ENCODED)
@Produces(DMR_ENCODED)
public class ManagementResource {

    @POST
    public Response execute(final InputStream inputStream) {
        return Response.status(Status.NOT_FOUND).entity("No instances registered.").build();
        // if (instances.isEmpty()) {
        // return Response.status(Status.NOT_FOUND).entity("No instances registered.").build();
        // } else {
        // try {
        // ModelNode modelNode = ModelNode.fromBase64(inputStream);
        // Operation operation = Operation.Factory.create(modelNode);
        // ModelNode result = instances.execute(operation);
        // return Response.ok(result).build();
        // } catch (IOException e) {
        // return Response.serverError().entity("Unable to execute operation: " + e.getMessage()).build();
        // }
        // }
    }

    @POST
    @Path("/{name}")
    public Response executeSingle(@PathParam("name") final String name, final InputStream inputStream) {
        return Response.status(Status.NOT_FOUND).entity("Instance " + name + " not found.").build();
        // if (instances.hasInstance(name)) {
        // try {
        // ModelNode modelNode = ModelNode.fromBase64(inputStream);
        // Operation operation = Operation.Factory.create(modelNode);
        // ModelNode result = instances.executeSingle(name, operation);
        // if (result != null) {
        // return Response.ok(result).build();
        // } else {
        // return Response.status(Status.NOT_FOUND).entity("Instance " + name + " not found.").build();
        // }
        // } catch (IOException e) {
        // return Response.serverError().entity("Unable to execute operation: " + e.getMessage()).build();
        // }
        // } else {
        // return Response.status(Status.NOT_FOUND).entity("Instance " + name + " not found").build();
        // }
    }
}
