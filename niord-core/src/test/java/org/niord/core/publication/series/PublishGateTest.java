package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release rail, enforced.
 *
 * The checklist was server-authoritative on the way OUT and unread on the way
 * IN: an admin could be shown a red row and publish anyway, and the publish
 * stamped a cut-off that nothing can un-stamp. These are the refusals, one per
 * BLOCK row, each with the catalogued code a client can branch on.
 *
 * Every case asserts that NOTHING was stamped. That is the property that matters
 * -- a refusal after the stamp is not a refusal, it is a half-published issue.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class PublishGateTest {

    @Inject
    IssuePublishService publishService;

    @Inject
    IssuePreviewService previews;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(SeriesStatus status) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(status);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
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
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    private PublicationIssue issue(PublicationSeries s, Date intervalFrom) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(intervalFrom);
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        i.createDesc("da").setName("Test issue");
        em.persist(i);
        previews.record(i, "da", "preview.pdf",
                "preview-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return i;
    }

    /** A released neighbour, so the bracket has something to refuse against. */
    private PublicationIssue released(PublicationSeries s, Date stamp) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(stamp);
        i.setCutoffSource("STAMPED_AT_PUBLISH");
        i.setPublicFrom(stamp);
        i.setPublicWindowSource(PublicWindowSource.DERIVED);
        i.createDesc("da").setName("Neighbour");
        em.persist(i);
        return i;
    }

    private IssueLifecycleService.TransitionRefusedException refused(PublicationIssue i, Date stamp) {
        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> publishService.publish(i.getId(),
                                new IssuePublishService.PublishRequest(false,
                                        IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp)));
        assertNull(i.getCutoffStampedAt(),
                "the cut-off was stamped despite the refusal; a stamp cannot be taken back");
        assertEquals(IssueStatus.OPEN, i.getStatus(), "the status flipped despite the refusal");
        // That each of these codes is CATALOGUED -- one code, one HTTP status -- is
        // asserted in the web module, where the catalogue lives.
        return e;
    }

    // ------------------------------------------------------------------ the rows

    /** A DRAFT series has not been finished; nothing may go public from it. */
    @Test
    @Transactional
    public void aDraftSeriesCannotPublish() {
        PublicationSeries s = series(SeriesStatus.DRAFT);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        assertEquals("SERIES_NOT_ACTIVE", refused(i, new Date(1_700_000_000_000L)).code());
    }

    /**
     * A query-backed series that names no report has nothing to render.
     *
     * The whole pre-flight guarantee for a generated series' languages: publish
     * writes their file, so the only thing that CAN be checked beforehand is that
     * there is something to write it with.
     */
    @Test
    @Transactional
    public void aGeneratedSeriesWithNoReportCannotPublish() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        s.setReportId(null);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        assertEquals("REPORT_NOT_CONFIGURED", refused(i, new Date(1_700_000_000_000L)).code());
    }

    /** An uploaded publication's bytes are a precondition, not an output. */
    @Test
    @Transactional
    public void anUploadedIssueWithNoBytesCannotPublish() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.UPLOADED_FILE);
        s.setReportId(null);
        s.setTimeRelation(null);
        s.setCriteria(null);
        s.setAliveAtCutoff(null);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        assertEquals("MISSING_FILE_FOR_LANGUAGE", refused(i, new Date(1_700_000_000_000L)).code());
    }

    /** A citable series with no reference format renders a blank citation. */
    @Test
    @Transactional
    public void aCitableSeriesWithNoReferenceFormatCannotPublish() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        s.setMessagePublication(MessagePublication.EXTERNAL);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        assertEquals("REFERENCE_FORMAT_MISSING_LANGUAGE",
                refused(i, new Date(1_700_000_000_000L)).code());
    }

    /**
     * A cut-off at or before the predecessor's is a 400, not a 500.
     *
     * The interval it would build is empty, and the half-open Interval refused it
     * with an IllegalArgumentException no mapper knew -- so an admin choosing an
     * instant one second too early got a bare 500 with a stack trace.
     */
    @Test
    @Transactional
    public void aCutoffAtOrBeforeThePredecessorIsRefusedWithACode() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        Date predecessorStamp = new Date(1_700_000_000_000L);
        released(s, predecessorStamp);
        PublicationIssue i = issue(s, predecessorStamp);
        em.flush();

        assertEquals("CUTOFF_BEFORE_PREVIOUS", refused(i, predecessorStamp).code());
        assertEquals("CUTOFF_BEFORE_PREVIOUS",
                refused(i, new Date(predecessorStamp.getTime() - 1)).code());
    }

    /**
     * And its mirror, which is what makes recovering a missing week safe.
     *
     * Stamping above an issue that has already released would cap the live one's
     * window and make the recovered two-year-old issue the site's current
     * publication -- the flagship gap-recovery flow, publishing the wrong document.
     */
    @Test
    @Transactional
    public void aCutoffAtOrAfterTheSuccessorIsRefusedWithACode() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        Date successorStamp = new Date(1_700_600_000_000L);
        released(s, successorStamp);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        assertEquals("CUTOFF_AFTER_SUCCESSOR", refused(i, successorStamp).code());
        assertEquals("CUTOFF_AFTER_SUCCESSOR",
                refused(i, new Date(successorStamp.getTime() + 1)).code());
    }

    /** A cut-off in the future freezes the list before its window closed. */
    @Test
    @Transactional
    public void aFutureCutoffIsRefusedWithACode() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        assertEquals("CUTOFF_IN_FUTURE",
                refused(i, new Date(System.currentTimeMillis() + 86_400_000L)).code());
    }

    // ------------------------------------------------- what publish then records

    /**
     * The stamp's provenance, and the flag a retro-created issue arrived with.
     *
     * NOW and a chosen instant are stamped identically, so the source column is
     * the only thing that tells them apart afterwards. And an issue whose cut-off
     * was RECONSTRUCTED has now had one stamped by this system: leaving the flag
     * set badges a genuinely stamped instant as recovered for the rest of its life.
     */
    @Test
    @Transactional
    public void publishRecordsWhereTheCutoffCameFromAndClearsTheReconstructedFlag() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);

        PublicationIssue chosen = issue(s, new Date(1_699_000_000_000L));
        chosen.setCutoffReconstructed(true);
        em.flush();
        publishService.publish(chosen.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        assertEquals("STAMPED_MANUAL_TIME", chosen.getCutoffSource(),
                "an admin-chosen instant is indistinguishable from a release-moment stamp without this");
        assertTrue(!chosen.isCutoffReconstructed(),
                "the issue still claims a reconstructed cut-off after this system stamped one");

        PublicationSeries other = series(SeriesStatus.ACTIVE);
        PublicationIssue now = issue(other, new Date(System.currentTimeMillis() - 7 * 24 * 3600_000L));
        em.flush();
        publishService.publish(now.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null, null));
        assertEquals("STAMPED_AT_PUBLISH", now.getCutoffSource());
    }

    /**
     * Step 14 creates nothing when the chain has already moved past this stamp.
     *
     * Deterministic, and no concurrency involved: publishing a recovered 2024 week
     * would otherwise mint a second OPEN issue opening at the 2024 stamp beside
     * the real current one, and the editor's publication panel then reports every
     * message published since as a live member of a two-year-old period.
     */
    @Test
    @Transactional
    public void noSuccessorIsMintedWhenALaterOrOpenIssueAlreadyExists() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        s.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);

        // A week that already released, above the one being recovered.
        released(s, new Date(1_700_600_000_000L));
        PublicationIssue recovered = issue(s, new Date(1_698_000_000_000L));
        em.flush();

        var result = publishService.publish(recovered.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_699_000_000_000L)));
        assertNull(result.successorId(),
                "a successor was minted beside an issue that had already released above it");

        // And the same when the issue ahead is still OPEN: the next one exists and
        // somebody is working on it.
        PublicationSeries chain = series(SeriesStatus.ACTIVE);
        chain.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);
        PublicationIssue publishing = issue(chain, new Date(1_699_000_000_000L));
        issue(chain, new Date(1_700_000_000_000L));
        em.flush();

        assertNull(publishService.publish(publishing.getId(),
                        new IssuePublishService.PublishRequest(false,
                                IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                                new Date(1_700_000_000_000L)))
                .successorId(),
                "a second OPEN issue was minted beside the one already being worked on");
    }

    /**
     * T0 gives an issue a period, its numbers and its names -- and publish
     * re-derives them from the instant that was actually stamped.
     *
     * Without the first half a natively created issue has no effective cut-off at
     * all: it sorts below every dated issue in its own series, gap detection skips
     * it, and the report header prints "Uge , ". Without the second, a week
     * released late is named for the week it was assembled in rather than the week
     * it closed.
     */
    @Test
    @Transactional
    public void anIssueIsBornWithAPeriodAndNumbersAndIsRenumberedAtPublish() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        // Wednesday 9 July 2026, 09:00 UTC -- week 28 opens.
        Date opens = new Date(1_783_155_600_000L);
        PublicationIssue i = lifecycle.create(s, opens, IntervalBoundSource.STAMPED, null);
        em.flush();

        assertNotNull(i.getIntervalTo(), "T0 derived no nominal close, so the issue has no cut-off at all");
        assertEquals(opens.getTime() + 7 * 24 * 3600_000L, i.getIntervalTo().getTime(),
                "the nominal close is one cadence period after the open");
        assertEquals(IntervalBoundSource.NOMINAL.name(), i.getIntervalToSource(),
                "a bound with no source loses the stamped-versus-nominal marker the list shows");
        assertNotNull(i.getWeek(), "no week was derived, so the report header prints 'Uge , '");
        assertNotNull(i.getYear());
        assertEquals(i.effectiveCutoff(), i.getIntervalTo(),
                "the nominal close IS the effective cut-off until something stamps one");

        Integer nominalWeek = i.getWeek();

        // Released five days late: the week it closed in is not the week its
        // nominal close fell in, and the document has to say which. Five days is
        // still ONE period -- a late week is not a double week -- so the issue is
        // named for a single, later week rather than gaining a second number.
        previews.record(i, "da", "preview.pdf",
                "preview-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Date late = new Date(opens.getTime() + 12 * 24 * 3600_000L);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null, late));

        assertEquals(late, i.getCutoffStampedAt());
        assertTrue(i.getWeek() > nominalWeek,
                "the numbers were not re-derived from the stamp; the issue is named for the week its "
                        + "nominal close fell in (" + nominalWeek + ") rather than the week it "
                        + "actually closed (" + i.getWeek() + ")");
        assertNull(i.getWeekTo(), "a week released late is one week, not two");
        assertEquals(i.getIntervalTo().getTime(), opens.getTime() + 7 * 24 * 3600_000L,
                "publish overwrote the nominal close; the stamp lives on cutoffStampedAt");
    }
}
