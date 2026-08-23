package org.niord.core.publication.series.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5.4a2. The snapshot header is derived per issue and never copied from the series.
 *
 * The population figures are read from the captured estate rather than asserted
 * as literals alone, so that a change in the capture shows up here instead of
 * silently invalidating the numbers this task was justified by.
 *
 * No database and no Quarkus.
 */
public class IssueSnapshotDeriverTest {

    private static final String PUBLICATIONS = "/fixtures/legacy-estate/publications.json";

    private static JsonNode estate() throws Exception {
        try (InputStream in = IssueSnapshotDeriverTest.class.getResourceAsStream(PUBLICATIONS)) {
            assertNotNull(in, PUBLICATIONS + " is missing");
            return new ObjectMapper().readTree(in);
        }
    }

    /** The stored filter of one captured publication, NULL-safe. */
    private static String filterOf(JsonNode publication) {
        JsonNode f = publication.get("messageTagFilter");
        return f == null || f.isNull() ? null : f.asText();
    }

    private static Publication legacy(String filter) {
        Publication p = new Publication();
        p.setMessageTagFilter(filter);
        return p;
    }

    private static PublicationIssue issueFor(String filter, PublicationSeries series) {
        PublicationIssue issue = new PublicationIssue();
        IssueSnapshotDeriver.derive(issue, legacy(filter), series, new Date(0L));
        return issue;
    }

    // ------------------------------------------------- the header is written

    /**
     * The defect this task closes: as originally specified the importer left
     * every one of these NULL on ~1,077 issues.
     */
    @Test
    public void everyImportedIssueCarriesATimeRelationAndAnAliveFlag() throws Exception {
        List<String> missing = new ArrayList<>();

        for (JsonNode p : estate()) {
            PublicationIssue issue = issueFor(filterOf(p), null);
            if (issue.getSnapshotTimeRelation() == null || issue.getSnapshotAliveAtCutoff() == null) {
                missing.add(p.get("publicationId").asText());
            }
        }

        assertTrue(missing.isEmpty(),
                "NULL snapshot columns are the state DATA-MODEL section 8 reserves for NO_MEMBERSHIP; "
                        + "leaving them null on an imported issue makes it indistinguishable from one that "
                        + "genuinely has no membership. Offenders: " + missing.size());
    }

    /** frozenAt is the run's instant, so one import is one identifiable event. */
    @Test
    public void theFreezeInstantIsRecorded() {
        Date at = new Date(1_700_000_000_000L);
        PublicationIssue issue = new PublicationIssue();
        IssueSnapshotDeriver.derive(issue, legacy(null), null, at);
        assertEquals(at, issue.getSnapshotFrozenAt());
    }

    // ----------------------------------------------------- the derivation table

