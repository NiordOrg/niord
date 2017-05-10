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

package org.niord.core.util;

import org.apache.commons.lang.StringUtils;
import org.niord.model.message.DateIntervalVo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.TimeZone;

/**
 * Used to format dates and date intervals that adheres to the standards used in navigational warnings.
 */
public class NavWarnDateFormatter {

    public enum Format { NAVTEX, HTML, PLAIN }

    String[] daySuffix;
    String htmlDateSuffix;
    String untilFurtherNoticeTxt;
    String andTxt;
    String fromTxt;
    String toTxt;
    String untilTxt;
    String dayPrefix;
    String timePrefix;
    String timeIntervalPrefix;
    Format format;
    Locale locale;
    TimeZone timeZone;
    String timeZoneTxt;


    /** No-access constructor **/
    private NavWarnDateFormatter() {
    }


    /**
     * Returns a new date formatter
     * @param bundle a resource bundle containing terms used for formatting dates
     * @return the formatter
     */
    public static NavWarnDateFormatter newDateFormatter(
            ResourceBundle bundle,
            Format format,
            Locale locale,
            String timeZoneId,
            boolean showTimeZone) {

        NavWarnDateFormatter formatter = new NavWarnDateFormatter();
        formatter.daySuffix             = bundle.getString("navwarn.date.day_suffix").split(",");
        formatter.htmlDateSuffix        = bundle.getString("navwarn.date.html_day_suffix");
        formatter.untilFurtherNoticeTxt = bundle.getString("navwarn.date.until_further_notice");
        formatter.andTxt                = " " + bundle.getString("term.and") + " ";
        formatter.fromTxt               = " " + bundle.getString("navwarn.date.from") + " ";
        formatter.toTxt                 = " " + bundle.getString("navwarn.date.to") + " ";
        formatter.untilTxt              = " " + bundle.getString("navwarn.date.until") + " ";
        formatter.dayPrefix             = format == Format.NAVTEX ? "" : " " + bundle.getString("navwarn.date.day_prefix") + " ";
        formatter.timePrefix            = format == Format.NAVTEX ? "" : " " + bundle.getString("navwarn.date.time_prefix") + " ";
        formatter.timeIntervalPrefix    = format == Format.NAVTEX ? "" : " " + bundle.getString("navwarn.date.time_interval_prefix") + " ";
        formatter.format                = format;
        formatter.locale                = locale;
        formatter.timeZoneTxt           = showTimeZone ? " " + timeZoneId + " " : "";
        formatter.timeZone              = StringUtils.isNotBlank(timeZoneId)
                ? TimeZone.getTimeZone(timeZoneId)
                : TimeZone.getDefault();

        return formatter;
    }


    /**
     * Formats the list of date intervals
     * @param dateIntervals the list of date intervals to format
     * @return the formatted date intervals
     */
    public String formatDateIntervals(List<DateIntervalVo> dateIntervals) {
        // Sanity checks
        if (dateIntervals == null || dateIntervals.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int x = 0; x < dateIntervals.size(); x++) {
            if (x > 0 && x == dateIntervals.size() - 1) {
                result.append(andTxt);
            } else if (x > 0) {
                result.append(", ");
            }
            result.append(formatDateInterval(dateIntervals.get(x)));
        }
        return result.toString();
    }


    /**
     * Formats the date interval
     * @param dateInterval the date interval to format
     * @return the formatted date interval
     */
    public String formatDateInterval(DateIntervalVo dateInterval) {
        // Sanity checks
        if (dateInterval == null) {
            return "";
        }
        if (dateInterval.getFromDate() == null && dateInterval.getToDate() == null) {
            return untilFurtherNoticeTxt;
        }

        if (format == Format.NAVTEX) {
            return formatNavtexDateInterval(dateInterval);
        } else {
            return formatNavWarnDateInterval(dateInterval);
        }
    }


    /**
     * Formats the date interval in a NAVTEX format
     * @param di the date interval to format
     * @return the NAVTEX-formatted interval
     */
    private String formatNavtexDateInterval(DateIntervalVo di) {
        if (di.getAllDay() == null || !di.getAllDay()) {
            // Date + time
            if (di.getFromDate() != null && di.getToDate() != null) {
                return TimeUtils.formatNavtexDate(di.getFromDate()).toUpperCase() +
                        " - " +
                        TimeUtils.formatNavtexDate(di.getToDate()).toUpperCase();
            } else if (di.getFromDate() != null) {
                return fromTxt
                        .replace("{{fromDate}}", TimeUtils.formatNavtexDate(di.getFromDate()))
                        .toUpperCase();
            } else {
                return fromTxt
                        .replace("{{toDate}}", TimeUtils.formatNavtexDate(di.getToDate()))
                        .toUpperCase();
            }

        } else {
            // Date only
            // Important, for date-only intervals, we do NOT use UTC, but rather the current time zone.

            return formatNavWarnDateInterval(di);

        }
    }


