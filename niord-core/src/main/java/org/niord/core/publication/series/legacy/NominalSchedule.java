package org.niord.core.publication.series.legacy;

import org.niord.core.publication.series.CutoffDay;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The nominal weekday and time a series releases on, read off its own history.
 *
 * The legacy model had no cadence and no schedule, so a translated series arrived
 * with neither -- and S-5 and S-7 require both of a series with a cadence. Every
 * imported weekly series was therefore unactivatable: correct in every other
 * respect and refused, on two fields the importer had left for somebody to guess.
 *
 * They do not need guessing. A series that has released weekly for years has said
 * what its schedule is several hundred times over, and the recovered cut-offs are
 * exactly that record.
 *
 * NOMINAL, and the distinction carries the whole design. The real cut-off is
 * stamped at publication and the next interval chains off THAT, so nothing here
 * has to be exact -- it has to be close enough to schedule by and to suggest the
 * next period from. That is why the hour is taken modally and the minutes are
 * dropped: a release that drifted to 12:07 one week is still a twelve o'clock
 * series, and recording 12:07 as the schedule would dress an accident up as an
 * intention.
 */
public final class NominalSchedule {

    private NominalSchedule() {
    }

    /**
     * The weekday the series most often closes on, or null when it cannot be told.
     *
     * Modal rather than mean: a weekday has no meaningful average, and a series
     * that slipped to Thursday twice in six years is a Wednesday series.
     */
    public static CutoffDay weekdayOf(List<Date> cutoffs, ZoneId zone) {
        Map<DayOfWeek, Integer> counts = new LinkedHashMap<>();
        for (Date cutoff : cutoffs) {
            if (cutoff == null) {
                continue;
            }
            DayOfWeek day = ZonedDateTime.ofInstant(cutoff.toInstant(), zone).getDayOfWeek();
            counts.merge(day, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(e -> CutoffDay.valueOf(e.getKey().name()))
                .orElse(null);
    }

    /**
     * The hour the series most often closes at, as "HH:00", or null when unknown.
     *
     * Minutes are deliberately dropped -- see the class note. An hour that appears
     * as often as another loses to whichever was seen first, which is arbitrary and
     * harmless: the two are equally supported by the evidence, and the stamped
     * cut-off overrides either.
     */
    public static String timeOfDayOf(List<Date> cutoffs, ZoneId zone) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Date cutoff : cutoffs) {
            if (cutoff == null) {
                continue;
            }
            int hour = ZonedDateTime.ofInstant(cutoff.toInstant(), zone).getHour();
            counts.merge(hour, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(e -> String.format("%02d:00", e.getKey()))
                .orElse(null);
    }

    /**
     * Where the series' first interval opens, or null when it has no issue.
     *
     * S-4 requires this of an interval-based series and of nothing else, so the
     * caller applies it only to a tiling one. The earliest interval start is the
     * answer by definition: it is where the archive begins.
     */
    public static Date firstIntervalStartOf(List<Date> intervalStarts) {
        return intervalStarts.stream()
                .filter(d -> d != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
