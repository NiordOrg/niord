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
import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationSearchParams;
import org.niord.core.publication.PublicationService;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.publication.vo.PublicationStatus.ACTIVE;

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
    PublicationService publicationService;

    @Inject
    MessageTagService messageTagService;

    @Inject
    AreaService areaService;

    @Inject
    NiordApp app;


    /***************************
     * Message functions
     ***************************/


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
            Set<String> publicationIds,
            Set<String> areaIds,
            Set<MainType> mainTypes,
            String wkt) throws Exception {

        Domain sortDomain = null;

        MessageSearchParams params = new MessageSearchParams();
        params.language(language)
                .publications(publicationIds)
                .mainTypes(mainTypes)
                .areaIds(areaIds)
                .extent(wkt)
                .includeNoPos(Boolean.TRUE); // Messages without a geometry may be included if WKT specified


        // Convert publications to their associated message tags
        if (!params.getPublications().isEmpty()) {
            Set<String> tags = publicationService.findTagsByPublicationIds(params.getPublications());

            // Validate user access to the tags
            if (!tags.isEmpty()) {
                String[] tagIds = tags.toArray(new String[tags.size()]);
                params.tags(messageTagService.findTags(tagIds).stream()
                        .map(MessageTag::getTagId)
                        .collect(Collectors.toSet()));
            }

            // Use the first specified publication domain to sort by
            Publication publication = publicationService.findByPublicationId(params.getPublications().iterator().next());
            if (publication != null) {
                sortDomain = publication.getDomain();
            }
        }

        boolean tagsSpecified = !params.getTags().isEmpty();
        boolean domainsSpecified = domainIds != null && !domainIds.isEmpty();
        boolean messageSeriesSpecified = messageSeries != null && !messageSeries.isEmpty();

        // If no tags or domains or message series have been defined, use the domains published by default
        if (!tagsSpecified && !domainsSpecified && !messageSeriesSpecified) {
            domainIds = domainService.getPublishedDomains().stream()
                    .map(Domain::getDomainId)
                    .collect(Collectors.toSet());
            domainsSpecified = true;
        }

        // Check if a domain has been specified - convert them to message series, area nad category restrictions
        if (domainsSpecified) {
            List<Domain> domains = domainService.findByDomainIds(domainIds);

            // Add the message series of the domains as a filter
            params.getSeriesIds().addAll(
                    domains.stream()
                            .flatMap(d -> d.getMessageSeries().stream())
                            .map(MessageSeries::getSeriesId)
                            .collect(Collectors.toSet())
            );

            // If all domains specify areas, add these as a filter
            if (domains.stream().noneMatch(d -> d.getAreas().isEmpty())) {
                params.getAreaIds().addAll(
                    domains.stream()
                        .flatMap(d -> d.getAreas().stream())
                        .map(a -> a.getId().toString())
                        .collect(Collectors.toList())
                );
            }

            // If all domains specify categories, add these as a filter
            if (domains.stream().noneMatch(d -> d.getCategories().isEmpty())) {
                params.getCategoryIds().addAll(
                        domains.stream()
                                .flatMap(d -> d.getCategories().stream())
                                .map(c -> c.getId().toString())
                                .collect(Collectors.toList())
                );
            }

            // Sort by the first domain specified
            if (!domains.isEmpty()) {
                sortDomain = domains.get(0);
            }
        }

        // Check if specific message series has been specified
        if (messageSeriesSpecified) {
            params.getSeriesIds().addAll(messageSeries);
        }

        // If no publications or message series (and thus, no domains) have been specified, return nothing
        if (params.getTags().isEmpty() && params.getSeriesIds().isEmpty()) {
            return new PagedSearchResultVo<>();
        }

        // Enforce allowed statuses
        if (params.getTags().isEmpty()) {
             params.statuses(Collections.singleton(Status.PUBLISHED));
        } else {
            params.statuses(new HashSet<>(Arrays.asList(Status.PUBLISHED, Status.CANCELLED, Status.EXPIRED)));
        }

        // Apply domain sort order
        params.checkSortByDomain(sortDomain);

        // Perform the search
        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        log.debug(String.format("Public search [%s] returns %d of %d messages in %d ms",
                params.toString(), searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));

        return searchResult;
    }


    /***************************
     * Publication functions
     ***************************/


    /**
     * Returns the publication with the given ID if it is public
     *
     * @param publicationId the publication ID
     * @return the publication with the given ID if it is public
     */
    public Publication getPublication(String publicationId) {
        Publication publication = publicationService.findByPublicationId(publicationId);
        return publication != null && publication.getStatus() == ACTIVE && publication.getCategory().isPublish()
                ? publication
                : null;
    }


    /**
     * Searches for publications
     */
    public List<Publication> searchPublications(String language, Long from, Long to) {

        PublicationSearchParams params = new PublicationSearchParams()
                .language(language)
                .from(from)
                .to(to)
                .statuses(ACTIVE)
                .published(true)
                .mainType(PublicationMainType.PUBLICATION);

        return publicationService
                .searchPublications(params)
                .getData();
    }


    /***************************
     * Area functions
     ***************************/


    /**
     * Returns the area with the given ID (ID or MRN)
     *
     * @param areaId the area ID or MRN
     * @return the area with the given ID if it is public
     */
    public Area getArea(String areaId) {
        return areaService.findByAreaId(areaId);
    }



}
