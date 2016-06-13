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

import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Returns and caches a thumbnail image for a message.
 * <p>
 * Can be used e.g. for a grid layout in search results.
 */
@javax.ws.rs.Path("/message-map-image")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class MessageMapImageRestService {

    static final String IMAGE_PLACEHOLDER = "../img/map_image_placeholder.png";
    static final String UPLOADED_IMAGE_PREFIX = "data:image/png;base64,";

    @Inject
    Logger log;

    @Inject
    MessageService messageService;

    @Inject
    MessageMapImageGenerator messageMapImageGenerator;

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

            // Check if a custom map image is defined
            String customThumbName = String.format("custom_thumb_%d.png", messageMapImageGenerator.getMapImageSize());
            Path imageRepoPath = messageService.getMessageFileRepoPath(message, customThumbName);
            if (Files.exists(imageRepoPath)) {
                return redirect(message, customThumbName);
            }


            // Check for a standard auto-generated message map image file
            if (message.getGeometry() != null && !message.getGeometry().getFeatures().isEmpty()) {

                // Construct the image file for the message
                String imageName = String.format("map_%d.png", messageMapImageGenerator.getMapImageSize());
                imageRepoPath = messageService.getMessageFileRepoPath(message, imageName);

                // If the image file does not exist, or if the message has been updated after the image file,
                // generate a new image file
                boolean imageFileExists = Files.exists(imageRepoPath);
                if (!imageFileExists ||
                        message.getUpdated().getTime() > Files.getLastModifiedTime(imageRepoPath).toMillis()) {
                    imageFileExists = messageMapImageGenerator.generateMessageMapImage(
                            message,
                            imageRepoPath);
                }

                // Either return the image file, or a place holder image
                if (imageFileExists) {
                    return redirect(message, imageName);
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


    /** Returns a redirect to the actual repository image file **/
    private Response redirect(Message message, String imageName) throws IOException, URISyntaxException {
        // Redirect the the repository streaming service
        String uri = "../" + messageService.getMessageFileRepoUri(message, imageName);
        return Response
                .temporaryRedirect(new URI(uri))
                .build();
    }


    /** Updates the map image with a custom image */
    @PUT
    @javax.ws.rs.Path("/{id}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({"editor"})
    public void updateMessageMapImage(@PathParam("id") String id, String image) throws Exception {

        Message message = messageService.findById(Integer.valueOf(id));
        if (message == null) {
            throw new WebApplicationException(404);
        }

        if (!image.toLowerCase().startsWith(UPLOADED_IMAGE_PREFIX)) {
            throw new WebApplicationException(400);
        }

        // Decode the base-64 encoded image data
        image = image.substring(UPLOADED_IMAGE_PREFIX.length());
        byte[] data = Base64.getDecoder().decode(image);

        // Construct the image file name for the message
        String imageName = String.format("custom_thumb_%d.png", messageMapImageGenerator.getMapImageSize());

        // Create a hashed sub-folder for the image file
        Path imageRepoPath = messageService.getMessageFileRepoPath(message, imageName);

        messageMapImageGenerator.generateMessageMapImage(data, imageRepoPath);
    }

}
