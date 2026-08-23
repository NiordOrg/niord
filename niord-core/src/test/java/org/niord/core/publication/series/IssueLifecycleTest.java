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
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The transitions other than publish, plus curation and the release rail. */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueLifecycleTest {

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueCurationService curation;

    @Inject
    PublishChecklistService checklist;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(TimeRelation relation) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setReportId("fm-report");
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

    /** An existing message, since O-6 refuses an override naming nothing. */
    private String someMessageUid() {
        List<String> uids = em.createQuery("SELECT m.uid FROM Message m ORDER BY m.id", String.class)
                .setMaxResults(1).getResultList();
        assertFalse(uids.isEmpty(), "the test database holds no messages; seed it first");
        return uids.get(0);
    }

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    // ================================================================= create

    @Test
    @Transactional
    public void publicIdIsMintedAtCreateAndSurvivesEveryTransition() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        String minted = i.getPublicId();
        assertNotNull(minted, "publicId must exist from the moment of create -- message HTML cites it");

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        assertEquals(minted, em.find(PublicationIssue.class, i.getId()).getPublicId(),
                "publicId changed across publish; every citation to it would break");
    }

    /** Retro-create is a category error for a series whose issues overlap. */
    @BindsRule({"I-8"})
    @Test
    @Transactional
    public void retroCreateIsRefusedForAnOverlappingSeries() {
        PublicationSeries inForce = series(TimeRelation.IN_FORCE_AT_CUTOFF);
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> lifecycle.retroCreate(inForce, new Date(), new Date(), user()));
        assertEquals("RETRO_CREATE_NOT_APPLICABLE", e.code());

        // And it is allowed for one that tiles.
        PublicationSeries tiling = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        em.flush();
        PublicationIssue recovered = lifecycle.retroCreate(tiling,
                new Date(1_690_000_000_000L), new Date(1_691_000_000_000L), user());
        assertEquals(IntervalBoundSource.RECOVERED, recovered.getIntervalFromSource());
        assertTrue(recovered.isCutoffReconstructed(),
                "a recovered issue must record that its cut-off was reconstructed rather than stamped");
    }

    /**
     * OQ-10. The new edition sets supersedes AND caps the predecessor in one
     * transaction -- the multi-step alternative is where the cap gets forgotten.
     */
    @BindsRule({"I-13"})
    @Test
    @Transactional
    public void aNewEditionLinksAndCapsInOneTransaction() {
        PublicationSeries s = series(TimeRelation.IN_FORCE_AT_CUTOFF);
        PublicationIssue first = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.MANUAL, user());
        em.flush();
        publishService.publish(first.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue predecessor = em.find(PublicationIssue.class, first.getId());
        assertNull(predecessor.getPublicTo(), "the first edition should start open-ended");

        PublicationIssue edition = lifecycle.newEdition(predecessor, new Date(1_710_000_000_000L), user());
        em.flush();

        assertNotNull(edition.getSupersedes(), "supersedes was not set");
        assertEquals(first.getId(), edition.getSupersedes().getId());
        assertNotNull(em.find(PublicationIssue.class, first.getId()).getPublicTo(),
                "the predecessor was not capped; two current editions would reach the download site");

        // The audit action that was previously unreachable by any API call.
        List<IssueAuditEntry> superseded = em.createQuery(
                        "SELECT a FROM IssueAuditEntry a WHERE a.issue.id = :id AND a.action = 'SUPERSEDED_BY'",
                        IssueAuditEntry.class)
                .setParameter("id", first.getId()).getResultList();
        assertEquals(1, superseded.size(), "SUPERSEDED_BY was not written");
    }

    // ============================================================ retire / reactivate

    /** Retiring leaves the file and the window in place: history, not erasure. */
    @Test
    @Transactional
    public void retiringLeavesTheWindowAndFileAlone() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, i.getId());
        Date windowFrom = published.getPublicFrom();
        String repoPath = published.getRepoPath();

        lifecycle.retire(published, user(), "superseded by an errata");
        em.flush();
        em.clear();

        PublicationIssue retired = em.find(PublicationIssue.class, i.getId());
        assertEquals(IssueStatus.RETIRED, retired.getStatus());
        assertNotNull(retired.getRetiredAt());
        assertEquals(windowFrom, retired.getPublicFrom(),
                "retiring moved the public window; people have cited this document");
        assertEquals(repoPath, retired.getRepoPath(), "retiring moved the file");

        lifecycle.reactivate(retired, user(), "the errata was withdrawn");
        em.flush();
        em.clear();
        assertEquals(IssueStatus.PUBLISHED, em.find(PublicationIssue.class, i.getId()).getStatus());
        assertNull(em.find(PublicationIssue.class, i.getId()).getRetiredAt());
    }

    // ================================================================= delete

    /**
     * DM-Q3 restated. C7's literal "no publicId" can never be true, because
     * publicId is minted at create. The real test is never-stamped and
     * never-published.
     */
    @BindsRule({"I-16"})
    @Test
    @Transactional
    public void onlyAnUntouchedIssueCanBeDeleted() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue fresh = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        assertNotNull(fresh.getPublicId(), "it HAS a publicId, which is why C7's literal wording cannot work");
        lifecycle.deleteIssue(fresh, user());
        em.flush();
        assertNull(em.find(PublicationIssue.class, fresh.getId()), "the untouched issue was not deleted");

        PublicationIssue published = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();
        publishService.publish(published.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue live = em.find(PublicationIssue.class, published.getId());
        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> lifecycle.deleteIssue(live, user()));
        assertEquals("ISSUE_NOT_DELETABLE", e.code());
    }

    /** X-5. A series with issues is retired, never deleted. */
    @BindsRule({"X-5"})
    @Test
    @Transactional
    public void aSeriesWithIssuesCannotBeDeleted() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        lifecycle.create(s, new Date(1_699_000_000_000L), IntervalBoundSource.STAMPED, user());
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> lifecycle.deleteSeries(s));
        assertEquals("SERIES_HAS_ISSUES", e.code());
        assertTrue(e.getMessage().contains("1 issue"), "the refusal should name the count: " + e.getMessage());
    }

    // ================================================================= curation

    @Test
    @Transactional
    public void curationIsRecordedNotAppliedDestructively() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        User curator = user();
        // A real message: O-6 refuses an override naming one that does not exist,
        // because an override that can never apply would sit in the audit trail
        // looking like a decision nobody can act on.
        String uid = someMessageUid();

        curation.include(i, uid, curator, "belongs in this week");
        em.flush();
        assertEquals(1, curation.forIssue(i).size());

        // A second decision on the same message replaces the first: two would be
        // either redundant or contradictory.
        curation.exclude(i, uid, curator, "changed my mind");
        em.flush();
        List<IssueOverride> after = curation.forIssue(i);
        assertEquals(1, after.size(), "two overrides for one message were kept");
        assertEquals(OverrideKind.EXCLUDE, after.get(0).getKind());
    }

    @BindsRule({"O-1"})
    @Test
    @Transactional
    public void anOverrideMustSayWhy() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.include(i, "some-uid", user(), "  "));
        assertEquals("OVERRIDE_REASON_REQUIRED", e.code());
    }

    /** Once frozen, the member set is history. Changing it would rewrite what was published. */
    @BindsRule({"O-3"})
    @Test
    @Transactional
    public void curationIsRefusedOnceTheIssueIsFrozen() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, i.getId());
        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.include(published, "some-uid", user(), "too late"));
        assertEquals("ISSUE_NOT_OPEN", e.code());
    }

    /** OQ-1: the transitional fallback accepts ADMIN, and says so. */
    @Test
    public void theCuratePermissionAcceptsTheDatedFallback() {
        assertTrue(PublicationPermissions.mayCurate(r -> r.equals("publication-curate")));
        assertTrue(PublicationPermissions.mayCurate(r -> r.equals("admin")),
                "the dated fallback should accept admin until the realm carries the role");
        assertFalse(PublicationPermissions.mayCurate(r -> r.equals("editor")));
        assertFalse(PublicationPermissions.mayCurate(null));
        assertNotNull(PublicationPermissions.FALLBACK_ADDED,
                "the fallback must carry a date so it does not quietly become permanent");
    }

    // ================================================================= checklist

    /** All fifteen rail codes are emitted, every time. */
    @Test
    @Transactional
    public void theRailEmitsEveryCodeItDeclares() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        PublishChecklistService.Checklist result =
                checklist.compute(i, new Date(1_700_000_000_000L), false, false);

        assertEquals(15, PublishChecklistService.CODES.size(), "the rail is not fifteen codes");
        assertEquals(PublishChecklistService.CODES.size(), result.rows().size(),
                "the rail emitted " + result.rows().size() + " rows; the UI renders and translates all "
                        + PublishChecklistService.CODES.size());

        List<String> emitted = result.rows().stream().map(PublishChecklistService.CheckRow::code).toList();
        assertEquals(PublishChecklistService.CODES, emitted, "the rail rows are out of order or incomplete");
    }

    /**
     * FILE_PRESENT_PER_LANGUAGE does not apply to generated content.
     *
     * Gating publish on a file that publish itself writes would mean no
     * query-backed issue could ever be published at all.
     */
    @Test
    @Transactional
    public void theFileCheckDoesNotGateTheFileItselfProduces() {
        PublicationSeries generated = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(generated, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        PublishChecklistService.CheckRow row =
                checklist.compute(i, new Date(1_700_000_000_000L), false, false).rows().stream()
                        .filter(r -> r.code().equals("FILE_PRESENT_PER_LANGUAGE"))
                        .findFirst().orElseThrow();

        assertTrue(row.passed(),
                "a query-backed issue was blocked for lacking the file that publishing generates; no such "
                        + "issue could ever be published");
    }

    /** The one acknowledgeable row is the one an exclusions panel cannot show. */
    @Test
    @Transactional
    public void exactlyOneRailRowIsAcknowledgeable() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        List<PublishChecklistService.CheckRow> acknowledgeable =
                checklist.compute(i, new Date(1_700_000_000_000L), false, false).rows().stream()
                        .filter(PublishChecklistService.CheckRow::acknowledgeable).toList();

        assertEquals(1, acknowledgeable.size(), "expected exactly one acknowledgeable row");
        assertEquals("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF", acknowledgeable.get(0).code());
    }

    /** A future cut-off blocks unless it was explicitly allowed. */
    @Test
    @Transactional
    public void aFutureCutoffBlocksUnlessAllowed() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue i = lifecycle.create(s, new Date(), IntervalBoundSource.STAMPED, user());
        em.flush();

        Date future = new Date(System.currentTimeMillis() + 86_400_000L);
        assertTrue(checklist.compute(i, future, false, false).blockingCodes().contains("CUTOFF_NOT_FUTURE"));
        assertFalse(checklist.compute(i, future, true, false).blockingCodes().contains("CUTOFF_NOT_FUTURE"));
    }
}
