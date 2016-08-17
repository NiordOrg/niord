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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps a feature name or feature coordinate name.
 *
 * These are stored as properties in a GeoJson feature using the following patterns
 * <ul>
 *     <li>Feature name: "name:lang" - where lang is the 2 character language code.</li>
 *     <li>Feature coordinate name: "name:index:lang" - where index is the coordinate index
 *             and lang is the 2 character language code.</li>
 * </ul>
 */
public class FeatureName {

    private static Pattern FEATURE_NAME = Pattern.compile("^name:([a-zA-Z_]+)$");
    private static Pattern FEATURE_COORD_NAME = Pattern.compile("^name:(\\d+):([a-zA-Z_]+)$");

    enum Type { FeatureName, FeatureCoordName }

    Object key;
    Object value;
    String language;
    int coordIndex;
    Type type;

    /** Constructor **/
    @SuppressWarnings("unused")
    public FeatureName(Object key, Object value) {
        this.key = key;
        this.value = value;
        parseKey();
    }

    /** Constructor **/
    public FeatureName(Map.Entry<String, Object> kv) {
        this.key = kv.getKey();
        this.value = kv.getValue();
        parseKey();
    }

    /** Parses the key to determine the kind of feature name property **/
    private void parseKey() {
        Matcher m1 = FEATURE_NAME.matcher(key.toString());
        Matcher m2 = FEATURE_COORD_NAME.matcher(key.toString());
        if (m1.matches()) {
            type = Type.FeatureName;
            language = m1.group(1);
        } else if (m2.matches()) {
            type = Type.FeatureCoordName;
            coordIndex = Integer.valueOf(m2.group(1));
            language = m2.group(2);
        } else {
            type = null;
        }
    }

    /**
     * Returns the name in the given language of the feature as stored in the feature properties.
     * Returns null if no feature name is found.
     *
     * @param properties the feature properties
     * @param language the language
     * @return the name in the given language of the feature, or null if not found
     */
    public static String getFeatureName(Map<String, Object> properties, String language) {
        if (properties != null) {
            Object name = properties.get("name:" + language);
            return name == null ? null : name.toString();
        }
        return null;
    }

    /**
     * Returns the name in the given language of the coordinate as stored in the feature properties.
     * Returns null if no coordinate name is found.
     *
     * @param properties the feature properties
     * @param language the language
     * @param coordIndex the coordinate index
     * @return the name in the given language of the coordinate, or null if not found
     */
    public static String getFeatureCoordinateName(Map<String, Object> properties, String language, int coordIndex) {
        if (properties != null) {
            Object name = properties.get("name:" + coordIndex + ":" + language );
            return name == null ? null : name.toString();
        }
        return null;
    }

    public boolean isValid() {
        return type != null;
    }

    public boolean isFeatureName() {
        return type == Type.FeatureName;
    }

    public boolean isFeatureCoordName() {
        return type == Type.FeatureCoordName;
    }

    public String getKey() {
        return key.toString();
    }

    public Object getValue() {
        return value;
    }

    public String getValueString() {
        return value == null ? null : value.toString();
    }

    public String getLanguage() {
        return language;
    }

    public int getCoordIndex() {
        return coordIndex;
    }

    public Type getType() {
        return type;
    }
}
