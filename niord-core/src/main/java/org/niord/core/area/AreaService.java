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
package org.niord.core.area;

import com.vividsolutions.jts.geom.Geometry;
import org.apache.commons.lang.StringUtils;
import org.niord.core.area.vo.SystemAreaVo.AreaMessageSorting;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.db.SpatialIntersectsPredicate;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.Message;
import org.niord.core.model.BaseEntity;
import org.niord.core.service.TreeBaseService;
import org.niord.core.settings.SettingsService;
import org.niord.model.search.PagedSearchParamsVo;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.niord.core.area.AreaSearchParams.TREE_SORT_ORDER;

/**
 * Business interface for accessing Niord areas
 */
@Stateless
@SuppressWarnings("unused")
public class AreaService extends TreeBaseService<Area> {

    public static final String SETTING_AREA_LAST_UPDATED = "areaLastUpdate";

    @Inject
    private Logger log;

    @Inject
    SettingsService settingsService;

    @Inject
    DomainService domainService;


    /**
     * Returns the area with the given legacy id
     *
     * @param legacyId the legacy id of the area
     * @return the area with the given legacy id or null if not found
     */
    public Area findByLegacyId(String legacyId) {
        try {
            return em.createNamedQuery("Area.findByLegacyId", Area.class)
                    .setParameter("legacyId", legacyId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Searches for areas matching the given search params
     *
     * @param params the sesarch params
     * @return the search result
     */
    @SuppressWarnings("all")
    public List<Area> searchAreas(AreaSearchParams params) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Area> areaQuery = cb.createQuery(Area.class);

        Root<Area> areaRoot = areaQuery.from(Area.class);

        // Build the predicate
        CriteriaHelper<Area> criteriaHelper = new CriteriaHelper<>(cb, areaQuery);

        // Match the name
        Join<Area, AreaDesc> descs = areaRoot.join("descs", JoinType.LEFT);
        if (params.isExact()) {
            criteriaHelper.equalsIgnoreCase(descs.get("name"), params.getName());
        } else {
            criteriaHelper.like(descs.get("name"), params.getName());
        }

        // Optionally, match the language
        if (StringUtils.isNotBlank(params.getLanguage())) {
            criteriaHelper.equals(descs.get("lang"), params.getLanguage());
        }

        // Optionally, match the parent
        if (params.getParentId() != null) {
            areaRoot.join("parent", JoinType.LEFT);
            Path<Area> parent = areaRoot.get("parent");
            criteriaHelper.equals(parent.get("id"), params.getParentId());
        }

        // Assemple lineage filters from search domain and areas
        Set<String> lineages = new HashSet<>();

        // Optionally, filter by the areas associated with the specified domain
        if (StringUtils.isNotBlank(params.getDomain())) {
            Domain d = domainService.findByDomainId(params.getDomain());
            if (d != null && d.getAreas().size() > 0) {
                d.getAreas().forEach(a -> lineages.add(a.getLineage()));
            }
        }

        // Optionally, filter by area subtrees
        if (params.getAreaIds() != null && !params.getAreaIds().isEmpty()) {
            getAreaDetails(params.getAreaIds()).forEach(a -> lineages.add(a.getLineage()));
        }

        // If defined, apply the area lineage filter
        if (!lineages.isEmpty()) {
            Predicate[] areaMatch = lineages.stream()
                    .map(lineage -> cb.like(areaRoot.get("lineage"), lineage + "%"))
                    .toArray(Predicate[]::new);
            criteriaHelper.add(cb.or(areaMatch));
        }

        // Optionally, search by type
        if (params.getType() != null) {
            criteriaHelper.add(cb.equal(areaRoot.get("type"), params.getType()));
        }

        // Optionally, require that the area has an associated geometry
        if (params.isGeometry()) {
            criteriaHelper.add(cb.isNotNull(areaRoot.get("geometry")));
        }

        // Optionally, require that the area has an messageSorting type
        if (params.isMessageSorting()) {
            criteriaHelper.add(cb.isNotNull(areaRoot.get("messageSorting")));
        }

        // Unless the "inactive" search flag is set, only include active areas.
        if (!params.isInactive()) {
            criteriaHelper.add(cb.equal(areaRoot.get("active"), true));
        }

        // Compute the sort order
        List<Order> sortOrders = new ArrayList<>();
        if (TREE_SORT_ORDER.equals(params.getSortBy())) {
            Arrays.asList("treeSortOrder", "siblingSortOrder", "id")
                .forEach(field -> {
                    if (params.getSortOrder() == PagedSearchParamsVo.SortOrder.ASC) {
                        sortOrders.add(cb.asc(areaRoot.get(field)));
                    } else {
                        sortOrders.add(cb.desc(areaRoot.get(field)));
                    }
                });
        }


        // Complete the query
        areaQuery.select(areaRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(sortOrders);

        // Execute the query and update the search result
        return em.createQuery(areaQuery)
                .setMaxResults(params.getMaxSize())
                .getResultList();
    }


    /**
     * Returns the hierarchical list of root areas.
     * <p>
     * @return the hierarchical list of root areas
     */
    public List<Area> getAreaTree() {
        return getTree(Area.class, "Area.findAreasWithDescs");
    }


    /** {@inheritDoc} **/
    @Override
    public List<Area> getRootEntities() {
        return em.createNamedQuery("Area.findRootAreas", Area.class)
                .getResultList();
    }


    /**
     * Looks up an area
     *
     * @param id the id of the area
     * @return the area
     */
    public Area getAreaDetails(Integer id) {
        return getByPrimaryKey(Area.class, id);
    }


    /**
     * Looks up the areas with the given IDs
     *
     * @param ids the ids of the area
     * @return the area
     */
    public List<Area> getAreaDetails(Set<Integer> ids) {
        return em.createNamedQuery("Area.findAreasWithIds", Area.class)
                .setParameter("ids", ids)
                .getResultList();
    }


    /**
     * Updates the area data from the area template, but not the parent-child hierarchy of the area
     *
     * @param area the area to update
     * @return the updated area
     */
    public Area updateAreaData(Area area) {
        Area original = getByPrimaryKey(Area.class, area.getId());
        return updateAreaData(original, area);
    }


    /**
     * Updates the area data from the area template, but not the parent-child hierarchy of the area
     *
     * @param original the original area to update
     * @param area the template area to update the original with
     * @return the updated area
     */
    public Area updateAreaData(Area original, Area area) {

        original.setMrn(area.getMrn());
        original.setType(area.getType());
        original.setActive(area.isActive());
        original.setSiblingSortOrder(area.getSiblingSortOrder());
        original.copyDescsAndRemoveBlanks(area.getDescs());
        original.setGeometry(area.getGeometry());
        original.setMessageSorting(area.getMessageSorting());
        original.setOriginLatitude(area.getOriginLatitude());
        original.setOriginLongitude(area.getOriginLongitude());
        original.setOriginAngle(area.getOriginAngle());
        original.getEditorFields().clear();
        original.getEditorFields().addAll(area.getEditorFields());

        original.updateLineage();
        original.updateActiveFlag();

        original = saveEntity(original);

        return original;
    }


    /**
     * If there are any changes to data, updates the area data from the area template,
     * but not the parent-child hierarchy of the area
     *
     * @param original the original area to update
     * @param area the template area to update the original with
     * @return the updated area
     */
    public Area checkUpdateAreaData(Area original, Area area) {
        if (original.hasChanged(area)) {
            return updateAreaData(original, area);
        }
        return original;
    }


    /**
     * Creates a new area based on the area template
     * @param area the area to create
     * @param parentId the id of the parent area
     * @return the created area
     */
    public Area createArea(Area area, Integer parentId) {

        if (parentId != null) {
            Area parent = getByPrimaryKey(Area.class, parentId);
            parent.addChild(area);
        }

        area = saveEntity(area);

        // The area now has an ID - Update lineage
        area.updateLineage();
        area.updateActiveFlag();
        area = saveEntity(area);

        em.flush();
        return area;
    }


    /**
     * Moves the area to the given parent id
     * @param areaId the id of the area to create
     * @param parentId the id of the parent area
     * @return if the area was moved
     */
    public boolean moveArea(Integer areaId, Integer parentId) {
        return moveEntity(Area.class, areaId, parentId);
    }

    /**
     * Changes the sort order of an area, by moving it up or down compared to siblings.
     * <p>
     * Please note that by moving "up" we mean in a geographical tree structure,
     * i.e. a smaller sortOrder value.
     *
     * @param areaId the id of the area to move
     * @param moveUp whether to move the area up or down
     * @return if the area was moved
     */
    public boolean changeSortOrder(Integer areaId, boolean moveUp) {
        return changeSortOrder(Area.class, areaId, moveUp);
    }


    /**
     * Deletes the area and sub-areas
     * @param areaId the id of the area to delete
     */
    public boolean deleteArea(Integer areaId) {

        Area area = getByPrimaryKey(Area.class, areaId);
        if (area != null) {
            // Remove parent area relation
            area.setParent(null);
            saveEntity(area);
            remove(area);
            log.debug("Removed area " + areaId);
            return true;
        }
        return false;
    }


    /**
     * Looks up an area by name
     * @param name the name to search for
     * @param lang the language. Optional
     * @param parentId the parent ID. Optional
     * @return The matching area, or null if not found
     */
    public Area findByName(String name, String lang, Integer parentId) {
        if (StringUtils.isBlank(name)) {
            return null;
        }

        AreaSearchParams params = new AreaSearchParams();
        params.parentId(parentId)
                .language(lang)
                .inactive(true) // Also search inactive areas
                .name(name)
                .exact(true) // Not substring matches
                .maxSize(1);

        List<Area> areas = searchAreas(params);

        return areas.isEmpty() ? null : areas.get(0);
    }

    /**
     * Returns the area with the given MRN. Returns null if the area is not found.
     *
     * @param mrn the MRN of the area
     * @return the area with the given MRN or null if not found
     */
    public Area findByMrn(String mrn) {
        try {
            return em.createNamedQuery("Area.findByMrn", Area.class)
                    .setParameter("mrn", mrn)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the area with the given ID or MRN. Returns null if the area is not found.
     *
     * @param areaId the area ID or MRN
     * @return the area with the given ID or MRN or null if not found
     */
    public Area findByAreaId(String areaId) {
        if (StringUtils.isNumeric(areaId)) {
            return getAreaDetails(Integer.valueOf(areaId));
        }
        return findByMrn(areaId);
    }


    /**
     * Ensures that the template area and it's parents exists.
     *
     * @param templateArea the template area
     * @param create whether to create a missing area or just find it
     * @param update whether to update an existing area or just find it
     * @return the area
     */
    public Area importArea(Area templateArea, boolean create, boolean update) {
        // Sanity checks
        if (templateArea == null) {
            return null;
        }

        // Check if we can find the area by MRN
        if (StringUtils.isNotBlank(templateArea.getMrn())) {
            Area area = findByMrn(templateArea.getMrn());
            if (area != null) {
                return update ? checkUpdateAreaData(area, templateArea) : area;
            }
        }

        // Recursively, resolve the parent areas
        Area parent = null;
        if (templateArea.getParent() != null) {
            parent = importArea(templateArea.getParent(), create, update);
            if (!create && parent == null) {
                return null;
            }
        }
        Integer parentId = (parent == null) ? null : parent.getId();

        // Check if we can find the given area
        Area area = null;
        for (int x = 0; area == null && x < templateArea.getDescs().size(); x++) {
            AreaDesc desc = templateArea.getDescs().get(x);
            area = findByName(desc.getName(), desc.getLang(), parentId);
        }

        // Create the area if no matching area was found
        if (create && area == null) {
            area = createArea(templateArea, parentId);
        } else if (update && area != null) {
            area = checkUpdateAreaData(area, templateArea);
        }
        return area;
    }


    /**
     * Returns the last change date for areas or null if no area exists
     * @return the last change date for areas
     */
    @Override
    public Date getLastUpdated() {
        try {
            return em.createNamedQuery("Area.findLastUpdated", Date.class).getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Called periodically every hour to re-sort the area tree
     *
     * Potentially, a heavy-duty function that scans the entire area tree,
     * sorts it and update the treeSortOrder. Use with care.
     *
     * @return if the sort order was updated
     */
    @Schedule(persistent = false, second = "3", minute = "13", hour = "*")
    public boolean recomputeTreeSortOrder() {
        return recomputeTreeSortOrder(SETTING_AREA_LAST_UPDATED);
    }


    /**
     * For areas, override default tree sort order function to take message sorting into account
     *
     * @param areas the list of areas to update
     * @param index the current area index
     * @param updatedAreas the list of updated areas given by sub-tree roots.
     * @param ancestorUpdated if an ancestor area has been updated
     * @return the index after processing the list of areas.
     */
    @Override
    protected int recomputeTreeSortOrder(List<Area> areas, int index, List<Area> updatedAreas, boolean ancestorUpdated) {
        return recomputeTreeSortOrder(areas, index, updatedAreas, ancestorUpdated, false);
    }


    /**
     * Recursively recomputes the "treeSortOrder", by enumerating the sorted area list and their children.
     * The "treeSortOrder" is used for sorting messages by their area. If an area specifies the "messageSorting"
     * field, then all sub-areas of this are will have the same treeSortOrder.
     *
     * @param areas the list of areas to update
     * @param index the current area index
     * @param updatedAreas the list of updated areas given by sub-tree roots.
     * @param ancestorUpdated if an ancestor area has been updated
     * @param messageSorting whether this sub-tree is ordered via the "messageSorting" parameter or not
     * @return the index after processing the list of areas.
     */
    @SuppressWarnings("all")
    private int recomputeTreeSortOrder(List<Area> areas, int index, List<Area> updatedAreas, boolean ancestorUpdated, boolean messageSorting) {

        for (Area area : areas) {
            if (!messageSorting) {
                index++;
            }
            boolean areaMessageSorting = messageSorting || area.getMessageSorting() != null;
            boolean updated = ancestorUpdated;
            if (index != area.getTreeSortOrder()) {
                area.setTreeSortOrder(index);
                updated = true;
                if (!ancestorUpdated) {
                    updatedAreas.add(area);
                }
            }

            // NB: area.getChildren() is by definition sorted (by "siblingSortOrder")
            index = recomputeTreeSortOrder(area.getChildren(), index, updatedAreas, updated, areaMessageSorting);
        }

        return index;
    }


    /**
     * Returns the list of active areas intersecting with the given geometry down to the given level.
     * The result will be pruned, so that parent areas are not included
     * @param geometry the geometry
     * @param maxLevel the max level in the area tree. Root level is level 1.
     * @return the list of active charts intersecting with the given geometry
     */
    public List<Area> getIntersectingAreas(Geometry geometry, int maxLevel, boolean domain) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Area> areaQuery = cb.createQuery(Area.class);

        Root<Area> areaRoot = areaQuery.from(Area.class);

        // Build the predicate
        CriteriaHelper<Area> criteriaHelper = new CriteriaHelper<>(cb, areaQuery);

        Predicate geomPredicate = new SpatialIntersectsPredicate(
                cb,
                areaRoot.get("geometry"),
                geometry);
        criteriaHelper.add(geomPredicate);

        // Only search for active charts
        criteriaHelper.add(cb.equal(areaRoot.get("active"), true));

        // Optionally, filter by the areas associated with the specified domain
        if (domain) {
            Domain d = domainService.currentDomain();
            if (d != null && d.getAreas().size() > 0) {
                Predicate[] areaMatch = d.getAreas().stream()
                        .map(Area::getLineage)
                        .map(lineage -> cb.like(areaRoot.get("lineage"), lineage + "%"))
                        .toArray(Predicate[]::new);
                criteriaHelper.add(cb.or(areaMatch));
            }
        }

        // Compute the sort order
        List<Order> sortOrders = Stream.of("treeSortOrder", "siblingSortOrder", "id")
                .map(field -> cb.asc(areaRoot.get(field)))
                .collect(Collectors.toList());

        // Complete the query
        areaQuery.select(areaRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(sortOrders);

        // Execute the query to find all intersecting areas
        List<Area> areas = em.createQuery(areaQuery)
                .getResultList();

        // Ensure that we go no deeper than maxLevel.
        // It is assumed that parent areas include sub-areas geometry-wise.
        areas = areas.stream()
                .map(a -> {
                    List<Area> lineage = a.lineageAsList();
                    Collections.reverse(lineage);
                    return lineage.get(Math.min(lineage.size() - 1, maxLevel - 1));
                })
                .distinct()
                .collect(Collectors.toList());

        // Lastly, remove all parent areas
        Set<Integer> parentAreaIds = areas.stream()
                .flatMap(a -> a.parentLineageAsList().stream())
                .map(BaseEntity::getId)
                .collect(Collectors.toSet());
        areas.removeIf(a -> parentAreaIds.contains(a.getId()));

        return areas;
    }


    /***************************************/
    /** Message Area Sorting              **/
    /***************************************/


    /**
     * Generate a tentative sorting order for the message within its associated area.
     * The area-sort value is based on the message center latitude and longitude, and the sorting type for
     * its first associated area.
     * <p>
     * The original algorithm was used in the DMA MSIadmin web application, and implemented by
     * {@code dk.frv.msiedit.core.domain.Location.generateSortingOrder()}
     *
     * @return a sorting order
     */
    public double computeMessageAreaSortingOrder(Message message) {

        double no = 0.0;

        // Sanity check
        if (message.getAreas().isEmpty()) {
            return no;
        }

        // Compute the message center
        double[] center = GeoJsonUtils.computeCenter(message.toGeoJson());
        if (center == null) {
            return no;
        }
        double lat = center[1];
        double lon = center[0];

        // Find parent area with a "messageSorting" definition
        Area area = message.getAreas().get(0);
        while (area != null && area.getMessageSorting() == null) {
            area = area.getParent();
        }
        if (area == null) {
            return no;
        }
        AreaMessageSorting sortType = area.getMessageSorting();

        switch (sortType) {
            case NS:
                no = -lat;
                break;
            case SN:
                no = lat;
                break;
            case EW:
                no = -lon;
                break;
            case WE:
                no = lon;
                break;
            case CW:
            case CCW:
                no = computeCwOrCcwSortOrder(area, lat, lon);
                break;
        }
        // Each sort number must be different
        no += new Random().nextDouble() / 1000000.0;

        return no;
    }


    /** Calculates the message area sort order for CW and CCW types **/
    private double computeCwOrCcwSortOrder(Area area, double lat, double lon) {
        double no = 0.0;
        if (area.getOriginLatitude() == null || area.getOriginLongitude() == null) {
            return no;
        }

        double x = lon2x(area.getOriginLongitude(), area.getOriginLatitude(), lon, lat);
        double y = lat2y(area.getOriginLongitude(), area.getOriginLatitude(), lon, lat);
        double ang = 0.0;

        if (x == 0.0 && y > 0.0) {
            ang = 90.0;
        } else if (x == 0.0 && y < 0.0) {
            ang = 270.0;
        } else if (x != 0.0) {
            ang = Math.atan(y / x) * 180 / Math.PI;
            if (x < 0.0) {
                ang += 180.0;
            } else if (x > 0.0 && y < 0.0) {
                ang += 360.0;
            }
        }
        no = ang - (area.getOriginAngle() == null ? 0 : area.getOriginAngle());

        if (no < 0.0) {
            no += 360.0;
        }
        return (area.getMessageSorting() == AreaMessageSorting.CW) ? -no : no;
    }


    /** calculates the horizontal distance from lon0 to lon **/
    private double lon2x(double lon0, double lat0, double lon, double lat) {
        double radius=6356752.3; //Radius of the sphere.
        double deg2Rad = 180.0 / Math.PI;
        double lon_rad = lon / deg2Rad;
        double lat_rad = lat / deg2Rad;
        double lon0_rad = lon0 / deg2Rad;
        double lat0_rad = lat0 / deg2Rad;

        double x=0.0;
        double denom = (1.0 + Math.sin(lat0_rad) * Math.sin(lat_rad)
                + Math.cos(lat0_rad) * Math.cos(lat_rad) * Math.cos(lon_rad - lon0_rad));

        if (denom != 0.0) {
            x = ((2.0*radius) / denom) * Math.cos(lat_rad) * Math.sin(lon_rad - lon0_rad);
        }
        return x;
    }


    /** calculates the vertical distance from lon0 to lon **/
    private double lat2y(double lon0, double lat0, double lon, double lat) {
        double radius=6356752.3; //Radius of the sphere.
        double deg2Rad = 180.0 / Math.PI;
        double lon_rad = lon / deg2Rad;
        double lat_rad = lat / deg2Rad;
        double lon0_rad = lon0 / deg2Rad;
        double lat0_rad = lat0 / deg2Rad;

        double y=0.0;
        double denom = (1.0 + Math.sin(lat0_rad) * Math.sin(lat_rad)
                + Math.cos(lat0_rad) * Math.cos(lat_rad) * Math.cos(lon_rad - lon0_rad));

        if (denom != 0.0) {
            y = ((2.0*radius) / denom) * (Math.cos(lat0_rad) * Math.sin(lat_rad)
                    - Math.sin(lat0_rad) * Math.cos(lat_rad) * Math.cos(lon_rad - lon0_rad));
        }

        return y;
    }

}
