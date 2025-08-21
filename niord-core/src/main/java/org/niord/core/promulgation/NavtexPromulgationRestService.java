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

import static org.niord.core.promulgation.NavtexPromulgationService.NAVTEX_LINE_LENGTH;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.promulgation.vo.NavtexMessagePromulgationVo;
import org.niord.core.promulgation.vo.NavtexTransmitterVo;
import org.niord.core.user.Roles;
import org.niord.core.util.TextUtils;
import org.niord.model.DataFilter;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import jakarta.ws.rs.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.niord.core.promulgation.NavtexPromulgationService.NAVTEX_LINE_LENGTH;

/**
 * REST interface to managing NAVTEX transmitters
 */
@Path("/promulgation/navtex")
@ApplicationScoped
@RolesAllowed(Roles.SYSADMIN)
public class NavtexPromulgationRestService {

    @Inject
    NavtexPromulgationService navtexPromulgationService;


    /** Reformats the NAVTEX text */
    @PUT
    @Path("/reformat-navtex")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll
    @NoCache
    public NavtexMessagePromulgationVo reformatNavtex(NavtexMessagePromulgationVo navtex) throws Exception {
        if (StringUtils.isNotBlank(navtex.getText())) {
            String text = navtex.getText();

            // Split into 40-character lines and enforce uppercase
            text = TextUtils.maxLineLength(text, NAVTEX_LINE_LENGTH)
                    .replaceAll("(?is)\\n+", "\n")
                    .toUpperCase()
                    .trim();

            navtex.setText(text);
        }
        return navtex;
    }


    /***************************************/
    /** Transmitter Handling              **/
    /***************************************/


    /** Returns all transmitters */
    @GET
    @Path("/transmitters/{typeId}/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @Transactional
    public List<NavtexTransmitterVo> getTransmitters(
            @PathParam("typeId") String typeId,
            @QueryParam("lang") @DefaultValue("en") String lang) {

        DataFilter filter = DataFilter.get().lang(lang);
        return navtexPromulgationService.getTransmitters(typeId).stream()
                .map(t -> t.toVo(filter))
                .collect(Collectors.toList());
    }


    /** Creates a new transmitter */
    @POST
    @Path("/transmitters/{typeId}/transmitter/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @Transactional
    public NavtexTransmitterVo createTransmitter(
            @PathParam("typeId") String typeId,
            NavtexTransmitterVo transmitter) throws Exception {

        if (!Objects.equals(typeId, transmitter.getPromulgationType().getTypeId())) {
            throw new WebApplicationException(400);
        }

        NavtexTransmitter t = new NavtexTransmitter(transmitter);
        return navtexPromulgationService.createTransmitter(t)
                .toVo(DataFilter.get());
    }


    /** Updates an existing transmitter */
    @PUT
    @Path("/transmitters/{typeId}/transmitter/{name}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @Transactional
    public NavtexTransmitterVo updateTransmitter(
            @PathParam("typeId") String typeId,
            @PathParam("name") String name,
            NavtexTransmitterVo transmitter) throws Exception {

        if (!Objects.equals(typeId, transmitter.getPromulgationType().getTypeId())) {
            throw new WebApplicationException(400);
        }
        if (!Objects.equals(name, transmitter.getName())) {
            throw new WebApplicationException(400);
        }

        NavtexTransmitter t = new NavtexTransmitter(transmitter);
        return navtexPromulgationService.updateTransmitter(t)
                .toVo(DataFilter.get());
    }


    /** Deletes an existing transmitter */
    @DELETE
    @Path("/transmitters/{typeId}/transmitter/{name}")
    @NoCache
    public void deleteTransmitter(
            @PathParam("typeId") String typeId,
            @PathParam("name") String name) throws Exception {

        navtexPromulgationService.deleteTransmitter(typeId, name);
    }

}
