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

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.promulgation.vo.SafetyNetAreaVo;
import org.niord.core.promulgation.vo.SafetyNetMessagePromulgationVo;
import org.niord.core.user.Roles;
import org.niord.core.util.TextUtils;
import org.niord.model.DataFilter;

import javax.annotation.security.PermitAll;
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

import static org.niord.core.promulgation.NavtexPromulgationService.NAVTEX_LINE_LENGTH;

/**
 * REST interface to managing SafetyNET areas
 */
@Path("/promulgation/safetynet")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.SYSADMIN)
@SuppressWarnings("unused")
public class SafetyNetPromulgationRestService {

    @Inject
    SafetyNetPromulgationService safetyNetPromulgationService;


    /** Reformats the SafetyNET text */
    @PUT
    @Path("/reformat-safetynet")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll
    @NoCache
    public SafetyNetMessagePromulgationVo reformatSafetyNet(SafetyNetMessagePromulgationVo safetynet) throws Exception {
        if (StringUtils.isNotBlank(safetynet.getText())) {
            String text = safetynet.getText();

            // Clean up blank space and enforce uppercase
            text = TextUtils.maxLineLength(text, NAVTEX_LINE_LENGTH)
                    .replaceAll("(?is)\\n+", "\n")
                    .toUpperCase()
                    .trim();

            safetynet.setText(text);
        }
        return safetynet;
    }


    /***************************************/
    /** SafetyNET Area Handling           **/
    /***************************************/


    /** Returns all areas */
    @GET
    @Path("/areas/{typeId}/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SafetyNetAreaVo> getAreas(
            @PathParam("typeId") String typeId,
            @QueryParam("lang") @DefaultValue("en") String lang) {

        DataFilter filter = DataFilter.get().lang(lang);
        return safetyNetPromulgationService.getAreas(typeId).stream()
                .map(a -> a.toVo(DataFilter.get().fields(DataFilter.DETAILS)))
                .collect(Collectors.toList());
    }


    /** Creates a new area */
    @POST
    @Path("/areas/{typeId}/area/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public SafetyNetAreaVo createArea(
            @PathParam("typeId") String typeId,
            SafetyNetAreaVo area) throws Exception {

        if (!Objects.equals(typeId, area.getPromulgationType().getTypeId())) {
            throw new WebApplicationException(400);
        }

        return safetyNetPromulgationService.createArea(area.toEntity())
                .toVo(DataFilter.get().fields(DataFilter.DETAILS));
    }


    /** Updates an existing area */
    @PUT
    @Path("/areas/{typeId}/area/{name}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public SafetyNetAreaVo updateArea(
            @PathParam("typeId") String typeId,
            @PathParam("name") String name,
            SafetyNetAreaVo area) throws Exception {

        if (!Objects.equals(typeId, area.getPromulgationType().getTypeId())) {
            throw new WebApplicationException(400);
        }
        if (!Objects.equals(name, area.getName())) {
            throw new WebApplicationException(400);
        }

        return safetyNetPromulgationService.updateArea(area.toEntity())
                .toVo(DataFilter.get().fields(DataFilter.DETAILS));
    }


    /** Deletes an existing area */
    @DELETE
    @Path("/areas/{typeId}/area/{name}")
    @NoCache
    public void deleteArea(
            @PathParam("typeId") String typeId,
            @PathParam("name") String name) throws Exception {

        safetyNetPromulgationService.deleteArea(typeId, name);
    }

}
