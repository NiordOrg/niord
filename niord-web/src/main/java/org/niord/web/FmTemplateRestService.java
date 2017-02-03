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
import org.niord.core.fm.FmTemplate;
import org.niord.core.fm.FmTemplateService;
import org.niord.core.fm.vo.FmTemplateVo;
import org.niord.core.user.Roles;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for generating PDF message reports.
 */
@Path("/templates")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.ADMIN)
@SuppressWarnings("unused")
public class FmTemplateRestService {

    @Inject
    Logger log;

    @Inject
    FmTemplateService fmTemplateService;

    
    /** Returns all templates */
    @GET
    @Path("/all")
    @GZIP
    @NoCache
    public List<FmTemplateVo> getTemplates() {
        return fmTemplateService.findAll().stream()
                .map(FmTemplate::toVo)
                .collect(Collectors.toList());
    }


    /** Creates a new template */
    @POST
    @Path("/template/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FmTemplateVo createTemplate(FmTemplateVo template) throws Exception {
        log.info("Creating template " + template);
        return fmTemplateService.createTemplate(new FmTemplate(template))
                .toVo();
    }


    /** Updates an existing template */
    @PUT
    @Path("/template/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FmTemplateVo updateTemplate(@PathParam("id") Integer id, FmTemplateVo template) throws Exception {
        if (!Objects.equals(id, template.getId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating template " + template);
        return fmTemplateService.updateTemplate(new FmTemplate(template))
                .toVo();
    }


    /** Deletes an existing template */
    @DELETE
    @Path("/template/{id}")
    @GZIP
    @NoCache
    public void deleteTemplate(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting template " + id);
        fmTemplateService.deleteTemplate(id);
    }

}
