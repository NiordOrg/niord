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

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.vo.SystemMessageSeriesVo;
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
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for managing message series.
 */
@Path("/message-series")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class MessageSeriesRestService {

    @Inject
    Logger log;

    @Inject
    MessageSeriesService messageSeriesService;


    /** Returns all message series */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemMessageSeriesVo> getAllMessageSeries(
            @QueryParam("messageNumbers") @DefaultValue("false") boolean messageNumbers) {
        List<SystemMessageSeriesVo> messageSeries = messageSeriesService.getAllMessageSeries().stream()
                .map(ms -> ms.toVo(SystemMessageSeriesVo.class))
                .collect(Collectors.toList());

        if (messageNumbers) {
            // Load the next message number in the current year
            int year = Calendar.getInstance().get(Calendar.YEAR);
            messageSeries.forEach(ms -> {
                int nextMessageNumber = messageSeriesService.getNextMessageNumber(ms.getSeriesId(), year);
                ms.setNextMessageNumber(nextMessageNumber);
            });
        }

        return messageSeries;
    }


    /** Returns the message series with the given comma-separated ID's */
    @GET
    @Path("/search/{seriesIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemMessageSeriesVo> getMessageSeries(
            @PathParam("seriesIds") String seriesIds,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        return messageSeriesService.findByIds(seriesIds.split(",")).stream()
                .limit(limit)
                .map(ms -> ms.toVo(SystemMessageSeriesVo.class))
                .collect(Collectors.toList());
    }


    /** Searches message series based on the given search parameters */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemMessageSeriesVo> searchMessageSeries(
            @QueryParam("name") @DefaultValue("") String name,
            @QueryParam("domain") @DefaultValue("false") String domainId,
            @QueryParam("limit") @DefaultValue("100") int limit) {
        return messageSeriesService.searchMessageSeries(name, domainId, limit).stream()
                .map(ms -> ms.toVo(SystemMessageSeriesVo.class))
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
    public SystemMessageSeriesVo createMessageSeries(SystemMessageSeriesVo seriesVo) throws Exception {
        log.info("Creating message series " + seriesVo);
        return messageSeriesService.createMessageSeries(new MessageSeries(seriesVo)).toVo(SystemMessageSeriesVo.class);
    }


    /** Updates an existing message series */
    @PUT
    @Path("/series/{seriesId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public SystemMessageSeriesVo updateMessageSeries(@PathParam("seriesId") String seriesId, SystemMessageSeriesVo seriesVo) throws Exception {
        if (!Objects.equals(seriesId, seriesVo.getSeriesId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating message series " + seriesVo);
        return messageSeriesService.updateMessageSeries(new MessageSeries(seriesVo)).toVo(SystemMessageSeriesVo.class);
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
        messageSeriesService.deleteMessageSeries(seriesId);
    }


    /** Returns the next message series number for the given year */
    @GET
    @Path("/series/{seriesIds}/number/{year}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public Integer getNextMessageSeriesNumber(
            @PathParam("seriesIds") String seriesIds,
            @PathParam("year") int year) {
        return messageSeriesService.getNextMessageNumber(seriesIds, year);
    }


    /** Sets the next message series number for the given year */
    @PUT
    @Path("/series/{seriesIds}/number/{year}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({ "sysadmin" })
    public Integer setNextMessageSeriesNumber(
            @PathParam("seriesIds") String seriesIds,
            @PathParam("year") int year,
            Integer nextNumber) {
        log.info("Updating next number for message series " + seriesIds + " to " + nextNumber);
        messageSeriesService.setNextMessageNumber(seriesIds, year, nextNumber);
        return nextNumber;
    }


    /** Computes the next message series number for the given year */
    @GET
    @Path("/series/{seriesIds}/compute-number/{year}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public Integer computeNextMessageSeriesNumber(
            @PathParam("seriesIds") String seriesIds,
            @PathParam("year") int year) {
        return messageSeriesService.computeNextMessageNumber(seriesIds, year);
    }
}
