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
import java.util.stream.Collectors;

/**
 * Parses a light characteristic string into a LightModel and
 * formats the LightModel into a human readable text for a specific language.
 */
@SuppressWarnings("unused")
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
                        String morseCode = getTelephonyCode(groupSpec);
                        lightGroup.setMorseCode(morseCode);
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
            lc = removeMatch(lc, m);
        }

        // Extract period
        m = PERIOD.matcher(lc);
        if (m.find()) {
            lightModel.setPeriod(Integer.valueOf(m.group(1)));
            lc = removeMatch(lc, m);
        }

        // Extract range
        m = RANGE.matcher(lc);
        if (m.find()) {
            lightModel.setRange(Integer.valueOf(m.group(1)));
            lc = removeMatch(lc, m);
        }

        if (StringUtils.isNotBlank(lc)) {
            throw new Exception("Invalid light character format: " + lightCharacter + ". Unknown part " + lc);
        }

        return lightModel;
    }


    /** Removes a regex match from the string **/
    private String removeMatch(String text, Matcher matcher) {
        String result = text.substring(0, matcher.start())
                + text.substring(matcher.end());
        return result.trim();
    }


    /**
     * Returns the telephony code for the given character
     * @param c the character. Valid intervals: a-z and 0-9
     * @return the telephony code or a blank string if invalid
     */
    public String getTelephonyCode(char c) {
        switch (Character.toUpperCase(c)) {
            case 'A': return "Alpha";
            case 'B': return "Bravo";
            case 'C': return "Charlie";
            case 'D': return "Delta";
            case 'E': return "Echo";
            case 'F': return "Foxtrot";
            case 'G': return "Golf";
            case 'H': return "Hotel";
            case 'I': return "India";
            case 'J': return "Juliet";
            case 'K': return "Kilo";
            case 'L': return "Lima";
            case 'M': return "Mike";
            case 'N': return "November";
            case 'O': return "Oscar";
            case 'P': return "Papa";
            case 'Q': return "Quebec";
            case 'R': return "Romeo";
            case 'S': return "Sierra";
            case 'T': return "Tango";
            case 'U': return "Uniform";
            case 'V': return "Victor";
            case 'W': return "Whiskey";
            case 'X': return "X-ray";
            case 'Y': return "Yankee";
            case 'Z': return "Zulu";
            case '1': return "One";
            case '2': return "Two";
            case '3': return "Three";
            case '4': return "Four";
            case '5': return "Five";
            case '6': return "Six";
            case '7': return "Seven";
            case '8': return "Eight";
            case '9': return "Nine";
            case '0': return "Zero";
        }
        return "";
    }


    /**
     * Returns the telephony code for the given string
     * @param text the string to return a telephony code for. Valid intervals: a-z and 0-9
     * @return the telephony code or a blank string if invalid
     */
    public String getTelephonyCode(String text) {
        return text.chars()
                .mapToObj(c -> (char)c)
                .map(this::getTelephonyCode)
                .collect(Collectors.joining("-"));
    }


    /**
     * Returns if the telephony code for the given string is valid
     * @param text the string to validate. Valid intervals: a-z and 0-9
     * @return if the telephony code for the given string is valid
     */
    public boolean telephonyCodeValid(String text) {
        return text.chars()
                .mapToObj(c -> (char)c)
                .allMatch(c -> StringUtils.isNotBlank(getTelephonyCode(c)));
    }

}
