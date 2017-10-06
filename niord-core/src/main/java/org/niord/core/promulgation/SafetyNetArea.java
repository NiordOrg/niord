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

import org.niord.core.model.BaseEntity;
import org.niord.core.promulgation.vo.SafetyNetAreaVo;
import org.niord.model.DataFilter;
import org.niord.model.message.MainType;
import org.niord.model.message.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;

/**
 * Defines a SafetyNET area.
 * <p>
 * The model implemented in Niord is that used by the Inmarsat MMS (Maritime Safety Server System), rather than
 * the EGC (older model).
 * <p>
 * The best source of information is the IMO International SafetyNET manual.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="SafetyNetArea.findByName",
                query="SELECT a FROM SafetyNetArea a where a.promulgationType.typeId = :typeId "
                        + " and lower(a.name) = lower(:name) order by a.sortOrder asc"),
        @NamedQuery(name="SafetyNetArea.findByType",
                query="SELECT a FROM SafetyNetArea a where a.promulgationType.typeId = :typeId "
                        + "  order by a.sortOrder asc")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@SuppressWarnings("unused")
public abstract class SafetyNetArea extends BaseEntity<Integer> {

    public static final int DEFAULT_SORT_ORDER = 100;


    /** SafetyNET Transmission Repetition */
    public enum SafetyNetRepetition {
        REPETITION_01("01", "Transmit once"),
        REPETITION_11("11", "Two transmissions, 6 min. interval"),
        REPETITION_16("16", "Every 12 hours"),
        REPETITION_42("42", "Every 4 hours");

        final String code;
        final String description;

        SafetyNetRepetition(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }


    @NotNull
    @ManyToOne
    PromulgationType promulgationType;

    boolean active;

    // The name of the area
    @Column(nullable = false, unique = true)
    String name;

    SafetyNetRepetition repetition;

    int sortOrder = DEFAULT_SORT_ORDER;


    /** Constructor */
    public SafetyNetArea() {
    }


    /** Constructor */
    public SafetyNetArea(SafetyNetAreaVo area) {
        if (area.getPromulgationType() != null) {
            this.promulgationType = new PromulgationType(area.getPromulgationType());
        }
        this.active = area.isActive();
        this.name  = area.getName();
        this.repetition = area.getRepetition();
        this.sortOrder = area.getSortOrder() != null ? area.getSortOrder() : DEFAULT_SORT_ORDER;
    }


    /** Updates this entity from another entity **/
    public void update(SafetyNetArea area) {
        // NB: We never update promulgationType of an area
        this.active = area.isActive();
        this.name  = area.getName();
        this.repetition = area.getRepetition();
        this.sortOrder = area.getSortOrder();
    }


    /** Returns a value object representation of this entity **/
    public abstract SafetyNetAreaVo toVo(DataFilter filter);


    /** Updates a value object representation of this entity **/
    public <A extends SafetyNetAreaVo> A toVo(A area, DataFilter filter) {
        if (filter.includeDetails()) {
            area.setPromulgationType(promulgationType.toVo());
            area.setSortOrder(sortOrder);
        }
        area.setName(name);
        area.setActive(active);
        area.setRepetition(repetition);
        return area;
    }


    /**
     * Returns if the given SafetyNET area supports the given message type
     * @param messageType the message type to match
     * @return if the given SafetyNET area supports the given message type
     */
    public boolean supportsMessageType(Type messageType) {
        // Default is to support all NW types
        return messageType != null && messageType.getMainType() == MainType.NW;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public PromulgationType getPromulgationType() {
        return promulgationType;
    }

    public void setPromulgationType(PromulgationType promulgationType) {
        this.promulgationType = promulgationType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SafetyNetRepetition getRepetition() {
        return repetition;
    }

    public void setRepetition(SafetyNetRepetition repetition) {
        this.repetition = repetition;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
