/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.mailinglist;

import org.apache.commons.lang.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class used for computing next scheduled times for scheduled mailing list triggers
 */
public class ScheduledExecutionTimeUtil {

    public static Pattern SCHEDULED_TIME_FORMAT = Pattern.compile(
            "(?<hour>[01]?[0-9]|2[0-3]):(?<minute>[0-5][0-9])"
    );

    /**
     * Computes the next execution time for the given scheduled mailing list trigger
     * @param trigger the scheduled mailing list trigger
     * @return the next execution time
     */
    public static Date computeNextExecutionTime(MailingListTrigger trigger) {
        // Validate the trigger
        if (trigger == null || trigger.getType() != TriggerType.SCHEDULED ||
                trigger.getScheduleType() == null || StringUtils.isBlank(trigger.getScheduledExecutionTime())) {
            throw new IllegalArgumentException("Invalid scheduled mailing list trigger");
        }
        return computeNextExecutionTime(
                new Date(), // Now
                trigger.getScheduleType(),
                trigger.getScheduledExecutionTime(),
                trigger.getScheduledExecutionTimeZone());
    }


    /**
     * Computes the next execution time (after the baseTime) for the given scheduled mailing list trigger
     * @param baseTime the base time after which to find the next execution
     * @param scheduleType the schedule type, either daily or a specific week day
     * @param scheduledExecutionTime The time of day, in a "HH24:MM" format, where the trigger should be executed
     * @param scheduledExecutionTimeZone The time-zone of the scheduled execution time
     * @return the next execution time
     */
    public static Date computeNextExecutionTime(
            Date baseTime,
            ScheduleType scheduleType,
            String scheduledExecutionTime,
            String scheduledExecutionTimeZone) {

        // Validate the schduling parameters
        if (baseTime == null || scheduleType == null || StringUtils.isBlank(scheduledExecutionTime)) {
            throw new IllegalArgumentException("Invalid scheduled execution parameters");
        }

        // Validate that we can parse the time expression
        Matcher m = SCHEDULED_TIME_FORMAT.matcher(scheduledExecutionTime);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid scheduled execution time format " + scheduledExecutionTime);
        }
        int hour = Integer.valueOf(m.group("hour"));
        int minute = Integer.valueOf(m.group("minute"));

        ZoneId zoneId = StringUtils.isNotBlank(scheduledExecutionTimeZone)
                ? ZoneId.of(scheduledExecutionTimeZone)
                : ZoneId.systemDefault();

        // Set "time" to the requested time on the "baseTime" date
        ZonedDateTime nextExecution = ZonedDateTime.ofInstant(baseTime.toInstant(), zoneId)
                .with(LocalTime.of(hour, minute));

        if (scheduleType == ScheduleType.DAILY) {
            if (nextExecution.toInstant().isBefore(baseTime.toInstant())) {
                nextExecution = nextExecution.plusDays(1);
            }
        } else {
            nextExecution = nextExecution.with(scheduleType.getCalendarField());
            if (nextExecution.toInstant().isBefore(baseTime.toInstant())) {
                nextExecution = nextExecution.plusWeeks(1);
            }
        }


        return Date.from(nextExecution.toInstant());
    }

}
