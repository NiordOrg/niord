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

package org.niord.core.publication.series.replay;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageTag;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.legacy.CutoffRecovery;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shadow-diff, driven over two consecutive fixture weeks.
 *
 * Two weeks rather than one because the second is where the chaining shows: its
 * window has to open at the FIRST week's cut-off, and that boundary is supplied
 * by the first week's own run. A single-week test would pass with the interval
 * logic entirely absent.
 *
 * Every fixture gets a UUID-scoped message series, because this database is
 * shared between suites -- a query that reached another suite's messages would
 * fail here for reasons that have nothing to do with the shadow-diff.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class ShadowDiffTest {

    @Inject
    EntityManager em;

    @Inject
    ShadowDiffService shadowDiff;

    private static final long HOUR = 3_600_000L;
    private static final long WEEK = 7 * 24 * HOUR;

    /**
     * What this test seeded, so it can take it away again.
     *
     * NOT "legacy-publications". That value means the legacy importer created the
     * row, and nothing here is an import -- but more to the point, it is what the
     * importer's own undo is scoped by, so a fixture wearing it is a fixture the
     * importer would consider its own.
     */
    private static final String SEEDED_BY = "shadow-diff-fixture";

    /**
     * Removes the fixtures, because leaving them behind breaks a DIFFERENT test.
     *
     * The series here carry imported issues with no publicTo, which is exactly the
     * shape CutoverPreflightTest asserts the estate does not have -- and the
     * pre-flight reads the whole schema rather than a fixture, because on a real
     * deployment the whole schema IS the estate. So every run of this class left
     * one series behind that made the pre-flight report the archive as forked.
     *
     * It failed with a different series id each run, which is what a leftover
     * fixture looks like and what a real defect does not. Measured before fixing:
     * one violating series, a fixture; zero real imported series. The estate was
     * clean the whole time.
     */
    @AfterEach
    @Transactional
    public void removeWhatWasSeeded() {
        List<Integer> seriesIds = em.createQuery(
                        "SELECT s.id FROM PublicationSeries s WHERE s.importSource = :src", Integer.class)
                .setParameter("src", SEEDED_BY).getResultList();
        if (seriesIds.isEmpty()) {
            return;
        }

        List<Integer> issueIds = em.createQuery(
                        "SELECT i.id FROM PublicationIssue i WHERE i.series.id IN :series", Integer.class)
                .setParameter("series", seriesIds).getResultList();

        if (!issueIds.isEmpty()) {
            em.createQuery("DELETE FROM IssueMember m WHERE m.issue.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
            em.createQuery("DELETE FROM IssueOverride o WHERE o.issue.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
            em.createQuery("DELETE FROM IssueAuditEntry a WHERE a.issue.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
        }

        // ShadowDiffRun keys on the series' STRING seriesId rather than on a
        // relation -- it outlives the rows it describes, which is the point of a
        // shadow diff -- so it has to be named before the series goes.
        List<String> seriesKeys = em.createQuery(
                        "SELECT s.seriesId FROM PublicationSeries s WHERE s.importSource = :src",
                        String.class)
                .setParameter("src", SEEDED_BY).getResultList();
        if (!seriesKeys.isEmpty()) {
            em.createQuery("DELETE FROM ShadowDiffRun r WHERE r.seriesId IN :keys")
                    .setParameter("keys", seriesKeys).executeUpdate();
        }

        // The desc rows go before their owners. Both are child tables with a real
        // FK, and Hibernate does not cascade a bulk DELETE -- so deleting the
        // parent first fails the constraint rather than taking the children with
        // it.
        if (!issueIds.isEmpty()) {
            em.createQuery("DELETE FROM PublicationIssueDesc d WHERE d.entity.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
        }
        em.createQuery("DELETE FROM PublicationIssue i WHERE i.series.id IN :series")
                .setParameter("series", seriesIds).executeUpdate();
        em.createQuery("DELETE FROM PublicationSeriesDesc d WHERE d.entity.id IN :series")
                .setParameter("series", seriesIds).executeUpdate();

        // The languages collection table has a real FK too, and no entity of its
        // own to delete through -- @ElementCollection means native SQL is the only
        // way to reach it. Missed on the first attempt: the series delete failed
        // on it after every other child was already gone.
        em.createNativeQuery(
                        "DELETE FROM PublicationSeries_languages WHERE PublicationSeries_id IN (:series)")
                .setParameter("series", seriesIds).executeUpdate();

        em.createQuery("DELETE FROM PublicationSeries s WHERE s.id IN :series")
                .setParameter("series", seriesIds).executeUpdate();
    }

    /**
     * Two consecutive weeks, both green.
     *
     * The tag holds exactly what the criteria select over each window, which is
     * the state the cutover precondition is waiting for. If this cannot be made
     * to agree in a fixture, nothing in production will agree either.
     */
    @Test
    @Transactional
    public void twoConsecutiveWeeksBothDiffClean() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t0 = new Date(System.currentTimeMillis() - 3 * WEEK);
        Date t1 = new Date(t0.getTime() + WEEK);
        Date t2 = new Date(t1.getTime() + WEEK);

        Message a = message(ms, new Date(t0.getTime() + HOUR));
        Message b = message(ms, new Date(t0.getTime() + 2 * HOUR));
        Message c = message(ms, new Date(t1.getTime() + HOUR));
        Message d = message(ms, new Date(t1.getTime() + 2 * HOUR));

        Publication template = template(series);
        Publication week1 = release(template, t1, tag(a, b));
        Publication week2 = release(template, t2, tag(c, d));

        ShadowDiffRun r1 = shadowDiff.diff(week1);
        assertNull(r1.getSkipReason(), "week 1 was comparable");
        assertTrue(r1.isGreen(),
                "week 1 diverged: missing " + r1.missing() + ", extra " + r1.extra());

        ShadowDiffRun r2 = shadowDiff.diff(week2);
        assertNull(r2.getSkipReason(), "week 2 was comparable");
        assertTrue(r2.isGreen(),
                "week 2 diverged: missing " + r2.missing() + ", extra " + r2.extra());

        // The chain: week 2's window opens where week 1 closed. Without this the
        // second week would resolve over an unbounded start and pick up week 1's
        // messages as extras -- so a green r2 above already depends on it, and
        // this asserts the mechanism rather than the symptom.
        assertEquals(t1, r2.getIntervalFrom(),
                "week 2 opens at week 1's cut-off");
    }

    /**
     * A real divergence is reported, not smoothed over.
     *
     * The mirror of the test above: same shape, one message tagged that the
     * criteria do not select. A shadow-diff that could only ever come back green
     * would be worthless, and nothing else here would notice.
     */
    @Test
    @Transactional
    public void aMessageTaggedButNotSelectedIsReportedAsMissing() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t0 = new Date(System.currentTimeMillis() - 3 * WEEK);
        Date t1 = new Date(t0.getTime() + WEEK);

        Message inWindow = message(ms, new Date(t0.getTime() + HOUR));

        // Published a week AFTER the cut-off, so no window ending at t1 selects
        // it -- but legacy recorded it anyway. This is the shape of the
        // LEGACY_TAG_STALENESS class.
        Message afterCutoff = message(ms, new Date(t1.getTime() + WEEK));

        ShadowDiffRun run = shadowDiff.diff(
                release(template(series), t1, tag(inWindow, afterCutoff)));

        assertNull(run.getSkipReason());
        assertTrue(run.missing().contains(afterCutoff.getUid()),
                "the tagged-but-unselected message is reported missing");
        assertEquals(1, run.getMissingCount());
        assertEquals(0, run.getExtraCount());
        assertTrue(!run.isGreen(), "a run carrying a delta is not green");
    }

    /**
     * A hand-replaced file is skipped (C6).
     *
     * That file was never generated from a member list, so "reproducible from
     * the member list" is not a property it has. Diffing it would manufacture a
     * divergence out of a document nobody generated -- and worse, it would count
     * against the series' green streak.
     */
    @Test
    @Transactional
    public void aHandReplacedFileIsSkippedRatherThanDiffed() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t0 = new Date(System.currentTimeMillis() - 2 * WEEK);
        Date t1 = new Date(t0.getTime() + WEEK);
        Message a = message(ms, new Date(t0.getTime() + HOUR));

        Publication week = release(template(series), t1, tag(a));
        importedIssueWithStickyFile(series, week);

        ShadowDiffRun run = shadowDiff.diff(week);

        assertEquals("FILE_REPLACED_BY_HAND", run.getSkipReason());
        assertEquals(0, run.getMissingCount());
        assertEquals(0, run.getExtraCount());
    }

    /**
     * A skipped run does not extend a green streak.
     *
     * Asserted on the stored row because that is what the endpoint reads: green
     * is true (nothing diverged) while skipReason is set (nothing was compared),
     * and it is the SECOND field that stops a week nobody could compare being
     * counted as evidence that it agreed.
     */
    @Test
    @Transactional
    public void aSkippedRunIsGreenButCarriesItsReason() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template(series), t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));
        importedIssueWithStickyFile(series, week);

        ShadowDiffRun run = shadowDiff.diff(week);

        assertTrue(run.isGreen(), "nothing diverged, because nothing was compared");
        assertNotNull(run.getSkipReason(), "and the reason is what keeps that honest");
    }

    /**
     * An IN_FORCE issue is compared with NO lower bound.
     *
     * Interval says it outright: previousCutoff is "null when there is no lower
     * bound: the first issue of a series, and every IN_FORCE_AT_CUTOFF issue,
     * which never has one". The diff used to fall back to the previous stamp
     * whenever the bound was null, inventing a one-week window for an issue that
     * has none by definition.
     *
     * The assertion is on what the RUN RECORDS, and that is not a hedge -- it is
     * the whole of what the fallback got wrong here. MemberResolutionService reads
     * a lower bound only under PUBLISHED_IN_INTERVAL, so an in-force resolution
     * never saw the fabricated value and membership was unaffected. Asserting on
     * members would therefore pass with the fallback restored.
     *
     * It matters beyond reporting in one case: criteriaFor classifies by the
     * RELEASE's filter, so a blank filter on an in-force series resolves as
     * PUBLISHED_IN_INTERVAL, reads the bound, and gets a week instead of years.
     */
    @Test
    @Transactional
    public void anInForceIssueIsComparedWithNoLowerBound() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);
        series.setTimeRelation(TimeRelation.IN_FORCE_AT_CUTOFF);
        series.setAliveAtCutoff(true);
        em.flush();

        Publication template = template(series);
        Date t1 = new Date(System.currentTimeMillis() - WEEK);

        // A PREDECESSOR with a stamped cut-off. Without one there is nothing for the
        // old fallback to find, and the test passes whether the bug is present or
        // not -- which is what it did on the first attempt.
        Publication earlier = release(template, new Date(t1.getTime() - WEEK),
                tag(message(ms, new Date(t1.getTime() - 2 * WEEK))));
        PublicationIssue earlierIssue = importedIssue(series, earlier);
        earlierIssue.setCutoffStampedAt(new Date(t1.getTime() - WEEK));
        em.flush();

        Publication week = release(template, t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));

        // An in-force issue carries no lower bound, by construction.
        PublicationIssue issue = importedIssue(series, week);
        issue.setIntervalFrom(null);
        issue.setCutoffStampedAt(t1);
        em.flush();

        ShadowDiffRun run = shadowDiff.diff(week);

        assertNull(run.getIntervalFrom(),
                "a null lower bound is the ANSWER for an in-force issue, not a gap to fill "
                        + "from the previous release -- filling it bounds a series that reaches "
                        + "back years to a single week");
    }

    /**
     * An annual in-force edition is compared at the END of its changeover day,
     * and only then does it agree with the tag that recorded it.
     *
     * The changeover is a day's work, not a moment's. On "EfS A - 2025" the public
     * window was opened at 10:28:17, the 2024 notices were cancelled at 11:18 and
     * the 2025 notices published at 11:28 -- so a cut-off at the opening instant
     * resolves the list from BEFORE the changeover and the diff reported 29
     * missing and 29 extra against a tag holding the new list. 2024 and 2022 had
     * the same shape; 2026 and 2023 were green only because those years' notices
     * happened to go out before the window was opened, which is luck rather than
     * correctness.
     *
     * BOTH SIDES ARE ASSERTED, on one fixture, because "green" on its own does not
     * say the rule did anything: the same fixture read at the opening instant is
     * red with exactly one message missing and one extra, which is the shape the
     * estate showed at 29x.
     */
    @Test
    @Transactional
    public void anAnnualInForceEditionIsComparedAtTheEndOfItsChangeoverDay() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);
        series.setCadence(SeriesCadence.YEARLY);
        series.setTimeRelation(TimeRelation.IN_FORCE_AT_CUTOFF);
        series.setAliveAtCutoff(true);
        em.flush();

        ZoneId zone = series.cutoffZone();
        ZonedDateTime day = ZonedDateTime.now(zone).minusYears(1)
                .withMonth(2).withDayOfMonth(7).withHour(10).withMinute(28).withSecond(17)
                .withNano(0);
        Date windowOpens = Date.from(day.toInstant());
        Date cancelledAt = Date.from(day.withHour(11).withMinute(18).withSecond(0).toInstant());
        Date publishedAt = Date.from(day.withHour(11).withMinute(29).withSecond(0).toInstant());

        // Last year's notice, withdrawn during the changeover, and this year's,
        // published fifty minutes later. Both are resolvable -- a cancelled
        // message stays public -- and which of them is in force is decided
        // entirely by where the cut-off falls on this one day.
        Message lastYear = message(ms, Date.from(day.minusYears(1).toInstant()));
        lastYear.setPublishDateTo(cancelledAt);
        lastYear.setStatus(Status.CANCELLED);
        Message thisYear = message(ms, publishedAt);
        em.flush();

        Publication template = template(series);
        Publication edition = release(template, windowOpens, tag(thisYear));
        // The in-force regime, declared on the release itself -- which is where
        // the diff reads it from, not from the series row.
        edition.setMessageTagFilter("msg.status == Status.PUBLISHED");
        em.flush();

        PublicationIssue issue = importedIssue(series, edition);
        issue.setIntervalFrom(null);
        issue.setIntervalTo(windowOpens);
        issue.setCutoffStampedAt(
                CutoffRecovery.fromPublicWindowOpen(windowOpens, zone).cutoff());
        em.flush();

        ShadowDiffRun green = shadowDiff.diff(edition);

        assertNull(green.getSkipReason(), "nothing here is uncomparable");
        assertEquals(issue.getCutoffStampedAt(), green.getCutoffAt(),
                "the diff must read the issue's own cut-off, which is the end of the changeover day");
        assertTrue(green.isGreen(),
                "the edition resolves to the list it shipped: missing " + green.missing()
                        + ", extra " + green.extra());

        // The counterfactual, on the same fixture: move the cut-off back to the
        // instant the window opened and the edition resolves to last year's list.
        shadowDiff.reset();
        issue.setCutoffStampedAt(windowOpens);
        em.flush();

        ShadowDiffRun red = shadowDiff.diff(edition);

        assertFalse(red.isGreen(),
                "at the opening instant the changeover has not happened yet, so this cannot agree");
        assertEquals(1, red.missing().size(),
                "this year's notice was published after the window opened, so it is not selected");
        assertEquals(1, red.extra().size(),
                "and last year's, cancelled an hour later, still is");
        assertTrue(red.extra().contains(lastYear.getUid()));
        assertTrue(red.missing().contains(thisYear.getUid()));
    }

    /**
     * An interval that does not run forwards is SKIPPED, not thrown.
     *
     * Interval refuses to be built from one, and the sweep catches the throw and
     * steps over the release -- so the comparison is absent, the release never
     * settles, and nothing says why. Three issues in the estate are like this.
     */
    @Test
    @Transactional
    public void anEmptyIntervalIsSkippedWithAReasonRatherThanThrowing() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template(series), t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));

        // The close lands exactly on the open.
        PublicationIssue issue = importedIssue(series, week);
        issue.setIntervalFrom(t1);
        issue.setCutoffStampedAt(t1);
        em.flush();

        ShadowDiffRun run = shadowDiff.diff(week);

        assertEquals("EMPTY_INTERVAL", run.getSkipReason(),
                "a zero-length period contains nothing; saying so leaves a countable row "
                        + "instead of a release that quietly never gets compared");
    }

    /**
     * A release whose tag is EMPTY is skipped, not reported as all-extra.
     *
     * undiffedReleases already refuses a release with no tag -- the comparison
     * would be against absence -- and a tag holding nothing is that same absence
     * with a row in front of it. Nine of the eleven NCAGS annex editions are link
     * publications with no membership whose interval is a multi-year visibility
     * window, so everything published in those years resolved into them.
     */
    @Test
    @Transactional
    public void anEmptyTagIsSkippedRatherThanReportedAsAllExtra() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        // A message inside the window, and a tag that records none of it.
        message(ms, new Date(t1.getTime() - HOUR));
        Publication week = release(template(series), t1, tag());

        PublicationIssue issue = importedIssue(series, week);
        issue.setIntervalFrom(new Date(t1.getTime() - WEEK));
        issue.setCutoffStampedAt(t1);
        em.flush();

        ShadowDiffRun run = shadowDiff.diff(week);

        assertEquals("EMPTY_TAG", run.getSkipReason(),
                "nothing was recorded, so nothing can be compared -- reporting the resolution "
                        + "as extra would make an unrecorded publication look like a defect");
        assertTrue(run.extra().isEmpty());
    }

    // ------------------------------------------- the interval it compares over

    /**
     * The comparison uses the ISSUE's interval, not the nominal release time.
     *
     * The release action runs a little after the bound it closes -- twenty to
     * thirty minutes in this archive -- and it sweeps up everything published up
     * to the moment it runs. Bounding the resolution at the NOMINAL time therefore
     * drops exactly those messages, and they are in the tag, so every one of them
     * was reported as a missing member.
     *
     * Measured before the fix: every still-PUBLISHED member the diff called missing
     * had been published 20-35 minutes after the nominal bound.
     */
    @Test
    @Transactional
    public void theComparisonUsesTheIssuesOwnCutoffRatherThanTheNominalReleaseTime() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date nominal = new Date(System.currentTimeMillis() - WEEK);
        Date actuallyReleased = new Date(nominal.getTime() + 30 * 60_000L);

        // Published AFTER the nominal bound but BEFORE the release ran: in the tag,
        // and invisible to a resolution bounded at the nominal time.
        Message late = message(ms, new Date(nominal.getTime() + 10 * 60_000L));
        Publication week = release(template(series), nominal, tag(late));

        PublicationIssue issue = importedIssue(series, week);
        issue.setIntervalFrom(new Date(nominal.getTime() - WEEK));
        issue.setCutoffStampedAt(actuallyReleased);
        em.flush();

        ShadowDiffRun run = shadowDiff.diff(week);

        assertEquals(actuallyReleased, run.getCutoffAt(),
                "the comparison must bound at the moment membership was frozen, not at the "
                        + "nominal release time the archive never actually used");
        assertTrue(run.missing().isEmpty(),
                "a message published between the nominal bound and the release is IN the tag; "
                        + "bounding at the nominal time reports it missing forever");
    }

    /**
     * reset unsettles a release, so the ordinary sweep recomputes it.
     *
     * The key covers the LEGACY inputs; the comparison also depends on the
     * imported side and on the diff logic. When the logic changes every stored
     * verdict is stale and no query reselects it, and the only other remedy is
     * hand-written SQL against the run table.
     */
    @Test
    @Transactional
    public void resetUnsettlesAReleaseSoTheSweepRecomputesIt() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template(series), t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));

        assertNull(shadowDiff.diff(week).getSkipReason());
        assertFalse(shadowDiff.undiffedReleases().stream()
                        .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())),
                "settled, so the ordinary sweep will not look at it again");

        shadowDiff.reset();

        assertTrue(shadowDiff.undiffedReleases().stream()
                        .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())),
                "after a reset the sweep must see it again -- otherwise a change to the diff "
                        + "logic can never be applied to anything already compared");

        shadowDiff.diffById(week.getPublicationId());
        assertEquals(1, runsFor(week).size(), "one run per stamp, by constraint");
        assertNull(runsFor(week).get(0).getSkipReason());
    }

    // ------------------------------------------------ a release still recording

    /**
     * A RECORDING release is not comparable: its tag is still being written to.
     *
     * The status means exactly that -- "published messages will be added to the
     * publication message tag" -- so diffing one diffs a moving target. Observed
     * on the deployed estate: legacy removed a withdrawn message from an open P&T
     * week three days after that week's cut-off, and the diff reported the new
     * engine as wrong for having frozen what was true AT the cut-off.
     *
     * It matters because the cutover gate counts consecutive green weeks from the
     * NEWEST release, and the newest release is always the recording one. One
     * mid-window edit would otherwise hold a series back indefinitely.
     */
    @Test
    @Transactional
    public void areleaseStillRecordingIsNotSelected() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template(series), t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));
        week.setStatus(PublicationStatus.RECORDING);
        em.merge(week);
        em.flush();

        assertFalse(shadowDiff.undiffedReleases().stream()
                        .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())),
                "a release whose tag is still being written to was offered to the sweep");
    }

    /**
     * And a verdict already taken while it was recording is DISCARDED.
     *
     * Excluding it from selection is not enough on its own, because the verdict is
     * sticky. A run is keyed on (publicationId, p.updated), and mutating a tag
     * does not touch the publication's own updated stamp -- the real P&T release
     * that failed carries updated = 12 Aug against a 19 Aug cut-off. So a release
     * compared once while recording is never reselected, and a false red outlives
     * the condition that caused it: the series never shows a green streak again.
     */
    @Test
    @Transactional
    public void averdictTakenWhileRecordingIsDiscarded() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template(series), t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));

        // Compared while it was still closed-looking, then found to be recording.
        assertNull(shadowDiff.diff(week).getSkipReason());
        assertEquals(1, runsFor(week).size());

        week.setStatus(PublicationStatus.RECORDING);
        em.merge(week);
        em.flush();

        assertEquals(1, shadowDiff.discardRecordingComparisons(),
                "the stale verdict was not discarded");
        em.flush();
        assertEquals(0, runsFor(week).size(),
                "a comparison taken against a moving target is not evidence, and keeping it "
                        + "would outlive the condition that made it wrong");
    }

    /** Once the window closes, the sweep picks it up again. */
    @Test
    @Transactional
    public void aclosedReleaseBecomesComparableAgain() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template(series), t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));
        week.setStatus(PublicationStatus.RECORDING);
        em.merge(week);
        em.flush();

        assertFalse(shadowDiff.undiffedReleases().stream()
                .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())));

        week.setStatus(PublicationStatus.ACTIVE);
        em.merge(week);
        em.flush();

        assertTrue(shadowDiff.undiffedReleases().stream()
                        .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())),
                "a closed release must become comparable, or excluding it while open would "
                        + "mean never comparing it at all");
    }

    // ------------------------------------------------ a skip is not an answer

    /**
     * A skipped release is compared again once the imported side exists.
     *
     * This is the one that bit. The run is keyed on the LEGACY row's updated
     * stamp, but every skip reason is a fact about the IMPORTED side -- no series,
     * no membership semantics, a file replaced by hand -- and the imported side is
     * replaced wholesale every time the archive is re-imported.
     *
     * One scheduled tick fired while an undo had emptied the estate. It wrote
     * NO_IMPORTED_SERIES against all 1,077 releases, and because a frozen archive's
     * updated stamp never changes again, every one of them was excluded from ever
     * being compared. The cutover precondition counts green weeks; it could not
     * have accumulated a single one.
     */
    @Test
    @Transactional
    public void aSkippedReleaseIsComparedOnceTheImportedSideExists() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        // A template no series points at yet: exactly the shape of an estate that
        // has been undone, or not imported yet.
        Publication orphanTemplate = new Publication();
        orphanTemplate.setPublicationId(UUID.randomUUID().toString());
        em.persist(orphanTemplate);
        em.flush();

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(orphanTemplate, t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));

        ShadowDiffRun skipped = shadowDiff.diff(week);
        assertEquals("NO_IMPORTED_SERIES", skipped.getSkipReason(),
                "nothing is imported, so there is nothing to compare against");

        // The archive is imported: the series now resolves.
        series.setLegacyTemplateId(orphanTemplate.getPublicationId());
        em.flush();

        ShadowDiffRun compared = shadowDiff.diff(week);
        assertNull(compared.getSkipReason(),
                "the imported side exists now, so this release must actually be compared "
                        + "-- caching the skip is caching an answer about state that is gone");

        List<ShadowDiffRun> runs = runsFor(week);
        assertEquals(1, runs.size(),
                "the worthless skip is replaced, not kept beside the comparison");
        assertNull(runs.get(0).getSkipReason());
    }

    /**
     * A release with NO TEMPLATE resolves through its imported issue.
     *
     * Thirteen orphan publications were reported NO_IMPORTED_SERIES on every run,
     * for a reason that was never true. seriesFor asked only "which series carries
     * this template id", and substituted the PUBLICATION id when there was no
     * template -- but an orphan-grouped series is authored by the grouping pass and
     * carries no template id at all, so the lookup could not match by construction.
     *
     * Not cosmetic: a release that cannot be compared is never a green week, and
     * the cutover precondition counts green weeks.
     *
     * The same lookup now also covers a REDIRECTED template -- the six "DONT USE"
     * clones and NCAGS 2021 are rulings that a template is not a series, so nothing
     * carries their template id either.
     */
    @Test
    @Transactional
    public void anorphanReleaseResolvesThroughItsImportedIssue() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        // NO TEMPLATE. This is what an orphan publication actually looks like.
        Publication week = release(null, t1, tag(message(ms, new Date(t1.getTime() - HOUR))));
        importedIssue(series, week);

        ShadowDiffRun run = shadowDiff.diff(week);

        assertNull(run.getSkipReason(),
                "an orphan release whose issue is imported under a series was reported "
                        + "unimportable; it can never count as a green week");
        assertEquals(series.getSeriesId(), run.getSeriesId(),
                "the run names no series, so the report cannot say where it belongs");
    }

    /**
     * Re-diffing something that still cannot be compared does not accumulate rows.
     *
     * Without this the fix above trades one bug for a slower one: a release nothing
     * can ever compare -- a hand-replaced file, say -- would collect a row every
     * hour for as long as the system runs.
     */
    @Test
    @Transactional
    public void aReleaseThatStillCannotBeComparedDoesNotAccumulateRows() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template(series), t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));
        importedIssueWithStickyFile(series, week);

        for (int tick = 0; tick < 3; tick++) {
            assertNotNull(shadowDiff.diff(week).getSkipReason(),
                    "a hand-replaced file is never comparable, on any tick");
        }

        assertEquals(1, runsFor(week).size(),
                "three ticks, one row -- a skip carries no evidence, so there is nothing to keep");
    }

    /**
     * The scheduler picks a skipped release back up, and leaves a compared one alone.
     *
     * Through runOnce, which is the path that actually failed: the defect was never
     * in diff() but in the query feeding it, which treated any row at the stamp as
     * settling the release. One row per release-stamp is a schema constraint
     * (UK_shadowdiff_publication_stamp), so a skip is not merely stale evidence --
     * it occupies the only slot the real comparison could ever use.
     */
    @Test
    @Transactional
    public void theSchedulerRetriesASkippedReleaseAndLeavesAComparedOneAlone() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);

        Publication orphanTemplate = new Publication();
        orphanTemplate.setPublicationId(UUID.randomUUID().toString());
        em.persist(orphanTemplate);
        em.flush();

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(orphanTemplate, t1,
                tag(message(ms, new Date(t1.getTime() - HOUR))));
        week.setUpdated(new Date(t1.getTime() + HOUR));
        em.flush();

        assertNotNull(shadowDiff.diff(week).getSkipReason(), "nothing is imported yet");

        // The archive lands. The legacy row has not changed -- it never does again
        // for a frozen archive -- so only the skip clause can bring this back.
        series.setLegacyTemplateId(orphanTemplate.getPublicationId());
        em.flush();

        // The query is what the defect lived in, so it is asserted directly.
        // runOnce() now opens a transaction per release -- correct in production,
        // invisible to a fixture this test has not committed.
        assertTrue(shadowDiff.undiffedReleases().stream()
                        .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())),
                "a release whose only run is a SKIP must be offered for diffing again; that is "
                        + "the clause whose absence held the only slot forever");

        shadowDiff.diffById(week.getPublicationId());
        List<ShadowDiffRun> afterImport = runsFor(week);
        assertEquals(1, afterImport.size(), "one row per release-stamp, by constraint");
        assertNull(afterImport.get(0).getSkipReason(),
                "the scheduler must retry a release it could not compare; otherwise the skip "
                        + "holds the only slot forever and no green week can ever be recorded");

        // And a settled release is left alone: the comparison is the evidence the report
        // counts, so re-running must not disturb it.
        assertFalse(shadowDiff.undiffedReleases().stream()
                        .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())),
                "a COMPARISON settles the release: the evidence the report counts must not be "
                        + "discarded and recomputed on every sweep");
    }

    /**
     * A skip that is a fact about the LEGACY release is recorded once and left
     * alone. NO_MEMBERSHIP_SEMANTICS will never clear -- the publication has no
     * member list and never will -- so re-selecting it every sweep is what kept
     * "remaining" from ever reaching zero, forty batches over, while the re-diffed
     * rows' fresh stamps sat at the head of every streak count.
     */
    @Test
    @Transactional
    public void aSkipThatCannotClearIsNotOfferedForDiffingAgain() {
        String seriesKey = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = messageSeries(seriesKey);
        PublicationSeries series = importedSeries(seriesKey);
        // No member list, and never one: the shape of an annex or an uploaded document.
        series.setCriteria(null);

        Publication template = new Publication();
        template.setPublicationId(UUID.randomUUID().toString());
        em.persist(template);
        series.setLegacyTemplateId(template.getPublicationId());
        em.flush();

        Date t1 = new Date(System.currentTimeMillis() - WEEK);
        Publication week = release(template, t1, tag(message(ms, new Date(t1.getTime() - HOUR))));

        ShadowDiffRun skipped = shadowDiff.diff(week);
        assertEquals("NO_MEMBERSHIP_SEMANTICS", skipped.getSkipReason());

        assertFalse(shadowDiff.undiffedReleases().stream()
                        .anyMatch(p -> p.getPublicationId().equals(week.getPublicationId())),
                "a skip that is a property of the legacy release is terminal: offering it again "
                        + "every sweep is what stopped the batch loop from converging");
    }

    private List<ShadowDiffRun> runsFor(Publication release) {
        em.flush();
        return em.createQuery(
                        "SELECT r FROM ShadowDiffRun r WHERE r.legacyPublicationId = :id",
                        ShadowDiffRun.class)
                .setParameter("id", release.getPublicationId())
                .getResultList();
    }

    // ------------------------------------------------------------- fixtures

    private MessageSeries messageSeries(String seriesId) {
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId(seriesId);
        ms.setMainType(MainType.NM);
        em.persist(ms);
        em.flush();
        return ms;
    }

    /**
     * A message, WELL FORMED.
     *
     * mainType, type and status are all set: this database is shared, and
     * IssueInvariantsTest picks fixtures with "ORDER BY m.id", so a malformed
     * row left here fails a different suite nowhere near its cause.
     */
    private Message message(MessageSeries series, Date publishedAt) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(series);
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(publishedAt);
        em.persist(m);
        em.flush();
        return m;
    }

    private MessageTag tag(Message... messages) {
        MessageTag t = new MessageTag();
        t.setTagId(UUID.randomUUID().toString());
        t.setName("shadow-" + UUID.randomUUID().toString().substring(0, 8));
        t.getMessages().addAll(List.of(messages));
        em.persist(t);
        em.flush();
        return t;
    }

    /**
     * The template every release of a series hangs off.
     *
     * Weekly releases are siblings under one template, and the imported series
     * carries THAT id -- not any individual release's. Modelling it the other
     * way round (each release claiming the series) is what the first version of
     * this fixture did, and it meant the second week silently orphaned the
     * first: seriesFor(week1) then found nothing and reported
     * NO_IMPORTED_SERIES, which reads like a service bug and was a fixture bug.
     */
    private Publication template(PublicationSeries series) {
        Publication t = new Publication();
        t.setPublicationId(UUID.randomUUID().toString());
        em.persist(t);
        em.flush();

        series.setLegacyTemplateId(t.getPublicationId());
        em.flush();
        return t;
    }

    /** A legacy publication standing in for one release of that template. */
    private Publication release(Publication template, Date cutoff, MessageTag tag) {
        Publication p = new Publication();
        p.setPublicationId(UUID.randomUUID().toString());
        p.setTemplate(template);
        p.setMessageTag(tag);
        p.setPublishDateFrom(cutoff);

        // The blank filter: the sticky regime, which translates to
        // PUBLISHED_IN_INTERVAL with aliveAtCutoff off. Chosen because it is the
        // one the fixture's criteria can actually agree with.
        p.setMessageTagFilter(null);

        em.persist(p);
        em.flush();
        return p;
    }

    private PublicationSeries importedSeries(String messageSeriesId) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.DRAFT);
        s.setImportSource(SEEDED_BY);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of(messageSeriesId)));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        PublicationSeriesDesc d = s.createDesc("da");
        d.setName("Shadow-diff fixture");

        em.persist(s);
        em.flush();
        return s;
    }

    /** An imported issue whose file somebody replaced by hand. */
    /** An imported issue for a release, with no sticky file. */
    private PublicationIssue importedIssue(PublicationSeries series, Publication release) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setPublicId(UUID.randomUUID().toString());
        i.setLegacyPublicationId(release.getPublicationId());
        i.setRepoPath("shadow/" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(i);
        em.flush();
        return i;
    }

    private PublicationIssue importedIssueWithStickyFile(PublicationSeries series,
                                                         Publication release) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setPublicId(UUID.randomUUID().toString());
        i.setLegacyPublicationId(release.getPublicationId());
        i.setRepoPath("shadow/" + UUID.randomUUID().toString().substring(0, 8));

        PublicationIssueDesc d = i.createDesc("da");
        d.setName("Hand-replaced");
        d.setFileSourceSticky(true);

        em.persist(i);
        em.flush();
        return i;
    }
}
