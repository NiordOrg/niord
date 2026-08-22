package org.niord.core.publication.series.resolve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The canonical membership rule, against the captured corpus.
 *
 * No database and no Quarkus, by design: this runs in about half a second, and
 * it is the one rule in the system that must not be wrong.
 *
 * What these assertions can and cannot reach. The predicate decides a candidate
 * pool; the pool itself comes from SQL, which is B1.2's work. So the tests here
 * assert that the rule does not WRONGLY EXCLUDE any recorded member, and pin the
 * specific ways it has been seen to go wrong. Asserting that the resolver
 * reproduces a recorded set from the whole corpus needs that corpus, and is
 * B1.2's to make -- this task is explicitly not signed off without it.
 */
public class MembershipPredicateTest {

    private static final String DIR = "/fixtures/publications/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------ helpers

    private static JsonNode fixture(String name) throws Exception {
        try (InputStream in = MembershipPredicateTest.class.getResourceAsStream(DIR + name + ".json")) {
            assertNotNull(in, "fixture " + name + " is missing");
            return MAPPER.readTree(in);
        }
    }

    private static Date date(JsonNode node) {
        return node == null || node.isNull() ? null : new Date(node.asLong());
    }

    private static MessageFacts facts(JsonNode m) {
        return new MessageFacts(
                m.get("uid").asText(),
                date(m.get("publishDateFrom")),
                date(m.get("publishDateTo")),
                m.path("status").isNull() ? null : Status.valueOf(m.get("status").asText()),
                m.path("type").isNull() ? null : Type.valueOf(m.get("type").asText()),
                m.path("seriesId").asText(null));
    }

    private static List<MessageFacts> members(JsonNode fixture) {
        List<MessageFacts> out = new ArrayList<>();
        for (JsonNode m : fixture.path("members")) {
            out.add(facts(m));
        }
        return out;
    }

    /**
     * The issue's cut-off.
     *
     * NOT the tag's own created stamp -- that is when the issue OPENED, not when
     * it closed. Measured: every one of nm-w45-2018's 23 members was published
     * after its tag was created, and the members of each 2026 week run from just
     * after that week's stamp to just before the NEXT week's. The stamp is a
     * recovery source only through chaining: issue N's cut-off is issue N+1's
     * tag creation, which is what "exact on 452 of 496 CHAINED pairs" means.
     *
     * With no successor fixture to chain from, the last member's stamp is used.
     * The upper bound is closed, so that member sits exactly on the cut-off and
     * is included -- which exercises RI-2's closed upper bound on real data
     * rather than only in the synthetic case.
     */
    private static Date cutoffOf(JsonNode fixture) {
        long last = Long.MIN_VALUE;
        for (JsonNode m : fixture.path("members")) {
            if (!m.path("publishDateFrom").isNull()) {
                last = Math.max(last, m.get("publishDateFrom").asLong());
            }
        }
        if (last == Long.MIN_VALUE) {
            throw new IllegalStateException("fixture has no member with a publishDateFrom to derive a cut-off from");
        }
        return new Date(last);
    }

    private static ResolvedCriteria weekly(boolean aliveAtCutoff) {
        return new ResolvedCriteria(TimeRelation.PUBLISHED_IN_INTERVAL, Set.of(), Set.of(), aliveAtCutoff);
    }

    // ------------------------------------------------------------ RI-1, derived

    @Test
    public void publicStatusesAreDerivedAndNonEmpty() {
        assertFalse(MembershipPredicate.PUBLIC_STATUSES.isEmpty(),
                "an empty public-status set silently empties every issue in the system");
        assertEquals(Set.of(Status.PUBLISHED, Status.EXPIRED, Status.CANCELLED),
                new LinkedHashSet<>(MembershipPredicate.PUBLIC_STATUSES),
                "the derived set no longer matches Status.isPublic()");

        for (Status s : Status.values()) {
            assertEquals(s.isPublic(), MembershipPredicate.PUBLIC_STATUSES.contains(s),
                    s + " disagrees with Status.isPublic()");
        }
    }

    /**
     * The status conjunct that empties history. Most of the historical corpus is
     * EXPIRED or CANCELLED, so a rule admitting only PUBLISHED returns almost
     * nothing -- and returns it quietly.
     */
    @Test
    public void expiredAndCancelledMembersAreNotDropped() throws Exception {
        JsonNode f = fixture("nm-w45-2018");
        List<MessageFacts> pool = members(f);

        long historical = pool.stream()
                .filter(m -> m.status() == Status.EXPIRED || m.status() == Status.CANCELLED)
                .count();
        assertEquals(pool.size(), historical, "fixture no longer holds only EXPIRED/CANCELLED members");

        Set<String> kept = MembershipPredicate.members(pool, weekly(false),
                new Interval(null, cutoffOf(f)));
        assertEquals(pool.size(), kept.size(),
                "a PUBLISHED-only conjunct would empty this issue; " + kept.size() + " of " + pool.size() + " survived");
    }

