package org.niord.model.vo.geojson;


import java.util.function.Consumer;

/**
 * GeoJSON Polygon, as defined in the specification:
 * http://geojson.org/geojson-spec.html#polygon
 */
public class PolygonVo extends GeometryVo {

    private double[][][] coordinates;

    @SuppressWarnings("unused")
    public PolygonVo() {
    }

    public PolygonVo(double[][][] coordinates) {
        this.coordinates = coordinates;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        visitCoordinates(coordinates, handler);
    }

    public double[][][] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(double[][][] coordinates) {
        this.coordinates = coordinates;
    }
}

