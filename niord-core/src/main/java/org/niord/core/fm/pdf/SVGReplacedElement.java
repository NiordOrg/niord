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
package org.niord.core.fm.pdf;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.print.PrintTranscoder;
import org.w3c.dom.Document;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextReplacedElement;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.PageBox;
import org.xhtmlrenderer.render.RenderingContext;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Paper;

/**
 * Used for producing SVG in Flying Saucer PDF reports.
 * Kindly borrowed from:
 * http://www.samuelrossille.com/posts/2013-08-13-render-html-with-svg-to-pdf-with-flying-saucer.html
 */
public class SVGReplacedElement implements ITextReplacedElement {

    private Point location = new Point(0, 0);
    private Document svg;
    private int cssWidth;
    private int cssHeight;

    /** Constructor **/
    public SVGReplacedElement(Document svg, int cssWidth, int cssHeight) {
        this.cssWidth = cssWidth;
        this.cssHeight = cssHeight;
        this.svg = svg;
    }


    /** {@inheritDoc} **/
    @Override
    public void detach(LayoutContext c) {
    }


    /** {@inheritDoc} **/
    @Override
    public int getBaseline() {
        return 0;
    }


    /** {@inheritDoc} **/
    @Override
    public int getIntrinsicWidth() {
        return cssWidth;
    }


    /** {@inheritDoc} **/
    @Override
    public int getIntrinsicHeight() {
        return cssHeight;
    }


    /** {@inheritDoc} **/
    @Override
    public boolean hasBaseline() {
        return false;
    }


    /** {@inheritDoc} **/
    @Override
    public boolean isRequiresInteractivePaint() {
        return false;
    }


    /** {@inheritDoc} **/
    @Override
    public Point getLocation() {
        return location;
    }


    /** {@inheritDoc} **/
    @Override
    public void setLocation(int x, int y) {
        this.location.x = x;
        this.location.y = y;
    }


    /** {@inheritDoc} **/
    @Override
    public void paint(RenderingContext renderingContext, ITextOutputDevice outputDevice,
                      BlockBox blockBox) {
        PdfContentByte cb = outputDevice.getWriter().getDirectContent();
        float width = cssWidth / outputDevice.getDotsPerPoint();
        float height = cssHeight / outputDevice.getDotsPerPoint();

        PdfTemplate template = cb.createTemplate(width, height);
        Graphics2D g2d = new PdfGraphics2D(template, width, height);
        PrintTranscoder prm = new PrintTranscoder();
        TranscoderInput ti = new TranscoderInput(svg);
        prm.transcode(ti, null);
        PageFormat pg = new PageFormat();
        Paper pp = new Paper();
        pp.setSize(width, height);
        pp.setImageableArea(0, 0, width, height);
        pg.setPaper(pp);
        prm.print(g2d, pg, 0);
        g2d.dispose();

        PageBox page = renderingContext.getPage();
        float x = blockBox.getAbsX() + page.getMarginBorderPadding(renderingContext, CalculatedStyle.LEFT);
        float y = (page.getBottom() - (blockBox.getAbsY() + cssHeight)) + page.getMarginBorderPadding(
                renderingContext, CalculatedStyle.BOTTOM);
        x /= outputDevice.getDotsPerPoint();
        y /= outputDevice.getDotsPerPoint();

        cb.addTemplate(template, x, y);
    }
}
