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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.model.DataFilter;
import org.niord.model.PagedSearchParamsVo.SortOrder;
import org.niord.model.PagedSearchResultVo;
import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A public REST API for accessing Niord data.
 */
@Api(value = "/api/v1", description = "Public API for accessing the Niord NW-NM system")
@Path("/api/v1")
@Stateless
public class ApiRestService {

    @Inject
    Logger log;

    @Inject
    MessageService messageService;


    /**
     * Returns all published messages.
     * Optionally, filter by a geometry defined by the WKT (well-known text) parameter.
     */
    @ApiOperation(value = "Returns the public NW and NM messages",
            response = MessageVo.class,
            responseContainer = "List")
    @GET
    @Path("/messages")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageVo> search(
            @ApiParam(value = "Two-letter ISO 639-1 language code", example="en")
            @QueryParam("lang") String language,

            @ApiParam(value = "Either NW (navigational warnings) or NM (notices to mariners)", example="NW")
            @QueryParam("mainType") Set<MainType> mainTypes,

            @ApiParam(value = "Well-Known Text for geographical extent", example="POLYGON((10 55, 10 56, 11 56, 11 55, 10 55))")
            @QueryParam("wkt") String wkt

    ) throws Exception {

        MessageSearchParams params = new MessageSearchParams();
        params.language(language)
                .statuses(Collections.singleton(Status.PUBLISHED))
                .mainTypes(mainTypes)
                .extent(wkt)
                .includeGeneral(Boolean.TRUE) // Messages without a geometry may be included if WKT specified
                .sortBy("AREA")
                .sortOrder(SortOrder.ASC);

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
