package org.niord.core.publication.series.replay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * The report the UAT and cutover decisions are made from.
 *
 * Markdown, because the decision it supports is made by people reading it and
 * arguing about it, and JSON is a shape for programs. The same numbers are
 * available structurally from the shadow-diff endpoint; this is the version
 * somebody pastes into a meeting.
 *
 * <h2>What it must never do</h2>
 *
 * Say a series is ready when it is not. Two consecutive green weeks is the
 * cutover precondition, and the failure mode here is not a wrong number -- it
 * is a number that is technically right and reads as more than it is. So a
 * skipped week never counts towards a streak, a series with no runs at all is
 * called out rather than shown as a blank, and the report always states what it
 * did NOT examine.
 */
@ApplicationScoped
public class DiagnosticReportService {

    @Inject
    ShadowDiffService shadowDiff;

    @Inject
    ReplayHarness replay;

    /** The cutover precondition, in one place. */
    public static final int REQUIRED_GREEN_RELEASES = 2;

    /**
     * The ongoing half: per series, the green streak and what is outstanding.
     *
     * @param includeHistorical also run the full historical replay and check it against
     *                          the manifest. Off by default because the replay
     *                          re-resolves every imported issue -- minutes of
     *                          work, and nobody wants it on a page refresh.
     */
    public String render(boolean includeHistorical) {
        StringBuilder md = new StringBuilder();
        md.append("# Publications cutover — diagnostic report\n\n");
        md.append("Generated ").append(stamp(new Date())).append(".\n\n");

        renderShadowDiff(md);

        if (includeHistorical) {
            renderReplay(md);
        } else {
            md.append("\n## Historical replay\n\n");
            md.append("**Not run.** It re-resolves every imported issue, so it is opt-in: ")
              .append("`?historical=true`. Without it this report says nothing about the ")
              .append("imported archive, only about releases since the shadow-diff started.\n");
        }

        return md.toString();
    }

    // --------------------------------------------------------- the ongoing half

    private void renderShadowDiff(StringBuilder md) {
        List<ShadowDiffRun> runs = shadowDiff.all();

        md.append("## Readiness — two consecutive green releases per series\n\n");

        if (runs.isEmpty()) {
            md.append("**No shadow-diff has run yet.** The job compares each legacy release as it ")
              .append("happens, so this stays empty until the branch is deployed AND a release ")
              .append("occurs. An empty table is not a passing one: no series meets the ")
              .append("precondition, because no series has any evidence.\n");
            return;
        }

        Map<String, List<ShadowDiffRun>> bySeries = new LinkedHashMap<>();
        for (ShadowDiffRun run : runs) {   // already newest-first
            bySeries.computeIfAbsent(
                    run.getSeriesId() == null ? "(unmapped)" : run.getSeriesId(),
                    k -> new ArrayList<>()).add(run);
        }

        md.append("| Series | Consecutive green | Runs | Skipped | Ready |\n");
        md.append("| --- | ---: | ---: | ---: | :---: |\n");

        List<String> notReady = new ArrayList<>();
        for (Map.Entry<String, List<ShadowDiffRun>> e : bySeries.entrySet()) {
            List<ShadowDiffRun> seriesRuns = e.getValue();   // newest release first
            ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(seriesRuns);

            if (!r.ready()) {
                notReady.add(e.getKey());
            }
            md.append("| `").append(e.getKey()).append("` | ").append(r.consecutiveGreen())
              .append(" | ").append(r.runs())
              .append(" | ").append(r.skipped())
              .append(" | ").append(r.exempt() ? "exempt — not comparable" : r.ready() ? "**yes**" : "no")
              .append(" |\n");
        }

        md.append("\nA **skipped** release does not extend a streak. A week nobody could compare ")
          .append("is not evidence that the week agreed, and counting it would let a series reach ")
          .append("the precondition without a single comparison.\n");

        if (!notReady.isEmpty()) {
            md.append("\n**Not yet clear for cutover:** ");
            md.append(String.join(", ", notReady)).append(".\n");
        }

        renderOutstanding(md, bySeries);
    }

