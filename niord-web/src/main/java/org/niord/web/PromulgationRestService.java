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
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.promulgation.PromulgationManager;
import org.niord.core.promulgation.vo.PromulgationServiceDataVo;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;

/**
 * REST interface for accessing Freemarker templates.
 */
@Path("/promulgations")
@Stateless
@SecurityDomain("keycloak")
@SuppressWarnings("unused")
public class PromulgationRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    PromulgationManager promulgationManager;

    @Inject
    UserService userService;

    /** Returns all templates */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll // NB: Checked programmatically to facilitate tickets
    @NoCache
    public List<PromulgationServiceDataVo> getPromulgations() {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.SYSADMIN)) {
            throw new WebApplicationException(403);
        }

        return promulgationManager.getAllPromulgationServices();
    }


    /** Updates an existing template */
    @PUT
    @Path("/promulgation/{type}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public PromulgationServiceDataVo updatePromulgation(
            @PathParam("type") String type,
            PromulgationServiceDataVo serviceData
    ) throws Exception {

        if (!Objects.equals(type, serviceData.getType())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating promulgation service " + type);
        return promulgationManager.updatePromulgationService(serviceData);
    }

}
