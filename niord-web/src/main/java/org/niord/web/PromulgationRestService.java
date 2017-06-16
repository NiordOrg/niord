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
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.PromulgationManager;
import org.niord.core.promulgation.PromulgationType;
import org.niord.core.promulgation.PromulgationTypeService;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.PromulgationServiceVo;
import org.niord.core.promulgation.vo.PromulgationTypeVo;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing and managing promulgation types.
 */
@Path("/promulgations")
@Stateless
@SecurityDomain("keycloak")
@SuppressWarnings("unused")
public class PromulgationRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    PromulgationTypeService promulgationTypeService;

    @Inject
    PromulgationManager promulgationManager;

    @Inject
    UserService userService;


    /***************************************/
    /** Promulgation Services             **/
    /***************************************/


    /** Returns all promulgation service IDs */
    @GET
    @Path("/promulgation-services/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @RolesAllowed(Roles.SYSADMIN)
    @NoCache
    public List<PromulgationServiceVo> getPromulgationServices() {
        return promulgationManager.promulgationServices();
    }


    /***************************************/
    /** Promulgation Types                **/
    /***************************************/

    /** Returns all promulgation types */
    @GET
    @Path("/promulgation-types/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll // NB: Checked programmatically to facilitate tickets
    @NoCache
    public List<PromulgationTypeVo> getPromulgationTypes() {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.SYSADMIN)) {
            throw new WebApplicationException(403);
        }

        return promulgationTypeService.getAll().stream()
                .map(PromulgationType::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the given promulgation type */
    @GET
    @Path("/promulgation-type/{typeId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @RolesAllowed(Roles.EDITOR)
    @NoCache
    public PromulgationTypeVo getPromulgationType(@PathParam("typeId") String typeId) throws Exception {
        return promulgationTypeService.getPromulgationType(typeId).toVo();
    }


    /** Creates a promulgation type */
    @POST
    @Path("/promulgation-type/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public PromulgationTypeVo createPromulgationType(PromulgationTypeVo type) throws Exception {
        return promulgationTypeService.createPromulgationType(new PromulgationType(type)).toVo();
    }


    /** Updates an existing promulgation type */
    @PUT
    @Path("/promulgation-type/{typeId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public PromulgationTypeVo updatePromulgationType(
            @PathParam("typeId") String typeId,
            PromulgationTypeVo type
    ) throws Exception {

        if (!Objects.equals(typeId, type.getTypeId())) {
            throw new WebApplicationException(400);
        }

        return promulgationTypeService.updatePromulgationType(new PromulgationType(type)).toVo();
    }


    /** Deletes an existing promulgation type */
    @DELETE
    @Path("/promulgation-type/{typeId}")
    @RolesAllowed(Roles.SYSADMIN)
    @NoCache
    public void updatePromulgationType(@PathParam("typeId") String typeId) throws Exception {
         promulgationTypeService.deletePromulgationType(typeId);
    }


    /**
     * Imports an uploaded promulgation types json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-promulgation-types")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importReports(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "promulgation-type-import");
    }


    /***************************************/
    /** Message Promulgation Management   **/
    /***************************************/

    /** Generates a message promulgation record for the given typeId and message */
    @POST
    @Path("/generate/{typeId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.EDITOR)
    @GZIP
    @NoCache
    public BaseMessagePromulgationVo<?> generateMessagePromulgation(
            @PathParam("typeId") String typeId,
            SystemMessageVo messageVo
    ) throws Exception {

        log.info("Updating promulgation service " + typeId);
        return promulgationManager.generateMessagePromulgation(typeId, messageVo);
    }
}
