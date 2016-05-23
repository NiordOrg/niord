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

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.model.BaseEntity;
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


    @GET
    @Path("/message/{messageId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageVo getMessage(
            @PathParam("messageId") String messageId,
            @QueryParam("lang") String language) throws Exception {

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            throw new WebApplicationException("Message " + messageId + " does not exist", 404);
        }

        DataFilter filter = DataFilter.get()
                .lang(language)
                .fields(DataFilter.DETAILS, DataFilter.GEOMETRY, "Area.parent", "Category.parent");

        return message.toVo(filter);
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
            @QueryParam("viewMode") String viewMode
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

        Domain currentDomain = domainService.currentDomain();

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


        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        log.info(String.format("Search [%s] returns %d of %d messages in %d ms",
                params.toString(), searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));

        DataFilter filter = ("map".equalsIgnoreCase(viewMode))
                ? DataFilter.get().lang(language).fields(DataFilter.GEOMETRY, "MessageDesc.title")
                : DataFilter.get().lang(language).fields(DataFilter.DETAILS, DataFilter.GEOMETRY, "Area.parent", "Category.parent");

        return searchResult.map(m -> m.toVo(filter));
    }


}
