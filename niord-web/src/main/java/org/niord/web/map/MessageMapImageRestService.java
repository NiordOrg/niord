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
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.model.vo.MainType;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Returns and caches a thumbnail image for a message.
 * <p></p>
 * Can be used e.g. for a grid layout in search results.
 */
@javax.ws.rs.Path("/message-map-image")
@Stateless
public class MessageMapImageRestService extends AbstractMapImageRestService {

    private static Image nwImage;
    private static Image nmImage;

    @Inject
    Logger log;

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;

    /**
     * Main GET method
     */
    @GET
    @javax.ws.rs.Path("/{id}.png")
    @Produces("image/png")
    public Response getMessageMapImage(@PathParam("id") String id) throws IOException, URISyntaxException {
        try {
            Message message = messageService.findById(Integer.valueOf(id));
            if (message == null) {
                throw new WebApplicationException(404);
            }

            if (message.getGeometry() != null && !message.getGeometry().getFeatures().isEmpty()) {

                FeatureCollectionVo fc = message.getGeometry().toGeoJson();

                // Construct the image file name for the message
                String imageName = String.format("map_%d.png", mapImageSize);

                // Create a hashed sub-folder for the image file
                Path imageRepoPath = messageService.getMessageFileRepoPath(message, imageName);

                // If the image file does not exist, or if the message has been updated after the image file,
                // generate a new image file
                boolean imageFileExists = Files.exists(imageRepoPath);
                if (!imageFileExists ||
                        message.getUpdated().getTime() > Files.getLastModifiedTime(imageRepoPath).toMillis()) {
                    imageFileExists = createMapImage(
                            fc,
                            imageRepoPath,
                            getMessageImage(message),
                            message.getUpdated());
                }

                // Either return the image file, or a place holder image
                if (imageFileExists) {

                    // Redirect the the repository streaming service
                    String uri = "../" + messageService.getMessageFileRepoUri(message, imageName);
                    return Response
                            .temporaryRedirect(new URI(uri))
                            .build();
                }
            }

        } catch (Exception ex) {
            log.warn("Error fetching map image for message: " + ex);
        }

        // Show a placeholder image
        return Response
                .temporaryRedirect(new URI(IMAGE_PLACEHOLDER))
                .build();
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
            String imageUrl = app.getBaseUri() + "/img/msi.png";
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
