/*
 * Copyright 2026 Danish Maritime Authority.
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

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.CutoffDefault;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The four-stage cut-off recovery cascade.
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

    /**
     * Stage 1: the row's own last-write time, taken as the release.
     *
     * Also what an in-force annual records when a credible release stamp decided
     * which DAY its cut-off falls on -- see {@link #forAnnualInForce}. There the
     * stamp is the later of the row's last-write time and the row's OWN tag
     * creation, and this value covers both.
     *
     * COVERING BOTH IS DELIBERATE, AND IT IS THE HONEST OPTION AVAILABLE. There
     * is no value in this vocabulary that names an issue's own tag creation:
     * FROM_NEXT_TAG is the SUCCESSOR's tag, which is a different event with the
     * opposite meaning, and FROM_COVER is a date printed on a PDF. Minting a new
     * one is not free either -- the set is pinned by the readers that render it.
     * So both candidates report the stage they share, which is "a stamp on this
     * row, believed as its release", and the distinction between the two is not
     * recoverable from the column afterwards.
     */
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
     *
     * For an IN-FORCE annual this is the fallback rather than the rule: a
     * credible release stamp on a later day wins, and the row then records
     * FROM_UPDATED instead. See {@link #forAnnualInForce}.
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
     * The cut-off of an ANNUAL IN-FORCE issue: the END of the LATER of two days
     * -- the day its window opens, and the day it was released -- in the series'
     * own zone.
     *
     * WHY A DAY AND NOT THE INSTANT THE WINDOW OPENED. The changeover is a day's
     * work, not a moment's: the previous year's notices are cancelled and the new
     * year's published in the same sitting, and the window is opened somewhere in
     * the middle of it. Measured on "EfS A - 2025": the window opened at 10:28:17,
     * the 2024 notices were cancelled at 11:18 and the 2025 notices published at
     * 11:28. Resolving at 10:28:17 therefore produced the 2024 list -- 29 members
     * missing and 29 extra against the tag that holds the 2025 list.
     *
     * WHY THE LATER OF THE TWO DAYS AND NOT SIMPLY THE WINDOW'S. The window is
     * opened during the sitting on some editions and named nominally on others,
     * at the turn of the year, while the sitting happens weeks afterwards.
     * "Skydeområder 2025" is the second kind: window from 1 January, the outgoing
     * set cancelled and the incoming set published on 7 February, released on 26
     * February. At the end of 1 January none of that had happened, and the
     * edition resolved to the previous year's list -- 32 members missing and 30
     * extra. What the edition means is what was in force at the end of the day it
     * was released, and the window-open day is only the same answer when the two
     * are the same day.
     *
     * WHICH RELEASE STAMPS COUNT. Only one that is credible as the release: the
     * caller passes the stamp it already believed enough to record as the issue's
     * publication moment, and null where nothing was credible. A stamp written
     * years after the window closed is an edit, and an edit decides nothing.
     * Without a credible stamp the window-open day stands.
     *
     * WEEKLY IN-FORCE IS UNTOUCHED. A weekly release has a credible release stamp
     * minutes from its nominal close, and the cascade uses it; there is no
     * day-long changeover to contain. This applies only where the year itself is
     * the period.
     *
     * PROVENANCE FOLLOWS THE BRANCH. Where the release stamp decided the day the
     * answer was read off something that witnessed the release, so it records
     * FROM_UPDATED; where the window-open day stood it was read off the calendar,
     * so it records PUBLIC_WINDOW. What is recorded is still a DAY in both cases
     * -- the release instant is kept separately, as publishedAt, and is never
     * this.
     *
     * The instant itself comes from {@link CutoffDefault#annualInForceCutoff},
     * which is where the native publish default reads it from too -- so an
     * edition this import recovers and one an admin publishes describe the same
     * instant.
     */
    public static Recovered forAnnualInForce(Date windowOpen, Date release, java.time.ZoneId zone) {
        Date cutoff = CutoffDefault.annualInForceCutoff(windowOpen, release, zone);
        if (cutoff == null) {
            return new Recovered(null, MANUAL, true);
        }
        return CutoffDefault.releaseDayIsLater(windowOpen, release, zone)
                ? new Recovered(cutoff, FROM_UPDATED, true)
                : new Recovered(cutoff, PUBLIC_WINDOW, true);
    }

    /**
     * Whether the SOURCE names something that witnessed the release action.
     *
     * Only the stages that read a stamp qualify. A nominal close or a
     * public-window boundary is not a moment anybody pressed anything, and
     * reporting it as one would be the laundering this class exists to prevent.
     *
     * IT IS THE PROVENANCE THAT IS BEING ASKED ABOUT, NOT THE INSTANT. For every
     * stage of the cascade the recovered cut-off IS the stamp, so the two amount
     * to the same thing; for an annual in-force edition the cut-off is the end of
     * the release DAY and the stamp is hours earlier, so the release moment has
     * to come from the stamp rather than from here.
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
     * When the edition at position i STOPPED being the current one: the earliest
     * later tag in its chain that was assembled after this edition's window
     * opened.
     *
     * WHAT IT IS FOR. A write at or after that moment is the edition's
     * WITHDRAWAL, not its release -- the changeover of an annual is one sitting,
     * and it deactivates the outgoing edition minutes after it assembles the
     * incoming one. Measured on the 2022 firing editions: the incoming tag was
     * created 14:46:50 and the outgoing row was last written 14:52:17, six
     * minutes later. Believed as a release, that write dates the OUTGOING edition
     * to the day its replacement went out, and the pair then sorts with the
     * replacement first.
     *
     * WHAT COUNTS AS A REPLACEMENT: a later entry that took over DURING this
     * edition's window, i.e. one whose own window opens inside it. That is the
     * re-edition shape -- a second 2022 firing edition opening on the same day as
     * the first, a second 2017 EfS A edition opening in March of the year the
     * first was still running -- and it is the only shape that ends an edition
     * early.
     *
     * NEXT PERIOD IS NOT A REPLACEMENT, and this is the clause that costs the
     * most to get wrong. The 2027 firing row's tag was created on 2 January 2026,
     * the very instant the 2026 edition was released; read as the 2026 edition's
     * replacement it rejects that edition's own release stamp and leaves it with
     * no publication moment at all. Three more rows behave the same way. Each
     * year's edition ends where the next year's WINDOW opens, not where somebody
     * first assembled a list for it.
     *
     * NOT SIMPLY {@link #nextTagCreated} either. Between the two 2022 firing
     * editions sits a third row whose title never had its year substituted,
     * carrying a tag created in January 2020 -- two years before either edition's
     * window. Taken as the bound it rejects everything, including the outgoing
     * edition's OWN tag, which is plainly its release. A tag made before this
     * edition's window even opened cannot be the moment this edition was
     * replaced, so it is skipped and the scan goes on to the first one that
     * could be.
     *
     * An edition with no stated window end keeps only that second guard: there is
     * no span for a successor to open inside, so any later tag created after this
     * window opened is taken as the bound.
     *
     * Returns null when nothing later qualifies -- the newest edition of a chain,
     * and every edition that simply ran its year out, has not been replaced. That
     * is an answer rather than a gap.
     */
    public static Date replacedAt(List<Publication> chainOrdered, int i,
                                  Date windowOpen, Date windowClose) {
        if (chainOrdered == null || i < 0 || windowOpen == null) {
            return null;
        }
        Date earliest = null;
        for (int j = i + 1; j < chainOrdered.size(); j++) {
            Publication later = chainOrdered.get(j);
            Date created = later.getMessageTag() == null ? null : later.getMessageTag().getCreated();
            if (created == null || !created.after(windowOpen)) {
                continue;
            }
            if (windowClose != null) {
                Date opens = later.getPublishDateFrom();
                if (opens == null || opens.before(windowOpen) || opens.after(windowClose)) {
                    continue;
                }
            }
            if (earliest == null || created.before(earliest)) {
                earliest = created;
            }
        }
        return earliest;
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
