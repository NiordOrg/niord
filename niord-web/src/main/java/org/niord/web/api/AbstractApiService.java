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
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.model.BaseEntity;
import org.niord.model.DataFilter;
import org.niord.model.search.PagedSearchParamsVo;
import org.niord.model.search.PagedSearchResultVo;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.inject.Inject;
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

    /**
     * Returns all published messages.
     * Optionally, filter by a geometry defined by the WKT (well-known text) parameter.
     */
    public List<MessageVo> search(String language, String domainId, Set<String> messageSeries, Set<MainType> mainTypes, String wkt) throws Exception {

        MessageSearchParams params = new MessageSearchParams();
        params.language(language)
                .statuses(Collections.singleton(Status.PUBLISHED))
                .mainTypes(mainTypes)
                .extent(wkt)
                .includeGeneral(Boolean.TRUE) // Messages without a geometry may be included if WKT specified
                .sortBy("AREA")
                .sortOrder(PagedSearchParamsVo.SortOrder.ASC);

        // Check if a domain has been specified
        if (StringUtils.isNotBlank(domainId)) {
            Domain domain = domainService.findByDomainId(domainId);
            if (domain != null) {
                // Add the message series of the domain as a filter
                params.seriesIds(
                    domain.getMessageSeries().stream()
                        .map(MessageSeries::getSeriesId)
                        .collect(Collectors.toSet())
                );

                // Set the areas to be the ones defined by the domain
                params.areaIds(
                        domain.getAreas().stream()
                                .map(BaseEntity::getId)
                                .collect(Collectors.toSet())
                );

                // Set the categories to be the ones defined by the domain
                params.categoryIds(
                        domain.getCategories().stream()
                                .map(BaseEntity::getId)
                                .collect(Collectors.toSet())
                );
            }
        }

        // Check if specific message series has been specified
        if (messageSeries != null && !messageSeries.isEmpty()) {
            params.getSeriesIds().addAll(messageSeries);
        }

        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        log.info(String.format("Public search [%s] returns %d of %d messages in %d ms",
                params.toString(), searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));

        DataFilter filter = DataFilter.get()
                .lang(language)
                .fields("Message.geometry", "Message.details", "Area.parent", "Category.parent");

        return searchResult
                .map(m -> m.toVo(filter))
                .getData();
    }
}
