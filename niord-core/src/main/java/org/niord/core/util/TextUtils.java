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
package org.niord.core.util;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.examples.HtmlToPlainText;
import org.jsoup.nodes.Document;
import org.w3c.tidy.Tidy;

import java.io.StringReader;
import java.io.StringWriter;

/**
 * Text utility methods
 */
@SuppressWarnings("unused")
public class TextUtils {

    /**
     * Converts the text from html to plain text
     * @param html the html
     * @return the plain text version
     */
    public static String html2txt(String html) {
        try {
            Document doc = Jsoup.parse(html);
            return new HtmlToPlainText().getPlainText(doc.body());
        } catch (Exception e) {
            // If any error occurs, return the original html
            return html;
        }
    }


    /**
     * Converts the text from plain text to html
     * @param text the text
     * @return the html version
     */
    public static String txt2html(String text) {
        text = StringUtils.replaceEach(text,
                new String[]{"&", "\"", "<", ">", "\n", "\t"},
                new String[]{"&amp;", "&quot;", "&lt;", "&gt;", "<br>", "&nbsp;&nbsp;&nbsp;"});
        return text;
    }


    /**
     * Use JTidy to clean up the HTML
     * @param html the HTML to clean up
     * @return the resulting XHTML
     */
    public static org.w3c.dom.Document cleanHtml(String html) {
        Tidy tidy = new Tidy();

        tidy.setShowWarnings(false); //to hide errors
        tidy.setQuiet(true); //to hide warning

        tidy.setXHTML(true);
        return tidy.parseDOM(new StringReader(html), new StringWriter());
    }


}
