/*
 * Copyright 2017 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.integration.NiordIntegration;
import org.niord.core.integration.NiordIntegrationService;
import org.niord.core.integration.vo.NiordIntegrationVo;
import org.niord.core.user.Roles;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing and managing promulgation types.
 */
@Path("/niord-integrations")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.ADMIN)
@SuppressWarnings("unused")
public class NiordIntegrationRestService {

    @Inject
    Logger log;

    @Inject
    NiordIntegrationService niordIntegrationService;

    /** Returns all Niord integration points */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<NiordIntegrationVo> getAllNiordIntegrations() {
        return niordIntegrationService.getAllNiordIntegrations().stream()
                .map(NiordIntegration::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the Niord integration point with the given ID */
    @GET
    @Path("/niord-integration/{id}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public NiordIntegrationVo getNiordIntegration(@PathParam("id") Integer id) {
        return niordIntegrationService.findById(id).toVo();
    }


    /** Creates a new Niord integration point */
    @POST
    @Path("/niord-integration/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public NiordIntegrationVo createNiordIntegration(NiordIntegrationVo integration) throws Exception {
        log.info("Creating Niord integration point " + integration.getUrl());
        return niordIntegrationService
                .createNiordIntegration(new NiordIntegration(integration))
                .toVo();
    }


    /** Updates an existing Niord integration point */
    @PUT
    @Path("/niord-integration/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public NiordIntegrationVo updateNiordIntegration(@PathParam("id") Integer id, NiordIntegrationVo integration) throws Exception {
        if (!Objects.equals(id, integration.getId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating Niord integration point " + integration.getId());
        return niordIntegrationService
                .updateNiordIntegration(new NiordIntegration(integration))
                .toVo();
    }


    /** Deletes an existing Niord integration point */
    @DELETE
    @Path("/niord-integration/{id}")
    @Consumes("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public void deleteNiordIntegration(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting Niord integration point " + id);
        niordIntegrationService.deleteNiordIntegration(id);
    }


    /** Updates an existing Niord integration point */
    @PUT
    @Path("/execute/{id}")
    @Consumes("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public void executeNiordIntegration(@PathParam("id") Integer id) throws Exception {
        log.info("Executing Niord integration point " + id);
        niordIntegrationService.processNiordIntegration(id);
    }

}
