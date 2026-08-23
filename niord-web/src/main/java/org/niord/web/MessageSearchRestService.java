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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageIdMatch;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.message.MemberSetDesignation;
import org.niord.core.message.PublicationMemberSetSource;
import org.niord.core.user.UserService;
import org.niord.model.DataFilter;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;

/**
 * REST interface for searching messages.
 */
@Path("/messages")
@RequestScoped
@Transactional
@PermitAll
public class MessageSearchRestService {


    @Inject
    Logger log;

    @Inject
    MessageService messageService;

    @Inject
    MessageTagService messageTagService;

    @Inject
    PublicationMemberSetSource memberSetSource;

    @Inject
    DomainService domainService;

    @Inject
    UserService userService;


    /** Returns the domain used for searching messages **/
    private Domain searchDomain(MessageSearchParams params) {
        Domain searchDomain = null;

        // Check if a domain parameter has been specified
        if (StringUtils.isNotBlank(params.getDomain())) {
            searchDomain = domainService.findByDomainId(params.getDomain());
        }

        // Fall back to the current domain
        if (searchDomain == null) {
            searchDomain = domainService.currentDomain();
        }

        return searchDomain;
    }


    /**
     * Main search method.
     * Validates the message search parameters and enforces security.
     *
     * @param params the search parameters
     * @return the search result
     */
    private PagedSearchResultVo<Message> search(MessageSearchParams params) throws Exception {

        Domain currentDomain = domainService.currentDomain();
        Domain searchDomain = searchDomain(params);

        // Resolve publication= before any of the decisions below, and refuse
        // rather than widen.
        //
        // The tier comes from the CALLER, not from the endpoint: this class is
        // @PermitAll, so an anonymous request reaches it. Hard-coding the
        // internal tier here would let anyone who guesses an id resolve an OPEN
        // issue or a DRAFT publication -- the contents of an issue nobody has
        // released.
        PublicationMemberSetSource.Audience audience = userService.currentUser() != null
                ? PublicationMemberSetSource.Audience.INTERNAL
                : PublicationMemberSetSource.Audience.PUBLIC;

        MemberSetDesignation designation =
                memberSetSource.designate(params.getPublications(), audience);
        params.memberSetDesignation(designation);
        boolean memberSetDesignated = designation.designatesMemberSet();

        // Enforce security rules - depends on whether the current user is in the context of a domain or not.
        if (searchDomain != null) {

            /* TODO: Reconsider if we should really restrict messages by domain areas and categories
            // If no areas specified, use the ones of the search domain
            if (params.getAreaIds().isEmpty() && !searchDomain.getAreas().isEmpty()) {
                params.areaIds(
                        searchDomain.getAreas().stream()
                                .map(BaseEntity::getId)
                                .collect(Collectors.toSet())
                );
            }

            // If no categories specified, use the ones of the search domain
            if (params.getCategoryIds().isEmpty() && !searchDomain.getCategories().isEmpty()) {
                params.categoryIds(
                        searchDomain.getCategories().stream()
                                .map(BaseEntity::getId)
                                .collect(Collectors.toSet())
                );
            }
            */

            // Validate that the specified tags are allowed for the current user.
            //
            // Only the tag= parameter reaches here now. Publication-derived tags
            // used to be folded in above and validated against the search domain
            // with them -- which truncated an official issue for an admin browsing
            // in another domain. A designated member set is served verbatim: it is
            // an explicit list, and 17 live publications have no domain at all.
            if (!params.getTags().isEmpty()) {
                String[] tagIds = params.getTags().toArray(new String[params.getTags().size()]);
                params.tags(messageTagService.findTags(searchDomain, tagIds).stream()
                        .map(MessageTag::getTagId)
                        .collect(Collectors.toSet()));
            }

            // If nothing designates a member set and no valid tags are specified,
            // impose restrictions on statuses.
            //
            // A UNION, not a replacement. getTags() is fed from two sources here:
            // the tag= parameter and, until now, the publication conversion. Drop
            // the conversion and simply swap the test, and every plain tag= search
            // has the restriction block re-imposed on it -- cross-domain tagged
            // messages disappear and non-public statuses are stripped, from a
            // parameter this redesign is not supposed to touch.
            if (!memberSetDesignated && params.getTags().isEmpty()) {

                Set<String> domainSeries = searchDomain.getMessageSeries().stream()
                        .map(MessageSeries::getSeriesId)
                        .collect(Collectors.toSet());

                // Restrict message series IDs to valid ones for the search domain
                params.seriesIds(
                        params.getSeriesIds().stream()
                                .filter(domainSeries::contains)
                                .collect(Collectors.toSet())
                );
                // If no message series is specified, use the ones of the search domain
                if (params.getSeriesIds().isEmpty()) {
                    params.seriesIds(domainSeries);
                }

                // If the search domain != current domain, restrict statuses to published, cancelled and expired
                if (currentDomain == null || !Objects.equals(searchDomain.getDomainId(), currentDomain.getDomainId())) {
                    params.getStatuses().removeIf(s -> !s.isPublic());
                }

                // If no valid status is defined return only published messages
                if (params.getStatuses().isEmpty()) {
                    params.getStatuses().add(Status.PUBLISHED);
                }
            }

        } else {
            // Return published messages if nothing narrows the search. Same union:
            // a designated member set counts, and so does a plain tag=.
            if (!memberSetDesignated && params.getTags().isEmpty()) {
                params.statuses(Collections.singleton(Status.PUBLISHED));
            }
        }

        // Adjust the date interval so that is the beginning of the from-date and end of the to-date.
        if (currentDomain != null) {
            params.adjustDateInterval(currentDomain.timeZone());
        }

        // Perform the search
        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);

