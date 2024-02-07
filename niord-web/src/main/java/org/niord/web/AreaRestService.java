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
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.area.Area;
import org.niord.core.area.AreaSearchParams;
import org.niord.core.area.AreaService;
import org.niord.core.area.vo.SystemAreaVo;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.GeometryVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST interface for accessing areas.
 */
@Path("/areas")
@RequestScoped
@Transactional
@PermitAll
public class AreaRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    AreaService areaService;

    /**
     * Searches for areas matching the given name in the given language
     *
     * @param lang  the language
     * @param name  the search name
     * @param domain  if defined, restricts the search to the areas of the given domain
     * @param geometry  if true, only return areas with geometries
     * @param limit the maximum number of results
     * @return the search result
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemAreaVo> searchAreas(
            @QueryParam("lang") String lang,
            @QueryParam("name") String name,
            @QueryParam("domain") String domain,
            @QueryParam("geometry") @DefaultValue("false") boolean geometry,
            @QueryParam("messageSorting") @DefaultValue("false") boolean messageSorting,
            @QueryParam("inactive") @DefaultValue("false") boolean inactive,
            @QueryParam("limit") int limit) {

        if (StringUtils.isBlank(name)) {
            return Collections.emptyList();
        }

        AreaSearchParams params = new AreaSearchParams();
        params.language(lang)
                .name(name)
                .domain(domain)
                .geometry(geometry)
                .messageSorting(messageSorting)
                .inactive(inactive)
                .maxSize(limit);

        log.debug(String.format("Searching for areas: %s", params));

        DataFilter f = DataFilter.get()
                .fields(DataFilter.PARENT, DataFilter.DETAILS)
                .lang(lang);
        DataFilter filter = (geometry) ? f.fields(DataFilter.GEOMETRY) : f;

        return areaService.searchAreas(params).stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .collect(Collectors.toList());
    }


    /** Returns the area with the given IDs */
    @GET
    @Path("/search/{areaIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemAreaVo> searchAreaIds(@PathParam("areaIds") String areaIds,
                                      @QueryParam("lang") String lang,
                                      @QueryParam("limit") @DefaultValue("1000") int limit) {

        log.debug(String.format("Searching for areas ids=%s, lang=%s, limit=%d", areaIds, lang, limit));

        Set<Integer> ids = Arrays.stream(areaIds.split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.PARENT, DataFilter.GEOMETRY, DataFilter.DETAILS);

        return areaService.getAreaDetails(ids).stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .limit(limit)
                .collect(Collectors.toList());
    }


    /** Returns all areas via a list of hierarchical root areas */
    @GET
    @Path("/area-roots")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemAreaVo> getAreaRoots(@QueryParam("lang") String lang) {

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.CHILDREN, DataFilter.DETAILS);

        return areaService.getAreaTree().stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .collect(Collectors.toList());
    }


    /** Returns all areas via a list of hierarchical root areas including geometries */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemAreaVo> getAll() {

        DataFilter filter = DataFilter.get()
                .fields(DataFilter.CHILDREN, DataFilter.GEOMETRY, DataFilter.DETAILS);

        return areaService.getAreaTree().stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .collect(Collectors.toList());
    }


    /** Returns the area with the given ID */
    @GET
    @Path("/area/{areaId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public SystemAreaVo getArea(
            @PathParam("areaId") Integer areaId,
            @QueryParam("parent") @DefaultValue("false") boolean includeParents
    ) throws Exception {
        log.debug("Getting area " + areaId);
        Area area = areaService.getAreaDetails(areaId);

        // Return the area without parent and child areas
        DataFilter filter = DataFilter.get()
                .fields(DataFilter.GEOMETRY, DataFilter.DETAILS);

        if (includeParents) {
            filter = filter.fields(DataFilter.PARENT);
        }

        return area == null ? null : area.toVo(SystemAreaVo.class, filter);
    }

    /** Creates a new area */
    @POST
    @Path("/area/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    public SystemAreaVo createArea(SystemAreaVo areaVo) throws Exception {
        Area area = new Area(areaVo);
        log.info("Creating area " + area);
        Integer parentId = (areaVo.getParent() == null) ? null : areaVo.getParent().getId();
        return areaService
                .createArea(area, parentId)
                .toVo(SystemAreaVo.class, DataFilter.get());
    }


    /** Updates an area */
    @PUT
    @Path("/area/{areaId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    public SystemAreaVo updateArea(@PathParam("areaId") Integer areaId, SystemAreaVo areaVo) throws Exception {
        if (!Objects.equals(areaId, areaVo.getId())) {
            throw new WebApplicationException(400);
        }
        Area area = new Area(areaVo);
        log.info("Updating area " + area);
        return areaService.updateAreaData(area).toVo(SystemAreaVo.class, DataFilter.get());
    }


    /** Deletes the given area */
    @DELETE
    @Path("/area/{areaId}")
    @RolesAllowed(Roles.ADMIN)
    public boolean deleteArea(@PathParam("areaId") Integer areaId) throws Exception {
        log.info("Deleting area " + areaId);
        return areaService.deleteArea(areaId);
    }


    /** Move an area to a new parent area **/
    @PUT
    @Path("/move-area")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    public boolean moveArea(MoveAreaVo moveAreaVo) throws Exception {
        log.info("Moving area " + moveAreaVo.getAreaId() + " to " + moveAreaVo.getParentId());
        return areaService.moveArea(moveAreaVo.getAreaId(), moveAreaVo.getParentId());
    }


    /** Changes sort order of an area in the area tree by moving it up or down among siblings */
    @PUT
    @Path("/change-sort-order")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    public boolean changeSortOrder(ChangeSortOrderVo changeSortOrderVo) throws Exception {
        log.info("Changing sort-order of area " + changeSortOrderVo.getAreaId()
                + " moving " + (changeSortOrderVo.isMoveUp() ? "up" : "down"));
        return areaService.changeSortOrder(changeSortOrderVo.getAreaId(), changeSortOrderVo.isMoveUp());
    }


    /** Re-computes the sort order of the area tree */
    @PUT
    @Path("/recompute-tree-sort-order")
    @RolesAllowed(Roles.ADMIN)
    public boolean recomputeTreeSortOrder() {
        areaService.recomputeTreeSortOrder();
        return true;
    }


    /**
     * Imports an uploaded Areas json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-areas")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.ADMIN)
    public String importAreas(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "area-import");
    }


    /**
     * Returns the list of active areas intersecting with the given geometry down to the given level.
     * The result will be pruned, so that parent areas are not included
     * @param maxLevel the max level in the area tree. Root level is level 1.
     * @return the list of active charts intersecting with the given geometry
     */
    @POST
    @Path("/intersecting-areas")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.EDITOR)
    @GZIP
    @NoCache
    public List<SystemAreaVo> computeIntersectingAreas(
            @QueryParam("lang") String lang,
            @QueryParam("geometry") @DefaultValue("false") boolean geometry,
            @QueryParam("domain") @DefaultValue("false") boolean domain,
            @QueryParam("maxLevel") @DefaultValue("2") int maxLevel,
            FeatureCollectionVo featureCollection) {

        GeometryVo geometryVo = featureCollection.toGeometry();
        if (geometryVo == null) {
            return Collections.emptyList();
        }

        DataFilter f = DataFilter.get()
                .fields(DataFilter.PARENT, DataFilter.DETAILS)
                .lang(lang);
        DataFilter filter = (geometry) ? f.fields(DataFilter.GEOMETRY) : f;

        return areaService.getIntersectingAreas(JtsConverter.toJts(geometryVo), maxLevel, domain).stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .collect(Collectors.toList());
    }


    /**
     * ******************
     * Helper classes
     * *******************
     */

    /** Encapsulates the parameters used for moving an area to a new parent area */
    @SuppressWarnings("unused")
    public static class MoveAreaVo implements IJsonSerializable {
        Integer areaId, parentId;

        public Integer getAreaId() {
            return areaId;
        }

        public void setAreaId(Integer areaId) {
            this.areaId = areaId;
        }

        public Integer getParentId() {
            return parentId;
        }

        public void setParentId(Integer parentId) {
            this.parentId = parentId;
        }
    }


    /** Encapsulates the parameters used for moving an area up or down among sibling areas */
    @SuppressWarnings("unused")
    public static class ChangeSortOrderVo implements IJsonSerializable {
        Integer areaId;
        boolean moveUp;

        public Integer getAreaId() {
            return areaId;
        }

        public void setAreaId(Integer areaId) {
            this.areaId = areaId;
        }

        public boolean isMoveUp() {
            return moveUp;
        }

        public void setMoveUp(boolean moveUp) {
            this.moveUp = moveUp;
        }
    }
}
