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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.PromulgationManager;
import org.niord.core.promulgation.PromulgationType;
import org.niord.core.promulgation.PromulgationTypeService;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.PromulgationServiceVo;
import org.niord.core.promulgation.vo.PromulgationTypeVo;
import org.niord.core.promulgation.vo.PublicPromulgationTypeVo;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST interface for accessing and managing promulgation types.
 */
@Path("/promulgations")
@RequestScoped
@Transactional
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
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-promulgation-types")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importReports(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "promulgation-type-import");
    }


    /***************************************/
    /** Public Promulgation Types         **/
    /***************************************/


    /** Returns the promulgation types with the given comma-separated IDs  */
    @GET
    @Path("/public-promulgation-type/{typeIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll
    @NoCache
    public List<PublicPromulgationTypeVo> getPublicPromulgationTypes(@PathParam("typeIds") String typeIds) {
        Set<String> ids = new HashSet<>();
        if (StringUtils.isNotBlank(typeIds)) {
            ids.addAll(Arrays.asList(typeIds.split(",")));
        }
        return promulgationTypeService.getAll().stream()
                .filter(t -> ids.contains(t.getTypeId()))
                .map(PromulgationType::toVo)
                .map(PromulgationTypeVo::toPublicPromulgationType)
                .collect(Collectors.toList());
    }


    /** Searches the promulgation types whose type ID or name matches the given string  */
    @GET
    @Path("/search-public-promulgation-type")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll
    @NoCache
    public List<PublicPromulgationTypeVo> searchPublicPromulgationTypes(
            @QueryParam("type") @DefaultValue("")  String type) {
        if (StringUtils.isBlank(type)) {
            return Collections.emptyList();
        }
        String str = type.toLowerCase();
        return promulgationTypeService.getAll().stream()
                .filter(t -> t.getTypeId().toLowerCase().contains(str) || t.getName().toLowerCase().contains(str))
                .map(PromulgationType::toVo)
                .map(PromulgationTypeVo::toPublicPromulgationType)
                .collect(Collectors.toList());
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
