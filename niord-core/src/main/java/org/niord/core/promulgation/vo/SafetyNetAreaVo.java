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
package org.niord.core.promulgation.vo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.niord.core.promulgation.SafetyNetArea;
import org.niord.core.promulgation.SafetyNetCircularArea;
import org.niord.core.promulgation.SafetyNetCoastalArea;
import org.niord.core.promulgation.SafetyNetCoastalArea.CoastalArea;
import org.niord.core.promulgation.SafetyNetNavareaArea;
import org.niord.core.promulgation.SafetyNetNavareaArea.NAVAREA;
import org.niord.core.promulgation.SafetyNetRectangularArea;
import org.niord.model.IJsonSerializable;

/**
 * Defines a SafetyNET area.
 * <p>
 * The model implemented in Niord is that used by the Inmarsat MMS (Maritime Safety Server System), rather than
 * the EGC (older model).
 * <p>
 * The best source of information is the IMO International SafetyNET manual.
 */
@SuppressWarnings("unused")
@JsonTypeInfo(use= JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value=SafetyNetAreaVo.SafetyNetCircularAreaVo.class, name="CIRCULAR"),
        @JsonSubTypes.Type(value=SafetyNetAreaVo.SafetyNetRectangularAreaVo.class, name="RECTANGULAR"),
        @JsonSubTypes.Type(value=SafetyNetAreaVo.SafetyNetCoastalAreaVo.class, name="COASTAL"),
        @JsonSubTypes.Type(value=SafetyNetAreaVo.SafetyNetNavareaAreaVo.class, name="NAVAREA")
})
public class SafetyNetAreaVo implements IJsonSerializable {

    PromulgationTypeVo promulgationType;
    String name;
    boolean active;
    Integer priority;


    /**
     * Returns the entity associated with this value object
     */
    public SafetyNetArea toEntity() {
        SafetyNetArea area = null;
        if (this instanceof SafetyNetCircularAreaVo) {
            area = new SafetyNetCircularArea((SafetyNetCircularAreaVo) this);
        } else if (this instanceof SafetyNetRectangularAreaVo) {
            area = new SafetyNetRectangularArea((SafetyNetRectangularAreaVo) this);
        } else if (this instanceof SafetyNetCoastalAreaVo) {
            area = new SafetyNetCoastalArea((SafetyNetCoastalAreaVo) this);
        } else if (this instanceof SafetyNetNavareaAreaVo) {
            area = new SafetyNetNavareaArea((SafetyNetNavareaAreaVo) this);
        }
        return area;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public PromulgationTypeVo getPromulgationType() {
        return promulgationType;
    }

    public void setPromulgationType(PromulgationTypeVo promulgationType) {
        this.promulgationType = promulgationType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /*******************************************/
    /** SafetyNetCircularAreaVo sub-class     **/
    /*******************************************/

    public static class SafetyNetCircularAreaVo extends SafetyNetAreaVo {
        String center;
        String radius;

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


    /*******************************************/
    /** SafetyNetRectangularAreaVo sub-class  **/
    /*******************************************/

    public static class SafetyNetRectangularAreaVo extends SafetyNetAreaVo {
        String swCorner;
        String northings;
        String eastings;


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


    /*******************************************/
    /** SafetyNetCoastalAreaVo sub-class      **/
    /*******************************************/

    public static class SafetyNetCoastalAreaVo extends SafetyNetNavareaAreaVo {
        CoastalArea coastalArea;

        public CoastalArea getCoastalArea() {
            return coastalArea;
        }

        public void setCoastalArea(CoastalArea coastalArea) {
            this.coastalArea = coastalArea;
        }
    }


    /*******************************************/
    /** SafetyNetNavareaAreaVo sub-class      **/
    /*******************************************/

    public static class SafetyNetNavareaAreaVo extends SafetyNetAreaVo {
        NAVAREA navarea;

        public NAVAREA getNavarea() {
            return navarea;
        }

        public void setNavarea(NAVAREA navarea) {
            this.navarea = navarea;
        }
    }
}
