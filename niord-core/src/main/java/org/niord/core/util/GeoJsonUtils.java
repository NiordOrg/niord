package org.niord.core.util;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import org.niord.model.vo.geojson.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility functions for processing GeoJson data
 */
public class GeoJsonUtils {

    final static GeometryFactory factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));

    private GeoJsonUtils() {
    }

    /** Rounds the coordinates of the GeoJson object */
    public static void roundCoordinates(GeoJsonVo g, int decimals) {
        g.visitCoordinates(c -> {
            c[0] = new BigDecimal(c[0]).setScale(decimals, RoundingMode.HALF_EVEN).doubleValue();
            c[1] = new BigDecimal(c[1]).setScale(decimals, RoundingMode.HALF_EVEN).doubleValue();
        });
    }

    /** Swaps the coordinates of the GeoJson object */
    public static void swapCoordinates(GeoJsonVo g) {
        g.visitCoordinates(c -> {
            double tmp = c[0];
            c[0] = c[1];
            c[1] = tmp;
        });
    }


    /**
     * Converts a lat-lon position to the corresponding JTS geometry
     * @param lat the latitude
     * @param lon the longitude
     * @return the corresponding JTS geometry
     */
    public static Geometry toJtsPoint(double lat, double lon) {
        return factory.createPoint(toJtsCoords(new double[]{lon, lat}));
    }


    /**
     * Converts two lat-lon corner positions to the corresponding JTS geometry
     * @param minLat the minimum latitude
     * @param minLon the minimum longitude
     * @param maxLat the maximum latitude
     * @param maxLon the maximum longitude
     * @return the corresponding JTS geometry
     */
    public static Geometry toJtsExtent(double minLat, double minLon, double maxLat, double maxLon) {
        double[][][] coords = {{
                    {minLon, minLat},
                    {minLon, maxLat},
                    {maxLon, maxLat},
                    {maxLon, minLat},
                    {minLon, minLat}
            }};
        return toJtsPolygon(coords);
    }


    /**
     * Converts a GeoJson geometry to the corresponding JTS geometry
     * @param g the GeoJson to convert
     * @return the corresponding JTS geometry
     */
    public static Geometry toJts(GeometryVo g) {
        if (g == null) {
            return null;
        } if (g instanceof PointVo) {
            return factory.createPoint(toJtsCoords(((PointVo)g).getCoordinates()));
        } else if (g instanceof LineStringVo) {
            return factory.createLineString(toJtsCoords(((LineStringVo)g).getCoordinates()));
        } else if (g instanceof PolygonVo) {
            return toJtsPolygon(((PolygonVo)g).getCoordinates());
        } else if (g instanceof MultiPointVo) {
            return factory.createMultiPoint(toJtsCoords(((MultiPointVo)g).getCoordinates()));
        } else if (g instanceof MultiLineStringVo) {
            return toJtsMultiLineString((MultiLineStringVo)g);
        } else if (g instanceof MultiPolygonVo) {
            return toJtsMultiPolygon((MultiPolygonVo)g);
        } else if (g instanceof GeometryCollectionVo) {
            return toJtsGeometryCollection((GeometryCollectionVo)g);
        } else {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * Converts a JTS geometry to the corresponding GeoJson geometry
     * @param g the JTS to convert
     * @return the corresponding GeoJson geometry
     */
    public static GeometryVo fromJts(Geometry g) {
        if (g == null) {
            return null;
        }

        Class<? extends Geometry> c = g.getClass();
        if (c.equals(Point.class)) {
            return new PointVo(fromJtsCoords(g.getCoordinate()));
        } else if (c.equals(LineString.class)) {
            return new LineStringVo(fromJtsCoords(g.getCoordinates()));
        } else if (c.equals(Polygon.class)) {
            return fromJtsPolygon((Polygon) g);
        } else if (c.equals(MultiPoint.class)) {
            return new MultiPointVo(fromJtsCoords(g.getCoordinates()));
        } else if (c.equals(MultiLineString.class)) {
            return fromJtsMultiLineString((MultiLineString) g);
        } else if (c.equals(MultiPolygon.class)) {
            return fromJtsMultiPolygon((MultiPolygon) g);
        } else if (c.equals(GeometryCollection.class)) {
            return fromJtsGeometryCollection((GeometryCollection) g);
        } else {
            throw new UnsupportedOperationException();
        }
    }


    private static Coordinate toJtsCoords(double[] coords) {
        return new Coordinate(coords[0], coords[1]);
    }

    private static Coordinate[] toJtsCoords(double[][] coords) {
        Coordinate[] coordinates = new Coordinate[coords.length];
        for (int i = 0; i < coords.length; i++) {
            coordinates[i] = toJtsCoords(coords[i]);
        }
        return coordinates;
    }

    private static Polygon toJtsPolygon(double[][][] coordinates) {
        LinearRing outerRing = factory.createLinearRing(toJtsCoords(coordinates[0]));

        if (coordinates.length > 1) {
            int size = coordinates.length - 1;
            LinearRing[] innerRings = new LinearRing[size];
            for (int i = 0; i < size; i++) {
                innerRings[i] = factory.createLinearRing(toJtsCoords(coordinates[i + 1]));
            }
            return factory.createPolygon(outerRing, innerRings);
        } else {
            return factory.createPolygon(outerRing);
        }
    }

    private static MultiLineString toJtsMultiLineString(MultiLineStringVo multiLineString) {
        int size = multiLineString.getCoordinates().length;
        LineString[] lineStrings = new LineString[size];
        for (int i = 0; i < size; i++) {
            lineStrings[i] = factory.createLineString(toJtsCoords(multiLineString.getCoordinates()[i]));
        }
        return factory.createMultiLineString(lineStrings);
    }

    private static MultiPolygon toJtsMultiPolygon(MultiPolygonVo multiPolygon) {
        int size = multiPolygon.getCoordinates().length;
        Polygon[] polygons = new Polygon[size];
        for (int i = 0; i < size; i++) {
            polygons[i] = toJtsPolygon(multiPolygon.getCoordinates()[i]);
        }
        return factory.createMultiPolygon(polygons);
    }

    private static GeometryCollection toJtsGeometryCollection(GeometryCollectionVo gc) {
        int size = gc.getGeometries().length;
        Geometry[] geometries = new Geometry[size];
        for (int i = 0; i < size; i++) {
            geometries[i] = toJts(gc.getGeometries()[i]);
        }
        return factory.createGeometryCollection(geometries);
    }


    private static double[] fromJtsCoords(Coordinate coordinate) {
        return new double[] { coordinate.x, coordinate.y };
    }

    private static double[][] fromJtsCoords(Coordinate[] coordinates) {
        double[][] array = new double[coordinates.length][];
        for (int i = 0; i < coordinates.length; i++) {
            array[i] = fromJtsCoords(coordinates[i]);
        }
        return array;
    }

    private static PolygonVo fromJtsPolygon(Polygon polygon) {
        int size = polygon.getNumInteriorRing() + 1;
        double[][][] rings = new double[size][][];
        rings[0] = fromJtsCoords(polygon.getExteriorRing().getCoordinates());
        for (int i = 0; i < size - 1; i++) {
            rings[i + 1] = fromJtsCoords(polygon.getInteriorRingN(i).getCoordinates());
        }
        return new PolygonVo(rings);
    }

    private static MultiLineStringVo fromJtsMultiLineString(MultiLineString multiLineString) {
        int size = multiLineString.getNumGeometries();
        double[][][] lineStrings = new double[size][][];
        for (int i = 0; i < size; i++) {
            lineStrings[i] = fromJtsCoords(multiLineString.getGeometryN(i).getCoordinates());
        }
        return new MultiLineStringVo(lineStrings);
    }

    private static MultiPolygonVo fromJtsMultiPolygon(MultiPolygon multiPolygon) {
        int size = multiPolygon.getNumGeometries();
        double[][][][] polygons = new double[size][][][];
        for (int i = 0; i < size; i++) {
            polygons[i] = fromJtsPolygon((Polygon) multiPolygon.getGeometryN(i)).getCoordinates();
        }
        return new MultiPolygonVo(polygons);
    }

    private static GeometryCollectionVo fromJtsGeometryCollection(GeometryCollection gc) {
        int size = gc.getNumGeometries();
        GeometryVo[] geometries = new GeometryVo[size];
        for (int i = 0; i < size; i++) {
            geometries[i] = fromJts(gc.getGeometryN(i));
        }
        return new GeometryCollectionVo(geometries);
    }

}
