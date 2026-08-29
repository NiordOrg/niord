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

package org.niord.core.publication.series.legacy;

import org.niord.core.publication.series.TestOwnerDomain;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.BindsRule;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.PublicationSeries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pre-flight, and the trigger audit that has to be read before cutover.
 *
 * The pass reports rather than throws, because it is a checklist an admin runs
 * and reads. What must not happen is a violation going unnoticed, so the test
 * asserts the shape of the answer and that the report exists at all -- an absent
 * report is a failed pre-flight, not a clean one.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class CutoverPreflightTest {

    /** Committed whether or not it is empty; see theTriggerAuditIsCommittedEmptyOrNot. */
    private static final Path REPORT =
            Path.of("src", "test", "resources", "fixtures", "cutover-trigger-audit.md");

    @Inject
    CutoverPreflightService preflight;

    @Inject
    EntityManager em;

    /** The pass runs over the imported estate and answers in one shape. */
    @Test
    @Transactional
    public void thePreflightRunsAndReportsEveryCheck() {
        CutoverPreflightService.Preflight result = preflight.run();

        assertNotNull(result.violations());
        assertNotNull(result.triggerAudit());
        assertNotNull(result.counts());

        for (String key : List.of("importedIssues", "seriesWithACurrentIssue", "idCollisions",
                "triggersNamingAWeeklyTag", "duplicateMemberships", "duplicateOverrides")) {
            assertTrue(result.counts().containsKey(key),
                    "the pre-flight must report " + key + "; a check that runs and says nothing is "
                            + "indistinguishable from one that did not run");
        }
    }

    /**
     * The per-series sheet: the diff's verdict, the missing periods, the kind.
     *
     * Steps 4 and 5 of the rehearsal read these, and before they were here they
     * were answerable only by calling two other endpoints and counting rows in a
     * third. A checklist ticked from three moments can be ticked against three
     * different states of the estate, which is precisely what a cutover window
     * cannot afford.
     */
    @Test
    @Transactional
    public void thePreflightDescribesEverySeries() {
        CutoverPreflightService.Preflight result = preflight.run();

        assertNotNull(result.series());
        for (String key : List.of("series", "uncoveredPeriods")) {
            assertTrue(result.counts().containsKey(key),
                    "the pre-flight must report " + key);
        }
        // Every kind is named whether or not the estate has one: an absent kind
        // and a kind with no series read alike on a sheet, and step 5 is ticked by
        // comparing the three numbers against expected ones.
        for (org.niord.core.publication.series.SeriesKind kind
                : org.niord.core.publication.series.SeriesKind.values()) {
            assertTrue(result.counts().containsKey("seriesOfKind" + kind.name()),
                    "step 5 compares SCHEDULED / UNSCHEDULED / ONE_OFF against expected counts, so "
                            + "every kind is reported even at zero: " + result.counts().keySet());
        }

        result.series().forEach((seriesId, row) -> {
            assertEquals(seriesId, row.seriesId());
            assertNotNull(row.status(), "a row with no status cannot be judged");
            assertNotNull(row.kind());
            assertTrue(row.consecutiveGreen() >= 0);
            // Absent, not zero, where gap detection did not run. A closed gate is
            // the absence of a finding; "0 gaps" claims something nobody checked.
            if (!row.gapDetectionEnabled()) {
                assertNull(row.gapCount(),
                        "'" + seriesId + "' has gap detection off and still reported a count");
                assertNotNull(row.gapReasonCode(),
                        "'" + seriesId + "' reports no gaps and does not say why");
            }
        });
    }

    /**
     * Neither a gap nor an unproven series makes the pre-flight dirty.
     *
     * A gap is a fact about an archive that predates this system -- the estate has
     * had them since 2017 -- and readiness is the FLIP's own precondition,
     * refused at the flip with its own code. Folding either into `clear` would
     * stop the pre-flight ever passing on an estate in exactly the state
     * everybody expects, and a check that can never come back clean is a check
     * people learn to ignore.
     */
    @Test
    @Transactional
    public void gapsAndReadinessAreReportedButDoNotMakeThePreflightDirty() {
        CutoverPreflightService.Preflight result = preflight.run();

        for (CutoverPreflightService.Violation v : result.violations()) {
            assertFalse(v.code().contains("GAP") || v.code().contains("READINESS"),
                    "the pre-flight raised " + v.code() + " as a violation; gaps and readiness are "
                            + "reported per series and judged elsewhere");
        }
    }

    /**
     * I-18 is asserted across every imported issue, and the id space does not
     * collide.
     *
     * Both are cheap to state and one-way to get wrong: after the flip a second
     * current issue is serving the public, and a colliding id means one citation
     * resolves to whichever document the query found first.
     */
    @BindsRule({"I-18"})
    @Test
    @Transactional
    public void theEstateHasOneCurrentIssuePerSeriesAndNoCollidingIds() {
        CutoverPreflightService.Preflight result = preflight.run();

        List<CutoverPreflightService.Violation> fatal = result.violations().stream()
                .filter(v -> v.code().startsWith("I18_") || v.code().startsWith("X1_"))
                .toList();

        assertTrue(fatal.isEmpty(),
                "the imported estate must not carry two current issues on one series, nor a colliding "
                        + "publicId: " + fatal);
    }

    /**
     * The trigger audit is emitted as a committed file, empty or not.
     *
     * An absent report is a failed pre-flight rather than a clean one: "we found
     * nothing" and "nobody looked" are indistinguishable afterwards, and the
     * failure being guarded against is a mailing that silently stops going out.
     */
    @Test
    @Transactional
    public void theTriggerAuditIsCommittedEmptyOrNot() throws Exception {
        assertTrue(Files.exists(REPORT),
                REPORT + " must be committed even when it lists nothing -- otherwise 'we found no "
                        + "triggers' and 'nobody ran the audit' look identical later");

        String report = Files.readString(REPORT);
        assertFalse(report.isBlank(), "the report must say what was looked for, not merely exist");
        assertTrue(report.contains("nm-w"),
                "the report must name the tag shape it searched for, so a reader can judge whether "
                        + "the search was the right one");
    }

    /**
     * The audit reads messageFilter, not only messageQuery.
     *
     * This is the hole the live estate exposed: TWELVE of the fifteen triggers on
     * niord.t-dma.dk carry no messageQuery at all and put their logic in
     * messageFilter. The first version of this audit scanned messageQuery alone
     * and reported a clean result having read a fifth of the triggers -- silence
     * that reads as success, which is exactly what the committed report exists to
     * prevent.
     *
     * The expressions below are the real ones, copied from that environment.
     */
    @Test
    public void theAuditReadsEveryFieldATriggerCanExpressItselfIn() {
        // Real messageFilter expressions from the live estate. None names a tag,
        // and all twelve would have been invisible to a messageQuery-only scan.
        for (String live : List.of(
                "msg.messageSeries.seriesId == 'dma-nw-local' && msg.type == 'LOCAL_WARNING'",
                "msg.promulgation('navtex').promulgate && msg.promulgation('navtex').useTransmitter('Baltico')",
                "msg.messageSeries.seriesId == 'ako-nw' && msg.type == 'COASTAL_WARNING'")) {
            assertFalse(namesAWeeklyTag(live), "no live trigger names a weekly tag: " + live);
        }

        // A tag inside a script expression is quoted, not a query parameter --
        // so anchoring the pattern on "tag=" would miss every one of these.
        for (String wouldBreakAtC8 : List.of(
                "msg.tags.contains('nm-w27-2026')",
                "msg.tags.any(t -> t.name == \"nm-pt-w51-2017\")",
                "tag=nm-w01-2025",
                "status=PUBLISHED&tag=nm-pt-w12-2018")) {
            assertTrue(namesAWeeklyTag(wouldBreakAtC8),
                    "this stops matching at C8 and must be reported: " + wouldBreakAtC8);
        }

        // And the near-misses that must NOT be reported.
        for (String unrelated : List.of(
                "tag=general-notices", "publication=abc-123", "messageSeries=dma-nm",
                "tag=nm-almanac-2024-v1", "tag=firing-areas-2019-v1")) {
            assertFalse(namesAWeeklyTag(unrelated), "not a weekly tag: " + unrelated);
        }
    }

    /**
     * A series with nothing to compare is exempt ON THE SHEET, not only at the flip.
     *
     * Step 4 of the rehearsal is ticked from these rows, so a row reading "0
     * consecutive green of 0 runs, not ready" against a publication that is a
     * single uploaded document sends an operator looking for evidence that
     * cannot exist -- and, on the estate that produced this defect, made the
     * bulk flip refuse everything.
     *
     * Both halves are asserted together, because the useful claim is the
     * DIFFERENCE: the query-backed series beside it, which the diff really could
     * compare, must still read not ready with the same zero runs.
     */
    @Test
    @Transactional
    public void aSeriesWithNoMembershipToCompareIsReportedExempt() {
        PublicationSeries uploaded = seedSeries(ContentMode.UPLOADED_FILE, false);
        PublicationSeries generated = seedSeries(ContentMode.GENERATED_FROM_QUERY, true);
        em.flush();
        try {
            java.util.Map<String, CutoverPreflightService.SeriesRow> rows = preflight.run().series();

            CutoverPreflightService.SeriesRow u = rows.get(uploaded.getSeriesId());
            assertNotNull(u, "every series in the estate gets a row: " + rows.keySet());
            assertTrue(u.exempt(), "an uploaded document has no member list the diff could reproduce");
            assertTrue(u.meetsCutoverPrecondition());
            assertEquals(0, u.runs());

            CutoverPreflightService.SeriesRow g = rows.get(generated.getSeriesId());
            assertNotNull(g);
            assertFalse(g.exempt(), "a query-backed series with criteria CAN be compared");
            assertFalse(g.meetsCutoverPrecondition(),
                    "and with no comparisons it has no evidence, which is a different answer "
                            + "from having nothing to compare");
        } finally {
            em.remove(uploaded);
            em.remove(generated);
            em.flush();
        }
    }

    /**
     * The smallest series the pre-flight will describe, in the given shape.
     *
     * The series is removed again by the caller; the category is REUSED under a
     * fixed id instead, because it is what the series points at and a fresh one
     * per run would accumulate a row per test on a database every test shares.
     */
    private PublicationSeries seedSeries(ContentMode mode, boolean withCriteria) {
        org.niord.core.publication.PublicationCategory c = em.createQuery(
                        "SELECT c FROM PublicationCategory c WHERE c.categoryId = :id",
                        org.niord.core.publication.PublicationCategory.class)
                .setParameter("id", "preflight-probe")
                .getResultStream().findFirst().orElse(null);
        if (c == null) {
            c = new org.niord.core.publication.PublicationCategory();
            c.setCategoryId("preflight-probe");
            c.setPriority(900);
            c.setPublish(false);
            em.persist(c);
        }

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("preflight-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(org.niord.core.publication.series.SeriesStatus.ACTIVE);
        s.setKind(org.niord.core.publication.series.SeriesKind.ONE_OFF);
        s.setCadence(org.niord.core.publication.series.SeriesCadence.NONE);
        s.setContentMode(mode);
        s.setCutoffDefault(org.niord.core.publication.series.CutoffDefault.RELEASE_MOMENT);
        s.setReleaseMode(org.niord.core.publication.series.ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(org.niord.core.publication.series.NextIssueCreation.MANUAL);
        s.setNumberingScheme(org.niord.core.publication.series.NumberingScheme.NONE);
        s.setPublicAuthority(org.niord.core.publication.series.PublicAuthority.LEGACY);
        s.setCategory(c);
        // Every publication names the desk that owns it: the column is NOT NULL and
        // S-20a refuses a save without one, so a fixture that left it out no longer
        // describes a state the system can be in.
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        s.createDesc("da").setName("Pre-flight probe");
        if (withCriteria) {
            org.niord.core.publication.series.criteria.IssueCriteriaVo doc =
                    new org.niord.core.publication.series.criteria.IssueCriteriaVo();
            org.niord.core.publication.series.criteria.MessageSeriesCriterionVo node =
                    new org.niord.core.publication.series.criteria.MessageSeriesCriterionVo();
            node.setValues(new java.util.ArrayList<>(List.of("dma-nm")));
            doc.getCriteria().add(node);
            s.setCriteria(doc);
        }
        em.persist(s);
        return s;
    }

    /**
     * The ownerless count is on the sheet, whatever it reads.
     *
     * An absent number and a zero read alike on a checklist somebody ticks, so the
     * count is written whether or not there is anything to report -- and a
     * publication with no owner is a violation rather than a note, because the
     * owner decides which admin list it appears on, who may change it, and the
     * timezone its cut-offs are reckoned in.
     */
    @Test
    @Transactional
    public void theOwnerlessCountIsReportedAndIsAViolationWhenItIsNotZero() {
        CutoverPreflightService.Preflight result = preflight.run();

        assertNotNull(result.counts().get("seriesWithoutOwner"),
                "the pre-flight does not say how many publications have no owner; an absent number "
                        + "and zero read alike on a sheet somebody ticks");

        long ownerless = em.createQuery(
                        "SELECT COUNT(s) FROM PublicationSeries s WHERE s.domain IS NULL", Long.class)
                .getSingleResult();
        assertEquals((int) ownerless, result.counts().get("seriesWithoutOwner").intValue(),
                "the reported count disagrees with the estate it describes");
        assertEquals(ownerless, result.violations().stream()
                        .filter(v -> "SERIES_WITHOUT_OWNER".equals(v.code())).count(),
                "an ownerless publication must be a VIOLATION, not a note: it appears on no admin "
                        + "list, nobody administers it, and it has no timezone to read a cut-off in");
    }

    /** The audit's own pattern, applied the way the audit applies it. */
    private static boolean namesAWeeklyTag(String expression) {
        return java.util.regex.Pattern
                .compile("(nm-(?:pt-)?w\\d{1,2}(?:-\\d{1,2})?-\\d{4})",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(expression).find();
    }
}
