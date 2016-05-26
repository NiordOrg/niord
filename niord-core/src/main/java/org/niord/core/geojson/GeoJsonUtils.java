package org.niord.core.geojson;

import org.niord.model.vo.geojson.GeoJsonVo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility functions for processing GeoJson data
 */
@SuppressWarnings("unused")
public class GeoJsonUtils {

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
}
