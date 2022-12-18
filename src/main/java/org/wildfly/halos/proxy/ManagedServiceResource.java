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

    @GET
    @Path("/modifications")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ManagedServiceModification> modifications() {
        return managedServiceRepository.modifications();
    }

    @PUT
    @Path("/connect/{id}/{capability}")
    public Response connect(@PathParam("id") final String managedServiceId,
            @PathParam("capability") final String capabilityId) {
        ManagedService managedService = managedServiceRepository.managedService(managedServiceId);
        CapabilityCollector capabilityCollector = capabilityRepository.collector(capabilityId);
        if (managedService != null && capabilityCollector != null) {
            if (managedService.status() != ManagedService.Status.CONNECTED) {
                managedServiceRepository.connect(managedService, capabilityCollector);
                return Response.ok().build();
            } else {
                return Response.noContent().build();
            }
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
