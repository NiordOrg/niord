/*
 * Copyright 2016 Danish Maritime Authority.
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
import org.niord.core.area.FiringAreaService;
import org.niord.core.area.vo.SystemAreaVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * REST interface for accessing and updating firing area schedules.
 */
@Path("/firing-areas")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class FiringAreaRestService {

    @Inject
    Logger log;

    @Inject
    FiringAreaService firingAreaService;

    /**
     * Fetches the firing areas that has firing periods on the specified date
     * @param date the date
     * @param inactive whether to include inactive areas or not
     * @param lang the language to filter areas by
     * @return the firing areas that has firing periods on the specified date
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemAreaVo> getFiringPeriodsForDate(
            @QueryParam("date") Long date,
            @QueryParam("query") String query,
            @QueryParam("area") Set<Integer> areaIds,
            @QueryParam("inactive") @DefaultValue("false") Boolean inactive,
            @QueryParam("lang") String lang) {

        Date searchDate = date != null ? new Date(date) : new Date();
        return firingAreaService.getFiringPeriodsForDate(searchDate, query, areaIds, inactive, lang);
    }


    /**
     * Updates the list of firing periods for the area at the given date
     * @param area the area to update
     * @param date the date to update the schedule for
     * @param lang the language to filter areas by
     * @return the updated area
     */
    @PUT
    @Path("/firing-area")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public SystemAreaVo updateFiringPeriodsForArea(
            @QueryParam("date") Long date,
            @QueryParam("lang") String lang,
            SystemAreaVo area) {

        Objects.requireNonNull(date, "The date must be specified");

        log.info("Updating firing periods for area " + area.getId());
        return firingAreaService.updateFiringPeriodsForArea(area, new Date(date), lang);
    }

}
