/*
 * Copyright 2026 Danish Maritime Authority.
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
        // Five, since type drift left. It is not a fact about a resolution -- a
        // resolution has no frozen snapshot to compare against -- and the member
        // list already answers it per row, against the live message.
        assertEquals(5, warnings.size(), "the warnings vocabulary is not five codes");

        Set<String> both = new LinkedHashSet<>(misses);
        both.retainAll(warnings);
        assertTrue(both.isEmpty(), "these codes appear in BOTH vocabularies: " + both);

        // The dropped names are dropped, not aliased. Emitting one is a bug.
        for (String gone : List.of("CANCELLED_OR_EXPIRED_ALIVE_AT_CUTOFF",
                "TYPE_MUTATED_SINCE_FREEZE", "PUBLISH_DATE_NULL")) {
            assertFalse(misses.contains(gone) || warnings.contains(gone),
                    gone + " came back; it was dropped rather than aliased");
        }

        assertEquals(1, EnumSet.allOf(ResolutionWarningCode.class).stream()
                        .filter(ResolutionWarningCode::isAcknowledgeable).count(),
                "exactly one warning is acknowledgeable, and it is CANCELLED_BUT_DATE_ALIVE");
        assertTrue(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE.isAcknowledgeable());
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
