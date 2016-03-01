package org.niord.core.aton;

import org.niord.core.model.Extent;
import org.niord.model.PagedSearchParamsVo;

import java.util.List;

/**
 * AtoN search parameters
 */
public class AtonSearchParams extends PagedSearchParamsVo {

    String name;
    List<Extent> mapExtents;
    List<Extent> chartExtents;

    /** Getters **/

    public String getName() {
        return name;
    }

    public List<Extent> getMapExtents() {
        return mapExtents;
    }

    public List<Extent> getChartExtents() {
        return chartExtents;
    }

    /** Method-chaining setters **/

    public AtonSearchParams name(String name) {
        this.name = name;
        return this;
    }

    public AtonSearchParams mapExtents(List<Extent> extents) {
        this.mapExtents = extents;
        return this;
    }

    public AtonSearchParams mapExtents(double minLat, double minLon, double maxLat, double maxLon) {
        this.mapExtents = new Extent(minLat, minLon, maxLat, maxLon).normalize();
        return this;
    }

    public AtonSearchParams chartExtents(List<Extent> chartExtents) {
        this.chartExtents = chartExtents;
        return this;
    }
}