        // Record a textual description of the search
        String description = params.toString();
        searchResult.setDescription(description);

        log.debug(String.format("Search [%s] returns %d of %d messages in %d ms",
                description, searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));
        return searchResult;
    }


    /**
     * Main search method.
     * Validates the message search parameters and enforces security.
     *
     * @param params the search parameters
     * @return the search result
     */
    public PagedSearchResultVo<MessageVo> searchMessages(MessageSearchParams params) throws Exception {

        DataFilter filter = ("map".equalsIgnoreCase(params.getViewMode()))
                ? Message.MESSAGE_MAP_FILTER.lang(params.getLanguage())
                : Message.MESSAGE_DETAILS_FILTER.lang(params.getLanguage()).user(userService.userResolver());
        return search(params).map(m -> m.toVo(MessageVo.class, filter));
    }


    /**
     * Main search method.
     * Validates the message search parameters and enforces security.
     *
     * @param params the search parameters
     * @return the search result
     */
    public PagedSearchResultVo<SystemMessageVo> searchSystemMessages(MessageSearchParams params) throws Exception {

        DataFilter filter = ("map".equalsIgnoreCase(params.getViewMode()))
                ? Message.MESSAGE_MAP_FILTER.lang(params.getLanguage())
                : Message.MESSAGE_DETAILS_AND_PROMULGATIONS_FILTER.lang(params.getLanguage()).user(userService.userResolver());
        return search(params).map(m -> m.toVo(SystemMessageVo.class, filter));
    }


    /**
     * Main search method
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PagedSearchResultVo<MessageVo> search(@Context  HttpServletRequest request) throws Exception {
        MessageSearchParams params = MessageSearchParams.instantiate(domainService.currentDomain(), request);
        return searchMessages(params);
    }


    /**
     * Returns a list of message IDs (database ID or shortId) that - possibly partially - matches
     * real text.
     *
     * @param lang the language to return the title in
     * @param txt the text to match
     * @param maxGroupCount the max number of matching message IDs to return.
     * @param includeText whether to include the search text as a match
     * @param deleted whether to include deleted messages in the result
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
            @QueryParam("includeText") @DefaultValue("true") boolean includeText,
            @QueryParam("deleted") @DefaultValue("false") boolean deleted) {

        return messageService.searchMessageIds(lang, txt, maxGroupCount, includeText, deleted);
    }


}
