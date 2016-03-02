package org.niord.core.aton;

import com.vividsolutions.jts.geom.Geometry;
import org.niord.core.util.GeoJsonUtils;
import org.niord.model.PagedSearchParamsVo;

/**
 * AtoN search parameters
 */
public class AtonSearchParams extends PagedSearchParamsVo {

    String name;
    Geometry extent;
    String[] chartNumbers;

    public void setExtent(double minLat, double minLon, double maxLat, double maxLon) {
        extent = GeoJsonUtils.toJtsExtent(minLat, minLon, maxLat, maxLon);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Geometry getExtent() {
        return extent;
    }

    public void setExtent(Geometry extent) {
        this.extent = extent;
    }

    public String[] getChartNumbers() {
        return chartNumbers;
    }

    public void setChartNumbers(String[] chartNumbers) {
        this.chartNumbers = chartNumbers;
    }
}
