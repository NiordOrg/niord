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

package org.niord.core.chart.vo;

import org.niord.model.geojson.GeometryVo;
import org.niord.model.message.ChartVo;

/**
 * Extends the {@linkplain ChartVo} model with system-specific fields and attributes.
 */
@SuppressWarnings("unused")
public class SystemChartVo extends ChartVo {

    GeometryVo geometry;
    String horizontalDatum;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public GeometryVo getGeometry() {
        return geometry;
    }

    public void setGeometry(GeometryVo geometry) {
        this.geometry = geometry;
    }

    public String getHorizontalDatum() {
        return horizontalDatum;
    }

    public void setHorizontalDatum(String horizontalDatum) {
        this.horizontalDatum = horizontalDatum;
    }

}
