package org.niord.core.publication.series.replay;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageTag;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
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
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B6.2. The shadow-diff, driven over two consecutive fixture weeks.
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

        shadowDiff.runOnce();
        List<ShadowDiffRun> afterImport = runsFor(week);
        assertEquals(1, afterImport.size(), "one row per release-stamp, by constraint");
        assertNull(afterImport.get(0).getSkipReason(),
                "the scheduler must retry a release it could not compare; otherwise the skip "
                        + "holds the only slot forever and no green week can ever be recorded");

        // And a settled release is left alone: the comparison is the evidence B6.3
        // counts, so re-running must not disturb it.
        Date settledAt = afterImport.get(0).getComparedAt();
        shadowDiff.runOnce();
        List<ShadowDiffRun> afterRerun = runsFor(week);
        assertEquals(1, afterRerun.size());
        assertEquals(settledAt, afterRerun.get(0).getComparedAt(),
                "a compared release is settled and must not be re-compared");
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
        s.setImportSource("legacy-publications");
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
