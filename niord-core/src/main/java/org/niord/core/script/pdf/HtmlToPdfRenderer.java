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
package org.niord.core.script.pdf;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.PDFEncryption;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Used for creating a PDF file from HTML.
 * <p>
 * Important: If the HTML document contains inline SVG elements, it MUST defined the following CSS style:
 * <pre>
 * svg {
 *   display: inline-block;
 * }
 * </pre>
 */
public class HtmlToPdfRenderer {

    private Document doc = null;
    private String pdfEncryptionPassword = null;
    private String baseUri = "";
    private OutputStream pdf = null;

    /** No-access constructor **/
    private HtmlToPdfRenderer() {
    }

    /** Factory method for returning a HtmlToPdfRendererBuilder **/
    public static HtmlToPdfRendererBuilder newBuilder() {
        return new HtmlToPdfRendererBuilder();
    }


    /**
     * Renders the PDF from the HTML file
     */
    public void render() throws Exception {

        // Clean up HTML. Sometimes they paste html into fields that contain illegal tags, e.g. "<o:p></o:p>"
        cleanUpHtml();

        // Update all SVG elements
        updateSvgElements();

        // Convert the JSoup Document to an XML Document
        W3CDom w3cDom = new W3CDom();
        org.w3c.dom.Document xhtmlContent = w3cDom.fromJsoup(doc);

        // NB: We cannot use JTidy to clean the document, as it will remove all SVG tags :-(
        // TextUtils.cleanHtml(html);

        // Generate PDF from the HTML
        ITextRenderer renderer = new ITextRenderer();

        // Add support for SVG in PDF generation
        ChainingReplacedElementFactory chainingReplacedElementFactory = new ChainingReplacedElementFactory();
        chainingReplacedElementFactory.addReplacedElementFactory(renderer.getSharedContext().getReplacedElementFactory());
        chainingReplacedElementFactory.addReplacedElementFactory(new SVGReplacedElementFactory());
        renderer.getSharedContext().setReplacedElementFactory(chainingReplacedElementFactory);

        // Check if we need to encrypt the PDF
        if (StringUtils.isNotBlank(pdfEncryptionPassword)) {
            renderer.setPDFEncryption(new PDFEncryption(null, pdfEncryptionPassword.getBytes()));
        }

        renderer.setDocument(xhtmlContent, baseUri);
        renderer.layout();
        renderer.createPDF(pdf);
    }


    /**
     * Clean up HTML.
     * Sometimes they paste html into fields that contain illegal tags, e.g. "<o:p></o:p>"
     */
    private void cleanUpHtml() {
        // Remove tags with a namespace component to get rid of e.g. "<o:p></o:p>" tags
        doc.select("*").stream()
                .filter(e -> e.tagName().contains(":"))
                .forEach(Node::remove);
    }


    /**
     * Updates all the svg elements of the HTML document,
     * by copying svg width and height into a style attributes.
     * Otherwise, the PDF rendering just doesn't know how to size the SVG element.
     */
    private void updateSvgElements() {
        doc.select("svg").forEach(svg -> {
            String style = svg.attr("style");
            if (!style.matches("[^-]width")) {
                String width = svg.attr("width");
                if (StringUtils.isNotBlank(width)) {
                    if (StringUtils.isNumeric(width)) {
                        width += "px";
                    }
                    style = "width: " + width + ";" + style;
                }
            }
            if (!style.matches("[^-]height")) {
                String height = svg.attr("height");
                if (StringUtils.isNotBlank(height)) {
                    if (StringUtils.isNumeric(height)) {
                        height += "px";
                    }
                    style = "height: " + height + ";" + style;
                }
            }
            if (StringUtils.isNotBlank(style)) {
                svg.attr("style", style);
            }
        });
    }


    /**
     * Used by the client for building a new HtmlToPdfRenderer.
     *
     * Initialize the builder by calling HtmlToPdfRenderer.newBuilder()
     */
    public static class HtmlToPdfRendererBuilder {

        HtmlToPdfRenderer renderer = new HtmlToPdfRenderer();

        /** No-access constructor **/
        private HtmlToPdfRendererBuilder() {
        }


        /** Sets the raw HTML content **/
        public HtmlToPdfRendererBuilder html(String html) {
            renderer.doc = Jsoup.parse(html);
            return this;
        }

        /** Sets the HTML from an input stream **/
        public HtmlToPdfRendererBuilder html(InputStream in) throws Exception {
            renderer.doc = Jsoup.parse(in, "UTF-8", "");
            return this;
        }


        /** Sets the PDF encryption password, causing the PDF to be encrypted **/
        public HtmlToPdfRendererBuilder encrypt(String password) {
            renderer.pdfEncryptionPassword = password;
            return this;
        }


        /** Sets the base URI to use for the HTML **/
        public HtmlToPdfRendererBuilder baseUri(String baseUri) {
            renderer.baseUri = baseUri;
            return this;
        }


        /** Sets the output for the generated PDF **/
        public HtmlToPdfRendererBuilder pdf(OutputStream pdf) {
            renderer.pdf = pdf;
            return this;
        }


        /** Sets the output for the generated PDF **/
        public HtmlToPdfRendererBuilder pdf(File pdf) throws Exception {
            renderer.pdf = new FileOutputStream(pdf);
            return this;
        }


        /**
         * Returns an HtmlToPdfRenderer instantiated via this builder
         * @return an instantiated HtmlToPdfRenderer
         */
        public HtmlToPdfRenderer build() {

            // Check required parameters
            if (renderer.doc == null) {
                throw new IllegalArgumentException("No HTML defined");
            }
            if (renderer.pdf == null) {
                throw new IllegalArgumentException("No PDF output defined");
            }

            return renderer;
        }

    }

}
