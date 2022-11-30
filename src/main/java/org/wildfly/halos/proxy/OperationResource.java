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

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.halos.proxy.dmr.DispatchException;
import org.wildfly.halos.proxy.dmr.Dispatcher;
import org.wildfly.halos.proxy.dmr.Operation;

import static org.wildfly.halos.proxy.dmr.ModelNodeMessageBodyWriter.DMR_ENCODED;

@Path("/api/v1/operation")
@Consumes(DMR_ENCODED)
@Produces(DMR_ENCODED)
public class OperationResource {

    @Inject
    InstanceRepository instanceRepository;

    @POST
    @Path("/{id}")
    public Response executeSingle(@PathParam("id") final String id, final InputStream inputStream) {
        ModelControllerClient client = instanceRepository.getClient(id);
        if (client != null) {
            try {
                ModelNode modelNode = ModelNode.fromBase64(inputStream);
                Operation operation = new Operation(modelNode);
                ModelNode result = new Dispatcher(client).execute(operation);
                return Response.ok(result).build();
            } catch (DispatchException e) {
                return Response.serverError().entity(e.getMessage()).build();
            } catch (IOException e) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(String.format("Invalid operation payload: %s", e.getMessage())).build();
            }
        } else {
            return Response.status(Status.NOT_FOUND).entity("Instance " + id + " not found.").build();
        }
    }
}
