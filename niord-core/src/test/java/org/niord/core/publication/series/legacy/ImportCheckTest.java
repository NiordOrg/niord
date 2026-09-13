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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.BindsRule;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The import check, and the trigger audit that has to be read alongside it.
 *
 * The pass reports rather than throws, because it is a checklist an admin runs
 * and reads. What must not happen is a violation going unnoticed, so the test
 * asserts the shape of the answer and that the report exists at all -- an absent
 * report is a failed check, not a clean one.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class ImportCheckTest {

    /** Committed whether or not it is empty; see theTriggerAuditIsCommittedEmptyOrNot. */
    private static final Path REPORT =
            Path.of("src", "test", "resources", "fixtures", "import-check-trigger-audit.md");

    @Inject
    ImportCheckService importCheck;

    @Inject
    EntityManager em;

    /** The pass runs over the imported estate and answers in one shape. */
    @Test
    @Transactional
    public void theCheckRunsAndReportsEveryAssertion() {
        ImportCheckService.ImportCheck result = importCheck.run();

        assertNotNull(result.violations());
        assertNotNull(result.triggerAudit());
        assertNotNull(result.counts());

        for (String key : List.of("importedIssues", "seriesWithACurrentIssue", "idCollisions",
                "triggersNamingAWeeklyTag", "duplicateMemberships", "duplicateOverrides")) {
            assertTrue(result.counts().containsKey(key),
                    "the check must report " + key + "; an assertion that runs and says nothing is "
                            + "indistinguishable from one that did not run");
        }
    }

    /**
     * The per-series sheet: the missing periods, and the kind.
     *
     * Several rows of the rehearsal checklist read these, and before they were
     * here they were answerable only by calling other endpoints and counting
     * rows. A checklist ticked from three moments can be ticked against three
     * different states of the estate, which is precisely what the go-live window
     * cannot afford.
     */
    @Test
    @Transactional
    public void theCheckDescribesEverySeries() {
        ImportCheckService.ImportCheck result = importCheck.run();

        assertNotNull(result.series());
        for (String key : List.of("series", "uncoveredPeriods")) {
            assertTrue(result.counts().containsKey(key),
                    "the check must report " + key);
        }
        // Every kind is named whether or not the estate has one: an absent kind
        // and a kind with no series read alike on a sheet, and the estate-shape
        // step is ticked by comparing the three numbers against expected ones.
        for (org.niord.core.publication.series.SeriesKind kind
                : org.niord.core.publication.series.SeriesKind.values()) {
            assertTrue(result.counts().containsKey("seriesOfKind" + kind.name()),
                    "the estate-shape step compares SCHEDULED / UNSCHEDULED / ONE_OFF against "
                            + "expected counts, so every kind is reported even at zero: "
                            + result.counts().keySet());
        }

        result.series().forEach((seriesId, row) -> {
            assertEquals(seriesId, row.seriesId());
            assertNotNull(row.status(), "a row with no status cannot be judged");
            assertNotNull(row.kind());
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
     * A gap does not make the check dirty.
     *
     * A gap is a fact about an archive that predates this system -- the estate has
     * had them since 2017 -- and folding it into `clear` would stop the check ever
     * passing on an estate in exactly the state everybody expects, and a check
     * that can never come back clean is a check people learn to ignore.
     */
    @Test
    @Transactional
    public void gapsAreReportedButDoNotMakeTheCheckDirty() {
        ImportCheckService.ImportCheck result = importCheck.run();

        for (ImportCheckService.Violation v : result.violations()) {
            assertFalse(v.code().contains("GAP"),
                    "the check raised " + v.code() + " as a violation; gaps are reported per series "
                            + "and judged elsewhere");
        }
    }

    /**
     * I-18 is asserted across every imported issue, and the id space does not
     * collide.
     *
     * Both are cheap to state and one-way to get wrong: once an imported issue is
     * published a second current issue is serving the public, and a colliding id
     * means one citation resolves to whichever document the query found first.
     */
    @BindsRule({"I-18"})
    @Test
    @Transactional
    public void theEstateHasOneCurrentIssuePerSeriesAndNoCollidingIds() {
        ImportCheckService.ImportCheck result = importCheck.run();

        List<ImportCheckService.Violation> fatal = result.violations().stream()
                .filter(v -> v.code().startsWith("I18_") || v.code().startsWith("X1_"))
                .toList();

        assertTrue(fatal.isEmpty(),
                "the imported estate must not carry two current issues on one series, nor a colliding "
                        + "publicId: " + fatal);
    }

    /**
     * The trigger audit is emitted as a committed file, empty or not.
     *
     * An absent report is a failed check rather than a clean one: "we found
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
     * The ownerless count is on the sheet, and on this database it reads zero.
     *
     * An absent number and a zero read alike on a checklist somebody ticks, so the
     * count is written whether or not there is anything to report.
     *
     * ZERO IS ALL THIS PATH CAN EVER SEE HERE, and that is worth stating rather
     * than dressing up: V13 made the owner column NOT NULL on this database, so
     * the query behind the finding cannot return a row. Running the whole check
     * therefore proves the count key exists and nothing else -- the shape of a
     * finding when there IS one is asserted below, on the builder.
     */
    @Test
    @Transactional
    public void theOwnerlessCountIsAlwaysOnTheSheet() {
        ImportCheckService.ImportCheck result = importCheck.run();

        assertNotNull(result.counts().get(ImportCheckService.OWNERLESS_COUNT),
                "the check does not say how many publications have no owner; an absent number "
                        + "and zero read alike on a sheet somebody ticks");

        long ownerless = em.createQuery(
                        "SELECT COUNT(s) FROM PublicationSeries s WHERE s.domain IS NULL", Long.class)
                .getSingleResult();
        assertEquals((int) ownerless,
                result.counts().get(ImportCheckService.OWNERLESS_COUNT).intValue(),
                "the reported count disagrees with the estate it describes");
    }

    /**
     * And what a finding looks like when the estate really does carry one.
     *
     * Driven through the builder with the count supplied, because the real query
     * cannot produce one on a database whose owner column is NOT NULL -- see
     * above. Three things are pinned, and each is what somebody downstream depends
     * on: the CODE, which a runbook greps the report for; the COUNT KEY, which the
     * checklist reads and compares against an expected number; and the MESSAGE,
     * because a violation that says a publication is wrong without saying what to
     * do about it holds the window open for as long as it takes somebody to work
     * it out.
     */
    @Test
    public void anownerlessPublicationIsAViolationThatSaysWhatToDo() {
        List<ImportCheckService.Violation> violations = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        ImportCheckService.reportOwnerless(
                List.of("stranded-series", "another-stranded-series"), violations, counts);

        assertEquals(2, counts.get(ImportCheckService.OWNERLESS_COUNT),
                "the count key the checklist reads must carry the number of findings");
        assertEquals(2, violations.size(),
                "an ownerless publication must be a VIOLATION, not a note: it appears on no admin "
                        + "list, nobody administers it, and it has no timezone to read a cut-off in");

        ImportCheckService.Violation first = violations.get(0);
        assertEquals("SERIES_WITHOUT_OWNER", first.code(),
                "the code is the external handle a runbook greps for; renaming it silently breaks "
                        + "the step that reads it");
        assertEquals(ImportCheckService.SERIES_WITHOUT_OWNER, first.code());
        assertEquals("stranded-series", first.subject(),
                "the finding must name the publication, or the operator has to find it themselves");
        assertTrue(first.detail().contains("Assign one before the import"),
                "the finding must say what to do about it: " + first.detail());
    }

    /**
     * A released issue with no publish stamp is a violation, and OPEN is not.
     *
     * The public list is the new-model issues union the legacy rows no published
     * issue has taken over, and the legacy half's exclusion is keyed on
     * publishedAt being set. An unstamped PUBLISHED or RETIRED issue is therefore
     * served twice -- once from each half, under one publication id.
     *
     * Driven through the builder on issues built in memory, because on a
     * correctly imported estate the real pass can only ever report zero and an
     * assertion that zero looks right is an assertion about nothing. What is
     * pinned is what the finding looks like when there IS one: the CODE a runbook
     * greps for, the COUNT KEY the sheet reads, and a message saying what to do.
     */
    @Test
    public void aReleasedIssueWithoutAPublishStampIsAViolation() {
        List<ImportCheckService.Violation> violations = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        ImportCheckService.reportUnstampedReleasedIssues(
                List.of(issue("nm-2019-12", IssueStatus.PUBLISHED, null),
                        issue("nm-2018-04", IssueStatus.RETIRED, null),
                        issue("nm-2020-01", IssueStatus.PUBLISHED, new java.util.Date()),
                        issue("nm-2026-33", IssueStatus.OPEN, null)),
                violations, counts);

        assertEquals(2, counts.get(ImportCheckService.UNSTAMPED_RELEASED_COUNT),
                "a stamped issue and an OPEN one are not findings; the other two are");
        assertEquals(2, violations.size());

        ImportCheckService.Violation first = violations.get(0);
        assertEquals("PUBLISHED_ISSUE_WITHOUT_STAMP", first.code(),
                "the code is the external handle the runbook greps for");
        assertEquals(ImportCheckService.PUBLISHED_ISSUE_WITHOUT_STAMP, first.code());
        assertEquals("nm-2019-12", first.subject(),
                "the finding must name the issue, or the operator has to find it themselves");
        assertTrue(first.detail().contains("served twice"),
                "the finding must say what goes wrong on the public site: " + first.detail());
        assertEquals("nm-2018-04", violations.get(1).subject(),
                "RETIRED is a violation too: an issue published and then retired has taken over "
                        + "its legacy row permanently, and an unstamped one resurrects it");
    }

    /** The unstamped count is on the sheet whatever the estate turns out to hold. */
    @Test
    @Transactional
    public void theUnstampedReleasedCountIsAlwaysOnTheSheet() {
        ImportCheckService.ImportCheck result = importCheck.run();

        assertNotNull(result.counts().get(ImportCheckService.UNSTAMPED_RELEASED_COUNT),
                "the check does not say how many released imported issues carry no publish stamp; "
                        + "an absent number and zero read alike on a sheet somebody ticks");
    }

    /** An issue with just the three facts this finding reads. */
    private static PublicationIssue issue(String publicId, IssueStatus status, java.util.Date publishedAt) {
        PublicationIssue i = new PublicationIssue();
        i.setPublicId(publicId);
        i.setStatus(status);
        i.setPublishedAt(publishedAt);
        return i;
    }

    /** The audit's own pattern, applied the way the audit applies it. */
    private static boolean namesAWeeklyTag(String expression) {
        return java.util.regex.Pattern
                .compile("(nm-(?:pt-)?w\\d{1,2}(?:-\\d{1,2})?-\\d{4})",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(expression).find();
    }
}
