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
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicWindowSource;
import org.niord.core.publication.series.PublicationIssueDesc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A publication nobody has released is on no public site, and the import says so.
 *
 * WHAT THE ARCHIVE HOLDS. The old model kept one window per publication --
 * publishDateFrom to publishDateTo -- and filled it in when the row was CREATED,
 * not when it went out. The weekly template mints next week's row a period ahead
 * with the window it is EXPECTED to occupy already written in, so four rows of the
 * captured estate carry a window for an edition that has never been published.
 *
 * COPIED ACROSS, THAT WINDOW IS A CLAIM. An issue with publicFrom set is on the
 * public site from that instant and the public adapter has no second opinion about
 * it: the row says it is current, so it is served as current. Three of the four
 * are dated ahead, which means the archive quietly acquires an edition that does
 * not exist yet, on a date nothing decided. The fourth carries a literal ${year}
 * in its title and no dates at all.
 *
 * SO AN UNRELEASED ROW ARRIVES WITH NO WINDOW, which is the shape a natively
 * created successor already has: it is minted with neither end, and the publish
 * action opens the window at the instant it actually runs. The planned dates are
 * not lost -- the interval's upper bound IS the planned cut-off, and the issue
 * list projects the expected window from it for the screen that has to show one.
 *
 * AND ITS PREDECESSOR STAYS OPEN-ENDED. The supersession pass ends each edition
 * where its successor OPENS, and an unreleased row never opened: with no window it
 * sorts to the front of its series' chain and states no instant anything could be
 * capped at. Were it to close the CURRENT edition at the date it is merely EXPECTED
 * to open, the series would serve the week before last between that instant and the
 * real release. So the newest published row of every series with an edition in
 * preparation keeps publicTo IS NULL, which is what "this is still the current
 * edition" means, and the publish action caps it at the successor's real stamp when
 * that finally happens.
 *
 * Driven from the captured estate through the real plan, because which rows are
 * ADJACENT is decided by the series each one is filed into -- and the throwaway
 * clones and the template-less double weeks are filed by ruling, so a chain built
 * by template has the wrong neighbours in exactly the places this is about.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class UnreleasedIssueWindowTest {

    /**
     * Every row of the estate that has never been released, and the series it is
     * filed into.
     *
     * Written out rather than filtered for, because the SET is the property. Three
     * were minted a period ahead by their own template -- the two weeklies and next
     * year's firing list -- and the fourth is the undated ${year} row. A fifth
     * appearing here means the capture has moved and what is measured below is
     * something else.
     */
    private static final Map<String, String> NEVER_RELEASED = new LinkedHashMap<>();

    static {
        NEVER_RELEASED.put("355264bf-0f93-468c-ae74-d1c00f2b79b9", "weekly-ntm");
        NEVER_RELEASED.put("c113c4eb-c671-48a0-95f5-c9341f866f95", "weekly-ntm-p-t");
        NEVER_RELEASED.put("46c4ed07-17a7-4afc-87f9-c78d266c4805", "firing-practice-areas");
        NEVER_RELEASED.put("edc1b215-22a9-4a11-b590-c62489aa5554", "firing-practice-areas");
    }

    /**
     * The current edition of each series that has one in preparation.
     *
     * These are the rows the supersession pass would close at their unreleased
     * successor's planned start. Named rather than recomputed: "the newest one" is
     * a description, and a test that recomputed the description would follow a
     * change to the chain order instead of catching it.
     *
     * MEASURED, THEIR WINDOWS DO NOT MOVE, and that is worth stating rather than
     * quietly leaving out. All three carry an end date the archive recorded --
     * 2026-08-19T09:59Z on the two weeklies, 2026-12-31T11:10Z on the firing list
     * -- and a recorded end is never overwritten, so the supersession pass had
     * already stepped over them at its first guard. What keeps it true for a row
     * the archive left open-ended is that an unreleased successor has no public
     * start to be capped at and sorts ahead of the whole released chain. This
     * capture holds no such row in that position, so the test that clears one
     * makes it.
     */
    private static final Map<String, String> CURRENT_EDITION = new LinkedHashMap<>();

    static {
        CURRENT_EDITION.put("4b708e62-51ca-4288-89a0-6fae01e103db", "weekly-ntm-p-t");
        CURRENT_EDITION.put("5cc97a7f-2f86-44be-af00-930a73c3e32f", "weekly-ntm");
        CURRENT_EDITION.put("558aa1ac-7807-43b0-8d00-9b074baca026", "firing-practice-areas");
    }

    /** The current EfS, and the week being prepared behind it. */
    private static final String WEEKLY_CURRENT = "5cc97a7f-2f86-44be-af00-930a73c3e32f";
    private static final String WEEKLY_IN_PREPARATION = "355264bf-0f93-468c-ae74-d1c00f2b79b9";

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

    // ------------------------------------------------------------ the measurement

    /**
     * THE WHOLE CHANGE, row by row, so a reader can check it rather than trust it.
     *
     * Printed as well as asserted: this is the one place the estate-wide effect is
     * visible, and "which rows moved" is the question asked of a cut-over run
     * afterwards.
     */
    @Test
    public void everyRowWhoseWindowMovesIsNamed() {
        Map<String, Publication> legacyById = new LinkedHashMap<>();
        for (Publication p : publicationsOf()) {
            legacyById.put(p.getPublicationId(), p);
        }

        System.out.println("[unreleased windows] unreleased rows whose public window is cleared:");
        for (String publicationId : unreleasedIds()) {
            Publication legacy = legacyById.get(publicationId);
            System.out.println("  " + describe(issue(publicationId), publicationId)
                    + " -- the archive's window was "
                    + stamp(legacy == null ? null : legacy.getPublishDateFrom()) + " to "
                    + stamp(legacy == null ? null : legacy.getPublishDateTo()));
        }

        Map<String, PublicationIssue> current = currentEditionOfASeriesWithOneInPreparation();
        System.out.println("[unreleased windows] the current edition behind each of them, and where "
                + "its public window ends:");
        current.forEach((publicationId, issue) ->
                System.out.println("  " + describe(issue, publicationId)
                        + " -- public from " + stamp(issue.getPublicFrom())
                        + " to " + stamp(issue.getPublicTo())
                        + " (" + (issue.getPublicTo() == null ? "open-ended"
                                : "the end the archive recorded") + ")"));

        assertEquals(NEVER_RELEASED.keySet(), new LinkedHashSet<>(unreleasedIds()),
                "a different set of rows is unreleased than the capture holds");
        assertEquals(CURRENT_EDITION.keySet(), current.keySet(),
                "a different set of rows is the current edition behind an unreleased one than the "
                        + "measurement recorded");
    }

    // ---------------------------------------------------- what each rule produces

    /**
     * No unreleased row carries a window, at either end.
     *
     * The SOURCE is left where a natively created successor leaves it. It is a
     * NOT NULL column with a DERIVED default and the create path does not set it
     * either, so an imported unreleased row and a minted one are the same row in
     * every field that describes a public window: neither end, and the default
     * beside them. Nothing reads the source while the window is absent -- an admin
     * setting a period by hand stamps MANUAL then, and the publish action stamps
     * DERIVED when it opens one.
     */
    @Test
    public void anUnreleasedRowCarriesNoPublicWindow() {
        for (String publicationId : NEVER_RELEASED.keySet()) {
            PublicationIssue issue = issue(publicationId);
            assertEquals(IssueStatus.OPEN, issue.getStatus(),
                    publicationId + " is no longer unreleased, so it no longer demonstrates this");
            assertNull(issue.getPublicFrom(), describe(issue, publicationId)
                    + " is on the public site from a date nothing published it on");
            assertNull(issue.getPublicTo(), describe(issue, publicationId)
                    + " carries the end of a window it never had a start for");
            assertEquals(PublicWindowSource.DERIVED, issue.getPublicWindowSource(),
                    describe(issue, publicationId) + " differs from a natively created successor, "
                            + "which reaches the same state on the column's own default");
        }
    }

    /**
     * And three of the four genuinely HAD dates to drop.
     *
     * The positive control. If the estate's unreleased rows carried no window in
     * the first place, every assertion above would pass over a rule that does
     * nothing, and the defect it prevents would come back the first time a capture
     * included a pre-created row.
     */
    @Test
    public void theDroppedWindowsWereRealDatesInTheArchive() {
        int dated = 0;
        for (String publicationId : NEVER_RELEASED.keySet()) {
            if (row(publicationId).getPublishDateFrom() != null) {
                dated++;
            }
        }
        assertEquals(3, dated,
                "three of the four unreleased rows were minted with the window their edition is "
                        + "expected to occupy; a count of zero means this rule drops nothing");
    }

    /**
     * The planned cut-off survives, on the bound that is a fact about the content.
     *
     * The window is dropped because it is a claim about the public site. When the
     * content period closes is a different question and the archive does answer it,
     * so an unreleased row keeps its interval -- which is what the issue list reads
     * to project the expected window back onto the screen.
     */
    @Test
    public void theUnreleasedRowKeepsThePlannedCutoffOnItsInterval() {
        for (String publicationId : NEVER_RELEASED.keySet()) {
            PublicationIssue issue = issue(publicationId);
            Date planned = row(publicationId).getPublishDateFrom();
            if (planned == null) {
                continue;
            }
            assertEquals(planned, issue.effectiveCutoff(), describe(issue, publicationId)
                    + " lost the planned cut-off along with the window; the issue list has nothing "
                    + "left to project an expected window from");
            assertNull(issue.getCutoffStampedAt(), describe(issue, publicationId)
                    + " carries a release stamp for a release that never happened");
        }
    }

    /**
     * The current edition keeps the end the ARCHIVE gave it, and no other.
     *
     * All three of these carry a recorded publishDateTo, so what has to hold is
     * that the import still copies it verbatim: an end date in the archive is data
     * (the 2017 NCAGS edition really did stop on 23 December), and the rule about
     * unreleased successors must not have started rewriting recorded ends on its
     * way past.
     */
    @Test
    public void theCurrentEditionKeepsTheEndTheArchiveRecorded() {
        Map<String, PublicationIssue> current = currentEditionOfASeriesWithOneInPreparation();
        assertEquals(CURRENT_EDITION.size(), current.size(),
                "a different number of series has an edition in preparation than the measurement "
                        + "recorded: " + current.keySet());

        for (String publicationId : CURRENT_EDITION.keySet()) {
            PublicationIssue issue = issue(publicationId);
            assertEquals(IssueStatus.PUBLISHED, issue.getStatus());
            assertNotNull(issue.getPublicFrom(), describe(issue, publicationId)
                    + " is published and must state when it went public");
            assertEquals(row(publicationId).getPublishDateTo(), issue.getPublicTo(),
                    describe(issue, publicationId) + " no longer ends where the archive says it does");
        }
    }

    /**
     * AND WHERE THE ARCHIVE RECORDED NO END, the current edition stays current.
     *
     * The rule's real subject, reached by clearing the one field that hides it.
     * Every row in that position in this capture carries a recorded end, so the
     * supersession pass steps over it at its FIRST guard and nothing past that
     * guard is exercised at all -- which would leave the rest of the rule untested
     * and passing.
     *
     * WHAT KEEPS THE WINDOW OPEN is the shape of the unreleased row rather than
     * any one guard, so the shape is asserted beside the outcome. The week being
     * prepared has no public window, so it sorts to the front of the series'
     * chain: it stands ahead of every released edition and never behind the newest
     * one, and the newest one is the tail that nothing follows. "publicTo stayed
     * null" on its own would pass just as well if the chain were ordered some
     * other way and the pass merely happened to find nothing to write.
     *
     * Cleared on the current EfS, because the weekly series is where the failure
     * costs the most: it releases every week, so a current edition ended at the
     * next week's planned start leaves the site serving the week before last for
     * days at a time rather than for a year.
     */
    @Test
    public void theCurrentEditionStaysOpenEndedWhereTheArchiveRecordsNoEnd() {
        List<Publication> rows = LegacyEstateFixture.publications();
        Publication weeklyCurrent = rows.stream()
                .filter(p -> WEEKLY_CURRENT.equals(p.getPublicationId()))
                .findFirst().orElseThrow();
        assertNotNull(weeklyCurrent.getPublishDateTo(),
                "the current EfS already has no recorded end, so clearing it demonstrates nothing");
        Date plannedStartOfTheWeekAfter = rows.stream()
                .filter(p -> WEEKLY_IN_PREPARATION.equals(p.getPublicationId()))
                .findFirst().orElseThrow().getPublishDateFrom();
        assertNotNull(plannedStartOfTheWeekAfter,
                "the week being prepared carries no planned start, so there is no date in the "
                        + "archive that the current edition could wrongly be capped at");

        weeklyCurrent.setPublishDateTo(null);
        LegacyImportService.Plan cleared =
                importService.planFrom(LegacyEstateFixture.templates(), rows);

        PublicationIssue issue = cleared.issues().get(WEEKLY_CURRENT);
        assertNotNull(issue);
        assertNull(issue.getPublicTo(),
                "the current EfS was closed at " + stamp(issue.getPublicTo()) + ", and the week "
                        + "after it is still unpublished (it plans to open at "
                        + stamp(plannedStartOfTheWeekAfter) + "). Until that release actually "
                        + "happens the site's newest notice-to-mariners is the week before last.");

        // And the reason it stayed open, so the assertion above cannot pass for
        // some other one.
        PublicationIssue preparing = cleared.issues().get(WEEKLY_IN_PREPARATION);
        assertNotNull(preparing);
        assertEquals(IssueStatus.OPEN, preparing.getStatus(),
                "the week being prepared is no longer unreleased, so it no longer demonstrates this");
        assertNull(preparing.getPublicFrom(),
                "the week being prepared states a public start, which is an instant the edition "
                        + "before it could be ended at");

        List<String> chain = chainOf(cleared, "weekly-ntm");
        assertEquals(WEEKLY_IN_PREPARATION, chain.get(0),
                "the week being prepared no longer sorts ahead of every released edition: " + chain);
        assertEquals(WEEKLY_CURRENT, chain.get(chain.size() - 1),
                "the current edition is no longer the end of the chain, so some other row of the "
                        + "series now stands behind it as its successor: " + chain);
    }

    /**
     * The weekly pair, named: the current EfS and the week being prepared.
     *
     * Stated on its own because a pinned pair says which two rows the general
     * assertions above are about, and because these two are what an admin looks at
     * on the day of a release.
     */
    @Test
    public void theWeekBeingPreparedIsOnNoPublicSite() {
        PublicationIssue preparing = issue(WEEKLY_IN_PREPARATION);
        assertEquals("weekly-ntm", preparing.getSeries().getSeriesId());
        assertEquals(IssueStatus.OPEN, preparing.getStatus());
        assertNull(preparing.getPublicFrom(),
                "the week being prepared is on the public site before anybody published it");
        assertNull(preparing.getPublicTo());

        PublicationIssue current = issue(WEEKLY_CURRENT);
        assertNotNull(current.effectiveCutoff());
        assertNotNull(preparing.effectiveCutoff());
        org.junit.jupiter.api.Assertions.assertTrue(
                current.effectiveCutoff().before(preparing.effectiveCutoff()),
                "the current edition must close before the week being prepared does, or they are "
                        + "not predecessor and successor at all");
    }

    // ------------------------------------------------------------------ helpers

    /** The unreleased issues, in the plan's own order. */
    private List<String> unreleasedIds() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, PublicationIssue> e : plan().issues().entrySet()) {
            if (e.getValue().getStatus() == IssueStatus.OPEN) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /**
     * For each series holding an unreleased issue, the newest PUBLISHED row before it.
     *
     * By effective cut-off, which is the order the issue list and the publish
     * action both read the chain in.
     */
    private Map<String, PublicationIssue> currentEditionOfASeriesWithOneInPreparation() {
        Map<String, List<PublicationIssue>> bySeries = new LinkedHashMap<>();
        for (PublicationIssue issue : plan().issues().values()) {
            if (issue.getSeries() != null) {
                bySeries.computeIfAbsent(issue.getSeries().getSeriesId(), k -> new ArrayList<>())
                        .add(issue);
            }
        }

        Map<String, PublicationIssue> out = new LinkedHashMap<>();
        for (List<PublicationIssue> group : bySeries.values()) {
            if (group.stream().noneMatch(i -> i.getStatus() == IssueStatus.OPEN)) {
                continue;
            }
            group.stream()
                    .filter(i -> i.getStatus() == IssueStatus.PUBLISHED)
                    .filter(i -> i.effectiveCutoff() != null)
                    .max(Comparator.comparing(PublicationIssue::effectiveCutoff))
                    .ifPresent(newest -> out.put(newest.getPublicId(), newest));
        }
        return out;
    }

    /**
     * One series' issues in the order the supersession pass reads them.
     *
     * The pass's own key, restated: public start first with a MISSING one before
     * every stated one, then the row's updated stamp, then the id. Restated rather
     * than reached for, because what is asserted is that this order puts a
     * particular row in a particular place -- a helper that called into the pass
     * would agree with it by construction.
     */
    private static List<String> chainOf(LegacyImportService.Plan plan, String seriesId) {
        return plan.issues().values().stream()
                .filter(i -> i.getSeries() != null && seriesId.equals(i.getSeries().getSeriesId()))
                .sorted(Comparator
                        .comparingLong((PublicationIssue i) -> millis(i.getPublicFrom()))
                        .thenComparingLong(i -> millis(i.getUpdated()))
                        .thenComparing(PublicationIssue::getPublicId))
                .map(PublicationIssue::getPublicId)
                .toList();
    }

    /** Null-safe ordering key: a missing date sorts first, never last. */
    private static long millis(Date d) {
        return d == null ? Long.MIN_VALUE : d.getTime();
    }

    private List<Publication> publicationsOf() {
        plan();
        return publications;
    }

    private static String describe(PublicationIssue issue, String publicationId) {
        String seriesId = issue.getSeries() == null ? "?" : issue.getSeries().getSeriesId();
        return seriesId + " / " + name(issue) + " (" + publicationId + ")";
    }

    private static String name(PublicationIssue issue) {
        for (PublicationIssueDesc d : issue.getDescs()) {
            if ("da".equals(d.getLang()) && d.getName() != null) {
                return d.getName();
            }
        }
        return issue.getDescs().isEmpty() ? "(unnamed)" : issue.getDescs().get(0).getName();
    }

    private static String stamp(Date d) {
        return d == null ? "none" : d.toInstant().toString();
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
