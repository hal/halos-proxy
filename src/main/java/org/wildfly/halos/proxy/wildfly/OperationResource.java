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
package org.wildfly.halos.proxy.wildfly;

import java.io.InputStream;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Path("/api/v1/wildfly/operation")
public class OperationResource {

    @Inject
    WildFlyServerRepository repository;

    //
    // @POST
    @Path("/{id}")
    public Response executeSingle(@PathParam("id") final String id, final InputStream inputStream) {
        /*
         * ModelControllerClient client = serverRepository.client(id); if (client != null) { try { ModelNode modelNode =
         * ModelNode.fromBase64(inputStream); Operation operation = new Operation(modelNode); ModelNode result = new
         * Dispatcher(client).execute(operation); return Response.ok(result).build(); } catch (DispatchException e) { return
         * Response.serverError().entity(e.getMessage()).build(); } catch (IOException e) { return
         * Response.status(Status.BAD_REQUEST) .entity(String.format("Invalid operation payload: %s", e.getMessage())).build();
         * } } else { return Response.status(Status.NOT_FOUND).entity("Instance " + id + " not found.").build(); }
         */
        return Response.status(Status.NOT_FOUND).entity("Instance " + id + " not found.").build();
    }
}
