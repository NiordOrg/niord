package org.niord.core.aton;

import com.vividsolutions.jts.geom.Geometry;
import org.niord.core.util.GeoJsonUtils;
import org.niord.model.PagedSearchParamsVo;

/**
 * AtoN search parameters
 */
public class AtonSearchParams extends PagedSearchParamsVo {

    private String name;
    private Geometry extent;
    private String[] chartNumbers;
    private boolean emptyOnOverflow;

    public void setExtent(Double minLat, Double minLon, Double maxLat, Double maxLon) {
        if (minLat != null || minLon != null || maxLat != null || maxLon != null) {
            minLat = minLat != null ? minLat : -90.0;
            minLon = minLon != null ? minLon : -180.0;
            maxLat = maxLat != null ? maxLat : 90.0;
            maxLon = maxLon != null ? maxLon : 180.0;
            extent = GeoJsonUtils.toJtsExtent(minLat, minLon, maxLat, maxLon);
        }
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

    public boolean isEmptyOnOverflow() {
        return emptyOnOverflow;
    }

    public void setEmptyOnOverflow(boolean emptyOnOverflow) {
        this.emptyOnOverflow = emptyOnOverflow;
    }
}
