package org.niord.model.vo.geojson;


import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GeoJSON Feature, as defined in the specification:
 * http://geojson.org/geojson-spec.html#feature-objects
 */
public class FeatureVo extends GeoJsonVo {

    private Object id;
    private GeometryVo geometry;
    private Map<String, Object> properties = new HashMap<>();

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        if (geometry != null) {
            geometry.visitCoordinates(handler);
        }
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public GeometryVo getGeometry() {
        return geometry;
    }

    public void setGeometry(GeometryVo geometry) {
        this.geometry = geometry;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}

