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
import org.niord.core.publication.PublicationSearchParams;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationResolver;
import org.niord.core.publication.series.PublicationPublicAdapter;
import org.niord.core.publication.PublicationService;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.message.MemberSetDesignation;
import org.niord.core.message.PublicationMemberSetSource;
import org.niord.model.DataFilter;
import org.niord.model.publication.PublicationVo;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
    PublicationResolver publicationResolver;

    @Inject
    PublicationPublicAdapter publicationAdapter;

    @Inject
    PublicationMemberSetSource memberSetSource;

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


        // Resolve the publication= ids here, once, and refuse rather than widen.
        //
        // This used to convert publications to message tags and then let tag
        // PRESENCE drive every decision below. An id that produced no tag -- a
        // typo, or any of the 27 ACTIVE publications that have no message tag --
        // therefore looked exactly like "no publication was named", and the
        // widening below returned every published message. The designation keeps
        // the two apart: it is true as soon as one id resolves, whatever it
        // contains.
        MemberSetDesignation designation = memberSetSource.designate(
                params.getPublications(), PublicationMemberSetSource.Audience.PUBLIC);
        params.memberSetDesignation(designation);

        boolean memberSetDesignated = designation.designatesMemberSet();

        if (memberSetDesignated) {
            // Use the first specified publication's domain to sort by
            sortDomain = publicationResolver.sortDomain(params.getPublications());
        }

        boolean domainsSpecified = domainIds != null && !domainIds.isEmpty();
        boolean messageSeriesSpecified = messageSeries != null && !messageSeries.isEmpty();

        // If nothing designates a member set and no domains or message series have
        // been defined, use the domains published by default
        if (!memberSetDesignated && !domainsSpecified && !messageSeriesSpecified) {
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
        if (!memberSetDesignated && params.getSeriesIds().isEmpty()) {
            return new PagedSearchResultVo<>();
        }

        // Enforce allowed statuses.
        //
        // Derived from Status.isPublic() rather than listed: all 2,324 members of
        // the blank-era issues are EXPIRED or CANCELLED and not one is PUBLISHED,
        // so a literal "PUBLISHED only" filter empties every historical issue --
        // and empties it quietly, because an issue with no messages still renders.
        if (!memberSetDesignated) {
            params.statuses(Collections.singleton(Status.PUBLISHED));
        } else {
            params.statuses(Arrays.stream(Status.values())
                    .filter(Status::isPublic)
                    .collect(Collectors.toSet()));
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
     * Returns the publication with the given ID if it is public.
     *
     * Returns a value object rather than the entity. The public face of a
     * publication has two possible sources -- the legacy table and, after
     * cutover, a published issue of a series -- and only one of them has a
     * {@code Publication} row behind it. Converting here lets the second source
     * be added without fabricating a transient entity to carry it.
     *
     * @param publicationId the publication ID
     * @param language      the language to filter the descriptions by
     * @return the publication with the given ID if it is public, or null
     */
    public PublicationVo getPublication(String publicationId, String language) {
        // Through the shared resolver, so a citation into a cut-over series
        // resolves here exactly as it does in the message search. The public
        // tier: a PUBLISHED issue, or an ACTIVE publication in a publishing
        // category, and nothing else.
        return publicationResolver.publicVo(
                publicationId, language, PublicationMemberSetSource.Audience.PUBLIC);
    }


    /**
     * Searches for publications
     */
    public List<PublicationVo> searchPublications(String language, Long from, Long to) {
        // The transition union: published issues of series that have cut over,
        // plus the legacy rows none of them has taken over. Before the first flip
        // the first half is empty and this is the legacy list, in the legacy
        // order -- category priority, then publish date descending.
        return publicationAdapter.listVo(
                from == null ? null : new Date(from),
                to == null ? null : new Date(to),
                language);
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
