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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.NiordApp;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.domain.DomainService;
import org.niord.core.message.MessagePrintParams;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.core.report.FmReport;
import org.niord.core.report.FmReportService;
import org.niord.core.report.vo.FmReportVo;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.FmTemplateService.ProcessFormat;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.niord.model.DataFilter;
import org.niord.model.message.MessageVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST interface for generating PDF message reports.
 */
@Path("/message-reports")
@RequestScoped
@Transactional
@PermitAll
@SuppressWarnings("unused")
public class MessageReportRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    NiordApp app;

    @Inject
    DomainService domainService;

    @Inject
    UserService userService;

    @Inject
    MessageService messageService;

    @Inject
    MessageRestService messageRestService;

    @Inject
    MessageSearchRestService messageSearchRestService;

    @Inject
    FmReportService fmReportService;

    @Inject
    FmTemplateService templateService;

    /***************************************/
    /** PDF Reports                       **/
    /***************************************/

    /**
     * Returns the reports available for printing message lists in the current domain
     * @return the reports available for printing message lists in the current domain
     */
    @GET
    @Path("/reports")
    @GZIP
    @NoCache
    public List<FmReportVo> getReports(
            @QueryParam("expandParams") @DefaultValue("true") boolean expandParams
    ) {
        return fmReportService.getReports().stream()
                .map(FmReport::toVo)
                .map(r -> expandParams ? fmReportService.expandReportParams(r) : r)
                .collect(Collectors.toList());
    }


    /**
     * Returns all reports
     * @return all reports
     */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<FmReportVo> getAllReports() {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.SYSADMIN)) {
            throw new WebApplicationException(403);
        }

        DataFilter filter = DataFilter.get().fields("domains");
        return fmReportService.getAllReports().stream()
                .map(r -> r.toVo(filter))
                .collect(Collectors.toList());
    }


    /**
     * Returns the reports available for printing message details
     * @return the reports available for printing message details
     */
    @GET
    @Path("/detail-reports")
    @GZIP
    @NoCache
    public List<FmReportVo> getDetailReports() {
        return Arrays.asList(
                fmReportService.getStandardReport().toVo(),
                fmReportService.getDraftReport().toVo()
        );
    }


    /** Creates a new report */
    @POST
    @Path("/report/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FmReportVo createReport(FmReportVo report) throws Exception {
        log.info("Creating report " + report);
        return fmReportService.createReport(new FmReport(report))
                .toVo();
    }


    /** Updates an existing report */
    @PUT
    @Path("/report/{reportId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FmReportVo updateReport(@PathParam("reportId") String reportId, FmReportVo report) throws Exception {
        if (!Objects.equals(reportId, report.getReportId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating report " + report);
        return fmReportService.updateReport(new FmReport(report))
                .toVo();
    }


    /** Deletes an existing report */
    @DELETE
    @Path("/report/{reportId}")
    @GZIP
    @NoCache
    public void deleteReport(@PathParam("reportId") String reportId) throws Exception {
        log.info("Deleting report " + reportId);
        fmReportService.deleteReport(reportId);
    }


    /**
     * Imports an uploaded reports json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-reports")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importReports(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "report-import");
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

        // The "draft" report display all language variants, so, sort instead of filter by language
        MessageVo message = messageRestService.getMessage(messageId, null);
        message.sortDescs(language);

        MessagePrintParams printParams = MessagePrintParams.instantiate(request);

        try {
            FmReport report = fmReportService.getReport(printParams.getReport());

            ProcessFormat format = printParams.getDebug() ? ProcessFormat.TEXT : ProcessFormat.PDF;

            StreamingOutput stream = os -> {
                try {
                    templateService.newFmTemplateBuilder()
                            .templatePath(report.getTemplatePath())
                            .data("executionMode", app.getExecutionMode())
                            .data("messages", Collections.singleton(message))
                            .data("areaHeadings", false)
                            .data("pageSize", printParams.getPageSize())
                            .data("pageOrientation", printParams.getPageOrientation())
                            .data("mapThumbnails", printParams.getMapThumbnails())
                            .data("frontPage", false)
                            .data(report.getProperties())   // Let report override settings
                            .data(printParams.getParams())  // Custom user-defined params
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
                    .header("Content-Disposition", printParams.getFileNameHeader("message-" + messageId))
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
        PagedSearchResultVo<MessageVo> result = messageSearchRestService.searchMessages(params);
        result.getData().forEach(m -> m.sort(language));

        // Get the UIDs of the messages that should start on a new page
        Set<String> separatePageIds = messageService.getSeparatePageUids(
                result.getData().stream().map(MessageVo::getId).collect(Collectors.toSet()));

        try {
            FmReport report = fmReportService.getReport(printParams.getReport());

            ProcessFormat format = printParams.getDebug() ? ProcessFormat.TEXT : ProcessFormat.PDF;

            StreamingOutput stream = os -> {
                try {
                    templateService.newFmTemplateBuilder()
                            .templatePath(report.getTemplatePath())
                            .data("executionMode", app.getExecutionMode())
                            .data("messages", result.getData())
                            .data("areaHeadings", params.sortByArea())
                            .data("searchCriteria", result.getDescription())
                            .data("pageSize", printParams.getPageSize())
                            .data("pageOrientation", printParams.getPageOrientation())
                            .data("mapThumbnails", printParams.getMapThumbnails())
                            .data("separatePageIds", separatePageIds)
                            .data("frontPage", true)
                            .data(report.getProperties())  // Let report override settings
                            .data(printParams.getParams()) // Custom user-defined params
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
                    .header("Content-Disposition", printParams.getFileNameHeader("messages"))
                    .build();

        } catch (Exception e) {
            log.error("Error generating PDF for messages", e);
            throw e;
        }
    }

}
