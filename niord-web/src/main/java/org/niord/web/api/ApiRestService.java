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
package org.niord.web.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.NiordApp;
import org.niord.core.area.Area;
import org.niord.core.message.Message;
import org.niord.core.publication.Publication;
import org.niord.model.DataFilter;
import org.niord.model.message.AreaVo;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageVo;
import org.niord.model.publication.PublicationVo;
import org.niord.model.search.PagedSearchResultVo;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A public REST API for accessing message and publications Niord data.
 * <p>
 * Also, defines the Swagger API using annotations.
 * <p>
 * NB: Swagger codegen-generated model classes cannot handle UNIX Epoch timestamps, which is the date format
 * used in JSON throughout Niord. Instead, these classes expects dates to be encoded as ISO-8601 strings
 * (e.g. "2016-10-12T13:21:22.000+0000").<br>
 * To facilitate both formats, use the "dateFormat" parameter.
 */
@Path("/public/v1")
@RequestScoped
@Transactional
@SuppressWarnings("unused")
public class ApiRestService extends AbstractApiService {

    /**
     * The format to use for dates in generated JSON
     **/
    public enum JsonDateFormat {
        UNIX_EPOCH, ISO_8601
    }

    @Inject
    NiordApp app;


    /***************************
     * Message end-points
     ***************************/


