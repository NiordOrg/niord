/*
 * Copyright 2017 Danish Maritime Authority.
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
package org.niord.core.service;

import org.niord.core.model.TreeBaseEntity;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;
import org.slf4j.Logger;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DAO-like base class for services that work on work on {@linkplain TreeBaseEntity} entities
 */
public abstract class TreeBaseService<E extends TreeBaseEntity<E>> extends BaseService {

    @Inject
    private Logger log;

    @Inject
    SettingsService settingsService;

    /**
     * Returns the hierarchical list of root entities.
     * <p>
     * @return the hierarchical list of root entities
     */
    public List<E> getTree(Class<E> clz, String query) {

        // Get all entities 
        List<E> entities = em
                .createNamedQuery(query, clz)
                .getResultList();

        // Extract the roots
        return entities.stream()
                .filter(E::isRoot)
                .sorted()
                .collect(Collectors.toList());
    }


    /** Returns the list of root entities **/
    public abstract List<E> getRootEntities();


    /**
     * Moves the entity to the given parent id
     * @param entityId the id of the entity to create
     * @param parentId the id of the parent entity
     * @return if the entity was moved
     */
    public boolean moveEntity(Class<E> clz, Integer entityId, Integer parentId) {
        E entity = getByPrimaryKey(clz, entityId);

        if (entity.getParent() != null && entity.getParent().getId().equals(parentId)) {
            return false;
        }

        if (entity.getParent() != null) {
            entity.getParent().getChildren().remove(entity);
        }

        if (parentId == null) {
            entity.setParent(null);
        } else {
            E parent = getByPrimaryKey(clz, parentId);
            parent.addChild(entity);
        }

        // Save the entity
        // TODO: For some reason this doesn't update the parent/child
        //       relationship so as a temporary fix, we do this manually until
        //       we figure out what the hell is going on!
        saveEntity(entity);
        em.createQuery(String.format("UPDATE %s a set a.parent = :parent WHERE a.id = :id", entity.getClass().getName()))
                .setParameter("parent", entity.getParent())
                .setParameter("id", entity.getId())
                .executeUpdate();
        em.flush();

        // Update all lineages
        updateLineages(clz);
        entity.updateActiveFlag();

        return true;
    }


    /**
     * Update lineages for all entities
     */
    public void updateLineages(Class<E> clz) {

        // Get root entities
        List<E> roots = getAll(clz).stream()
                .filter(E::isRoot)
                .collect(Collectors.toList());

        // Update each root subtree
        List<E> updated = new ArrayList<>();
        roots.forEach(entity -> updateLineages(entity, updated));

        // Persist the changes
        updated.forEach(this::saveEntity);
        em.flush();
    }


    /**
     * Recursively updates the lineages of entities rooted at the given entity
     * @param entity the entity whose sub-tree should be updated
     * @param entities the list of updated entities
     * @return if the lineage was updated
     */
    private boolean updateLineages(E entity, List<E> entities) {

        boolean updated = entity.updateLineage();
        if (updated) {
            entities.add(entity);
        }
        entity.getChildren().forEach(childEntity -> updateLineages(childEntity, entities));
        return updated;
    }


