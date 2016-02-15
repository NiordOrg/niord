package org.niord.model.vo.geojson;


import java.util.function.Consumer;

/**
 * GeoJSON GeometryCollection, as defined in the specification:
 * http://geojson.org/geojson-spec.html#geometry-collection
 */
public class GeometryCollectionVo extends GeometryVo {

    private GeometryVo[] geometries;

    public GeometryCollectionVo() {
    }

    public GeometryCollectionVo(GeometryVo[] geometries) {
        this.geometries = geometries;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        if (geometries != null) {
            for (int x = 0; x < geometries.length; x++) {
                geometries[x].visitCoordinates(handler);
            }
        }
    }

    public GeometryVo[] getGeometries() {
        return geometries;
    }

    public void setGeometries(GeometryVo[] geometries) {
        this.geometries = geometries;
    }
}
