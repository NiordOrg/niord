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
import org.niord.core.util.PositionFormatter;
import org.niord.core.util.PositionUtils;
import org.niord.core.util.TextUtils;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.niord.core.util.PositionFormatter.LATLON_NAVTEX;

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
                    + "<span data-latitude=\"56.8866\">56° 53,195'N</span> - <span data-longitude=\"9.0417\">009° 02,501'E</span> "
                    + " is inoperative.<br>";

        // Replace positions with NAVTEX format
        String html2 = PositionUtils.replaceSeparator(html, " ");
        System.out.println("No separator: " + html2);

        String navtex = PositionUtils.replacePositions(Locale.ENGLISH, LATLON_NAVTEX, html2);
        navtex = TextUtils.trimHtmlWhitespace(navtex);
        navtex = navtex.replaceAll("(?is)\\s+(the|in pos\\.|is)\\s+", " ");
        System.out.println("NAVTEX: " + navtex);

        // Replace positions with audio format
        ResourceBundle bundle = ResourceBundle.getBundle("template", Locale.ENGLISH);
        PositionFormatter.Format audioFormat = PositionUtils.getAudioFormat(bundle);
        String audio = PositionUtils.replacePositions(Locale.ENGLISH, audioFormat, html);
        System.out.println("Audio: " + audio);

    }

}
