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

package org.niord.core.promulgation;

import jakarta.annotation.security.RolesAllowed;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.promulgation.vo.TwitterSettingsVo;
import org.niord.core.user.Roles;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

/**
 * REST interface for managing Twitter settings
 */
@Path("/promulgation/twitter-settings")
@ApplicationScoped
@RolesAllowed(Roles.SYSADMIN)
@SuppressWarnings("unused")
public class TwitterSettingsRestService {


    @Inject
    TwitterPromulgationService twitterPromulgationService;


    /** Returns the settings for the given type */
    @GET
    @Path("/{typeId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public TwitterSettingsVo getSettings(
            @PathParam("typeId") String typeId) {
        TwitterSettings settings = twitterPromulgationService.getSettings(typeId);
        return settings == null ? null : settings.toVo();
    }


    /** Creates new settings */
    @POST
    @Path("/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public TwitterSettingsVo createSettings(
            TwitterSettingsVo settings) throws Exception {

        return twitterPromulgationService.createSettings(new TwitterSettings(settings)).toVo();
    }


    /** Updates the credential for the given promulgation type */
    @PUT
    @Path("/{typeId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public TwitterSettingsVo updateSettings(
            @PathParam("typeId") String typeId,
            TwitterSettingsVo settings) throws Exception {

        return twitterPromulgationService.updateSettings(new TwitterSettings(settings)).toVo();
    }

}
