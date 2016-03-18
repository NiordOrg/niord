package org.niord.core.util;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

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
     * Checks if the two Dates is for the same date
     * @param date1 the first date
     * @param date2 the second date
     * @return if the two Dates is for the same date
     */
    public static boolean sameDate(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        return resetTime(date1).getTime() == resetTime(date2).getTime();
    }

    /**
     * Checks if the two Dates is for the same date, hour and minute
     * @param date1 the first date
     * @param date2 the second date
     * @return if the two Dates is for the same date, hour and minute
     */
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
}
