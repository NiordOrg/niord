package org.niord.core.publication.series.replay;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The historical replay's gate, driven over synthetic diffs.
 *
 * Synthetic on purpose. The gate's job is to decide what a diff MEANS, and that
 * decision has to be tested against cases the real estate does not currently
 * contain -- an entry that stopped diverging, a partly-explained divergence, an
 * entry for an issue nobody replayed. Waiting for the archive to produce one of
 * those is waiting for the failure this exists to catch.
 */
public class ReplayGateTest {

    private static final String ISSUE = "nm-w27-2026";

    private static ReplayReport.IssueDiff diff(Set<String> missing, Set<String> extra) {
        return new ReplayReport.IssueDiff(ISSUE, "weekly-ntm", missing, extra);
    }

    /**
     * Parses through the SAME validation production uses.
     *
     * Not wrapped in a try/catch: several of these tests assert on the refusal
     * message, and re-wrapping would replace the sentence the manifest wrote
     * with one this test wrote.
     */
    private static ExpectedDiffManifest manifestOf(String json) {
        return ExpectedDiffManifest.parse(json);
    }

    // ------------------------------------------------------------ the gate

    /** An unexplained divergence fails. This is the whole point. */
    @Test
    public void aDivergenceWithNoManifestEntryFails() {
        ReplayReport report = new ReplayReport();
        report.recordDiff(diff(Set.of("uid-a"), Set.of()));

        List<ReplayGate.Failure> failures =
                ReplayGate.evaluate(report, manifestOf("{\"expectedDiffs\":[]}"));

        assertEquals(1, failures.size());
        assertEquals(ReplayGate.Failure.Kind.UNEXPECTED_DIVERGENCE, failures.get(0).kind());
    }

    /** An explained one passes. */
    @Test
    public void aDivergenceTheManifestAccountsForPasses() {
        ReplayReport report = new ReplayReport();
        report.recordDiff(diff(Set.of("uid-a"), Set.of()));

        List<ReplayGate.Failure> failures = ReplayGate.evaluate(report, manifestOf("""
                {"expectedDiffs":[{
                  "publicId":"nm-w27-2026",
                  "divergenceClass":"TYPE_MUTATED_AFTER_RELEASE",
                  "missing":["uid-a"],
                  "extra":[],
                  "reason":"NM-300-24 was T at release and is PERMANENT_NOTICE today"
                }]}"""));

        assertTrue(failures.isEmpty(), ReplayGate.describe(failures));
    }

    /**
     * A PARTLY explained divergence fails.
     *
     * The entry permits uid-a; the replay found uid-a and uid-b. Passing this
     * would let one understood divergence carry an arbitrary number of
     * unexamined ones on the same issue, which is the failure mode a manifest
     * is most likely to develop over time.
     */
    @Test
    public void aDivergenceOnlyPartlyAccountedForFails() {
        ReplayReport report = new ReplayReport();
        report.recordDiff(diff(Set.of("uid-a", "uid-b"), Set.of()));

        List<ReplayGate.Failure> failures = ReplayGate.evaluate(report, manifestOf("""
                {"expectedDiffs":[{
                  "publicId":"nm-w27-2026",
                  "divergenceClass":"TYPE_MUTATED_AFTER_RELEASE",
                  "missing":["uid-a"],
                  "extra":[],
                  "reason":"only uid-a was ever explained"
                }]}"""));

        assertEquals(1, failures.size());
        assertEquals(ReplayGate.Failure.Kind.UNEXPECTED_DIVERGENCE, failures.get(0).kind());
        assertTrue(failures.get(0).detail().contains("uid-b"),
                "the failure names the part nobody explained");
    }

    /**
     * An entry that no longer diverges fails, so the manifest cannot rot.
     *
     * Without this the manifest only ever grows, and every entry in it is
     * standing permission for a divergence to reappear unannounced.
     */
    @Test
    public void aManifestEntryThatNoLongerDivergesFails() {
        ReplayReport report = new ReplayReport();
        report.recordIdentical();

        List<ReplayGate.Failure> failures = ReplayGate.evaluate(report, manifestOf("""
                {"expectedDiffs":[{
                  "publicId":"nm-w27-2026",
                  "divergenceClass":"LEGACY_TAG_STALENESS",
                  "missing":["uid-a"],
                  "extra":[],
                  "reason":"stale tag member, cancelled while not RECORDING"
                }]}"""));

        assertEquals(1, failures.size());
        assertEquals(ReplayGate.Failure.Kind.MANIFEST_ENTRY_NO_LONGER_DIVERGES,
                failures.get(0).kind());
    }

