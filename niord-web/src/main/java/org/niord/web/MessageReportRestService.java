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

package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.domain.DomainService;
import org.niord.core.fm.FmReport;
import org.niord.core.fm.FmService;
import org.niord.core.fm.FmService.ProcessFormat;
import org.niord.core.fm.vo.FmReportVo;
import org.niord.core.message.MessagePrintParams;
import org.niord.core.message.MessageSearchParams;
import org.niord.model.message.MessageVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST interface for generating PDF message reports.
 */
@Path("/message-reports")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
@SuppressWarnings("unused")
public class MessageReportRestService {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;

    @Inject
    MessageRestService messageRestService;

    @Inject
    MessageSearchRestService messageSearchRestService;

    @Inject
    FmService fmService;

    /***************************************/
    /** PDF Reports                       **/
    /***************************************/

    /**
     * Returns the reports available for printing message lists
     * @return the reports available for printing message lists
     */
    @GET
    @Path("/reports")
    @GZIP
    @NoCache
    public List<FmReportVo> getReports() {
        return fmService.getReports().stream()
                .map(FmReport::toVo)
                .collect(Collectors.toList());
    }


    /**
     * Generates a PDF for the message with the given message id, which may be either a UID,
     * or a short ID of a message.
     *
     * If the debug flag is set to true, the HTML that is used for the PDF is returned directly.
     *
     * @param messageId the message ID
     * @param language the language of the returned data
     * @return the message as a PDF
     */
    @GET
    @Path("/message/{messageId}.pdf")
    @GZIP
    @NoCache
    public Response generatePdfForMessage(
            @PathParam("messageId") String messageId,
            @QueryParam("lang") String language,
            @Context HttpServletRequest request) throws Exception {

        MessageVo message = messageRestService.getMessage(messageId, language);

        MessagePrintParams printParams = MessagePrintParams.instantiate(request);

        try {
            ProcessFormat format = printParams.getDebug() ? ProcessFormat.TEXT : ProcessFormat.PDF;

            StreamingOutput stream = os -> {
                try {
                    fmService.newTemplateBuilder()
                            .templatePath("/templates/messages/message-details-pdf.ftl")
                            .data("messages", Collections.singleton(message))
                            .data("pageSize", printParams.getPageSize())
                            .data("pageOrientation", printParams.getPageOrientation())
                            .data("mapThumbnails", printParams.getMapThumbnails())
                            .data("draft", false)
                            .dictionaryNames("web", "message", "pdf")
                            .language(language)
                            .process(format, os);
                } catch (Exception e) {
                    throw new WebApplicationException("Error generating PDF for message " + messageId, e);
                }
            };

            Response.ResponseBuilder response = Response.ok(stream);
            return printParams.getDebug()
                    ? response.type("text/html;charset=UTF-8").build()
                    : response.type("application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"message-" + messageId + ".pdf\"")
                    .build();

        } catch (Exception e) {
            log.error("Error generating PDF for message " + messageId, e);
            throw e;
        }
    }


    /**
     * Generates a PDF for the message search result.
     *
     * If the debug flag is set to true, the HTML that is used for the PDF is returned directly.
     */
    @GET
    @Path("/report.pdf")
    @GZIP
    @NoCache
    public Response generatePdfForSearch(@Context HttpServletRequest request) throws Exception {

        // Perform a search for at most 1000 messages
        MessageSearchParams params = MessageSearchParams.instantiate(domainService.currentDomain(), request);
        params.maxSize(1000).page(0);

        MessagePrintParams printParams = MessagePrintParams.instantiate(request);

        // We prefer to get all language variants and then sort the result
        String language = params.getLanguage();
        params.language(null);
        PagedSearchResultVo<MessageVo> result = messageSearchRestService.search(params);
        result.getData().forEach(m -> m.sort(language));

        try {
            FmReport report = fmService.getReport(printParams.getReport());

            ProcessFormat format = printParams.getDebug() ? ProcessFormat.TEXT : ProcessFormat.PDF;

            StreamingOutput stream = os -> {
                try {
                    fmService.newTemplateBuilder()
                            .templatePath(report.getTemplatePath())
                            .data("messages", result.getData())
                            .data("areaHeadings", params.sortByArea())
                            .data("searchCriteria", result.getDescription())
                            .data("pageSize", printParams.getPageSize())
                            .data("pageOrientation", printParams.getPageOrientation())
                            .data("mapThumbnails", printParams.getMapThumbnails())
                            .data(report.getProperties()) // Let report override settings
                            .dictionaryNames("web", "message", "pdf")
                            .language(language)
                            .process(format, os);
                } catch (Exception e) {
                    throw new WebApplicationException("Error generating PDF for messages", e);
                }
            };

            Response.ResponseBuilder response = Response.ok(stream);
            return printParams.getDebug()
                    ? response.type("text/html;charset=UTF-8").build()
                    : response.type("application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"messages.pdf\"")
                    .build();

        } catch (Exception e) {
            log.error("Error generating PDF for messages", e);
            throw e;
        }
    }

}
