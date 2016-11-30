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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.repo.RepositoryService;
import org.niord.model.geojson.FeatureCollectionVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Returns the map thumbnail image associated with a message.
 * Can be used e.g. for a grid layout in search results.
 * <p>
 * Either a standard map image is generated and returned, or the user may
 * upload a custom map image thumbnail.
 * <p>
 * The URL to fetch an image can either be specified via a message ID or a temporary repository path.
 * The latter is used when messages is being edited.
 */
@javax.ws.rs.Path("/message-map-image")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class MessageMapImageRestService {

    static final int CACHE_TIMEOUT_MINUTES = 10;
    static final String IMAGE_PLACEHOLDER = "../img/map_image_placeholder.png";
    static final String UPLOADED_IMAGE_PREFIX = "data:image/png;base64,";

    @Context
    ServletContext servletContext;

    @Inject
    Logger log;

    @Inject
    RepositoryService repositoryService;

    @Inject
    MessageService messageService;

    @Inject
    MessageMapImageGenerator messageMapImageGenerator;

    /**
     * Returns the map thumbnail image associated with the message with the given UID
     * @param uid the UID of the message
     * @return the map thumbnail image
     */
    @GET
    @javax.ws.rs.Path("/{uid}.png")
    @Produces("image/png")
    public Response getMessageMapImage(@PathParam("uid") String uid) throws IOException, URISyntaxException {
        try {
            Message message = messageService.findByUid(uid);
            if (message == null) {
                return Response
                        .status(HttpServletResponse.SC_NOT_FOUND)
                        .entity("Message map image not found for message: " + uid)
                        .build();
            }

            // Check if a custom map image is defined
            if (StringUtils.isNotBlank(message.getThumbnailPath())) {
                Path imageRepoPath = repositoryService.getRepoRoot().resolve(message.getThumbnailPath());
                if (Files.exists(imageRepoPath)) {
                    return redirect(imageRepoPath);
                }
            }


            // Check for a standard auto-generated message map image file
            FeatureCollectionVo[] fcs = message.toGeoJson();
            if (fcs.length > 0) {

                // Construct the image file for the message
                String imageName = String.format("map_%d.png", messageMapImageGenerator.getMapImageSize());
                Path imageRepoPath = repositoryService.getRepoRoot().resolve(message.getRepoPath()).resolve(imageName);

                // If the image file does not exist, or if the message has been updated after the image file,
                // generate a new image file
                boolean imageFileExists = Files.exists(imageRepoPath);
                if (!imageFileExists ||
                        message.getUpdated().getTime() > Files.getLastModifiedTime(imageRepoPath).toMillis()) {
                    imageFileExists = messageMapImageGenerator.generateMessageMapImage(
                            message,
                            fcs,
                            imageRepoPath);
                }

                // Either return the image file, or a place holder image
                if (imageFileExists) {
                    return redirect(imageRepoPath);
                }
            }

        } catch (Exception ex) {
            log.warn("Error fetching map image for message: " + ex);
        }

        // Show a placeholder image
        return Response
                .temporaryRedirect(new URI(IMAGE_PLACEHOLDER))
                .expires(getExpiryTime())
                .build();
    }


    /**
     * Returns a redirect to the actual repository image file
     **/
    private Response redirect(Path imagePath) throws IOException, URISyntaxException {
        // Redirect the the repository streaming service
        String uri = repositoryService.getRepoUri(imagePath);
        return Response
                .temporaryRedirect(new URI("../" + uri))
                .expires(getExpiryTime())
                .build();
    }


    /** Returns the cache timeout **/
    private Date getExpiryTime() {
        return new Date(System.currentTimeMillis() + 1000L * 60L * CACHE_TIMEOUT_MINUTES);
    }


    /**
     * Updates the map image with a custom image
     */
    @PUT
    @javax.ws.rs.Path("/{folder:.+}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("text/plain")
    @RolesAllowed({"editor"})
    public String updateMessageMapImage(@PathParam("folder") String path, String image) throws Exception {

        // Validate that the path is a temporary repository folder path
        Path folder = repositoryService.validateTempRepoPath(path);

        if (!image.toLowerCase().startsWith(UPLOADED_IMAGE_PREFIX)) {
            throw new WebApplicationException(400);
        }

        // Decode the base-64 encoded image data
        image = image.substring(UPLOADED_IMAGE_PREFIX.length());
        byte[] data = Base64.getDecoder().decode(image);

        // Construct the file path for the message
        String imageName = String.format("custom_thumb_%d.png", messageMapImageGenerator.getMapImageSize());
        Path imageRepoPath = folder.resolve(imageName);

        messageMapImageGenerator.generateMessageMapImage(data, imageRepoPath);

        // Return the new thumbnail path
        return path + "/" + imageName;
    }


    /**
     * Called to upload a custom message map image via a multipart form-data request
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @javax.ws.rs.Path("/{folder:.+}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("editor")
    public String uploadMessageMapImage(@PathParam("folder") String path, @Context HttpServletRequest request) throws Exception {

        // Validate that the path is a temporary repository folder path
        Path folder = repositoryService.validateTempRepoPath(path);

        FileItemFactory factory = RepositoryService.newDiskFileItemFactory(servletContext);
        ServletFileUpload upload = new ServletFileUpload(factory);

        List<FileItem> items = upload.parseRequest(request);

        // Get hold of the first uploaded image
        FileItem imageItem = items.stream()
                .filter(item -> !item.isFormField())
                .findFirst()
                .orElse(null);

        if (imageItem == null) {
            throw new WebApplicationException(400);
        }
        // Construct the file path for the message
        String imageName = String.format("custom_thumb_%d.png", messageMapImageGenerator.getMapImageSize());
        Path imageRepoPath = folder.resolve(imageName);

        try {
            byte[] data = IOUtils.toByteArray(imageItem.getInputStream());
            if (messageMapImageGenerator.generateMessageMapImage(data, imageRepoPath)) {
                log.info("Generated image thumbnail from uploaded image");
            } else {
                log.error("Failed generating image thumbnail from uploaded image");
            }
        } catch (IOException e) {
            log.error("Error generating image thumbnail from uploaded image:\nError: ", e);
        }

        return path + "/" + imageName;
    }
}
