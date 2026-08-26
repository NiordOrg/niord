package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.BindsRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5.7. The pre-flight, and the trigger audit that has to be read before cutover.
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
     * I-18 is asserted across every imported issue, and the id space does not
     * collide.
     *
     * Both are cheap to state and one-way to get wrong: after B7.1 a second
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

    /** The audit's own pattern, applied the way the audit applies it. */
    private static boolean namesAWeeklyTag(String expression) {
        return java.util.regex.Pattern
                .compile("(nm-(?:pt-)?w\\d{1,2}(?:-\\d{1,2})?-\\d{4})",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(expression).find();
    }
}
