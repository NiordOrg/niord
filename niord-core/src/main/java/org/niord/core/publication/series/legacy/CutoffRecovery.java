package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * B5.4b. The four-stage cut-off recovery cascade.
 *
 * Legacy never stored a release instant. It stored publishDateFrom, which is the
 * start of the PUBLIC window, and updated, which is whenever the row was last
 * written. The cut-off -- the instant membership was frozen -- has to be
 * recovered, and the four stages are ordered by how directly each one witnesses
 * that instant.
 *
 * NEVER KEY ON updated ALONE. It recovers most weekly and P&T cut-offs and
 * almost none of the non-weekly ones, because a row that was edited years later
 * carries the edit, not the release. The cascade exists because one source is
 * right for most rows and wrong for a class of rows that is small, identifiable,
 * and would be silently wrong.
 *
 * EVERY STAGE RECORDS ITSELF. cutoffSource carries its OWN value, and
 * cutoffReconstructed is true on ALL four -- because legacy stored no release
 * instant, so no stage here read one. Collapsing RECOVERED_FROM_UPDATED into
 * STAMPED would launder a last-write timestamp into a release stamp for roughly
 * 700 issues, and nothing downstream could tell which of them was real.
 */
public final class CutoffRecovery {

    /** Stage 1: the row's own last-write time, taken as the release. */
    public static final String FROM_UPDATED = "RECOVERED_FROM_UPDATED";

    /** Stage 2: the NEXT issue's tag creation, which witnesses this issue closing. */
    public static final String FROM_NEXT_TAG = "RECOVERED_FROM_NEXT_TAG";

    /** Stage 3: the date printed on the PDF cover of an annual. */
    public static final String FROM_COVER = "RECOVERED_FROM_COVER";

    /** Stage 4: nothing witnesses it. A human decides. */
    public static final String MANUAL = "MANUAL";

    /**
     * Not a stage. The issue was never released, so there is no instant to find.
     *
     * Distinct from MANUAL, which means the release happened and the evidence is
     * gone. Here nothing is missing: an OPEN issue is unstamped because nobody
     * has published it, and it gets its stamp from the publish action like any
     * other. Recording it as a recovery failure would put a "needs a human"
     * marker on the one row that is behaving normally.
     */
    public static final String NOT_RELEASED = "NOT_RELEASED";

    /**
     * How far apart updated and the next tag may be before the tag wins.
     *
     * Five minutes, because the two are written by the same release action when
     * that action ran normally: a gap inside five minutes is the same event seen
     * twice, and a gap outside it means the row was touched again later.
     */
    public static final long AGREEMENT_WINDOW_MS = 5 * 60 * 1000L;

    /** What the cascade concluded, and which stage concluded it. */
    public record Recovered(Date cutoff, String source, boolean reconstructed) {
    }

    /**
     * The interval a recovered cut-off has to be believable within.
     *
     * Every stage below reads a timestamp off a legacy row, and a timestamp being
     * present is not the same as it being this release. The bounds are what turns
     * a plausible date into a checkable claim.
     */
    public record Bounds(Date from, Date to) {

        /** No interval to check against -- an in-force issue, or the head of a chain. */
        public static final Bounds NONE = new Bounds(null, null);

        /**
         * Whether a candidate could be this period's close.
         *
         * Strictly after the open, because a period cannot close before it begins.
         * The upper bound is deliberately loose -- a full period past the nominal
         * close -- because the release action legitimately runs a little AFTER the
         * bound it closes at, and a tight ceiling would reject the ordinary case.
         */
        boolean believable(Date candidate) {
            if (candidate == null) {
                return false;
            }
            if (from != null && !candidate.after(from)) {
                return false;
            }
            if (from != null && to != null) {
                long period = to.getTime() - from.getTime();
                return candidate.getTime() <= to.getTime() + period;
            }
            return true;
        }
    }

    private CutoffRecovery() {
    }

