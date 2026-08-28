package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.domain.Domain;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueDraftVo;
import org.niord.core.publication.series.vo.IssueDraftWarningVo;
import org.niord.core.publication.series.vo.PublicationIssueDescVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.time.ZoneId;
import java.time.ZonedDateTime;
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
 * S12. The issue that does not exist yet.
 *
 * What is pinned here is the part three screens share and could each get wrong
 * on their own: WHERE the proposed period opens and closes, HOW FIRM each bound
 * is, and WHAT the issue would be called. The name is the one that has a known
 * wrong answer -- deriving it from the interval START returns "uge 26" for a
 * week production calls "uge 27", because every weekly window runs Wednesday to
 * Wednesday and therefore spans two ISO weeks.
 *
 * And that the endpoint writes nothing, asserted by counting rows rather than by
 * reading the code, because "it only reads" is a claim that decays.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueDraftTest {

    @Inject
    IssueDraftService drafts;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    /** The series' own zone, which every derivation below is read in. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Copenhagen");

    private static final long WEEK = 7L * 24 * 3600 * 1000;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(TimeRelation relation, String namePattern) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        // S-20: the timezone a cut-off is read in is the DOMAIN's, so a series
        // whose weeks must come out right has to have one.
        Domain d = new Domain();
        d.setDomainId("dom-" + UUID.randomUUID().toString().substring(0, 8));
        d.setName("Test domain");
        d.setTimeZone("Europe/Copenhagen");
        em.persist(d);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setDomain(d);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(SeriesCadence.WEEKLY);
        s.setNominalCutoffDay(CutoffDay.WEDNESDAY);
        s.setNominalCutoffTime("12:00");
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setFirstIssueStartsAt(wednesday(2026, 6, 24));
        // No report: a draft never renders, and a configured report would make
        // the published fixtures below refuse for want of a document.
        s.setCategory(c);
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        PublicationSeriesDesc desc = s.createDesc("da");
        desc.setName("Test series");
        desc.setNameSuggestionPattern(namePattern);
        desc.setFileNamePattern("test-w${week-2-digits}-${year}.pdf");
        em.persist(s);
        em.flush();
        return s;
    }

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    /** Noon on a given date, in the series' zone -- the shape of a real cut-off. */
    private static Date wednesday(int year, int month, int day) {
        return Date.from(ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZONE).toInstant());
    }

    private PublicationIssue publishedIssue(PublicationSeries s, Date from, Date stamp) {
        PublicationIssue i = lifecycle.create(s, from, IntervalBoundSource.STAMPED, user());
        em.flush();
        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, user(), stamp));
        em.flush();
        return em.find(PublicationIssue.class, i.getId());
    }

    private static String nameOf(IssueDraftVo draft, String lang) {
        for (PublicationIssueDescVo d : draft.getDescs()) {
            if (lang.equals(d.getLang())) {
                return d.getName();
            }
        }
        return null;
    }

    private static boolean warns(IssueDraftVo draft, String code) {
        return draft.getWarnings().stream().map(IssueDraftWarningVo::code).anyMatch(code::equals);
    }

    // ============================================================ the three asks

    /**
     * "The next one after this": the period opens where the named issue closed.
     *
     * The bound reads STAMPED because the publish recorded it, and that marker is
     * the difference between a date somebody can act on and one the cadence
     * guessed.
     */
    @Test
    @Transactional
    public void aDraftChainedOffAnIssueOpensAtItsClose() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        Date stamp = wednesday(2026, 7, 1);
        PublicationIssue published = publishedIssue(s, wednesday(2026, 6, 24), stamp);

        IssueDraftVo draft = drafts.draft(s, published.getPublicId(), null, null, new Date());

        assertEquals(stamp, draft.getIntervalFrom(), "the chain continues where that issue ended");
        assertEquals("STAMPED", draft.getIntervalFromSource(),
                "the publish recorded that instant; it is not the cadence talking");
        assertEquals(published.getPublicId(), draft.getChainedFromPublicId());
        assertEquals(new Date(stamp.getTime() + WEEK), draft.getIntervalTo(),
                "one cadence period on, which is what the gap rows are tiled with");
        assertEquals("NOMINAL", draft.getIntervalToSource(), "nothing has stamped the close yet");
        assertEquals(draft.getIntervalTo(), draft.getEffectiveCutoff());
        assertNull(draft.getPublicId(), "the id is minted at create, not proposed here");
    }

    /**
     * The gap row hands its own bounds back, and gets its own marker back.
     *
     * A typed bound that lands exactly on an issue's close IS a chained bound.
     * Marking it MANUAL would show one provenance in the list and a different one
     * in the form the row opens.
     */
    @Test
    @Transactional
    public void anExplicitIntervalThatLandsOnACloseIsStillChained() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        Date stamp = wednesday(2026, 7, 1);
        PublicationIssue published = publishedIssue(s, wednesday(2026, 6, 24), stamp);

        IssueDraftVo draft = drafts.draft(s, null, stamp, wednesday(2026, 7, 8), new Date());

        assertEquals(stamp, draft.getIntervalFrom());
        assertEquals("STAMPED", draft.getIntervalFromSource());
        assertEquals(published.getPublicId(), draft.getChainedFromPublicId());
        assertEquals(wednesday(2026, 7, 8), draft.getIntervalTo());
        assertEquals("MANUAL", draft.getIntervalToSource(), "the caller chose the close");
        assertTrue(draft.getWarnings().stream()
                        .noneMatch(w -> IssueDraftService.NOT_CHAINED.equals(w.code())),
                "it chains off a real issue: " + draft.getWarnings());
    }

    /**
     * At the head of the chain the series' own declared start is the bound.
     *
     * S-4 requires it of every interval-based series precisely so a newly
     * activated one has somewhere to begin. NOMINAL rather than STAMPED: it is a
     * declaration, not a record of anything that happened.
     */
    @Test
    @Transactional
    public void aSeriesWithNoIssuesOpensAtItsDeclaredFirstStart() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");

        IssueDraftVo draft = drafts.draft(s, null, null, null, new Date());

        assertEquals(wednesday(2026, 6, 24), draft.getIntervalFrom());
        assertEquals("NOMINAL", draft.getIntervalFromSource());
        assertNull(draft.getChainedFromPublicId(), "nothing precedes the first issue");
        assertEquals(wednesday(2026, 7, 1), draft.getIntervalTo());
    }

    /** With no argument at all the draft follows the newest issue. */
    @Test
    @Transactional
    public void withNoArgumentsTheDraftFollowsTheNewestIssue() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        publishedIssue(s, wednesday(2026, 6, 24), wednesday(2026, 7, 1));
        PublicationIssue newest = publishedIssue(s, wednesday(2026, 7, 1), wednesday(2026, 7, 8));

        IssueDraftVo draft = drafts.draft(s, null, null, null, new Date());

        assertEquals(newest.getPublicId(), draft.getChainedFromPublicId());
        assertEquals(wednesday(2026, 7, 8), draft.getIntervalFrom());
    }

    // =============================================================== the naming

    /**
     * The name comes from the ISO week of the CUT-OFF, never of the start.
     *
     * The canonical weekly window opens Wednesday of week 26 and closes Wednesday
     * of week 27, and production calls the result "EfS uge 27". Naming from the
     * start returns 26 -- and it is the plausible mistake, because the start is
     * the field the create form is built around.
     */
    @Test
    @Transactional
    public void theNameComesFromTheWeekTheIntervalCLOSEDIn() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");

        IssueDraftVo draft = drafts.draft(s, null, wednesday(2026, 6, 24), wednesday(2026, 7, 1),
                new Date());

        assertEquals(Integer.valueOf(27), draft.getWeek(),
                "the interval opens in week 26 and closes in week 27; the issue is named for 27");
        assertEquals(Integer.valueOf(2026), draft.getYear());
        assertNull(draft.getWeekTo(), "a single ordinary week is not a double week");
        assertEquals("EfS uge 27, 2026", nameOf(draft, "da"));
    }

    /**
     * A window that really has swallowed a second period is named for both weeks.
     *
     * The holiday double issue: two periods, so ${week} re-points at the first
     * week it closed and ${weekTo} carries the cut-off's own. The threshold is a
     * quarter-period of tolerance rather than rounding, so a single week released
     * five days late stays one week.
     */
    @Test
    @Transactional
    public void aTwoPeriodWindowIsNamedForBothWeeks() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL,
                "EfS uge ${week}+${weekTo}, ${year}");

        IssueDraftVo doubleWeek = drafts.draft(s, null, wednesday(2026, 6, 24), wednesday(2026, 7, 8),
                new Date());
        assertEquals(Integer.valueOf(27), doubleWeek.getWeek());
        assertEquals(Integer.valueOf(28), doubleWeek.getWeekTo());
        assertEquals("EfS uge 27+28, 2026", nameOf(doubleWeek, "da"));

        // Twelve days: one week released five days late, and still one week.
        IssueDraftVo late = drafts.draft(s, null, wednesday(2026, 6, 24),
                new Date(wednesday(2026, 6, 24).getTime() + 12 * 24 * 3600_000L), new Date());
        assertNull(late.getWeekTo(),
                "floor(periods + 1/4) keeps a late single week single; rounding would call it two");
    }

    /** The file name is expanded from the same numbers, per configured language. */
    @Test
    @Transactional
    public void theFileNameIsExpandedFromTheSameCutOff() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");

        IssueDraftVo draft = drafts.draft(s, null, wednesday(2026, 6, 24), wednesday(2026, 7, 1),
                new Date());

        assertEquals(1, draft.getDescs().size(), "one row per declared language");
        assertEquals("test-w27-2026.pdf", draft.getDescs().get(0).getFileName());
    }

    // =============================================================== refusals

    /**
     * A period reaching back into a released issue is refused HERE, not on save.
     *
     * The rule is the create's own, called from the draft, so the preview and the
     * save can never disagree about whether a period is free.
     */
    @Test
    @Transactional
    public void anIntervalInsideAReleasedIssueIsRefused() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        Date from = wednesday(2026, 6, 24);
        Date stamp = wednesday(2026, 7, 1);
        PublicationIssue released = publishedIssue(s, from, stamp);

        Date inside = new Date((from.getTime() + stamp.getTime()) / 2);
        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> drafts.draft(s, null, inside, stamp, new Date()));

        assertEquals("ISSUE_INTERVAL_OVERLAP", e.code());
        assertTrue(e.getMessage().contains(released.getPublicId()),
                "the refusal names the issue whose period this one reaches into");
    }

    /** An interval that ends before it begins selects nothing and is refused. */
    @Test
    @Transactional
    public void anInvertedIntervalIsRefused() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> drafts.draft(s, null, wednesday(2026, 7, 8), wednesday(2026, 7, 1),
                                new Date()));
        assertEquals("INTERVAL_INVERTED", e.code());
    }

    /** A draft chained off an issue of another series is a caller error, not a 500. */
    @Test
    @Transactional
    public void anUnknownPredecessorIsRefusedByName() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> drafts.draft(s, "not-an-issue-of-this-series", null, null, new Date()));
        assertEquals("ISSUE_NOT_FOUND", e.code());
    }

    // =============================================================== warnings

    /**
     * An in-force publication has NO period start, and the draft says so twice:
     * by leaving the bound empty, and in words.
     *
     * §4.2 refuses an intervalFrom on such a series outright, so a draft that
     * prefilled one would fill a form with a value the create rejects.
     */
    @Test
    @Transactional
    public void anInForceSeriesGetsNoLowerBound() {
        PublicationSeries s = series(TimeRelation.IN_FORCE_AT_CUTOFF, "Skydeområder ${year}");

        IssueDraftVo draft = drafts.draft(s, null, null, null, new Date());

        assertNull(draft.getIntervalFrom(), "an in-force issue has no period start at all");
        assertNull(draft.getIntervalFromSource());
        assertNotNull(draft.getIntervalTo(), "but it does have the instant it describes");
        assertTrue(warns(draft, IssueDraftService.IN_FORCE_HAS_NO_LOWER_BOUND),
                "and it says why: " + draft.getWarnings());
    }

    /** A series nothing is expected from still drafts, and says it is not active. */
    @Test
    @Transactional
    public void aDraftOnANonActiveSeriesWarns() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        s.setStatus(SeriesStatus.DRAFT);
        em.flush();

        IssueDraftVo draft = drafts.draft(s, null, null, null, new Date());

        assertNotNull(draft.getIntervalTo(), "the draft is still computed");
        assertTrue(warns(draft, IssueDraftService.SERIES_NOT_ACTIVE), draft.getWarnings().toString());
    }

    /** A bound that chains off nothing is allowed, and is marked as such. */
    @Test
    @Transactional
    public void aBoundThatChainsOffNothingIsWarnedAbout() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        publishedIssue(s, wednesday(2026, 6, 24), wednesday(2026, 7, 1));

        // Well after the released issue, and on no boundary anything recorded.
        Date adrift = new Date(wednesday(2026, 7, 1).getTime() + 3 * 24 * 3600_000L);
        IssueDraftVo draft = drafts.draft(s, null, adrift, null, new Date());

        assertEquals("MANUAL", draft.getIntervalFromSource());
        assertNull(draft.getChainedFromPublicId());
        assertTrue(warns(draft, IssueDraftService.NOT_CHAINED), draft.getWarnings().toString());
    }

    // ========================================================= the live count

    /**
     * The count is taken, and it is a number rather than an absence.
     *
     * "11 ville matche" is what an admin decides on when they are looking at a
     * missing week, and the whole endpoint is pointless for the gap row without
     * it. The value depends on the corpus, so what is asserted is that a count
     * was TAKEN -- null would mean nobody looked.
     */
    @Test
    @Transactional
    public void theDraftCountsWhatTheCriteriaWouldSelect() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");

        IssueDraftVo draft = drafts.draft(s, null, wednesday(2026, 6, 24), wednesday(2026, 7, 1),
                new Date());

        assertNotNull(draft.getWouldMatchCount(),
                "null means no count was taken; 0 means the interval is empty, and the two must not "
                        + "reach a screen looking alike");
        assertTrue(draft.getWouldMatchCount() >= 0);
    }

    /** A publication that selects nothing by query gets null and a reason, never 0. */
    @Test
    @Transactional
    public void aSeriesWithNoCriteriaGetsNoCountAndSaysWhy() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        s.setCriteria(null);
        em.flush();

        IssueDraftVo draft = drafts.draft(s, null, wednesday(2026, 6, 24), wednesday(2026, 7, 1),
                new Date());

        assertNull(draft.getWouldMatchCount());
        assertTrue(warns(draft, IssueDraftService.NO_MEMBERSHIP_CRITERIA),
                draft.getWarnings().toString());
    }

    // ======================================================== persists nothing

    /**
     * Counted rather than reasoned about.
     *
     * The endpoint is called on every drag of a date picker. "It only reads" is a
     * property of today's code, and the failure it guards against -- a draft that
     * quietly creates the issue it is previewing -- would show up as duplicate
     * weeks nobody created.
     */
    @Test
    @Transactional
    public void aDraftWritesNothing() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL, "EfS uge ${week}, ${year}");
        publishedIssue(s, wednesday(2026, 6, 24), wednesday(2026, 7, 1));
        em.flush();

        long issuesBefore = count("PublicationIssue");
        long descsBefore = count("PublicationIssueDesc");
        long membersBefore = count("IssueMember");
        long auditBefore = count("IssueAuditEntry");

        drafts.draft(s, null, null, null, new Date());
        drafts.draft(s, null, wednesday(2026, 7, 1), wednesday(2026, 7, 8), new Date());
        em.flush();

        assertEquals(issuesBefore, count("PublicationIssue"), "no issue was created");
        assertEquals(descsBefore, count("PublicationIssueDesc"), "and no desc row");
        assertEquals(membersBefore, count("IssueMember"), "the probe resolves, it does not store");
        assertEquals(auditBefore, count("IssueAuditEntry"), "and nothing was audited");
    }

    private long count(String entity) {
        return em.createQuery("SELECT COUNT(e) FROM " + entity + " e", Long.class).getSingleResult();
    }
    @jakarta.inject.Inject
    org.niord.core.publication.series.IssuePreviewService previewService;

    /**
     * Records a preview so the publish has bytes to promote.
     *
     * A query-backed series names a report and publish refuses to leave a
     * language without a document, so these fixtures release the way an admin
     * does after looking at the preview: regenerate = false, promoting exactly
     * the bytes that were reviewed. The bytes themselves are irrelevant here.
     */
    private void previewFor(org.niord.core.publication.series.PublicationIssue issue) {
        for (org.niord.core.publication.series.PublicationIssueDesc desc : issue.getDescs()) {
            previewService.record(issue, desc.getLang(), "preview.pdf",
                    "preview-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

}
