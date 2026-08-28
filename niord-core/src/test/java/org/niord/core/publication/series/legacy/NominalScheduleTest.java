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

package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.CutoffDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reading a series' schedule off its own history. Pure, no database.
 *
 * The importer left nominalCutoffDay and nominalCutoffTime unset because the
 * legacy model had no schedule to copy. S-5 and S-7 require both of any series
 * with a cadence, so every imported weekly series was unactivatable -- correct in
 * every other respect and refused on two fields nobody had the data to fill in by
 * hand. The data was in the archive all along.
 */
public class NominalScheduleTest {

    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");

    /** A Copenhagen-local instant. */
    private static Date at(int year, int month, int day, int hour, int minute) {
        return Date.from(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, CPH).toInstant());
    }

    /**
     * A weekly series that has always closed on a Wednesday says so.
     *
     * The weekly EfS is the case this exists for: released every Wednesday for
     * years, and imported with no weekday at all.
     */
    @Test
    public void theModalWeekdayIsTheSchedule() {
        List<Date> wednesdays = List.of(
                at(2026, 1, 7, 12, 0),
                at(2026, 1, 14, 12, 0),
                at(2026, 1, 21, 12, 0));

        assertEquals(CutoffDay.WEDNESDAY, NominalSchedule.weekdayOf(wednesdays, CPH));
    }

    /**
     * A week that slipped does not rewrite the schedule.
     *
     * Releases drift; the nominal day is what the series INTENDS. A mean would be
     * meaningless here and a "most recent" rule would let one late Thursday
     * redefine six years of Wednesdays.
     */
    @Test
    public void oneSlippedWeekDoesNotChangeTheWeekday() {
        List<Date> mostlyWednesday = List.of(
                at(2026, 1, 7, 12, 0),
                at(2026, 1, 15, 12, 0), // a Thursday
                at(2026, 1, 21, 12, 0),
                at(2026, 1, 28, 12, 0));

        assertEquals(CutoffDay.WEDNESDAY, NominalSchedule.weekdayOf(mostlyWednesday, CPH));
    }

    /** The hour is modal too, and the minutes are dropped on purpose. */
    @Test
    public void theHourIsModalAndTheMinutesAreDropped() {
        List<Date> aroundNoon = List.of(
                at(2026, 1, 7, 12, 0),
                at(2026, 1, 14, 12, 7),
                at(2026, 1, 21, 12, 41));

        assertEquals("12:00", NominalSchedule.timeOfDayOf(aroundNoon, CPH),
                "12:07 is a twelve o'clock series that ran seven minutes late, and recording the "
                        + "seven minutes would dress an accident up as an intention");
    }

    /** Read in the series' own zone, not the JVM's. */
    @Test
    public void theZoneIsTheSeriesOwn() {
        List<Date> lateEvening = List.of(at(2026, 6, 3, 23, 30));

        assertEquals("23:00", NominalSchedule.timeOfDayOf(lateEvening, CPH));
        // The same instant is an hour earlier in UTC, and in summer that is still
        // the same day -- but the hour must follow the zone it is read in.
        assertEquals("21:00", NominalSchedule.timeOfDayOf(lateEvening, ZoneId.of("UTC")));
    }

    /** Nothing to read from is null, not a guess. */
    @Test
    public void noCutoffsMeansNoSchedule() {
        assertNull(NominalSchedule.weekdayOf(List.of(), CPH));
        assertNull(NominalSchedule.timeOfDayOf(List.of(), CPH));
        assertNull(NominalSchedule.firstIntervalStartOf(List.of()));
    }

    /** Nulls in the list are skipped rather than throwing: an OPEN issue has no cut-off. */
    @Test
    public void nullsAreSkipped() {
        List<Date> withGaps = java.util.Arrays.asList(null, at(2026, 1, 7, 12, 0), null);

        assertEquals(CutoffDay.WEDNESDAY, NominalSchedule.weekdayOf(withGaps, CPH));
        assertEquals("12:00", NominalSchedule.timeOfDayOf(withGaps, CPH));
    }

    /** The first interval opens where the archive begins. */
    @Test
    public void theFirstIntervalIsTheEarliestStart() {
        Date earliest = at(2019, 4, 24, 12, 0);
        List<Date> starts = List.of(at(2026, 1, 7, 12, 0), earliest, at(2020, 5, 6, 12, 0));

        assertEquals(earliest, NominalSchedule.firstIntervalStartOf(starts));
    }
}
