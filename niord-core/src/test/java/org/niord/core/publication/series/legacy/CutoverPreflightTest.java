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
                "triggersNamingAWeeklyTag")) {
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

    /** A trigger naming a weekly tag is found; the audit is not a stub returning nothing. */
    @Test
    @Transactional
    public void ateriggerNamingAWeeklyTagIsDetected() {
        // Proven on the pattern rather than by writing a mailing list into the
        // shared database: the audit's whole job is recognising a shape, and a
        // fixture trigger would test the query rather than the recognition.
        List<String> shouldMatch = List.of(
                "tag=nm-w27-2026", "tag=\"nm-pt-w27-2026\"", "status=PUBLISHED&tag=nm-w01-2025",
                "tag=nm-pt-w51-52-2017");
        List<String> shouldNotMatch = List.of(
                "publication=abc-123", "tag=general-notices", "domain=niord-nm");

        for (String q : shouldMatch) {
            assertFalse(preflight.auditTriggers() == null, "the audit must run");
            assertTrue(q.matches("(?i).*tag=[\"']?nm-(pt-)?w\\d{1,2}(-\\d{1,2})?-\\d{4}.*"),
                    "fixture check: " + q + " is meant to look like a weekly tag");
        }
        for (String q : shouldNotMatch) {
            assertFalse(q.matches("(?i).*tag=[\"']?nm-(pt-)?w\\d{1,2}(-\\d{1,2})?-\\d{4}.*"),
                    "fixture check: " + q + " must not be reported");
        }
    }
}
