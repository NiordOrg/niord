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
import org.niord.core.area.AreaSearchParams;
import org.niord.core.area.AreaService;
import org.niord.core.dictionary.DictionaryEntry;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.domain.Domain;
import org.niord.core.geojson.FeatureService;
import org.niord.core.message.DateInterval;
import org.niord.core.message.Message;
import org.niord.core.message.MessageDesc;
import org.niord.core.message.MessagePart;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.service.BaseService;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.util.TimeUtils;
import org.niord.model.message.AreaType;
import org.niord.model.message.MainType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Status;
import org.niord.model.message.Type;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.settings.Setting.Type.Integer;

/**
 * The Firing Exercise service generates firing exercise messages based on firing schedules.
 * <p>
 * The service will periodically load all active schedules and perform the following steps:
 * <ul>
 *     <li>Load all firing area messages from the source domain.</li>
 *     <li>Load all future firing periods for the firing areas of the source domain.</li>
 *     <li>Combine the firing area messages and firing periods to generate firing exercise message templates
 *          for the target message series.</li>
 *     <li>Fill out a textual time part of the firing exercise messages based on the time zone of the target domain.</li>
 *     <li>Compare the list of firing exercise message templates with the ones already published in the target domain,
 *          and cancel and publish new messages accordingly.</li>
 * </ul>
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class FiringExerciseService extends BaseService {

    // Default number of schedule days to include in the firing exercise messages
    @Inject
    @Setting(value="firingExerciseScheduleDays",
            defaultValue="30",
            description="Default number of scheduled days to include in generated firing exercises",
            type = Integer)
    private int firingExerciseScheduleDays;

    @Inject
    private Logger log;

    @Inject
    FiringScheduleService firingScheduleService;

    @Inject
    MessageService messageService;

    @Inject
    AreaService areaService;

    @Inject
    FeatureService featureService;

    @Inject
    DictionaryService dictionaryService;

    /**
     * Every night at 3 AM, firing exercises are created and updated according to the active schedules
     */
    @Schedule(persistent = false, hour = "3")
    public void updateFiringExercises() {

        Date today = TimeUtils.resetTime(new Date());

        // Get active schedules
        List<FiringSchedule> activeSchedules = firingScheduleService.getFiringSchedules().stream()
                .filter(FiringSchedule::isActive)
                .collect(Collectors.toList());

        log.info("Processing " + activeSchedules.size() + " active firing schedules");

        for (FiringSchedule schedule : activeSchedules) {

            // Get hold of the relevant firing areas
            List<Area> firingAreas = getFiringAreas(schedule.getDomain());

            // Get firing area message templates
            List<Message> firingAreaMessages = getFiringAreaMessages(
                    schedule.getDomain(),
                    null,
                    firingAreas);

            // Find the firing periods
            int days = schedule.getScheduleDays() != null ? schedule.getScheduleDays() : firingExerciseScheduleDays;
            Date endDate = TimeUtils.add(today, Calendar.DATE, days);
            List<FiringPeriod> firingPeriods = em.createNamedQuery("FiringPeriod.findByAreasAndDateInterval", FiringPeriod.class)
                    .setParameter("areas", firingAreas)
                    .setParameter("fromDate", today)
                    .setParameter("toDate", TimeUtils.endOfDay(endDate))
                    .getResultList();


            // Create (un-persisted) firing exercise messages from the firing area templates and the firing periods
            List<Message> newFiringExerciseMessages = generateFiringExerciseMessages(
                    schedule,
                    firingAreaMessages,
                    firingPeriods);


            // Get the currently published firing exercise messages
            List<Message> currentFiringExerciseMessages = getFiringAreaMessages(
                    schedule.getTargetDomain(),
                    schedule.getTargetMessageSeries(),
                    firingAreas);


            // Persist new messages if they are not already published
            newFiringExerciseMessages.forEach(message
                    ->  checkCreateFiringExerciseMessage(message, currentFiringExerciseMessages));

            // The messages left in the currentFiringExerciseMessages should be cancelled
            currentFiringExerciseMessages.forEach(this::cancelCurrentFiringExerciseMessage);
        }
    }


    /**
     * Checks if the firing exercise message should be created by looking at any existing
     * messages.
     * Also, if an existing message exists, this is removed from the list of current messages.
     * @param message the message to create if it has been updated
     */
    private void checkCreateFiringExerciseMessage(Message message, List<Message> currentFiringExerciseMessages) {
        Area firingArea = getFiringAreaForMessage(message);

        Message currentFiringExerciseMessage = currentFiringExerciseMessages.stream()
                .filter(m -> Objects.equals(firingArea.getId(), getFiringAreaForMessage(m).getId()))
                .findFirst()
                .orElse(null);

        try {
            if (currentFiringExerciseMessage == null || !sameFiringPeriods(message, currentFiringExerciseMessage)) {
                log.info("Creating firing exercise message for area " + firingArea.getDescs().get(0).getName());
                Message msg = messageService.createMessage(message);

                if (currentFiringExerciseMessage != null) {
                    currentFiringExerciseMessages.remove(currentFiringExerciseMessage);
                    cancelCurrentFiringExerciseMessage(currentFiringExerciseMessage);
                }

            } else {
                currentFiringExerciseMessages.remove(currentFiringExerciseMessage);
            }
        } catch (Exception e) {
            log.error("Error creating new firing exercise message for area "
                    + firingArea.getDescs().get(0).getName(), e);
        }
    }


    /**
     * Cancels the given firing exercise message
     * @param message the message to cancel
     */
    private void cancelCurrentFiringExerciseMessage(Message message) {
        try {
            log.info("Cancelling firing exercise message " + message.getUid());
            messageService.updateStatus(message.getUid(), Status.CANCELLED);
        } catch (Exception e) {
            log.error("Error cancelling existing firing exercise message", e);
        }
    }


    /**
     * Checks if the two messages defines the same firing periods
     * @param m1 the first message
     * @param m2 the second message
     * @return if the two messages defines the same firing periods
     */
    private boolean sameFiringPeriods(Message m1, Message m2) {
        List<DateInterval> d1 = m1.getParts().stream()
                .flatMap(p -> p.getEventDates().stream())
                .sorted()
                .collect(Collectors.toList());
        List<DateInterval> d2 = m2.getParts().stream()
                .flatMap(p -> p.getEventDates().stream())
                .sorted()
                .collect(Collectors.toList());

        if (d1.size() == d2.size()) {
            for (int x = 0; x < d1.size(); x++) {
                if (d1.get(x).compareTo(d2.get(x)) != 0) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }


    /** Based on the firing area message templates and the firing periods, generate new firing exercise messages **/
    private List<Message> generateFiringExerciseMessages(
            FiringSchedule schedule,
            List<Message> firingAreaMessages,
            List<FiringPeriod> firingPeriods) {

        List<Message> result = new ArrayList<>();

        for (Message firingAreaMessage : firingAreaMessages) {

            Set<Integer> areaIds = firingAreaMessage.getAreas().stream()
                    .filter(a -> a.getType() == AreaType.FIRING_AREA)
                    .map(BaseEntity::getId)
                    .collect(Collectors.toSet());

            // Get firing periods for the message
            List<FiringPeriod> fps = firingPeriods.stream()
                    .filter(fp -> areaIds.contains(fp.getArea().getId()))
                    .sorted()
                    .collect(Collectors.toList());

            if (fps.isEmpty()) {
                continue;
            }

            // Construct the firing exercise message from the firing area message and firing periods
            Message message = new Message();
            message.assignNewUid();
            message.setMainType(schedule.getTargetMessageSeries().getMainType());
            message.setType(message.getMainType() == MainType.NW ? Type.LOCAL_WARNING : Type.TEMPORARY_NOTICE);
            message.setMessageSeries(schedule.getTargetMessageSeries());
            message.setStatus(Status.PUBLISHED);
            message.getAreas().addAll(firingAreaMessage.getAreas());
            message.getCategories().addAll(firingAreaMessage.getCategories());
            message.getCharts().addAll(firingAreaMessage.getCharts());

            // Copy message description entities and message parts
            MessageVo firingAreaMessageVo = firingAreaMessage.toVo(SystemMessageVo.class, Message.MESSAGE_DETAILS_FILTER);
            firingAreaMessageVo.getDescs().forEach(d -> message.addDesc(new MessageDesc(d)));
            firingAreaMessageVo.getParts()
                    .forEach(p -> {
                        if (p.getGeometry() != null) {
                            p.setGeometry(featureService.copyFeatureCollection(p.getGeometry()));
                        }
                        message.addPart(new MessagePart(p));
                    });

            // Get hold of the "time" message part, or add it as the very first message part
            if (message.partsByType(MessagePartType.TIME).isEmpty()) {
                MessagePart timePart = new MessagePart(MessagePartType.TIME);
                message.getParts().add(0, timePart);
                timePart.setMessage(message);
            }
            MessagePart timePart = message.partsByType(MessagePartType.TIME).get(0);

            // Copy firing periods to event dates
            fps.forEach(fp -> timePart.addEventDates(fp.toDateInterval()));

            // Make sure the message expires after last event date
            message.updateEventDateInterval();
            message.setPublishDateTo(message.getEventDateTo());

            // Format the firing periods as text
            formatTimeDescription(timePart, message.computeLanguages());

            // Get hold of the "details" message part, or create it
            if (message.partsByType(MessagePartType.DETAILS).isEmpty()) {
                MessagePart detailsPart = message.addPart(new MessagePart(MessagePartType.DETAILS));
            }
            MessagePart detailsPart = message.partsByType(MessagePartType.DETAILS).get(0);

            // Format the message subject
            formatSubject(detailsPart, message.computeLanguages());

            // Update the message title
            message.setAutoTitle(true);
            message.updateMessageTitle();

            result.add(message);
        }


        return result;
    }


    /**
     * Formats the date intervals as text
     * @param timePart the message part to update
     * @param languages the languages to include
     */
    private void formatTimeDescription(MessagePart timePart, Set<String> languages) {
        DictionaryEntry dateTimeFormat = dictionaryService.findByName("message")
                .getEntries().get("msg.time.date_time_format");
        DictionaryEntry timeFormat = dictionaryService.findByName("message")
                .getEntries().get("msg.time.time_format");

        languages.forEach(lang -> {
            String dtf = (dateTimeFormat != null && dateTimeFormat.getDesc(lang) != null)
                    ? dateTimeFormat.getDesc(lang).getValue()
                    : "d MMMM yyyy, 'hours' HH:mm";
            String tf = (timeFormat != null && timeFormat.getDesc(lang) != null)
                    ? timeFormat.getDesc(lang).getValue()
                    : "HH:mm";

            String txt = timePart.getEventDates().stream()
                    .map(di -> {
                        SimpleDateFormat sdf1 = new SimpleDateFormat(dtf, new Locale(lang));
                        SimpleDateFormat sdf2 = TimeUtils.sameDate(di.getFromDate(), di.getToDate())
                            ? new SimpleDateFormat(tf)
                            : sdf1;
                        return String.format("%s - %s", sdf1.format(di.getFromDate()), sdf2.format(di.getToDate()));
                    })
                    .collect(Collectors.joining(".<br>"));

            if (StringUtils.isNotBlank(txt)) {
                timePart.checkCreateDesc(lang).setDetails(txt);
            }
        });
    }


    /**
     * Formats the subject
     * @param detailsPart the message part to update
     * @param languages the languages to include
     */
    private void formatSubject(MessagePart detailsPart, Set<String> languages) {
        DictionaryEntry titleEntry = dictionaryService.findByName("message")
                .getEntries().get("msg.firing_exercises.subject");

        languages.forEach(lang -> {

            String subject = (titleEntry != null && titleEntry.getDesc(lang) != null)
                    ? titleEntry.getDesc(lang).getValue()
                    : "Firing exercises. Warning.";

            detailsPart.checkCreateDesc(lang).setSubject(subject);
        });
    }

    /**
     * Returns the firing area associated with the message
     * @param message the message
     * @return the firing area associated with the message
     */
    private Area getFiringAreaForMessage(Message message) {
        return message.getAreas().stream()
                .filter(a -> a.getType() == AreaType.FIRING_AREA)
                .findFirst()
                .orElse(null);
    }


    /** Returns the published firing area messages from the given domain **/
    private List<Message> getFiringAreaMessages(Domain domain, MessageSeries series, List<Area> firingAreas) {

        Set<String> areaIds = firingAreas.stream().map(a -> a.getId().toString()).collect(Collectors.toSet());

        Set<String> seriesIds = new HashSet<>();
        if (domain != null) {
            seriesIds.addAll(domain.getMessageSeries().stream().map(MessageSeries::getSeriesId).collect(Collectors.toList()));
        }
        if (series != null) {
            seriesIds.add(series.getSeriesId());
        }

        MessageSearchParams params = new MessageSearchParams()
                .statuses(Status.PUBLISHED)
                .seriesIds(seriesIds)
                .areaIds(areaIds);

        // Ensure that the message is actually associated with a firing area
        return messageService.search(params).getData().stream()
                .filter(m -> m.getAreas().stream().anyMatch(a -> a.isActive() && a.getType() == AreaType.FIRING_AREA))
                .collect(Collectors.toList());
    }



    /** Returns the firing areas for the domain **/
    private List<Area> getFiringAreas(Domain domain) {
        AreaSearchParams param = new AreaSearchParams()
                .domain(domain == null ? null : domain.getDomainId())
                .inactive(false)
                .type(AreaType.FIRING_AREA);
        return areaService.searchAreas(param);
    }


}
