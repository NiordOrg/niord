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

package org.niord.s124madame.promulgation;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.user.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

/**
 * REST interface for managing Twitter settings
 */
@Path("/promulgation/baleen-settings")
@ApplicationScoped
@RolesAllowed(Roles.SYSADMIN)
@SuppressWarnings("unused")
@Transactional
public class BaleenSettingsRestService {

    @Inject
    BaleenPromulgationService baleenPromulgationService;

    /** Returns the settings for the given type */
    @GET
    @Path("/{typeId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public BaleenSettingsVo getSettings(
            @PathParam("typeId") String typeId) {
        BaleenSettings settings = baleenPromulgationService.getSettings(typeId);
        return settings == null ? null : settings.toVo();
    }


    /** Creates new settings */
    @POST
    @Path("/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public BaleenSettingsVo createSettings(
            BaleenSettingsVo settings) throws Exception {

        return baleenPromulgationService.createSettings(new BaleenSettings(settings)).toVo();
    }


    /** Updates the credential for the given promulgation type */
    @PUT
    @Path("/{typeId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public BaleenSettingsVo updateSettings(
            @PathParam("typeId") String typeId,
            BaleenSettingsVo settings) throws Exception {

        return baleenPromulgationService.updateSettings(new BaleenSettings(settings)).toVo();
    }

}
