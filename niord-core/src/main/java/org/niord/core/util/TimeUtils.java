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

package org.niord.core.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Utility methods for handling date and time.
 */
@SuppressWarnings("unused")
public class TimeUtils {

    /**
     * Resets the time part of the date to 0:0:0
     * @param cal the date to reset
     * @return the reset date
     */
    public static Calendar resetTime(Calendar cal) {
        if (cal != null) {
            cal.set(Calendar.HOUR_OF_DAY, 0);            // set hour to midnight
            cal.set(Calendar.MINUTE, 0);                 // set minute in hour
            cal.set(Calendar.SECOND, 0);                 // set second in minute
            cal.set(Calendar.MILLISECOND, 0);            // set millis in second
        }
        return cal;
    }


    /**
     * Resets the time part of the date to 0:0:0
     * @param date the date to reset
     * @return the reset date
     */
    public static Date resetTime(Date date) {
        if (date != null) {
            Calendar cal = Calendar.getInstance();       // get calendar instance
            cal.setTime(date);                           // set cal to date
            date = resetTime(cal).getTime();
        }
        return date;
    }


    /**
     * Resets the time part of the date to 0:0:0 in the given time zone
     * @param date the date to reset
     * @param timeZone the time zone
     * @return the reset date
     */
    public static Date resetTime(Date date, TimeZone timeZone) {
        if (date != null) {
            ZonedDateTime time = ZonedDateTime.ofInstant(date.toInstant(), timeZone.toZoneId());
            time = time.with(LocalTime.of(0, 0, 0, 0));
            date = Date.from(time.toInstant());
        }
        return date;
    }


    /**
     * Resets the seconds part of the date to 0
     * @param cal the date to reset
     * @return the reset date
     */
    public static Calendar resetSeconds(Calendar cal) {
        if (cal != null) {
            cal.set(Calendar.SECOND, 0);                 // set second in minute
            cal.set(Calendar.MILLISECOND, 0);            // set millis in second
        }
        return cal;
    }


    /**
     * Resets the seconds part of the date to 0
     * @param date the date to reset
     * @return the reset date
     */
    public static Date resetSeconds(Date date) {
        if (date != null) {
            Calendar cal = Calendar.getInstance();       // get calendar instance
            cal.setTime(date);                           // set cal to date
            date = resetSeconds(cal).getTime();
        }
        return date;
    }


    /**
     * Resets the time part of the date to 23:59:59
     * @param date the date to set as the end of the day
     * @return the end of the day
     */
    public static Date endOfDay(Date date) {
        if (date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal = resetTime(cal);
            cal.add(Calendar.DATE, 1);
            cal.add(Calendar.SECOND, -1);
            date = cal.getTime();
        }
        return date;
    }


    /**
     * Resets the time part of the date to 23:59:59 in the given time zone
     * @param date the date to set as the end of the day
     * @param timeZone the time zone
     * @return the end of the day
     */
    public static Date endOfDay(Date date, TimeZone timeZone) {
        if (date != null) {
            ZonedDateTime time = ZonedDateTime.ofInstant(date.toInstant(), timeZone.toZoneId());
            time = time.with(LocalTime.of(23, 59, 59, 0));
            date = Date.from(time.toInstant());
        }
        return date;
    }


    /**
     * Shortcut function that returns the given Calendar field for the date
     * @param date the date
     * @param calFieldId the Calendar field
     * @return the result
     */
    public static Integer getCalendarField(Date date, int calFieldId) {
        if (date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return cal.get(calFieldId);
        }
        return null;
    }


    /**
     * Shortcut function that adds an amount to the given calendar field
     * @param date the date
     * @param calFieldId the Calendar field
     * @param amount the amount to add
     * @return the result
     */
    public static Date add(Date date, int calFieldId, int amount) {
        if (date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(calFieldId, amount);
            date = cal.getTime();
        }
        return date;
    }


    /**
     * Returns a Date for the given year, month and day.
     * Parameters left as null are ignored, so e.g. getDate(null, 0, 1) will return January 1st of the current year.
     *
     * @param year the year
     * @param month the month of the year. NB: Months are zero-based.
     * @param day the day of the month. NB: Days are one-based.
     * @return the first day of the current year
     */
    public static Date getDate(Integer year, Integer month, Integer day) {
        Calendar cal = Calendar.getInstance();
        if (year != null) {
            cal.set(Calendar.YEAR, year);
        }
        if (month != null) {
            cal.set(Calendar.MONTH, month);
        }
        if (day != null) {
            cal.set(Calendar.DAY_OF_MONTH, day);
        }
        return TimeUtils.resetTime(cal.getTime());
    }


    /**
     * Checks if the two Dates is for the same date
     * @param date1 the first date
     * @param date2 the second date
     * @return if the two Dates is for the same date
     */
    @SuppressWarnings("all")
    public static boolean sameDate(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        return resetTime(date1).getTime() == resetTime(date2).getTime();
    }


    /**
     * Checks if the two Dates is for the same date taking into account the given time zone
     * @param date1 the first date
     * @param date2 the second date
     * @return if the two Dates is for the same date
     */
    public static boolean sameDate(Date date1, Date date2, TimeZone timeZone) {
        if (date1 == null || date2 == null) {
            return false;
        } else if (timeZone == null) {
            return sameDate(date1, date2);
        }
        LocalDate d1 = date1.toInstant().atZone(timeZone.toZoneId()).toLocalDate();
        LocalDate d2 = date2.toInstant().atZone(timeZone.toZoneId()).toLocalDate();
        return d1.equals(d2);
    }


    /**
     * Checks if the two Dates is for the same date, hour and minute
     * @param date1 the first date
     * @param date2 the second date
     * @return if the two Dates is for the same date, hour and minute
     */
    @SuppressWarnings("all")
    public static boolean sameDateHourMinute(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        return resetSeconds(date1).getTime() == resetSeconds(date2).getTime();
    }


    /**
     * Computes the number of weeks in a given year
     * See: http://stackoverflow.com/questions/18438332/how-to-get-total-number-of-week-in-the-current-year
     *
     * @param year the year of choice
     * @return the number of weeks in the year
     */
    public static int getNumberOfWeeksInYear(int year) {
        GregorianCalendar cal = new GregorianCalendar(year,12,31);
        cal.setFirstDayOfWeek(Calendar.MONDAY); // week begin from Monday
        cal.setMinimalDaysInFirstWeek(4); // 1 week minimum from Thursday
        return cal.getMaximum(Calendar.WEEK_OF_YEAR);
    }


    /**
     * Computes the ISO-8601 week of year for the given date
     * See: http://stackoverflow.com/questions/147178/what-is-a-good-simple-way-to-compute-iso-8601-week-number
     *
     * @param date the date of choice
     * @return the ISO-8601 week number of the date
     */
    public static int getISO8601WeekOfYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setMinimalDaysInFirstWeek(4);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setTime(date);
        return calendar.get(Calendar.WEEK_OF_YEAR);
    }

}
