package org.niord.core.publication.series.replay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * B6.1's build gate: {@code actualDiffs subset-of manifest}.
 *
 * Deliberately NOT "no diffs". The archive is not fully reproducible and three
 * independent sweeps said so -- four annual issues are the answer to no query at
 * any instant, ten annexes have no recoverable member list. A zero-diff gate
 * would demand the impossible and be switched off within a week, which is worse
 * than no gate because switching it off is silent.
 *
 * What it keeps is the half that pays: an UNEXPLAINED divergence still fails.
 */
public final class ReplayGate {

    /** A reason the gate failed, in the words the failure should be read in. */
    public record Failure(String publicId, Kind kind, String detail) {

        public enum Kind {
            /** The replay found a divergence no manifest entry accounts for. */
            UNEXPECTED_DIVERGENCE,
            /** A manifest entry permits more than the replay actually found. */
            MANIFEST_ENTRY_NO_LONGER_DIVERGES,
            /** An entry names an issue the replay never compared. */
            MANIFEST_ENTRY_NOT_REPLAYED
        }
    }

    private ReplayGate() {
    }

    /**
     * Everything wrong with this replay against this manifest.
     *
     * Returns every failure rather than the first: somebody clearing these is
     * doing it one build at a time otherwise, and each build is a deploy.
     */
    public static List<Failure> evaluate(ReplayReport report, ExpectedDiffManifest manifest) {
        List<Failure> failures = new ArrayList<>();

        Set<String> diverged = new LinkedHashSet<>();
        for (ReplayReport.IssueDiff diff : report.diffs()) {
            diverged.add(diff.publicId());
            ExpectedDiffManifest.Entry entry = manifest.get(diff.publicId());

            if (entry == null) {
                failures.add(new Failure(diff.publicId(), Failure.Kind.UNEXPECTED_DIVERGENCE,
                        "no manifest entry: " + diff.missing().size() + " missing, "
                                + diff.extra().size() + " extra. Either the engine changed or a "
                                + "divergence nobody has explained has appeared."));
            } else if (!entry.covers(diff)) {
                Set<String> unaccountedMissing = new LinkedHashSet<>(diff.missing());
                unaccountedMissing.removeAll(entry.missing());
                Set<String> unaccountedExtra = new LinkedHashSet<>(diff.extra());
                unaccountedExtra.removeAll(entry.extra());

                failures.add(new Failure(diff.publicId(), Failure.Kind.UNEXPECTED_DIVERGENCE,
                        "the manifest entry (" + entry.divergenceClass() + ") does not account for "
                                + "missing " + unaccountedMissing + " / extra " + unaccountedExtra
                                + ". A partly-explained divergence is an unexplained one."));
            }
        }

        // The anti-rot half. An entry that no longer fires is permission nobody
        // is using, and the day the thing it permitted comes back, it comes back
        // silently.
        for (ExpectedDiffManifest.Entry entry : manifest.entries()) {
            if (diverged.contains(entry.publicId())) {
                continue;
            }
            boolean replayed = report.compared() > 0
                    && !report.skipped().containsKey(entry.publicId());

            failures.add(replayed
                    ? new Failure(entry.publicId(), Failure.Kind.MANIFEST_ENTRY_NO_LONGER_DIVERGES,
                            "expected " + entry.divergenceClass() + " but the replay matched exactly. "
                                    + "Delete the entry -- keeping it grants permission for something "
                                    + "that has stopped happening.")
                    : new Failure(entry.publicId(), Failure.Kind.MANIFEST_ENTRY_NOT_REPLAYED,
                            "the replay never compared this issue, so the entry cannot be "
                                    + "confirmed or retired. It is permission granted in the dark."));
        }

        return failures;
    }

    /** The failures, as the one string a build log should carry. */
    public static String describe(List<Failure> failures) {
        if (failures.isEmpty()) {
            return "replay gate clear";
        }
        StringBuilder sb = new StringBuilder(failures.size() + " replay gate failure(s):");
        for (Failure f : failures) {
            sb.append("\n  [").append(f.kind()).append("] ").append(f.publicId())
                    .append(" -- ").append(f.detail());
        }
        return sb.toString();
    }
}
