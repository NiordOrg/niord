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
package org.niord.core.area;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.service.BaseService;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;
import org.niord.model.vo.AreaVo.AreaMessageSorting;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.model.vo.AreaVo.AreaMessageSorting.CW;

/**
 * Business interface for accessing Niord areas
 */
@Stateless
@SuppressWarnings("unused")
public class AreaService extends BaseService {

    public static final String SETTING_AREA_LAST_UPDATED = "areaLastUpdate";

    @Inject
    private Logger log;

    @Inject
    SettingsService settingsService;

    @Inject
    DomainService domainService;

    /**
     * Searches for areas matching the given search params
     *
     * @param params the sesarch params
     * @return the search result
     */
    @SuppressWarnings("all")
    public List<Area> searchAreas(AreaSearchParams params) {

        // Sanity check
        if (StringUtils.isBlank(params.getName())) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Area> areaQuery = cb.createQuery(Area.class);

        Root<Area> areaRoot = areaQuery.from(Area.class);

        // Build the predicate
        CriteriaHelper<Area> criteriaHelper = new CriteriaHelper<>(cb, areaQuery);

        // Match the name
        Join<Area, AreaDesc> descs = areaRoot.join("descs", JoinType.LEFT);
        criteriaHelper.like(descs.get("name"), params.getName());
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

        // Optionally, filter by the domains associated with the current domain
        if (params.isDomain()) {
            Domain d = domainService.currentDomain();
            if (d != null && d.getAreas().size() > 0) {
                Predicate[] areaMatch = d.getAreas().stream()
                        .map(a -> cb.like(areaRoot.get("lineage"), a.getLineage() + "%"))
                        .toArray(Predicate[]::new);
                criteriaHelper.add(cb.or(areaMatch));
            }
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

        // Complete the query
        areaQuery.select(areaRoot)
                .distinct(true)
                .where(criteriaHelper.where());
                //.orderBy(cb.asc(cb.locate(cb.lower(descs.get("name")), name.toLowerCase())));

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

        // Get all areas along with their AreaDesc records
        // Will ensure that all Area entities are cached in the entity manager before organizing the result
        List<Area> areas = em
                .createNamedQuery("Area.findAreasWithDescs", Area.class)
                .getResultList();

        // Extract the roots
        return areas.stream()
                .filter(Area::isRootArea)
                .sorted()
                .collect(Collectors.toList());
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

        original.updateLineage();
        original.updateActiveFlag();

        original = saveEntity(original);

        // Evict all cached messages for the area subtree
        // evictCachedMessages(original);

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
        Area area = getByPrimaryKey(Area.class, areaId);

        if (area.getParent() != null && area.getParent().getId().equals(parentId)) {
            return false;
        }

        if (area.getParent() != null) {
            area.getParent().getChildren().remove(area);
        }

        if (parentId == null) {
            area.setParent(null);
        } else {
            Area parent = getByPrimaryKey(Area.class, parentId);
            parent.addChild(area);
        }

        // Save the entity
        saveEntity(area);
        em.flush();

        // Update all lineages
        updateLineages();
        area.updateActiveFlag();

        // Evict all cached messages for the area subtree
        //area = getByPrimaryKey(Area.class, area.getId());
        //evictCachedMessages(area);

        return true;
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
        Area area = getByPrimaryKey(Area.class, areaId);
        boolean updated = false;

        List<Area> siblings;
        if (area.getParent() != null) {
            siblings = area.getParent().getChildren();
        } else {
            siblings = em
                    .createNamedQuery("Area.findRootAreas", Area.class)
                    .getResultList();
        }
        Collections.sort(siblings);

        int index = siblings.indexOf(area);

        if (moveUp) {
            if (index == 1) {
                area.setSiblingSortOrder(siblings.get(0).getSiblingSortOrder() - 10.0);
                updated = true;
            } else if (index > 1) {
                double so1 =  siblings.get(index - 1).getSiblingSortOrder();
                double so2 =  siblings.get(index - 2).getSiblingSortOrder();
                area.setSiblingSortOrder((so1 + so2) / 2.0);
                updated = true;
            }

        } else {
            if (index == siblings.size() - 2) {
                area.setSiblingSortOrder(siblings.get(siblings.size() - 1).getSiblingSortOrder() + 10.0);
                updated = true;
            } else if (index < siblings.size() - 2) {
                double so1 =  siblings.get(index + 1).getSiblingSortOrder();
                double so2 =  siblings.get(index + 2).getSiblingSortOrder();
                area.setSiblingSortOrder((so1 + so2) / 2.0);
                updated = true;
            }

        }

        if (updated) {
            log.info("Updates sort order for area " + area.getId() + " to " + area.getSiblingSortOrder());
            // Save the entity
            saveEntity(area);
            // NB: Cache eviction not needed since lineage is the same...
        }

        return updated;
    }


    /**
     * Update lineages for all areas
     */
    public void updateLineages() {

        log.info("Update area lineages");

        // Get root areas
        List<Area> roots = getAll(Area.class).stream()
            .filter(Area::isRootArea)
            .collect(Collectors.toList());

        // Update each root subtree
        List<Area> updated = new ArrayList<>();
        roots.forEach(area -> updateLineages(area, updated));

        // Persist the changes
        updated.forEach(this::saveEntity);
        em.flush();
    }


    /**
     * Recursively updates the lineages of areas rooted at the given area
     * @param area the area whose sub-tree should be updated
     * @param areas the list of updated areas
     * @return if the lineage was updated
     */
    private boolean updateLineages(Area area, List<Area> areas) {

        boolean updated = area.updateLineage();
        if (updated) {
            areas.add(area);
        }
        area.getChildren().forEach(childArea -> updateLineages(childArea, areas));
        return updated;
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
        AreaSearchParams params = new AreaSearchParams();
        params.parentId(parentId)
                .language(lang)
                .inactive(true) // Also search inactive areas
                .name(name)
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
     * Ensures that the template area and it's parents exists.
     *
     * @param templateArea the template area
     * @param create whether to create a missing area or just find it
     * @return the area
     */
    public Area findOrCreateArea(Area templateArea, boolean create) {
        // Sanity checks
        if (templateArea == null) {
            return null;
        }

        // Check if we can find the area by MRN
        if (StringUtils.isNotBlank(templateArea.getMrn())) {
            Area area = findByMrn(templateArea.getMrn());
            if (area != null) {
                return area;
            }
        }

        // Recursively, resolve the parent areas
        Area parent = null;
        if (templateArea.getParent() != null) {
            parent = findOrCreateArea(templateArea.getParent(), create);
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
        }
        return area;
    }


    /**
     * Returns the last change date for areas or null if no area exists
     * @return the last change date for areas
     */
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
        long t0 = System.currentTimeMillis();

        // Compare the last area update date and the last processed date
        Date lastAreaUpdate = getLastUpdated();
        if (lastAreaUpdate == null) {
            // No areas
            return false;
        }

        Date lastProcessedUpdate = settingsService.getDate(new Setting(SETTING_AREA_LAST_UPDATED, null, false));
        if (lastProcessedUpdate == null) {
            lastProcessedUpdate = new Date(0);
        }

        if (!lastAreaUpdate.after(lastProcessedUpdate)) {
            log.debug("No area tree changes since last execution of recomputeTreeSortOrder()");
            return false;
        }

        // Get root areas (sorted)
        List<Area> roots = em
                .createNamedQuery("Area.findRootAreas", Area.class)
                .getResultList();

        // Re-compute the tree sort order
        List<Area> updated = new ArrayList<>();
        recomputeTreeSortOrder(roots, 0, updated, false, false);

        // Persist changed areas
        updated.forEach(this::saveEntity);

        em.flush();

        // Update the last processed date
        settingsService.setDate(SETTING_AREA_LAST_UPDATED, lastAreaUpdate);

        log.info("Recomputed tree sort order in " + (System.currentTimeMillis() - t0) + " ms");

        return updated.size() > 0;
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
        if (message.getAreas().isEmpty() || message.getGeometry() == null) {
            return no;
        }

        // Compute the message center
        double[] center = message.getGeometry().toGeoJson().computeCenter();
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
        return (area.getMessageSorting() == CW) ? -no : no;
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
