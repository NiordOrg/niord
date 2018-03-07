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
package org.niord.s124;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * A public REST API for accessing messages as S-124 GML.
 * <p>
 * You can test that the produced GML is valid according to the schema, using something along the lines of:
 * <pre>
 *     xmllint --noout --schema http://localhost:8080/rest/S-124/S124.xsd http://localhost:8080/rest/S-124/NW-061-17.gml
 * </pre>
 */
@Api(value = "/S-124",
        description = "Public API for accessing messages as S-124 GML. " +
                "NB: Only use this service for test purposes, not for production.",
        tags = {"S-124" })
@Path("/S-124")
public class S124RestService {

    @Inject
    Logger log;

    @Inject
    S124Service s124Service;


    /**
     * Returns the S-124 GML representation for the given message
     */
    @ApiOperation(
            value = "Returns S-124 GML representation for the message." +
                    "NB: Only use this service for test purposes, not for production.",
            response = String.class,
            tags = {"S-124"}
    )
    @GET
    @Path("/{messageId}.gml")
    @Produces({"application/gml+xml;charset=UTF-8"})
    public Response s124MessageDetails(
            @ApiParam(value = "The message UID or short ID", example = "NW-061-17")
            @PathParam("messageId") String messageId,

            @ApiParam(value = "Two-letter ISO 639-1 language code", example = "en")
            @QueryParam("lang") @DefaultValue("en") String language

    ) throws Exception {

        long t0 = System.currentTimeMillis();


        try {
            String result = s124Service.generateGML(messageId, language);

            // Pretty print the result
            result = prettyPrint(result);

            log.info("Generated GML for message " + messageId + " in " + (System.currentTimeMillis() - t0) + " ms");
            return Response.ok(result)
                    .type("application/gml+xml;charset=UTF-8")
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Message does not exist: " + messageId);
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity("No message found with ID: " + messageId)
                    .build();
        } catch (Exception e) {
            log.error("Error generating S-124 GML for message " + messageId + ": " + e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_HTML_TYPE)
                    .entity("Error generating GML: " + e.getMessage())
                    .build();
        }
    }


    /** Arghh, for some insane reason, this function does not work properly :-( **/
    public static String prettyPrint(String input) {
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(xmlInput, xmlOutput);
            return stringWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }


    /**
     * {@inheritDoc}
     */
    @GET
    @Path("/{file}.xsd")
    @Produces({"text/xml;charset=UTF-8"})
    public Response xsdFile(
            @PathParam("file") String file

    ) throws Exception {

        String xsdFile = file + ".xsd";

        try (InputStream in = getClass().getResourceAsStream("/xsd/" + xsdFile)) {

            String xsd = IOUtils.toString(in);

            log.info("Returning XSD " + xsdFile);
            return Response.ok(xsd)
                    .type("application/gml+xml;charset=UTF-8")
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            log.error("XSD does not exist: " + xsdFile);
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity("XSD does not exist: " + xsdFile)
                    .build();
        }
    }

}
