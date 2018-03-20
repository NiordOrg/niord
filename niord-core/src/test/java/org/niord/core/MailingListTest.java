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

package org.niord.core;

import org.junit.Test;
import org.niord.core.mailinglist.ScheduleType;
import org.niord.core.mailinglist.ScheduledExecutionTimeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

/**
 * Mailing list tests
 */
public class MailingListTest {

    @Test
    public void testScheduledExecutionTime() throws Exception {

        String timeZone = "Europe/Copenhagen";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone(timeZone));

        Date nextExecutionTime = ScheduledExecutionTimeUtil
                .computeNextExecutionTime(
                        sdf.parse("2017-03-25 15:00:00 +0100"),
                        ScheduleType.DAILY,
                        "15:55",
                        timeZone);

        assertEquals("2017-03-25 15:55:00 CET", sdf.format(nextExecutionTime));


        nextExecutionTime = ScheduledExecutionTimeUtil
                .computeNextExecutionTime(
                        sdf.parse("2017-03-25 15:56:00 +0100"),
                        ScheduleType.DAILY,
                        "15:55",
                        timeZone);

        assertEquals("2017-03-26 15:55:00 CEST", sdf.format(nextExecutionTime));


        nextExecutionTime = ScheduledExecutionTimeUtil
                .computeNextExecutionTime(
                        sdf.parse("2017-03-25 15:56:00 +0100"),
                        ScheduleType.SATURDAY,
                        "15:55",
                        timeZone);

        assertEquals("2017-04-01 15:55:00 CEST", sdf.format(nextExecutionTime));

    }

}
