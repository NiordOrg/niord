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

package org.niord.core.integration.vo;

import org.apache.commons.lang.StringUtils;
import org.niord.model.IJsonSerializable;

/**
 * Value object for the {@code MessageSeriesMapping} class
 */
@SuppressWarnings("unused")
public class MessageSeriesMappingVo implements IJsonSerializable {

    String sourceSeriesId;
    String targetSeriesId;


    /** Returns if the mapping is properly defined **/
    public boolean mappingDefined() {
        return StringUtils.isNotBlank(sourceSeriesId) && StringUtils.isNotBlank(targetSeriesId);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

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
