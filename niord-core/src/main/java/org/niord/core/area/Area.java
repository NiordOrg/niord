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
import org.niord.core.area.vo.SystemAreaVo;
import org.niord.core.area.vo.SystemAreaVo.AreaMessageSorting;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.model.TreeBaseEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.AreaVo;

import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
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
        @NamedQuery(name="Area.findByLegacyId",
                query = "select a FROM Area a where a.legacyId = :legacyId"),
        @NamedQuery(name  = "Area.findRootAreas",
                query = "select distinct a from Area a left join fetch a.children where a.parent is null order by a.siblingSortOrder"),
        @NamedQuery(name  = "Area.findAreasWithDescs",
                query = "select distinct a from Area a left join fetch a.descs order by a.parent, a.siblingSortOrder"),
        @NamedQuery(name  = "Area.findAreasWithIds",
                query = "select distinct a from Area a left join fetch a.descs where a.id in (:ids)"),
        @NamedQuery(name  = "Area.findByMrn",
                query = "select a from Area a left join fetch a.descs where a.mrn = :mrn"),
        @NamedQuery(name  = "Area.findLastUpdated",
                query = "select max(a.updated) from Area a")
})
@SuppressWarnings("unused")
public class Area extends TreeBaseEntity<Area> implements ILocalizable<AreaDesc> {

    String legacyId;

    String mrn;

    AreaType type;

    @Column(columnDefinition = "GEOMETRY")
    Geometry geometry;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<AreaDesc> descs = new ArrayList<>();

    @Column(length = 256)
    String lineage;

    AreaMessageSorting messageSorting;
    Float originLatitude;   // For CW and CCW message sorting
    Float originLongitude;  // For CW and CCW message sorting
    Integer originAngle;    // For CW and CCW message sorting


    @ElementCollection
    List<String> editorFields = new ArrayList<>();

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


    /** {@inheritDoc} **/
    @Override
    public Area asEntity() {
        return this;
    }


    /** Updates this area from the given area */
    public void updateArea(AreaVo area, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Area.class);

        this.mrn = area.getMrn();
        this.active = area.isActive();
        this.id = area.getId();

        if (compFilter.includeParent() && area.getParent() != null) {
            parent = new Area(area.getParent(), filter);
        }
        if (area.getDescs() != null) {
            area.getDescs()
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }

        if (area instanceof SystemAreaVo) {
            SystemAreaVo sysArea = (SystemAreaVo)area;
            this.type = sysArea.getType();
            this.siblingSortOrder = sysArea.getSiblingSortOrder() == null ? 0 : sysArea.getSiblingSortOrder();
            this.messageSorting = sysArea.getMessageSorting();
            this.originLatitude = sysArea.getOriginLatitude();
            this.originLongitude = sysArea.getOriginLongitude();
            this.originAngle = sysArea.getOriginAngle();

            if (compFilter.includeGeometry()) {
                this.geometry = JtsConverter.toJts(sysArea.getGeometry());
            }

            if (compFilter.includeChildren() && sysArea.getChildren() != null) {
                sysArea.getChildren().stream()
                        .map(a -> new Area(a, filter))
                        .forEach(this::addChild);
            }
            if (sysArea.getEditorFields() != null) {
                editorFields.addAll(sysArea.getEditorFields());
            }
        }
    }


    /** Converts this entity to a value object */
    public <A extends AreaVo> A toVo(Class<A> clz, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Area.class);

        A area = newInstance(clz);
        area.setId(id);
        area.setMrn(mrn);
        area.setActive(active);

        if (area instanceof SystemAreaVo) {
            SystemAreaVo sysArea = (SystemAreaVo)area;
            if (compFilter.includeDetails()) {
                sysArea.setType(type);
                sysArea.setSiblingSortOrder(siblingSortOrder);
                sysArea.setMessageSorting(messageSorting);
                sysArea.setOriginLatitude(originLatitude);
                sysArea.setOriginLongitude(originLongitude);
                sysArea.setOriginAngle(originAngle);

                if (!editorFields.isEmpty()) {
                    sysArea.setEditorFields(new ArrayList<>(editorFields));
                }
            }

            if (compFilter.includeGeometry()) {
                sysArea.setGeometry(JtsConverter.fromJts(geometry));
            }

            if (compFilter.includeChildren()) {
                children.forEach(child -> sysArea.checkCreateChildren().add(child.toVo(SystemAreaVo.class, filter)));
            }
        }

        if (compFilter.includeParent() && parent != null) {
            area.setParent(parent.toVo(clz, filter));
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
     * Only checks relevant values, not e.g. database id, created date, parent and child areas, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the area has changed
     */
    @Transient
    public boolean hasChanged(Area template) {
        return !Objects.equals(siblingSortOrder, template.getSiblingSortOrder()) ||
                !Objects.equals(mrn, template.getMrn()) ||
                !Objects.equals(type, template.getType()) ||
                !Objects.equals(active, template.isActive()) ||
                descsChanged(template) ||
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

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    public AreaType getType() {
        return type;
    }

    public void setType(AreaType type) {
        this.type = type;
    }

    @Override
    public List<AreaDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<AreaDesc> descs) {
        this.descs = descs;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public AreaMessageSorting getMessageSorting() {
        return messageSorting;
    }

    public void setMessageSorting(AreaMessageSorting messageSorting) {
        this.messageSorting = messageSorting;
    }

    public Float getOriginLatitude() {
        return originLatitude;
    }

    public void setOriginLatitude(Float originLatitude) {
        this.originLatitude = originLatitude;
    }

    public Float getOriginLongitude() {
        return originLongitude;
    }

    public void setOriginLongitude(Float originLongitude) {
        this.originLongitude = originLongitude;
    }

    public Integer getOriginAngle() {
        return originAngle;
    }

    public void setOriginAngle(Integer originAngle) {
        this.originAngle = originAngle;
    }

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }
}