    // ------------------------------------------------- positive controls

    @Test
    public void positiveControlsKeepEveryRecordedMember() throws Exception {
        assertKeepsEveryMember("nm-pt-w01-2026", 123);
        assertKeepsEveryMember("nm-w01-2026", 2);
    }

    private void assertKeepsEveryMember(String name, int expected) throws Exception {
        JsonNode f = fixture(name);
        List<MessageFacts> pool = members(f);
        assertEquals(expected, pool.size(), name + " no longer holds " + expected + " members");

        Set<String> kept = MembershipPredicate.members(pool, weekly(false),
                new Interval(null, cutoffOf(f)));
        assertEquals(expected, kept.size(),
                name + ": the rule excluded " + (expected - kept.size()) + " recorded member(s)");
    }

    // -------------------------------------- RI-4, the NULL-safety collapse

    /**
     * The sharpest regression in the corpus, pinned from BOTH sides: the correct
     * NULL-safe clause keeps all 165 members, and the NULL-unsafe form -- which
     * compares publishDateTo as though null were a date -- keeps 42.
     *
     * 42 is measured here, not carried over. The figure recorded during analysis
     * was 47, computed against a cut-off this fixture does not store; with the
     * recovered cut-off the survivors are 42. The mechanism is what matters and
     * it is unambiguous: 123 of the 165 members have a NULL publishDateTo, and
     * the unsafe form discards every one of them.
     */
    @Test
    public void nullPublishDateToIsAliveNotExpired() throws Exception {
        JsonNode f = fixture("nm-pt-w28-2026");
        List<MessageFacts> pool = members(f);
        Date cutoff = cutoffOf(f);
        assertEquals(165, pool.size(), "P&T uge 28/2026 no longer holds 165 members");

        Set<String> safe = MembershipPredicate.members(pool, weekly(true), new Interval(null, cutoff));
        assertEquals(165, safe.size(), "the NULL-safe alive clause dropped members it must keep");

        // The bug, written out so the regression is pinned from the other side too.
        long unsafe = pool.stream()
                .filter(m -> m.publishDateTo() != null && !m.publishDateTo().before(cutoff))
                .count();
        assertEquals(42, unsafe, "the NULL-unsafe form no longer collapses the way it did");
        assertEquals(0, pool.stream()
                .filter(m -> m.publishDateTo() != null && m.publishDateTo().before(cutoff)).count(),
                "no recorded member should have expired before its own issue closed");

        long nullTo = pool.stream().filter(m -> m.publishDateTo() == null).count();
        assertEquals(123, nullTo, "the fixture no longer carries 123 still-open members");
        assertTrue(safe.size() > unsafe * 3,
                "the collapse should be dramatic: " + safe.size() + " safe against " + unsafe + " unsafe");
    }

    // ------------------------------------------- RI-3, NULL publishDateFrom

    @Test
    public void nullPublishDateFromIsExcludedAndReported() throws Exception {
        JsonNode f = fixture("nm-780-18");
        assertFalse(f.path("absent").asBoolean(false), "NM-780-18 should exist");

        MessageFacts m = facts(f.get("facts"));
        assertTrue(m.publishDateFrom() == null, "NM-780-18 no longer has a null publishDateFrom");

        // It must be excluded from every issue it could otherwise leak into, and
        // for a reason that can be surfaced -- not dropped where nobody sees it.
        for (String issue : List.of("nm-w23-2026", "nm-w24-2026", "nm-w25-2026", "nm-w26-2026",
                "nm-w27-2026", "nm-w28-2026", "nm-w01-2026", "nm-w45-2018")) {
            JsonNode iss = fixture(issue);
            MemberDecision d = MembershipPredicate.decide(m, weekly(false),
                    new Interval(null, cutoffOf(iss)));
            assertFalse(d.member(), "NM-780-18 leaked into " + issue);
            assertEquals(MembershipReason.NO_PUBLISH_DATE, d.reason(),
                    "NM-780-18 was excluded from " + issue + " for the wrong reason");
            assertTrue(d.isReportableOmission(), "the omission is not reportable, so nobody would see it");
        }

        Map<String, MemberDecision> decided = MembershipPredicate.decideAll(
                List.of(m), weekly(false), new Interval(null, new Date(1_700_000_000_000L)));
        assertEquals(Set.of(m.uid()), MembershipPredicate.reportableOmissions(decided));
    }

