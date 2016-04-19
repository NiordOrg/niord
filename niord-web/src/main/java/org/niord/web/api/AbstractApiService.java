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

import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.model.DataFilter;
import org.niord.model.PagedSearchParamsVo;
import org.niord.model.PagedSearchResultVo;
import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class for the API services
 */
public abstract class AbstractApiService {

    @Inject
    Logger log;

    @Inject
    MessageService messageService;

    /**
     * Returns all published messages.
     * Optionally, filter by a geometry defined by the WKT (well-known text) parameter.
     */
    public List<MessageVo> search(String language, Set<MainType> mainTypes, String wkt) throws Exception {

        MessageSearchParams params = new MessageSearchParams();
        params.language(language)
                .statuses(Collections.singleton(Status.PUBLISHED))
                .mainTypes(mainTypes)
                .extent(wkt)
                .includeGeneral(Boolean.TRUE) // Messages without a geometry may be included if WKT specified
                .sortBy("AREA")
                .sortOrder(PagedSearchParamsVo.SortOrder.ASC);

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
