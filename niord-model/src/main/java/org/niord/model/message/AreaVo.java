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
package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;
import org.niord.model.geojson.GeometryVo;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Value object for the {@code Area} model entity
 */
@ApiModel(value = "Area", description = "Hierarchical area model")
@XmlRootElement(name = "area")
@XmlType(propOrder = {
        "mrn", "type", "active", "parent", "geometry", "children", "descs",
        "messageSorting", "originLatitude", "originLongitude", "originAngle"
})
@SuppressWarnings("unused")
public class AreaVo implements ILocalizable<AreaDescVo>, IJsonSerializable, Comparable<AreaVo> {

    // Defines the sorting type of messages withing this area
    public enum AreaMessageSorting { NS, SN, EW, WE, CW, CCW }

    Integer id;
    String mrn;
    AreaType type;
    boolean active = true;
    AreaVo parent;
    GeometryVo geometry;
    List<AreaVo> children;
    Double siblingSortOrder;
    AreaMessageSorting messageSorting;
    Float originLatitude;   // For CW and CCW message sorting
    Float originLongitude;  // For CW and CCW message sorting
    Integer originAngle;    // For CW and CCW message sorting
    List<AreaDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public int compareTo(AreaVo area) {
        return (area == null || siblingSortOrder == area.getSiblingSortOrder()) ? 0 : (siblingSortOrder < area.getSiblingSortOrder() ? -1 : 1);
    }


    /**
     * Recursively sorts the children
     */
    public void sortChildren() {
        if (children != null) {
            Collections.sort(children);
            children.forEach(AreaVo::sortChildren);
        }
    }


    /** {@inheritDoc} */
    @Override
    public AreaDescVo createDesc(String lang) {
        AreaDescVo desc = new AreaDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /** Returns the list of child areas, and creates an empty list if needed */
    public List<AreaVo> checkCreateChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public AreaVo getParent() {
        return parent;
    }

    public void setParent(AreaVo parent) {
        this.parent = parent;
    }

    public GeometryVo getGeometry() {
        return geometry;
    }

    public void setGeometry(GeometryVo geometry) {
        this.geometry = geometry;
    }

    public List<AreaVo> getChildren() {
        return children;
    }

    public void setChildren(List<AreaVo> children) {
        this.children = children;
    }

    @XmlAttribute
    public Double getSiblingSortOrder() {
        return siblingSortOrder;
    }

    public void setSiblingSortOrder(Double siblingSortOrder) {
        this.siblingSortOrder = siblingSortOrder;
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

    @Override
    public List<AreaDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<AreaDescVo> descs) {
        this.descs = descs;
    }
}
