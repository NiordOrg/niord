/*
 * Copyright 2026 Danish Emergency Management Agency.
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the line falls between a row the document printed and one it could not have.
 *
 * The importer drops a row on this answer and the import check reports one on the
 * same answer, so the boundary is worth pinning from both sides: a rule that
 * moved by a day would either discard genuine members of the reconstructed
 * cut-offs or let months of tag drift into the frozen archive.
 *
 * No database and no Quarkus.
 */
public class LateMemberRuleTest {

    /** Where the estate's cut-offs are read: the zone the weekly desks sit in. */
    private static final ZoneId COPENHAGEN = ZoneId.of("Europe/Copenhagen");

    /** A Wednesday sitting, noon local time. */
    private static final Instant CUTOFF = at("2025-01-08T12:00");

    /**
     * Thirteen days later is still the same document.
     *
     * Most imported cut-offs are reconstructed -- from an update stamp, a nominal
     * close or the public window -- and land days before the sitting they stand
     * for. A row inside the grace is a row whose cut-off is wrong, not a row that
     * belongs to another publication, and dropping it would delete an archive to
     * punish a reconstruction.
     */
    @Test
    public void arowInsideTheGraceIsKept() {
        assertFalse(LateMemberRule.publishedAfter(CUTOFF, COPENHAGEN, at("2025-01-21T09:00")),
                "thirteen days after a reconstructed cut-off is within reach of the real sitting");
    }

    /**
     * Fifteen days later is somebody else's notice, filed under the nearest tag name.
     *
     * No reconstruction is out by a fortnight. Past the grace the only explanation
     * left is that the tag went on being edited after the document had gone out.
     */
    @Test
    public void arowPastTheGraceIsLeftOut() {
        assertTrue(LateMemberRule.publishedAfter(CUTOFF, COPENHAGEN, at("2025-01-23T09:00")),
                "fifteen days after the cut-off cannot have been on the printed page");
    }

    /**
     * The boundary is the END of the day the grace lands on, read in the series' zone.
     *
     * Not a fixed multiple of 24 hours: the notices a publication announces are
     * stamped in the same sitting, minutes either side of its own timestamp, so a
     * measure in hours cuts a working day in half. The day the grace expires is
     * still inside it; midnight after it is not.
     */
    @Test
    public void theBoundaryIsTheEndOfTheDayTheGraceLandsIn() {
        assertFalse(LateMemberRule.publishedAfter(CUTOFF, COPENHAGEN, at("2025-01-22T23:59")),
                "the fourteenth day has not ended, so the row is still kept");
        assertTrue(LateMemberRule.publishedAfter(CUTOFF, COPENHAGEN, at("2025-01-23T00:00")),
                "midnight in Copenhagen begins the fifteenth day, which is past the grace");
    }

    /**
     * The zone is the one the boundary is read in, not the one the instants happen to carry.
     *
     * The same instant falls on either side of the line depending on the desk's
     * zone, which is why the rule takes one rather than reading the JVM's. A
     * Copenhagen midnight is 23:00 UTC on the day before, so a row stamped there
     * is past the grace in Copenhagen and still inside it in UTC.
     */
    @Test
    public void theZoneDecidesWhichDayTheRowFallsIn() {
        Instant copenhagenMidnight = at("2025-01-23T00:00");

        assertTrue(LateMemberRule.publishedAfter(CUTOFF, COPENHAGEN, copenhagenMidnight));
        assertFalse(LateMemberRule.publishedAfter(CUTOFF, ZoneId.of("UTC"), copenhagenMidnight),
                "read in UTC the row is still on the fourteenth day, and the desk's zone is what "
                        + "the cut-off was stamped in");
    }

    /**
     * An issue with no reference instant keeps every row.
     *
     * The reference is the cut-off stamp, or the publish stamp where the recovery
     * gave the issue no cut-off. An issue with neither states no instant to
     * measure against, and discarding its members would enforce a rule nobody
     * evaluated. The same for a member with no publish date: there is nothing to
     * compare it with.
     */
    @Test
    public void nothingIsLeftOutWhenThereIsNothingToMeasureAgainst() {
        assertFalse(LateMemberRule.publishedAfter(null, COPENHAGEN, at("2026-12-31T09:00")),
                "an issue with neither a cut-off nor a publish stamp judges no row");
        assertFalse(LateMemberRule.publishedAfter(CUTOFF, COPENHAGEN, null),
                "a member with no publish date cannot be shown to be late");
    }

    /** A wall-clock time in the desk's zone, which is where the rule reads days. */
    private static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(COPENHAGEN).toInstant();
    }
}
