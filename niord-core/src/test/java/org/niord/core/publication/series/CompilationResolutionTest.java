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
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.IssueOrdering;
import org.niord.core.publication.series.resolve.MembershipReason;
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueDraftVo;
import org.niord.core.publication.series.vo.IssueDraftWarningVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RI-16 and RI-17: what a compilation contains, and in which order.
 *
 * DATABASE-BACKED, unlike the membership predicate's own tests, and that is the
 * whole difference between the two derivations. A compiled membership is not a
 * verdict on message facts -- it is a fact about OTHER ISSUES' frozen rows, so
 * there is nothing to decide in the abstract and nothing a pure fixture could
 * stand in for. The rows and their order are the subject.
 *
 * The fixture is a weekly whose issues were frozen by hand rather than published
 * through the transaction, because what is being asserted is the READ: which
 * issues are in the window, which of their rows survive the owner rule, and what
 * order they come out in. Publishing three weeklies through the full transaction
 * to produce the same rows would make the assertions depend on every step of it.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class CompilationResolutionTest {

    @Inject
    CompilationResolver compilations;

    @Inject
    MemberResolutionService resolver;

    @Inject
    IssueDraftService drafts;

    @Inject
    EntityManager em;

    private static final long HOUR = 3600_000L;

    /**
     * The fixture's clock, fixed rather than relative to now.
     *
     * Every bound in this file is a window over these instants, and a fixture
     * anchored on the wall clock would make the assertions depend on when the
     * suite ran -- which is how a boundary test passes for eleven months.
     */
    private static final Date T0 = new Date(1_700_000_000_000L);

    private static Date at(int hours) {
        return new Date(T0.getTime() + hours * HOUR);
    }

    // ------------------------------------------------------------------ fixture

    private String messageSeriesId;

    /** The weekly being compiled, and the compilation that names it. */
    private record Estate(PublicationSeries source, PublicationSeries compilation,
                          Map<String, Message> messages,
                          PublicationIssue week1, PublicationIssue week2, PublicationIssue week3,
                          PublicationIssue retired, PublicationIssue open) {
    }

    /**
     * Three published weeks, a retired one, an open one, and a repeated message.
     *
     * Every element of it is load-bearing: the repeat is what makes the owner
     * rule visible, the retired issue is what makes "published only" mean
     * something, and the open one is what the coverage survey reports.
     */
    private Estate estate() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        messageSeriesId = TestIds.id("ms-");
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId(messageSeriesId);
        ms.setMainType(MainType.NM);
        em.persist(ms);

        PublicationSeries source = series(c, "weekly", SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationSeries compilation = series(c, "annual", SeriesCadence.YEARLY,
                TimeRelation.COMPILED_FROM_SOURCE);
        // No criteria and no liveness flag: S-24 and S-2 refuse both on a
        // compilation, and the derivation never asks for either.
        compilation.setAliveAtCutoff(null);
        compilation.setSourceSeries(source);

        Map<String, Message> messages = new LinkedHashMap<>();
        for (String shortId : List.of("NM-1", "NM-2", "NM-3", "NM-4", "NM-5", "NM-9")) {
            messages.put(shortId, message(shortId));
        }

        // Week 1 closes at hour 24 and printed NM-1 then NM-2.
        PublicationIssue week1 = issue(source, T0, at(24), IssueStatus.PUBLISHED);
        member(week1, messages.get("NM-1"), 0);
        member(week1, messages.get("NM-2"), 1);

        // Week 2 closes at hour 48 and printed NM-3 -- and NM-2 again. THE REPEAT:
        // fourteen memberships in the live estate are in more than one weekly, and
        // the compilation has to print each message once, under the week that
        // printed it first.
        PublicationIssue week2 = issue(source, at(24), at(48), IssueStatus.PUBLISHED);
        member(week2, messages.get("NM-3"), 0);
        member(week2, messages.get("NM-2"), 1);

        PublicationIssue week3 = issue(source, at(48), at(72), IssueStatus.PUBLISHED);
        member(week3, messages.get("NM-4"), 0);

        // RETIRED: what went out for that period should not stand, so its rows are
        // not something to compile.
        PublicationIssue retired = issue(source, at(72), at(96), IssueStatus.RETIRED);
        member(retired, messages.get("NM-5"), 0);

        // OPEN: no frozen rows exist at all, so it contributes nothing and is
        // reported instead.
        PublicationIssue open = issue(source, at(96), null, IssueStatus.OPEN);
        open.setIntervalTo(at(120));
        em.merge(open);

        em.flush();
        return new Estate(source, compilation, messages, week1, week2, week3, retired, open);
    }

    private PublicationSeries series(PublicationCategory c, String label, SeriesCadence cadence,
                                     TimeRelation relation) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(Boolean.FALSE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setCategory(c);
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        s.createDesc("da").setName("Test " + label);
        em.persist(s);
        return s;
    }

    private PublicationIssue issue(PublicationSeries s, Date from, Date stamp, IssueStatus status) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(status);
        i.setIntervalFrom(from);
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        i.setCutoffStampedAt(stamp);
        i.createDesc("da").setName("Issue " + i.getPublicId());
        em.persist(i);
        return i;
    }

    private Message message(String shortId) {
        MessageSeries ms = em.createQuery(
                        "SELECT ms FROM MessageSeries ms WHERE ms.seriesId = :id", MessageSeries.class)
                .setParameter("id", messageSeriesId).getSingleResult();
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(ms);
        m.setShortId(shortId);
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(T0);
        em.persist(m);
        return m;
    }

    /** A frozen row, written by hand exactly as the freeze would have written it. */
    private void member(PublicationIssue issue, Message m, int sortIndex) {
        IssueMember row = new IssueMember();
        row.setIssue(issue);
        row.setMessageUid(m.getUid());
        row.setMessage(m);
        row.setSortIndex(sortIndex);
        row.setFrozenShortId(m.getShortId());
        row.setFrozenMainType(m.getMainType().name());
        row.setFrozenType(m.getType().name());
        row.setFrozenStatus(m.getStatus().name());
        row.setFrozenPublishDateFrom(m.getPublishDateFrom());
        row.setSource(MemberSource.CRITERIA);
        em.persist(row);
    }

    /**
     * The short ids of a member SET, sorted, so a failure names notices.
     *
     * SORTED because a member set is a set: the union comes back in whatever
     * order one query over fifty issues happens to return, and an assertion that
     * depended on it would be pinning the database rather than the rule. Print
     * order is a separate question with its own assertion below.
     */
    private static List<String> shortIdsOf(Estate e, Iterable<String> uids) {
        List<String> out = new ArrayList<>(orderedShortIds(e, uids));
        out.sort(String::compareTo);
        return out;
    }

    /** The same names, in the order they were given. */
    private static List<String> orderedShortIds(Estate e, Iterable<String> uids) {
        Map<String, String> byUid = new LinkedHashMap<>();
        e.messages().forEach((shortId, m) -> byUid.put(m.getUid(), shortId));
        List<String> out = new ArrayList<>();
        for (String uid : uids) {
            out.add(byUid.getOrDefault(uid, uid));
        }
        return out;
    }

    private static String uid(Estate e, String shortId) {
        return e.messages().get(shortId).getUid();
    }

    // -------------------------------------------------------------- the union

    /**
     * RI-16. The union of the published sources' frozen rows, and nothing else.
     *
     * NM-5 is the assertion that matters here: it is a perfectly ordinary member
     * row whose issue was retired, and retiring an issue is the statement that
     * what went out for that period should not stand. A compilation that carried
     * it would print a notice under a week that was withdrawn.
     */
    @BindsRule({"RI-16"})
    @Test
    @Transactional
    public void theUnionIsWhatThePublishedSourcesPrinted() {
        Estate e = estate();

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)), Set.of(), Set.of());

        assertEquals(List.of("NM-1", "NM-2", "NM-3", "NM-4"), shortIdsOf(e, r.members()),
                "the union is not what the published weeks printed");
        assertFalse(r.members().contains(uid(e, "NM-5")),
                "a retired week's rows were compiled; retiring an issue says its contents should "
                        + "not stand, and the annual would print them under a withdrawn week");
        assertTrue(r.compiled(), "the resolution does not say it was compiled");
    }

    /**
     * RI-16. A repeated uid is owned by the EARLIEST source issue.
     *
     * The member table is unique on (issue, messageUid), so keeping both rows is
     * not an option -- the only question is which week the row is printed under,
     * and the answer has to be deterministic or the same year renders differently
     * on two runs.
     */
    @Test
    @Transactional
    public void arepeatedMessageIsOwnedByTheWeekThatPrintedItFirst() {
        Estate e = estate();

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)), Set.of(), Set.of());

        CompilationResolver.SourceRef owner = r.sourceOf().get(uid(e, "NM-2"));
        assertNotNull(owner, "the repeated message has no source issue at all");
        assertEquals(e.week1().getPublicId(), owner.publicId(),
                "the repeated message is owned by the later week; the earliest source printed it "
                        + "first and is where a reader will look for it");
        assertEquals(at(24), owner.cutoff());
        assertEquals(1, owner.sortIndex(), "the owner's own print position did not travel");
    }

    /**
     * RI-16. Every reason is FROM_SOURCE_ISSUE, and there are no misses.
     *
     * A compilation rejects nothing: its candidates are rows another issue
     * already published, judged at that issue's own cut-off. An omissions panel
     * for one would be a list of messages nobody's criteria dropped.
     */
    @Test
    @Transactional
    public void everyCompiledMemberSaysItsSourceIssuePrintedIt() {
        Estate e = estate();

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)), Set.of(), Set.of());

        for (String uid : r.members()) {
            assertEquals(MembershipReason.FROM_SOURCE_ISSUE, r.decisions().get(uid).reason(),
                    "a compiled member gives a reason no source issue supports");
        }
        assertTrue(r.misses().isEmpty(),
                "a compilation reported criteria misses; it runs no criteria to miss with");
        assertEquals(4, r.candidateCount(),
                "the union uids are the candidate set, which is what appliedAtPublish reads");
    }

    // ------------------------------------------------------------ the window

    /**
     * The window decides which weeks are compiled, and moving it moves the union.
     *
     * This is what makes a running annual answerable mid-year and what the
     * `?at=` parameter means on the issue screen: the numbers move with the
     * cut-off being asked about, rather than with the clock.
     */
    @Test
    @Transactional
    public void thewindowDecidesWhichWeeksAreCompiled() {
        Estate e = estate();

        assertEquals(List.of("NM-1", "NM-2"),
                shortIdsOf(e, compilations.resolve(e.source(), new Interval(T0, at(24)),
                        Set.of(), Set.of()).members()),
                "a window closing on week 1's cut-off must hold week 1 and nothing after it");

        assertEquals(List.of("NM-1", "NM-2", "NM-3"),
                shortIdsOf(e, compilations.resolve(e.source(), new Interval(T0, at(48)),
                        Set.of(), Set.of()).members()));

        // Half-open at the lower bound: a week stamped exactly on the previous
        // compilation's cut-off belongs to that one, and to exactly one. NM-1 is
        // week 1's alone, so its absence is what says week 1 was not compiled
        // again -- NM-2 is here because week 2 printed it too, which is the owner
        // rule seen from the other side.
        assertEquals(List.of("NM-2", "NM-3", "NM-4"),
                shortIdsOf(e, compilations.resolve(e.source(), new Interval(at(24), at(72)),
                        Set.of(), Set.of()).members()),
                "week 1, stamped exactly on the lower bound, was compiled twice");
    }

    /** With no lower bound, everything up to the cut-off is compiled. */
    @Test
    @Transactional
    public void anunboundedWindowCompilesEverythingUpToTheCutoff() {
        Estate e = estate();

        assertEquals(List.of("NM-1", "NM-2", "NM-3"),
                shortIdsOf(e, compilations.resolve(e.source(), Interval.upTo(at(48)),
                        Set.of(), Set.of()).members()));
    }

    // ------------------------------------------------------------- curation

    /**
     * RI-10 applies on top, exactly as it does to a query.
     *
     * An exclude removes a row a week printed; an include adds one no week did,
     * and it belongs to no week -- which is why the PDF gives the includes a
     * section of their own.
     */
    @Test
    @Transactional
    public void curationAppliesOnTopOfTheUnion() {
        Estate e = estate();

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)),
                Set.of(uid(e, "NM-9")), Set.of(uid(e, "NM-3")));

        assertEquals(List.of("NM-1", "NM-2", "NM-4", "NM-9"), shortIdsOf(e, r.members()));
        assertEquals(MembershipReason.MANUAL_EXCLUDE, r.decisions().get(uid(e, "NM-3")).reason());
        assertFalse(r.decisions().get(uid(e, "NM-3")).member());
        assertEquals(MembershipReason.MANUAL_INCLUDE, r.decisions().get(uid(e, "NM-9")).reason());
        assertNull(r.sourceOf().get(uid(e, "NM-9")),
                "a manual include has no source week, and inventing one would put it under a "
                        + "heading nobody chose");
    }

    /** An exclude naming a message no week printed is reported, and still applies. */
    @Test
    @Transactional
    public void anexcludeOutsideTheUnionIsReportedAsStale() {
        Estate e = estate();

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)), Set.of(), Set.of(uid(e, "NM-9")));

        assertTrue(r.warning(ResolutionWarningCode.STALE_OVERRIDE).isPresent(),
                "an exclude that changed nothing went unreported");
    }

    // --------------------------------------------------------------- RI-17

    /**
     * RI-17. Compiled rows come out week by week, in each week's own order, and
     * the manual includes come last.
     *
     * The series' own sort is deliberately NOT what decides this. An annual that
     * re-sorted its thousand members by area would not read as the year's weeklies
     * stapled together -- it would read as a different document with the same
     * contents, and the per-week headings would have nothing to head.
     */
    @BindsRule({"RI-17"})
    @Test
    @Transactional
    public void thecompiledOrderIsWeekByWeekWithTheIncludesLast() {
        Estate e = estate();

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)), Set.of(uid(e, "NM-9")), Set.of());

        IssueOrdering.SortSpec sort = IssueOrdering.resolveSort("AREA", IssueOrdering.Direction.ASC, null);
        List<IssueOrdering.Orderable> ordered = MemberResolutionService.orderFor(
                r, resolver.orderablesFor(r.members()), sort);

        assertEquals(List.of("NM-1", "NM-2", "NM-3", "NM-4", "NM-9"),
                orderedShortIds(e, ordered.stream().map(IssueOrdering.Orderable::uid).toList()),
                "the compiled order is not week-by-week with the manual include last");

        Map<String, Integer> sortIndex = IssueOrdering.assignSortIndex(ordered);
        assertEquals(5, sortIndex.size());
        assertEquals(Integer.valueOf(0), sortIndex.get(uid(e, "NM-1")));
        assertEquals(Integer.valueOf(4), sortIndex.get(uid(e, "NM-9")),
                "sortIndex must be dense over the WHOLE list; the manual section is part of the "
                        + "same document");
    }

    /** Anything that is not a compilation orders exactly as it always did. */
    @Test
    @Transactional
    public void anuncompiledResolutionIsOrderedByTheSeriesSort() {
        Estate e = estate();
        Set<String> uids = Set.of(uid(e, "NM-4"), uid(e, "NM-1"));
        IssueOrdering.SortSpec sort = IssueOrdering.resolveSort("AREA", IssueOrdering.Direction.ASC, null);
        List<IssueOrdering.Orderable> orderables = resolver.orderablesFor(uids);

        assertEquals(
                IssueOrdering.order(orderables, sort).stream()
                        .map(IssueOrdering.Orderable::uid).toList(),
                MemberResolutionService.orderFor(MemberResolutionService.Resolution.curated(uids),
                                orderables, sort).stream()
                        .map(IssueOrdering.Orderable::uid).toList(),
                "a curated resolution was reordered; only a compilation orders by its sources");
    }

    // -------------------------------------------------------------- survey

    /**
     * The coverage survey: which weeks are in, which are still open, and where the
     * period is not covered at all.
     *
     * An OPEN week is the case this exists for: an annual released in early
     * January before the last week of December is out is a legitimate release and
     * a recurring one, and a missing week is invisible in a list of a thousand
     * rows.
     */
    @Test
    @Transactional
    public void thesurveyReportsAnOpenWeekAndTheWarningRefusesAnUnacknowledgedRelease() {
        Estate e = estate();

        CompilationResolver.Survey survey =
                compilations.survey(e.source(), new Interval(T0, at(200)));

        assertEquals(1, survey.open().size(), "the open week was not reported");
        assertEquals(e.open().getPublicId(), survey.open().get(0).publicId());
        assertEquals(3, survey.published().size(),
                "the published weeks are what the snapshot header records; the retired one is not "
                        + "one of them");
        assertFalse(survey.complete());

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)), Set.of(), Set.of());
        assertTrue(r.warning(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE).isPresent(),
                "an incomplete period raised no warning, so the publish gate would let the year "
                        + "out silently short of a week");
        assertTrue(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE.isAcknowledgeable(),
                "the warning must be acknowledgeable, or a release nobody can complete is the "
                        + "only outcome");
    }

    /** A period every published week covers is complete, and raises nothing. */
    @Test
    @Transactional
    public void acoveredPeriodIsComplete() {
        Estate e = estate();

        CompilationResolver.Survey survey =
                compilations.survey(e.source(), new Interval(T0, at(72)));

        assertTrue(survey.complete(),
                "a period covered by three published weeks reported as incomplete: "
                        + survey.open() + " " + survey.missing());
        assertEquals(3, survey.sources().size());
        assertTrue(compilations.resolve(e.source(), new Interval(T0, at(72)), Set.of(), Set.of())
                        .warning(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE).isEmpty());
    }

    /**
     * The stretch AFTER the last published week is not a gap.
     *
     * The half worth asserting on its own: the next weekly's cut-off falls after
     * this annual's, so it belongs to the next annual by the cut-off rule --
     * calling the tail a gap would report every compilation as incomplete for
     * ever, including a finished one. Nothing before the first week is a gap
     * either, for the mirror reason: the compilation's period may open before the
     * source series produced anything.
     */
    @Test
    @Transactional
    public void thestretchesOutsideThePublishedWeeksAreNotGaps() {
        Estate e = estate();

        // The window runs to hour 200 and the last published week closed at 72.
        assertTrue(compilations.survey(e.source(), new Interval(T0, at(200))).missing().isEmpty(),
                "the stretch after the last published week was reported missing");

        // And one opening long before week 1 did.
        assertTrue(compilations.survey(e.source(), new Interval(at(-240), at(72))).missing().isEmpty(),
                "the run-up before the first published week was reported missing");
    }

    /** A stretch BETWEEN two published weeks that nothing covers is MISSING. */
    @Test
    @Transactional
    public void astretchNoWeekCoversIsReportedMissing() {
        Estate e = estate();
        // Week 3 opens six hours after week 2 closed. Nothing covers the six hours
        // in between, and no issue exists for them.
        e.week3().setIntervalFrom(at(54));
        em.merge(e.week3());
        em.flush();

        CompilationResolver.Survey survey =
                compilations.survey(e.source(), new Interval(T0, at(72)));

        assertEquals(1, survey.missing().size(),
                "the uncovered stretch between week 2 and week 3 was not reported");
        CompilationResolver.SourceIssue gap = survey.missing().get(0);
        assertEquals(at(48), gap.intervalFrom(), "the gap does not open where week 2 closed");
        assertEquals(at(54), gap.cutoff(), "the gap does not close where week 3 opened");
        assertNull(gap.publicId(), "a synthesised row must name no issue");
        assertEquals(CompilationResolver.MISSING, gap.status());
        assertFalse(survey.complete());

        // The row sits between the weeks that bracket it, so the sources panel
        // reads as the period does rather than listing the gaps at the end.
        assertEquals(2, survey.sources().indexOf(gap),
                "the missing stretch is not between the weeks it separates");
    }

    /** The survey reads in cut-off order, so the panel reads as the period does. */
    @Test
    @Transactional
    public void thesurveyIsInCutoffOrder() {
        Estate e = estate();

        List<Date> cutoffs = compilations.survey(e.source(), new Interval(T0, at(200)))
                .sources().stream().map(CompilationResolver.SourceIssue::cutoff).toList();

        assertEquals(List.of(at(24), at(48), at(72), at(120)), cutoffs,
                "the sources panel would list the weeks out of order");
    }

    // ------------------------------------------------------------- contains

    /**
     * contains() answers the same question the union does, one message at a time.
     *
     * It exists because the message editor's panel asks it once per open issue on
     * every message somebody opens, and a running annual's union is a thousand
     * rows it would throw away. Two answers to one question is exactly the kind of
     * disagreement nobody reproduces, so it is asserted against the union itself.
     */
    @Test
    @Transactional
    public void containsAgreesWithTheUnionItRefusesToLoad() {
        Estate e = estate();
        Interval window = new Interval(T0, at(200));
        Set<String> union = compilations.resolve(e.source(), window, Set.of(), Set.of()).members();

        for (Map.Entry<String, Message> entry : e.messages().entrySet()) {
            String uid = entry.getValue().getUid();
            assertEquals(union.contains(uid), compilations.contains(e.source(), window, uid),
                    "contains() disagrees with the union about " + entry.getKey());
        }

        assertFalse(compilations.contains(e.source(), window, "no-such-uid"));
        assertFalse(compilations.contains(e.source(), new Interval(T0, at(24)), uid(e, "NM-4")),
                "contains() ignored the window; a later week's row is not in an earlier period");
    }

    // ---------------------------------------------------------------- the draft

    /**
     * The draft of a compilation carries the count the gap row is read for.
     *
     * The retro years are created from the strip: an admin looking at an
     * uncovered year presses create on the gap row, and the number on that form
     * is the whole of what they are deciding on. A compilation has no criteria
     * document, so the count used to come back null under "this publication
     * selects nothing by query -- it is an uploaded file or a link", which is a
     * false sentence about the one regime the row exists for.
     */
    @Test
    @Transactional
    public void adraftOfACompilationCountsWhatItsSourcesPrinted() {
        Estate e = estate();

        IssueDraftVo draft = drafts.draft(e.compilation(), null, T0, at(72), at(200));

        assertEquals(4, draft.getWouldMatchCount(),
                "the draft of a compilation reports no count, or the wrong one: the three published "
                        + "weeks in the window printed NM-1 to NM-4");
        assertTrue(draft.getWarnings().stream()
                        .map(IssueDraftWarningVo::code)
                        .noneMatch(IssueDraftService.NO_MEMBERSHIP_CRITERIA::equals),
                "the draft says the series selects nothing by query. It selects nothing BY QUERY, "
                        + "and it still has members -- the sentence reads to an admin as 'this is "
                        + "an uploaded file'");
    }

    // ----------------------------------------------------- an include on a union uid

    /**
     * An include naming a message a source week also printed is a HAND decision.
     *
     * Curation refuses the pairing in the ordinary order -- an include on a
     * message already in the union is OVERRIDE_ALREADY_A_MEMBER -- but the other
     * order is reachable: the include is recorded while the week is still open,
     * and the week is published afterwards. The row is then both, and one answer
     * has to win everywhere, because the freeze keys on the decision and the
     * order and the printed sections keyed on the source map. Split answers put
     * the notice in the middle of a week on screen and under "added by hand" in
     * the document that was released.
     */
    @Test
    @Transactional
    public void anincludeOnAUnionUidIsCarriedByTheHandAndNotByItsWeek() {
        Estate e = estate();
        String repeated = uid(e, "NM-2");

        MemberResolutionService.Resolution r = compilations.resolve(
                e.source(), new Interval(T0, at(200)), Set.of(repeated), Set.of());

        assertEquals(MembershipReason.MANUAL_INCLUDE, r.decisions().get(repeated).reason(),
                "the include lost to the union; a curator's decision is not overwritten by a "
                        + "derivation");
        assertNull(r.sourceOf().get(repeated),
                "the row is both a manual include and owned by a week, so the freeze files it "
                        + "under the hand and the ordering files it inside the week");
        assertTrue(r.candidateUids().contains(repeated),
                "the union still considered it, and the stale-override test reads exactly that");
    }
}