    // --------------------------------------------- RI-2, the boundary rule

    /**
     * A message stamped exactly on a shared cut-off belongs to the EARLIER issue
     * and to exactly one. No production message can settle this -- none of the
     * 10,413 sits on any of the 992 stamps -- so the case is authored.
     */
    @Test
    public void aMessageOnTheCutoffLandsInTheEarlierIssueOnly() throws Exception {
        JsonNode f = fixture("synthetic-boundary-pair");
        assertTrue(f.path("synthetic").asBoolean(false), "this fixture must declare itself synthetic");

        JsonNode cutoffs = f.get("cutoffs");
        Date previous = new Date(cutoffs.get("previousIssueCutoff").asLong());
        Date shared = new Date(cutoffs.get("sharedCutoff").asLong());
        Date next = new Date(cutoffs.get("nextIssueCutoff").asLong());

        List<MessageFacts> pool = members(f);
        Set<String> earlier = MembershipPredicate.members(pool, weekly(false), new Interval(previous, shared));
        Set<String> later = MembershipPredicate.members(pool, weekly(false), new Interval(shared, next));

        Set<String> expectedEarlier = expected(f, "earlierIssue");
        Set<String> expectedLater = expected(f, "laterIssue");

        assertEquals(expectedEarlier, earlier, "the earlier issue's membership is wrong at the boundary");
        assertEquals(expectedLater, later, "the later issue's membership is wrong at the boundary");

        Set<String> both = new LinkedHashSet<>(earlier);
        both.retainAll(later);
        assertTrue(both.isEmpty(),
                "a message landed in BOTH issues: " + both + ". That is what reusing an inclusive-inclusive "
                        + "between() produces, and it double-counts every shared cut-off in the system.");
    }

