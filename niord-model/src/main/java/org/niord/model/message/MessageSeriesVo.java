/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.model.message;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents a message series
 */
@Schema(name = "MessageSeries", description = "A message series")
@XmlRootElement(name = "messageSeries")
@XmlType(propOrder = {
        "seriesId", "mainType"
})
@SuppressWarnings("unused")
public class MessageSeriesVo implements IJsonSerializable {

    String seriesId;
    MainType mainType;


    /** Returns a filtered copy of this entity **/
    public MessageSeriesVo copy(DataFilter filter) {

        DataFilter compFilter = filter.forComponent("MessageSeries");

        MessageSeriesVo series = new MessageSeriesVo();
        series.setSeriesId(seriesId);
        if (compFilter.includeDetails()) {
            series.setMainType(mainType);
        }
        return series;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public MainType getMainType() {
        return mainType;
    }

    public void setMainType(MainType mainType) {
        this.mainType = mainType;
    }
}
