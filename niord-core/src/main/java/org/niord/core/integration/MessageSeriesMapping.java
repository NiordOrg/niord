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

package org.niord.core.integration;

import org.niord.core.integration.vo.MessageSeriesMappingVo;
import org.niord.core.model.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * Defines which message series to import from the Niord server and
 * how to map the import message series ID
 */
@Entity
@SuppressWarnings("unused")
public class MessageSeriesMapping extends BaseEntity<Integer> {

    @NotNull
    @ManyToOne
    NiordIntegration niordIntegration;

    @NotNull
    String sourceSeriesId;

    @NotNull
    String targetSeriesId;


    /** No-argument constructor */
    public MessageSeriesMapping() {
    }


    /** Constructor */
    public MessageSeriesMapping(MessageSeriesMappingVo mapping) {
        this.sourceSeriesId = mapping.getSourceSeriesId();
        this.targetSeriesId = mapping.getTargetSeriesId();
    }


    /** Converts this entity to a value object */
    public MessageSeriesMappingVo toVo() {
        MessageSeriesMappingVo mapping = new MessageSeriesMappingVo();
        mapping.setSourceSeriesId(sourceSeriesId);
        mapping.setTargetSeriesId(targetSeriesId);

        return mapping;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public NiordIntegration getNiordIntegration() {
        return niordIntegration;
    }

    public void setNiordIntegration(NiordIntegration niordIntegration) {
        this.niordIntegration = niordIntegration;
    }

    public String getSourceSeriesId() {
        return sourceSeriesId;
    }

    public void setSourceSeriesId(String sourceSeriesId) {
        this.sourceSeriesId = sourceSeriesId;
    }

    public String getTargetSeriesId() {
        return targetSeriesId;
    }

    public void setTargetSeriesId(String targetSeriesId) {
        this.targetSeriesId = targetSeriesId;
    }
}
