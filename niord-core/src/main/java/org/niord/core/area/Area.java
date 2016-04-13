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

import com.vividsolutions.jts.geom.Geometry;
import org.apache.commons.lang.StringUtils;
import org.niord.core.model.VersionedEntity;
import org.niord.core.util.GeoJsonUtils;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.vo.AreaVo;

import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a specific named area, part of an area-hierarchy
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name  = "Area.findRootAreas",
                query = "select distinct a from Area a left join fetch a.children where a.parent is null order by a.siblingSortOrder"),
        @NamedQuery(name  = "Area.findAreasWithDescs",
                query = "select distinct a from Area a left join fetch a.descs order by a.parent, a.siblingSortOrder"),
        @NamedQuery(name  = "Area.findAreasWithIds",
                query = "select distinct a from Area a left join fetch a.descs where a.id in (:ids)"),
        @NamedQuery(name  = "Area.findLastUpdated",
                query = "select max(a.updated) from Area a")
})
@SuppressWarnings("unused")
public class Area extends VersionedEntity<Integer> implements ILocalizable<AreaDesc>, Comparable<Area> {

    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH })
    private Area parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @OrderBy("siblingSortOrder ASC")
    private List<Area> children = new ArrayList<>();

    @Column(columnDefinition = "GEOMETRY")
    Geometry geometry;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<AreaDesc> descs = new ArrayList<>();

    @Column(length = 256)
    String lineage;

    // The sortOrder is used to sort this area among siblings, and exposed via the Admin UI
    @Column(columnDefinition="DOUBLE default 0.0")
    double siblingSortOrder;

    // The treeSortOrder is re-computed at regular intervals by the system and designates
    // the index of the area in an entire sorted area tree. Used for area sorting.
    @Column(columnDefinition="INT default 0")
    int treeSortOrder;


    /** Constructor */
    public Area() {
    }


    /** Constructor */
    public Area(AreaVo area) {
        this(area, DataFilter.get().fields("geometry"));
    }


    /** Constructor */
    public Area(AreaVo area, DataFilter filter) {
        updateArea(area, filter);
    }


    /** Updates this area from the given area */
    public void updateArea(AreaVo area, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Area.class);

        this.id = area.getId();
        this.siblingSortOrder = area.getSiblingSortOrder();
        if (compFilter.includeGeometry()) {
            this.geometry = GeoJsonUtils.toJts(area.getGeometry());
        }
        if (compFilter.includeParent() && area.getParent() != null) {
            parent = new Area(area.getParent(), filter);
        }
        if (compFilter.includeChildren() && area.getChildren() != null) {
            area.getChildren().stream()
                    .map(a -> new Area(a, filter))
                    .forEach(this::addChild);
        }
        if (area.getDescs() != null) {
            area.getDescs().stream()
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }
    }


    /** Converts this entity to a value object */
    public AreaVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Area.class);

        AreaVo area = new AreaVo();
        area.setId(id);
        area.setSiblingSortOrder(siblingSortOrder);

        if (compFilter.includeGeometry()) {
            area.setGeometry(GeoJsonUtils.fromJts(geometry));
        }

        if (compFilter.includeChildren()) {
            children.forEach(child -> area.checkCreateChildren().add(child.toVo(compFilter)));
        }

        if (compFilter.includeParent() && parent != null) {
            area.setParent(parent.toVo(compFilter));
        } else if (compFilter.includeParentId() && parent != null) {
            AreaVo parent = new AreaVo();
            parent.setId(parent.getId());
            area.setParent(parent);
        }

        if (!descs.isEmpty()) {
            area.setDescs(getDescs(filter).stream()
                .map(AreaDesc::toVo)
                .collect(Collectors.toList()));
        }
        return area;
    }


    /**
     * Checks if the values of the area has changed.
     * Only checks relevant values, not e.g. database id, created date, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the area has changed
     */
    @Transient
    public boolean hasChanged(Area template) {
        return !Objects.equals(siblingSortOrder, template.getSiblingSortOrder()) ||
                descsChanged(template) ||
                parentChanged(template) ||
                geometryChanged(template);
    }


    /** Checks if the geometry has changed */
    protected boolean geometryChanged(Area template) {
        if (geometry == null && template.getGeometry() == null) {
            return false;
        } else if (geometry == null || template.getGeometry() == null) {
            return true;
        }
        return !geometry.equals(template.getGeometry());
    }


    /** Checks if the geometry has changed */
    private boolean descsChanged(Area template) {
        return descs.size() != template.getDescs().size() ||
                descs.stream()
                    .anyMatch(d -> template.getDesc(d.getLang()) == null ||
                            !Objects.equals(d.getName(), template.getDesc(d.getLang()).getName()));
    }

    /** Checks if the parents have changed */
    private boolean parentChanged(Area template) {
        return (parent == null && template.getParent() != null) ||
                (parent != null && template.getParent() == null) ||
                (parent != null && template.getParent() != null &&
                        !Objects.equals(parent.getId(), template.getParent().getId()));
    }

    /** {@inheritDoc} */
    @Override
    public AreaDesc createDesc(String lang) {
        AreaDesc desc = new AreaDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /**
     * Adds a child area, and ensures that all references are properly updated
     *
     * @param area the area to add
     */
    public void addChild(Area area) {
        // Add the area to the end of the children list
        Area lastChild = children.isEmpty() ? null : children.get(children.size() - 1);
        area.setSiblingSortOrder(lastChild == null ? Math.random() : lastChild.getSiblingSortOrder() + 10.0d);

        // Give it initial tree sort order. Won't really be correct until the tree sort order has
        // been re-computed for the entire tree.
        area.setTreeSortOrder(lastChild == null ? treeSortOrder : lastChild.getTreeSortOrder());

        children.add(area);
        area.setParent(this);
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


    /** {@inheritDoc} */
    @Override
    public int compareTo(Area area) {
        return (area == null || siblingSortOrder == area.getSiblingSortOrder())
                ? 0
                : (siblingSortOrder < area.getSiblingSortOrder() ? -1 : 1);
    }


    /**
     * Checks if this is a root area
     *
     * @return if this is a root area
     */
    @Transient
    public boolean isRootArea() {
        return parent == null;
    }


    /**
     * By convention, a list of areas will be emitted top-to-bottom. So, if we have a list with:
     * [ Kattegat -> Danmark, Skagerak -> Danmark, Hamborg -> Tyskland ], the resulting title
     * line should be: "Danmark. Tyskland. Kattegat. Skagerak. Hamborg."
     * @param areas the areas to compute a title line prefix for
     * @return the title line prefix
     */
    public static String computeAreaTitlePrefix(List<Area> areas, String language) {
        List<List<String>> areaNames = new ArrayList<>();
        int maxLevels = 0;
        for (Area a  : areas) {
            List<String> areaLineageNames = new ArrayList<>();
            for (Area area = a; area != null; area = area.getParent()) {
                AreaDesc desc = area.getDescs(DataFilter.get().lang(language)).get(0);
                if (!StringUtils.isBlank(desc.getName())) {
                    areaLineageNames.add(desc.getName());
                }
            }
            Collections.reverse(areaLineageNames);
            areaNames.add(areaLineageNames);
            maxLevels = Math.max(maxLevels, areaLineageNames.size());
        }

        StringBuilder str = new StringBuilder();
        for (int x = 0; x < maxLevels; x++) {
            Set<String> emittedNames = new HashSet<>();
            for (List<String> areaLineageNames : areaNames) {
                if (areaLineageNames.size() > x) {
                    String name = areaLineageNames.get(x);
                    if (!emittedNames.contains(name)) {
                        emittedNames.add(name);
                        str.append(name);
                        if (!name.endsWith(".")) {
                            str.append(".");
                        }
                        str.append(" ");
                    }
                }
            }
        }

        return str.toString().trim();
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public List<AreaDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<AreaDesc> descs) {
        this.descs = descs;
    }

    public Area getParent() {
        return parent;
    }

    public void setParent(Area parent) {
        this.parent = parent;
    }

    public List<Area> getChildren() {
        return children;
    }

    public void setChildren(List<Area> children) {
        this.children = children;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
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

    public void setSiblingSortOrder(double sortOrder) {
        this.siblingSortOrder = sortOrder;
    }

    public int getTreeSortOrder() {
        return treeSortOrder;
    }

    public void setTreeSortOrder(int treeSortOrder) {
        this.treeSortOrder = treeSortOrder;
    }
}

