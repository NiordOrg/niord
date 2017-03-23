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

package org.niord.core.aton;

import org.apache.commons.lang.StringUtils;
import org.niord.core.aton.LightCharacterModel.LightGroup;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a light characteristic string into a LightModel and
 * formats the LightModel into a human readable text for a specific language.
 */
public class LightCharacterParser {

    public static String LIGHT_PHASES = "FFl|LFl|Fl|F|IVQ|VQ|IQ|IUQ|UQ|Q|Iso|Oc|Al|Mo|Gr";

    public static String LIGHT_COLORS = "W|R|G|Bu|Y|Am";

    public static Pattern LIGHT_CHARACTER_GROUPS = Pattern.compile(
            "^" +
            "(?<phase>(" + LIGHT_PHASES + "))" +
            "(\\((?<group>\\w+(\\+\\w+)*)\\))?\\s*" +
            "(?<colors>(" + LIGHT_COLORS + ")*)?"
    );

    public static Pattern LIGHT_FORMAT = Pattern.compile(
            "(?<color>" + LIGHT_COLORS + ")"
    );

    private static final Pattern ELEVATION = Pattern.compile("(\\d+)m");

    private static final Pattern PERIOD = Pattern.compile("(\\d+)s");

    private static final Pattern RANGE = Pattern.compile("(\\d+)M");


    /** No-access constructor **/
    private LightCharacterParser() {
    }


    /**
     * Returns an instance of the light character parser
     * @return an instance of the light character parser
     */
    public static LightCharacterParser getInstance() {
        return new LightCharacterParser();
    }


    /**
     * Normalizes the light character for easier parsing
     * @param lightCharacter the light character to normalize
     * @return the normalized light character
     */
    public String normalize(String lightCharacter) {
        if (StringUtils.isBlank(lightCharacter)) {
            return lightCharacter;
        }
        return lightCharacter
                        .replace("Qk", "Q")
                        .replace("Bu", "B")
                        .replace("Bl", "B")
                        .replace("Occ", "Oc")
                        .replace("Alt", "Al")
                        .replace("Gp", "")
                        .replace(".", "")
                        .replaceAll("\\s+"," ")
                        .trim();
    }


    /**
     * Parses the light characteristic string into a light model
     * @param lightCharacter the light characteristic
     * @return the light model
     */
    public LightCharacterModel parse(String lightCharacter) throws Exception {
        if (StringUtils.isBlank(lightCharacter)) {
            throw new Exception("Blank light character");
        }

        // Normalize the light character
        String lc = normalize(lightCharacter);
        LightCharacterModel lightModel = new LightCharacterModel();

        while (true) {
            Matcher m = LIGHT_CHARACTER_GROUPS.matcher(lc);

            if (m.find()) {
                String phaseSpec = m.group("phase");
                String colorsSpec = m.group("colors");
                String groupSpec = m.group("group");


                lc = lc.substring(m.end()).trim();
                if (lc.startsWith("+")) {
                    lc = lc.substring(1).trim();
                }

                LightGroup lightGroup = new LightGroup();
                lightModel.getLightGroups().add(lightGroup);

                // Phase
                if (StringUtils.isNotBlank(phaseSpec)) {
                    lightGroup.setPhase(phaseSpec);
                }

                // Colors
                if (StringUtils.isNotBlank(colorsSpec)) {
                    Matcher cm = LIGHT_FORMAT.matcher(colorsSpec);
                    while (cm.find()) {
                        lightGroup.getColors().add(cm.group("color"));
                    }
                }

                // Group
                if (StringUtils.isNotBlank(groupSpec)) {
                    if ("Mo".equalsIgnoreCase(phaseSpec)) {
                        lightGroup.setMorseCode(groupSpec);
                    } else {
                        lightGroup.setGrouped(true);
                        Arrays.stream(groupSpec.split("\\+"))
                                .map(g -> Integer.valueOf(g.trim()))
                                .forEach(g -> lightGroup.getGroupSpec().add(g));
                    }
                }
            } else {
                break;
            }
        }

        if (lightModel.getLightGroups().isEmpty()) {
            throw new Exception("Invalid light character format: " + lightCharacter);
        }

        // Extract elevation
        Matcher m = ELEVATION.matcher(lc);
        if (m.find()) {
            lightModel.setElevation(Integer.valueOf(m.group(1)));
        }

        // Extract period
        m = PERIOD.matcher(lc);
        if (m.find()) {
            lightModel.setPeriod(Integer.valueOf(m.group(1)));
        }

        // Extract range
        m = RANGE.matcher(lc);
        if (m.find()) {
            lightModel.setRange(Integer.valueOf(m.group(1)));
        }

        return lightModel;
    }

}
