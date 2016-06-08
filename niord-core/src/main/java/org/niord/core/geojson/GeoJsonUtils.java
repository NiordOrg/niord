package org.niord.core.geojson;

import org.apache.commons.lang.StringUtils;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.niord.model.vo.geojson.FeatureVo;
import org.niord.model.vo.geojson.GeoJsonVo;
import org.niord.model.vo.geojson.GeometryCollectionVo;
import org.niord.model.vo.geojson.GeometryVo;
import org.niord.model.vo.geojson.LineStringVo;
import org.niord.model.vo.geojson.MultiLineStringVo;
import org.niord.model.vo.geojson.MultiPointVo;
import org.niord.model.vo.geojson.MultiPolygonVo;
import org.niord.model.vo.geojson.PointVo;
import org.niord.model.vo.geojson.PolygonVo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Returns the n'th coordinate **/
    public static double[] computeCoordinate(GeoJsonVo g, int n) {
        // Note to self: This can be implemented much more efficiently
        AtomicInteger cnt = new AtomicInteger(0);
        double[][] resultHolder = new double[1][];
        g.visitCoordinates(xy -> {
            if (cnt.getAndIncrement() == n) {
                resultHolder[0] = xy;
            }
        });
        return resultHolder[0];
    }


    /** Sets a "language" feature property flag and optionally removes all names not of the given language */
    public static <GJ extends GeoJsonVo> GJ setLanguage(GJ g, String language, boolean removeOtherNames) {
        if (g == null || StringUtils.isBlank(language)) {
            return g;
        }
        if (g instanceof FeatureCollectionVo) {
            FeatureCollectionVo fc = (FeatureCollectionVo)g;
            if (fc.getFeatures() != null) {
                Arrays.stream(fc.getFeatures()).forEach(f -> setLanguage(f, language, removeOtherNames));
            }
        } else if (g instanceof FeatureVo) {
            FeatureVo f = (FeatureVo) g;
            f.getProperties().put("language", language);
            if (removeOtherNames) {
                f.getProperties().entrySet().removeIf(e -> {
                    FeatureName name = new FeatureName(e);
                    return name.isValid() && !language.equals(name.getLanguage());
                });
            }
        }
        return g;
    }

    /**
     * Serializes the GeoJSON of the feature collection into a flat list of coordinates for each feature.
     * The list of coordinates can e.g. be used to present for an end-user, rather than the underlying GeoJSON.
     * <p>
     * Each feature and each coordinate of each feature may have a localized name as stored in the
     * feature properties according to the {@linkplain FeatureName} conventions.
     * <p>
     * When serializing coordinates, adhere to a couple of rules:
     * <li>
     *     <ul>If the "parentFeatureIds" feature property is defined, skip the coordinates.</ul>
     *     <ul>If the "restriction" feature property has the value "affected", skip the coordinates.</ul>
     *     <ul>For polygon linear rings, skip the last coordinate (which is identical to the first).</ul>
     *     <ul>For (multi-)polygons, only include the exterior ring, not the interior ring.</ul>
     * </li>
     * <p>
     * This implementation should be kept in sync with the {@code MapService.serializeCoordinates()} JavaScript function.
     *
     * @param fc the feature collection to serialize
     * @param language the language
     * @return the serialized coordinates
     */
    public static List<SerializedFeature> serializeFeatureCollection(FeatureCollectionVo fc, String language) {
        List<SerializedFeature> result = new ArrayList<>();
        if (fc != null) {
            for (FeatureVo feature : fc.getFeatures()) {
                if (feature.getProperties().containsKey("parentFeatureIds") ||
                        "affected".equals(feature.getProperties().get("restriction"))) {
                    continue;
                }

                // If no language param is defined, check if the feature defines a "language" property. Default to "en"
                String featureLang = (String)feature.getProperties().get("language");
                String lang = StringUtils.isBlank(language) ? StringUtils.defaultIfBlank(featureLang, "en") : language;

                SerializedFeature sf = new SerializedFeature();
                sf.setName(FeatureName.getFeatureName(feature.getProperties(), lang));
                AtomicInteger index = new AtomicInteger(0);
                serializeGeometry(feature.getGeometry(), sf, feature.getProperties(), lang, new AtomicInteger(0));
                if (StringUtils.isNotBlank(sf.getName()) || !sf.getCoordinates().isEmpty()) {
                    result.add(sf);
                }
            }
        }

        // Update the start indexes if the coordinates
        int startIndex = 1;
        for (SerializedFeature sf : result) {
            sf.setStartIndex(startIndex);
            startIndex += sf.getCoordinates().size();
        }

        return result;
    }


    /** Serializes the geometry **/
    private static void serializeGeometry(GeometryVo g, SerializedFeature sf, Map<String, Object> properties, String language, AtomicInteger index) {
        if (g != null) {
            if (g instanceof PointVo) {
                serializeCoordinates(((PointVo)g).getCoordinates(), IncludeCoord.ALL, sf, properties, language, index);
            } else if (g instanceof LineStringVo) {
                serializeCoordinates(((LineStringVo)g).getCoordinates(), IncludeCoord.ALL, sf, properties, language, index);
            } else if (g instanceof PolygonVo) {
                PolygonVo pol = (PolygonVo)g;
                for (int ring = 0; pol.getCoordinates() != null && ring < pol.getCoordinates().length; ring++) {
                    IncludeCoord incl = ring == 0 ? IncludeCoord.ALL_BUT_LAST : IncludeCoord.NONE;
                    serializeCoordinates(pol.getCoordinates()[ring], incl, sf, properties, language, index);
                }
            } else if (g instanceof MultiPointVo) {
                serializeCoordinates(((MultiPointVo)g).getCoordinates(), IncludeCoord.ALL, sf, properties, language, index);
            } else if (g instanceof MultiLineStringVo) {
                serializeCoordinates(((MultiLineStringVo)g).getCoordinates(), IncludeCoord.ALL, sf, properties, language, index);
            } else if (g instanceof MultiPolygonVo) {
                MultiPolygonVo mp = (MultiPolygonVo)g;
                for (int p = 0; mp.getCoordinates() != null && p < mp.getCoordinates().length; p++) {
                    for (int ring = 0; mp.getCoordinates()[p] != null && ring < mp.getCoordinates()[p].length; ring++) {
                        IncludeCoord incl = ring == 0 ? IncludeCoord.ALL_BUT_LAST : IncludeCoord.NONE;
                        serializeCoordinates(mp.getCoordinates()[p][ring], incl, sf, properties, language, index);
                    }
                }
            } else if (g instanceof GeometryCollectionVo) {
                GeometryCollectionVo gc = (GeometryCollectionVo)g;
                if (gc.getGeometries() != null) {
                    for (GeometryVo g2 : gc.getGeometries()) {
                        serializeGeometry(g2, sf, properties, language, index);
                    }
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }


    /** Serializes the geometry coordinates */
    private static <T> void serializeCoordinates(T coords, IncludeCoord incl, SerializedFeature sf, Map<String, Object> properties, String language, AtomicInteger index) {
        if (coords != null) {
            if (coords instanceof double[]) {
                if (((double[])coords).length >= 2) {
                    int coordIndex = index.getAndIncrement();
                    if (incl == IncludeCoord.ALL) {
                        SerializedCoordinates sc = new SerializedCoordinates();
                        sc.setCoordinates((double[])coords);
                        sc.setName(FeatureName.getFeatureCoordinateName(properties, language, coordIndex));
                        sf.getCoordinates().add(sc);
                    }
                }
            } else if (coords instanceof double[][]) {
                double[][] coordSet = (double[][])coords;
                for (int c = 0; c < coordSet.length; c++) {
                    IncludeCoord inclCoord = incl == IncludeCoord.ALL_BUT_LAST
                            ? (c == coordSet.length - 1 ? IncludeCoord.NONE : IncludeCoord.ALL)
                            : incl;
                    serializeCoordinates(coordSet[c], inclCoord, sf, properties, language, index);
                }
            } else if (coords instanceof double[][][]) {
                for (double[][] c : (double[][][])coords) {
                    serializeCoordinates(c, incl, sf, properties, language, index);
                }
            } else if (coords instanceof double[][][][]) {
                for (double[][][] c : (double[][][][])coords) {
                    serializeCoordinates(c, incl, sf, properties, language, index);
                }
            }
        }
    }

    enum IncludeCoord { ALL, NONE, ALL_BUT_LAST }

    /**
     * Encapsulates a single lon-lat coordinate with a language specific name
     */
    public static class SerializedCoordinates {
        String name;
        double[] coordinates;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double[] getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(double[] coordinates) {
            this.coordinates = coordinates;
        }
    }

    /**
     * Encapsulates a feature with a list of coordinates and a language specific name
     */
    public static class SerializedFeature {
        String name;
        int startIndex = 0;
        List<SerializedCoordinates> coordinates = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public void setStartIndex(int startIndex) {
            this.startIndex = startIndex;
        }

        public List<SerializedCoordinates> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<SerializedCoordinates> coordinates) {
            this.coordinates = coordinates;
        }
    }
}
