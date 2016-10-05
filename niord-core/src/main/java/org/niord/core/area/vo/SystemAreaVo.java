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

package org.niord.core.area.vo;

import org.niord.model.geojson.GeometryVo;
import org.niord.model.message.AreaType;
import org.niord.model.message.AreaVo;

import javax.xml.bind.annotation.XmlAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extends the {@linkplain AreaVo} model with system-specific fields and attributes.
 */
@SuppressWarnings("unused")
public class SystemAreaVo extends AreaVo implements Comparable<SystemAreaVo> {

    // Defines the sorting type of messages withing this area
    public enum AreaMessageSorting { NS, SN, EW, WE, CW, CCW }

    AreaType type;
    GeometryVo geometry;
    List<SystemAreaVo> children;
    Double siblingSortOrder;
    AreaMessageSorting messageSorting;
    Float originLatitude;   // For CW and CCW message sorting
    Float originLongitude;  // For CW and CCW message sorting
    Integer originAngle;    // For CW and CCW message sorting
    List<String> editorFields;


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(SystemAreaVo area) {
        return (area == null || siblingSortOrder == area.getSiblingSortOrder()) ? 0 : (siblingSortOrder < area.getSiblingSortOrder() ? -1 : 1);
    }


    /** Returns the list of child areas, and creates an empty list if needed */
    public List<SystemAreaVo> checkCreateChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
    }

    /**
     * Recursively sorts the children
     */
    public void sortChildren() {
        if (children != null) {
            Collections.sort(children);
            children.forEach(SystemAreaVo::sortChildren);
        }
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public AreaType getType() {
        return type;
    }

    public void setType(AreaType type) {
        this.type = type;
    }

    public GeometryVo getGeometry() {
        return geometry;
    }

    public void setGeometry(GeometryVo geometry) {
        this.geometry = geometry;
    }

    public List<SystemAreaVo> getChildren() {
        return children;
    }

    public void setChildren(List<SystemAreaVo> children) {
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

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }
}
