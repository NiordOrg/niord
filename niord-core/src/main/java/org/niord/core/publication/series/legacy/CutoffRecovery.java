package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;

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

    private CutoffRecovery() {
    }

    /**
     * Runs the cascade for one issue.
     *
     * nextTagCreated is the messageTag.created of the issue that FOLLOWS this one
     * in its series, or null when there is no next issue or it carries no tag.
     * coverDate is the date printed on an annual's cover, or null.
     */
    public static Recovered recover(Publication legacy, Date nextTagCreated, Date coverDate) {
        Date updated = legacy.getUpdated();

        // Stage 2 overrides stage 1 where they disagree by more than the window.
        // Ordered this way round deliberately: `updated` is present on every row
        // and so would always win a first-non-null race, which is exactly the
        // "key on updated alone" failure this cascade exists to avoid.
        if (nextTagCreated != null
                && (updated == null
                    || Math.abs(nextTagCreated.getTime() - updated.getTime()) > AGREEMENT_WINDOW_MS)) {
            return new Recovered(nextTagCreated, FROM_NEXT_TAG, true);
        }

        if (updated != null) {
            return new Recovered(updated, FROM_UPDATED, true);
        }

        if (coverDate != null) {
            return new Recovered(coverDate, FROM_COVER, true);
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