    /**
     * The four shapes map as verified against all 1,077 captured publications,
     * with zero unmapped.
     */
    @Test
    public void theFourShapesMapAsVerifiedOverTheWholeEstate() throws Exception {
        Map<String, Integer> byOutcome = new LinkedHashMap<>();

        for (JsonNode p : estate()) {
            PublicationIssue issue = issueFor(filterOf(p), null);
            String key = issue.getSnapshotTimeRelation() + "/" + issue.getSnapshotAliveAtCutoff();
            byOutcome.merge(key, 1, Integer::sum);
        }

        assertEquals(501 + 29, byOutcome.getOrDefault("IN_FORCE_AT_CUTOFF/true", 0),
                "type-and-status (501) and status (29) are the in-force regimes");
        assertEquals(387, byOutcome.getOrDefault("PUBLISHED_IN_INTERVAL/true", 0),
                "the phase era");
        assertEquals(160, byOutcome.getOrDefault("PUBLISHED_IN_INTERVAL/false", 0),
                "the blank era: 159 NULL plus the one empty string");
        assertEquals(1077, byOutcome.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(3, byOutcome.size(), "a fourth outcome means a shape mapped somewhere new");
    }

    /**
     * A fifth shape fails the import loudly rather than defaulting.
     *
     * Defaulting is the specific hazard: a shape nobody has seen would land in
     * whichever bucket the last else-branch names, and 1,077 rows would import
     * looking correct.
     */
    @Test
    public void anUnknownShapeFailsRatherThanDefaulting() {
        assertThrows(LegacyFilterTranslator.UnknownLegacyFilterException.class,
                () -> issueFor("msg.status == Status.DRAFT", null));
    }

    // ------------------------------------- the assertion that catches the bug

    /**
     * The blank-era weekly EfS issues carry false while their series carries true.
     *
     * This is the assertion the review said would have caught the defect, so it
     * is written the way the defect would have to pass: the series handed in says
     * IN_FORCE_AT_CUTOFF and aliveAtCutoff = true, exactly as the real series row
     * does, and every one of these issues must still come out false.
     */
    @Test
    public void theBlankEraWeeklyIssuesAreFalseWhileTheirSeriesIsTrue() throws Exception {
        PublicationSeries series = new PublicationSeries();
        series.setTimeRelation(TimeRelation.IN_FORCE_AT_CUTOFF);
        series.setAliveAtCutoff(true);

        int blankWeekly = 0;
        List<String> leaked = new ArrayList<>();

        for (JsonNode p : estate()) {
            String filter = filterOf(p);
            boolean blank = filter == null || filter.isBlank();
            JsonNode periodical = p.get("periodicalType");
            boolean weekly = periodical != null && !periodical.isNull()
                    && "WEEKLY".equals(periodical.asText());
            if (!blank || !weekly) {
                continue;
            }
            blankWeekly++;

            PublicationIssue issue = issueFor(filter, series);
            if (!Boolean.FALSE.equals(issue.getSnapshotAliveAtCutoff())
                    || !TimeRelation.PUBLISHED_IN_INTERVAL.name().equals(issue.getSnapshotTimeRelation())) {
                leaked.add(p.get("publicationId").asText());
            }
        }

        assertEquals(116, blankWeekly,
                "the blank-era weekly EfS population is 116 issues in the captured estate");
        assertTrue(leaked.isEmpty(),
                "these issues took their answer from the series instead of their own filter, which is "
                        + "the defect B5.4a2 exists to close: " + leaked);
    }

    /** 122 tag-carrying blank-era issues need false, not only the weekly ones. */
    @Test
    public void everyTagCarryingBlankEraIssueIsFalse() throws Exception {
        int tagCarryingBlank = 0;

        for (JsonNode p : estate()) {
            String filter = filterOf(p);
            boolean blank = filter == null || filter.isBlank();
            boolean hasTag = p.get("messageTag") != null && !p.get("messageTag").isNull();
            if (!blank || !hasTag) {
                continue;
            }
            tagCarryingBlank++;
            assertFalse(issueFor(filter, null).getSnapshotAliveAtCutoff(),
                    p.get("publicationId").asText() + " is a tag-carrying blank-era issue and must not "
                            + "acquire the alive conjunct");
        }

        assertEquals(122, tagCarryingBlank);
    }

    /**
     * The series is not consulted, proven by handing in the opposite of every
     * answer and getting the same result.
     *
     * A test that passes `null` for the series would prove only that the code
     * does not crash without one. This hands in a series whose every relevant
     * field contradicts the filter, so any read of it changes the outcome.
     */
    @Test
    public void theSeriesIsNeverConsultedForTheTimeRelationOrTheAliveFlag() throws Exception {
        PublicationSeries contradicting = new PublicationSeries();
        contradicting.setTimeRelation(TimeRelation.IN_FORCE_AT_CUTOFF);
        contradicting.setAliveAtCutoff(true);

        PublicationSeries alsoContradicting = new PublicationSeries();
        alsoContradicting.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        alsoContradicting.setAliveAtCutoff(false);

        for (JsonNode p : estate()) {
            String filter = filterOf(p);
            PublicationIssue a = issueFor(filter, contradicting);
            PublicationIssue b = issueFor(filter, alsoContradicting);
            PublicationIssue none = issueFor(filter, null);

            assertEquals(a.getSnapshotTimeRelation(), b.getSnapshotTimeRelation());
            assertEquals(a.getSnapshotAliveAtCutoff(), b.getSnapshotAliveAtCutoff());
            assertEquals(a.getSnapshotTimeRelation(), none.getSnapshotTimeRelation());
            assertEquals(a.getSnapshotAliveAtCutoff(), none.getSnapshotAliveAtCutoff());
        }
    }

    // --------------------------------------------------------- the rest of it

    /**
     * The criteria snapshot is a document, never null.
     *
     * A null criteria column means no query at all; an empty criteria list is a
     * legal query meaning everything in scope. The blank-era issues are the
     * second, and confusing the two turns a scoped issue into one that resolves
     * the whole corpus.
     */
    @Test
    public void theCriteriaSnapshotIsAnEmptyDocumentAndNotNullForTheBlankEra() {
        PublicationIssue issue = issueFor(null, null);
        assertNotNull(issue.getCriteriaSnapshot(),
                "null means no query at all, which is not what a blank filter meant");
        assertTrue(issue.getCriteriaSnapshot().getCriteria().isEmpty(),
                "the blank era narrowed on nothing; that is an empty query, not a missing one");
    }

    /** The P&T disjunction becomes a set-valued messageType node, status dropped. */
    @Test
    public void theTypeDisjunctionBecomesOneSetValuedNode() {
        PublicationIssue issue = issueFor(
                "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) "
                        + "&& msg.status == Status.PUBLISHED", null);

        assertEquals(1, issue.getCriteriaSnapshot().getCriteria().size());
        assertEquals(List.of("TEMPORARY_NOTICE", "PRELIMINARY_NOTICE"),
                issue.getCriteriaSnapshot().getCriteria().get(0).getValues());
    }

    /** No status value reaches the stored document, on any shape. */
    @Test
    public void noStatusConjunctIsStored() throws Exception {
        for (JsonNode p : estate()) {
            PublicationIssue issue = issueFor(filterOf(p), null);
            issue.getCriteriaSnapshot().getCriteria().forEach(node ->
                    node.getValues().forEach(v -> assertFalse(v.contains("PUBLISHED"),
                            "status is a resolver invariant (RI-1); storing it lets an edit weaken it")));
        }
    }

    /** No domain means no recorded scope, and null says that rather than "". */
    @Test
    public void anAbsentDomainRecordsNullScopeNotAnEmptyOne() {
        PublicationIssue issue = issueFor(null, null);
        assertNull(issue.getSnapshotSeriesIds(),
                "the empty string would claim the scope was computed and found to be nothing");
    }
}