    /**
     * Runs the cascade for one issue.
     *
     * nextTagCreated is the messageTag.created of the issue that FOLLOWS this one
     * in its series, or null when there is no next issue or it carries no tag.
     * coverDate is the date printed on an annual's cover, or null.
     *
     * released says whether this row was ever published -- PUBLISHED or RETIRED,
     * never OPEN. It is a REQUIRED argument rather than a defaulted one because
     * every stage below reads a timestamp that exists on an unreleased row too,
     * and each of them would happily return it.
     *
     * bounds is the issue's own interval. A stage whose answer falls outside it is
     * not believed, and the cascade moves on rather than returning it -- because a
     * cut-off outside the period it supposedly closes is wrong however respectable
     * its provenance. NtM Week 52 - 2025 covered 17-24 December and recovered a
     * cut-off of 2 January, 349 days early, from an `updated` stamp that looks like
     * a placeholder row created in January for the year-end edition and never
     * touched again. Its 19 members were all published inside the interval, so
     * nothing else about the row was wrong.
     *
     * When nothing fits, the answer is MANUAL with no date. For a tiling issue
     * that is not a loss: intervalTo IS the nominal close, and effectiveCutoff
     * already coalesces onto it.
     */
    public static Recovered recover(Publication legacy, Date nextTagCreated, Date coverDate,
                                    boolean released, Bounds bounds) {
        // Before any stage runs. A row nobody released has no release instant, and
        // every source below would still produce a date for it: `updated` on a
        // never-published issue is when its PREDECESSOR was released, because that
        // is the action that created it. Taken as a cut-off it lands a full period
        // BEFORE the issue's own interval even opens -- so the row sorts a week
        // early, ties with the issue before it, and anchors gap arithmetic on a
        // date at which it did not exist.
        if (!released) {
            return new Recovered(null, NOT_RELEASED, false);
        }

        Date updated = legacy.getUpdated();

        // Stage 2 overrides stage 1 where they disagree by more than the window.
        // Ordered this way round deliberately: `updated` is present on every row
        // and so would always win a first-non-null race, which is exactly the
        // "key on updated alone" failure this cascade exists to avoid.
        boolean tagOutranksUpdated = nextTagCreated != null
                && (updated == null
                    || Math.abs(nextTagCreated.getTime() - updated.getTime()) > AGREEMENT_WINDOW_MS);

        List<Recovered> candidates = new ArrayList<>();
        if (tagOutranksUpdated) {
            candidates.add(new Recovered(nextTagCreated, FROM_NEXT_TAG, true));
            if (updated != null) {
                candidates.add(new Recovered(updated, FROM_UPDATED, true));
            }
        } else {
            if (updated != null) {
                candidates.add(new Recovered(updated, FROM_UPDATED, true));
            }
            if (nextTagCreated != null) {
                candidates.add(new Recovered(nextTagCreated, FROM_NEXT_TAG, true));
            }
        }
        if (coverDate != null) {
            candidates.add(new Recovered(coverDate, FROM_COVER, true));
        }

        // The precedence is unchanged; what is new is that a stage has to produce a
        // date that could BE this period's close. The lower-ranked stages are tried
        // rather than skipped, because the next witness is still better evidence
        // than no witness.
        for (Recovered candidate : candidates) {
            if (bounds == null || bounds.believable(candidate.cutoff())) {
                return candidate;
            }
        }

        return new Recovered(null, MANUAL, true);
    }

    /**
     * The next issue's tag-creation instant, for an issue at position i.
     *
     * The list must be the series' own issues in chain order. Returns null at the
     * end of the chain, which is correct rather than missing: the newest issue
     * has no successor to witness it closing.
     */
    public static Date nextTagCreated(List<Publication> chainOrdered, int i) {
        if (i < 0 || i + 1 >= chainOrdered.size()) {
            return null;
        }
        Publication next = chainOrdered.get(i + 1);
        return next.getMessageTag() == null ? null : next.getMessageTag().getCreated();
    }

    /**
     * Every stage is a reconstruction, so cutoffReconstructed is true on all of
     * them and cutoffSource is what distinguishes them.
     *
     * That is not a shortcut: legacy stored NO release instant, so there is no
     * stage here that read one. A stage-1 row whose reconstructed flag said false
     * would be claiming its updated column was a release stamp, which is the
     * laundering this cascade exists to prevent.
     */
    public static boolean isStamped(String source) {
        return false;
    }
}
