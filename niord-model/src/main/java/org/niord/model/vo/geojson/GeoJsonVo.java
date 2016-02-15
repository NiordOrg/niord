package org.niord.model.vo.geojson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Base GeoJSON object as defined in the specification:
 * http://geojson.org/geojson-spec.html#geojson-objects
 */
@JsonTypeInfo(property = "type", use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PointVo.class,               name = "Point"),
        @JsonSubTypes.Type(value = MultiPointVo.class,          name = "MultiPoint"),
        @JsonSubTypes.Type(value = LineStringVo.class,          name = "LineString"),
        @JsonSubTypes.Type(value = MultiLineStringVo.class,     name = "MultiLineString"),
        @JsonSubTypes.Type(value = PolygonVo.class,             name = "Polygon"),
        @JsonSubTypes.Type(value = MultiPolygonVo.class,        name = "MultiPolygon"),
        @JsonSubTypes.Type(value = GeometryCollectionVo.class,  name = "GeometryCollection"),
        @JsonSubTypes.Type(value = FeatureVo.class,             name = "Feature"),
        @JsonSubTypes.Type(value = FeatureCollectionVo.class,   name = "FeatureCollection")
})
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public abstract class GeoJsonVo implements Serializable {

    private static final ObjectMapper mapper = new ObjectMapper();

    private String type;
    private CrsVo crs;
    private double[] bbox;

    /** {@inheritDoc} */
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            return "Error " + e;
        }
    }

    /**
     * Visits all included coordinates and executes the handler
     * @param handler the handler
     */
    public abstract void visitCoordinates(Consumer<double[]> handler);

    /** Utility method used for visiting multidimensional arrays of coordinates */
    static <T> void visitCoordinates(T coords, Consumer<double[]> handler) {
        if (coords != null) {
            if (coords instanceof double[]) {
                if (((double[])coords).length >= 2) {
                    handler.accept((double[])coords);
                }
            } else if (coords instanceof double[][]) {
                for (double[] c : (double[][])coords) {
                    visitCoordinates(c, handler);
                }
            } else if (coords instanceof double[][][]) {
                for (double[][] c : (double[][][])coords) {
                    visitCoordinates(c, handler);
                }
            } else if (coords instanceof double[][][][]) {
                for (double[][][] c : (double[][][][])coords) {
                    visitCoordinates(c, handler);
                }
            }
        }
    }

    /** Computes the bounding box of the geometry **/
    public double[] computeBBox() {
        // TODO: Naive implementation - cater with border cases later...
        double[] bbox = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        visitCoordinates(xy -> {
            bbox[0] = Math.min(bbox[0], xy[0]);
            bbox[1] = Math.min(bbox[1], xy[1]);
            bbox[2] = Math.max(bbox[2], xy[0]);
            bbox[3] = Math.max(bbox[3], xy[1]);
        });
        return bbox;
    }

    /** Computes the center of the geometry **/
    public double[] computeCenter() {
        double[] bbox = computeBBox();
        return new double[]{(bbox[0] + bbox[2]) / 2.0, (bbox[1] + bbox[3]) / 2.0};
    }

    /** Returns the n'th coordinate **/
    public double[] computeCoordinate(int n) {
        // Note to self: This can be implemented much more efficiently
        AtomicInteger cnt = new AtomicInteger(0);
        double[][] resultHolder = new double[1][];
        visitCoordinates(xy -> {
            if (cnt.getAndIncrement() == n) {
                resultHolder[0] = xy;
            }
        });
        return resultHolder[0];
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public CrsVo getCrs() {
        return crs;
    }

    public void setCrs(CrsVo crs) {
        this.crs = crs;
    }

    public double[] getBbox() {
        return bbox;
    }

    public void setBbox(double[] bbox) {
        this.bbox = bbox;
    }
}
