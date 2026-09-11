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
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The printed numbering: what an edition is CALLED, beside what it computes as.
 *
 * The whole of this is one distinction. week, weekTo and year are DERIVED from
 * the cut-off and are arithmetic -- the list order, the timeline strip, gap
 * detection and the archive all read them, and nothing a person types may reach
 * them. The labels are free text and go on the page: "36+37", "36 og 37", "36
 * &amp; 37" have all been published by this estate, and none of them is a number.
 *
 * So every assertion here is a pair: the printed thing moved, and the computed
 * thing did not.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueNumberingLabelTest {

    /** 2026-09-02 12:00 UTC -- a Wednesday in ISO week 36 of 2026. */
    private static final long CUTOFF = 1_788_350_400_000L;

    private static final long WEEK = 7L * 24 * 3600 * 1000;

    @Inject
    IssueEditService editService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueAuditService audit;

    @Inject
    IssueShape shape;

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
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        PublicationSeriesDesc desc = s.createDesc("da");
        desc.setName("Test series");
        // Both patterns render the numbering, which is the point: one typed label
        // has to reach the title AND the file name, or the document argues with
        // its own address.
        desc.setNameSuggestionPattern("Uge ${week}, ${year}");
        desc.setFileNamePattern("EfS-Uge-${week}-${year}.pdf");
        em.persist(s);

        PublicationIssue i = lifecycle.create(s, new Date(CUTOFF - WEEK),
                IntervalBoundSource.STAMPED, user());
        i.setIntervalTo(new Date(CUTOFF));
        shape.renumber(i, s);
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

    private static IssueEditService.IssueEdit labels(String week, String year) {
        return new IssueEditService.IssueEdit(null, null, null, null, null, false, null,
                week, year, null, null);
    }

    // ------------------------------------------------------------------- derived

    /** The fixture is numbered from its cut-off, which is what everything else assumes. */
    @Test
    @Transactional
    public void theFixtureIsNumberedFromItsCutoff() {
        PublicationIssue issue = anIssue();
        assertEquals(36, issue.getWeek());
        assertEquals(2026, issue.getYear());
        assertEquals("Uge 36, 2026", issue.getDescs().get(0).getName());
    }

    // -------------------------------------------------------------------- W1

    /**
     * A typed week reaches the title -- and the derived week does not move.
     *
     * The double week is the case this exists for: "uge 36 og 37" was typed by
     * hand, in every language, every time a week went out late, because the only
     * column that could hold it was the one the ordering reads.
     */
    @Test
    @Transactional
    public void atypedWeekIsPrintedWhileTheDerivedWeekStands() {
        PublicationIssue issue = anIssue();

        editService.update(issue, labels("36+37", null), user());
        em.flush();

        assertEquals("36+37", PrintedNumbering.printedWeek(issue),
                "the printed week is not the label somebody typed");
        assertEquals("Uge 36+37, 2026", issue.getDescs().get(0).getName(),
                "the suggested name did not follow the printed numbering, so the title and the "
                        + "file name would disagree about which weeks the edition covers");
        assertEquals(36, issue.getWeek(),
                "the DERIVED week moved; the list order, the timeline and the gap detector all "
                        + "read it, and a free-text week would take every one of them with it");
        assertTrue(actions(issue).contains(AuditAction.NUMBERING_CHANGED));
    }

    /** The file-name pattern reads the same label, so the document lands where its title says. */
    @Test
    @Transactional
    public void atypedWeekReachesTheFileName() {
        PublicationIssue issue = anIssue();

        editService.update(issue, labels("36+37", null), user());
        em.flush();

        assertEquals("EfS-Uge-36+37-2026.pdf",
                IssueFileNaming.suggested(issue, issue.getSeries(), issue.getDescs().get(0),
                        new Date(CUTOFF)),
                "the file name still renders the derived week; one decision, two answers");
    }

    /** A year label prints too -- which is how an edition spanning a turn of the year is titled. */
    @Test
    @Transactional
    public void atypedYearIsPrinted() {
        PublicationIssue issue = anIssue();

        editService.update(issue, labels(null, "2025/2026"), user());
        em.flush();

        assertEquals("2025/2026", PrintedNumbering.printedYear(issue));
        assertEquals(2026, issue.getYear(), "the derived year moved");
        assertEquals("Uge 36, 2025/2026", issue.getDescs().get(0).getName());
    }

    /**
     * A BLANK label is the way back, and the only one.
     *
     * There is no third state to store: absent means the derived number prints,
     * so "follow the cut-off again" is expressed by emptying the field. Refusing
     * blank would leave a typed label reachable only by retyping the derived
     * number as text, which is the same drift by hand.
     */
    @Test
    @Transactional
    public void ablankLabelGoesBackToTheDerivedNumber() {
        PublicationIssue issue = anIssue();
        editService.update(issue, labels("36+37", null), user());
        em.flush();

        editService.update(issue, labels("", null), user());
        em.flush();

        assertNull(issue.getWeekLabel(), "the label was not cleared");
        assertEquals("36", PrintedNumbering.printedWeek(issue));
        assertEquals("Uge 36, 2026", issue.getDescs().get(0).getName(),
                "the name did not go back to rendering the derived week");
        assertFalse(PrintedNumbering.isOverridden(issue));
    }

    /**
     * A label nobody sent is left alone.
     *
     * The load-bearing case for every OTHER edit: the criteria panel sends
     * {criteriaOverride} and nothing else, and absent and null are the same value
     * by the time a body has been deserialised. Reading a null as "clear" would
     * make every partial edit wipe the numbering of the issue it was editing.
     */
    @Test
    @Transactional
    public void anabsentLabelIsNotAClear() {
        PublicationIssue issue = anIssue();
        editService.update(issue, labels("36+37", null), user());
        em.flush();

        editService.update(issue,
                new IssueEditService.IssueEdit(Map.of("da", "Noget andet"), null, null, null),
                user());
        em.flush();

        assertEquals("36+37", issue.getWeekLabel(),
                "a partial edit that said nothing about the numbering cleared it");
    }

    /** Its own audit action, carrying both ends. */
    @Test
    @Transactional
    public void thetrailSaysWhatTheNumberingWasAndWhatItBecame() {
        PublicationIssue issue = anIssue();
        editService.update(issue, labels("36+37", null), user());
        em.flush();

        IssueAuditEntry entry = audit.forIssue(issue).stream()
                .filter(a -> a.getAction() == AuditAction.NUMBERING_CHANGED)
                .findFirst().orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) entry.getDetail();
        @SuppressWarnings("unchecked")
        Map<String, Object> to = (Map<String, Object>) detail.get("to");
        assertEquals("36+37", to.get("weekLabel"));
        assertTrue(detail.containsKey("from"),
                "a history line that cannot say what the numbering was answers nothing");
    }

    /** Nothing sent, nothing recorded. */
    @Test
    @Transactional
    public void anunchangedLabelProducesNoEntry() {
        PublicationIssue issue = anIssue();
        editService.update(issue, labels("36+37", null), user());
        em.flush();
        editService.update(issue, labels("36+37", null), user());
        em.flush();

        assertEquals(1, actions(issue).stream().filter(a -> a == AuditAction.NUMBERING_CHANGED).count(),
                "a re-sent identical label produced a second entry saying it changed");
    }

    // --------------------------------------------------------------- refusals

    /** Longer than the column: it goes on a cover and into a file name. */
    @Test
    @Transactional
    public void atoolongLabelIsRefused() {
        PublicationIssue issue = anIssue();
        String tooLong = "3".repeat(PrintedNumbering.MAX_LABEL + 1);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue, labels(tooLong, null), user()));
        assertEquals(PrintedNumbering.INVALID, e.code());
    }

    /** A newline in a label reaches a PDF heading and a published file name. */
    @Test
    @Transactional
    public void acontrolCharacterIsRefused() {
        PublicationIssue issue = anIssue();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue, labels("36\n37", null), user()));
        assertEquals(PrintedNumbering.INVALID, e.code());
    }

    /**
     * AND NOTHING ELSE IS REFUSED. The ruling is explicit: a week may be written
     * any way the publication writes it, so a validator that understood "36+37"
     * would refuse "36 og 37" the first week somebody typed it.
     */
    @Test
    @Transactional
    public void afreeTextLabelIsNotFormatChecked() {
        PublicationIssue issue = anIssue();

        editService.update(issue, labels("36 og 37", null), user());
        em.flush();

        assertEquals("36 og 37", issue.getWeekLabel());
    }

    /** A published edition's numbering is frozen with everything else it printed. */
    @Test
    @Transactional
    public void apublishedIssueRefusesALabel() {
        PublicationIssue issue = anIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(issue, labels("36+37", null), user()));
        assertEquals("ISSUE_NOT_OPEN", e.code());
    }

    // ------------------------------------------------------------------- wire

    /** Both facts reach the editor shape: what was written, and what will be printed. */
    @Test
    @Transactional
    public void thewireCarriesTheLabelAndTheResolvedValue() {
        PublicationIssue issue = anIssue();
        editService.update(issue, labels("36+37", null), user());
        em.flush();

        SystemPublicationIssueVo vo = issue.toVo(SystemPublicationIssueVo.class);

        assertEquals("36+37", vo.getWeekLabel());
        assertEquals("36+37", vo.getPrintedWeek());
        assertEquals("2026", vo.getPrintedYear());
        assertEquals(36, vo.getWeek(), "the numeric week left the wire with the label");
        assertTrue(vo.isNumberingOverridden());
        assertNull(vo.getYearLabel());
    }
}
