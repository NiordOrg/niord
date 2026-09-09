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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.PublicationIssue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CADENCE-LESS issue is decided by its own release, never by its withdrawal.
 *
 * THE SHAPE WITH NO CEILING. Three of the branches that recover a cut-off bound
 * the stamps they will believe: the weekly one against its nominal close, the
 * annual in-force one against the moment its successor took over. The
 * cadence-less branch bounded nothing, because a cadence-less row routinely
 * carries no window END -- and with no upper bound the believability test
 * degenerates to "any stamp after the window opened".
 *
 * WHAT THAT PRODUCED. On an archived row the last write is the one that set it
 * inactive, made in the sitting that published its replacement. The 2023
 * ice-service edition was last written 47 seconds after the 2026 edition's window
 * opened, so it took 2026 as its cut-off: it sorted AFTER the 2026 edition, at
 * the bottom of a list ordered by period, and was numbered year 2026 week 2. Two
 * further rows had no cut-off at all, one of them the CURRENT edition of a live
 * series, because their only stamp fell before their own window opened and there
 * was no fallback.
 *
 * THE RULE. A stamp at or after the successor's window opens is a withdrawal and
 * is not believed; where nothing is believable the cut-off is the instant the
 * publication went public, recorded as PUBLIC_WINDOW so it is not laundered into
 * a release moment.
 *
 * Driven from the captured estate, through the real plan.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class CadencelessCutoffTest {

    private static final String ICE = "nm-annex-ice-service";
    private static final String NCAGS = "nm-annex-ncags";
    private static final String LIGHTS = "danish-list-of-lights";

    /**
     * The twelve rows whose only remaining candidate was their withdrawal, and
     * which therefore fall back to the instant they went public.
     *
     * Eleven had a cut-off moved back off a withdrawal or a later edit; two of
     * them -- NCAGS 2020 and the CURRENT "Dansk Fyrliste 2022" -- had no cut-off
     * at all, and a row with none has no sort key and no numbers either.
     */
    private static final List<String> DECIDED_BY_THE_WINDOW = List.of(
            "42e0f0ca-4f40-4c8d-bc93-40ac979dcf1d",   // Dansk Fyrliste 2015
            "68e7e766-1ee5-4dd0-ae6d-fcdc55ff3171",   // Dansk Fyrliste 2018
            "01daa3e3-3f6e-4359-97b8-1a9eef8bfbe1",   // Dansk Fyrliste 2022, the current edition
            "5219c871-d627-4c27-bf9e-d6b263bc47f1",   // NCAGS 2018
            "567eb759-c091-4064-8e09-fe6f057c7aab",   // NCAGS 2020
            "3c529a8c-657f-4c29-baf3-bcdc81e981b4",   // NCAGS 2021
            "fc46a5be-431d-49c0-91b8-3265330615de",   // NCAGS 2022
            "8ebc1e8d-62e6-4b0f-b24d-12a9a768a908",   // NCAGS 2023
            "c8d4c4b5-ea2e-4525-9d9f-d638e371c135",   // NCAGS, the first 2026 edition
            "55a6060a-c84f-4bb7-b3ad-d122528b59b5",   // ice service 2017
            "c2b753d6-3612-444c-87c8-047ff39be5f6",   // ice service 2022
            "3556c834-c87d-4af3-ba7b-084378b09017");  // ice service 2023, the inverted row

    /**
     * The thirteenth: NCAGS 2017, whose own write is believed once the successor
     * stops being read as its release.
     *
     * It used to take the NEXT edition's tag creation, a year later, which put it
     * a day after the 2018 edition in the list. Its own last-write stamp is a real
     * release stamp and is now the one that survives.
     */
    private static final String DECIDED_BY_ITS_OWN_WRITE = "5c05b168-7045-4f65-b3c4-9217cd319bc2";

    @Inject
    LegacyImportService importService;

    private static LegacyImportService.Plan plan;
    private static List<Publication> publications;

    private LegacyImportService.Plan plan() {
        if (plan == null) {
            publications = LegacyEstateFixture.publications();
            plan = importService.planFrom(LegacyEstateFixture.templates(), publications);
        }
        return plan;
    }

    /**
     * Every one of the thirteen, by the rule that decided it.
     *
     * Asserted against the ROW's own columns rather than against typed instants,
     * so the test states the rule -- the cut-off is the window's open, or the
     * row's own write -- instead of restating a measurement.
     */
    @Test
    public void theWithdrawalStampNeverDecidesACadencelessCutoff() {
        List<String> failures = new ArrayList<>();
        for (String id : DECIDED_BY_THE_WINDOW) {
            PublicationIssue issue = issue(id);
            Publication row = row(id);
            if (!CutoffRecovery.PUBLIC_WINDOW.equals(issue.getCutoffSource())) {
                failures.add(id + ": decided by " + issue.getCutoffSource() + " at "
                        + issue.getCutoffStampedAt() + ", not by its public window");
                continue;
            }
            if (!row.getPublishDateFrom().equals(issue.getCutoffStampedAt())) {
                failures.add(id + ": cut off at " + issue.getCutoffStampedAt()
                        + " but its window opened at " + row.getPublishDateFrom());
            }
            assertTrue(issue.isCutoffReconstructed(), id + " must still say it was reconstructed");
        }
        assertTrue(failures.isEmpty(), String.join("\n  ", failures));

        PublicationIssue ncags2017 = issue(DECIDED_BY_ITS_OWN_WRITE);
        assertEquals(CutoffRecovery.FROM_UPDATED, ncags2017.getCutoffSource(),
                "NCAGS 2017 has a release stamp of its own; the next year's tag is not its close");
        assertEquals(row(DECIDED_BY_ITS_OWN_WRITE).getUpdated(), ncags2017.getCutoffStampedAt());
    }

    /**
     * The three cadence-less annex series come out in release order, with no row
     * left unsorted.
     *
     * The symptom this was found by: the 2023 ice-service edition below the 2026
     * one, and the live "Dansk Fyrliste 2022" at the epoch because it had no
     * cut-off to sort by at all.
     */
    @Test
    public void theCadencelessSeriesSortInReleaseOrder() {
        for (String seriesId : List.of(ICE, NCAGS, LIGHTS)) {
            List<PublicationIssue> issues = issuesOf(seriesId);
            assertTrue(issues.size() > 1, seriesId + " should have several editions");

            // Editions released in the SAME action are not ordered against each
            // other -- three NCAGS editions went public at one instant, and which
            // of them comes first is not a question the archive answers. What
            // must hold is that no LATER release sorts above an earlier one, so
            // the comparison is between release groups.
            Date previousWindow = null;
            Date groupLatest = null;
            Date previousGroupLatest = null;
            for (PublicationIssue issue : issues) {
                assertNotNull(issue.effectiveCutoff(),
                        seriesId + "/" + issue.getPublicId() + " has no cut-off, so it sorts nowhere");
                if (previousWindow != null && previousWindow.before(issue.getPublicFrom())) {
                    previousGroupLatest = groupLatest;
                    groupLatest = null;
                }
                if (previousGroupLatest != null) {
                    assertTrue(!issue.effectiveCutoff().before(previousGroupLatest),
                            seriesId + ": an edition that went public at " + issue.getPublicFrom()
                                    + " sorts at " + issue.effectiveCutoff() + ", before an edition "
                                    + "released earlier which sorts at " + previousGroupLatest);
                }
                if (groupLatest == null || issue.effectiveCutoff().after(groupLatest)) {
                    groupLatest = issue.effectiveCutoff();
                }
                previousWindow = issue.getPublicFrom();
            }
        }
    }

    /**
     * The 2023 ice-service edition sorts before the 2026 one, and is numbered for
     * the year it went out in.
     *
     * Stated on its own because it is the row the defect was reported on, and the
     * numbers are the second half of the damage: week and year are re-derived from
     * the effective cut-off, so a 2023 edition dated 2026 was labelled "2026 (2)".
     */
    @Test
    public void the2023IceEditionSortsBeforeThe2026One() {
        PublicationIssue ice2023 = issue("3556c834-c87d-4af3-ba7b-084378b09017");
        PublicationIssue ice2026 = issue("c4fc75b8-f62c-4a06-92a8-6956c098ca75");

        assertTrue(ice2023.effectiveCutoff().before(ice2026.effectiveCutoff()),
                "the 2023 edition still sorts after the 2026 one");
        assertEquals(2023, ice2023.getYear(),
                "and it is numbered for the year it went out in, not for the year it was withdrawn");
    }

    /**
     * NOTHING ELSE MOVED. The weekly and the yearly branches never reach this
     * fallback, and the census says so rather than a reader having to trust it.
     *
     * Structural rather than incidental: a cadenced row is bounded by its nominal
     * close, and a successor opens a whole period after it, so a withdrawal stamp
     * was already outside the bounds; the two yearly branches never call the
     * cascade at all.
     */
    @Test
    public void theWeeklyAndYearlyBranchesAreUntouched() {
        int weekly = 0;
        int neverReleased = 0;
        for (String seriesId : List.of("weekly-ntm", "weekly-ntm-p-t", "ugentlig-efs-tt")) {
            for (PublicationIssue issue : issuesOf(seriesId)) {
                weekly++;
                if (CutoffRecovery.NOT_RELEASED.equals(issue.getCutoffSource())) {
                    neverReleased++;
                    continue;
                }
                assertTrue(!CutoffRecovery.PUBLIC_WINDOW.equals(issue.getCutoffSource()),
                        seriesId + "/" + issue.getPublicId() + " fell back to its public window, "
                                + "which is the cadence-less answer and not a weekly one");
            }
        }
        assertEquals(1002, weekly, "the cadenced series hold 1,002 rows of the estate");
        assertEquals(2, neverReleased,
                "two of them are the open issues, which never reach a recovery branch at all");

        int yearly = 0;
        int yearlyNeverReleased = 0;
        for (String seriesId : List.of("efs-a", "firing-practice-areas", "accumulated-yearly-ntm")) {
            for (PublicationIssue issue : issuesOf(seriesId)) {
                yearly++;
                if (CutoffRecovery.NOT_RELEASED.equals(issue.getCutoffSource())) {
                    yearlyNeverReleased++;
                }
                // The yearly branches answer PUBLIC_WINDOW or, for an in-force
                // edition dated by a credible release stamp, FROM_UPDATED. MANUAL
                // is their "no date at all" -- an edition with no window to read a
                // boundary off -- and NOT_RELEASED is the open one. None of them
                // is the cadence-less fallback, which is the point.
                assertTrue(CutoffRecovery.PUBLIC_WINDOW.equals(issue.getCutoffSource())
                                || CutoffRecovery.FROM_UPDATED.equals(issue.getCutoffSource())
                                || CutoffRecovery.MANUAL.equals(issue.getCutoffSource())
                                || CutoffRecovery.NOT_RELEASED.equals(issue.getCutoffSource()),
                        seriesId + "/" + issue.getPublicId() + " is decided by "
                                + issue.getCutoffSource() + ", which is not one of the yearly "
                                + "branch's answers");
            }
        }
        assertEquals(47, yearly, "the yearly series hold 47 rows of the estate");
        assertEquals(2, yearlyNeverReleased,
                "two of them are the open firing editions, so 45 rows reach a yearly branch");
    }

    // ------------------------------------------------------------------ helpers

    private List<PublicationIssue> issuesOf(String seriesId) {
        List<PublicationIssue> out = new ArrayList<>();
        for (PublicationIssue issue : plan().issues().values()) {
            if (issue.getSeries() != null && seriesId.equals(issue.getSeries().getSeriesId())) {
                out.add(issue);
            }
        }
        out.sort((a, b) -> {
            long left = a.getPublicFrom() == null ? 0 : a.getPublicFrom().getTime();
            long right = b.getPublicFrom() == null ? 0 : b.getPublicFrom().getTime();
            return Long.compare(left, right);
        });
        return out;
    }

    private Publication row(String publicationId) {
        plan();
        for (Publication p : publications) {
            if (publicationId.equals(p.getPublicationId())) {
                return p;
            }
        }
        throw new IllegalStateException(publicationId + " is not in the estate fixture");
    }

    private PublicationIssue issue(String publicationId) {
        PublicationIssue issue = plan().issues().get(publicationId);
        assertNotNull(issue, publicationId + " is not in the plan");
        return issue;
    }
}
