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
import org.niord.core.script.ScriptResource;
import org.niord.core.script.ScriptResourceHistory;
import org.niord.core.script.ScriptResourceService;
import org.niord.core.script.vo.ScriptResourceHistoryVo;
import org.niord.core.script.vo.ScriptResourceVo;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing script resources, such as Freemarker templates and JavaScript Resources.
 */
@Path("/script-resources")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.SYSADMIN)
@SuppressWarnings("unused")
public class ScriptResourceRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    UserService userService;

    @Inject
    ScriptResourceService resourceService;

    
    /** Returns all script resources */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll
    @NoCache
    public List<ScriptResourceVo> getScriptResources(@QueryParam("type") ScriptResource.Type type) {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.ADMIN)) {
            throw new WebApplicationException(403);
        }

        if (type != null) {
            return resourceService.findByTypes(type).stream()
                    .map(ScriptResource::toVo)
                    .collect(Collectors.toList());
        }

        return resourceService.findAll().stream()
                .map(ScriptResource::toVo)
                .collect(Collectors.toList());
    }


    /** Creates a new script resource */
    @POST
    @Path("/script-resource/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public ScriptResourceVo createScriptResource(ScriptResourceVo resource) throws Exception {
        log.info("Creating resource " + resource);
        return resourceService.createScriptResource(new ScriptResource(resource))
                .toVo();
    }


    /** Updates an existing script resource */
    @PUT
    @Path("/script-resource/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public ScriptResourceVo updateScriptResource(@PathParam("id") Integer id, ScriptResourceVo resource) throws Exception {
        if (!Objects.equals(id, resource.getId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating resource " + resource);
        return resourceService.updateScriptResource(new ScriptResource(resource))
                .toVo();
    }


    /** Deletes an existing script resource */
    @DELETE
    @Path("/script-resource/{id}")
    @GZIP
    @NoCache
    public void deleteScriptResource(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting resource " + id);
        resourceService.deleteScriptResource(id);
    }


    /** Reloads script resources from the class-path */
    @POST
    @Path("/reload/")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public Integer reloadScriptResourcesFromClassPath() throws Exception {
        log.info("Reloading resources from classpath");
        return resourceService.reloadScriptResourcesFromClassPath();
    }


    /**
     * Imports an uploaded script resources json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-script-resources")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importScriptResources(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "script-resource-import");
    }



    /***************************************/
    /** Freemarker Template History       **/
    /***************************************/


    /**
     * Returns the script resource history for the given resource ID
     * @param resourceId the script resource ID or resource series ID
     * @return the resource history
     */
    @GET
    @Path("/script-resource/{resourceId}/history")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<ScriptResourceHistoryVo> getScriptResourceHistory(@PathParam("resourceId") Integer resourceId) {

        // Get the resource id
        ScriptResource resource = resourceService.findById(resourceId);
        if (resource == null) {
            return Collections.emptyList();
        }

        return resourceService.getScriptResourceHistory(resource.getId()).stream()
                .map(ScriptResourceHistory::toVo)
                .collect(Collectors.toList());
    }

}
