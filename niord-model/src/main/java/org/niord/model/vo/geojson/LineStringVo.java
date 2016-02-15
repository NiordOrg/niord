package org.niord.model.vo.geojson;


import java.util.function.Consumer;

/**
 * GeoJSON Point, as defined in the specification:
 * http://geojson.org/geojson-spec.html#linestring
 */
public class LineStringVo extends GeometryVo {

    private double[][] coordinates;

    public LineStringVo() {
    }

    public LineStringVo(double[][] coordinates) {
        this.coordinates = coordinates;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        visitCoordinates(coordinates, handler);
    }

    public double[][] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(double[][] coordinates) {
        this.coordinates = coordinates;
    }
}

