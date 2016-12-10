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

package org.niord.core.schedule;

import org.apache.commons.lang.StringUtils;
import org.niord.core.area.Area;
import org.niord.core.area.AreaDesc;
import org.niord.core.area.AreaSearchParams;
import org.niord.core.area.AreaService;
import org.niord.core.area.vo.SystemAreaVo;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.ChartService;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.model.BaseEntity;
import org.niord.core.schedule.vo.FiringScheduleVo;
import org.niord.core.service.BaseService;
import org.niord.core.util.TimeUtils;
import org.niord.model.DataFilter;
import org.niord.model.message.AreaType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.niord.core.area.AreaSearchParams.TREE_SORT_ORDER;
import static org.niord.model.message.MainType.NM;
import static org.niord.model.search.PagedSearchParamsVo.SortOrder.ASC;

/**
 * Business interface for accessing Niord firing areas
 */
@Stateless
@SuppressWarnings("unused")
public class FiringScheduleService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    MessageService messageService;

    @Inject
    AreaService areaService;

    @Inject
    ChartService chartService;

    @Inject
    CategoryService categoryService;


    /***************************************/
    /** Firing Schedules                  **/
    /***************************************/


    /**
     * Fetches the firing schedules, i.e. all firing areas and their firing periods that matches the parameters
     *
     * @param date the date
     * @param query a search query
     * @param areaIds area subtrees to search
     * @param inactive whether to include inactive areas or not
     * @param lang the language to filter areas by
     * @return the firing schedules that matches the parameters
     */
    public List<FiringScheduleVo> getFiringSchedules(Date date, String query, Set<Integer> areaIds, boolean inactive, String lang) {

        Set<Boolean> activeSet = inactive
            ? new HashSet<>(Arrays.asList(TRUE, FALSE))
            : Collections.singleton(TRUE);

        // Find the firing periods
        List<FiringPeriod> fps = em.createNamedQuery("FiringPeriod.findByDateInterval", FiringPeriod.class)
                .setParameter("active", activeSet)
                .setParameter("fromDate", TimeUtils.resetTime(date))
                .setParameter("toDate", TimeUtils.endOfDay(date))
                .getResultList();

        // Look up all firing areas
        DataFilter filter = DataFilter.get()
                .fields(DataFilter.PARENT, DataFilter.DETAILS, DataFilter.GEOMETRY)
                .lang(lang);

        AreaSearchParams param = new AreaSearchParams()
                .name(query)
                .areaIds(areaIds)
                .inactive(inactive)
                .type(AreaType.FIRING_AREA);
        param.sortBy(TREE_SORT_ORDER).sortOrder(ASC);

        List<FiringScheduleVo> result = areaService.searchAreas(param).stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .map(FiringScheduleVo::new)
                .collect(Collectors.toList());

        Map<Integer, FiringScheduleVo> scheduleLookup = result.stream()
                .collect(Collectors.toMap(s -> s.getArea().getId(), Function.identity()));

        // Group the firing periods by area
        for (FiringPeriod fp : fps) {
            FiringScheduleVo schedule = scheduleLookup.get(fp.getArea().getId());
            if (schedule != null) {
                schedule.getFiringPeriods().add(fp.toVo());
            }
        }

        return result;
    }


    /**
     * Fetches the firing schedule, i.e. the area and its firing periods on the specified date, and null if not found
     * @param areaId the id of the area
     * @param date the date
     * @param lang the language to filter areas by
     * @return the firing schedule, and null if not found
     */
    public FiringScheduleVo getFiringSchedule(Integer areaId, Date date, String lang) {

        // TODO: Not very efficient
        return getFiringSchedules(date, null, null, true, lang).stream()
                .filter(schedule -> Objects.equals(schedule.getArea().getId(), areaId))
                .findFirst()
                .orElse(null);
    }


    /**
     * Updates the firing schedule, i.e. the list of firing periods for an area at the given date
     * @param firingSchedule the area to update
     * @param date the date to update the schedule for
     * @param lang the language to filter areas by
     * @return the updated area
     */
    public FiringScheduleVo updateFiringScheduleForDate(FiringScheduleVo firingSchedule, Date date, String lang) {

        // Fetch the current schedule for the area
        FiringScheduleVo currentSchedule = getFiringSchedule(firingSchedule.getArea().getId(), date, lang);
        if (currentSchedule == null) {
            throw new WebApplicationException("No firing area " + firingSchedule.getArea().getId(), 400);
        }

        Area area = areaService.getAreaDetails(firingSchedule.getArea().getId());
        List<FiringPeriod> updatedFps = firingSchedule.getFiringPeriods().stream()
                .map(fp -> new FiringPeriod(area, fp))
                .collect(Collectors.toList());

        // Handle new firing periods
        updatedFps.stream()
                .filter(BaseEntity::isNew)
                .forEach(this::addFiringPeriod);

        // Handle updated firing periods
        updatedFps.stream()
                .filter(BaseEntity::isPersisted)
                .forEach(this::updateFiringPeriod);

        // Create a look-up of all updated (i.e. existing firing periods)
        Set<Integer> updatedFpIds = updatedFps.stream()
                .filter(BaseEntity::isPersisted)
                .map(BaseEntity::getId)
                .collect(Collectors.toSet());

        // Handle deleted firing periods
        currentSchedule.getFiringPeriods().stream()
                .filter(fp -> !updatedFpIds.contains(fp.getId()))
                .forEach(fp -> deleteFiringPeriod(fp.getId()));

        // Return the updated firing area
        return getFiringSchedule(area.getId(), date, lang);
    }


    /***************************************/
    /** Firing Periods                    **/
    /***************************************/


    /**
     * Returns any existing firing period with the given area and date interval. Return null if none is found
     * @param area the area
     * @param firingPeriodFromDate the from date
     * @param firingPeriodToDate the to date
     * @return any existing firing period with the given area and date interval
     */
    public FiringPeriod findFiringPeriod(Area area, Date firingPeriodFromDate, Date firingPeriodToDate) {
        return em.createNamedQuery("FiringPeriod.findByAreaAndInterval", FiringPeriod.class)
                .setParameter("area", area)
                .setParameter("fromDate", TimeUtils.resetSeconds(firingPeriodFromDate))
                .setParameter("toDate", TimeUtils.resetSeconds(firingPeriodToDate))
                .getResultList().stream()
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns the list of legacy firing periods
     * @return the list of legacy firing periods
     */
    public List<FiringPeriod> getAllLegacyFiringPeriods() {
        return em.createNamedQuery("FiringPeriod.findLegacyFiringPeriods", FiringPeriod.class)
                .getResultList();
    }


    /**
     * Creates a new firing period based on the given template
     * @param templatePeriod the template firing period
     * @return the persisted firing period
     */
    public FiringPeriod addFiringPeriod(FiringPeriod templatePeriod) {
        // Validation
        if (templatePeriod.isPersisted()) {
            throw new IllegalArgumentException("Cannot create existing firing period");
        }
        if (!templatePeriod.firingPeriodDefined()) {
            throw new IllegalArgumentException("Firing period must defined area, from- and to-dates");
        }

        return saveEntity(templatePeriod);
    }


    /**
     * Updates firing period based on the given template
     * @param templatePeriod the template firing period
     * @return the persisted firing period
     */
    public FiringPeriod updateFiringPeriod(FiringPeriod templatePeriod) {
        // Validation
        if (templatePeriod.isNew()) {
            throw new IllegalArgumentException("Cannot update non-existing firing period");
        }
        if (!templatePeriod.firingPeriodDefined()) {
            throw new IllegalArgumentException("Firing period must defined area, from- and to-dates");
        }

        FiringPeriod originalFp = getByPrimaryKey(FiringPeriod.class, templatePeriod.getId());
        originalFp.updateFiringPeriod(templatePeriod);

        return saveEntity(originalFp);
    }


    /**
     * Deletes the firing period with the given ID
     * @param id the firing period ID
     */
    public boolean deleteFiringPeriod(Integer id) {

        FiringPeriod firingPeriod = getByPrimaryKey(FiringPeriod.class, id);
        if (firingPeriod != null) {
            remove(firingPeriod);
            return true;
        }
        return false;
    }


    /***************************************/
    /** Firing Area Template Messages     **/
    /***************************************/


    /**
     * Generates a firing area message template for all active firing areas.
     * @param messageSeries the message series to use
     * @param messageTag the message tag to assign the messages to
     * @return the list of newly created firing area message templates
     */
    public List<Message> generateFiringAreaMessages(MessageSeries messageSeries, MessageTag messageTag) {

        // Search for active firing areas
        AreaSearchParams param = new AreaSearchParams()
                .inactive(false)
                .type(AreaType.FIRING_AREA);
        param.sortBy(TREE_SORT_ORDER).sortOrder(ASC);

        List<Area> firingAreas = areaService.searchAreas(param);
        if (firingAreas.isEmpty()) {
            log.info("No active firing areas found - skip generating messages");
            return Collections.emptyList();
        }

        List<Message> faMessages = new ArrayList<>();
        for (Area area : firingAreas) {

            Message message = new Message();
            message.setMessageSeries(messageSeries);
            message.setMainType(messageSeries.getMainType());
            message.setType(messageSeries.getMainType() == NM ? Type.MISCELLANEOUS_NOTICE : Type.LOCAL_WARNING);
            message.setStatus(Status.DRAFT);
            message.setShortId("FA-" + area.getDescs().get(0).getName().replace(" ", "-"));
            message.setAutoTitle(true);
            message.getAreas().add(area);

            MessagePart part = message.addPart(new MessagePart(MessagePartType.DETAILS));
            if (area.getGeometry() != null) {

                // Compute the charts for the message
                message.getCharts().addAll(chartService.getIntersectingCharts(area.getGeometry()));

                // Copy the area geometry to the message
                FeatureCollection featureCollection = new FeatureCollection();
                Feature feature = new Feature();
                featureCollection.addFeature(feature);
                feature.setGeometry(area.getGeometry());
                area.getDescs().stream()
                        .filter(desc -> StringUtils.isNotBlank(desc.getName()))
                        .forEach(desc -> feature.getProperties().put("name:" + desc.getLang(), desc.getName()));
                part.setGeometry(featureCollection);
            }

            // Update the title line
            for (AreaDesc desc : area.getDescs()) {
                message.checkCreateDesc(desc.getLang());
            }
            message.updateMessageTitle();

            // Categories
            Category firingExercise = categoryService.getFiringExercisesCategory();
            if (firingExercise != null) {
                message.getCategories().add(firingExercise);
            }

            // Save the message
            message = messageService.saveMessage(message);

            // Add to message tag
            if (messageTag != null) {
                messageTag.getMessages().add(message);
                message.getTags().add(messageTag);
            }


            faMessages.add(message);
        }

        if (messageTag != null) {
            messageTag.updateMessageCount();
            saveEntity(messageTag);
        }

        return faMessages;
    }
}