    /** An entry for an issue the replay skipped is permission granted in the dark. */
    @Test
    public void aManifestEntryForASkippedIssueFails() {
        ReplayReport report = new ReplayReport();
        report.recordIdentical();
        report.recordSkip(ISSUE, ReplayReport.SkipReason.FILE_REPLACED_BY_HAND);

        List<ReplayGate.Failure> failures = ReplayGate.evaluate(report, manifestOf("""
                {"expectedDiffs":[{
                  "publicId":"nm-w27-2026",
                  "divergenceClass":"UNION_OVER_LONG_WINDOW",
                  "missing":["uid-a"],
                  "extra":[],
                  "reason":"two annual cohorts in one issue"
                }]}"""));

        assertEquals(1, failures.size());
        assertEquals(ReplayGate.Failure.Kind.MANIFEST_ENTRY_NOT_REPLAYED, failures.get(0).kind());
    }

    // ------------------------------------------------- what the manifest refuses

    /** A reason is mandatory: an unexplained divergence is not an expected one. */
    @Test
    public void anEntryWithNoReasonIsRefusedAtLoad() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> manifestOf("""
                {"expectedDiffs":[{
                  "publicId":"nm-w27-2026",
                  "divergenceClass":"MANUAL_TAG_EDIT",
                  "missing":["uid-a"],
                  "extra":[],
                  "reason":"   "
                }]}"""));
        assertTrue(e.getMessage().contains("no reason"), e.getMessage());
    }

    /** A class has to be one of the measured ones -- naming one is a claim. */
    @Test
    public void anEntryWithAnUnknownClassIsRefusedAtLoad() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> manifestOf("""
                {"expectedDiffs":[{
                  "publicId":"nm-w27-2026",
                  "divergenceClass":"BECAUSE_I_SAID_SO",
                  "missing":["uid-a"],
                  "extra":[],
                  "reason":"it is fine, honestly"
                }]}"""));
        assertTrue(e.getMessage().contains("not one of the measured classes"), e.getMessage());
    }

    /** An entry permitting nothing only hides that the issue is watched. */
    @Test
    public void anEntryWithAnEmptyDeltaIsRefusedAtLoad() {
        assertThrows(IllegalStateException.class, () -> manifestOf("""
                {"expectedDiffs":[{
                  "publicId":"nm-w27-2026",
                  "divergenceClass":"MANUAL_TAG_EDIT",
                  "missing":[],
                  "extra":[],
                  "reason":"nothing to see"
                }]}"""));
    }

    /** Two entries for one issue means two stories about one thing. */
    @Test
    public void twoEntriesForOneIssueAreRefusedAtLoad() {
        assertThrows(IllegalStateException.class, () -> manifestOf("""
                {"expectedDiffs":[
                  {"publicId":"nm-w27-2026","divergenceClass":"MANUAL_TAG_EDIT",
                   "missing":["uid-a"],"extra":[],"reason":"one story"},
                  {"publicId":"nm-w27-2026","divergenceClass":"LEGACY_TAG_STALENESS",
                   "missing":["uid-b"],"extra":[],"reason":"a different story"}
                ]}"""));
    }

    // -------------------------------------------------- the committed manifest

    /**
     * The manifest that ships loads, and is currently empty.
     *
     * Empty is the honest starting state: the entries have to name real uids on
     * real issues, and those come from a replay against an environment holding
     * both the imported issues and the message estate. Asserting the count keeps
     * that fact visible -- when it stops being zero, this test says so and
     * somebody confirms the entries were measured rather than guessed.
     */
    @Test
    public void theCommittedManifestLoadsAndIsStillEmpty() {
        ExpectedDiffManifest shipped =
                ExpectedDiffManifest.load(ExpectedDiffManifest.DEFAULT_RESOURCE);

        assertEquals(0, shipped.size(),
                "the manifest has gained entries. That is expected once the replay has run "
                        + "against real data -- confirm each entry names measured uids and update "
                        + "this assertion to the new count.");
    }

    /** An absent manifest must fail loudly: silence would pass every divergence. */
    @Test
    public void anAbsentManifestIsNotAnEmptyOne() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ExpectedDiffManifest.load("/no/such/manifest.json"));
        assertTrue(e.getMessage().contains("not an empty one"), e.getMessage());
    }
}
