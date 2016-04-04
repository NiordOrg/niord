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
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.model.vo.MessageSeriesVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing message series.
 */
@Path("/message-series")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class MessageSeriesRestService {

    @Inject
    Logger log;

    @Inject
    MessageService messageService;


    /****************************/
    /** Message Series         **/
    /****************************/

    /** Returns all message series */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageSeriesVo> getAllMessageSeries() {
        return messageService.getAllMessageSeries().stream()
                .map(MessageSeries::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the message series with the given comma-separated ID's */
    @GET
    @Path("/search/{seriesIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageSeriesVo> getMessageSeries(
            @PathParam("seriesIds") String seriesIds,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        return messageService.findByIds(seriesIds.split(",")).stream()
                .limit(limit)
                .map(MessageSeries::toVo)
                .collect(Collectors.toList());
    }


    /** Searches message series based on the given search parameters */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageSeriesVo> searchMessageSeries(
            @QueryParam("name") @DefaultValue("") String name,
            @QueryParam("limit") @DefaultValue("100") int limit) {
        return messageService.searchMessageSeries(name, limit).stream()
                .map(MessageSeries::toVo)
                .collect(Collectors.toList());
    }


    /** Creates a new message series */
    @POST
    @Path("/series/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public MessageSeriesVo createMessageSeries(MessageSeriesVo seriesVo) throws Exception {
        log.info("Creating message series " + seriesVo);
        return messageService.createMessageSeries(new MessageSeries(seriesVo)).toVo();
    }


    /** Updates an existing message series */
    @PUT
    @Path("/series/{seriesId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public MessageSeriesVo updateMessageSeries(@PathParam("seriesId") String seriesId, MessageSeriesVo seriesVo) throws Exception {
        if (!Objects.equals(seriesId, seriesVo.getSeriesId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating message series " + seriesVo);
        return messageService.updateMessageSeries(new MessageSeries(seriesVo)).toVo();
    }


    /** Deletes an existing message series */
    @DELETE
    @Path("/series/{seriesId}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public void deleteMessageSeries(@PathParam("seriesId") String seriesId) throws Exception {
        log.info("Deleting message series " + seriesId);
        messageService.deleteMessageSeries(seriesId);
    }

}
