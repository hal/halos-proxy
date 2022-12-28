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

import java.util.Collection;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestStreamElementType;
import org.wildfly.halos.api.CapabilityExtension;
import org.wildfly.halos.api.Connection;
import org.wildfly.halos.api.ManagedService;
import org.wildfly.halos.api.ManagedServiceModification;

import io.smallrye.mutiny.Multi;

@Path("/api/v1/services")
@Produces(MediaType.APPLICATION_JSON)
public class ManagedServiceResource {

    @Inject ManagedServiceRepository managedServiceRepository;
    @Inject CapabilityRepository capabilityRepository;

    @GET
    public Collection<ManagedService> services() {
        return managedServiceRepository.managedServices();
    }

    @PUT
    @Path("/{name}/connect/{capability}")
    public Response connect(@PathParam("name") final String managedServiceName,
            @PathParam("capability") final String capability) {
        ManagedService managedService = managedServiceRepository.managedService(managedServiceName);
        CapabilityExtension capabilityExtension = capabilityRepository.extension(capability);
        if (managedService != null && capabilityExtension != null) {
            if (managedService.connection().status() != Connection.Status.CONNECTED) {
                managedServiceRepository.connect(managedService, capabilityExtension);
                return Response.ok().build();
            } else {
                return Response.notModified().build();
            }
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/modifications")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ManagedServiceModification> modifications() {
        return managedServiceRepository.modifications();
    }
}