    /**
     * Changes the sort order of an entity, by moving it up or down compared to siblings.
     * <p>
     * Please note that by moving "up" we mean in a geographical tree structure,
     * i.e. a smaller sortOrder value.
     *
     * @param entityId the id of the entity to move
     * @param moveUp whether to move the entity up or down
     * @return if the entity was moved
     */
    public boolean changeSortOrder(Class<E> clz, Integer entityId, boolean moveUp) {
        E entity = getByPrimaryKey(clz, entityId);
        boolean updated = false;

        List<E> siblings;
        if (entity.getParent() != null) {
            siblings = entity.getParent().getChildren();
        } else {
            siblings = getRootEntities();
        }
        Collections.sort(siblings);

        int index = siblings.indexOf(entity);


        // As a bootstrap issue, some sibling entities may have the same sibling sort order, e.g. 0.0.
        // If that is the case, simply re-assign new values
        if (siblings.stream().map(E::getSiblingSortOrder).distinct().count() != siblings.size()) {
            for (int x = 0; x < siblings.size(); x++) {
                E a = siblings.get(x);
                a.setSiblingSortOrder(x);
                saveEntity(a);
            }
        }


        if (moveUp) {
            if (index == 1) {
                entity.setSiblingSortOrder(siblings.get(0).getSiblingSortOrder() - 10.0);
                updated = true;
            } else if (index > 1) {
                double so1 =  siblings.get(index - 1).getSiblingSortOrder();
                double so2 =  siblings.get(index - 2).getSiblingSortOrder();
                entity.setSiblingSortOrder((so1 + so2) / 2.0);
                updated = true;
            }

        } else {
            if (index == siblings.size() - 2) {
                entity.setSiblingSortOrder(siblings.get(siblings.size() - 1).getSiblingSortOrder() + 10.0);
                updated = true;
            } else if (index < siblings.size() - 2) {
                double so1 =  siblings.get(index + 1).getSiblingSortOrder();
                double so2 =  siblings.get(index + 2).getSiblingSortOrder();
                entity.setSiblingSortOrder((so1 + so2) / 2.0);
                updated = true;
            }

        }

        if (updated) {
            log.info("Updates sort order for entity " + entity.getId() + " to " + entity.getSiblingSortOrder());
            // Save the entity
            saveEntity(entity);
        }

        return updated;
    }


    /**
     * Returns the last change date for the tree entities or null if no entity exists
     * @return the last change date for tree entities
     */
    public abstract Date getLastUpdated();


    /**
     * Re-sort the entity tree.
     *
     * Potentially, a heavy-duty function that scans the entire entity tree,
     * sorts it and update the treeSortOrder. Use with care.
     *
     * @return if the sort order was updated
     */
    public boolean recomputeTreeSortOrder(String lastProcessedSettingsKey) {
        long t0 = System.currentTimeMillis();

        // Compare the last entity update date and the last processed date
        Date lastEntityUpdate = getLastUpdated();
        if (lastEntityUpdate == null) {
            // No entities
            return false;
        }

        Date lastProcessedUpdate = settingsService.getDate(new Setting(lastProcessedSettingsKey, null, false));
        if (lastProcessedUpdate == null) {
            lastProcessedUpdate = new Date(0);
        }

        if (!lastEntityUpdate.after(lastProcessedUpdate)) {
            log.debug("No changes since last execution of recomputeTreeSortOrder()");
            return false;
        }

        // Get root entities (sorted)
        List<E> roots = getRootEntities();

        // Re-compute the tree sort order
        List<E> updated = new ArrayList<>();
        recomputeTreeSortOrder(roots, 0, updated, false);

        // Persist changed entities
        updated.forEach(this::saveEntity);

        em.flush();

        // Update the last processed date
        settingsService.setDate(lastProcessedSettingsKey, lastEntityUpdate);

        log.info("Recomputed tree sort order in " + (System.currentTimeMillis() - t0) + " ms");

        return updated.size() > 0;
    }


    /**
     * Recursively recomputes the "treeSortOrder", by enumerating the sorted tree entity list and their children.
     *
     * @param entities the list of entities to update
     * @param index the current entity index
     * @param updatedEntities the list of updated entities given by sub-tree roots.
     * @param ancestorUpdated if an ancestor entity has been updated
     * @return the index after processing the list of entities.
     */
    @SuppressWarnings("all")
    protected int recomputeTreeSortOrder(List<E> entities, int index, List<E> updatedEntities, boolean ancestorUpdated) {

        for (E entity : entities) {
            index++;
            boolean updated = ancestorUpdated;
            if (index != entity.getTreeSortOrder()) {
                entity.setTreeSortOrder(index);
                updated = true;
                if (!ancestorUpdated) {
                    updatedEntities.add(entity);
                }
            }

            // NB: entity.getChildren() is by definition sorted (by "siblingSortOrder")
            index = recomputeTreeSortOrder(entity.getChildren(), index, updatedEntities, updated);
        }

        return index;
    }

}
