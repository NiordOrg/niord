package org.niord.web.map;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps a feature name or feature coordinate name.
 *
 * These are stored as properties in a GeoJson feature using the following patterns
 * <ul>
 *     <li>Feature name: "name#lang" - where lang is the 2 character language code.</li>
 *     <li>Feature coordinate name: "name#index#lang" - where index is the coordinate index
 *             and lang is the 2 character language code.</li>
 * </ul>
 */
public class FeatureName {

    private static Pattern FEATURE_NAME = Pattern.compile("^name#([a-zA-Z_]+)$");
    private static Pattern FEATURE_COORD_NAME = Pattern.compile("^name#(\\d+)#([a-zA-Z_]+)$");

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
    public FeatureName(Map.Entry<Object, Object> kv) {
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
