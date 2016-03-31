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

import org.niord.core.NiordApp;
import org.niord.core.repo.RepositoryService;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.niord.web.TestRestService;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/**
 * Returns and caches a thumbnail image for a message.
 * <p></p>
 * Can be used e.g. for a grid layout in search results.
 */
@WebServlet(value = "/message-map-image/*", asyncSupported = true)
public class MessageMapImageServlet extends AbstractMapImageServlet  {

    private static Image msiImage;

    @Inject
    Logger log;

    @Inject
    TestRestService testService;

    @Inject
    RepositoryService repositoryService;

    @Inject
    NiordApp app;

    /**
     * Main GET method
     * @param request servlet request
     * @param response servlet response
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Strip png path of the request path info to get the id of the message
            String uid = request.getPathInfo().substring(1).split("\\.")[0];

            // TODO: Hack for now. Look up the feature collection
            FeatureCollectionVo fc = testService.getAllFeatureCollections().stream()
                    .filter(f -> f.getId().equals(uid))
                    .findFirst()
                    .orElse(null);

            if (fc == null) {
                throw new IllegalArgumentException("FeatureCollection " + uid + " does not exist");
            }

            if (fc.getFeatures().length > 0) {
                // Construct the image file name for the message
                String imageName = String.format("map_%s_%d.png", uid, mapImageSize);

                Path imageRepoPath = repositoryService.getTempRepoRoot().resolve(imageName);

                // TODO: Hard-code last updated one minute ago for now
                Date messageUpdated = new Date(System.currentTimeMillis() - 60L * 1000L);

                // If the image file does not exist, or if the message has been updated after the image file,
                // generate a new image file
                boolean imageFileExists = Files.exists(imageRepoPath);
                if (!imageFileExists ||
                        messageUpdated.getTime() > Files.getLastModifiedTime(imageRepoPath).toMillis()) {
                    imageFileExists = createMapImage(
                            fc,
                            imageRepoPath,
                            getMessageImage(),
                            new Date()); // TODO: use messageUpdated
                }

                // Either return the image file, or a place holder image
                if (imageFileExists) {
                    // Redirect the the repository streaming service
                    String uri = repositoryService.getRepoUri(imageRepoPath);
                    response.sendRedirect(uri);
                    return;
                }
            }

        } catch (Exception ex) {
            log.warn("Error fetching map image for message: " + ex);
        }

        // Show a placeholder image
        response.sendRedirect(IMAGE_PLACEHOLDER);
    }

    /**
     * Depending on the type of message, return an MSI or an NM image
     * @return the corresponding image
     */
    public Image getMessageImage() {
        return getMsiImage();
    }

    /**
     * Returns the MSI symbol image
     * @return the MSI symbol image
     */
    private synchronized Image getMsiImage() {
        if (msiImage == null) {
            String imageUrl = app.getBaseUri() + "/img/msi.png";
            try {
                msiImage = ImageIO.read(new URL(imageUrl));
            } catch (IOException e) {
                log.error("This should never happen - could not load image from " + imageUrl);
            }
        }
        return msiImage;
    }

}
