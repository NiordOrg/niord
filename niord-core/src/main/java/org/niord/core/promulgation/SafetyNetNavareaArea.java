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

import org.niord.core.promulgation.vo.SafetyNetAreaVo.SafetyNetNavareaAreaVo;
import org.niord.model.DataFilter;
import org.niord.model.message.Type;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Used for defining a NAVAREA SafetyNET distribution area.
 * <p>
 * The model implemented in Niord is that used by the Inmarsat MSS (Maritime Safety Server System), rather than
 * the EGC (older model).
 * <p>
 * The best source of information is the IMO International SafetyNET manual.
 */
@Entity
@SuppressWarnings("unused")
public class SafetyNetNavareaArea extends SafetyNetArea {

    // Defines the valid NAVAREAs
    public enum NAVAREA {

        I("UK", 1),
        II("France", 2),
        III("Spain", 3),
        IV("United States", 4),
        V("Brazil", 5),
        VI("Argentina", 6),
        VII("South Africa", 7),
        VIII("India", 8),
        IX("Pakistan", 9),
        X("Australia", 10),
        XI("Japan", 11),
        XII("United States", 12),
        XIII("Russian Federation", 13),
        XIV("New Zealand", 14),
        XV("Chile", 15),
        XVI("Peru", 16),
        XVII("Canada", 17),
        XVIII("Canada", 18),
        XIX("Norway", 19),
        XX("Russian Federation", 20),
        XXI("Russian Federation", 21);

        final String coordinator;
        final int number;

        /** Constructor **/
        NAVAREA(String coordinator, int number) {
            this.coordinator = coordinator;
            this.number = number;
        }


        public String getCoordinator() {
            return coordinator;
        }

        public int getNumber() {
            return number;
        }
    }


    @Enumerated(EnumType.STRING)
    NAVAREA navarea;

    /** Constructor */
    public SafetyNetNavareaArea() {
    }


    /** Constructor */
    public SafetyNetNavareaArea(SafetyNetNavareaAreaVo area) {
        super(area);
        this.navarea = area.getNavarea();
    }


    /** Updates this entity from another entity **/
    public void update(SafetyNetNavareaArea area) {
        super.update(area);
        this.navarea = area.getNavarea();
    }


    /** Returns a value object representation of this entity **/
    @Override
    public SafetyNetNavareaAreaVo toVo(DataFilter filter) {
        SafetyNetNavareaAreaVo area = toVo(new SafetyNetNavareaAreaVo(), filter);
        if (filter.includeDetails()) {
            area.setNavarea(navarea);
        }
        return area;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsMessageType(Type messageType) {
        return messageType == Type.NAVAREA_WARNING;
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    public NAVAREA getNavarea() {
        return navarea;
    }

    public void setNavarea(NAVAREA navarea) {
        this.navarea = navarea;
    }
}
