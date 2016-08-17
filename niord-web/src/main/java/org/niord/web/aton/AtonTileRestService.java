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
package org.niord.web.aton;

import org.apache.commons.io.IOUtils;
import org.niord.core.aton.AtonSearchParams;
import org.niord.core.aton.AtonService;
import org.niord.core.repo.RepositoryService;
import org.niord.core.util.GlobalMercator;
import org.niord.core.util.GraphicsUtils;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/**
 * Feeds AtoN data as bitmaps.
 * Can be used for servicing an OpenStreetMap Layer in Openlayers.
 * The layer should be configured to have the url "/rest/aton-tiles/${z}/${x}/${y}.png"
 * <p>
 * The handling of blank tiles in particular is un-optimal. This is due to problems getting the service
 * to work with Microsoft IE and Edge:
 * <ul>
 *     <li>IE and Edge will abort redirection requests, preventing us from redirecting blank tiles to
 *         a common global bitmap, which would have been more efficient.</li>
 *     <li>This also prevents us from redirecting the streaming of tiles to the repository service.</li>
 *     <li>The tiles have to be 256x256, preventing us from streaming a 1x1 blank tile.</li>
 * </ul>
 */
@javax.ws.rs.Path("/aton-tiles")
public class AtonTileRestService {

    static final int        TILE_SIZE           = 256;
    static final int        TILE_TTL_HOURS      = 24; // A tile file is refreshed every 24 hours...
    static final String     TILE_REPO_FOLDER    = "aton_tiles";
    static final Color      ATON_COLOR          = new Color(200, 0, 0);

    @Inject
    Logger log;

    @Inject
    AtonService atonService;

    @Inject
    AtonBlankTileCache blankTileCache;

    @Inject
    RepositoryService repositoryService;

    /**
     * Streams the given tile
     */
    @GET
    @javax.ws.rs.Path("/{z}/{x}/{y}.png")
    public Response streamTile(@PathParam("z") int z, @PathParam("x") int x, @PathParam("y") int y,
                                  @Context Request request) throws IOException {

        try {
            // Next, check if the tile exists in the repository
            Path file = repositoryService.getRepoRoot()
                    .resolve(TILE_REPO_FOLDER)
                    .resolve(String.valueOf(z))
                    .resolve(String.valueOf(x))
                    .resolve(String.valueOf(y) + ".png");

            long ttlMs = 1000L * 60L * 60L * TILE_TTL_HOURS;
            Date expirationDate = new Date(System.currentTimeMillis() + ttlMs);

            // Check if the tile is a known blank tile
            if (blankTileCache.getCache().containsKey(file.toString())) {
                return streamBlankTile(expirationDate);

            } else if (Files.exists(file) &&
                    System.currentTimeMillis() < Files.getLastModifiedTime(file).toMillis() + ttlMs) {
                // The tile exists and is not expired

                // Check for an ETag match
                EntityTag etag = entityTagForFile(file);
                Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
                if (responseBuilder != null) {
                    // Etag match
                    log.trace("File unchanged. Return code 304");
                    return responseBuilder
                            .expires(expirationDate)
                            .build();
                } else {
                    log.trace("Return existing tile " + file);
                    return streamTile(file, expirationDate, etag);
                }
            }


            // Search all messages in the bounds of the tile
            long t0 = System.currentTimeMillis();
            GlobalMercator mercator = new GlobalMercator();
            double[] bounds = mercator.TileLatLonBounds(x, y, z);

            // Convert to mapExtents search parameters
            AtonSearchParams param = new AtonSearchParams()
                .extent(-bounds[2], bounds[1], -bounds[0], bounds[3]);

            // Compute the atons of the tile extent
            java.util.List<double[]> atonLonLast = atonService.searchPositions(param);

            // If the search result is empty, return blank and cache the result
            if (atonLonLast.isEmpty()) {
                blankTileCache.getCache().put(file.toString(), file.toString());
                return streamBlankTile(expirationDate);
            }

            // Generate an image
            BufferedImage image = generateAtonTile(z, bounds, mercator, atonLonLast);

            // Write the image to the repository
            checkCreateParentDirs(file);
            ImageIO.write(image, "png", file.toFile());
            log.debug("Generated " + file + " in " + (System.currentTimeMillis() - t0) + " ms");

            return streamTile(file, expirationDate, null);

        } catch (Exception e) {
            log.error(String.format("Error generating tile z=%d, x=%d, y=%d. Error=%s", z, x, y, e));
            return Response
                    .status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format("Error generating tile z=%d, x=%d, y=%d. Error=%s", z, x, y, e))
                    .build();

        }
    }


    /**
     * Generates an AtoN tile
     * @param z the zoom level
     * @param bounds the tile bounds
     * @param mercator the mercator calculator
     * @param atonLonLats the aton positions
     * @return the resulting image
     */
    private BufferedImage generateAtonTile(int z, double[] bounds, GlobalMercator mercator, java.util.List<double[]> atonLonLats) {

        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        GraphicsUtils.antialias(g2);

        int xy0[] =  mercator.LatLonToPixels(-bounds[0], bounds[1], z);

        atonLonLats.stream().forEach(lonLat -> {

            int xy[] = mercator.LatLonToPixels(lonLat[1], lonLat[0], z);
            double px = xy[0] - xy0[0];
            double py = -(xy[1] - xy0[1]);
            double radius = (z < 6) ? 0.5 : 1.0;

            Shape theCircle = new Ellipse2D.Double(px - radius, py - radius, 2.0 * radius, 2.0 * radius);
            g2.setColor(ATON_COLOR);
            g2.fill(theCircle);
        });

        g2.dispose();
        return image;
    }

    /**
     * Ensures that parent directories are created
     * @param file the file whose parent directories will be created
     */
    private void checkCreateParentDirs(Path file) throws IOException {
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
    }

    /**
     * Streams a tile
     * @param file the tile to stream
     * @param expirationDate the expiration date of the returned tile
     * @param etag the E-Tag. May be null.
     * @return the response
     */
    private Response streamTile(Path file, Date expirationDate, EntityTag etag) throws IOException {
        if (etag == null) {
            etag = entityTagForFile(file);
        }
        return Response
                .ok(file.toFile(), "image/png")
                .expires(expirationDate)
                .tag(etag)
                .build();
    }

    /**
     * Streams a blank tile
     * @param expirationDate the expiration date of the returned tile
     * @return the response
     */
    private Response streamBlankTile(Date expirationDate) throws IOException {
        Path file = repositoryService.getRepoRoot()
                .resolve(TILE_REPO_FOLDER)
                .resolve("blank_256.png");

        // Make sure the blank file is present in the repository
        if (Files.notExists(file)) {
            checkCreateParentDirs(file);
            IOUtils.copy(
                    getClass().getResourceAsStream("/blank_256.png"),
                    new FileOutputStream(file.toFile()));
        }

        log.trace("Streaming blank file: " + file);
        return Response
                .ok(file.toFile(), "image/png")
                .expires(expirationDate)
                .build();
    }

    /**
     * Computes an E-Tag for the file
     * @param file the file
     * @return the E-Tag for the file
     */
    private EntityTag entityTagForFile(Path file) throws IOException {
        return new EntityTag("" + Files.getLastModifiedTime(file).toMillis() + "_" + Files.size(file), true);
    }

}

