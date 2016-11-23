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
import org.niord.core.publication.PublicationType;
import org.niord.core.publication.PublicationTypeService;
import org.niord.model.publication.PublicationTypeVo;
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
 * REST interface for accessing publication types.
 */
@Path("/publication-types")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class PublicationTypeRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    PublicationTypeService publicationTypeService;


    /** Returns all publication types up to the given limit */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<PublicationTypeVo> getAllPublicationTypes(
            @QueryParam("lang") String lang,
            @QueryParam("limit") @DefaultValue("1000") int limit) {
        DataFilter dataFilter = DataFilter.get().lang(lang);
        return publicationTypeService.getPublicationTypes().stream()
                .limit(limit)
                .map(p -> p.toVo(dataFilter))
                .sorted(publicationTypeNameComparator(lang))
                .collect(Collectors.toList());
    }


    /** Returns the publication type with the given ID */
    @GET
    @Path("/publication-type/{typeId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PublicationTypeVo getPublicationType(@PathParam("typeId") String typeId) throws Exception {
        return publicationTypeService.findByTypeId(typeId)
                .toVo(DataFilter.get());
    }


    /** Creates a new publication type */
    @POST
    @Path("/publication-type/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public PublicationTypeVo createPublicationType(PublicationTypeVo publicationType) throws Exception {
        log.info("Creating publication type " + publicationType);
        return publicationTypeService.createPublicationType(new PublicationType(publicationType))
                .toVo(DataFilter.get());
    }


    /** Updates an existing publication type */
    @PUT
    @Path("/publication-type/{typeId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public PublicationTypeVo updatePublicationType(
            @PathParam("typeId") String typeId,
            PublicationTypeVo type) throws Exception {

        if (!Objects.equals(typeId, type.getTypeId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating publication type " + typeId);
        return publicationTypeService.updatePublicationType(new PublicationType(type))
                .toVo(DataFilter.get());
    }


    /** Deletes an existing publication type */
    @DELETE
    @Path("/publication-type/{typeId}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public void deletePublicationType(@PathParam("typeId") String typeId) throws Exception {
        log.info("Deleting publication type " + typeId);
        publicationTypeService.deletePublicationType(typeId);
    }


    /**
     * Imports an uploaded Publications json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-publication-types")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importPublications(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "publication-type-import");
    }

    /** Returns a publication name comparator **/
    private Comparator<PublicationTypeVo> publicationTypeNameComparator(String lang) {
        return (p1, p2) -> {
            String n1 = p1.getDesc(lang) != null ? p1.getDesc(lang).getName() : null;
            String n2 = p2.getDesc(lang) != null ? p2.getDesc(lang).getName() : null;
            return TextUtils.compareIgnoreCase(n1, n2);
        };
    }

}
