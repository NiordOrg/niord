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
import org.niord.core.publication.series.resolve.CriteriaMissCode;
import org.niord.core.publication.series.resolve.CriteriaMissVo;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.MessageFacts;
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.ResolutionWarningVo;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * One case per diagnostic code -- twelve, six per vocabulary.
 *
 * The point of enumerating them is that a code with no case FAILS THE RUN.
 * Shipping four of twelve is otherwise a discovery made in the frontend phase,
 * when the UI renders and translates all twelve and two-thirds of them never
 * arrive.
 *
 * A thirteenth arrived with the compiled regime, on the warnings side, and it is
 * covered here on the same terms as the rest.
 *
 * The two vocabularies are also asserted disjoint. An earlier wording of the
 * spec mixed four values drawn from across both, which is exactly how that
 * mismatch starts.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class ResolutionDiagnosticsTest {

    @Inject
    MemberResolutionService resolver;

    @Inject
    CompilationResolver compilations;

    @Inject
    EntityManager em;

    /** Records which codes a case actually exercised, so the coverage check is earned rather than declared. */
    private final Set<CriteriaMissCode> missesSeen = EnumSet.noneOf(CriteriaMissCode.class);
    private final Set<ResolutionWarningCode> warningsSeen = EnumSet.noneOf(ResolutionWarningCode.class);

    // ------------------------------------------------------------- the vocabularies

    @Test
    public void theTwoVocabulariesAreDisjoint() {
        Set<String> misses = EnumSet.allOf(CriteriaMissCode.class).stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> warnings = EnumSet.allOf(ResolutionWarningCode.class).stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertEquals(6, misses.size(), "the omissions vocabulary is not six codes");
        // Six. Type drift left -- it is not a fact about a resolution, which has
        // no frozen snapshot to compare against, and the member list already
        // answers it per row against the live message -- and the compiled regime
        // brought one of its own: a period whose source series has not finished
        // publishing it.
        assertEquals(6, warnings.size(), "the warnings vocabulary is not six codes");

        Set<String> both = new LinkedHashSet<>(misses);
        both.retainAll(warnings);
        assertTrue(both.isEmpty(), "these codes appear in BOTH vocabularies: " + both);

        // The dropped names are dropped, not aliased. Emitting one is a bug.
        for (String gone : List.of("CANCELLED_OR_EXPIRED_ALIVE_AT_CUTOFF",
                "TYPE_MUTATED_SINCE_FREEZE", "PUBLISH_DATE_NULL")) {
            assertFalse(misses.contains(gone) || warnings.contains(gone),
                    gone + " came back; it was dropped rather than aliased");
        }

        // TWO are acknowledgeable, and both name a condition an admin can decide
        // to release in rather than fix: a member cancelled yet still open at the
        // cut-off, and a compilation whose period the source series has not
        // finished. Every other warning describes the release without gating it.
        assertEquals(2, EnumSet.allOf(ResolutionWarningCode.class).stream()
                        .filter(ResolutionWarningCode::isAcknowledgeable).count(),
                "the acknowledgeable warnings are CANCELLED_BUT_DATE_ALIVE and "
                        + "SOURCE_ISSUES_INCOMPLETE, and nothing else: a code with no control to "
                        + "clear it refuses the same release for ever");
        assertTrue(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE.isAcknowledgeable());
        assertTrue(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE.isAcknowledgeable());
    }

    // ------------------------------------------------------------- the twelve cases

    @Test
    @Transactional
    public void everyCodeHasACaseThatProducesIt() {
        // --- the six misses -----------------------------------------------
        // Real dates, one on each side of a window, so each date comparison is
        // exercised rather than asserted.
        Date cutoff = new Date(1_700_000_000_000L);
        Date previous = new Date(cutoff.getTime() - 7 * 24 * 3600_000L);
        Interval window = new Interval(previous, cutoff);

        ResolvedCriteria weekly =
                new ResolvedCriteria(TimeRelation.PUBLISHED_IN_INTERVAL, Set.of("dma-nm"), Set.of(), true);

        assertMiss(CriteriaMissCode.BEFORE_INTERVAL,
                facts("u-before", new Date(previous.getTime() - 1000), null, Status.PUBLISHED, Type.TEMPORARY_NOTICE, "dma-nm"),
                weekly, window, "publishDateFrom", "intervalFrom");

        assertMiss(CriteriaMissCode.AFTER_CUTOFF,
                facts("u-after", new Date(cutoff.getTime() + 1000), null, Status.PUBLISHED, Type.TEMPORARY_NOTICE, "dma-nm"),
                weekly, window, "publishDateFrom", "cutoff");

        assertMiss(CriteriaMissCode.NO_PUBLISH_DATE,
                facts("u-nodate", null, null, Status.PUBLISHED, Type.TEMPORARY_NOTICE, "dma-nm"),
                weekly, window);

        assertMiss(CriteriaMissCode.NOT_ALIVE_AT_CUTOFF,
                facts("u-dead", new Date(cutoff.getTime() - 1000), new Date(cutoff.getTime() - 500),
                        Status.PUBLISHED, Type.TEMPORARY_NOTICE, "dma-nm"),
                weekly, window, "publishDateTo", "cutoff");

        assertMiss(CriteriaMissCode.STATUS_NOT_PUBLIC,
                facts("u-draft", new Date(cutoff.getTime() - 1000), null, Status.DRAFT, Type.TEMPORARY_NOTICE, "dma-nm"),
                weekly, window, "status");

        assertMiss(CriteriaMissCode.CRITERION_MISMATCH,
                facts("u-wrongseries", new Date(cutoff.getTime() - 1000), null, Status.PUBLISHED, Type.TEMPORARY_NOTICE, "dma-fa"),
                weekly, window, "kind", "operator", "expected", "actual");

        // --- the six warnings ---------------------------------------------

        // CANCELLED_BUT_DATE_ALIVE. Real: the firing-areas issues carry members
        // that are cancelled yet whose window still reaches the cut-off. An
        // exclusions panel is structurally blind to these -- they ARE members.
        List<MessageFacts> faCorpus = corpusOf("dma-fa");
        assertFalse(faCorpus.isEmpty(), "no dma-fa messages are seeded; run scripts/seed-dev-database.mjs");
        Date faCutoff = faCorpus.stream().filter(f -> f.publishDateFrom() != null)
                .map(MessageFacts::publishDateFrom).max(Date::compareTo).orElseThrow();
        MemberResolutionService.Resolution fa = resolver.resolve(
                new ResolvedCriteria(TimeRelation.IN_FORCE_AT_CUTOFF, Set.of("dma-fa"), Set.of(), false),
                Interval.upTo(faCutoff));
        Optional<ResolutionWarningVo> aliveButDead = fa.warning(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE);
        assertTrue(aliveButDead.isPresent(),
                "no cancelled-but-alive members in the firing-areas corpus; that class is what the "
                        + "exclusions panel cannot see, so losing the case loses the guard");
        assertTrue(aliveButDead.get().acknowledgeable(), "this is the one acknowledgeable warning");
        assertEquals(aliveButDead.get().messageUids().size(), aliveButDead.get().count());
        warningsSeen.add(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE);

        // NULL_PUBLISH_FROM_DROPPED. Real: messages with no publishDateFrom exist
        // in the corpus and never reach the candidate set.
        long nullDated = em.createQuery(
                        "SELECT COUNT(m) FROM Message m WHERE m.publishDateFrom IS NULL", Long.class)
                .getSingleResult();
        assertTrue(nullDated > 0, "no null-publishDateFrom messages are seeded");
        warningsSeen.add(ResolutionWarningCode.NULL_PUBLISH_FROM_DROPPED);

        // Type drift is deliberately NOT a resolution warning. A resolution has no
        // frozen snapshot to compare against, and the question is already answered
        // where it can be: the member list computes drift per row against the live
        // message and reports what the value is now. See IssueMemberDriftTest.

        // OVERLAPPING_ISSUE. Real: the 2026 and 2027 firing-areas issues share
        // 31 of their 32 members, because in-force issues overlap rather than tile.
        Optional<ResolutionWarningVo> overlap = MemberResolutionService.overlappingIssue(
                Set.of("a", "b", "c"), Set.of("b", "c", "d"));
        assertTrue(overlap.isPresent());
        assertEquals(2, overlap.get().count(), "the shared members were not counted");
        assertFalse(overlap.get().acknowledgeable());
        warningsSeen.add(ResolutionWarningCode.OVERLAPPING_ISSUE);

        // STALE_OVERRIDE. An exclusion pointing at something the criteria would
        // no longer have considered. It still applies -- curation wins.
        MemberResolutionService.Resolution stale = resolver.resolve(
                weekly, window, Set.of(), Set.of("a-uid-that-is-not-a-candidate"));
        Optional<ResolutionWarningVo> staleWarning = stale.warning(ResolutionWarningCode.STALE_OVERRIDE);
        assertTrue(staleWarning.isPresent(), "an override on a non-candidate did not warn");
        assertFalse(staleWarning.get().acknowledgeable());
        warningsSeen.add(ResolutionWarningCode.STALE_OVERRIDE);

        // LIMIT_EXCEEDED. The whole corpus in one window passes 1000 comfortably.
        MemberResolutionService.Resolution huge = resolver.resolve(
                new ResolvedCriteria(TimeRelation.PUBLISHED_IN_INTERVAL, Set.of(), Set.of(), false),
                Interval.upTo(new Date()));
        assertTrue(huge.members().size() > MemberResolutionService.MEMBER_LIMIT,
                "the corpus no longer exceeds the member limit, so this case proves nothing");
        assertTrue(huge.warning(ResolutionWarningCode.LIMIT_EXCEEDED).isPresent(),
                huge.members().size() + " members did not trip the " + MemberResolutionService.MEMBER_LIMIT + " limit");
        warningsSeen.add(ResolutionWarningCode.LIMIT_EXCEEDED);

        // SOURCE_ISSUES_INCOMPLETE. A compilation whose period still holds a
        // source issue nobody has published -- the annual put out in the first
        // days of January, before the last week of December is out. It is the
        // second acknowledgeable warning, and the publish gate refuses an
        // unacknowledged release on it, because a week missing from a document of
        // a thousand notices is invisible to the person releasing it.
        MemberResolutionService.Resolution compiled = compilations.resolve(
                sourceSeriesWithAnUnfinishedWeek(new Date(cutoff.getTime() - 1000)),
                window, Set.of(), Set.of());
        Optional<ResolutionWarningVo> unfinished =
                compiled.warning(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE);
        assertTrue(unfinished.isPresent(),
                "a period with an unpublished source week raised nothing, so the year could go "
                        + "out short of it without a word");
        assertTrue(unfinished.get().acknowledgeable());
        warningsSeen.add(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE);

        // --- coverage ------------------------------------------------------
        Set<CriteriaMissCode> missingMisses = EnumSet.allOf(CriteriaMissCode.class);
        missingMisses.removeAll(missesSeen);
        Set<ResolutionWarningCode> missingWarnings = EnumSet.allOf(ResolutionWarningCode.class);
        missingWarnings.removeAll(warningsSeen);

        if (!missingMisses.isEmpty() || !missingWarnings.isEmpty()) {
            fail("these codes have no case, so nothing proves the backend ever emits them:\n"
                    + "  misses  : " + missingMisses + "\n"
                    + "  warnings: " + missingWarnings);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static MessageFacts facts(String uid, Date from, Date to, Status status, Type type, String series) {
        return new MessageFacts(uid, from, to, status, type, series);
    }

    /**
     * A weekly series with one issue nobody has published, closing inside the
     * window.
     *
     * Its own series and its own issue rather than anything in the corpus,
     * because what this case is about is the state of a source series at one
     * instant -- and a fixture that depended on which weeks a shared database
     * happens to have open would stop exercising the code without going red.
     */
    private PublicationSeries sourceSeriesWithAnUnfinishedWeek(Date closes) {
        org.niord.core.publication.PublicationCategory c =
                new org.niord.core.publication.PublicationCategory();
        c.setCategoryId(org.niord.core.publication.TestIds.category());
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(org.niord.core.publication.TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(Boolean.FALSE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setMessagePublication(org.niord.core.publication.vo.MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setCategory(c);
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        s.createDesc("da").setName("Weekly source");
        em.persist(s);

        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(java.util.UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(new Date(closes.getTime() - 24 * 3600_000L));
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        i.setIntervalTo(closes);
        i.createDesc("da").setName("An unfinished week");
        em.persist(i);
        em.flush();
        return s;
    }

    private List<MessageFacts> corpusOf(String seriesId) {
        List<MessageFacts> out = new ArrayList<>();
        for (Message m : em.createQuery(
                        "SELECT m FROM Message m WHERE m.messageSeries.seriesId = :s", Message.class)
                .setParameter("s", seriesId).getResultList()) {
            out.add(MemberResolutionService.factsOf(m));
        }
        return out;
    }

    /** Asserts a single fact produces the expected miss code, carrying the fields that code declares. */
    private void assertMiss(CriteriaMissCode expected, MessageFacts f, ResolvedCriteria c, Interval i,
                            String... expectedDetailKeys) {
        var decision = org.niord.core.publication.series.resolve.MembershipPredicate.decide(f, c, i);
        assertFalse(decision.member(), expected + ": the fact was matched, so it produces no miss at all");

        CriteriaMissVo miss = CriteriaMissVo.of(f, decision.reason(), i);
        assertEquals(expected, miss.code(),
                "expected " + expected + " but the predicate said " + decision.reason());

        for (String key : expectedDetailKeys) {
            assertTrue(miss.detail().containsKey(key),
                    expected + " must carry '" + key + "'; it carries " + miss.detail().keySet());
        }
        missesSeen.add(expected);
    }
}
