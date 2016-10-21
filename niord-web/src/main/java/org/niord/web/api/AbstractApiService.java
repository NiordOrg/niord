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

import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.model.BaseEntity;
import org.niord.model.DataFilter;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchParamsVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
     * Returns all published messages.
     * Optionally, filter by a geometry defined by the WKT (well-known text) parameter.
     */
    public List<MessageVo> search(
            String language,
            Set<String> domainIds,
            Set<String> messageSeries,
            Set<MainType> mainTypes,
            String wkt,
            boolean externalize) throws Exception {

        MessageSearchParams params = new MessageSearchParams();
        params.language(language)
                .statuses(Collections.singleton(Status.PUBLISHED))
                .mainTypes(mainTypes)
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
                                    .map(BaseEntity::getId)
                                    .collect(Collectors.toSet())
                    );

                    // Set the categories to be the ones defined by the domain
                    params.getCategoryIds().addAll(
                            domain.getCategories().stream()
                                    .map(BaseEntity::getId)
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
            return Collections.emptyList();
        }


        // Perform the search
        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        log.info(String.format("Public search [%s] returns %d of %d messages in %d ms",
                params.toString(), searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));

        DataFilter filter = Message.MESSAGE_DETAILS_FILTER.lang(language);
        String baseUri = app.getBaseUri();

        return searchResult
                .map(m -> {
                    MessageVo message = m.toVo(MessageVo.class, filter);

                    // If "externalize" is set, rewrite all links to make them external
                    if (externalize) {
                        externalizeMessage(message, m.getRepoPath(), baseUri);
                    }

                    return message;
                })
                .getData();
    }


    /** rewrite all links to make them external URLs **/
    private void externalizeMessage(MessageVo message, String repoPath, String baseUri) {
        if (message.getParts() != null) {
            String from = concat("/rest/repo/file", repoPath);
            String to = concat(baseUri, from);
            message.getParts().forEach(mp -> mp.rewriteRepoPath(from, to));
        }
        if (message.getAttachments() != null) {
            String to = concat(baseUri, "rest/repo/file", repoPath);
            message.getAttachments().forEach(att -> att.rewriteRepoPath(repoPath, to));
        }

    }


    /** Concatenates the URI components **/
    private String concat(String... paths) {
        StringBuilder result = new StringBuilder();
        if (paths != null) {
            Arrays.stream(paths)
                    .filter(StringUtils::isNotBlank)
                    .forEach(p -> {
                        if (result.length() > 0 && !result.toString().endsWith("/") && !p.startsWith("/")) {
                            result.append("/");
                        } else if (result.toString().endsWith("/") && p.startsWith("/")) {
                            p = p.substring(1);
                        }
                        result.append(p);
                    });
        }
        return result.toString();
    }

}
