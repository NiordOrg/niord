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

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.niord.core.util.PositionFormatter.format;

/**
 * Lat-lon position utility methods.
 *
 * Handles formatting and extraction of latitude and longitude in HTML.
 */
@SuppressWarnings("unused")
public class PositionUtils {

    public static final String LATITUDE_FORMAT   = "(?<latDegs>[\\d\\.,]+)°\\s*((?<latMin>[\\d\\.,]+)')?(?<latDir>[NS])";
    public static final String LONGITUDE_FORMAT  = "(?<lonDegs>[\\d\\.,]+)°\\s*((?<lonMin>[\\d\\.,]+)')?(?<lonDir>[EW])";

    public static final Pattern LATITUDE_PATTERN  = Pattern.compile(LATITUDE_FORMAT);
    public static final Pattern LONGITUDE_PATTERN = Pattern.compile(LONGITUDE_FORMAT);
    public static final Pattern POSITION_PATTERN  = Pattern.compile(
            "(?<lat>"+ LATITUDE_FORMAT + ")(?<separator>\\s+-\\s+)" + "(?<lon>"+ LONGITUDE_FORMAT + ")");


    /** Formats the latitude **/
    public static String formatLat(Locale locale, PositionFormatter.Format format, Double value) {
       return format(locale, format.getLatFormat(), value);
    }


    /** Formats the longitude **/
    public static String formatLon(Locale locale, PositionFormatter.Format format, Double value) {
        return format(locale, format.getLonFormat(), value);
    }


    /**
     * Creates a position format suitable for audio, e.g. being read up on radio by a non-expert
     * @param bundle the language bundle containing relevant names used for the positions
     * @return the audio format
     */
    public static PositionFormatter.Format getAudioFormat(ResourceBundle bundle, int decimals) {
        String deg = bundle.getString("position.deg");
        String min = bundle.getString("position.min");
        String ns = bundle.getString("position.north") + "," + bundle.getString("position.south");
        String ew = bundle.getString("position.east") + "," + bundle.getString("position.west");
        return new PositionFormatter.Format(
                "DEG-F[%d] " + deg + " MIN[%." + decimals + "f] " + min + " DIR[" + ns + "]",
                "DEG-F[%d] " + deg + " MIN[%." + decimals + "f] " + min + " DIR[" + ew + "]");
    }


    /**
     * Replaces positions wrapped in the given HTML with positions using the given format and locale
     * @param html the HTML to transform
     * @param positionAssembler re-assembles a position
     * @return the transformed HTML
     */
    public static String updatePositionFormat(String html, PositionAssembler positionAssembler) {

        if (StringUtils.isBlank(html)) {
            return html;
        }

        // Replace latitude
        Matcher m = LATITUDE_PATTERN.matcher(html);
        StringBuilder result = new StringBuilder();
        int x = 0;
        while (m.find()) {
            String degs = m.group("latDegs");
            String mins = m.group("latMin");
            String dir = m.group("latDir");
            result.append(html.substring(x, m.start()));
            x = m.end();
            result.append(positionAssembler.assemble(degs, mins, dir));
        }
        html = result.append(html.substring(x)).toString();

        // Replace longitude
        m = LONGITUDE_PATTERN.matcher(html);
        result = new StringBuilder();
        x = 0;
        while (m.find()) {
            String degs = m.group("lonDegs");
            String mins = m.group("lonMin");
            String dir = m.group("lonDir");
            result.append(html.substring(x, m.start()));
            x = m.end();
            result.append(positionAssembler.assemble(degs, mins, dir));
        }
        html = result.append(html.substring(x)).toString();

        return html;
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

        Matcher m = POSITION_PATTERN.matcher(html);

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
