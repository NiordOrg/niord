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

import org.niord.core.promulgation.vo.SafetyNetAreaVo.SafetyNetRectangularAreaVo;
import org.niord.model.DataFilter;
import org.niord.model.message.Type;

import jakarta.persistence.Entity;

/**
 * Used for defining a rectangular SafetyNET distribution area.
 * <p>
 * The model implemented in Niord is that used by the Inmarsat MSS (Maritime Safety Server System), rather than
 * the EGC (older model).
 * <p>
 * The best source of information is the IMO International SafetyNET manual.
 */
@Entity
@SuppressWarnings("unused")
public class SafetyNetRectangularArea extends SafetyNetArea {

    String swCorner;
    String northings;
    String eastings;

    /** Constructor */
    public SafetyNetRectangularArea() {
    }


    /** Constructor */
    public SafetyNetRectangularArea(SafetyNetRectangularAreaVo area) {
        super(area);
        this.swCorner = area.getSwCorner();
        this.northings = area.getNorthings();
        this.eastings = area.getEastings();
    }


    /** Updates this entity from another entity **/
    public void update(SafetyNetRectangularArea area) {
        super.update(area);
        this.swCorner = area.getSwCorner();
        this.northings = area.getNorthings();
        this.eastings = area.getEastings();
    }


    /** Returns a value object representation of this entity **/
    @Override
    public SafetyNetRectangularAreaVo toVo(DataFilter filter) {
        SafetyNetRectangularAreaVo area = toVo(new SafetyNetRectangularAreaVo(), filter);
        if (filter.includeDetails()) {
            area.setSwCorner(swCorner);
            area.setNorthings(northings);
            area.setEastings(eastings);
        }
        return area;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsMessageType(Type messageType) {
        // TODO: Verify assumption: Rectangular supports all message types except NAVAREA
        return super.supportsMessageType(messageType) && messageType != Type.NAVAREA_WARNING;
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    public String getSwCorner() {
        return swCorner;
    }

    public void setSwCorner(String swCorner) {
        this.swCorner = swCorner;
    }

    public String getNorthings() {
        return northings;
    }

    public void setNorthings(String northings) {
        this.northings = northings;
    }

    public String getEastings() {
        return eastings;
    }

    public void setEastings(String eastings) {
        this.eastings = eastings;
    }
}
