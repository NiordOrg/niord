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

package org.niord.web;

import org.junit.Test;
import org.niord.core.util.PositionAssembler;
import org.niord.core.util.PositionUtils;
import org.niord.core.util.TextUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;

import static org.niord.core.util.PositionUtils.POSITION_PATTERN;

/**
 * Test extracting positions from html and replacing them with the positions in a new format.
 *
 * NB: As this test class uses the template_en.properties resource bundle, it is placed in niord-web
 * rather than niord-core.
 */
@SuppressWarnings("unused")
public class PositionTest {

    @Test
    public void testUnwrappingPos() {
        String html = "RACON on  north cardinal buoy \"xx\" in pos.\n"
                    + "56° 53,195'N - 009° 02,501'E "
                    + " is inoperative.<br>";

        html = TextUtils.trimHtmlWhitespace(html);

        String navtex = PositionUtils.replaceSeparator(html, " ");
        PositionAssembler navtexPosAssembler = PositionAssembler.newNavtexPositionAssembler();
        navtex = PositionUtils.updatePositionFormat(navtex, navtexPosAssembler);
        navtex = navtex.replaceAll("(?is)\\s+(the|in pos\\.|is)\\s+", " ");
        System.out.println("NAVTEX: " + navtex);

        // Replace positions with audio format
        ResourceBundle bundle = ResourceBundle.getBundle("template", Locale.ENGLISH);
        PositionAssembler audioPosAssembler = PositionAssembler.newAudioPositionAssembler(Locale.ENGLISH, bundle);
        String audio = PositionUtils.updatePositionFormat(html, audioPosAssembler);
        System.out.println("Audio: " + audio);

    }


    @Test
    public void testPosRegEx() {
        String[] lats = {
                "Nordkardinalen på pos. 56° 55,4'N - 009° 05,3'E er permanent inddraget.",
                "The north cardinal light buoy in pos. 56° 55.4'N - 009° 05.3'E has been withdrawn.",
                "The north cardinal light buoy in pos. 56°N - 009°E has been withdrawn."
        };
        Arrays.stream(lats)
                .forEach(p -> {
                    Matcher m = POSITION_PATTERN.matcher(p);
                    if (m.find()) {
                        System.out.println(
                                "MATCH " + p +
                                        "\t\t-> deg=" + m.group("latDegs") +
                                        ", min=" + m.group("latMin") +
                                        ", dir=" + m.group("latDir"));
                    } else {
                        System.out.println("NO MATCH " + p);
                    }
                });
    }


}
