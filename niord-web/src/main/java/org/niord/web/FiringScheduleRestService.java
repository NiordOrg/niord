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
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;
import org.niord.core.schedule.FiringSchedule;
import org.niord.core.schedule.FiringScheduleService;
import org.niord.core.schedule.vo.FiringAreaPeriodsVo;
import org.niord.core.schedule.vo.FiringScheduleVo;
import org.niord.model.IJsonSerializable;
import org.niord.model.message.MessageVo;
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
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * REST interface for accessing and updating firing schedules.
 */
@Path("/firing-schedules")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class FiringScheduleRestService {

    @Inject
    Logger log;

    @Inject
    FiringScheduleService firingScheduleService;

    @Inject
    DomainService domainService;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    MessageTagService messageTagService;


    /***************************************/
    /** Firing Schedules                  **/
    /***************************************/


    /**
     * Returns all firing schedules
     * @return all firing schedules
     */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<FiringScheduleVo> getFiringSchedules() {
        return firingScheduleService.getFiringSchedules().stream()
                .map(FiringSchedule::toVo)
                .collect(Collectors.toList());
    }


    /**
     * Creates a new firing schedule based on the schedule template
     * @param schedule the firing schedule to create
     * @return the created firing schedule
     */
    @POST
    @Path("/firing-schedule/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"sysadmin"})
    public FiringScheduleVo createFiringSchedule(FiringScheduleVo schedule) {

        log.info("Create new firing schedule " + schedule);
        return firingScheduleService.createFiringSchedule(new FiringSchedule(schedule)).toVo();
    }


    /**
     * Updates the firing schedule data from the schedule template
     * @param schedule the firing schedule to update
     * @return the updated firing schedule
     */
    @PUT
    @Path("/firing-schedule/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"sysadmin"})
    public FiringScheduleVo updateFiringSchedule(@PathParam("id") Integer id, FiringScheduleVo schedule) {
        if (!Objects.equals(id, schedule.getId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating firing schedule " + schedule);
        return firingScheduleService.updateFiringSchedule(new FiringSchedule(schedule)).toVo();
    }


    /**
     * Deletes the firing schedule
     * @param id the ID of the firing schedule to delete
     */
    @DELETE
    @Path("/firing-schedule/{id}")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public void deleteFiringSchedule(@PathParam("id") Integer id) {

        log.info("Updating firing schedule " + id);
        firingScheduleService.deleteFiringSchedule(id);
    }


    /***************************************/
    /** Firing Area Periods               **/
    /***************************************/


    /**
     * Fetches the firing area periods, i.e. all firing areas and their firing periods that matches the parameters
     *
     * @param date the date
     * @param query a search query
     * @param areaIds area subtrees to search
     * @param inactive whether to include inactive areas or not
     * @param lang the language to filter areas by
     * @return the firing area periods that matches the parameters
     */
    @GET
    @Path("/search-firing-area-periods")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<FiringAreaPeriodsVo> searchFiringAreaPeriods(
            @QueryParam("date") Long date,
            @QueryParam("query") String query,
            @QueryParam("area") Set<Integer> areaIds,
            @QueryParam("inactive") @DefaultValue("false") Boolean inactive,
            @QueryParam("lang") String lang) {

        Date searchDate = date != null ? new Date(date) : new Date();
        return firingScheduleService.searchFiringAreaPeriods(searchDate, query, areaIds, inactive, lang);
    }


    /**
     * Updates the firing area periods, i.e. the list of firing periods for an area at the given date
     * @param firingAreaPeriods the firing area periods to update
     * @param date the date to update the firing periods for
     * @param lang the language to filter areas by
     * @return the updated firing area periods
     */
    @PUT
    @Path("/firing-area-periods")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public FiringAreaPeriodsVo updateFiringAreaPeriodsForDate(
            @QueryParam("date") Long date,
            @QueryParam("lang") String lang,
            FiringAreaPeriodsVo firingAreaPeriods) {

        Objects.requireNonNull(date, "The date must be specified");
        Objects.requireNonNull(firingAreaPeriods.getArea(), "The firing area must be specified");

        log.info("Updating firing area periods for area " + firingAreaPeriods.getArea().getId());
        return firingScheduleService.updateFiringAreaPeriodsForDate(firingAreaPeriods, new Date(date), lang);
    }


    /***************************************/
    /** Firing Area Template Messages     **/
    /***************************************/


    /**
     * Generates a firing area message template for all active firing areas
     * @param params the parameters
     * @return the generated firing area messages
     */
    @POST
    @Path("/generate-firing-area-messages")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"admin"})
    public List<MessageVo> generateFiringAreaMessages(FiringAreaMessageParams params) {

        // Validate the the message series belong to the current domain
        if (!domainService.currentDomain().containsMessageSeries(params.getSeriesId())) {
            throw new WebApplicationException(403);
        }

        MessageSeries messageSeries = messageSeriesService.findBySeriesId(params.getSeriesId());
        MessageTag tag = messageTagService.findTag(domainService.currentDomain(), params.getTagId());

        log.info("Generating firing area message templates. Message Series: "
                + params.getSeriesId() + ". Message Tag: " + params.getTagId());
        return firingScheduleService.generateFiringAreaMessages(messageSeries, tag).stream()
                .map(m -> m.toVo(MessageVo.class, Message.MESSAGE_DETAILS_FILTER))
                .collect(Collectors.toList());
    }



    /**
     * Helper class used when generating firing area template messages
     */
    @SuppressWarnings("unused")
    public static class FiringAreaMessageParams implements IJsonSerializable {
        String seriesId;
        String tagId;

        public String getSeriesId() {
            return seriesId;
        }

        public void setSeriesId(String seriesId) {
            this.seriesId = seriesId;
        }

        public String getTagId() {
            return tagId;
        }

        public void setTagId(String tagId) {
            this.tagId = tagId;
        }
    }
}
