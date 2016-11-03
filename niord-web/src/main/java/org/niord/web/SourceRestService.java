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
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.source.Source;
import org.niord.core.source.SourceService;
import org.niord.core.source.vo.SourceVo;
import org.niord.model.DataFilter;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * REST interface for accessing sources.
 */
@Path("/sources")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class SourceRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    SourceService sourceService;


    /** Searches sources based on the given search parameters */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SourceVo> searchSources(
            @QueryParam("lang") String lang,
            @QueryParam("name") @DefaultValue("") String name,
            @QueryParam("inactive") @DefaultValue("false") boolean inactive,
            @QueryParam("limit") @DefaultValue("1000") int limit) {

        DataFilter dataFilter = DataFilter.get().lang(lang);

        return sourceService.searchSources(lang, name, inactive, limit).stream()
                .map(p -> p.toVo(dataFilter))
                .collect(Collectors.toList());
    }


    /** Returns all sources up to the given limit */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SourceVo> getAllSources(
            @QueryParam("lang") String lang,
            @QueryParam("limit") @DefaultValue("1000") int limit) {
        DataFilter dataFilter = DataFilter.get().lang(lang);
        return sourceService.getSources().stream()
                .limit(limit)
                .map(p -> p.toVo(dataFilter))
                .collect(Collectors.toList());
    }


    /** Returns the source with the given ID */
    @GET
    @Path("/source/{id}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public SourceVo getSource(@PathParam("id") Integer id) throws Exception {
        return sourceService.findById(id)
                .toVo(DataFilter.get());
    }


    /** Creates a new source */
    @POST
    @Path("/source/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public SourceVo createSource(SourceVo source) throws Exception {
        log.info("Creating source " + source);
        return sourceService.createSource(new Source(source))
                .toVo(DataFilter.get());
    }


    /** Updates an existing source */
    @PUT
    @Path("/source/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public SourceVo updateSource(
            @PathParam("id") Integer id,
            SourceVo source) throws Exception {

        if (!Objects.equals(id, source.getId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating source " + source);
        return sourceService.updateSource(new Source(source))
                .toVo(DataFilter.get());
    }


    /** Deletes an existing source */
    @DELETE
    @Path("/source/{id}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public void deleteSource(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting source " + id);
        sourceService.deleteSource(id);
    }


    /**
     * Imports an uploaded Sources json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-sources")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importSources(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "source-import");
    }

}
