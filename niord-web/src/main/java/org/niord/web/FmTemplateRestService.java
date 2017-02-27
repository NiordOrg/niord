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
import org.niord.core.fm.FmTemplate;
import org.niord.core.fm.FmTemplateHistory;
import org.niord.core.fm.FmTemplateService;
import org.niord.core.fm.vo.FmTemplateHistoryVo;
import org.niord.core.fm.vo.FmTemplateVo;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing Freemarker templates.
 */
@Path("/fm-templates")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.SYSADMIN)
@SuppressWarnings("unused")
public class FmTemplateRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    UserService userService;

    @Inject
    FmTemplateService templateService;

    
    /** Returns all Freemarker templates */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll
    @NoCache
    public List<FmTemplateVo> getFmTemplates() {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.ADMIN)) {
            throw new WebApplicationException(403);
        }

        return templateService.findAll().stream()
                .map(FmTemplate::toVo)
                .collect(Collectors.toList());
    }


    /** Creates a new Freemarker template */
    @POST
    @Path("/fm-template/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FmTemplateVo createFmTemplate(FmTemplateVo template) throws Exception {
        log.info("Creating template " + template);
        return templateService.createFmTemplate(new FmTemplate(template))
                .toVo();
    }


    /** Updates an existing Freemarker template */
    @PUT
    @Path("/fm-template/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FmTemplateVo updateFmTemplate(@PathParam("id") Integer id, FmTemplateVo template) throws Exception {
        if (!Objects.equals(id, template.getId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating template " + template);
        return templateService.updateFmTemplate(new FmTemplate(template))
                .toVo();
    }


    /** Deletes an existing Freemarker template */
    @DELETE
    @Path("/fm-template/{id}")
    @GZIP
    @NoCache
    public void deleteFmTemplate(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting template " + id);
        templateService.deleteFmTemplate(id);
    }


    /** Reloads Freemarker templates from the class-path */
    @POST
    @Path("/reload/")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public Integer reloadFmTemplatesFromClassPath() throws Exception {
        log.info("Reloading templates from classpath");
        return templateService.reloadFmTemplatesFromClassPath();
    }


    /**
     * Imports an uploaded Freemarker templates json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-templates")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importFmTemplates(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "fm-template-import");
    }



    /***************************************/
    /** Freemarker Template History       **/
    /***************************************/


    /**
     * Returns the Freemarker template history for the given template ID
     * @param templateId the Freemarker template ID or template series ID
     * @return the template history
     */
    @GET
    @Path("/fm-template/{templateId}/history")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<FmTemplateHistoryVo> getFmTemplateHistory(@PathParam("templateId") Integer templateId) {

        // Get the template id
        FmTemplate template = templateService.findById(templateId);
        if (template == null) {
            return Collections.emptyList();
        }

        return templateService.getFmTemplateHistory(template.getId()).stream()
                .map(FmTemplateHistory::toVo)
                .collect(Collectors.toList());
    }

}
