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
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
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
import java.util.List;
import java.util.function.Function;

/**
 * Text utility methods
 */
@SuppressWarnings("unused")
public class TextUtils {

    /**
     * Converts the text from html to plain text
     * @param html the html
     * @param cleanUpWhiteSpace if set, clean up newlines and white-space
     * @return the plain text version
     */
    public static String html2txt(String html, boolean cleanUpWhiteSpace) {
        if (StringUtils.isNotBlank(html)) {

            if (cleanUpWhiteSpace) {
                html = trimHtmlWhitespace(html);
            }

            try {
                Document doc = Jsoup.parse(html);

                // Do not use the following as it paginates each line to 80 characters
                //return new HtmlToPlainText().getPlainText(doc.body()).trim();

                FormattingVisitor formatter = new FormattingVisitor();
                NodeTraversor traversor = new NodeTraversor(formatter);
                traversor.traverse(doc);
                return formatter.toString();
            } catch (Exception ignored) {
            }
        }
        // If blank, or if any error occurs, return the original html
        return html;
    }


    /**
     * Converts the text from html to plain text
     * @param html the html
     * @return the plain text version
     */
    public static String html2txt(String html) {
        return html2txt(html, false);
    }


    /** Trims whitespace from the HTML **/
    public static String trimHtmlWhitespace(String html) {
        if (StringUtils.isNotBlank(html)) {
            html = html.replace("\n", " ")
                    .replace("\r", " ")
                    .replaceAll("\\s+", " ").trim();

        }
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
     * Used for e.g. NAVTEX. Split the text into lines of the max length
     * @param s the text
     * @param maxLength the max length of the individual lines
     * @return the result
     */
    public static String maxLineLength(String s, int maxLength) {
        if (StringUtils.isBlank(s)) {
            return s;
        }

        StringBuilder sb = new StringBuilder();
        while (s.length() > maxLength) {
            String t = s.substring(0, maxLength);
            int idx = t.indexOf("\n");
            if (idx == -1) {
                idx = t.lastIndexOf(" ");
            }
            if (idx != -1) {
                sb.append(s.substring(0, idx + 1).trim()).append("\n");
                s = s.substring(idx + 1).trim();
            } else {
                sb.append(t.trim()).append("\n");
                s = s.substring(t.length()).trim();
            }
        }
        sb.append(s);
        s = sb.toString();

        if (!s.endsWith("\n")) {
            s = s + "\n";
        }
        return s;
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


    /**
     * Removes any trailing dot character
     * @param text the text to remove a trailing dot from
     * @return the updated text
     **/
    public static String removeTrailingDot(String text) {
        if (StringUtils.isNotBlank(text)) {
            text = text.trim();
            while (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1).trim();
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
     * Used for joining strings with a different last delimiter.
     * Credits: http://stackoverflow.com/questions/34936771/join-strings-with-different-last-delimiter
     * <p>
     * Usage:
     * <pre>
     *  list.stream()
     *    .collect(
     *      Collectors.collectingAndThen(Collectors.toList(), joiningLastDelimiter(", ", " and "))
     *    );
     * </pre>
     *
     * @param delimiter the delimiter
     * @param lastDelimiter the last delimiter
     * @return the joining function
     */
    public static Function<List<String>, String> joiningLastDelimiter(
            String delimiter, String lastDelimiter) {
        return list -> {
            int last = list.size() - 1;
            if (last < 1) return String.join(delimiter, list);
            return String.join(lastDelimiter,
                    String.join(delimiter, list.subList(0, last)),
                    list.get(last));
        };
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


    /**
     * Copied from JSoup {@linkplain HtmlToPlainText}, but max-width removed
     */
    private static class FormattingVisitor implements NodeVisitor {
        private int width = 0;
        private StringBuilder accum = new StringBuilder(); // holds the accumulated text

        // hit when the node is first seen
        public void head(Node node, int depth) {
            String name = node.nodeName();
            if (node instanceof TextNode) {
                append(((TextNode) node).text()); // TextNodes carry all user-readable text in the DOM.
            } else if (name.equals("li")) {
                append("\n * ");
            } else if (name.equals("dt")) {
                append("  ");
            } else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5", "tr")) {
                append("\n");
            }
        }

        // hit when all of the node's children (if any) have been visited
        public void tail(Node node, int depth) {
            String name = node.nodeName();
            if (StringUtil.in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5")) {
                append("\n");
            } else if (name.equals("a")) {
                append(String.format(" <%s>", node.absUrl("href")));
            }
        }

        // appends text to the string builder with a simple word wrap method
        private void append(String text) {
            if (text.startsWith("\n")) {
                width = 0; // reset counter if starts with a newline. only from formats above, not in natural text
            }
            if (text.equals(" ") &&
                    (accum.length() == 0 || StringUtil.in(accum.substring(accum.length() - 1), " ", "\n"))) {
                return; // don't accumulate long runs of empty spaces
            }
            accum.append(text);
            width += text.length();
        }

        @Override
        public String toString() {
            return accum.toString();
        }
    }
}
