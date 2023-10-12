/* Copyright 2014 Malcolm Herring
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * For a copy of the GNU General Public License, see <http://www.gnu.org/licenses/>.
 */

package org.niord.web.aton;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.niord.core.aton.vo.AtonNodeVo;
import org.w3c.dom.Document;

import render.ChartContext;
import render.Renderer;
import s57.S57map;
import s57.S57map.Feature;
import s57.S57map.Snode;

/**
 * Renders the AtoN as either PNG or SVG.
 *
 * Based on the JSOM searchart Jicons class.
 */
public class AtonIconRenderer {

    public static final String SVG_NS = "http://www.w3.org/2000/svg";

	// Create a lock to avoid parallel graphic generations
	private static final Object lock = new Object();

	public static void renderIcon(AtonNodeVo aton, String format, OutputStream out, int w, int h, int x, int y, double s) throws IOException {

		// First, generate the map from the AtoN tags
		S57map map = new S57map(true);
		map.addNode(0, 0, 0);
		Arrays.stream(aton.getTags()).forEach(t -> map.addTag(t.getK(), t.getV()));
		map.tagsDone(0);

		// VAtoN are slightly bigger so be mindful
		int zoom = 16;
		double factor = (aton.isVAtoN()? 0.72 : 1.16) * s / Renderer.symbolScale[zoom];

		// Lock the generation with a synchronous lock
		synchronized (lock) {
			if ("PNG".equalsIgnoreCase(format)) {

				BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2 = img.createGraphics();

				Renderer.reRender(
						g2,
						new Rectangle(x, y, w, h),
						zoom,
						factor,
						map,
						new Context(w, h, x, y));

				ImageIO.write(img, "png", out);

			} else if ("SVG".equalsIgnoreCase(format)) {

				Document document = GenericDOMImplementation
						.getDOMImplementation()
						.createDocument(SVG_NS, "svg", null);

				SVGGraphics2D svgGenerator = new SVGGraphics2D(document);
				svgGenerator.setSVGCanvasSize(new Dimension(w, h));

				Renderer.reRender(
						svgGenerator,
						new Rectangle(x, y, w, h),
						zoom,
						factor,
						map,
						new Context(w, h, x, y));

				try {
					svgGenerator.stream(new OutputStreamWriter(out), true);
				} catch (SVGGraphics2DIOException e) {
					throw new IOException("Error generating SVG: " + e.getMessage(), e);
				}
			}
		}
	}


	static class Context implements ChartContext {

        int w, h, x, y;

        public Context(int w, int h, int x, int y) {
            this.w = w;
            this.h = h;
            this.x = x;
            this.y = y;
        }

        public Point2D getPoint(Snode coord) {
			return new Point2D.Double(x, y);
		}

		public double mile(Feature feature) {
			return Math.min(w, h);
		}

		public boolean clip() {
			return false;
		}

		public int grid() { return 0; }

		public Color background(S57map map) {
			return new Color(0, true);
		}

		public RuleSet ruleset() {
			return RuleSet.SEAMARK;
		}
	}
}
