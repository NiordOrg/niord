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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    private List<String> actions(PublicationIssue issue) {
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
        assertTrue(actions(issue).contains("NAME_CHANGED"));
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

        assertTrue(actions(issue).stream().noneMatch("NAME_CHANGED"::equals),
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
        assertTrue(actions(issue).contains("INTERVAL_CHANGED"));
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
        assertTrue(actions(issue).stream().noneMatch("INTERVAL_CHANGED"::equals));
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