    /**
     * {@inheritDoc}
     */
    @GET
    @Path("/messages")
    @Operation(summary = "Returns the published NW and NM messages")
    @APIResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = MessageVo.class, type= SchemaType.ARRAY)
            )
    )
    @Produces({"application/json;charset=UTF-8"})
    @GZIP
    public Response searchMessages(
            @Parameter(description = "Two-letter ISO 639-1 language code", example = "en")
            @QueryParam("lang") String language,

            @Parameter(description = "The IDs of the domains to select messages from", example = "niord-client-nw")
            @QueryParam("domain") Set<String> domainIds,

            @Parameter(description = "Specific message series to select messages from", example = "dma-nw")
            @QueryParam("messageSeries") Set<String> messageSeries,

            @Parameter(description = "The IDs of the publications to select message from")
            @QueryParam("publication") Set<String> publicationIds,

            @Parameter(description = "The IDs of the areas to select messages from", example = "urn:mrn:iho:country:dk")
            @QueryParam("areaId") Set<String> areaIds,

            @Parameter(description = "Either NW (navigational warnings) or NM (notices to mariners)", example = "NW")
            @QueryParam("mainType") Set<MainType> mainTypes,

            @Parameter(description = "Well-Known Text for geographical extent", example = "POLYGON((7 54, 7 57, 13 56, 13 57, 7 54))")
            @QueryParam("wkt") String wkt,

            @Parameter(description = "Whether to rewrite all embedded links and paths to be absolute URL's", example = "true")
            @QueryParam("externalize") @DefaultValue("true") boolean externalize,

            @Parameter(description = "The date format to use for JSON date-time encoding. Either 'UNIX_EPOCH' or 'ISO_8601'", example = "UNIX_EPOCH")
            @QueryParam("dateFormat") @DefaultValue("UNIX_EPOCH") JsonDateFormat dateFormat

    ) throws Exception {

        // Perform the search
        PagedSearchResultVo<Message> searchResult =
                super.searchMessages(language, domainIds, messageSeries, publicationIds, areaIds, mainTypes, wkt);


        // Convert messages to value objects and externalize message links, if requested
        List<MessageVo> messages = searchResult
                .map(m -> toMessageVo(m, language, externalize))
                .getData();

        // Depending on the dateFormat param, either use UNIX epoch or ISO-8601
        StreamingOutput stream = os -> objectMapperForDateFormat(dateFormat).writeValue(os, messages);

        return Response
                .ok(stream, MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                .build();

    }


    /**
     * {@inheritDoc}
     */
    @GET
    @Path("/message/{messageId}")
    @Operation(description = "Returns the public (published, cancelled or expired) NW or NM message details")
    @APIResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = MessageVo.class)
            )
    )
    @Produces({"application/json;charset=UTF-8"})
    @GZIP
    public Response messageDetails(
            @Parameter(description = "The message UID or short ID", example = "NM-1275-16")
            @PathParam("messageId") String messageId,

            @Parameter(description = "Two-letter ISO 639-1 language code", example = "en")
            @QueryParam("lang") String language,

            @Parameter(description = "Whether to rewrite all embedded links and paths to be absolute URL's", example = "true")
            @QueryParam("externalize") @DefaultValue("true") boolean externalize,

            @Parameter(description = "The date format to use for JSON date-time encoding. Either 'UNIX_EPOCH' or 'ISO_8601'", example = "UNIX_EPOCH")
            @QueryParam("dateFormat") @DefaultValue("UNIX_EPOCH") JsonDateFormat dateFormat

    ) throws Exception {

        // Perform the search
        Message message = super.getMessage(messageId);

        if (message == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity("No message found with ID: " + messageId)
                    .build();
        } else {

            // Convert message to value objects and externalize message links, if requested
            MessageVo result = toMessageVo(message, language, externalize);

            // Depending on the dateFormat param, either use UNIX epoch or ISO-8601
            StreamingOutput stream = os -> objectMapperForDateFormat(dateFormat).writeValue(os, result);

            return Response
                    .ok(stream, MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                    .build();
        }
    }



    /**
     * Returns the XSD for the Message class.
     * Two XSDs are produced, "schema1.xsd" and "schema2.xsd". The latter is the main schema for
     * Message and will import the former.
     * @return the XSD for the Message class
     */
    @GET
    @Path("/xsd/{schemaFile}")
    @Operation(description = "Returns XSD model of the Message class")
    @APIResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = String.class)
            )
    )
    @Tag(ref = "message")
    @Produces("application/xml;charset=UTF-8")
    @GZIP
    @NoCache
    public String getMessageXsd(
            @Parameter(description = "The schema file, either schema1.xsd or schema2.xsd", example="schema2.xsd")
            @PathParam("schemaFile")
                    String schemaFile) throws Exception {

        if (!schemaFile.equals("schema1.xsd") && !schemaFile.equals("schema2.xsd")) {
            throw new Exception("Only 'schema1.xsd' and 'schema2.xsd' allowed");
        }

        JAXBContext jaxbContext = JAXBContext.newInstance(MessageVo.class);

        Map<String, StringWriter> result = new HashMap<>();
        SchemaOutputResolver sor = new SchemaOutputResolver() {
            @Override
            public Result createOutput(String namespaceURI, String suggestedFileName) throws IOException {
                StringWriter out = new StringWriter();
                result.put(suggestedFileName, out);
                StreamResult result = new StreamResult(out);
                result.setSystemId(app.getBaseUri() + "/rest/public/v1/xsd/" + suggestedFileName);
                return result;
            }

        };

        // Generate the schemas
        jaxbContext.generateSchema(sor);

        return result.get(schemaFile).toString();
    }


    /**
     * Convert the message to a value object representation.
     * If requested, rewrite all links to make them external URLs.
     * @param msg the message to convert to a value object
     * @param externalize whether to rewrite all links to make them external URLs
     * @return the message value object representation
     **/
    private MessageVo toMessageVo(Message msg, String language, boolean externalize) {

        // Sanity check
        if (msg == null) {
            return null;
        }

        // Convert the message to a value object
        DataFilter filter = Message.MESSAGE_DETAILS_FILTER.lang(language);
        MessageVo message = msg.toVo(MessageVo.class, filter);

        // If "externalize" is set, rewrite all links to make them external
        if (externalize) {
            String baseUri = app.getBaseUri();

            if (message.getParts() != null) {
                String from = concat("/rest/repo/file", msg.getRepoPath());
                String to = concat(baseUri, from);
                message.getParts().forEach(mp -> mp.rewriteRepoPath(from, to));
            }
            if (message.getAttachments() != null) {
                String to = concat(baseUri, "rest/repo/file",  msg.getRepoPath());
                message.getAttachments().forEach(att -> att.rewriteRepoPath( msg.getRepoPath(), to));
            }
        }

        return message;
    }


    /***************************
     * Publication end-points
     ***************************/


    /**
     * Searches for publications
     */
    @GET
    @Path("/publications")
    @Operation(description = "Returns the publications")
    @APIResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PublicationVo.class, type= SchemaType.ARRAY)
            )
    )
    @Tag(ref = "publications")
    @Produces({"application/json;charset=UTF-8"})
    @GZIP
    public Response searchPublications(

            @Parameter(description = "Two-letter ISO 639-1 language code", example = "en")
            @QueryParam("lang") String language,

            @Parameter(description = "Timestamp (Unix epoch) for the start date of the publications")
            @QueryParam("from") Long from,

            @Parameter(description = "Timestamp (Unix epoch) for the end date of the publications")
            @QueryParam("to") Long to,

            @Parameter(description = "Whether to rewrite all embedded links and paths to be absolute URL's", example = "true")
            @QueryParam("externalize") @DefaultValue("true") boolean externalize,

            @Parameter(description = "The date format to use for JSON date-time encoding. Either 'UNIX_EPOCH' or 'ISO_8601'", example = "UNIX_EPOCH")
            @QueryParam("dateFormat") @DefaultValue("UNIX_EPOCH") JsonDateFormat dateFormat
    ) {

        // If from and to-dates are unspecified, return the publications currently active
        if (from == null && to == null) {
            from = to = System.currentTimeMillis();
        }

        List<PublicationVo> publications = super.searchPublications(language, from, to).stream()
                .map(p -> toPublicationVo(p, language, externalize))
                .collect(Collectors.toList());

        // Depending on the dateFormat param, either use UNIX epoch or ISO-8601
        StreamingOutput stream = os -> objectMapperForDateFormat(dateFormat).writeValue(os, publications);

        return Response
                .ok(stream, MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                .build();
    }


    /**
     * Returns the details for the given publications
     */
    @GET
    @Path("/publication/{publicationId}")
    @Operation(description = "Returns the publication with the given ID")
    @APIResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PublicationVo.class)
            )
    )
    @Tag(ref = "publications")
    @Produces({"application/json;charset=UTF-8"})
    @GZIP
    public Response publicationDetails(

            @Parameter(description = "The publication ID", example = "5eab7f50-d890-42d9-8f0a-d30e078d3d5a")
            @PathParam("publicationId") String publicationId,

            @Parameter(description = "Two-letter ISO 639-1 language code", example = "en")
            @QueryParam("lang") String language,

            @Parameter(description = "Whether to rewrite all embedded links and paths to be absolute URL's", example = "true")
            @QueryParam("externalize") @DefaultValue("true") boolean externalize,

            @Parameter(description = "The date format to use for JSON date-time encoding. Either 'UNIX_EPOCH' or 'ISO_8601'", example = "UNIX_EPOCH")
            @QueryParam("dateFormat") @DefaultValue("UNIX_EPOCH") JsonDateFormat dateFormat
    ) {

        Publication publication = super.getPublication(publicationId);

        if (publication == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity("No publication found with ID: " + publicationId)
                    .build();
        } else {

            // Convert publication to value objects and externalize publication links, if requested
            PublicationVo result = toPublicationVo(publication, language, externalize);

            // Depending on the dateFormat param, either use UNIX epoch or ISO-8601
            StreamingOutput stream = os -> objectMapperForDateFormat(dateFormat).writeValue(os, result);

            return Response
                    .ok(stream, MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                    .build();
        }
    }


    /**
     * Convert the publication to a value object representation.
     * If requested, rewrite all links to make them external URLs.
     * @param pub the publication to convert to a value object
     * @param externalize whether to rewrite all links to make them external URLs
     * @return the publication value object representation
     **/
    private PublicationVo toPublicationVo(Publication pub, String language, boolean externalize) {

        // Sanity check
        if (pub == null) {
            return null;
        }

        // Convert the publication to a value object
        DataFilter filter = DataFilter.get().lang(language);
        PublicationVo publication = pub.toVo(PublicationVo.class, filter);

        // If "externalize" is set, rewrite all links to make them external
        if (externalize) {
            String baseUri = app.getBaseUri();

            if (publication.getDescs() != null) {
                publication.getDescs().stream()
                        .filter(d -> StringUtils.isNotBlank(d.getLink()))
                        .filter(d -> !d.getLink().matches("^(?i)(https?)://.*$"))
                        .forEach(d -> d.setLink(concat(baseUri, d.getLink())));
            }
        }

        return publication;
    }


    /***************************
     * Area end-points
     ***************************/


    /**
     * Returns the details for the given area
     */
    @GET
    @Path("/area/{areaId}")
    @Operation(description = "Returns the area with the given ID")
    @APIResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = AreaVo.class)
            )
    )
    @Tag(ref = "areas")
    @Produces({"application/json;charset=UTF-8"})
    @GZIP
    public Response areaDetails(

            @Parameter(description = "The area ID", example = "urn:mrn:iho:country:dk")
            @PathParam("areaId") String areaId,

            @Parameter(description = "Two-letter ISO 639-1 language code", example = "en")
            @QueryParam("lang") String language
    ) {

        Area area = super.getArea(areaId);

        if (area == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity("No area found with ID: " + areaId)
                    .build();
        } else {

            // Convert area to value objects
            AreaVo result = area.toVo(AreaVo.class, DataFilter.get().fields(DataFilter.PARENT).lang(language));

            return Response
                    .ok(result, MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                    .build();
        }
    }


    /**
     * Returns the details for the given area
     */
    @GET
    @Path("/area/{areaId}/sub-areas")
    @Operation(description = "Returns the sub-areas of the area with the given ID")
    @APIResponse(
            responseCode = "200",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = AreaVo.class, type = SchemaType.ARRAY)
            )
    )
    @Tag(ref = "areas")
    @Produces({"application/json;charset=UTF-8"})
    @GZIP
    public Response subAreas(

            @Parameter(description = "The area ID", example = "urn:mrn:iho:country:dk")
            @PathParam("areaId") String areaId,

            @Parameter(description = "Two-letter ISO 639-1 language code", example = "en")
            @QueryParam("lang") String language
    ) {

        Area area = super.getArea(areaId);

        if (area == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity("No area found with ID: " + areaId)
                    .build();
        } else {

            // NB: Area.getChildren() will return sub-areas in sibling sort order
            List<AreaVo> result = area.getChildren().stream()
                    .filter(Area::isActive)
                    .map(a -> a.toVo(AreaVo.class, DataFilter.get().lang(language)))
                    .collect(Collectors.toList());

            return Response
                    .ok(result, MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                    .build();
        }
    }


    /***************************
     * Utility functions
     ***************************/


    /** Returns an ObjectMapper for the given date format **/
    private ObjectMapper objectMapperForDateFormat(JsonDateFormat dateFormat) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                dateFormat == JsonDateFormat.UNIX_EPOCH);
        return mapper;
    }


    /** Concatenates the URI components **/
    private String concat(String... paths) {
        StringBuilder result = new StringBuilder();
        if (paths != null) {
            Arrays.stream(paths)
                    .filter(StringUtils::isNotBlank)
                    .forEach(p -> {
                        if (result.length() > 0 && !result.toString().endsWith("/") && !p.startsWith("/")) {
                            result.append("/");
                        } else if (result.toString().endsWith("/") && p.startsWith("/")) {
                            p = p.substring(1);
                        }
                        result.append(p);
                    });
        }
        return result.toString();
    }

}
