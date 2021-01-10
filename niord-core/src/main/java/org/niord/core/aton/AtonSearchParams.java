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

package org.niord.core.aton;

import org.locationtech.jts.geom.Geometry;
import org.niord.core.geojson.JtsConverter;
import org.niord.model.search.PagedSearchParamsVo;

import java.util.HashSet;
import java.util.Set;

/**
 * AtoN search parameters
 */
@SuppressWarnings("unused")
public class AtonSearchParams extends PagedSearchParamsVo {

    private String name;
    private Geometry extent;
    private Set<String> chartNumbers = new HashSet<>();
    private Set<Integer> areaIds = new HashSet<>();
    private boolean emptyOnOverflow;

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public String getName() {
        return name;
    }

    public AtonSearchParams name(String name) {
        this.name = name;
        return this;
    }

    public Geometry getExtent() {
        return extent;
    }

    public AtonSearchParams extent(Geometry extent) {
        this.extent = extent;
        return this;
    }

    public AtonSearchParams extent(Double minLat, Double minLon, Double maxLat, Double maxLon) {
        this.extent = JtsConverter.toJtsExtent(minLat, minLon, maxLat, maxLon);
        return this;
    }

    public Set<String> getChartNumbers() {
        return chartNumbers;
    }

    public AtonSearchParams chartNumbers(Set<String> chartNumbers) {
        this.chartNumbers = toSet(chartNumbers);
        return this;
    }

    public Set<Integer> getAreaIds() {
        return areaIds;
    }

    public AtonSearchParams areaIds(Set<Integer> areaIds) {
        this.areaIds = toSet(areaIds);
        return this;
    }

    public boolean isEmptyOnOverflow() {
        return emptyOnOverflow;
    }

    public AtonSearchParams emptyOnOverflow(boolean emptyOnOverflow) {
        this.emptyOnOverflow = emptyOnOverflow;
        return this;
    }
}
