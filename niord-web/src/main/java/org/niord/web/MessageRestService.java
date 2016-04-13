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
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
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
import java.util.Set;

/**
 * REST interface for managing messages.
 */
@Path("/messages")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class MessageRestService {

    @Inject
    Logger log;

    @Inject
    MessageService messageService;


    /***************************
     * Search functionality
     ***************************/

    @GET
    @Path("/message/{id}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageVo getMessage(
            @PathParam("id") Integer id,
            @QueryParam("lang") String language) throws Exception {

        DataFilter filter = DataFilter.get()
                .lang(language)
                .fields(DataFilter.DETAILS, DataFilter.GEOMETRY, "Area.parent", "Category.parent");

        return messageService.findById(id).toVo(filter);
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
            @QueryParam("status") Set<Status> statuses,
            @QueryParam("mainType") Set<MainType> mainTypes,
            @QueryParam("type") Set<Type> types,
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
