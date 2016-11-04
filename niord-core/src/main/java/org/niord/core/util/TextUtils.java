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

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
        if (StringUtils.isNotBlank(html)) {
            try {
                Document doc = Jsoup.parse(html);
                return new HtmlToPlainText().getPlainText(doc.body());
            } catch (Exception ignored) {
            }
        }
        // If blank, or if any error occurs, return the original html
        return html;
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


    /**
     * Ensures that the string ends with a trailing dot character
     * @param text the text to add a trailing dot to
     * @return the updated text
     **/
    public static String trailingDot(String text) {
        if (StringUtils.isNotBlank(text)) {
            text = text.trim();
            if (!text.endsWith(".")) {
                text = text + ".";
            }
        }
        return text;
    }


    /** Simple case-insensitive comparison between two strings **/
    public static int compareIgnoreCase(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return 0;
        } else if (s1 == null) {
            return 1;
        } else if (s2 == null) {
            return -1;
        }
        return s1.toLowerCase().compareTo(s2.toLowerCase());
    }

    /**
     * Prints an XML document to the output stream
     * @param doc the document to print
     * @param out the output stream
     */
    public static void printDocument(org.w3c.dom.Document doc, OutputStream out) throws IOException, TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        transformer.transform(new DOMSource(doc),
                new StreamResult(new OutputStreamWriter(out, "UTF-8")));
    }

}
