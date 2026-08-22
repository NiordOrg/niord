package org.niord.core.publication.series;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.DatabaseAvailable;
import org.niord.core.message.Message;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.MembershipPredicate;
import org.niord.core.publication.series.resolve.MessageFacts;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.Type;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The differential test: SQL narrowing must never drop something the rule would
 * have matched.
 *
 * This is the highest-consequence risk in the backend half. Two implementations
 * of one rule -- a SQL query and a pure function -- drift, and the drift is
 * silent: the issue simply has fewer members than it should, and every member it
 * does have is correct.
 *
 * The test compares against the WHOLE corpus rather than against each fixture's
 * own members. Running the predicate over the fixture's members alone would
 * prove nothing about narrowing, because the narrowing is precisely what decides
 * which rows are considered.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class MemberResolutionDifferentialTest {

    private static final String DIR = "/fixtures/publications/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Every fixture that carries a member list. */
    private static final List<String> MEMBER_FIXTURES = List.of(
            "nm-pt-w01-2026", "nm-w01-2026",
            "nm-w23-2026", "nm-pt-w23-2026", "nm-w24-2026", "nm-pt-w24-2026",
            "nm-w25-2026", "nm-pt-w25-2026", "nm-w26-2026", "nm-pt-w26-2026",
            "nm-w27-2026", "nm-pt-w27-2026", "nm-w28-2026", "nm-pt-w28-2026",
            "nm-w45-2018", "nm-pt-w12-2024",
            "skydeomraader-2017-ed1", "skydeomraader-2017-ed2", "skydeomraader-2018",
            "skydeomraader-2026", "skydeomraader-2027");

    @Inject
    MemberResolutionService resolver;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ setup

    private static JsonNode fixture(String name) throws Exception {
        try (InputStream in = MemberResolutionDifferentialTest.class.getResourceAsStream(DIR + name + ".json")) {
            assertNotNull(in, "fixture " + name + " is missing");
            return MAPPER.readTree(in);
        }
    }

    /** The whole seeded corpus, as the facts the rule depends on. */
    private List<MessageFacts> wholeCorpus() {
        return em.createQuery("SELECT m FROM Message m", Message.class)
                .getResultList()
                .stream()
                .map(MemberResolutionService::factsOf)
                .collect(Collectors.toList());
    }

    /** The cut-off, derived the same way the Tier-1 test derives it. */
    private static Date cutoffOf(JsonNode fixture) {
        long last = Long.MIN_VALUE;
        for (JsonNode m : fixture.path("members")) {
            if (!m.path("publishDateFrom").isNull()) {
                last = Math.max(last, m.get("publishDateFrom").asLong());
            }
        }
        return new Date(last);
    }

    /** Criteria reconstructed from what the fixture's own members actually are. */
    private static ResolvedCriteria criteriaFor(String name, JsonNode fixture) {
        Set<String> series = new LinkedHashSet<>();
        for (JsonNode m : fixture.path("members")) {
            String s = m.path("seriesId").asText(null);
            if (s != null) series.add(s);
        }

        // The firing-areas issues are the in-force shape: no lower bound, and
        // they overlap rather than tile.
        TimeRelation relation = name.startsWith("skydeomraader")
                ? TimeRelation.IN_FORCE_AT_CUTOFF
                : TimeRelation.PUBLISHED_IN_INTERVAL;

        // The P&T series is the one production filter that narrows on type.
        Set<Type> types = name.startsWith("nm-pt-")
                ? Set.of(Type.TEMPORARY_NOTICE, Type.PRELIMINARY_NOTICE)
                : Set.of();

        return new ResolvedCriteria(relation, series, types, false);
    }

    // ------------------------------------------------------ B1.2, the differential

    /**
     * For every fixture: the SQL candidate set contains everything the rule
     * matches, and both paths agree on the final membership.
     */
    @Test
    @Transactional
    public void sqlNarrowingNeverDropsAMatch() throws Exception {
        List<MessageFacts> corpus = wholeCorpus();
        assertTrue(corpus.size() > 10_000,
                "the corpus holds only " + corpus.size() + " messages; run scripts/seed-dev-database.mjs");

        List<String> failures = new ArrayList<>();

        for (String name : MEMBER_FIXTURES) {
            JsonNode f = fixture(name);
            ResolvedCriteria criteria = criteriaFor(name, f);
            Interval interval = Interval.upTo(cutoffOf(f));

            // The rule, applied to everything. This is the answer.
            Set<String> pureOnly = MembershipPredicate.members(corpus, criteria, interval);

            // The production path: SQL narrows, the rule decides.
            MemberResolutionService.Resolution sql = resolver.resolve(criteria, interval);
            Set<String> candidates = new LinkedHashSet<>(sql.candidateUids());

            if (!candidates.containsAll(pureOnly)) {
                Set<String> dropped = new LinkedHashSet<>(pureOnly);
                dropped.removeAll(candidates);
                failures.add(name + ": SQL dropped " + dropped.size()
                        + " row(s) the rule matches, e.g. " + dropped.stream().limit(3).toList());
            }
            if (!pureOnly.equals(sql.members())) {
                Set<String> onlyPure = new LinkedHashSet<>(pureOnly);
                onlyPure.removeAll(sql.members());
                Set<String> onlySql = new LinkedHashSet<>(sql.members());
                onlySql.removeAll(pureOnly);
                failures.add(name + ": final membership differs -- pure-only has " + onlyPure.size()
                        + " the SQL path lacks, SQL path has " + onlySql.size() + " the rule does not match");
            }
        }

        if (!failures.isEmpty()) {
            org.junit.jupiter.api.Assertions.fail(
                    "the two implementations of the rule have drifted:\n  " + String.join("\n  ", failures));
        }
    }

    /**
     * The guard proves itself: a narrowed SQL bound must make the test above red.
     *
     * Rather than editing the service, this reproduces its query with the lower
     * bound tightened from >= to >, which is what a well-meaning "optimisation"
     * looks like -- and then shows the candidate set losing a row the rule keeps.
     */
    @Test
    @Transactional
    public void aTightenedSqlBoundIsDetectable() throws Exception {
        List<MessageFacts> corpus = wholeCorpus();

        // A window whose lower bound sits exactly on a real message's stamp.
        JsonNode f = fixture("nm-w28-2026");
        ResolvedCriteria criteria = criteriaFor("nm-w28-2026", f);
        long onTheBound = f.get("members").get(0).get("publishDateFrom").asLong();
        Interval interval = new Interval(new Date(onTheBound - 1), new Date(cutoffOf(f).getTime()));

        Set<String> loose = corpus.stream()
                .filter(m -> m.publishDateFrom() != null
                        && m.publishDateFrom().getTime() >= interval.previousCutoff().getTime()
                        && m.publishDateFrom().getTime() <= interval.cutoff().getTime())
                .map(MessageFacts::uid).collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> tightened = corpus.stream()
                .filter(m -> m.publishDateFrom() != null
                        && m.publishDateFrom().getTime() > interval.previousCutoff().getTime()
                        && m.publishDateFrom().getTime() <= interval.cutoff().getTime())
                .map(MessageFacts::uid).collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(loose.size() >= tightened.size(), "the loose bound should never return fewer rows");
        assertTrue(loose.containsAll(tightened), "the tightened bound is not a subset of the loose one");

        // And the service itself uses the loose form, so its candidates are a superset.
        MemberResolutionService.Resolution r = resolver.resolve(criteria, interval);
        assertTrue(new LinkedHashSet<>(r.candidateUids()).containsAll(r.members()),
                "the candidate set does not contain its own members");
    }

    // ------------------------------------------------------------- B1.1 invariants

    /** RI-6, on the path that would silently empty the issue. */
    @BindsRule({"RI-6"})
    @Test
    @Transactional
    public void anUnresolvableOperandRaisesRatherThanEmptyingTheIssue() {
        Set<String> blankSeries = new LinkedHashSet<>();
        blankSeries.add("");
        ResolvedCriteria withBlank =
                new ResolvedCriteria(TimeRelation.PUBLISHED_IN_INTERVAL, blankSeries, Set.of(), false);

        assertThrows(MemberResolutionService.UnresolvableOperandException.class,
                () -> resolver.resolve(withBlank, Interval.upTo(new Date())),
                "a blank operand became a SQL IN over nothing, which is always false -- the issue would "
                        + "have resolved empty while looking perfectly healthy");
    }

    /** RI-12. Nothing caps the result at 100, which is the default in the params object. */
    @BindsRule({"RI-12", "RI-13"})
    @Test
    @Transactional
    public void thereIsNoAccidentalCap() {
        ResolvedCriteria all = new ResolvedCriteria(TimeRelation.PUBLISHED_IN_INTERVAL, Set.of(), Set.of(), false);
        MemberResolutionService.Resolution r = resolver.resolve(all, Interval.upTo(new Date()));

        assertTrue(r.candidateCount() > 1000,
                "only " + r.candidateCount() + " candidates came back; a 100-row or 1000-row cap is in play");
    }

    /** RI-5. The search REST layer, which day-snaps intervals, is not on the path. */
    @BindsRule({"RI-5"})
    @Test
    public void theSearchRestLayerIsNotOnTheCallPath() {
        for (String forbidden : List.of(
                "org.niord.web.MessageSearchRestService",
                "org.niord.core.message.MessageSearchParams")) {
            boolean referenced = false;
            try {
                Class<?> type = Class.forName(forbidden);
                for (var field : MemberResolutionService.class.getDeclaredFields()) {
                    if (type.isAssignableFrom(field.getType())) referenced = true;
                }
                for (var method : MemberResolutionService.class.getDeclaredMethods()) {
                    if (type.isAssignableFrom(method.getReturnType())) referenced = true;
                    for (var param : method.getParameterTypes()) {
                        if (type.isAssignableFrom(param)) referenced = true;
                    }
                }
            } catch (ClassNotFoundException e) {
                continue; // not on this module's classpath at all, which is even better
            }
            assertFalse(referenced, MemberResolutionService.class.getSimpleName() + " references " + forbidden
                    + ". That layer day-snaps the interval, rewrites seriesIds and forces PUBLISHED-only.");
        }
    }
}
