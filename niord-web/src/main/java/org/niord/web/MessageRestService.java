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
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.fm.FmService;
import org.niord.core.fm.FmService.ProcessFormat;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.model.BaseEntity;
import org.niord.core.user.TicketService;
import org.niord.model.DataFilter;
import org.niord.model.PagedSearchParamsVo.SortOrder;
import org.niord.model.PagedSearchResultVo;
import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.niord.model.vo.Type;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

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
    MessageService messageService;

    @Inject
    DomainService domainService;

    @Inject
    FmService fmService;

    @Inject
    TicketService ticketService;


    /***************************
     * Search functionality
     ***************************/

    /**
     * Resolves the database id of the given message id, which may be either a database id,
     * or a short ID or an MRN of a message.
     *
     * @param messageId the mesage id to resolve
     * @return the message id
     */
    private Integer resolveMessageId(String messageId) {
        Message message = messageService.resolveMessage(messageId);
        return message == null ? null : message.getId();
    }


    /**
     * Returns the message with the given message id, which may be either a database id,
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
            @QueryParam("lang") String language) throws Exception {

        // TODO: Validate message series etc according to the current domain

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return null;
        }

        DataFilter filter = DataFilter.get()
                .lang(language)
                .fields("Message.details", "Message.geometry", "Area.parent", "Category.parent");

        return message.toVo(filter);
    }


    /**
     * Generates a PDF for the message with the given message id, which may be either a database id,
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
            @QueryParam("debug") @DefaultValue("false") boolean debug) throws Exception {

        MessageVo message = getMessage(messageId, language);

        try {
            ProcessFormat format = debug ? ProcessFormat.TEXT : ProcessFormat.PDF;

            StreamingOutput stream = os -> {
                try {
                    fmService.newTemplateBuilder()
                            .setTemplatePath("/templates/messages/message-details.ftl")
                            .setData("msg", message)
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

}
