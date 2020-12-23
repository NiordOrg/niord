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
import org.jboss.ejb3.annotation.SecurityDomain;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.niord.model.publication.PublicationCategoryVo;
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
 * REST interface for accessing publication categories.
 */
@Path("/publication-categories")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class PublicationCategoryRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    PublicationCategoryService publicationCategoryService;


    /** Returns all publication categories up to the given limit */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<PublicationCategoryVo> getAllPublicationCategories(
            @QueryParam("lang") String lang,
            @QueryParam("limit") @DefaultValue("1000") int limit) {
        DataFilter dataFilter = DataFilter.get().lang(lang);
        return publicationCategoryService.getPublicationCategories().stream()
                .limit(limit)
                .map(p -> p.toVo(dataFilter))
                .collect(Collectors.toList());
    }


    /** Returns the publication category with the given ID */
    @GET
    @Path("/publication-category/{categoryId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PublicationCategoryVo getPublicationCategory(@PathParam("categoryId") String categoryId) throws Exception {
        return publicationCategoryService.findByCategoryId(categoryId)
                .toVo(DataFilter.get());
    }


    /** Creates a new publication category */
    @POST
    @Path("/publication-category/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public PublicationCategoryVo createPublicationCategory(PublicationCategoryVo publicationCategory) throws Exception {
        log.info("Creating publication category " + publicationCategory);
        return publicationCategoryService.createPublicationCategory(new PublicationCategory(publicationCategory))
                .toVo(DataFilter.get());
    }


    /** Updates an existing publication category */
    @PUT
    @Path("/publication-category/{categoryId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public PublicationCategoryVo updatePublicationCategory(
            @PathParam("categoryId") String categoryId,
            PublicationCategoryVo category) throws Exception {

        if (!Objects.equals(categoryId, category.getCategoryId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating publication category " + categoryId);
        return publicationCategoryService.updatePublicationCategory(new PublicationCategory(category))
                .toVo(DataFilter.get());
    }


    /** Deletes an existing publication category */
    @DELETE
    @Path("/publication-category/{categoryId}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public void deletePublicationCategory(@PathParam("categoryId") String categoryId) throws Exception {
        log.info("Deleting publication category " + categoryId);
        publicationCategoryService.deletePublicationCategory(categoryId);
    }


    /**
     * Imports an uploaded Publications json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-publication-categories")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.ADMIN)
    public String importPublications(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "publication-category-import");
    }

}
