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

package org.niord.core.model;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface implemented by tree-based entities.
 * Additionally, entities implementing this class have an active-flag.
 */
@MappedSuperclass
public abstract class TreeBaseEntity<E extends TreeBaseEntity<E>> extends VersionedEntity<Integer> implements Comparable<E> {

    protected boolean active = true;

    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH })
    protected E parent;

    @SuppressWarnings("all")
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @OrderBy("siblingSortOrder ASC")
    protected List<E> children = new ArrayList<>();

    @Column(length = 256)
    protected String lineage;

    // The sortOrder is used to sort this category among siblings, and exposed via the Admin UI
    @Column(columnDefinition="DOUBLE default 0.0")
    protected double siblingSortOrder;

    // The treeSortOrder is re-computed at regular intervals by the system and designates
    // the index of the category in an entire sorted category tree. Used for category sorting.
    @Column(columnDefinition="INT default 0")
    protected int treeSortOrder;



    /** Returns this tree-based node as an entity **/
    public abstract E asEntity();


    /**
     * Adds a child entity, and ensures that all references are properly updated
     *
     * @param entity the entity to add
     */
    public void addChild(E entity) {
        // Add the entity to the end of the children list
        TreeBaseEntity<E> lastChild = getChildren().isEmpty() ? null : getChildren().get(getChildren().size() - 1);

        // Give it initial tree sort order. Won't really be correct until the tree sort order has
        // been re-computed for the entire tree.
        entity.setTreeSortOrder(lastChild == null ? getTreeSortOrder() : lastChild.getTreeSortOrder());

        getChildren().add(entity);
        entity.setParent(asEntity());
    }


    /**
     * Update the lineage to have the format "/root-id/.../parent-id/id"
     * @return if the lineage was updated
     */
    public boolean updateLineage() {
        String oldLineage = getLineage();
        setLineage(getParent() == null
                ? "/" + getId() + "/"
                : getParent().getLineage() + getId() + "/");
        return !getLineage().equals(oldLineage);
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(E entity) {
        return (entity == null || getSiblingSortOrder() == entity.getSiblingSortOrder())
                ? 0
                : (getSiblingSortOrder() < entity.getSiblingSortOrder() ? -1 : 1);
    }


    /**
     * Checks if this is a root entity
     *
     * @return if this is a root entity
     */
    @Transient
    public boolean isRoot() {
        return getParent() == null;
    }


    /**
     * Returns the lineage of this entity as a list, ordered with this entity first, and the root-most entity last
     * @return the lineage of this entity
     */
    public List<E> lineageAsList() {
        List<E> entities = new ArrayList<>();
        for (E a = asEntity(); a != null; a = a.getParent()) {
            entities.add(a);
        }
        return entities;
    }


    /**
     * Returns the parent lineage of this entity as a list, ordered with this entities parent first, and the root-most entity last
     * @return the parent lineage of this entity
     */
    public List<E> parentLineageAsList() {
        return lineageAsList().stream()
                .skip(1)
                .collect(Collectors.toList());
    }


    /**
     * If the entity is active, ensure that parent entities are active.
     * If the entity is inactive, ensure that child entities are inactive.
     */
    @SuppressWarnings("all")
    public void updateActiveFlag() {
        if (isActive()) {
            // Ensure that parent categories are active
            if (getParent() != null && !getParent().isActive()) {
                getParent().setActive(true);
                getParent().updateActiveFlag();
            }
        } else {
            // Ensure that child categories are inactive
            getChildren().stream()
                    .filter(E::isActive)
                    .forEach(child -> {
                        child.setActive(false);
                        child.updateActiveFlag();
                    });
        }
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public E getParent() {
        return parent;
    }

    public void setParent(E parent) {
        this.parent = parent;
    }

    public List<E> getChildren() {
        return children;
    }

    public void setChildren(List<E> children) {
        this.children = children;
    }

    public String getLineage() {
        return lineage;
    }

    public void setLineage(String lineage) {
        this.lineage = lineage;
    }

    public double getSiblingSortOrder() {
        return siblingSortOrder;
    }

    public void setSiblingSortOrder(double siblingSortOrder) {
        this.siblingSortOrder = siblingSortOrder;
    }

    public int getTreeSortOrder() {
        return treeSortOrder;
    }

    public void setTreeSortOrder(int treeSortOrder) {
        this.treeSortOrder = treeSortOrder;
    }

}
