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

package org.niord.web;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.niord.core.util.NavWarnDateFormatter;
import org.niord.model.message.DateIntervalVo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TimeZone;

import static org.niord.core.util.NavWarnDateFormatter.Format.PLAIN;

/**
 * Test formatting navigational warning dates
 */
@SuppressWarnings("all")
public class NavWarnDateFormatterTest {

    public List<DateIntervalVo> getDateIntervals() throws ParseException {
        List<DateIntervalVo> dateIntervals = new ArrayList<>();

        dateIntervals.add(getDateInterval("12-04-2017 13:00", "12-04-2017 13:00", true));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "12-04-2017 13:00", false));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", null, true));
        dateIntervals.add(getDateInterval(null, "12-04-2017 13:00", true));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "12-04-2017 13:55", true));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "16-04-2017 13:55", true));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "16-05-2017 13:55", true));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "16-05-2018 13:55", true));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "12-04-2017 13:55", false));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "15-04-2017 13:55", false));
        dateIntervals.add(getDateInterval("12-04-2017 13:00", "15-05-2017 13:55", false));
        dateIntervals.add(getDateInterval("12-04-2016 13:00", "15-04-2017 13:55", false));

        return dateIntervals;
    }

    @Test
    public void testDateFormatter() throws ParseException {

        Locale locale = Locale.ENGLISH;

        // Replace positions with audio format
        ResourceBundle bundle = ResourceBundle.getBundle("template", locale);

        NavWarnDateFormatter formatter = NavWarnDateFormatter.newDateFormatter(
                bundle, PLAIN, locale, "UTC", "CEST", true);

        Arrays.stream(formatter.formatDateIntervals(getDateIntervals()).split(",|and")).forEach(
                di -> System.out.println(di.trim())
        );
    }


    protected DateIntervalVo getDateInterval(String date1, String date2, boolean allDay) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("CEST"));

        DateIntervalVo di = new DateIntervalVo();
        di.setAllDay(allDay);
        if (StringUtils.isNotBlank(date1)) {
            di.setFromDate(sdf.parse(date1));
        }
        if (StringUtils.isNotBlank(date2)) {
            di.setToDate(sdf.parse(date2));
        }
        return di;
    }

}
