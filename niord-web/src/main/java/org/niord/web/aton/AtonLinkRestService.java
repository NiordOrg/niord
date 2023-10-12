/*
 * Copyright 2023 GLA UK Research and Development Directive
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
package org.niord.web.aton;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.aton.*;
import org.niord.core.aton.vo.AtonLinkTypeCategoryVo;
import org.niord.core.aton.vo.AtonLinkVo;
import org.niord.core.user.Roles;
import org.niord.model.search.PagedSearchParamsVo.SortOrder;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API for accessing AtoN links, used for aggregating/associating a fixed set
 * of AtoN.
 */
@Path("/aton-links")
@RequestScoped
@Transactional
@PermitAll
public class AtonLinkRestService {

    @Inject
    Logger log;

    @Inject
    AtonLinkService atonLinkService;


    /** Returns the AtoN links with the given search parameters */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonLinkVo> searchAtonLinks(
            @QueryParam("name")  @DefaultValue("") String name,
            @QueryParam("type") Set<AtonLinkType> types,
            @QueryParam("sortBy") @DefaultValue("name") String sortBy,
            @QueryParam("sortOrder") @DefaultValue("ASC") SortOrder sortOrder,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        AtonLinkSearchParams params = new AtonLinkSearchParams();
        params.name(name)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .maxSize(limit);
        if (types != null) {
            params.types(types);
        }

        return this.atonLinkService.searchAtonLinks(params).stream()
                .map(AtonLink::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the AtoN links which contain the AtoN with the given AtoN UID */
    @GET
    @Path("/aton/{atonUid}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonLinkVo> findAtonLinksByAtonUid(@PathParam("atonUid") String atonUid) {
        return atonLinkService.findAtonLinksByAtonUid(atonUid)
                .stream()
                .map(AtonLink::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the AtoN links with the given AtoN link IDs */
    @GET
    @Path("/link/{linkIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonLinkVo> getAtonLinks(@PathParam("linkIds") String atonLinkUids) {
        return atonLinkService.findAtonLinks(Arrays.stream(atonLinkUids.split(","))
                        .map(UUID::fromString)
                        .toArray(UUID[]::new))
                .stream()
                .map(AtonLink::toVo)
                .collect(Collectors.toList());
    }


    /** Creates a new AtoN links from the given template */
    @POST
    @Path("/link/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public AtonLinkVo createAtonLink(AtonLinkVo link) {
        return atonLinkService.createAtonLink(new AtonLink(link)).toVo();
    }


    /** Updates an AtoN links from the given template */
    @PUT
    @Path("/link/{linkId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public AtonLinkVo updateAtonLink(@PathParam("linkId") UUID linkId, AtonLinkVo link) {
        if (!Objects.equals(linkId, link.getLinkId())) {
            throw new WebApplicationException(400);
        }
        return atonLinkService.updateAtonLink(new AtonLink(link)).toVo();
    }


    /** Deletes the AtoN links with the given AtoN link ID */
    @DELETE
    @Path("/link/{linkId}")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public boolean deleteAtonLink(@PathParam("linkId") UUID linkId) {
        log.info("Deleting AtoN link " + linkId);
        return atonLinkService.deleteAtonLink(linkId);
    }


    /** Clears AtoNs from the given AtoN link */
    @DELETE
    @Path("/link/{linkId}/atons")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public boolean clearAtonLink(@PathParam("linkId") UUID linkId) {
        log.info("Clearing AtoN link " + linkId);
        return atonLinkService.clearAtonLink(linkId);
    }


    /** Adds AtoNs to the given AtoN link */
    @PUT
    @Path("/link/{linkId}/add-atons")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public AtonLinkVo addAtonToAtonLink(@PathParam("linkId") UUID linkId, List<String> atonUids) {
        log.info("Adding AtoNs " + atonUids + " to AtoN link " + linkId);
        return atonLinkService.addAtonToAtonLink(linkId, atonUids).toVo();
    }


    /** Removes AtoNs from the given AtoN link */
    @PUT
    @Path("/link/{linkId}/remove-atons")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public AtonLinkVo removeAtonFromAtonLink(@PathParam("linkId") UUID linkId, List<String> atonUids) {
        log.info("Removing AtoNs " + atonUids + " from AtoN link " + linkId);
        return atonLinkService.removeAtonFromAtonLink(linkId, atonUids).toVo();
    }

    /** Returns all the AtoN link type categories in a list */
    @GET
    @Path("/type-categories")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonLinkTypeCategoryVo> getAtonLinkTypeCategories(@PathParam("linkType") AtonLinkType linkType) {

        return Arrays.stream(AtonLinkTypeCategory.values())
                .map(AtonLinkTypeCategoryVo::new)
                .collect(Collectors.toList());
    }

    /** Returns all the AtoN link type categories of a specific link type */
    @GET
    @Path("/type-categories/{linkType}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonLinkTypeCategoryVo> getAtonLinkTypeCategoriesByType(@PathParam("linkType") AtonLinkType linkType) {

        return Arrays.stream(AtonLinkTypeCategory.values())
                .filter(c -> c.getAtonLinkType() == linkType)
                .map(AtonLinkTypeCategoryVo::new)
                .collect(Collectors.toList());
    }
}