    private static Set<String> expected(JsonNode f, String key) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode n : f.get("expected").get(key)) {
            out.add(n.asText());
        }
        return out;
    }

    // ------------------------------------------------------------- RI-7

    /**
     * IN_FORCE_AT_CUTOFF has no lower bound and never consults the previous
     * issue. Applying a chained interval to the 2027 firing-areas issue would
     * leave it holding one message instead of thirty-two.
     */
    @Test
    public void inForceAtCutoffIgnoresAnyLowerBound() throws Exception {
        JsonNode f = fixture("skydeomraader-2027");
        List<MessageFacts> pool = members(f);
        assertEquals(32, pool.size(), "firing areas 2027 no longer holds 32 members");

        Date cutoff = new Date(pool.stream()
                .mapToLong(m -> m.publishDateFrom().getTime()).max().orElseThrow() + 1000L);

        ResolvedCriteria inForce = new ResolvedCriteria(TimeRelation.IN_FORCE_AT_CUTOFF, Set.of(), Set.of(), false);

        // A lower bound tight enough to exclude almost everything, to prove it is unused.
        Date lateLowerBound = new Date(cutoff.getTime() - 1000L);
        Set<String> withBound = MembershipPredicate.members(pool, inForce, new Interval(lateLowerBound, cutoff));
        Set<String> without = MembershipPredicate.members(pool, inForce, Interval.upTo(cutoff));

        assertEquals(32, without.size(), "IN_FORCE_AT_CUTOFF lost members with no lower bound applied");
        assertEquals(without, withBound,
                "IN_FORCE_AT_CUTOFF honoured a lower bound. Under a chained interval this issue would hold "
                        + withBound.size() + " message(s) instead of 32.");

        // And the same criteria under the other relation DOES chain -- so the test
        // is not passing merely because the bound was ineffective.
        ResolvedCriteria chained = new ResolvedCriteria(TimeRelation.PUBLISHED_IN_INTERVAL, Set.of(), Set.of(), false);
        Set<String> chainedMembers = MembershipPredicate.members(pool, chained, new Interval(lateLowerBound, cutoff));
        assertTrue(chainedMembers.size() < 32,
                "the lower bound had no effect even under PUBLISHED_IN_INTERVAL, so RI-7 proved nothing");
    }

    // ------------------------------------------- the 31-of-32 overlap pair

    /**
     * Member equality only. Whether these issues tile or overlap is B1.8's
     * assertion -- at this point there is no gap detection whose absence could
     * be checked.
     */
    @Test
    public void consecutiveInForceIssuesOverlapRatherThanTile() throws Exception {
        Set<String> a = members(fixture("skydeomraader-2026")).stream()
                .map(MessageFacts::uid).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> b = members(fixture("skydeomraader-2027")).stream()
                .map(MessageFacts::uid).collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(32, a.size());
        assertEquals(32, b.size());

        Set<String> shared = new LinkedHashSet<>(a);
        shared.retainAll(b);
        assertEquals(31, shared.size(),
                "the 2026 and 2027 issues share " + shared.size() + " members, not 31");
    }

    // ------------------------------------- rolled-back publish: a gap, not a member

    /**
     * Three instances, not one: the class recurs at roughly one to two a year and
     * is not a 2026 anomaly. A number-line gap is legitimate -- the message does
     * not exist, so it cannot be a member of anything.
     */
    @Test
    public void rolledBackPublishesAreAbsentEntirely() throws Exception {
        for (String name : List.of("nm-473-26", "nm-962-25", "nm-1046-25")) {
            JsonNode f = fixture(name);
            assertTrue(f.path("absent").asBoolean(false),
                    name + " is recorded as existing; the rolled-back-publish class means it does not");
            assertFalse(f.has("facts"), name + " carries facts for a message that does not exist");
        }
    }

    // ------------------------------------------------- RI-10, the set algebra

    @Test
    public void curationOverridesTheQueryAndExcludeWins() throws Exception {
        JsonNode f = fixture("nm-w01-2026");
        List<MessageFacts> pool = members(f);
        Map<String, MemberDecision> decided =
                MembershipPredicate.decideAll(pool, weekly(false), new Interval(null, cutoffOf(f)));

        String first = pool.get(0).uid();
        String added = "00000000-0000-4000-8000-0000000000ff";

        Set<String> withExclude = MembershipPredicate.applyOverrides(decided, Set.of(), Set.of(first));
        assertFalse(withExclude.contains(first), "a manual exclude did not remove the member");

        Set<String> withInclude = MembershipPredicate.applyOverrides(decided, Set.of(added), Set.of());
        assertTrue(withInclude.contains(added), "a manual include did not add the member");

        // An exclude is the later, more specific act.
        Set<String> both = MembershipPredicate.applyOverrides(decided, Set.of(added), Set.of(added));
        assertFalse(both.contains(added), "include won over exclude; exclude must win");

        assertEquals(MembershipReason.MANUAL_EXCLUDE,
                MembershipPredicate.reasonAfterOverrides(first, decided, Set.of(), Set.of(first)));
        assertEquals(MembershipReason.MANUAL_INCLUDE,
                MembershipPredicate.reasonAfterOverrides(added, decided, Set.of(added), Set.of()));
    }

    // ------------------------------------------------ release-moment precision

    /**
     * The rule compares instants, never days. Day-snapping was a real defect in
     * the legacy path, and it is invisible in any fixture whose members sit hours
     * from the boundary -- so this asserts on a boundary built to the second.
     */
    @Test
    public void comparisonIsToTheInstantNotTheDay() throws Exception {
        JsonNode f = fixture("nm-466-26");
        MessageFacts m = facts(f.get("facts"));
        assertNotNull(m.publishDateFrom());

        long stamp = m.publishDateFrom().getTime();

        MemberDecision justInside = MembershipPredicate.decide(m, weekly(false), Interval.upTo(new Date(stamp)));
        assertTrue(justInside.member(), "a message stamped exactly on the cut-off must be inside it");

        MemberDecision oneSecondOut = MembershipPredicate.decide(m, weekly(false),
                Interval.upTo(new Date(stamp - 1000L)));
        assertFalse(oneSecondOut.member(), "one second past the cut-off must fall outside");
        assertEquals(MembershipReason.AFTER_CUTOFF, oneSecondOut.reason());

        // Day-snapping would put both on the same side.
        long dayStart = stamp - (stamp % 86_400_000L);
        MemberDecision snapped = MembershipPredicate.decide(m, weekly(false), Interval.upTo(new Date(dayStart)));
        assertFalse(snapped.member(),
                "the message survived a cut-off snapped to the start of its day, which is day-snapping");
    }

    // ------------------------------------------- the cut-off recovery source

    /**
     * Issue N's cut-off is issue N+1's tag creation. This is the property the
     * importer leans on to recover cut-offs that were never stored, so it is
     * asserted rather than assumed.
     *
     * It also pins the correction that produced it: the tag's own created stamp
     * is when the issue OPENED. Every one of nm-w45-2018's 23 members was
     * published after its tag was created, so treating that stamp as the cut-off
     * excludes the entire issue -- silently, since AFTER_CUTOFF is a perfectly
     * ordinary reason.
     */
    @Test
    public void anIssueClosesWhenTheNextOneOpens() throws Exception {
        int pairs = 0;
        for (String series : List.of("nm-w", "nm-pt-w")) {
            for (int week = 23; week < 28; week++) {
                JsonNode current = fixture(series + week + "-2026");
                JsonNode next = fixture(series + (week + 1) + "-2026");

                long lastMember = cutoffOf(current).getTime();
                long nextOpens = next.get("tagCreated").asLong();

                assertTrue(lastMember <= nextOpens,
                        series + week + ": its last member is stamped after the next issue opened, so the "
                                + "issues overlap and the recovered cut-off would be wrong");

                // And every member of the current issue is inside the recovered window.
                Set<String> kept = MembershipPredicate.members(members(current), weekly(false),
                        new Interval(null, new Date(nextOpens)));
                assertEquals(members(current).size(), kept.size(),
                        series + week + ": the recovered cut-off excluded members of its own issue");
                pairs++;
            }
        }
        assertEquals(10, pairs, "expected 10 chained pairs across the two weekly series");
    }

    /**
     * The tag's created stamp is NOT the cut-off, stated as a test so the mistake
     * cannot be made twice.
     */
    @Test
    public void theTagCreationStampIsTheOpeningNotTheCutoff() throws Exception {
        JsonNode f = fixture("nm-w45-2018");
        long opened = f.get("tagCreated").asLong();

        long after = members(f).stream()
                .filter(m -> m.publishDateFrom().getTime() > opened)
                .count();
        assertEquals(members(f).size(), after,
                "every member should be published after the tag was created; if this changes, the cut-off "
                        + "derivation in cutoffOf() needs revisiting");

        Set<String> usingStampAsCutoff = MembershipPredicate.members(members(f), weekly(false),
                Interval.upTo(new Date(opened)));
        assertTrue(usingStampAsCutoff.isEmpty(),
                "using the creation stamp as the cut-off should empty this issue entirely -- that it does is "
                        + "exactly why it must not be used that way");
    }

    // ------------------------------------------------ M-1, keyed on uid

    /**
     * Member comparison is keyed on uid, never shortId.
     *
     * Real data, not an authored case -- an earlier attempt to reproduce this
     * concluded it did not occur, because it queried the corpus with
     * `messageSeries=`, which the backend silently drops. Asked correctly, the
     * firing-areas series holds 357 messages under 331 distinct shortIds, and
     * `FA/EK-R-19 2017` alone exists twice under two different uids.
     *
     * The consequence is what this pins: the two 2017 editions differ by
     * FIFTEEN members, and a shortId-keyed comparison reports ONE.
     */
    @Test
    public void memberComparisonIsKeyedOnUidNotShortId() throws Exception {
        List<MessageFacts> ed1 = members(fixture("skydeomraader-2017-ed1"));
        List<MessageFacts> ed2 = members(fixture("skydeomraader-2017-ed2"));

        JsonNode raw1 = fixture("skydeomraader-2017-ed1");
        JsonNode raw2 = fixture("skydeomraader-2017-ed2");

        Set<String> uidsA = ed1.stream().map(MessageFacts::uid).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> uidsB = ed2.stream().map(MessageFacts::uid).collect(Collectors.toCollection(LinkedHashSet::new));
        int uidDiff = symmetricDifference(uidsA, uidsB);

        Set<String> shortA = shortIds(raw1);
        Set<String> shortB = shortIds(raw2);
        int shortIdDiff = symmetricDifference(shortA, shortB);

        assertEquals(15, uidDiff, "the 2017 editions no longer differ by 15 members when keyed on uid");
        assertEquals(1, shortIdDiff,
                "the shortId-keyed comparison no longer undercounts; if this changed, re-check the fixtures");

        assertTrue(uidDiff > shortIdDiff,
                "shortId keying is supposed to UNDERCOUNT here -- that is the whole hazard");
        assertEquals(14, uidDiff - shortIdDiff,
                "a shortId-keyed diff hides 14 of the 15 real differences between these two editions");
    }

    /** shortId is captured for readability only; this is the one place it is read. */
    private static Set<String> shortIds(JsonNode fixture) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode m : fixture.path("members")) {
            out.add(m.path("shortId").asText(null));
        }
        return out;
    }

    private static int symmetricDifference(Set<String> a, Set<String> b) {
        int n = 0;
        for (String x : a) if (!b.contains(x)) n++;
        for (String x : b) if (!a.contains(x)) n++;
        return n;
    }
}
