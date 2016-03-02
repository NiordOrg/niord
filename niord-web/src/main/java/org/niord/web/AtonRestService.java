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
import org.niord.core.aton.AtonNode;
import org.niord.core.aton.AtonSearchParams;
import org.niord.core.aton.AtonService;
import org.niord.model.PagedSearchResultVo;
import org.niord.model.vo.aton.AtonNodeVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.List;

/**
 * REST interface for accessing AtoNs.
 */
@Path("/atons")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class AtonRestService {

    @Inject
    Logger log;

    @Inject
    AtonService atonService;

    @Inject
    TestRestService testRestService;


    /** Returns the AtoN with the given comma-separated IDs */
    @GET
    @Path("/{atonUids}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonNode> getAtons(@PathParam("atonUids") String atonUids) {
        return atonService.findByAtonUids(atonUids.split(","));
    }


    /** Returns the AtoNs within the given bounds */
    @GET
    @Path("/search-name")
    @Consumes("application/json")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonNodeVo> searchAtons(
            @QueryParam("name") @DefaultValue("") String name,
            @QueryParam("maxAtonNo") @DefaultValue("1000") int maxAtonNo
    ) {
        AtonSearchParams param = new AtonSearchParams();
        param.setName(name);
        param.maxSize(maxAtonNo);

        return atonService.search(param)
                .map(AtonNode::toVo)
                .getData();
    }


    /** Returns the AtoNs within the given bounds */
    @GET
    @Path("/search")
    @Consumes("application/json")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PagedSearchResultVo<AtonNodeVo> search(
            @QueryParam("name") String name,
            @QueryParam("chart") String[] charts,
            @QueryParam("minLat") @DefaultValue("-90") double minLat,
            @QueryParam("minLon") @DefaultValue("-180") double minLon,
            @QueryParam("maxLat") @DefaultValue("90") double maxLat,
            @QueryParam("maxLon") @DefaultValue("180") double maxLon,
            @QueryParam("maxAtonNo") Integer maxAtonNo,
            @QueryParam("emptyOnOverflow") @DefaultValue("false") boolean emptyOnOverflow
    ) {

        AtonSearchParams param = new AtonSearchParams();
        param.setName(name);
        param.setExtent(minLat, minLon, maxLat, maxLon);
        param.setChartNumbers(charts);
        param.maxSize(maxAtonNo);

        PagedSearchResultVo<AtonNode> atons = atonService.search(param);

        // For efficiency reasons the client may not want any data returned if the
        // result is larger than maxAtonNo
        if (maxAtonNo != null && atons.getTotal() > maxAtonNo) {
            atons.setData(null);
        }

        return atons.map(AtonNode::toVo);
    }
}
