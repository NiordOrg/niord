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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang.StringUtils;
import org.niord.core.promulgation.IMailPromulgation;
import org.niord.core.promulgation.SafetyNetMessagePromulgation;
import org.niord.core.promulgation.SafetyNetMessagePromulgation.SafetyNetPriority;
import org.niord.core.promulgation.SafetyNetMessagePromulgation.SafetyNetRepetition;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the promulgation data associated with SafetyNET mailing list promulgation
 */
@SuppressWarnings("unused")
public class SafetyNetMessagePromulgationVo
        extends BaseMessagePromulgationVo<SafetyNetMessagePromulgation>
        implements IMailPromulgation {

    SafetyNetPriority priority = SafetyNetPriority.SAFETY;
    SafetyNetRepetition repetition = SafetyNetRepetition.REPETITION_1;
    List<SafetyNetAreaVo> areas = new ArrayList<>();    // Selectable areas
    String areaName;                                    // Selected area name
    String text;
    String safetyNetId;


    /** Constructor **/
    public SafetyNetMessagePromulgationVo() {
        super();
    }


    /** Constructor **/
    public SafetyNetMessagePromulgationVo(PromulgationTypeVo type) {
        super(type);
    }


    /** {@inheritDoc} **/
    @Override
    public SafetyNetMessagePromulgation toEntity() {
        return new SafetyNetMessagePromulgation(this);
    }


    /** {@inheritDoc} **/
    @Override
    public boolean promulgationDataDefined() {
        return StringUtils.isNotBlank(text)
                || StringUtils.isNotBlank(areaName);
    }


    /** Resets data of this message promulgation **/
    public SafetyNetMessagePromulgationVo reset() {
        priority = SafetyNetPriority.SAFETY;
        repetition = SafetyNetRepetition.REPETITION_1;
        text = null;
        areas.clear();
        areaName = null;
        safetyNetId = null;
        return this;
    }


    /** Returns the selected area **/
    public SafetyNetAreaVo selectedArea() {
        return areas.stream()
                .filter(a -> a.getName().equals(areaName))
                .findFirst()
                .orElse(null);
    }


    @JsonIgnore
    public String getRepetitionDescription() {
        return repetition == null
                ? ""
                : repetition.getCode() + " - " + repetition.getDescription();
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public SafetyNetPriority getPriority() {
        return priority;
    }

    public void setPriority(SafetyNetPriority priority) {
        this.priority = priority;
    }

    public SafetyNetRepetition getRepetition() {
        return repetition;
    }

    public void setRepetition(SafetyNetRepetition repetition) {
        this.repetition = repetition;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }

    public List<SafetyNetAreaVo> getAreas() {
        return areas;
    }

    public void setAreas(List<SafetyNetAreaVo> areas) {
        this.areas = areas;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getSafetyNetId() {
        return safetyNetId;
    }

    public void setSafetyNetId(String safetyNetId) {
        this.safetyNetId = safetyNetId;
    }
}
