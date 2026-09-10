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

package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
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
        c.setCategoryId(TestIds.category());
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
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
        // Every publication names the desk that owns it: the column is NOT NULL and
        // S-20a refuses a save without one, so a fixture that left it out no longer
        // describes a state the system can be in.
        s.setDomain(TestOwnerDomain.of(em));
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
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    private List<AuditAction> actions(PublicationIssue issue) {
        return audit.forIssue(issue).stream().map(IssueAuditEntry::getAction).toList();
    }

    // ------------------------------------------------------------------ edition

    /**
     * The create writes the first edition, so no issue is born without one.
     *
     * The column was write-once at import: the archive carries an edition on 1,048
     * of its 1,077 rows because the previous system defaulted one, and every issue
     * created here had none. A series whose file-name pattern names the edition
     * then published as "…-v-2027.pdf" -- the token expands to the empty string,
     * which is not an unresolved token and so is refused by nothing.
     */
    @Test
    @Transactional
    public void anewIssueIsBornAsTheFirstEditionOfItsPeriod() {
        PublicationIssue issue = anIssue();
        assertEquals("1", issue.getEdition(),
                "an issue with no edition publishes with the token expanded to nothing");
    }

    /** A typed edition is trimmed, stored and recorded as its own event. */
    @Test
    @Transactional
    public void aneditionIsChangedAndAudited() {
        PublicationIssue issue = anIssue();

        editService.update(issue,
                new IssueEditService.IssueEdit(null, null, null, null, null, false, "  2  "),
                user());
        em.flush();

        assertEquals("2", issue.getEdition(), "the edition was not trimmed");
        assertTrue(actions(issue).contains(AuditAction.EDITION_CHANGED),
                "the edition is on the cover and in the file name; the trail has to say it moved");
    }

    /**
     * FREE TEXT, because the archive is.
     *
     * Two rows carry a YEAR, typed into the previous system's edition box, and one
     * of those years is in a published file name to this day. A numeric column
     * would refuse to round-trip the archive this has to carry.
     */
    @Test
    @Transactional
    public void anonNumericEditionIsKept() {
        PublicationIssue issue = anIssue();

        editService.update(issue,
                new IssueEditService.IssueEdit(null, null, null, null, null, false, "v2 rettet"),
                user());
        em.flush();

        assertEquals("v2 rettet", issue.getEdition());
    }

    /** Absent leaves it alone, like every other field on the edit. */
    @Test
    @Transactional
    public void anabsentEditionChangesNothing() {
        PublicationIssue issue = anIssue();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Uge 44"), null, null, null),
                user());
        em.flush();

        assertEquals("1", issue.getEdition());
        assertFalse(actions(issue).contains(AuditAction.EDITION_CHANGED),
                "a field nobody sent must not produce an entry saying it changed");
    }

    /**
     * A BLANK edition is refused rather than treated as a clear.
     *
     * "No edition" and "the first edition" are different claims, and the create
     * writes the first one precisely so nothing has to be ambiguous about which.
     */
    @Test
    @Transactional
    public void ablankEditionIsRefused() {
        PublicationIssue issue = anIssue();

        IssueLifecycleService.TransitionRefusedException refusal = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> editService.update(issue,
                        new IssueEditService.IssueEdit(null, null, null, null, null, false, "   "),
                        user()));
        assertEquals("EDITION_INVALID", refusal.code());
        assertEquals("1", issue.getEdition(), "the refusal must not have written anything");
    }

    /** And one longer than the column, for the same reason and with the same code. */
    @Test
    @Transactional
    public void anoverlongEditionIsRefused() {
        PublicationIssue issue = anIssue();
        String tooLong = "e".repeat(IssueEditService.MAX_EDITION + 1);

        IssueLifecycleService.TransitionRefusedException refusal = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> editService.update(issue,
                        new IssueEditService.IssueEdit(null, null, null, null, null, false, tooLong),
                        user()));
        assertEquals("EDITION_INVALID", refusal.code());
    }

    /**
     * A PUBLISHED issue's edition is frozen by the gate that freezes its names.
     *
     * It is on the cover of a document people have downloaded and in the file name
     * they downloaded it under; re-deciding it afterwards would leave the record
     * and the artefact disagreeing.
     */
    @Test
    @Transactional
    public void theeditionOfAPublishedIssueIsFrozen() {
        PublicationIssue issue = anIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        em.flush();

        IssueLifecycleService.TransitionRefusedException refusal = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> editService.update(issue,
                        new IssueEditService.IssueEdit(null, null, null, null, null, false, "2"),
                        user()));
        assertEquals("ISSUE_NOT_OPEN", refusal.code());
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
     * A BLANK NAME IS THE WAY BACK, and until now there was none.
     *
     * The flag a rename sets is what stops the shaping re-deriving the name, and
     * every name written through this service set it -- so an issue renamed once
     * was renamed for ever, and the drawer's "follow the series again" control
     * had nothing to call. The refusal it used to get even said to clear the
     * override instead, which was the one thing that could not be done.
     *
     * The suggestion is written back HERE, in the same request. A clear that only
     * dropped the flag would leave the drawer showing the withdrawn name until
     * something unrelated moved the interval.
     */
    @Test
    @Transactional
    public void ablankNameGoesBackToTheSeriesSuggestion() {
        PublicationIssue issue = anIssue();
        String suggested = issue.getDescs().get(0).getName();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Et navn nogen tastede"), null, null, null),
                user());
        em.flush();
        assertTrue(issue.getDescs().get(0).isNameOverridden(), "the rename did not pin the name");

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "   "), null, null, null), user());
        em.flush();

        PublicationIssueDesc desc = issue.getDescs().get(0);
        assertFalse(desc.isNameOverridden(),
                "the override survived the blank, so the name is still frozen and the control that "
                        + "sent it does nothing");
        assertEquals(suggested, desc.getName(),
                "the name did not go back to the series' suggestion; a clear that leaves the typed "
                        + "text on the row says one thing on the flag and another on the page");
    }

    /** And typing again re-pins it, so the two states are reachable in both directions. */
    @Test
    @Transactional
    public void aclearedNameCanBePinnedAgain() {
        PublicationIssue issue = anIssue();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Et navn"), null, null, null), user());
        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", ""), null, null, null), user());
        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Et andet navn"), null, null, null), user());
        em.flush();

        PublicationIssueDesc desc = issue.getDescs().get(0);
        assertEquals("Et andet navn", desc.getName());
        assertTrue(desc.isNameOverridden(), "the re-typed name did not pin, so the next edit re-derives it");
    }

    /**
     * Clearing one language says nothing about the others.
     *
     * The same per-language rule the rename has, and for the same reason: an
     * admin handing the Danish title back to the series has not asked for the
     * English one they typed last week to be discarded with it.
     */
    @Test
    @Transactional
    public void clearingOneLanguageLeavesTheOthersPinned() {
        PublicationIssue issue = anIssue();
        PublicationSeries s = issue.getSeries();
        s.getLanguages().add("en");
        PublicationSeriesDesc enSeries = s.createDesc("en");
        enSeries.setName("Test series");
        enSeries.setNameSuggestionPattern("Week ${week}, ${year}");
        PublicationIssueDesc enDesc = issue.createDesc("en");
        enDesc.setName("Week 44, 2023");
        em.flush();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Dansk navn", "en", "English name"),
                        null, null, null),
                user());
        em.flush();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", ""), null, null, null), user());
        em.flush();

        PublicationIssueDesc da = issue.getDescs().stream()
                .filter(d -> "da".equals(d.getLang())).findFirst().orElseThrow();
        PublicationIssueDesc en = issue.getDescs().stream()
                .filter(d -> "en".equals(d.getLang())).findFirst().orElseThrow();
        assertFalse(da.isNameOverridden(), "the language named in the map did not stop following");
        assertTrue(en.isNameOverridden(),
                "clearing one language cleared another; the map names exactly the languages the "
                        + "caller decided about");
        assertEquals("English name", en.getName(),
                "the re-shape ran over a language the caller said nothing about");
    }

    /** The trail says the name stopped being a decision, and what it was. */
    @Test
    @Transactional
    public void thetrailSaysAnameStoppedBeingAnOverride() {
        PublicationIssue issue = anIssue();
        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Et navn nogen tastede"), null, null, null),
                user());
        em.flush();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", ""), null, null, null), user());
        em.flush();

        // Newest first, so this is the clear rather than the rename before it.
        IssueAuditEntry entry = audit.forIssue(issue).stream()
                .filter(a -> a.getAction() == AuditAction.NAME_CHANGED)
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) entry.getDetail();
        assertEquals("da", detail.get("lang"));
        assertEquals("Et navn nogen tastede", detail.get("from"),
                "a history line that cannot say which name was withdrawn answers nothing");
        assertNull(detail.get("to"), "the entry claims a name was typed rather than handed back");
    }

    /** A blank for a language that already follows the series is not an event. */
    @Test
    @Transactional
    public void ablankNameOnALanguageThatAlreadyFollowsChangesNothing() {
        PublicationIssue issue = anIssue();
        String suggested = issue.getDescs().get(0).getName();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "  "), null, null, null), user());
        em.flush();

        assertEquals(suggested, issue.getDescs().get(0).getName());
        assertFalse(actions(issue).contains(AuditAction.NAME_CHANGED),
                "a Historik panel listing edits that changed nothing buries the ones that did");
    }

    /**
     * The one blank still refused: a language with no name to fall back on.
     *
     * The clear writes no name of its own -- it drops the flag and lets the
     * shaping render one -- so a desc that is already nameless would be left that
     * way, and an issue is unfindable in every list that shows it under a
     * language with no name.
     */
    @Test
    @Transactional
    public void ablankNameForALanguageWithNothingToFallBackOnIsRefused() {
        PublicationIssue issue = anIssue();
        issue.getDescs().get(0).setName("");
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue,
                                new IssueEditService.IssueEdit(Map.of("da", "   "), null, null, null),
                                user()));
        assertEquals(IssueEditService.NAME_BLANK, e.code());
    }

    /**
     * Longer than the column it lands in.
     *
     * Without the cap the value reaches the driver as a truncation from inside
     * the transaction that was renaming the issue -- which names no field and
     * gives no length, and is answered as a server fault rather than as a
     * refusal the form can show.
     */
    @Test
    @Transactional
    public void atoolongNameIsRefused() {
        PublicationIssue issue = anIssue();
        String suggested = issue.getDescs().get(0).getName();
        String tooLong = "N".repeat(IssueEditService.MAX_NAME + 1);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue,
                                new IssueEditService.IssueEdit(Map.of("da", tooLong), null, null, null),
                                user()));
        assertEquals(IssueEditService.NAME_INVALID, e.code());
        assertEquals(suggested, issue.getDescs().get(0).getName(), "the refused name was written anyway");
    }

    /** And a name that exactly fits it is not. */
    @Test
    @Transactional
    public void anameThatFitsTheColumnIsAccepted() {
        PublicationIssue issue = anIssue();
        String atTheCap = "N".repeat(IssueEditService.MAX_NAME);

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", atTheCap), null, null, null), user());
        em.flush();

        assertEquals(atTheCap, issue.getDescs().get(0).getName());
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

    /**
     * The names an admin corrected on the create dialog, applied at create.
     *
     * This is the sequence the create endpoint performs: the issue is created,
     * which derives the suggested name from the series' pattern, and the names
     * the dialog carried are then applied THROUGH THE RENAME. Applying them any
     * other way would set the name without marking it as a decision, and the next
     * interval change would put the suggestion back over a name somebody typed --
     * silently, and on an issue they had already gone looking for by that name.
     *
     * Creating and then renaming as two requests would leave a window in which
     * the issue is listed under a name nobody chose, which is what the optional
     * map on the create body removes.
     */
    @Test
    @Transactional
    public void namesGivenAtCreateAreAppliedThroughTheRenamePath() {
        PublicationIssue issue = anIssue();
        String suggested = issue.getDescs().get(0).getName();
        assertNotNull(suggested, "the create derived no name, so there is nothing to correct");

        editService.applyNames(issue, Map.of("da", "  EfS uge 44 (dobbeltuge)  "), user());
        em.flush();

        PublicationIssueDesc desc = issue.getDescs().get(0);
        assertEquals("EfS uge 44 (dobbeltuge)", desc.getName(), "the name was not applied, or not trimmed");
        assertTrue(desc.isNameOverridden(),
                "a name corrected at create that does not mark itself as one is put back by the first "
                        + "interval change");
        assertTrue(actions(issue).contains(AuditAction.NAME_CHANGED),
                "the issue was created under a name somebody typed and nothing in the trail says so");
    }

    /**
     * A field the create dialog left empty means "as the series names it".
     *
     * The dialog prefills the suggestions, so an emptied field is a deliberate
     * act -- and the only sensible reading of it is the one every other override
     * has: do not decide this one, let the series. Refusing here would ask an
     * admin to retype a name that is already on the screen in front of them.
     */
    @Test
    @Transactional
    public void aBlankNameGivenAtCreateFollowsTheSeries() {
        PublicationIssue issue = anIssue();
        String suggested = issue.getDescs().get(0).getName();

        editService.applyNames(issue, Map.of("da", "  "), user());
        em.flush();

        PublicationIssueDesc desc = issue.getDescs().get(0);
        assertEquals(suggested, desc.getName(), "the issue was created under a name nobody chose");
        assertFalse(desc.isNameOverridden(),
                "an emptied field pinned the suggestion, so the interval could never re-render it");
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
