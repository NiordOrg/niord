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
package org.niord.web;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.NiordApp;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.fm.FmService;
import org.niord.core.fm.FmService.ProcessFormat;
import org.niord.core.message.EditableMessageVo;
import org.niord.core.message.Message;
import org.niord.core.message.MessageHistory;
import org.niord.core.message.MessageIdMatch;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.model.BaseEntity;
import org.niord.core.repo.FileTypes;
import org.niord.core.repo.RepositoryService;
import org.niord.core.user.TicketService;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.PagedSearchParamsVo.SortOrder;
import org.niord.model.PagedSearchResultVo;
import org.niord.model.vo.AttachmentVo;
import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageHistoryVo;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.niord.model.vo.Type;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.model.vo.MessageTagVo.MessageTagType.PUBLIC;

/**
 * REST interface for managing messages.
 */
@Path("/messages")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
@SuppressWarnings("unused")
public class MessageRestService {

    @Inject
    Logger log;

    @Inject
    FileTypes fileTypes;

    @Inject
    NiordApp app;

    @Inject
    RepositoryService repositoryService;

    @Inject
    MessageService messageService;

    @Inject
    DomainService domainService;

    @Inject
    FmService fmService;

    @Inject
    TicketService ticketService;


    /***************************
     * Message access functions
     ***************************/

    /**
     * Checks that the user has editing access to the given message by matching the current domain
     * with the message series of the message.<br>
     * Throws a 403 response code error if no access.
     * @param message the message
     */
    private void checkMessageEditingAccess(Message message) {
        Domain domain = domainService.currentDomain();
        if (domain == null ||
                message == null ||
                message.getMessageSeries() == null ||
                domain.getMessageSeries().stream()
                        .noneMatch(ms -> ms.getSeriesId().equals(message.getMessageSeries().getSeriesId()))) {
            throw new WebApplicationException(403);
        }
    }


    /**
     * Checks that the user has viewing access to the given message. The message must adhere to one of the following
     * criteria:
     * <ul>
     *     <li>The message is published.</li>
     *     <li>The current domain of the user matches the message series of the message.</li>
     *     <li>The message is associated with a public tag.</li>
     * </ul>
     *
     * Throws a 403 response code error if no access.
     * @param message the message
     * @param ticket optionally a ticket parameter
     */
    private void checkMessageViewingAccess(Message message, String ticket) {
        // 1) Always grant access if the message is published
        if (message.getStatus() == Status.PUBLISHED) {
            return;
        }

        // 2) Grant access if the current domain of the user matches the message series of the message
        Domain domain = currentDomain(ticket);
        if (domain != null &&
                message.getMessageSeries() != null &&
                domain.getMessageSeries().stream()
                        .anyMatch(ms -> ms.getSeriesId().equals(message.getMessageSeries().getSeriesId()))) {
            return;
        }

        // 3) Grant access if the message is associated with a public tag.
        if (message.getTags().stream()
                .anyMatch(t -> t.getType() == PUBLIC)) {
            return;
        }

        // No access
        throw new WebApplicationException(403);
    }


    /**
     * Returns the message with the given message id, which may be either a UID,
     * or a short ID or an MRN of a message.
     *
     * If no message exists with the given ID, null is returned.
     *
     * @param messageId the message ID
     * @param language the language of the returned data
     * @return the message or null
     */
    @GET
    @Path("/message/{messageId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageVo getMessage(
            @PathParam("messageId") String messageId,
            @QueryParam("lang") String language,
            @QueryParam("ticket") String ticket
            ) throws Exception {

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return null;
        }

        // Validate access to the message
        checkMessageViewingAccess(message, ticket);

        DataFilter filter = DataFilter.get()
                .lang(language)
                .fields("Message.details", "Message.geometry", "Area.parent", "Category.parent");

