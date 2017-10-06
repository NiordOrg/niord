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

import org.niord.core.promulgation.vo.SafetyNetAreaVo.SafetyNetCoastalAreaVo;
import org.niord.model.DataFilter;
import org.niord.model.message.Type;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * Used for defining a coastal SafetyNET distribution area.
 * <p>
 * The model implemented in Niord is that used by the Inmarsat MSS (Maritime Safety Server System), rather than
 * the EGC (older model).
 * <p>
 * The best source of information is the IMO International SafetyNET manual.
 */
@Entity
@SuppressWarnings("unused")
public class SafetyNetCoastalArea extends SafetyNetNavareaArea {

    public enum CoastalArea { A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z }

    @Enumerated(EnumType.STRING)
    CoastalArea coastalArea;

    /** Constructor */
    public SafetyNetCoastalArea() {
    }


    /** Constructor */
    public SafetyNetCoastalArea(SafetyNetCoastalAreaVo area) {
        super(area);
        this.coastalArea = area.getCoastalArea();
    }


    /** Updates this entity from another entity **/
    public void update(SafetyNetCoastalArea area) {
        super.update(area);
        this.coastalArea = area.getCoastalArea();
    }

    /** Returns a value object representation of this entity **/
    @Override
    public SafetyNetCoastalAreaVo toVo(DataFilter filter) {
        SafetyNetCoastalAreaVo area = toVo(new SafetyNetCoastalAreaVo(), filter);
        if (filter.includeDetails()) {
            area.setNavarea(getNavarea());
            area.setCoastalArea(coastalArea);
        }
        return area;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsMessageType(Type messageType) {
        // Only use for coastal warnings
        return messageType == Type.COASTAL_WARNING;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public CoastalArea getCoastalArea() {
        return coastalArea;
    }

    public void setCoastalArea(CoastalArea coastalArea) {
        this.coastalArea = coastalArea;
    }
}
