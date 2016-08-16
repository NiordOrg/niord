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
    public List<MessageVo> search(String language, String domainId, Set<MainType> mainTypes, String wkt) throws Exception {

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
            Domain domain = domainService.findByClientId(domainId);
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

        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        log.info(String.format("Public search [%s] returns %d of %d messages in %d ms",
                params.toString(), searchResult.getData().size(), searchResult.getTotal(), System.currentTimeMillis() - t0));

        DataFilter filter = DataFilter.get()
                .lang(language)
                .fields(DataFilter.DETAILS, DataFilter.GEOMETRY, "Area.parent", "Category.parent");

        return searchResult
                .map(m -> m.toVo(filter))
                .getData();
    }
}
