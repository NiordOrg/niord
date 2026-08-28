package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Editing an open issue.
 *
 * The behaviour worth pinning is not that the setters run: it is which names
 * follow the interval and which do not. A name the series suggested is a
 * rendering of the period, so moving the period must move it; a name somebody
 * typed is a decision, and re-deriving over it discards that decision with no
 * trace and no way to notice.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueEditTest {

    private static final long WEEK = 7L * 24 * 3600 * 1000;

    @Inject
    IssueEditService editService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueAuditService audit;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixture

    private PublicationIssue anIssue() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
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
        PublicationSeriesDesc desc = s.createDesc("da");
        desc.setName("Test series");
        // The pattern is the point: a suggested name RENDERS the period, so a
        // moved interval has something visible to re-render.
        desc.setNameSuggestionPattern("Uge ${week}, ${year}");
        em.persist(s);

        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        i.setIntervalTo(new Date(1_699_000_000_000L + WEEK));
        em.flush();
        return i;
    }

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    private List<AuditAction> actions(PublicationIssue issue) {
        return audit.forIssue(issue).stream().map(IssueAuditEntry::getAction).toList();
    }

    // -------------------------------------------------------------------- names

    /** A typed name is stored, marked as a decision, and recorded. */
    @Test
    @Transactional
    public void anameIsChangedAndAudited() {
        PublicationIssue issue = anIssue();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "  Skydeomraader 2026  "), null, null, null),
                user());
        em.flush();

        PublicationIssueDesc desc = issue.getDescs().get(0);
        assertEquals("Skydeomraader 2026", desc.getName(), "the name was not trimmed");
        assertTrue(desc.isNameOverridden(),
                "a typed name that does not mark itself as one is put back by the next interval edit");
        assertTrue(actions(issue).contains(AuditAction.NAME_CHANGED));
    }

    /**
     * A blank name is refused rather than stored.
     *
     * The column is NOT NULL precisely because a nameless issue is unfindable in
     * every list that shows it, and an empty string clears it just as well as a
     * null would.
     */
    @Test
    @Transactional
    public void ablankNameIsRefused() {
        PublicationIssue issue = anIssue();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue,
                                new IssueEditService.IssueEdit(Map.of("da", "   "), null, null, null),
                                user()));
        assertEquals("NAME_BLANK", e.code());
    }

    /** A language the series does not carry has no row to write to. */
    @Test
    @Transactional
    public void anunconfiguredLanguageIsRefused() {
        PublicationIssue issue = anIssue();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue,
                                new IssueEditService.IssueEdit(Map.of("en", "Week 44"), null, null, null),
                                user()));
        assertEquals("NO_SUCH_LANGUAGE", e.code());
    }

    /** Renaming to the value it already holds writes no history. */
    @Test
    @Transactional
    public void anunchangedNameIsNotAnEvent() {
        PublicationIssue issue = anIssue();
        String current = issue.getDescs().get(0).getName();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", current), null, null, null), user());
        em.flush();

        assertFalse(actions(issue).contains(AuditAction.NAME_CHANGED),
                "a Historik panel listing edits that changed nothing buries the ones that did");
    }

    // ----------------------------------------------------------------- interval

    /**
     * Moving the interval moves the numbers and the suggested name with it.
     *
     * Every issue list and name pattern reads week/year, so leaving them behind
     * produces an issue labelled with one week sitting in another one's period.
     */
    @Test
    @Transactional
    public void movingTheIntervalRenumbersAndRenames() {
        PublicationIssue issue = anIssue();
        String before = issue.getDescs().get(0).getName();
        Integer weekBefore = issue.getWeek();

        editService.update(issue, new IssueEditService.IssueEdit(null,
                new Date(1_699_000_000_000L + 4 * WEEK),
                new Date(1_699_000_000_000L + 5 * WEEK), null), user());
        em.flush();

        assertNotEquals(weekBefore, issue.getWeek(), "the week was not re-derived");
        assertNotEquals(before, issue.getDescs().get(0).getName(),
                "the suggested name still renders the old period");
        assertEquals(IntervalBoundSource.MANUAL, issue.getIntervalFromSource(),
                "a typed bound recorded as STAMPED claims somebody stamped it at release");
        assertTrue(actions(issue).contains(AuditAction.INTERVAL_CHANGED));
    }

    /**
     * Each bound records where IT came from, not where the other one did.
     *
     * MANUAL means "somebody typed this bound". Writing it on both because one of
     * them moved claims an admin authored a period start that was in fact stamped
     * by the previous release -- and the "(stemplet)/(nominel)" marker the issue
     * list puts on every interval reads exactly these two columns, so the screen
     * then states something untrue about a bound nobody touched.
     */
    @Test
    @Transactional
    public void onlyTheBoundThatMovedIsReattributed() {
        PublicationIssue issue = anIssue();
        assertEquals(IntervalBoundSource.STAMPED, issue.getIntervalFromSource());

        // Only the close moves.
        editService.update(issue, new IssueEditService.IssueEdit(null, null,
                new Date(1_699_000_000_000L + 3 * WEEK), null), user());
        em.flush();

        assertEquals(IntervalBoundSource.STAMPED, issue.getIntervalFromSource(),
                "the start was re-attributed to a hand that never touched it");
        assertEquals(IntervalBoundSource.MANUAL, issue.getIntervalToSource(),
                "the bound that actually moved does not say it was typed");

        // And now only the start.
        PublicationIssue other = anIssue();
        IntervalBoundSource closeSourceBefore = other.getIntervalToSource();
        editService.update(other, new IssueEditService.IssueEdit(null,
                new Date(1_699_000_000_000L - WEEK), null, null), user());
        em.flush();

        assertEquals(IntervalBoundSource.MANUAL, other.getIntervalFromSource());
        assertEquals(closeSourceBefore, other.getIntervalToSource(),
                "the close was re-attributed although the edit never named it");
    }

    /**
     * An interval edit may not reach back into a released issue's period.
     *
     * The same refusal the create makes, and it belongs here for the same reason:
     * those messages have already gone out, and a second issue claiming them
     * publishes them twice under two names. Only the create checked, so the edit
     * was the way around the rule -- and the admin got a success toast for it.
     */
    @Test
    @Transactional
    public void anIntervalEditIntoAReleasedPeriodIsRefused() {
        PublicationIssue issue = anIssue();
        PublicationSeries s = issue.getSeries();

        // A neighbour that covered the fortnight before this issue opened.
        PublicationIssue released = new PublicationIssue();
        released.setSeries(s);
        released.setPublicId(UUID.randomUUID().toString());
        released.setRepoPath("publications/" + released.getPublicId());
        released.setStatus(IssueStatus.PUBLISHED);
        released.setIntervalFrom(new Date(1_699_000_000_000L - 2 * WEEK));
        released.setIntervalFromSource(IntervalBoundSource.STAMPED);
        released.setCutoffStampedAt(new Date(1_699_000_000_000L));
        released.setCutoffSource("STAMPED_AT_PUBLISH");
        released.createDesc("da").setName("Neighbour");
        em.persist(released);
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue, new IssueEditService.IssueEdit(null,
                                new Date(1_699_000_000_000L - WEEK), null, null), user()));
        assertEquals("ISSUE_INTERVAL_OVERLAP", e.code());

        // And the bound is untouched: a refusal that half-applied would leave the
        // issue covering a period nobody asked for.
        assertEquals(new Date(1_699_000_000_000L), issue.getIntervalFrom());
    }

    /** An issue may still be edited where it already is. */
    @Test
    @Transactional
    public void anIntervalEditThatDoesNotMoveIntoANeighbourIsAllowed() {
        PublicationIssue issue = anIssue();
        editService.update(issue, new IssueEditService.IssueEdit(null,
                new Date(1_699_000_000_000L + WEEK), new Date(1_699_000_000_000L + 2 * WEEK), null),
                user());
        em.flush();

        assertEquals(new Date(1_699_000_000_000L + WEEK), issue.getIntervalFrom());
    }

    /**
     * A typed name survives an interval move.
     *
     * This is the whole reason nameOverridden exists. Without it the rename is
     * discarded by the next interval edit, silently, and the only sign is that
     * the name went back to what the series would have called it.
     */
    @Test
    @Transactional
    public void anoverriddenNameSurvivesAnIntervalMove() {
        PublicationIssue issue = anIssue();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Saerudgave"), null, null, null), user());
        em.flush();

        editService.update(issue, new IssueEditService.IssueEdit(null,
                new Date(1_699_000_000_000L + 4 * WEEK),
                new Date(1_699_000_000_000L + 5 * WEEK), null), user());
        em.flush();

        assertEquals("Saerudgave", issue.getDescs().get(0).getName());
    }

    /**
     * A rename in the same call as an interval move wins.
     *
     * The interval re-derives the suggested names, so a rename applied first
     * would be overwritten by the very re-derivation it was meant to replace --
     * and the caller would have no way to tell that from a rename that failed.
     */
    @Test
    @Transactional
    public void arenameInTheSameCallAsAnIntervalMoveWins() {
        PublicationIssue issue = anIssue();

        editService.update(issue, new IssueEditService.IssueEdit(
                Map.of("da", "Uge 44 rettet"),
                new Date(1_699_000_000_000L + 4 * WEEK),
                new Date(1_699_000_000_000L + 5 * WEEK), null), user());
        em.flush();

        assertEquals("Uge 44 rettet", issue.getDescs().get(0).getName());
    }

    /** An interval that ends before it starts selects nothing. */
    @Test
    @Transactional
    public void aninvertedIntervalIsRefused() {
        PublicationIssue issue = anIssue();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue, new IssueEditService.IssueEdit(null,
                                new Date(1_699_000_000_000L + 5 * WEEK),
                                new Date(1_699_000_000_000L + 4 * WEEK), null), user()));
        assertEquals("INTERVAL_INVERTED", e.code());
    }

    /**
     * An absent field means "leave it alone", not "clear it".
     *
     * A form that had to round-trip the interval in order to rename an issue
     * would eventually round-trip a stale one.
     */
    @Test
    @Transactional
    public void anabsentFieldIsUntouched() {
        PublicationIssue issue = anIssue();
        Date from = issue.getIntervalFrom();
        Date to = issue.getIntervalTo();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Nyt navn"), null, null, null), user());
        em.flush();

        assertEquals(from, issue.getIntervalFrom());
        assertEquals(to, issue.getIntervalTo());
        assertFalse(actions(issue).contains(AuditAction.INTERVAL_CHANGED));
    }

    // --------------------------------------------------------- criteria override

    private static org.niord.core.publication.series.criteria.IssueCriteriaVo criteria(String... ids) {
        var node = new org.niord.core.publication.series.criteria.MessageSeriesCriterionVo();
        node.setValues(new java.util.ArrayList<>(List.of(ids)));
        var doc = new org.niord.core.publication.series.criteria.IssueCriteriaVo();
        doc.setCriteria(new java.util.ArrayList<
                org.niord.core.publication.series.criteria.IssueCriterionVo>(List.of(node)));
        return doc;
    }

    /**
     * An issue can be tailored to select something its series does not.
     *
     * The escape hatch legacy had no concept of: an edition that must differ used
     * to require cloning the whole template into a throwaway `dont-use-` series.
     */
    @Test
    @Transactional
    public void anissueCanBeGivenItsOwnCriteria() {
        PublicationIssue issue = anIssue();
        issue.getSeries().setCriteria(criteria("dma-nm"));
        em.flush();

        editService.update(issue, new IssueEditService.IssueEdit(
                null, null, null, null, criteria("dma-nm", "dma-fa"), false), user());
        em.flush();

        assertNotNull(issue.getCriteriaOverride());
        assertTrue(EffectiveCriteria.isOverridden(issue));
        assertTrue(actions(issue).contains(AuditAction.CRITERIA_OVERRIDDEN));
    }

    /** And handed back to the series again. */
    @Test
    @Transactional
    public void anoverrideCanBeCleared() {
        PublicationIssue issue = anIssue();
        issue.getSeries().setCriteria(criteria("dma-nm"));
        issue.setCriteriaOverride(criteria("dma-fa"));
        em.flush();

        editService.update(issue,
                new IssueEditService.IssueEdit(null, null, null, null, null, true), user());
        em.flush();

        assertNull(issue.getCriteriaOverride());
        assertFalse(EffectiveCriteria.isOverridden(issue));
    }

    /**
     * An override equal to the series' criteria is stored as no override.
     *
     * It is not a deviation. Recording it as one would label the issue "tilpasset
     * for denne udgave" while it selects exactly what the series does -- and would
     * make the shadow diff skip a week that had nothing wrong with it.
     */
    @Test
    @Transactional
    public void anoverrideIdenticalToTheSeriesIsNotStored() {
        PublicationIssue issue = anIssue();
        issue.getSeries().setCriteria(criteria("dma-nm"));
        em.flush();

        editService.update(issue, new IssueEditService.IssueEdit(
                null, null, null, null, criteria("dma-nm"), false), user());
        em.flush();

        assertNull(issue.getCriteriaOverride());
        assertFalse(EffectiveCriteria.isOverridden(issue));
    }

    /**
     * An unresolvable override is refused, not stored.
     *
     * A blank operand narrows to nothing, so the issue would publish EMPTY rather
     * than fail -- the one failure mode that looks like success. Refused here,
     * where somebody is watching, rather than at 02:00 under AUTO_RELEASE.
     */
    @Test
    @Transactional
    public void anunresolvableOverrideIsRefused() {
        PublicationIssue issue = anIssue();
        issue.getSeries().setCriteria(criteria("dma-nm"));
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue, new IssueEditService.IssueEdit(
                                null, null, null, null, criteria(""), false), user()));
        assertEquals("CRITERIA_INVALID", e.code());
        assertNull(issue.getCriteriaOverride());
    }

    /** A series that does not select by criteria cannot be overridden into one that does. */
    @Test
    @Transactional
    public void anoverrideOnANonQuerySeriesIsRefused() {
        PublicationIssue issue = anIssue();
        issue.getSeries().setContentMode(ContentMode.NONE);
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue, new IssueEditService.IssueEdit(
                                null, null, null, null, criteria("dma-nm"), false), user()));
        assertEquals("CRITERIA_NOT_APPLICABLE", e.code());
    }

    /** Saying nothing about the criteria leaves an existing override alone. */
    @Test
    @Transactional
    public void anabsentCriteriaFieldLeavesTheOverrideAlone() {
        PublicationIssue issue = anIssue();
        issue.getSeries().setCriteria(criteria("dma-nm"));
        issue.setCriteriaOverride(criteria("dma-fa"));
        em.flush();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Nyt navn"), null, null, null), user());
        em.flush();

        assertNotNull(issue.getCriteriaOverride());
        assertFalse(actions(issue).contains(AuditAction.CRITERIA_OVERRIDDEN));
    }

    // ------------------------------------------------------------------- status

    /**
     * A published issue is not editable.
     *
     * Its name is on a document people have downloaded and its interval is what
     * its frozen member list was resolved over. Changing either makes the record
     * describe something that never happened.
     */
    @Test
    @Transactional
    public void apublishedIssueIsRefused() {
        PublicationIssue issue = anIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue,
                                new IssueEditService.IssueEdit(Map.of("da", "For sent"), null, null, null),
                                user()));
        assertEquals("ISSUE_NOT_OPEN", e.code());
    }

    /** Report parameters round-trip. */
    @Test
    @Transactional
    public void reportParametersAreReplaced() {
        PublicationIssue issue = anIssue();

        editService.update(issue,
                new IssueEditService.IssueEdit(null, null, null, Map.of("frontpage", "special.ftl")),
                user());
        em.flush();

        assertEquals("special.ftl", issue.getReportParams().get("frontpage"));
    }
}
