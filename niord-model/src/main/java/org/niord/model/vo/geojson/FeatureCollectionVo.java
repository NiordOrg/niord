package org.niord.model.vo.geojson;


import java.util.function.Consumer;

/**
 * GeoJSON FeatureCollection, as defined in the specification:
 * hhttp://geojson.org/geojson-spec.html#feature-objects
 */
public class FeatureCollectionVo extends GeoJsonVo {

    // NB: "id" not directly mentioned in specification, but it's useful...
    private Object id;
    private FeatureVo[] features;

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        if (features != null) {
            for (int x = 0; x < features.length; x++) {
                features[x].visitCoordinates(handler);
            }
        }
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public FeatureVo[] getFeatures() {
        return features;
    }

    public void setFeatures(FeatureVo[] features) {
        this.features = features;
    }
}

