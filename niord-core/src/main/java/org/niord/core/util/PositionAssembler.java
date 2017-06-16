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

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Reassembles a position
 */
@SuppressWarnings("unused")
public abstract class PositionAssembler {

    /**
     * Assembles the parts of a position into a whole position
     * @param degrees the degrees
     * @param minutes the minutes
     * @param direction the direction
     * @return the assembled position
     */
    public abstract String assemble(String degrees, String minutes, String direction);


    /**
     * Returns a new NAVTEX position assembler
     * @return a new NAVTEX position assembler
     */
    public static PositionAssembler newNavtexPositionAssembler() {
        return new NavtexPositionAssembler();
    }


    /**
     * Returns a new audio position assembler
     * @return a new audio position assembler
     */
    public static PositionAssembler newAudioPositionAssembler(Locale locale, ResourceBundle bundle) {
        return new AudioPositionAssembler(locale, bundle);
    }


    /**
     * Helper-class for assembling a NAVTEX position
     */
    private static class NavtexPositionAssembler extends PositionAssembler {

        /** {@inheritDoc} **/
        @Override
        public String assemble(String degrees, String minutes, String direction) {
            if (StringUtils.isBlank(degrees)) {
                return "";
            }
            StringBuilder position = new StringBuilder();
            position.append(degrees);
            if (StringUtils.isNotBlank(minutes)) {
                // Always English decimal separator
                minutes = minutes.replace(",", ".");
                position.append("-").append(minutes);
            }
            position.append(direction);
            return position.toString();
        }
    }


    /**
     * Helper-class for assembling an audio position
     */
    private static class AudioPositionAssembler extends PositionAssembler {

        final Locale locale;
        final String degTxt, minTxt;
        final Map<String, String> directions = new HashMap<>();

        public AudioPositionAssembler(Locale locale, ResourceBundle bundle) {
            this.locale = locale;
            degTxt = bundle.getString("position.deg");
            minTxt = bundle.getString("position.min");
            directions.put("N", bundle.getString("position.north"));
            directions.put("S", bundle.getString("position.south"));
            directions.put("E", bundle.getString("position.east"));
            directions.put("W", bundle.getString("position.west"));
        }

        /** {@inheritDoc} **/
        @Override
        public String assemble(String degrees, String minutes, String direction) {
            if (StringUtils.isBlank(degrees)) {
                return "";
            }
            StringBuilder position = new StringBuilder();
            position.append(degrees).append(" ").append(degTxt).append(" ");
            if (StringUtils.isNotBlank(minutes)) {
                // Update decimal separator
                DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(locale);
                char separator = formatter.getDecimalFormatSymbols().getDecimalSeparator();
                minutes = minutes.replaceAll("[,\\.]", String.valueOf(separator));
                position.append(minutes).append(" ").append(minTxt).append(" ");
            }
            position.append(StringUtils.defaultIfBlank(directions.get(direction), direction));
            return position.toString();
        }
    }


}