    /**
     * Formats the date interval in a nav warn (i.e. non-NAVTEX) format
     * @param di the date interval to format
     * @return the nav warn-formatted interval
     */
    private String formatNavWarnDateInterval(DateIntervalVo di) {

        boolean allDay  = di.getAllDay() != null && di.getAllDay();

        String  time1   = allDay ? null : formatTime(di.getFromDate()),
                time2   = allDay ? null : formatTime(di.getToDate()),
                day1    = formatDay(di.getFromDate()),
                day2    = formatDay(di.getToDate()),
                month1  = formatMonth(di.getFromDate()),
                month2  = formatMonth(di.getToDate()),
                year1   = formatYear(di.getFromDate()),
                year2   = formatYear(di.getToDate());

        boolean sameYear    = Objects.equals(year1, year2);
        boolean sameMonth   = sameYear  && Objects.equals(month1, month2);
        boolean sameDay     = sameMonth && Objects.equals(day1, day2);
        boolean sameTime    = sameDay && Objects.equals(time1, time2);

        StringBuilder result = new StringBuilder();

        if (di.getFromDate() != null && di.getToDate() != null) {
            if (allDay) {
                // ****** Only dates *****
                year1   = sameYear  ? null : year1;
                month1  = sameMonth ? null : month1;
                day1    = sameDay   ? null : day1;

                String fromDate = formatDate(day1, month1, year1);
                String toDate = formatDate(day2, month2, year2);

                if (sameDay) {
                    result.append(dayPrefix).append(toDate);
                } else {
                    if (StringUtils.isNotBlank(fromDate)) {
                        result.append(fromTxt).append(dayPrefix).append(fromDate);
                    }
                    if (StringUtils.isNotBlank(toDate)) {
                        result.append(toTxt).append(dayPrefix).append(toDate);
                    }
                }

            } else {
                // ****** Date + time *****

                String fromDate = formatDate(day1, month1, year1);
                String toDate = formatDate(day2, month2, year2);

                if (sameTime) {
                    result.append(dayPrefix).append(fromDate).append(timePrefix).append(time1).append(timeZoneTxt);
                } else if (sameDay) {
                    result.append(dayPrefix).append(fromDate)
                            .append(fromTxt).append(timeIntervalPrefix).append(time1)
                            .append(toTxt).append(timeIntervalPrefix).append(time2).append(timeZoneTxt);
                } else {
                    result.append(fromTxt).append(dayPrefix).append(fromDate).append(timePrefix).append(time1).append(timeZoneTxt)
                            .append(toTxt).append(dayPrefix).append(toDate).append(timePrefix).append(time2).append(timeZoneTxt);
                }
            }

        } else if (di.getFromDate() != null) {

            String fromDate = formatDate(day1, month1, year1);
            result.append(fromTxt).append(dayPrefix).append(fromDate);
            if (!allDay) {
                result.append(timePrefix).append(time1).append(timeZoneTxt);
            }

        } else if (di.getToDate() != null) {

            String toDate = formatDate(day2, month2, year2);
            result.append(untilTxt).append(dayPrefix).append(toDate);
            if (!allDay) {
                result.append(timePrefix).append(time2).append(timeZoneTxt);
            }
        }


        return result.toString()
                .replaceAll("\\s+", " ")
                .trim();
    }


    /** Assemble a date from the individual components **/
    private String formatDate(String day, String month, String year) {
        StringBuilder result = new StringBuilder();

        if (day != null) {
            result.append(day).append(" ");
        }
        if (month != null) {
            result.append(month).append(" ");
        }
        if (year != null) {
            result.append(year).append(" ");
        }
        return result.toString();
    }


    /**
     * Formats the day incl a language specific suffix. Example "1" -> "1st" (plain) or "1<sup>st</sup>" (html)
     * @param date the day to format
     * @return the formatted day
     */
    private String formatDay(Date date) {
        if (date == null) {
            return null;
        }

        // Get the day from the date
        SimpleDateFormat dayFormat = new SimpleDateFormat("d");
        dayFormat.setTimeZone(timeZone);
        int day = Integer.valueOf(dayFormat.format(date));

        // Add the suffix, e.g. "1" -> "1st"
        String suffix = daySuffix[day];
        if (format == Format.HTML) {
            suffix = htmlDateSuffix.replace("{{suffix}}", suffix);
        }
        return String.valueOf(day) + suffix;
    }


    /**
     * Formats the month of the date
     * @param date the date to format the month of
     * @return the formatted month
     */
    private String formatMonth(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat monthFormat = new SimpleDateFormat(format == Format.NAVTEX ? "MMM" : "MMMM", locale);
        monthFormat.setTimeZone(timeZone);
        return monthFormat.format(date);
    }

    /**
     * Formats the year of the date
     * @param date the date to format the year of
     * @return the formatted year
     */
    private String formatYear(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
        yearFormat.setTimeZone(timeZone);
        return yearFormat.format(date);
    }

    /**
     * Formats the time of the date
     * @param date the date to format the time of
     * @return the formatted time
     */
    private String formatTime(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmm");
        timeFormat.setTimeZone(timeZone);
        return timeFormat.format(date);
    }

}