    /** Every run that carried a delta, with the uids. */
    private void renderOutstanding(StringBuilder md,
                                   Map<String, List<ShadowDiffRun>> bySeries) {

        List<ShadowDiffRun> outstanding = new ArrayList<>();
        bySeries.values().forEach(runs -> runs.stream()
                .filter(r -> r.getSkipReason() == null && !r.isGreen())
                .forEach(outstanding::add));

        md.append("\n## Outstanding diffs\n\n");
        if (outstanding.isEmpty()) {
            md.append("None. Every compared release matched exactly.\n");
            return;
        }

        md.append("| Series | Release | Cut-off | Missing | Extra |\n");
        md.append("| --- | --- | --- | --- | --- |\n");
        for (ShadowDiffRun r : outstanding) {
            md.append("| `").append(r.getSeriesId()).append("` | `")
              .append(r.getLegacyPublicationId()).append("` | ")
              .append(stamp(r.getCutoffAt())).append(" | ")
              .append(uids(r.missing())).append(" | ")
              .append(uids(r.extra())).append(" |\n");
        }
        md.append("\n*Missing* is recorded-but-not-resolved -- the new engine dropping something ")
          .append("the archive served. *Extra* is the reverse. They are different failures and ")
          .append("the second is the one that puts a notice in front of somebody.\n");
    }

    // ------------------------------------------------------ the historical half

    private void renderReplay(StringBuilder md) {
        ReplayReport report = replay.replayAll();
        ExpectedDiffManifest manifest =
                ExpectedDiffManifest.load(ExpectedDiffManifest.DEFAULT_RESOURCE);
        List<ReplayGate.Failure> failures = ReplayGate.evaluate(report, manifest);

        md.append("\n## Historical replay\n\n");
        md.append("| | |\n| --- | ---: |\n");
        md.append("| Issues compared | ").append(report.compared()).append(" |\n");
        md.append("| ... matching exactly | ").append(report.identical()).append(" |\n");
        md.append("| ... diverging | ").append(report.diffs().size()).append(" |\n");
        md.append("| Skipped | ").append(report.skipped().size()).append(" |\n");
        md.append("| Manifest entries | ").append(manifest.size()).append(" |\n");

        if (!report.skipped().isEmpty()) {
            md.append("\n**Skipped, by reason** — stated because a replay that reports only ")
              .append("comparisons reads as much better news than it is:\n\n");
            report.skipCounts().forEach((reason, n) ->
                    md.append("- `").append(reason).append("` — ").append(n).append("\n"));
        }

        md.append("\n### Gate\n\n");
        if (failures.isEmpty()) {
            md.append("**Clear.** Every divergence is accounted for by a manifest entry, and ")
              .append("every manifest entry still describes a real divergence.\n");
            return;
        }

        md.append("**").append(failures.size()).append(" failure(s).**\n\n");
        for (ReplayGate.Failure f : failures) {
            md.append("- `").append(f.publicId()).append("` **").append(f.kind()).append("** — ")
              .append(f.detail()).append("\n");
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The streak of green, compared releases from the newest backwards.
     *
     * Stops at the first release that is not BOTH green and compared. A skipped
     * release breaks the streak rather than being passed over, because the
     * question is "have the last N releases agreed", and a release nobody
     * compared has not agreed -- it has not been asked.
     */
    /** The one readiness rule, kept here by name for its callers. */
    static int consecutiveGreen(List<ShadowDiffRun> newestReleaseFirst) {
        return ShadowDiffService.readinessOf(newestReleaseFirst).consecutiveGreen();
    }

    private static String uids(java.util.Set<String> uids) {
        if (uids.isEmpty()) {
            return "—";
        }
        return uids.size() <= 5
                ? "`" + String.join("`, `", uids) + "`"
                : uids.size() + " uids";
    }

    private static String stamp(Date d) {
        if (d == null) {
            return "—";
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }
}
