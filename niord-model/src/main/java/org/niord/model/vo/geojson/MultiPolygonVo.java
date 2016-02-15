package org.niord.model.vo.geojson;


import java.util.function.Consumer;

/**
 * GeoJSON MultiPolygon, as defined in the specification:
 * http://geojson.org/geojson-spec.html#multipolygon
 */
public class MultiPolygonVo extends GeometryVo {

    private double[][][][] coordinates;

    public MultiPolygonVo() {
    }

    public MultiPolygonVo(double[][][][] coordinates) {
        this.coordinates = coordinates;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        visitCoordinates(coordinates, handler);
    }

    public double[][][][] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(double[][][][] coordinates) {
        this.coordinates = coordinates;
    }
}

