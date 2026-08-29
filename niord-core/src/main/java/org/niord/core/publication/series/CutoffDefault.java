/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.core.publication.series;

/**
 * Where a series' cut-off falls by default when an issue is published.
 *
 * The cut-off is the end of an issue's CONTENT period -- the instant membership
 * is decided at -- and the publication moment is when somebody pressed publish.
 * For a weekly list the two are minutes apart and the release stamps the
 * cut-off. For an annual list they can be a year apart: "EfS A 2018" describes
 * what was in force when the 2018 edition took effect, whenever in January the
 * document was finished, and "Akkumuleret EfS 2003" describes what was published
 * during 2003, and came out in 2016.
 *
 * The publish dialog offers this as the default and lets the admin choose a
 * past instant instead (never a future one); the publication moment is always
 * the actual one and never editable.
 */
public enum CutoffDefault {

    /** The moment of the release action. The weekly shape: the release closes the period. */
    RELEASE_MOMENT,

    /**
     * The nominal start of the period the issue is current for. The in-force
     * annual shape: the year's edition is decided where the year opens.
     */
    PERIOD_START,

    /**
     * The nominal end of the period the issue describes. The accumulated annual
     * shape: what was published during the year is known when the year closes.
     */
    PERIOD_END;

    /**
     * The default for a series shape, as the importer decides it and the create
     * form suggests it: yearly series are calendar-driven, everything else is
     * release-driven.
     */
    public static CutoffDefault forShape(SeriesCadence cadence,
                                         org.niord.core.publication.series.resolve.TimeRelation relation) {
        if (cadence == SeriesCadence.YEARLY) {
            return relation == org.niord.core.publication.series.resolve.TimeRelation.IN_FORCE_AT_CUTOFF
                    ? PERIOD_START : PERIOD_END;
        }
        return RELEASE_MOMENT;
    }

    /**
     * A publication whose period IS a year and whose content is what stood at the
     * end of it -- the one shape whose cut-off is a DAY rather than an instant.
     *
     * The changeover for such an edition is a day's work: the previous year's
     * notices are cancelled and the new year's published in one sitting, and the
     * public window is opened somewhere in the middle of it, or nominally at the
     * turn of the year while the sitting happens weeks later. Measured on "EfS A -
     * 2025", the window opened at 10:28:17, the 2024 notices were cancelled at
     * 11:18 and the 2025 notices published at 11:28 -- so a cut-off at the instant
     * the window opened resolves the edition from BEFORE its own changeover, and
     * produced 29 members missing and 29 extra against the tag that recorded it.
     * 2024 and 2022 have the same shape; 2026 and 2023 looked correct only because
     * those years' notices happened to go out before the window was opened.
     *
     * Which DAY the cut-off falls on is {@link #annualInForceCutoff}.
     *
     * BOTH HALVES ARE REQUIRED. A weekly in-force list -- the active P&T -- has a
     * release stamp minutes from its close and no day-long changeover to contain,
     * and an accumulated annual is decided where its window CLOSES, which is a
     * calendar boundary rather than a day anybody worked through. Neither is
     * touched by this.
     */
    public static boolean isAnnualInForce(SeriesCadence cadence,
                                          org.niord.core.publication.series.resolve.TimeRelation relation) {
        return cadence == SeriesCadence.YEARLY
                && relation == org.niord.core.publication.series.resolve.TimeRelation.IN_FORCE_AT_CUTOFF;
    }

    /**
     * THE cut-off of an annual in-force edition: the end of the LATER of two days
     * -- the day its public window opens, and the day it was released.
     *
     * ONE RULE, TWO CALLERS. The archive reader recovers it from a stored window
     * and a stored release stamp; the publish action reads it from the edition's
     * own boundary and the clock. They have to agree to the millisecond, because
     * a recovered edition and a natively published one sit in the same series and
     * are compared against each other.
     *
     * WHY THE LATER OF THE TWO. The changeover of an in-force annual is done by
     * hand, and the window is opened either during that sitting or nominally at
     * the turn of the year while the sitting happens weeks afterwards. "EfS A -
     * 2025" is the first shape: window opened 7 February 10:28, the outgoing
     * notices cancelled 11:18, the incoming ones published 11:28, released the
     * same day. "Skydeområder 2025" is the second: window opened 1 January, the
     * changeover done on 7 February, the edition released on 26 February. What
     * was in force at the end of the day the edition was RELEASED is the edition;
     * what was in force at the end of the day its window opened is only the same
     * answer when those are the same day.
     *
     * A null release leaves the window-open day standing, which is the answer
     * whenever nothing credible witnessed the release. A null window leaves the
     * release day. Both null is no answer at all.
     *
     * WHAT IS COMPARED IS THE DAY, NOT THE INSTANT. Both arguments are instants
     * and only the day each falls on is read; the release instant itself is a
     * separate fact, recorded separately, and is never replaced by this.
     */
    public static java.util.Date annualInForceCutoff(java.util.Date windowOpen, java.util.Date release,
                                                     java.time.ZoneId zone) {
        return releaseDayIsLater(windowOpen, release, zone)
                ? endOfDay(release, zone)
                : endOfDay(windowOpen, zone);
    }

    /**
     * Which of the two days {@link #annualInForceCutoff} took -- the release day,
     * or the window-open day it falls back to.
     *
     * Exposed because the answer's PROVENANCE differs by branch and the two must
     * not be decided twice: an edition whose day came off a release stamp was
     * settled by something that witnessed the release, and one that fell back to
     * its window was settled by the calendar.
     */
    public static boolean releaseDayIsLater(java.util.Date windowOpen, java.util.Date release,
                                            java.time.ZoneId zone) {
        java.util.Date releaseDay = endOfDay(release, zone);
        if (releaseDay == null) {
            return false;
        }
        java.util.Date openDay = endOfDay(windowOpen, zone);
        return openDay == null || releaseDay.after(openDay);
    }

    /**
     * The last instant of the day an instant falls in, in the given zone.
     *
     * The end of the day is the smallest bound that contains a whole changeover
     * -- a wider one starts pulling in the next day's editing, a narrower one puts
     * the answer back inside a sequence whose order within the day nobody
     * controlled. Which day to take is {@link #annualInForceCutoff}.
     *
     * To the millisecond, because that is the resolution the columns keep and a
     * cut-off rounded up to midnight would belong to the following day, which for
     * an edition taking effect on 1 January is the following YEAR.
     *
     * The zone is required rather than defaulted to the JVM's: which day an
     * instant belongs to is exactly the question a wrong zone answers wrongly, and
     * an instant an hour either side of midnight lands on two different days on
     * two different machines.
     */
    public static java.util.Date endOfDay(java.util.Date instant, java.time.ZoneId zone) {
        if (instant == null) {
            return null;
        }
        java.time.ZoneId z = zone == null ? java.time.ZoneOffset.UTC : zone;
        return java.util.Date.from(instant.toInstant().atZone(z)
                .toLocalDate()
                .atTime(23, 59, 59, 999_000_000)
                .atZone(z)
                .toInstant());
    }
}
