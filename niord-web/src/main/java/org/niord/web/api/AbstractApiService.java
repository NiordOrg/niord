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

import org.niord.core.NiordApp;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchParamsVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract base class for the API services
 */
public abstract class AbstractApiService {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;


    /**
     * Returns the message with the given ID if it is public
     *
     * @param messageId the message ID
     * @return the message with the given ID if it is public
     */
    public Message getMessage(String messageId) {
        Message message = messageService.resolveMessage(messageId);
        return message != null && message.getStatus().isPublic()
                ? message
                : null;
    }


    /**
     * Returns all published messages.
     * Optionally, filter by a geometry defined by the WKT (well-known text) parameter.
     */
    public PagedSearchResultVo<Message> searchMessages(
            String language,
            Set<String> domainIds,
            Set<String> messageSeries,
            Set<String> areaIds,
            Set<MainType> mainTypes,
            String wkt) throws Exception {

        MessageSearchParams params = new MessageSearchParams();
        params.language(language)
                .statuses(Collections.singleton(Status.PUBLISHED))
                .mainTypes(mainTypes)
                .areaIds(areaIds)
                .extent(wkt)
                .includeGeneral(Boolean.TRUE) // Messages without a geometry may be included if WKT specified
                .sortBy("AREA")
                .sortOrder(PagedSearchParamsVo.SortOrder.ASC);


        boolean domainsSpecified = domainIds != null && !domainIds.isEmpty();
        boolean messageSeriesSpecified = messageSeries != null && !messageSeries.isEmpty();

        // If no domain and no message series have been defined, use the domains published by default
        if (!domainsSpecified && !messageSeriesSpecified) {
            domainIds = domainService.getPublishedDomains().stream()
                    .map(Domain::getDomainId)
                    .collect(Collectors.toSet());
            domainsSpecified = true;
        }

        // Check if a domain has been specified
        if (domainsSpecified) {
            domainIds.forEach(domainId -> {
                Domain domain = domainService.findByDomainId(domainId);
                if (domain != null) {
                    // Add the message series of the domain as a filter
                    params.getSeriesIds().addAll(
                            domain.getMessageSeries().stream()
                                    .map(MessageSeries::getSeriesId)
                                    .collect(Collectors.toSet())
                    );

                    // Set the areas to be the ones defined by the domain
                    params.getAreaIds().addAll(
                            domain.getAreas().stream()
                                    .map(a -> a.getId().toString())
                                    .collect(Collectors.toSet())
                    );

                    // Set the categories to be the ones defined by the domain
                    params.getCategoryIds().addAll(
                            domain.getCategories().stream()
                                    .map(c -> c.getId().toString())
                                    .collect(Collectors.toSet())
                    );
                }
            });
        }

        // Check if specific message series has been specified
        if (messageSeriesSpecified) {
            params.getSeriesIds().addAll(messageSeries);
        }

        // If no message series (and thus, no domains) have been specified, return nothing
        if (params.getSeriesIds().isEmpty()) {
            return new PagedSearchResultVo<>();
        }


        // Perform the search
        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        log.info(String.format("Public search [%s] returns %d of %d messages in %d ms",
                params.toString(), searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));

        return searchResult;
    }

}
