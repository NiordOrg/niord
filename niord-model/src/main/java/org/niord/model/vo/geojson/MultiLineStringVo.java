package org.niord.model.vo.geojson;


import java.util.function.Consumer;

/**
 * GeoJSON MultiLineString, as defined in the specification:
 * http://geojson.org/geojson-spec.html#multilinestring
 */
public class MultiLineStringVo extends GeometryVo {

    private double[][][] coordinates;

    @SuppressWarnings("unused")
    public MultiLineStringVo() {
    }

    public MultiLineStringVo(double[][][] coordinates) {
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

