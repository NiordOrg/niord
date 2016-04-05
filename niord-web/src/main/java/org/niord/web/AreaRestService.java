/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.vo.AreaVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST interface for accessing areas.
 */
@Path("/areas")
@Stateless
@SecurityDomain("keycloak")
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
     * @param geometry  if true, only return areas with geometries
     * @param limit the maximum number of results
     * @return the search result
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AreaVo> searchAreas(
            @QueryParam("lang") String lang,
            @QueryParam("name") String name,
            @QueryParam("geometry") @DefaultValue("false") boolean geometry,
            @QueryParam("limit") int limit) {

        log.debug(String.format("Searching for areas lang=%s, name='%s', geometry=%s, limit=%d",
                lang, name, geometry, limit));

        DataFilter filter = DataFilter.get()
                .fields(DataFilter.PARENT, DataFilter.GEOMETRY)
                .lang(lang);

        return areaService.searchAreas(null, lang, name, geometry, limit).stream()
                .map(a -> a.toVo(filter))
                .collect(Collectors.toList());
    }


    /** Returns the area with the given IDs */
    @GET
    @Path("/search/{areaIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AreaVo> searchAreaIds(@PathParam("areaIds") String areaIds,
                                      @QueryParam("lang") @DefaultValue("en") String lang,
                                      @QueryParam("limit") @DefaultValue("1000") int limit) {

        log.debug(String.format("Searching for areas ids=%s, lang=%s, limit=%d", areaIds, lang, limit));

        Set<Integer> ids = Arrays.stream(areaIds.split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.PARENT, DataFilter.GEOMETRY);

        return areaService.getAreaDetails(ids).stream()
                .map(a -> a.toVo(filter))
                .limit(limit)
                .collect(Collectors.toList());
    }


    /** Returns all areas via a list of hierarchical root areas */
    @GET
    @Path("/area-roots")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AreaVo> getAreaRoots(@QueryParam("lang") String lang) {

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.CHILDREN);

        return areaService.getAreaTree().stream()
                .map(a -> a.toVo(filter))
                .collect(Collectors.toList());
    }


    /** Returns all areas via a list of hierarchical root areas including geometries */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AreaVo> getAll() {

        DataFilter filter = DataFilter.get()
                .fields(DataFilter.CHILDREN, DataFilter.GEOMETRY);

        return areaService.getAreaTree().stream()
                .map(a -> a.toVo(filter))
                .collect(Collectors.toList());
    }


    /** Returns the area with the given ID */
    @GET
    @Path("/area/{areaId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public AreaVo getArea(@PathParam("areaId") Integer areaId) throws Exception {
        log.debug("Getting area " + areaId);
        Area area = areaService.getAreaDetails(areaId);
        // Return the area without parent and child areas
        return area == null ? null : area.toVo(DataFilter.get().fields("geometry"));
    }

    /** Creates a new area */
    @POST
    @Path("/area/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    public AreaVo createArea(AreaVo areaVo) throws Exception {
        Area area = new Area(areaVo);
        log.info("Creating area " + area);
        Integer parentId = (areaVo.getParent() == null) ? null : areaVo.getParent().getId();
        return areaService.createArea(area, parentId).toVo(DataFilter.get());
    }


    /** Updates an area */
    @PUT
    @Path("/area/{areaId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    public AreaVo updateArea(@PathParam("areaId") Integer areaId, AreaVo areaVo) throws Exception {
        if (!Objects.equals(areaId, areaVo.getId())) {
            throw new WebApplicationException(400);
        }
        Area area = new Area(areaVo);
        log.info("Updating area " + area);
        return areaService.updateAreaData(area).toVo(DataFilter.get());
    }


    /** Deletes the given area */
    @DELETE
    @Path("/area/{areaId}")
    @RolesAllowed({"admin"})
    public boolean deleteArea(@PathParam("areaId") Integer areaId) throws Exception {
        log.info("Deleting area " + areaId);
        return areaService.deleteArea(areaId);
    }


    /** Move an area to a new parent area **/
    @PUT
    @Path("/move-area")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    public boolean moveArea(MoveAreaVo moveAreaVo) throws Exception {
        log.info("Moving area " + moveAreaVo.getAreaId() + " to " + moveAreaVo.getParentId());
        return areaService.moveArea(moveAreaVo.getAreaId(), moveAreaVo.getParentId());
    }


    /** Changes sort order of an area in the area tree by moving it up or down among siblings */
    @PUT
    @Path("/change-sort-order")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    public boolean changeSortOrder(ChangeSortOrderVo changeSortOrderVo) throws Exception {
        log.info("Changing sort-order of area " + changeSortOrderVo.getAreaId()
                + " moving " + (changeSortOrderVo.isMoveUp() ? "up" : "down"));
        return areaService.changeSortOrder(changeSortOrderVo.getAreaId(), changeSortOrderVo.isMoveUp());
    }


    /** Re-computes the sort order of the area tree */
    @PUT
    @Path("/recompute-tree-sort-order")
    @RolesAllowed({"admin"})
    public boolean recomputeTreeSortOrder() {
        return areaService.recomputeTreeSortOrder();
    }


    /**
     * Imports an uploaded Areas json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-areas")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importAreas(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "area-import");
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
