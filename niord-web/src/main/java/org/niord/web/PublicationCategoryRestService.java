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
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.niord.model.publication.PublicationCategoryVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * REST interface for accessing publication categories.
 */
@Path("/publication-categories")
@RequestScoped
@Transactional
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
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-publication-categories")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.ADMIN)
    public String importPublications(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "publication-category-import");
    }

}
