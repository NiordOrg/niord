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
package org.niord.core.promulgation;

import org.niord.core.promulgation.vo.SafetyNetAreaVo.SafetyNetCircularAreaVo;
import org.niord.model.DataFilter;
import org.niord.model.message.Type;

import javax.persistence.Entity;

/**
 * Used for defining a circular SafetyNET distribution area.
 * <p>
 * Please refer to the IMO International SafetyNET Manual.
 */
@Entity
@SuppressWarnings("unused")
public class SafetyNetCircularArea extends SafetyNetArea {

    String center;
    String radius;

    /** Constructor */
    public SafetyNetCircularArea() {
    }


    /** Constructor */
    public SafetyNetCircularArea(SafetyNetCircularAreaVo area) {
        super(area);
        this.center = area.getCenter();
        this.radius = area.getRadius();
    }


    /** Updates this entity from another entity **/
    public void update(SafetyNetCircularArea area) {
        super.update(area);
        this.center = area.getCenter();
        this.radius = area.getRadius();
    }


    /** Returns a value object representation of this entity **/
    @Override
    public SafetyNetCircularAreaVo toVo(DataFilter filter) {
        SafetyNetCircularAreaVo area = toVo(new SafetyNetCircularAreaVo(), filter);
        if (filter.includeDetails()) {
            area.setCenter(center);
            area.setRadius(radius);
        }
        return area;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsMessageType(Type messageType) {
        // TODO: Verify assumption: Circular supports all message types except NAVAREA
        return super.supportsMessageType(messageType) && messageType != Type.NAVAREA_WARNING;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getCenter() {
        return center;
    }

    public void setCenter(String center) {
        this.center = center;
    }

    public String getRadius() {
        return radius;
    }

    public void setRadius(String radius) {
        this.radius = radius;
    }

}
