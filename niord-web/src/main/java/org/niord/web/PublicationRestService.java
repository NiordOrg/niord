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
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationService;
import org.niord.core.publication.vo.PublicationVo;
import org.niord.core.util.TextUtils;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * REST interface for accessing publications.
 */
@Path("/publications")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class PublicationRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    PublicationService publicationService;


    /** Searches publications based on the given search parameters */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<PublicationVo> searchPublications(
            @QueryParam("lang") String lang,
            @QueryParam("name") @DefaultValue("") String name,
            @QueryParam("inactive") @DefaultValue("false") boolean inactive,
            @QueryParam("limit") @DefaultValue("1000") int limit) {

        DataFilter dataFilter = DataFilter.get().lang(lang);

        return publicationService.searchPublications(lang, name, inactive, limit).stream()
                .map(p -> p.toVo(dataFilter))
                .sorted(publicationNameComparator(lang))
                .collect(Collectors.toList());
    }


    /** Returns all publications up to the given limit */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<PublicationVo> getAllPublications(
            @QueryParam("lang") String lang,
            @QueryParam("limit") @DefaultValue("1000") int limit) {
        DataFilter dataFilter = DataFilter.get().lang(lang);
        return publicationService.getPublications().stream()
                .limit(limit)
                .map(p -> p.toVo(dataFilter))
                .sorted(publicationNameComparator(lang))
                .collect(Collectors.toList());
    }


    /** Returns the publication with the given ID */
    @GET
    @Path("/publication/{id}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PublicationVo getPublication(@PathParam("id") Integer id) throws Exception {
        return publicationService.findById(id)
                .toVo(DataFilter.get());
    }


    /** Creates a new publication */
    @POST
    @Path("/publication/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public PublicationVo createPublication(PublicationVo publication) throws Exception {
        log.info("Creating publication " + publication);
        return publicationService.createPublication(new Publication(publication))
                .toVo(DataFilter.get());
    }


    /** Updates an existing publication */
    @PUT
    @Path("/publication/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public PublicationVo updatePublication(
            @PathParam("id") Integer id,
            PublicationVo publication) throws Exception {

        if (!Objects.equals(id, publication.getId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating publication " + publication);
        return publicationService.updatePublication(new Publication(publication))
                .toVo(DataFilter.get());
    }


    /** Deletes an existing publication */
    @DELETE
    @Path("/publication/{id}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public void deletePublication(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting publication " + id);
        publicationService.deletePublication(id);
    }


    /**
     * Imports an uploaded Publications json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-publications")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importPublications(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "publication-import");
    }

    /** Returns a publication name comparator **/
    private Comparator<PublicationVo> publicationNameComparator(String lang) {
        return (p1, p2) -> {
            String n1 = p1.getDesc(lang) != null ? p1.getDesc(lang).getName() : null;
            String n2 = p2.getDesc(lang) != null ? p2.getDesc(lang).getName() : null;
            return TextUtils.compareIgnoreCase(n1, n2);
        };
    }

}