        return message.toVo(filter);
    }


    /**
     * Returns the editable message with the given message id, which may be either a UID,
     * or a short ID or an MRN of a message.
     *
     * If no message exists with the given ID, null is returned.
     *
     * @param messageId the message ID
     * @param language the language to sort the returned data by
     * @return the message or null
     */
    @GET
    @Path("/editable-message/{messageId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public EditableMessageVo getEditableMessage(
            @PathParam("messageId") String messageId,
            @QueryParam("lang") String language) throws Exception {

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return null;
        }

        // Validate access to the message
        checkMessageEditingAccess(message);

        DataFilter filter = DataFilter.get()
                .fields("Message.details", "Message.geometry", "Area.parent", "Category.parent");

        EditableMessageVo messageVo =  message.toEditableVo(filter);
        messageVo.sortDescs(language);

        // Create a temporary repository folder for the message
        messageService.createTempMessageRepoFolder(messageVo);

        return messageVo;
    }


    /**
     * Creates a new message template with a temporary repository path
     *
     * @return the new message template
     */
    @GET
    @Path("/new-message-template")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public EditableMessageVo newTemplateMessage(@QueryParam("mainType") MainType mainType) throws IOException {

        // Validate the mainType is valid in the current domain
        Domain domain = domainService.currentDomain();
        List<MessageSeries> messageSeries = domain.getMessageSeries().stream()
                .filter(ms -> ms.getMainType().equals(mainType))
                .collect(Collectors.toList());

        if (messageSeries.isEmpty()) {
            throw new WebApplicationException(403);
        }

        // Create a new template message
        Message message = messageService.newTemplateMessage(mainType);

        // If only one matching message series is defined, set it for the message.
        if (messageSeries.size() == 1) {
            message.setMessageSeries(messageSeries.get(0));
        }

        EditableMessageVo messageVo =  message.toEditableVo(DataFilter.get().fields("Message.details"));

        // Create a temporary repository folder for the message
        messageService.createTempMessageRepoFolder(messageVo);

        return messageVo;
    }


    /**
     * Creates a new draft message
     *
     * @param message the message to create
     * @return the persisted message
     */
    @POST
    @Path("/message")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageVo createMessage(EditableMessageVo message) throws Exception {
        log.info("Creating message " + message);
        Message msg = new Message(message);

        // Validate access to the message
        checkMessageEditingAccess(msg);

        msg = messageService.createMessage(msg);

        // Copy resources from the temporary editing message folder to the message repository folder
        message.setId(msg.getUid());
        messageService.updateMessageFromTempRepoFolder(message);

        return getMessage(msg.getUid(), null, null);
    }


    /**
     * Updates a message
     *
     * @param message the message to update
     * @return the updated message
     */
    @PUT
    @Path("/message/{messageId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageVo updateMessage(@PathParam("messageId") String messageId, EditableMessageVo message) throws Exception {
        if (!Objects.equals(messageId, message.getId())) {
            throw new WebApplicationException(400);
        }
        log.info("Updating message " + message);
        Message msg = new Message(message);

        // Validate access to the message
        checkMessageEditingAccess(msg);
        checkMessageEditingAccess(messageService.findByUid(messageId));

        msg = messageService.updateMessage(msg);

        // Copy resources from the temporary editing message folder to the message repository folder
        messageService.updateMessageFromTempRepoFolder(message);

        return getMessage(msg.getUid(), null, null);
    }


    /**
     * Updates the status of a message
     *
     * @param status the status update
     * @return the updated message
     */
    @PUT
    @Path("/message/{messageId}/status")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageVo updateMessageStatus(@PathParam("messageId") String messageId, String status) throws Exception {
        log.info("Updating status of message " + messageId + " to " + status);

        // Validate access to the message
        checkMessageEditingAccess(messageService.findByUid(messageId));

        Message msg = messageService.updateStatus(messageId, Status.valueOf(status));
        return getMessage(msg.getUid(), null, null);
    }


    /***************************
     * Attachment handling
     ***************************/


    /**
     * Called to upload message attachments to a temporary message folder
     *
     * @param request the servlet request
     * @return a the updated list of attachments
     */
    @POST
    @Path("/attachments/{folder:.+}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed("editor")
    public List<AttachmentVo> uploadMessageAttachments(
            @PathParam("folder") String path,
            @Context HttpServletRequest request) throws Exception {

        List<String> uploadedFiles = repositoryService.uploadTempFile(path, request);

        return uploadedFiles.stream()
                .map(f -> {
                    try {
                        File file = repositoryService.getRepoRoot().resolve(f).toFile();
                        AttachmentVo att = new AttachmentVo();
                        att.setFileName(file.getName());
                        att.setFileSize(file.length());
                        att.setFileUpdated(new Date(file.lastModified()));
                        att.setType(fileTypes.getContentType(file));
                        return att;
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .filter(att -> att != null)
                .collect(Collectors.toList());
    }


    /***************************
     * Search functionality
     ***************************/


    /**
     * Main search method
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PagedSearchResultVo<MessageVo> search(
            @QueryParam("lang") String language,
            @QueryParam("query") String query,
            @QueryParam("domain") @DefaultValue("true") boolean domain, // By default, filter by current domain
            @QueryParam("status") Set<Status> statuses,
            @QueryParam("mainType") Set<MainType> mainTypes,
            @QueryParam("type") Set<Type> types,
            @QueryParam("messageSeries") Set<String> seriesIds,
            @QueryParam("area") Set<Integer> areaIds,
            @QueryParam("category") Set<Integer> categoryIds,
            @QueryParam("chart") Set<String> chartNumbers,
            @QueryParam("tag") Set<String> tags,
            @QueryParam("aton") Set<String> atonUids,
            @QueryParam("fromDate") Long fromDate,
            @QueryParam("toDate") Long toDate,
            @QueryParam("minLat") Double minLat,
            @QueryParam("minLon") Double minLon,
            @QueryParam("maxLat") Double maxLat,
            @QueryParam("maxLon") Double maxLon,
            @QueryParam("includeGeneral") Boolean includeGeneral,
            @QueryParam("maxSize") @DefaultValue("100") int maxSize,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("sortBy") String sortBy,
            @QueryParam("sortOrder") SortOrder sortOrder,
            @QueryParam("viewMode") String viewMode,
            @QueryParam("ticket") String ticket
    ) throws Exception {

        MessageSearchParams params = new MessageSearchParams();
        params.language(language)
                .query(query)
                .statuses(statuses)
                .mainTypes(mainTypes)
                .types(types)
                .seriesIds(seriesIds)
                .areaIds(areaIds)
                .categoryIds(categoryIds)
                .chartNumbers(chartNumbers)
                .tags(tags)
                .atonUids(atonUids)
                .from(fromDate)
                .to(toDate)
                .extent(minLat, minLon, maxLat, maxLon)
                .includeGeneral(includeGeneral)
                .maxSize(maxSize)
                .page(page)
                .sortBy(sortBy)
                .sortOrder(sortOrder);

        Domain currentDomain = currentDomain(ticket);

        // Enforce security rules - depends on whether the current user is in the context of a domain or not.
        if (domain && currentDomain != null) {

            Set<String> domainSeries = currentDomain.getMessageSeries().stream()
                    .map(MessageSeries::getSeriesId)
                    .collect(Collectors.toSet());

            // Restrict message series IDs to valid ones for the current domain
            params.seriesIds(
                    params.getSeriesIds().stream()
                    .filter(domainSeries::contains)
                    .collect(Collectors.toSet())
            );
            // If no message series is specified, use the ones of the current domain
            if (params.getSeriesIds().isEmpty()) {
                params.seriesIds(domainSeries);
            }

            // If no areas specified, use the ones of the current domain
            if (params.getAreaIds().isEmpty() && !currentDomain.getAreas().isEmpty()) {
                params.areaIds(
                        currentDomain.getAreas().stream()
                                .map(BaseEntity::getId)
                                .collect(Collectors.toSet())
                );
            }

            // If no categories specified, use the ones of the current domain
            if (params.getCategoryIds().isEmpty() && !currentDomain.getCategories().isEmpty()) {
                params.categoryIds(
                        currentDomain.getCategories().stream()
                                .map(BaseEntity::getId)
                                .collect(Collectors.toSet())
                );
            }

            // Return published messages if no status is defined and no tags has been specified
            if (params.getStatuses().isEmpty() && params.getTags().isEmpty()) {
                params.getStatuses().add(Status.PUBLISHED);
            }

        } else {
            // Return published messages if no tags has been specified
            if (params.getTags().isEmpty()) {
                params.statuses(Collections.singleton(Status.PUBLISHED));
            }
        }

        // Perform the search
        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);

        // Record a textual description of the search
        String description = params.toString();
        searchResult.setDescription(description);

        log.info(String.format("Search [%s] returns %d of %d messages in %d ms",
                description, searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));

        DataFilter filter = ("map".equalsIgnoreCase(viewMode))
                ? DataFilter.get().lang(language).fields("Message.geometry", "MessageDesc.title")
                : DataFilter.get().lang(language).fields("Message.details", "Message.geometry", "Area.parent", "Category.parent");

        return searchResult.map(m -> m.toVo(filter));
    }


    /**
     * Returns a list of message IDs (database ID, MRN or shortId) that - possibly partially - matches
     * real text.
     *
     * @param lang the language to return the title in
     * @param txt the text to match
     * @param maxGroupCount the max number of matching message IDs to return.
     * @param includeText whether to include the search text as a match
     * @return the search result
     */
    @GET
    @Path("/search-message-ids")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageIdMatch> searchMessageIds(
            @QueryParam("lang") String lang,
            @QueryParam("txt") String txt,
            @QueryParam("maxGroupCount") @DefaultValue("10") int maxGroupCount,
            @QueryParam("includeText") @DefaultValue("true") boolean includeText) {

        return messageService.searchMessageIds(lang, txt, maxGroupCount, includeText);
    }


    /***************************
     * PDF functionality
     ***************************/


    /**
     * Generates a PDF for the message with the given message id, which may be either a UID,
     * or a short ID or an MRN of a message.
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
            @QueryParam("ticket") String ticket,
            @QueryParam("pageSize") @DefaultValue("A4") String pageSize,
            @QueryParam("pageOrientation") @DefaultValue("portrait") String pageOrientation,
            @QueryParam("debug") @DefaultValue("false") boolean debug) throws Exception {

        MessageVo message = getMessage(messageId, language, ticket);

        try {
            ProcessFormat format = debug ? ProcessFormat.TEXT : ProcessFormat.PDF;

            StreamingOutput stream = os -> {
                try {
                    fmService.newTemplateBuilder()
                            .setTemplatePath("/templates/messages/message-details.ftl")
                            .setData("msg", message)
                            .setData("pageSize", pageSize)
                            .setData("pageOrientation", pageOrientation)
                            .setDictionaryNames("web", "message", "pdf")
                            .setLanguage(language)
                            .process(format, os);
                } catch (Exception e) {
                    throw new WebApplicationException("Error generating PDF for message " + messageId, e);
                }
            };

            Response.ResponseBuilder response = Response.ok(stream);
            return debug
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
    @Path("/search.pdf")
    @GZIP
    @NoCache
    public Response generatePdfForSearch(
            @QueryParam("lang") String language,
            @QueryParam("query") String query,
            @QueryParam("domain") @DefaultValue("true") boolean domain, // By default, filter by current domain
            @QueryParam("status") Set<Status> statuses,
            @QueryParam("mainType") Set<MainType> mainTypes,
            @QueryParam("type") Set<Type> types,
            @QueryParam("messageSeries") Set<String> seriesIds,
            @QueryParam("area") Set<Integer> areaIds,
            @QueryParam("category") Set<Integer> categoryIds,
            @QueryParam("chart") Set<String> chartNumbers,
            @QueryParam("tag") Set<String> tags,
            @QueryParam("aton") Set<String> atonUids,
            @QueryParam("fromDate") Long fromDate,
            @QueryParam("toDate") Long toDate,
            @QueryParam("minLat") Double minLat,
            @QueryParam("minLon") Double minLon,
            @QueryParam("maxLat") Double maxLat,
            @QueryParam("maxLon") Double maxLon,
            @QueryParam("includeGeneral") Boolean includeGeneral,
            @QueryParam("sortBy") String sortBy,
            @QueryParam("sortOrder") SortOrder sortOrder,
            @QueryParam("ticket") String ticket,
            @QueryParam("pageSize") @DefaultValue("A4") String pageSize,
            @QueryParam("pageOrientation") @DefaultValue("portrait") String pageOrientation,
            @QueryParam("debug") @DefaultValue("false") boolean debug
    ) throws Exception {

        // Perform a search for at most 1000 messages
        PagedSearchResultVo<MessageVo> result = search(
                language,
                query,
                domain,
                statuses,
                mainTypes,
                types,
                seriesIds,
                areaIds,
                categoryIds,
                chartNumbers,
                tags,
                atonUids,
                fromDate,
                toDate,
                minLat,
                minLon,
                maxLat,
                maxLon,
                includeGeneral,
                1000,   // max-size
                0,      // page
                sortBy,
                sortOrder,
                null,    // viewMode
                ticket
        );

        try {
            ProcessFormat format = debug ? ProcessFormat.TEXT : ProcessFormat.PDF;

            StreamingOutput stream = os -> {
                try {
                    fmService.newTemplateBuilder()
                            .setTemplatePath("/templates/messages/message-list.ftl")
                            .setData("messages", result.getData())
                            .setData("areaHeadings", "AREA".equalsIgnoreCase(sortBy))
                            .setData("searchCriteria", result.getDescription())
                            .setData("pageSize", pageSize)
                            .setData("pageOrientation", pageOrientation)
                            .setDictionaryNames("web", "message", "pdf")
                            .setLanguage(language)
                            .process(format, os);
                } catch (Exception e) {
                    throw new WebApplicationException("Error generating PDF for messages", e);
                }
            };

            Response.ResponseBuilder response = Response.ok(stream);
            return debug
                    ? response.type("text/html;charset=UTF-8").build()
                    : response.type("application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"messages.pdf\"")
                    .build();

        } catch (Exception e) {
            log.error("Error generating PDF for messages", e);
            throw e;
        }
    }


    /***************************************/
    /** Message History methods           **/
    /***************************************/

    /**
     * Returns the message history for the given message ID
     * @param messageId the message ID or message series ID
     * @return the message history
     */
    @GET
    @Path("/message/{messageId}/history")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public List<MessageHistoryVo> getMessageHistory(@PathParam("messageId") String messageId) {

        // Get the message id
        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return Collections.emptyList();
        }

        return messageService.getMessageHistory(message.getId()).stream()
                .map(MessageHistory::toVo)
                .collect(Collectors.toList());
    }


    /***************************
     * Sorting functionality
     ***************************/


    /** Re-computes the area sort order of published messages in the current domain */
    @PUT
    @Path("/recompute-area-sort-order")
    @RolesAllowed({"admin"})
    public void reindexPublishedMessageAreaSorting() {

        // Search for published message in the curretn domain
        Domain domain = domainService.currentDomain();
        Set<String> messageSeries = domain.getMessageSeries().stream()
                .map(MessageSeries::getSeriesId)
                .collect(Collectors.toSet());

        MessageSearchParams params = new MessageSearchParams();
        params.statuses(Collections.singleton(Status.PUBLISHED))
                .seriesIds(messageSeries);

        // Perform the search and update the area sort order of the messages
        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        searchResult.getData().stream()
                .forEach(m -> messageService.computeMessageAreaSortingOrder(m));

        log.info(String.format("Updates area sort order for  %d messages in %d ms",
                searchResult.getData().size(), System.currentTimeMillis() - t0));
    }


    /** Swaps the area sort order of the two messages */
    @PUT
    @Path("/change-area-sort-order")
    @RolesAllowed({"editor"})
    public void changeAreaSortOrder(AreaSortOrderUpdateParam params) {
        if (params.getId() == null || (params.getBeforeId() == null && params.getAfterId() == null)) {
            throw new WebApplicationException(400);
        }
        messageService.changeAreaSortOrder(params.getId(), params.getAfterId(), params.getBeforeId());
    }


    /***************************
     * Ticket functionality
     ***************************/


    /**
     * Returns a ticket to be used in a subsequent call to generated a PDF.
     * This is needed because the URLs issued by the javascript client when generating a PDF
     * are not ajax-based, and thus, do not get authorization and domain headers injected.
     * @return a PDF ticket
     */
    @GET
    @Path("/pdf-ticket")
    @Produces("text/plain")
    @NoCache
    public String getPdfTicket() {
        return ticketService.createTicketForDomain(domainService.currentDomain());
    }


    /**
     * Resolves the current domain, either via the usual container managed approach or from
     * a ticket request parameter issued via a previous call to "/pdf-ticket"
     * @param ticket the request ticket
     * @return the current domain
     */
    private Domain currentDomain(String ticket) {
        Domain domain = domainService.currentDomain();
        if (domain == null && StringUtils.isNotBlank(ticket)) {
            domain = ticketService.resolveTicketDomain(ticket);
        }
        return domain;
    }


    /***************************
     * Utility functions
     ***************************/


    /**
     * Computes the title lines for the given message template
     *
     * @param message the message template to compute the title line for
     * @return the updated message template
     */
    @POST
    @Path("/compute-title-line")
    @Consumes("application/json")
    @Produces("application/json")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageVo computeTitleLine(MessageVo message) throws Exception {
        DataFilter filter = DataFilter.get().fields("MessageDesc.title");
        return messageService.computeTitleLine(new Message(message)).toVo(filter);
    }


    /**
     * Formats the feature collection according to the given template
     *
     * @param geometry the feature collection to format
     * @param language the language of the returned data
     * @param template the template to use for formatting the geometry
     * @param format the position format, either "dec", "sec" or "navtex"
     * @return the formatted geometry
     */
    @POST
    @Path("/format-message-geometry")
    @Consumes("application/json")
    @Produces("text/plain;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public Response formatGeometry(
            @QueryParam("lang") @DefaultValue("en") String language,
            @QueryParam("template") @DefaultValue("list") String template,
            @QueryParam("format") @DefaultValue("dec") String format,
            FeatureCollectionVo geometry) throws Exception {

        try {
            String lang = app.getLanguage(language);
            Arrays.stream(geometry.getFeatures())
                    .forEach(f -> f.getProperties().put("language", lang));

            String templatePath = String.format("/templates/geometry/%s.ftl", template);
            StreamingOutput stream = os -> {
                try {
                    fmService.newTemplateBuilder()
                            .setTemplatePath(templatePath)
                            .setData("geometry", geometry)
                            .setData("format", format)
                            .setDictionaryNames("web", "message")
                            .setLanguage(lang)
                            .process(ProcessFormat.TEXT, os);

                } catch (Exception e) {
                    throw new WebApplicationException("Error formatting geometry", e);
                }
            };

           return Response
                   .ok(stream)
                   .type("text/html;charset=UTF-8")
                   .build();

        } catch (Exception e) {
            log.error("Error formatting geometry: " + e.getMessage(), e);
            throw new WebApplicationException("Error formatting geometry", e);
        }
    }


    /***************************
     * Helper classes
     ***************************/

    /** Parameter that encapsulates a message being dragged to a new area sort order position */
    public static class AreaSortOrderUpdateParam implements IJsonSerializable {
        String id;
        String beforeId;
        String afterId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getBeforeId() {
            return beforeId;
        }

        public void setBeforeId(String beforeId) {
            this.beforeId = beforeId;
        }

        public String getAfterId() {
            return afterId;
        }

        public void setAfterId(String afterId) {
            this.afterId = afterId;
        }
    }

}
