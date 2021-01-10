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
package org.niord.web.map;

import org.locationtech.jts.awt.PointShapeFactory;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Geometry;
import org.niord.core.NiordApp;
import org.niord.core.geojson.FeatureName;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.message.Message;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.util.GlobalMercator;
import org.niord.core.util.GraphicsUtils;
import org.niord.model.message.MainType;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.FeatureVo;
import org.niord.model.geojson.GeometryCollectionVo;
import org.niord.model.geojson.GeometryVo;
import org.niord.model.geojson.LineStringVo;
import org.niord.model.geojson.MultiLineStringVo;
import org.niord.model.geojson.MultiPointVo;
import org.niord.model.geojson.MultiPolygonVo;
import org.niord.model.geojson.PointVo;
import org.niord.model.geojson.PolygonVo;
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
import java.io.ByteArrayInputStream;
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
    @Setting(value = "mapImageSize", defaultValue = "256", type = Integer, web = true,
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
    public boolean generateMessageMapImage(Message message, FeatureCollectionVo[] fcs, Path imageRepoPath) throws IOException {

        long t0 = System.currentTimeMillis();

        // Compute the bounds of the feature geometry and compute the center
        double[] bbox = GeoJsonUtils.computeBBox(fcs);
        double[] center = GeoJsonUtils.computeCenter(fcs);

        if (fcs != null && bbox != null && center != null) {

            boolean isSinglePoint = fcs.length == 1 && fcs[0].getFeatures().length == 1 &&
                        fcs[0].getFeatures()[0].getGeometry() instanceof PointVo;


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
            Arrays.stream(fcs)
                    .forEach(fc -> fc.visitCoordinates(coordinate -> {
                        int xy[] = mercator.LatLonToPixels(coordinate[1], coordinate[0], zoom);
                        coordinate[0] = xy[0] - cxy[0] - rxy[0];
                        coordinate[1] = cxy[1] - xy[1] - rxy[1];
                    }));

            // Draw each feature
            Arrays.stream(fcs)
                    .filter(fc -> fc.getFeatures() != null)
                    .flatMap(g -> Arrays.stream(g.getFeatures()))
                    .forEach(f -> drawGeometry(f, f.getGeometry(), g2, getMessageImage(message)));

            // Draw labels
            // Disabled for now - if enabled, we need to generate one image per language...
            //Arrays.asList(fc.getFeatures())
            //        .forEach(f -> drawLabels(f, g2, "da"));

            g2.dispose();

            if (!Files.exists(imageRepoPath.getParent())) {
                Files.createDirectories(imageRepoPath.getParent());
            }

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
    @SuppressWarnings("unused")
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
                        coord = GeoJsonUtils.computeCoordinate(f, fn.getCoordIndex());
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
            Geometry geometry = JtsConverter.toJts(g);
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
                f.getProperties().containsKey("parentFeatureIdsq");
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



    /**
     * Validates the the given image data buffer represents an image. If the image does not have the
     * proper proportions, it will be scaled.
     *
     * @param imageData the image data
     * @param imageRepoPath the path of the image
     * @return if the image file was properly created
     */
    public boolean generateMessageMapImage(byte[] imageData, Path imageRepoPath) throws IOException {

        // Check that we can read the image
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) {
            return false;
        }

        if (!Files.exists(imageRepoPath.getParent())) {
            Files.createDirectories(imageRepoPath.getParent());
        }

        if (image.getWidth() == mapImageSize && image.getHeight() == mapImageSize) {
            // Write the image file directly.
            // NB: We assume PNG
            Files.write(imageRepoPath, imageData);
            log.info("Update message map image " + imageRepoPath);

        } else {
            // Scale down
            BufferedImage destImage = new BufferedImage(mapImageSize, mapImageSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = destImage.createGraphics();
            GraphicsUtils.antialias(g2);
            float scale = Math.min((float)mapImageSize / image.getWidth(), (float)mapImageSize / image.getHeight());

            // We never scale up
            if (scale > 1.0f) {
                scale = 1.0f;
            }

            float dx = (mapImageSize - scale * image.getWidth()) / 2.0f;
            float dy = (mapImageSize - scale * image.getHeight()) / 2.0f;
            g2.drawImage(image,

                    // Destination coordinates:
                    Math.round(dx),
                    Math.round(dy),
                    Math.round(dx + scale * image.getWidth()),
                    Math.round(dy + scale  * image.getHeight()),

                    // Source coordinates:
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight(),
                    null);

            g2.dispose();

            // Save the image to the repository
            ImageIO.write(destImage, "png", imageRepoPath.toFile());
            image.flush();
            log.info("Update scaled message map image " + imageRepoPath);
        }

        return true;
    }
}
