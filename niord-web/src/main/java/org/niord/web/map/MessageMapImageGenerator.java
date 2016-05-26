/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.web.map;

import com.vividsolutions.jts.awt.PointShapeFactory;
import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.Geometry;
import org.niord.core.NiordApp;
import org.niord.core.geojson.FeatureName;
import org.niord.core.message.Message;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.util.GlobalMercator;
import org.niord.core.util.GraphicsUtils;
import org.niord.model.vo.MainType;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.niord.model.vo.geojson.FeatureVo;
import org.niord.model.vo.geojson.GeometryCollectionVo;
import org.niord.model.vo.geojson.GeometryVo;
import org.niord.model.vo.geojson.LineStringVo;
import org.niord.model.vo.geojson.MultiLineStringVo;
import org.niord.model.vo.geojson.MultiPointVo;
import org.niord.model.vo.geojson.MultiPolygonVo;
import org.niord.model.vo.geojson.PointVo;
import org.niord.model.vo.geojson.PolygonVo;
import org.slf4j.Logger;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static org.niord.core.settings.Setting.Type.Integer;

/**
 * Generates message map thumbnail images.
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class MessageMapImageGenerator {

    static final String STATIC_IMAGE_URL = "%s?center=%f,%f&zoom=%d&size=%dx%d";

    static final int ICON_SIZE = 20;

    static final float LINE_WIDTH = 1.0f;
    static final Color LINE_COLOR = new Color(143, 47, 123);
    static final Color FILL_COLOR = new Color(173, 87, 161, 80);
    static final Color BUFFER_LINE_COLOR = new Color(80, 80, 80, 80);
    static final Color BUFFER_FILL_COLOR = new Color(100, 100, 100, 60);

    static final GlobalMercator mercator = new GlobalMercator();

    @Inject
    Logger log;

    @Inject
    NiordApp app;

    @Inject
    @Setting(value = "mapImageServer", defaultValue = "http://staticmap.openstreetmap.de/staticmap.php",
            description = "URL of static-map service used for generation map thumbnails")
    String mapImageServer;

    @Inject
    @Setting(value = "mapImageSize", defaultValue = "256", type = Integer,
            description = "Size of map thumbnails")
    Integer mapImageSize;

    @Inject
    @Setting(value = "mapImageIndent", defaultValue = "22", type = Integer,
            description = "The indentation of map thumbnails")
    Integer mapImageIndent;

    @Inject
    @Setting(value = "mapImageZoomLevel", defaultValue = "8", type = Integer,
            description = "The map thumbnail zoom level used for single-position messages.")
    Integer zoomLevel;

    private Image nwImage;
    private Image nmImage;


    /** Returns the size of the map image */
    public java.lang.Integer getMapImageSize() {
        return mapImageSize;
    }


    /**
     * Fetches the map image and crops it if specified
     * @param centerPt the center point
     * @param zoom the zoom level
     * @return the image
     */
    protected BufferedImage fetchMapImage(double[] centerPt, int zoom) throws  IOException {
        // Fetch the image
        long fetchSize = mapImageSize + 2 * mapImageIndent;
        String url = String.format(
                STATIC_IMAGE_URL,
                mapImageServer,
                centerPt[1],
                centerPt[0],
                zoom,
                fetchSize,
                fetchSize);

        URLConnection con = new URL(url).openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        BufferedImage image;
        try (InputStream in = con.getInputStream()) {
            image = ImageIO.read(in);
        }

        // Check if we need to crop the image (e.g. to remove watermarks)
        if (mapImageIndent > 0) {
            // NB: sub-images share the same image buffer as the source image
            image = image.getSubimage(
                    mapImageIndent,
                    mapImageIndent,
                    mapImageSize,
                    mapImageSize);
        }

        return image;
    }

    /**
     * Attempts to create a map image for the locations at the given path
     * @param message the feature collection
     * @param imageRepoPath the path of the image
     * @return if the image file was properly created
     */
    public boolean generateMessageMapImage(Message message, Path imageRepoPath) throws IOException {

        long t0 = System.currentTimeMillis();

        FeatureCollectionVo fc = message.getGeometry().toGeoJson();

        if (fc.getFeatures() != null && fc.getFeatures().length > 0) {

            boolean isSinglePoint = fc.getFeatures().length == 1 &&
                        fc.getFeatures()[0].getGeometry() instanceof PointVo;

            // Compute the bounds of the feature geometry and compute the center
            double[] bbox = fc.computeBBox();
            double[] center = fc.computeCenter();

            // Find zoom level where polygon is at most 80% of bitmap width/height, and zoom level in
            // the range of 12 to 4. See http://wiki.openstreetmap.org/wiki/Zoom_levels
            int maxWH = (int) (mapImageSize.doubleValue() * 0.8);
            int zoom = (isSinglePoint)
                    ? zoomLevel
                    : computeZoomLevel(bbox, maxWH, maxWH, 12, 3);

            // Fetch the background OpenStreetMap image
            BufferedImage image = fetchMapImage(center, zoom);

            Graphics2D g2 = image.createGraphics();
            GraphicsUtils.antialias(g2);
            g2.setStroke(new BasicStroke(LINE_WIDTH));
            g2.setFont(new Font("Helvetica", Font.PLAIN, 11));

            // Convert Feature coordinates from lon-lat to image-relative XY.
            int[] rxy = new int[] { -mapImageSize / 2, -mapImageSize / 2 };
            int cxy[] = mercator.LatLonToPixels(center[1], center[0], zoom);
            fc.visitCoordinates(coordinate -> {
                int xy[] = mercator.LatLonToPixels(coordinate[1], coordinate[0], zoom);
                coordinate[0] = xy[0] - cxy[0] - rxy[0];
                coordinate[1] = cxy[1] - xy[1] - rxy[1];
            });

            // Draw each feature
            Arrays.asList(fc.getFeatures())
                    .forEach(f -> drawGeometry(f, f.getGeometry(), g2, getMessageImage(message)));

            // Draw labels
            Arrays.asList(fc.getFeatures())
                    .forEach(f -> drawLabels(f, g2, "da"));

            g2.dispose();

            // Save the image to the repository
            ImageIO.write(image, "png", imageRepoPath.toFile());
            image.flush();

            // Update the timestamp of the image file to match the change date of the message
            Files.setLastModifiedTime(
                    imageRepoPath,
                    FileTime.fromMillis(message.getUpdated().getTime()));

            log.info("Saved image for to file " + imageRepoPath + " in " +
                    (System.currentTimeMillis() - t0) + " ms");
            return true;
        }

        return false;
    }

    /**
     * Draws the geometry
     * @param f the parent GeoJson feature
     * @param g the geometry to draw
     * @param g2 the graphical context
     * @param pointIndicator the image to draw for Point geometries
     */
    private void drawGeometry(FeatureVo f, GeometryVo g, Graphics2D g2, Image pointIndicator) {

        boolean buffered = isBufferGeometry(f);

        if (g instanceof PointVo || g instanceof MultiPointVo) {
            double[][] coordinates = (g instanceof PointVo)
                    ? new double[][] { ((PointVo)g).getCoordinates() }
                    : ((MultiPointVo)g).getCoordinates();

            for (int x = 0; coordinates != null && x < coordinates.length; x++) {
                double[] coordinate = coordinates[x];
                if (coordinate != null && coordinate.length >= 2) {
                    g2.drawImage(pointIndicator,
                            (int) (coordinate[0] - ICON_SIZE / 2.0),
                            (int) (coordinate[1] - ICON_SIZE / 2.0),
                            ICON_SIZE,
                            ICON_SIZE,
                            null);
                }
            }

        } else if (g instanceof LineStringVo || g instanceof MultiLineStringVo) {
            Shape shape = convertToShape(g);
            if (shape != null) {
                g2.setColor(buffered ? BUFFER_LINE_COLOR : LINE_COLOR);
                g2.draw(shape);
            }

        } else if (g instanceof PolygonVo || g instanceof MultiPolygonVo) {
            Shape shape = convertToShape(g);
            if (shape != null) {
                g2.setColor(buffered ? BUFFER_FILL_COLOR : FILL_COLOR);
                g2.fill(shape);
                g2.setColor(buffered ? BUFFER_LINE_COLOR : LINE_COLOR);
                g2.draw(shape);
            }

        } else if (g instanceof GeometryCollectionVo) {
            GeometryCollectionVo geometryCollection = (GeometryCollectionVo)g;
            if (geometryCollection.getGeometries() != null) {
                for (GeometryVo geometry : geometryCollection.getGeometries()) {
                    drawGeometry(f, geometry, g2, pointIndicator);
                }
            }
        }
    }


    /**
     * Draws the feature labels
     * @param f the feature
     * @param g2 the graphical context
     * @param lang the language code
     */
    private void drawLabels(FeatureVo f, Graphics2D g2, String lang) {
        // Sanity check
        if (f.getProperties() == null) {
            return;
        }

        f.getProperties().entrySet().stream()
                .map(FeatureName::new)
                .filter(fn -> fn.isValid() && fn.getLanguage().equals(lang))
                .forEach(fn -> {

                    // Compute the coordinate
                    double[] coord = null;
                    boolean coordName = fn.isFeatureCoordName();
                    if (fn.isFeatureName()) {
                        coord = f.computeCenter();
                    } else if (fn.isFeatureCoordName()) {
                        coord = f.computeCoordinate(fn.getCoordIndex());
                    }

                    // Draw the label at the resolved coordinate
                    if (coord != null) {
                        drawLabel(g2, fn.getValueString(), coord, coordName);
                    }
                });
    }

    /**
     * Draws a specific label at the given coordinate
     * @param g2 the graphical context
     * @param label the label to draw
     * @param coord the coordinate to draw the label at
     * @param coordName whether the label is a feature label or a feature coordinate label
     */
    private void drawLabel(Graphics2D g2, String label, double[] coord, boolean coordName) {
        // Draw a point indicator for coordinate labels
        if (coordName) {
            double radius = 2.5;
            Shape circle = new Ellipse2D.Double(coord[0] - radius, coord[1] - radius, 2.0 * radius, 2.0 * radius);
            g2.setColor(LINE_COLOR);
            g2.fill(circle);
        }

        // Draw the text
        FontMetrics metrics = g2.getFontMetrics();
        int h = metrics.getHeight();
        int w = metrics.stringWidth(label);
        float x = (float) (coord[0] - w / 2.0);
        float y = (float) (coord[1] + (coordName ? h : h / 2.0) + 2.0);
        g2.setColor(Color.WHITE);
        g2.drawString(label, x - 1.0f, y - 1.0f);
        g2.setColor(LINE_COLOR);
        g2.drawString(label, x, y);
    }

    /**
     * Converts the geometry to a java Shape
     * @param g the GeoJson geometry
     * @return the corresponding Java2D Shape
     */
    private Shape convertToShape(GeometryVo g) {
        try {
            Geometry geometry = GeoJsonUtils.toJts(g);
            ShapeWriter writer = new ShapeWriter(null, new PointShapeFactory.Circle(3.0));
            return writer.toShape(geometry);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns if the feature represents a buffered geometry
     * @param f the feature to check
     * @return if the feature represents a buffered geometry for another feature
     */
    private boolean isBufferGeometry(FeatureVo f) {
        return f.getProperties() != null &&
                f.getProperties().containsKey("parentFeatureId");
    }

    /**
     * Computes the zoom level based on the bitmap size and the bounds
     * @param bbox the bounds
     * @param maxWidth the maximum bitmap width
     * @param maxHeight the maximum bitmap height
     * @param maxZoomLevel  the maximum zoom level
     * @param minZoomLevel the minimum zoom level
     * @return the optimal zoom level
     */
    private int computeZoomLevel(double[] bbox, int maxWidth, int maxHeight, int maxZoomLevel, int minZoomLevel) {
        for (int zoom = maxZoomLevel; zoom > minZoomLevel; zoom--) {
            int xy0[] = mercator.LatLonToPixels(bbox[1], bbox[0], zoom);
            int xy1[] = mercator.LatLonToPixels(bbox[3], bbox[2], zoom);
            if (xy1[0] - xy0[0] <= maxWidth && xy1[1] - xy0[1] <= maxHeight) {
                return zoom;
            }
        }
        return minZoomLevel;
    }

    /**
     * Depending on the type of message, return an MSI or an NM image
     * @return the corresponding image
     */
    public Image getMessageImage(Message message) {
        return message.getType().getMainType() == MainType.NM
                ? getNmImage()
                : getNwImage();
    }


    /**
     * Returns the NW symbol image
     * @return the NW symbol image
     */
    private synchronized Image getNwImage() {
        if (nwImage == null) {
            String imageUrl = app.getBaseUri() + "/img/nw.png";
            try {
                nwImage = ImageIO.read(new URL(imageUrl));
            } catch (IOException e) {
                log.error("This should never happen - could not load image from " + imageUrl);
            }
        }
        return nwImage;
    }

    /**
     * Returns the NM symbol image
     * @return the NM symbol image
     */
    private synchronized Image getNmImage() {
        if (nmImage == null) {
            String imageUrl = app.getBaseUri() + "/img/nm.png";
            try {
                nmImage = ImageIO.read(new URL(imageUrl));
            } catch (IOException e) {
                log.error("This should never happen - could not load image from " + imageUrl);
            }
        }
        return nmImage;
    }
}
