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

import org.niord.core.promulgation.vo.SafetyNetAreaVo;
import org.niord.core.promulgation.vo.SafetyNetMessagePromulgationVo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * Defines the message promulgation entity associated with SafetyNET mailing list promulgations
 */
@Entity
@DiscriminatorValue(SafetyNetMessagePromulgation.SERVICE_ID)
@SuppressWarnings("unused")
public class SafetyNetMessagePromulgation
        extends BaseMessagePromulgation<SafetyNetMessagePromulgationVo>
        implements IMailPromulgation {

    public static final String SERVICE_ID = "safetynet";

    /** SafetyNET Priority */
    public enum SafetyNetPriority {
        SAFETY,
        URGENCY,
        DISTRESS // NB: Distress not used for Navigational Warnings
    }

    @NotNull
    @Enumerated(EnumType.STRING)
    SafetyNetPriority priority = SafetyNetPriority.SAFETY;

    @NotNull
    @ManyToOne
    SafetyNetArea area;

    @Lob
    @Column(length = 16_777_216)
    String text;

    // The actual ID assigned to the SafetyNET message by the distributor (e.g. SHOM for NAVAREA II)
    String safetyNetId;


    /** Constructor **/
    public SafetyNetMessagePromulgation() {
        super();
    }


    /** Constructor **/
    public SafetyNetMessagePromulgation(SafetyNetMessagePromulgationVo promulgation) {
        super(promulgation);

        this.priority = promulgation.getPriority();
        this.text = promulgation.getText();
        this.safetyNetId = promulgation.getSafetyNetId();

        SafetyNetAreaVo a = promulgation.selectedArea();
        this.area = a != null ? a.toEntity() : null;
    }


    /** Returns a value object for this entity */
    @Override
    public SafetyNetMessagePromulgationVo toVo() {

        SafetyNetMessagePromulgationVo data = toVo(new SafetyNetMessagePromulgationVo());

        data.setPriority(priority);
        data.setText(text);
        data.setSafetyNetId(safetyNetId);
        if (area != null) {
            data.setAreaName(area.getName());
        }
        return data;
    }


    /** Updates this promulgation from another promulgation **/
    @Override
    public void update(BaseMessagePromulgation promulgation) {
        if (promulgation instanceof SafetyNetMessagePromulgation) {
            super.update(promulgation);

            SafetyNetMessagePromulgation p = (SafetyNetMessagePromulgation)promulgation;
            this.priority = p.getPriority();
            this.text = p.getText();
            this.safetyNetId = p.getSafetyNetId();
            this.area = p.getArea();
        }
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }

    public SafetyNetPriority getPriority() {
        return priority;
    }

    public void setPriority(SafetyNetPriority priority) {
        this.priority = priority;
    }

    public SafetyNetArea getArea() {
        return area;
    }

    public void setArea(SafetyNetArea area) {
        this.area = area;
    }

    public String getSafetyNetId() {
        return safetyNetId;
    }

    public void setSafetyNetId(String safetyNetId) {
        this.safetyNetId = safetyNetId;
    }
}
