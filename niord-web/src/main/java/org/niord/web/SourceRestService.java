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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.source.Source;
import org.niord.core.source.SourceService;
import org.niord.core.source.vo.SourceVo;
import org.niord.core.user.Roles;
import org.niord.core.util.TextUtils;
import org.niord.model.DataFilter;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;


/**
 * REST interface for accessing sources.
 */
@Path("/sources")
@RequestScoped
@Transactional
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
                .sorted(sourceNameComparator(lang))
                .collect(Collectors.toList());
    }


    /** Searches sources based on comma-separated ID's */
    @GET
    @Path("/search/{ids}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SourceVo> searchSourcesById(
            @PathParam("ids") String ids) {

        Set<Integer> idSet = Arrays.stream(ids.split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());

        return sourceService.findByIds(idSet).stream()
                .map(p -> p.toVo(DataFilter.get()))
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
                .sorted(sourceNameComparator(lang))
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
    @RolesAllowed(Roles.ADMIN)
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
    @RolesAllowed(Roles.ADMIN)
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
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public void deleteSource(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting source " + id);
        sourceService.deleteSource(id);
    }


    /**
     * Imports an uploaded Sources json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-sources")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.ADMIN)
    public String importSources(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "source-import");
    }


    /** Returns a source name comparator **/
    private Comparator<SourceVo> sourceNameComparator(String lang) {
        return (s1, s2) -> {
            String n1 = s1.getDesc(lang) != null ? s1.getDesc(lang).getName() : null;
            String n2 = s2.getDesc(lang) != null ? s2.getDesc(lang).getName() : null;
            return TextUtils.compareIgnoreCase(n1, n2);
        };
    }

}
