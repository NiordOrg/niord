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
import org.niord.core.area.*;
import org.niord.core.area.vo.SystemAreaVo;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.ChartService;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.*;
import org.niord.core.model.BaseEntity;
import org.niord.core.schedule.vo.FiringAreaPeriodsVo;
import org.niord.core.service.BaseService;
import org.niord.core.util.TimeUtils;
import org.niord.model.DataFilter;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.util.*;
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
@RequestScoped
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

    @Inject
    DomainService domainService;

    @Inject
    MessageSeriesService messageSeriesService;


    /***************************************/
    /** Firing Schedules                  **/
    /***************************************/


    /**
     * Returns all firing schedules
     *
     * @return all firing schedules
     */
    public List<FiringSchedule> getFiringSchedules() {
        return getAll(FiringSchedule.class);
    }


    /**
     * Returns the firing schedule with the given ID, or null if not found
     *
     * @param id the ID
     * @return the firing schedule with the given ID, or null if not found
     */
    public FiringSchedule findById(Integer id) {
        return getByPrimaryKey(FiringSchedule.class, id);
    }


    /**
     * Creates a new firing schedule based on the schedule template
     *
     * @param schedule the firing schedule to create
     * @return the created firing schedule
     */
    @Transactional
    public FiringSchedule createFiringSchedule(FiringSchedule schedule) {
        if (schedule.isPersisted()) {
            throw new IllegalArgumentException("Cannot create schedule with existing ID " + schedule.getId());
        }

        // Substitute the message series with the persisted ones
        schedule.setTargetMessageSeries(messageSeriesService.findBySeriesId(schedule.getTargetMessageSeries().getSeriesId()));

        // Substitute the target domain with the persisted ones
        schedule.setTargetDomain(domainService.findByDomainId(schedule.getTargetDomain().getDomainId()));

        // Substitute the domain with the persisted ones
        Domain domain = domainService.findByDomainId(schedule.getDomain().getDomainId());
        schedule.setDomain(domain);
        domain.setFiringSchedule(schedule);

        schedule = saveEntity(schedule);

        return schedule;
    }


    /**
     * Updates the firing schedule data from the schedule template
     *
     * @param schedule the firing schedule to update
     * @return the updated firing schedule
     */
    @Transactional
    public FiringSchedule updateFiringSchedule(FiringSchedule schedule) {
        FiringSchedule original = findById(schedule.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing schedule " + schedule.getId());
        }

        // Substitute the domain with the persisted ones
        Domain domain = domainService.findByDomainId(schedule.getDomain().getDomainId());
        if (!Objects.equals(original.getDomain().getDomainId(), domain.getDomainId())) {
            original.getDomain().setFiringSchedule(null);
        }
        original.setDomain(domain);
        domain.setFiringSchedule(original);

        // Substitute the target domain with the persisted ones
        original.setTargetDomain(domainService.findByDomainId(schedule.getTargetDomain().getDomainId()));

        // Substitute the message series with the persisted ones
        original.setTargetMessageSeries(messageSeriesService.findBySeriesId(schedule.getTargetMessageSeries().getSeriesId()));

        original.setScheduleDays(schedule.getScheduleDays());
        original.setActive(schedule.isActive());

        return saveEntity(original);
    }


    /**
     * Deletes the firing schedule
     *
     * @param id the ID of the firing schedule to delete
     * @noinspection all
     */
    @Transactional
    public boolean deleteFiringSchedule(Integer id) {

        FiringSchedule schedule = findById(id);
        if (schedule != null) {
            schedule.getDomain().setFiringSchedule(null);
            remove(schedule);
            return true;
        }
        return false;
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
    public List<FiringAreaPeriodsVo> searchFiringAreaPeriods(Date date, String query, Set<Integer> areaIds, boolean inactive, String lang) {

        Set<Boolean> activeSet = inactive
            ? new HashSet<>(Arrays.asList(TRUE, FALSE))
            : Collections.singleton(TRUE);

        // Adjust the date for the time-zone of the current domain
        Domain domain = domainService.currentDomain();
        TimeZone timeZone = domain != null ? domain.timeZone() : null;
        Date fromDate = TimeUtils.resetTime(date, timeZone);
        Date toDate = TimeUtils.endOfDay(date, timeZone);

        // Find the firing periods
        List<FiringPeriod> fps = em.createNamedQuery("FiringPeriod.findByDateInterval", FiringPeriod.class)
                .setParameter("active", activeSet)
                .setParameter("fromDate", fromDate)
                .setParameter("toDate", toDate)
                .getResultList();

        // Look up all firing areas for the current domain
        DataFilter filter = DataFilter.get()
                .fields(DataFilter.PARENT, DataFilter.DETAILS, DataFilter.GEOMETRY)
                .lang(lang);

        AreaSearchParams param = new AreaSearchParams()
                .name(query)
                .domain(domain == null ? null : domain.getDomainId())
                .areaIds(areaIds)
                .inactive(inactive)
                .type(AreaType.FIRING_AREA);
        param.sortBy(TREE_SORT_ORDER).sortOrder(ASC);

        List<FiringAreaPeriodsVo> result = areaService.searchAreas(param).stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .map(FiringAreaPeriodsVo::new)
                .collect(Collectors.toList());

        Map<Integer, FiringAreaPeriodsVo> areaLookup = result.stream()
                .collect(Collectors.toMap(s -> s.getArea().getId(), Function.identity()));

        // Group the firing periods by area
        for (FiringPeriod fp : fps) {
            FiringAreaPeriodsVo firingAreaPeriods = areaLookup.get(fp.getArea().getId());
            if (firingAreaPeriods != null) {
                firingAreaPeriods.getFiringPeriods().add(fp.toVo());
            }
        }

        return result;
    }


    /**
     * Fetches the firing area periods, i.e. the area and its firing periods on the specified date, and null if not found
     * @param areaId the id of the area
     * @param date the date
     * @param lang the language to filter areas by
     * @return the firing area periods, and null if not found
     */
    public FiringAreaPeriodsVo getFiringAreaPeriods(Integer areaId, Date date, String lang) {

        // TODO: Not very efficient
        return searchFiringAreaPeriods(date, null, null, true, lang).stream()
                .filter(fap -> Objects.equals(fap.getArea().getId(), areaId))
                .findFirst()
                .orElse(null);
    }


    /**
     * Updates the firing area periods, i.e. the list of firing periods for an area at the given date
     * @param firingAreaPeriods the area to update
     * @param date the date to update the firing periods for
     * @param lang the language to filter areas by
     * @return the updated firing area periods
     */
    public FiringAreaPeriodsVo updateFiringAreaPeriodsForDate(FiringAreaPeriodsVo firingAreaPeriods, Date date, String lang) {

        // Fetch the current firing periods for the area
        FiringAreaPeriodsVo currentFiringPeriods = getFiringAreaPeriods(firingAreaPeriods.getArea().getId(), date, lang);
        if (currentFiringPeriods == null) {
            throw new WebApplicationException("No firing area " + firingAreaPeriods.getArea().getId(), 400);
        }

        Area area = areaService.getAreaDetails(firingAreaPeriods.getArea().getId());
        List<FiringPeriod> updatedFps = firingAreaPeriods.getFiringPeriods().stream()
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
        currentFiringPeriods.getFiringPeriods().stream()
                .filter(fp -> !updatedFpIds.contains(fp.getId()))
                .forEach(fp -> deleteFiringPeriod(fp.getId()));

        // Return the updated firing area
        return getFiringAreaPeriods(area.getId(), date, lang);
    }


    /***************************************/
    /** Firing Periods                    **/
    /***************************************/


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
    @Transactional
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
    @Transactional
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
    @Transactional
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
    @Transactional
    public List<Message> generateFiringAreaMessages(MessageSeries messageSeries, MessageTag messageTag) {

        // Search for active firing areas for the current domain
        Domain domain = domainService.currentDomain();
        AreaSearchParams param = new AreaSearchParams()
                .inactive(false)
                .domain(domain != null ? domain.getDomainId() : null)
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
