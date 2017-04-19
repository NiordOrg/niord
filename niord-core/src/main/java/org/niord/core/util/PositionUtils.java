/*
 * Copyright 2017 Danish Maritime Authority.
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
package org.niord.core.util;

import org.apache.commons.lang.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.niord.core.util.PositionFormatter.LATLON_DEC;
import static org.niord.core.util.PositionFormatter.format;

/**
 * Lat-lon position utility methods.
 *
 * Handles wrapping and extraction of latitude and longitude in HTML.
 * Example of wrapped position:
 * <pre>
 *     <span data-latitude="56.8866">56° 53,195'N</span> - <span data-longitude="9.0417">009° 02,501'E</span>
 * </pre>
 */
@SuppressWarnings("unused")
public class PositionUtils {

    private static final int WRAPPED_POS_DECIMAL_NO     = 4;
    private static final String WRAPPED_LATITUDE_ATTR   = "data-latitude";
    private static final String WRAPPED_LONGITUDE_ATTR  = "data-longitude";

    private static Pattern WRAPPED_POS_PATTERN          = Pattern.compile(
            "(?i)<span (?<posAttr>(data-latitude|data-longitude))=\"(?<posVal>(\\d*\\.?\\d*))\">(.+?)</span>"
    );

    private static Pattern WRAPPED_POS_SEPARATOR_PATTERN = Pattern.compile(
            "(?i)(?<lat>(<span data-latitude=\"(\\d*\\.?\\d*)\">(.+?)</span>))"
            + "(?<separator>(.+))"
            + "(?<lon>(<span data-longitude=\"(\\d*\\.?\\d*)\">(.+?)</span>))"
    );

    /** Formats the latitude **/
    public static String formatLat(Locale locale, PositionFormatter.Format format, Double value, boolean wrap) {
        String result = format(locale, format.getLatFormat(), value);
        return wrap ? htmlWrap(result, WRAPPED_LATITUDE_ATTR, value) : result;
    }


    /** Formats the longitude **/
    public static String formatLon(Locale locale, PositionFormatter.Format format, Double value, boolean wrap) {
        String result = format(locale, format.getLonFormat(), value);
        return wrap ? htmlWrap(result, WRAPPED_LONGITUDE_ATTR, value) : result;
    }


    /**
     * Creates a position format suitable for audio, e.g. being read up on radio by a non-expert
     * @param bundle the language bundle containing relevant names used for the positions
     * @return the audio format
     */
    public static PositionFormatter.Format getAudioFormat(ResourceBundle bundle) {
        String deg = bundle.getString("position.deg");
        String min = bundle.getString("position.min");
        String ns = bundle.getString("position.north") + "," + bundle.getString("position.south");
        String ew = bundle.getString("position.east") + "," + bundle.getString("position.west");
        return new PositionFormatter.Format(
                "DEG-F[%d] " + deg + " MIN[%.1f] " + min + " DIR[" + ns + "]",
                "DEG-F[%d] " + deg + " MIN[%.1f] " + min + " DIR[" + ew + "]");
    }


    /**
     * Wraps the latitude or longitude in a html span element, e.g. <span data-latitude="56.8865">56° 53,195'N</span>
     * @param content the formatted latitude or longitude
     * @param attribute the span attribute to store the decimal value in
     * @param value the decimal latitude or longitude
     * @return the wrapped latitude or longitude
     */
    private static String htmlWrap(String content, String attribute, Double value) {

        String attrValue = value != null
                ? "" + new BigDecimal(value).setScale(WRAPPED_POS_DECIMAL_NO, RoundingMode.HALF_EVEN).doubleValue()
                : "";

        return String.format(
                "<span %s=\"%s\">%s</span>",
                attribute,
                attrValue,
                content
        );
    }


    /**
     * Replaces positions wrapped in the given HTML with positions using the given format and locale
     * @param locale the new locale to use
     * @param format the new format to use
     * @param html the HTML to transform
     * @return the transformed HTML
     */
    public static String replacePositions(Locale locale, PositionFormatter.Format format, String html) {

        if (StringUtils.isBlank(html)) {
            return html;
        }

        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        if (format == null) {
            format = LATLON_DEC;
        }

        Matcher m = WRAPPED_POS_PATTERN.matcher(html);

        StringBuilder result = new StringBuilder();
        int x = 0;
        while (m.find()) {
            String posAttr = m.group("posAttr");
            String posVal = m.group("posVal");
            if (StringUtils.isBlank(posAttr) || StringUtils.isBlank(posVal)) {
                continue;
            }
            result.append(html.substring(x, m.start()));
            x = m.end();

            Double value = Double.valueOf(posVal);

            String posFormat = WRAPPED_LATITUDE_ATTR.equals(posAttr) ? format.getLatFormat() : format.getLonFormat();
            result.append(format(locale, posFormat, value));
        }
        result.append(html.substring(x));

        return result.toString();
    }


    /**
     * Replaces separator characters, e.g. " - " between wrapped positions in HTML
     * @param html the HTML to transform
     * @return the transformed HTML
     */
    public static String replaceSeparator(String html, String newSeparator) {

        if (StringUtils.isBlank(html)) {
            return html;
        }

        Matcher m = WRAPPED_POS_SEPARATOR_PATTERN.matcher(html);

        StringBuilder result = new StringBuilder();
        int x = 0;
        while (m.find()) {
            String lat = m.group("lat");
            String lon = m.group("lon");
            String separator = m.group("separator");
            result.append(html.substring(x, m.start()));
            x = m.end();

            result.append(lat)
                    .append(StringUtils.defaultString(newSeparator))
                    .append(lon);
        }
        result.append(html.substring(x));

        return result.toString();
    }
}
