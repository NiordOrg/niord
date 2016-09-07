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
import org.niord.model.geojson.LineStringVo;
import org.niord.model.geojson.MultiPointVo;
import org.niord.model.geojson.PointVo;
import org.niord.model.geojson.PolygonVo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts a human-writable plain-text geometry specification into
 * a proper GeoJSON representation.
 * <p>
 * The supported plain text specification is very simple and constitutes lists of point, along with
 * localized names.<br>
 * Example:
 * <pre>
 *   1) 54° 45,7' N 10° 29,1' E, Ærø S.
 *   2) 54° 41,2' N 10° 36,9' E, Keldsnor SW.
 *   3) 54° 38,3' N 10° 40,1' E, Keldsnor S.
 *   4) 54° 41,0' N 10° 48,1' E, Keldsnor SE.
 * </pre>
 * Or:
 * <pre>
 *   54° 41,000'N - 010° 35,000'E
 *   54° 35,566'N - 010° 35,010'E
 *   54° 38,397'N - 010° 25,125'E
 * </pre>
 * Or:
 * <pre>
 *   Point, da:Slukket bøje, en: Unlit buoy
 *   54° 41,000'N - 010° 35,000'E
 *
 *   LineString
 *   54° 35,566'N - 010° 35,010'E, da: kyst, en: shore
 *   54° 38,397'N - 010° 25,125'E
 *   54° 39,111'N - 010° 21,202'E
 * </pre>
 */
public class PlainTextConverter {

