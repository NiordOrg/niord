package org.niord.core.aton;

import com.vividsolutions.jts.geom.Geometry;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.model.PagedSearchParamsVo;

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
        this.extent = GeoJsonUtils.toJtsExtent(minLat, minLon, maxLat, maxLon);
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
