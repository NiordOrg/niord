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

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.promulgation.vo.NavtexTransmitterVo;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface to managing NAVTEX transmitters
 */
@Path("/promulgation/navtex")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.SYSADMIN)
@SuppressWarnings("unused")
public class NavtexTransmitterRestService {

    @Inject
    NavtexPromulgationService navtexPromulgationService;


    /** Returns all transmitters */
    @GET
    @Path("/transmitters/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<NavtexTransmitterVo> getTransmitters(@QueryParam("lang") @DefaultValue("en") String lang) {
        DataFilter filter = DataFilter.get().lang(lang);
        return navtexPromulgationService.getTransmitters().stream()
                .map(t -> t.toVo(filter))
                .collect(Collectors.toList());
    }

    /** Creates a new transmitter */
    @POST
    @Path("/transmitters/transmitter/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public NavtexTransmitterVo createTransmitter(NavtexTransmitterVo transmitter) throws Exception {
        NavtexTransmitter t = new NavtexTransmitter(transmitter);
        return navtexPromulgationService.createTransmitter(t)
                .toVo(DataFilter.get());
    }


    /** Updates an existing transmitter */
    @PUT
    @Path("/transmitters/transmitter/{name}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public NavtexTransmitterVo updateTransmitter(@PathParam("name") String name, NavtexTransmitterVo transmitter) throws Exception {
        if (!Objects.equals(name, transmitter.getName())) {
            throw new WebApplicationException(400);
        }

        NavtexTransmitter t = new NavtexTransmitter(transmitter);
        return navtexPromulgationService.updateTransmitter(t)
                .toVo(DataFilter.get());
    }


    /** Deletes an existing transmitter */
    @DELETE
    @Path("/transmitters/transmitter/{name}")
    @NoCache
    public void deleteTransmitter(@PathParam("name") String name) throws Exception {
        navtexPromulgationService.deleteTransmitter(name);
    }

}
