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
import org.niord.core.service.BaseService;
import org.niord.core.settings.SettingsService;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business interface for accessing MSI-NM areas
 */
@Stateless
@SuppressWarnings("unused")
public class AreaService extends BaseService {

    public static final String SETTING_AREA_LAST_UPDATED = "areaLastUpdate";

    @Inject
    private Logger log;

    @Inject
    SettingsService settingsService;


    /**
     * Searches for areas matching the given term in the given language
     *
     * @param lang the language
     * @param name the search term
     * @param geometry  if true, only return areas with geometries
     * @param limit the maximum number of results
     * @return the search result
     */
    @SuppressWarnings("all")
    public List<Area> searchAreas(Integer parentId, String lang, String name, boolean geometry, int limit) {

        // Sanity check
        if (StringUtils.isBlank(name)) {
            return Collections.emptyList();
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Area> areaQuery = cb.createQuery(Area.class);

        Root<Area> areaRoot = areaQuery.from(Area.class);

        // Build the predicate
        CriteriaHelper<Area> criteriaHelper = new CriteriaHelper<>(cb, areaQuery);

        // Match the name
        Join<Area, AreaDesc> descs = areaRoot.join("descs", JoinType.LEFT);
        criteriaHelper.like(descs.get("name"), name);
        // Optionally, match the language
        if (StringUtils.isNotBlank(lang)) {
            criteriaHelper.equals(descs.get("lang"), lang);
        }

        // Optionally, match the parent
        if (parentId != null) {
            areaRoot.join("parent", JoinType.LEFT);
            Path<Area> parent = areaRoot.get("parent");
            criteriaHelper.equals(parent.get("id"), parentId);
        }

        // Optionally, require that the area has an associated geometry
        if (geometry) {
            criteriaHelper.add(cb.isNotNull(areaRoot.get("geometry")));
        }

        // Complete the query
        areaQuery.select(areaRoot)
                .distinct(true)
                .where(criteriaHelper.where());
                //.orderBy(cb.asc(cb.locate(cb.lower(descs.get("name")), name.toLowerCase())));

        // Execute the query and update the search result
        return em.createQuery(areaQuery)
                .setMaxResults(limit)
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
                // TODO .sorted()
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

        original.setSiblingSortOrder(area.getSiblingSortOrder());
        original.copyDescsAndRemoveBlanks(area.getDescs());
        original.setGeometry(area.getGeometry());
        original.updateLineage();

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

        // Non-root case
        if (area.getParent() != null) {
            List<Area> siblings = area.getParent().getChildren();
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

        } else {
            log.debug("Changing sort order of roots noy yet handled");
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
            area.setParent(null);
            saveEntity(area);
            remove(area);
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

        List<Area> areas = searchAreas(parentId, lang, name, false, 1);

        return areas.isEmpty() ? null : areas.get(0);
    }


    /**
     * Ensures that the template area and it's parents exists
     * @param templateArea the template area
     * @return the area
     */
    public Area findOrCreateArea(Area templateArea) {
        // Sanity checks
        if (templateArea == null || templateArea.getDescs().size() == 0) {
            return null;
        }

        // Recursively, resolve the parent areas
        Area parent = null;
        if (templateArea.getParent() != null) {
            parent = findOrCreateArea(templateArea.getParent());
        }
        Integer parentId = (parent == null) ? null : parent.getId();

        // Check if we can find the given area
        Area area = null;
        for (int x = 0; area == null && x < templateArea.getDescs().size(); x++) {
            AreaDesc desc = templateArea.getDescs().get(x);
            area = findByName(desc.getName(), desc.getLang(), parentId);
        }

        // Create the area if no matching area was found
        if (area == null) {
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

        Date lastProcessedUpdate = settingsService.getDate(SETTING_AREA_LAST_UPDATED);
        if (lastProcessedUpdate == null) {
            lastProcessedUpdate = new Date(0);
        }

        if (!lastAreaUpdate.after(lastProcessedUpdate)) {
            log.debug("No area tree changes since last execution of recomputeTreeSortOrder()");
            return false;
        }

        List<Area> roots = em
                .createNamedQuery("Area.findRootAreas", Area.class)
                .getResultList();

        // Sort the roots by sortOrder
        Collections.sort(roots);

        // Re-compute the tree sort order
        List<Area> updated = new ArrayList<>();
        recomputeTreeSortOrder(roots, 0, updated, false);

        // Persist changed areas
        updated.forEach(this::saveEntity);

        em.flush();

        // Update the last processed date
        settingsService.setDate(SETTING_AREA_LAST_UPDATED, lastAreaUpdate);

        log.info("Recomputed tree sort order in " + (System.currentTimeMillis() - t0) + " ms");

        return updated.size() > 0;
    }


    /**
     * Recursively recomputes the treeSortOrder, by enumerating the sorted area list and their children
     * @param areas the list of areas to update
     * @param index the current area index
     * @param updatedAreas the list of updated areas given by sub-tree roots.
     * @param ancestorUpdated if an ancestor area has been updated
     * @return the index after processing the list of areas.
     */
    private int recomputeTreeSortOrder(List<Area> areas, int index, List<Area> updatedAreas, boolean ancestorUpdated) {

        for (Area area : areas) {
            index++;
            boolean updated = ancestorUpdated;
            if (index != area.getTreeSortOrder()) {
                area.setTreeSortOrder(index);
                updated = true;
                if (!ancestorUpdated) {
                    updatedAreas.add(area);
                }
            }

            // NB: area.getChildren() is by definition sorted
            index = recomputeTreeSortOrder(area.getChildren(), index, updatedAreas, updated);
        }

        return index;
    }

}
