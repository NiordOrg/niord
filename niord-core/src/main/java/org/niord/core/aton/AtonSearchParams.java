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
import org.niord.core.domain.Domain;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.util.WebUtils;
import org.niord.model.search.PagedSearchParamsVo;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.niord.core.util.WebUtils.getParameterValues;

/**
 * AtoN search parameters
 */
@SuppressWarnings("unused")
public class AtonSearchParams extends PagedSearchParamsVo {

    private String name;
    private Geometry extent;
    private Set<String> atonUids = new HashSet<>();
    private Set<String> chartNumbers = new HashSet<>();
    private Set<Integer> areaIds = new HashSet<>();
    private boolean emptyOnOverflow;

    /**
     * Returns a MessageSearchParams initialized with parameter values from a URL using "default" parameter names
     * @param domain the current domain - defines defaults for sorting
     * @param url the URL
     * @return the MessageSearchParams initialized with parameter values
     */
    public static AtonSearchParams instantiate(Domain domain, String url) {
        return instantiate(domain, WebUtils.parseParameterMap(url));
    }


    /**
     * Returns a MessageSearchParams initialized with parameter values from a request using "default" parameter names
     * @param domain the current domain - defines defaults for sorting
     * @param req the servlet request
     * @return the MessageSearchParams initialized with parameter values
     */
    public static AtonSearchParams instantiate(Domain domain, HttpServletRequest req) {
        return instantiate(domain, req.getParameterMap());
    }


    /**
     * Returns a MessageSearchParams initialized with parameter values from a request parameter map
     * using "default" parameter names
     * @param domain the current domain - defines defaults for sorting
     * @param reqParams the request parameters
     * @return the MessageSearchParams initialized with parameter values
     */
    public static AtonSearchParams instantiate(Domain domain, Map<String, String[]> reqParams) {
        AtonSearchParams params = new AtonSearchParams();
        params.name(getParameterValues(reqParams, "name"))
                .atonUids(toSet(reqParams.get("atonUids"), Function.identity()))
                .chartNumbers(toSet(reqParams.get("chartNumbers"), Function.identity()))
                .areaIds(toSet(reqParams.get("areaIds"), Integer::valueOf))

                // Extent parameters
                .extent(checkNull(getParameterValues(reqParams, "minLat"), Double::valueOf),
                        checkNull(getParameterValues(reqParams, "minLon"), Double::valueOf),
                        checkNull(getParameterValues(reqParams, "maxLat"), Double::valueOf),
                        checkNull(getParameterValues(reqParams, "maxLon"), Double::valueOf))

                // Standard paged search parameters
                .maxSize(checkNull(getParameterValues(reqParams, "maxSize"), 100, Integer::valueOf))
                .page(checkNull(getParameterValues(reqParams, "page"), 0, Integer::valueOf))
                .sortBy(getParameterValues(reqParams, "sortBy"))
                .sortOrder(checkNull(getParameterValues(reqParams, "sortOrder"), SortOrder::valueOf));

        return params;
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /***/

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

    public Set<String> getAtonUids() {
        return atonUids;
    }

    public AtonSearchParams atonUids(Set<String> atonUids) {
        this.atonUids = atonUids;
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
