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
     * Not a witness of the release: the period's own nominal close, taken as the
     * cut-off because no stamp was believable.
     *
     * For a tiling issue that is the honest answer rather than a loss. The
     * content period closed at its nominal instant whatever time the release
     * action happened to run, and the membership rule reads the close, not the
     * action. A stamp that trails the close by days is an edit, and an edit is
     * not when the content was decided.
     */
    public static final String NOMINAL_CLOSE = "NOMINAL_CLOSE";

    /**
     * Not a witness of the release either: the boundary the public window names.
     *
     * An annual publication is released some time into the year it is valid for
     * -- or the year after the one it accumulates -- and the row's last-write
     * stamp says when somebody touched it, not what its contents describe. The
     * cut-off is the end of the content period, and for a yearly series the
     * public window IS that period (verified 2026-08-27: all 46 yearly rows carry
     * 1 January to 31 December). The release moment is kept separately, as
     * publishedAt, where it is credible.
     */
    public static final String PUBLIC_WINDOW = "PUBLIC_WINDOW";

    /** How far past the nominal close a release stamp may trail and still be the release. */
    public static final long RELEASE_SLACK_MS = 24L * 60 * 60 * 1000L;

    /**
     * How far BEFORE the nominal close a release stamp may run and still be the
     * release. The archive's shape is the release action running shortly before
     * the window opens -- a row written 11:30 for a 12:00 window, every week --
     * so a stamp minutes early is the release; one days early is the previous
     * issue's action, or a tag created ahead of time.
     */
    public static final long RELEASE_LEAD_MS = 3L * 60 * 60 * 1000L;

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
    public record Bounds(Date from, Date to, Long leadMillis, Long slackMillis) {

        /** No interval to check against -- an in-force issue, or the head of a chain. */
        public static final Bounds NONE = new Bounds(null, null, null, null);

        /** The original shape: after the open, up to a full period past the close. */
        public Bounds(Date from, Date to) {
            this(from, to, null, null);
        }

        /**
         * The release shape: the stamp must sit close to the nominal close -- no
         * more than {@code lead} before it and {@code slack} after it -- and after
         * the period opened.
         *
         * Measured at the holiday seams, both directions fail without this. Early:
         * a creation stamp a minute after the previous issue's on "uge 12-13 -
         * 2018", and next-tag stamps a full week early on the Christmas 2024 pair.
         * Late: an `updated` five days late on "EfS uge 14 - 2017", and the day the
         * clone was made on "uge 52 - 2024 og uge 1 - 2025". A full period of
         * slack after the close, with only the interval start as a floor, believed
         * all of them.
         */
        public static Bounds release(Date opened, Date nominalClose, long lead, long slack) {
            return new Bounds(opened, nominalClose, lead, slack);
        }

        /**
         * Whether a candidate could be this period's close.
         *
         * Strictly after the open, because a period cannot close before it begins.
         * Not earlier than the close minus the lead, where a lead is given. The
         * upper bound is the nominal close plus the slack -- a full period where
         * none is given, because the release action legitimately runs a little
         * AFTER the bound it closes at and a tight ceiling would reject the
         * ordinary case; a day where the caller knows the shape of the release.
         */
        boolean believable(Date candidate) {
            if (candidate == null) {
                return false;
            }
            if (from != null && !candidate.after(from)) {
                return false;
            }
            if (to != null && leadMillis != null && candidate.getTime() < to.getTime() - leadMillis) {
                return false;
            }
            if (to != null) {
                long slack = slackMillis != null ? slackMillis
                        : from != null ? to.getTime() - from.getTime() : Long.MAX_VALUE / 4;
                return candidate.getTime() <= to.getTime() + slack;
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
     * The cascade for a tiling issue whose nominal close is known.
     *
     * Same stages, same order, same bounds -- and when none of them produces a
     * believable release stamp the answer is the nominal close itself rather
     * than "a human decides". The period closed at that instant regardless of
     * what happened to the row afterwards; what is unknown is only the minute
     * the release action ran, and that is publishedAt's question, not this one.
     */
    public static Recovered recoverOrNominal(Publication legacy, Date nextTagCreated, Date coverDate,
                                             boolean released, Bounds bounds, Date nominalClose) {
        Recovered r = recover(legacy, nextTagCreated, coverDate, released, bounds);
        if (MANUAL.equals(r.source()) && nominalClose != null) {
            return new Recovered(nominalClose, NOMINAL_CLOSE, true);
        }
        return r;
    }

    /**
     * The cut-off of a yearly issue: the boundary its public window names.
     *
     * In-force lists (EfS A, Skydeområder) describe what was in force when the
     * year's edition took effect, so the cut-off is where the window OPENS.
     * Accumulated lists describe what was published during the year, so it is
     * where the window CLOSES. The caller passes the right end; this records the
     * provenance.
     */
    public static Recovered fromPublicWindow(Date boundary) {
        return boundary == null ? new Recovered(null, MANUAL, true)
                : new Recovered(boundary, PUBLIC_WINDOW, true);
    }

    /**
     * Whether a stamp is credible as the moment the release action ran.
     *
     * Only the stamps a stage actually believed as the release qualify. A
     * nominal close or a public-window boundary is not a moment anybody pressed
     * anything, and reporting it as one would be the laundering this class
     * exists to prevent.
     */
    public static boolean witnessesTheRelease(Recovered r) {
        return r != null && r.cutoff() != null
                && (FROM_UPDATED.equals(r.source()) || FROM_NEXT_TAG.equals(r.source())
                    || FROM_COVER.equals(r.source()));
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