    public static final Pattern TYPE_FORMAT = Pattern.compile(
            "^(?<type>Point|MultiPoint|LineString|Polygon)(?<desc>,.+)?$",
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern POSITION_FORMAT = Pattern.compile(
            "^(?<lat>[^NS]+)(?<latDir>[NS])([ -])*(?<lon>[^EW]+)(?<lonDir>[EW])(?<desc>.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private final Set<String> languages = new HashSet<>();

    /**
     * Constructor
     * @param languages the supported languages
     */
    private PlainTextConverter(String[] languages) {
        if (languages == null || languages.length == 0) {
            this.languages.add("en");
        } else {
            this.languages.addAll(Arrays.asList(languages));
        }
    }

    /** Factory method - returns a new plain text converter that supports the given languages **/
    public static PlainTextConverter newInstance(String[] languages) {
        return new PlainTextConverter(languages);
    }


    /**
     * Returns the geometry given by the given plain text representation.
     * Return null for empty representations and throws an exception when
     * experiencing parsing errors.
     *
     * @param text the plain text representation
     * @return the resulting GeoJSON feature collection
     */
    public FeatureCollectionVo fromPlainText(String text) throws Exception {

        if (StringUtils.isBlank(text)) {
            return null;
        }
        text = text.trim();

        // Group the text into feature lines
        String[] lines = Arrays.stream(text.split("\n")).map(String::trim).toArray(String[]::new);
        List<List<String>> featureCollectionLines = new ArrayList<>();
        List<String> featureLines = null;
        for (String line : lines) {
            if (StringUtils.isBlank(line)) {
                featureLines = null;
            } else {
                if (featureLines == null) {
                    featureLines = new ArrayList<>();
                    featureCollectionLines.add(featureLines);
                }
                featureLines.add(line);
            }
        }

        // Convert the feature lines into proper features
        List<FeatureVo> features = new ArrayList<>();
        for (List<String> fl : featureCollectionLines) {
            FeatureVo feature = fromPlainText(fl);
            if (feature == null) {
                return null;
            } else {
                features.add(feature);
            }
        }
        if (features.isEmpty()) {
            return null;
        }

        // Assemble the feature collection from the list of features
        FeatureCollectionVo fc = new FeatureCollectionVo();
        fc.setFeatures(features.toArray(new FeatureVo[features.size()]));

        return fc;
    }


    /** Converts a list of lines into a feature **/
    @SuppressWarnings("all")
    private FeatureVo fromPlainText(List<String> lines) throws Exception {

        Map<String, Object> properties = new HashMap<>();
        List<double[]> coordinates = new ArrayList<>();
        String type = null;
        int offset = 0;

        // Check if the first line designates the geometry type
        Matcher m = TYPE_FORMAT.matcher(lines.get(0));
        if (m.find()) {
            offset = 1;
            type = m.group("type");
            parseFeatureNames(properties, "name:", m.group("desc"));
        }

        // Parse the coordinates
        for (int coordIndex = offset; coordIndex < lines.size(); coordIndex++) {
            String line = lines.get(coordIndex);
            if (!parsePosition(line, coordinates, properties, coordIndex - offset)) {
                throw new Exception("Invalid format: " + line);
            }
        }

        if (coordinates.isEmpty()) {
            return null;
        } else if (type == null && coordinates.size() == 1) {
            type = "Point";
        } else if (type == null && coordinates.size() > 1) {
            type = "MultiPoint";
        }

        FeatureVo feature = new FeatureVo();
        feature.setProperties(properties);

        switch (type.toLowerCase()) {
            case "point":
                if (coordinates.size() != 1) {
                    return null;
                }
                PointVo point = new PointVo();
                point.setCoordinates(coordinates.get(0));
                feature.setGeometry(point);
                break;
            case "multipoint":
                if (coordinates.isEmpty()) {
                    return null;
                }
                MultiPointVo multiPoint = new MultiPointVo();
                multiPoint.setCoordinates(coordinates.toArray(new double[coordinates.size()][]));
                feature.setGeometry(multiPoint);
                break;
            case "linestring":
                if (coordinates.size() < 2) {
                    return null;
                }
                LineStringVo lineString = new LineStringVo();
                lineString.setCoordinates(coordinates.toArray(new double[coordinates.size()][]));
                feature.setGeometry(lineString);
                break;
            case "polygon":
                if (coordinates.size() < 3) {
                    return null;
                }
                // Same first and last coordinate in GeoJSON Polygon linear rings.
                coordinates.add(coordinates.get(0));
                PolygonVo polygon = new PolygonVo();
                double[][][] coords = new double[1][][];
                coords[0] = coordinates.toArray(new double[coordinates.size()][]);
                polygon.setCoordinates(coords);
                feature.setGeometry(polygon);
                break;
            default:
                return null;
        }

        return feature;
    }


    /** Parses a single line as a position and optionally the name properties **/
    private boolean parsePosition(String line, List<double[]> coordinates, Map<String, Object> properties, int index) throws Exception {
        line = line.replaceFirst("^\\d+\\)", "").trim();
        Matcher m = POSITION_FORMAT.matcher(line);
        if (m.find()) {
            double[] coord = {
                parseCoordinate(m.group("lon"), m.group("lonDir")),
                parseCoordinate(m.group("lat"), m.group("latDir"))
            };
            coordinates.add(coord);
            parseFeatureNames(properties, "name:"+ index + ":", m.group("desc"));
            return true;
        }
        return false;
    }


    /**  Parses the degree (either latitude or longitude) **/
    private double parseCoordinate(String deg, String dir) throws Exception {
        String[] parts = deg.replace("°", " ")
                .replace("'", "")
                .replace("I", " ") // When you copy-paste ° from DMA NtM PDF
                .replace("J", " ") // When you copy-paste ' from DMA NtM PDF
                .replaceAll("\\s+", " ")
                .replace(",", ".")
                .split(" ");
        if (parts.length == 0 || parts.length > 2) {
            throw new Exception("Invalid degree format: " + deg);
        }

        try {
            double val = Double.valueOf(parts[0]);
            if (parts.length == 2) {
                double min = Double.valueOf(parts[1]);
                val += min / 60.0;
            }

            if (dir.equalsIgnoreCase("S") || dir.equalsIgnoreCase("W")) {
                val = -val;
            }
            return val;
        } catch (NumberFormatException e) {
            throw new Exception("Invalid degree format: " + deg);
        }
    }


    /**
     * Parses the feature name properties from the description line.
     * <p>
     * For general lines, feature names are defined for all languages of this converter instance.
     * <p>
     * However, if the line contains language indicators ("lang:"), then this is used to split
     * the description line into separate language-specific parts. Example:<br>
     * <pre>, da: Ubåd U-9., en: Submarine U-9.</pre>
     **/
    private void parseFeatureNames(Map<String, Object> properties, String namePrefix, String desc) {
        if (StringUtils.isNotBlank(desc)) {
            desc = desc.replaceFirst("^,?\\s+", "");
            if (StringUtils.isNotBlank(desc)) {

                // Convert languages to regex string like "da:|en:|fr:"
                String langs = languages.stream()
                        .map(l -> l + ":")
                        .collect(Collectors.joining("|"));

                Pattern LANG_MATCH = Pattern.compile(
                        ",?\\s*(?<lang>" + langs + ")\\s*((^" + langs + ")*)",
                        Pattern.CASE_INSENSITIVE
                );

                Matcher m = LANG_MATCH.matcher(desc);
                String lang = null;
                int startIndex = 0;
                while (m.find()) {
                    if (lang != null) {
                        properties.put(namePrefix + lang, desc.substring(startIndex, m.start()));
                    }

                    lang = m.group("lang");
                    // Strip colon from language
                    lang = lang.substring(0, lang.length() - 1);
                    startIndex = m.end();
                }

                if (lang != null) {
                    properties.put(namePrefix + lang, desc.substring(startIndex));

                } else {
                    // Add the feature name for all languages
                    for (String l : languages) {
                        properties.put(namePrefix + l, desc);
                    }

                }
            }
        }
    }
}
