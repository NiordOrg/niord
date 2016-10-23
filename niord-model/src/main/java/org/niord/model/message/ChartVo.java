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

import io.swagger.annotations.ApiModel;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents a chart
 */
@ApiModel(value = "Chart", description = "Sea chart model")
@XmlRootElement(name = "chart")
@XmlType(propOrder = {
        "chartNumber", "internationalNumber", "active", "scale", "name"
})
public class ChartVo implements IJsonSerializable {

    String chartNumber;
    Integer internationalNumber;
    boolean active = true;
    Integer scale;
    String name;


    /** Returns a filtered copy of this entity **/
    public ChartVo copy(DataFilter filter) {

        DataFilter compFilter = filter.forComponent("Chart");

        ChartVo chart = new ChartVo();
        chart.setChartNumber(chartNumber);
        chart.setInternationalNumber(internationalNumber);
        chart.setActive(active);
        chart.setScale(scale);
        if (compFilter.includeDetails()) {
            chart.setName(name);
        }
        return chart;
    }


    /**
     * Returns a string representation of the chart including chart number and international number
     * @return a string representation of the chart
     */
    @SuppressWarnings("unused")
    public String toFullChartNumber() {
        return (internationalNumber == null)
                ? chartNumber
                : String.format("%s (INT %d)", chartNumber, internationalNumber);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getChartNumber() {
        return chartNumber;
    }

    public void setChartNumber(String chartNumber) {
        this.chartNumber = chartNumber;
    }

    public Integer getInternationalNumber() {
        return internationalNumber;
    }

    public void setInternationalNumber(Integer internationalNumber) {
        this.internationalNumber = internationalNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
