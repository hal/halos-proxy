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

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.as.controller.client.ModelControllerClient;

@Path("/api/v1/wildfly/servers")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @Inject WildFlyServerRepository serverRepository;
    @Inject StatusRepository statusRepository;

    @GET
    @Path("/{serverName}/status")
    public Response status(@PathParam("serverName") final String serverName) {
        WildFlyServer server = serverRepository.wildFlyServer(serverName);
        ModelControllerClient client = serverRepository.client(serverName);
        if (client != null && server != null) {
            Status status = statusRepository.status(client, server);
            return Response.ok(status).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
