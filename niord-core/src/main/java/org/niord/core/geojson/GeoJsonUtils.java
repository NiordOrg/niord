/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.geojson;

import org.apache.commons.lang.StringUtils;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.FeatureVo;
import org.niord.model.geojson.GeoJsonVo;
import org.niord.model.geojson.GeometryCollectionVo;
import org.niord.model.geojson.GeometryVo;
import org.niord.model.geojson.LineStringVo;
import org.niord.model.geojson.MultiLineStringVo;
import org.niord.model.geojson.MultiPointVo;
import org.niord.model.geojson.MultiPolygonVo;
import org.niord.model.geojson.PointVo;
import org.niord.model.geojson.PolygonVo;

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


    /** Computes the center of contained geometries **/
    public static double[] computeCenter(GeoJsonVo[] g) {
        if (g == null || g.length == 0) {
            return null;
        }
        double[][] centers = Arrays.stream(g)
                .map(GeoJsonVo::computeCenter)
                .toArray(double[][]::new);
        if (centers.length == 0) {
            return null;
        }
        double[] center = new double[] { 0.0, 0.0 };
        for (double[] c : centers) {
            center[0] += c[0];
            center[1] += c[1];
        }
        center[0] /= centers.length;
        center[1] /= centers.length;
        return center;
    }

    /** Computes the bounding box of the geometry **/
    public static double[] computeBBox(GeoJsonVo[] g) {
        if (g == null || g.length == 0) {
            return null;
        }
        double[][] bboxes = Arrays.stream(g)
                .map(GeoJsonVo::computeBBox)
                .toArray(double[][]::new);
        if (bboxes.length == 0) {
            return null;
        }
        double[] bbox = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (double[] b : bboxes) {
            bbox[0] = Math.min(bbox[0], b[0]);
            bbox[1] = Math.min(bbox[1], b[1]);
            bbox[2] = Math.max(bbox[2], b[2]);
            bbox[3] = Math.max(bbox[3], b[3]);
        }
        return bbox;
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
     * <ul>
     *     <li>If the "parentFeatureIds" feature property is defined, skip the coordinates.</li>
     *     <li>If the "restriction" feature property has the value "affected", skip the coordinates.</li>
     *     <li>For polygon linear rings, skip the last coordinate (which is identical to the first).</li>
     *     <li>For (multi-)polygons, only include the exterior ring, not the interior ring.</li>
     * </ul>
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
            int startIndex = 1;
            for (FeatureVo feature : fc.getFeatures()) {
                if (feature.getProperties().containsKey("parentFeatureIds") ||
                        "affected".equals(feature.getProperties().get("restriction"))) {
                    continue;
                }

                // If no language param is defined, check if the feature defines a "language" property. Default to "en"
                String featureLang = (String)feature.getProperties().get("language");
                String lang = StringUtils.isBlank(language) ? StringUtils.defaultIfBlank(featureLang, "en") : language;

                // Check if the feature contains a "startCoordIndex" property that overrides our computed index
                Number startCoordIndex = (Number) feature.getProperties().get("startCoordIndex");
                startIndex = startCoordIndex != null ? startCoordIndex.intValue() : startIndex;

                SerializedFeature sf = new SerializedFeature();
                sf.setName(FeatureName.getFeatureName(feature.getProperties(), lang));
                serializeGeometry(feature.getGeometry(), sf, feature.getProperties(), lang, new AtomicInteger(0));
                if (StringUtils.isNotBlank(sf.getName()) || !sf.getCoordinates().isEmpty()) {
                    result.add(sf);

                    // Update the start indexes if the coordinates
                    sf.setStartIndex(startIndex);
                    startIndex += sf.getCoordinates().size();
                }
            }
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
