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
package org.niord.core.category;

import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.vo.CategoryVo;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a specific named category, part of an category-hierarchy
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name  = "Category.findRootCategories",
                query = "select distinct c from Category c left join fetch c.children where c.parent is null"),
        @NamedQuery(name  = "Category.findCategoriesWithDescs",
                query = "select distinct c from Category c left join fetch c.descs"),
        @NamedQuery(name  = "Category.findCategoriesWithIds",
                query = "select distinct c from Category c left join fetch c.descs where c.id in (:ids)")
})
@SuppressWarnings("unused")
public class Category extends VersionedEntity<Integer> implements ILocalizable<CategoryDesc> {

    String mrn;

    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH })
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Category> children = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<CategoryDesc> descs = new ArrayList<>();

    @Column(length = 256)
    String lineage;


    /** Constructor */
    public Category() {
    }


    /** Constructor */
    public Category(CategoryVo category) {
        this(category, DataFilter.get());
    }


    /** Constructor */
    public Category(CategoryVo category, DataFilter filter) {
        updateCategory(category, filter);
    }


    /** Updates this category from the given category */
    public void updateCategory(CategoryVo category, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Category.class);

        this.id = category.getId();
        this.mrn = category.getMrn();
        if (compFilter.includeParent() && category.getParent() != null) {
            parent = new Category(category.getParent(), filter);
        }
        if (compFilter.includeChildren() && category.getChildren() != null) {
            category.getChildren().stream()
                    .map(a -> new Category(a, filter))
                    .forEach(this::addChild);
        }
        if (category.getDescs() != null) {
            category.getDescs().stream()
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }
    }


    /** Converts this entity to a value object */
    public CategoryVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Category.class);

        CategoryVo category = new CategoryVo();
        category.setId(id);
        category.setMrn(mrn);

        if (compFilter.includeChildren()) {
            children.forEach(child -> category.checkCreateChildren().add(child.toVo(compFilter)));
        }

        if (compFilter.includeParent() && parent != null) {
            category.setParent(parent.toVo(compFilter));
        } else if (compFilter.includeParentId() && parent != null) {
            CategoryVo parent = new CategoryVo();
            parent.setId(parent.getId());
            category.setParent(parent);
        }

        if (!descs.isEmpty()) {
            category.setDescs(getDescs(filter).stream()
                .map(CategoryDesc::toVo)
                .collect(Collectors.toList()));
        }
        return category;
    }


    /**
     * Checks if the values of the category has changed.
     * Only checks relevant values, not e.g. database id, created date, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the category has changed
     */
    @Transient
    public boolean hasChanged(Category template) {
        return !Objects.equals(mrn, template.getMrn()) ||
                descsChanged(template) ||
                parentChanged(template);
    }


    /** Checks if the geometry has changed */
    private boolean descsChanged(Category template) {
        return descs.size() != template.getDescs().size() ||
                descs.stream()
                    .anyMatch(d -> template.getDesc(d.getLang()) == null ||
                            !Objects.equals(d.getName(), template.getDesc(d.getLang()).getName()));
    }

    /** Checks if the parents have changed */
    private boolean parentChanged(Category template) {
        return (parent == null && template.getParent() != null) ||
                (parent != null && template.getParent() == null) ||
                (parent != null && template.getParent() != null &&
                        !Objects.equals(parent.getId(), template.getParent().getId()));
    }

    /** {@inheritDoc} */
    @Override
    public CategoryDesc createDesc(String lang) {
        CategoryDesc desc = new CategoryDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /**
     * Adds a child category, and ensures that all references are properly updated
     *
     * @param category the category to add
     */
    public void addChild(Category category) {
        children.add(category);
        category.setParent(this);
    }


    /**
     * Update the lineage to have the format "/root-id/.../parent-id/id"
     * @return if the lineage was updated
     */
    public boolean updateLineage() {
        String oldLineage = lineage;
        lineage = getParent() == null
                ? "/" + id + "/"
                : getParent().getLineage() + id + "/";
        return !lineage.equals(oldLineage);
    }


    /**
     * Checks if this is a root category
     *
     * @return if this is a root category
     */
    @Transient
    public boolean isRootCategory() {
        return parent == null;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    @Override
    public List<CategoryDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<CategoryDesc> descs) {
        this.descs = descs;
    }

    public Category getParent() {
        return parent;
    }

    public void setParent(Category parent) {
        this.parent = parent;
    }

    public List<Category> getChildren() {
        return children;
    }

    public void setChildren(List<Category> children) {
        this.children = children;
    }

    public String getLineage() {
        return lineage;
    }

    public void setLineage(String lineage) {
        this.lineage = lineage;
    }
}

