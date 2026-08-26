package org.niord.core.publication.series.resolve;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Gap detection, UPCOMING, and dormancy.
 *
 * All of it is GATED, and the gate is the interesting part. Gap detection only
 * makes sense where issues tile -- one ends, the next begins. Issues of an
 * IN_FORCE_AT_CUTOFF series OVERLAP: the 2026 and 2027 firing-areas issues share
 * 31 of their 32 members. Asking which year is "missing" between them is a
 * category error, and answering it produces a MISSING pseudo-row, a retro-create
 * affordance and a warning for something that was never absent.
 *
 * Dormancy is DERIVED, never stored. "We deliberately stopped" is RETIRED, which
 * is a decision and belongs in a column. "Nobody got round to it" is an
 * observation about the calendar, and an observation that is stored goes stale
 * the moment the calendar moves on.
 */
public final class GapDetection {

    /** Missed cadence periods before a series is considered dormant. */
    public static final int DORMANCY_PERIODS = 3;

    /**
     * Why gap detection did or did not run, as something an API can render.
     *
     * The prose says it once, for a human reading a log or a diagnostic. The code
     * is what a caller switches on and what a UI translates -- a client matching
     * on the sentence would break the first time the sentence is improved.
     */
    public enum Reason {
        /** The gate is open. */
        TILING_SERIES,
        /** Issues overlap rather than tile, so a missing period is a category error. */
        RELATION_NOT_TILING,
        /** A one-off has no period to be missing. */
        NO_CADENCE,
        /** Only an ACTIVE series is expected to keep producing. */
        SERIES_NOT_ACTIVE,
        /** Already flagged dormant; per-period warnings would bury that. */
        SERIES_DORMANT,
        /** A cadence whose period length could not be computed. */
        CADENCE_PERIOD_UNKNOWN
    }

    /** Why gap detection did or did not run. Carried so the answer is explainable. */
    public record Gate(boolean enabled, Reason code, String reason) {
    }

    /** A period with no issue. */
    public record Gap(Date from, Date to, int index) {
    }

    private GapDetection() {
    }

    /**
     * Whether gap detection runs at all.
     *
     * @param relation the series' time relation
     * @param cadence the cadence, or null for a one-off
     * @param active whether the series is ACTIVE
     * @param dormant whether it is dormant, derived
     */
    public static Gate gate(TimeRelation relation, String cadence, boolean active, boolean dormant) {
        // CADENCE FIRST, and the order is the whole point. S-1 leaves a
        // cadence-less series with no time relation at all, so asking about the
        // relation first answered every cadence-less series with
        // RELATION_NOT_TILING -- telling an admin that "issues of an
        // IN_FORCE_AT_CUTOFF series overlap" about a publication that has no
        // relation and no schedule. NO_CADENCE described exactly that series and
        // could never be reached by one.
        if (cadence == null || "NONE".equals(cadence)) {
            return new Gate(false, Reason.NO_CADENCE,
                    "this publication has no cadence, so there is no period it can be missing");
        }
        if (relation != TimeRelation.PUBLISHED_IN_INTERVAL) {
            return new Gate(false, Reason.RELATION_NOT_TILING,
                    "issues of an IN_FORCE_AT_CUTOFF series overlap rather than tile, so a missing period "
                            + "is a category error rather than a gap");
        }
        if (!active) {
            return new Gate(false, Reason.SERIES_NOT_ACTIVE,
                    "only an ACTIVE series is expected to keep producing issues");
        }
        if (dormant) {
            return new Gate(false, Reason.SERIES_DORMANT,
                    "a dormant series is already flagged as such; warning about every period since would "
                            + "bury the one fact that matters");
        }
        return new Gate(true, Reason.TILING_SERIES, "an active, tiling series with a cadence");
    }

    /**
     * The same gate, refusing to run when the cadence has no computable period.
     *
     * Not defensive padding. Adding a cadence constant without teaching
     * periodMillisOf about it yields a period of zero, and a zero period makes the
     * synthesizer return an empty list -- which a caller reporting the count alone
     * renders as "0 gaps" for a series nothing examined. That reads exactly like a
     * clean one, and it is the same confusion the reason code exists to prevent, so
     * the branch is here rather than left to whoever adds the constant.
     */
    public static Gate gate(TimeRelation relation, String cadence, boolean active, boolean dormant,
                            long periodMillis) {
        Gate gate = gate(relation, cadence, active, dormant);
        if (gate.enabled() && periodMillis <= 0) {
            return new Gate(false, Reason.CADENCE_PERIOD_UNKNOWN,
                    "the length of one " + cadence + " period could not be computed, so there is no "
                            + "period to call missing");
        }
        return gate;
    }

    /**
     * The periods between consecutive issues where one is missing.
     *
     * Returns empty whenever the gate is closed, so a caller that forgets to
     * check still cannot produce a pseudo-row for an overlapping series.
     */
    public static List<Gap> gaps(Gate gate, List<Date> cutoffsAscending, long periodMillis) {
        List<Gap> out = new ArrayList<>();
        if (!gate.enabled() || cutoffsAscending == null || cutoffsAscending.size() < 2) {
            return out;
        }

        for (int i = 1; i < cutoffsAscending.size(); i++) {
            long previous = cutoffsAscending.get(i - 1).getTime();
            long current = cutoffsAscending.get(i).getTime();
            long elapsed = current - previous;

            // A period and a half of slack: real releases drift by hours, and a
            // tighter bound would report a gap every time somebody published late.
            long missing = Math.round((double) elapsed / periodMillis) - 1;
            for (int k = 0; k < missing; k++) {
                long from = previous + (k + 1) * periodMillis;
                out.add(new Gap(new Date(from), new Date(from + periodMillis), out.size()));
            }
        }
        return out;
    }

    /**
     * Whether a series is dormant: nothing published for DORMANCY_PERIODS
     * cadence periods.
     *
     * For a weekly series that is three missed weeks -- long enough that a
     * holiday or a one-week slip does not raise it, short enough that a genuinely
     * abandoned series surfaces within a month.
     */
    public static boolean isDormant(Date lastIssueCutoff, Date now, long periodMillis) {
        if (lastIssueCutoff == null || now == null || periodMillis <= 0) {
            return false;
        }
        return now.getTime() - lastIssueCutoff.getTime() > DORMANCY_PERIODS * periodMillis;
    }

    /** The next nominal cut-off after the last issue: the UPCOMING row. */
    public static Date nextCutoff(Date lastIssueCutoff, long periodMillis) {
        return lastIssueCutoff == null ? null : new Date(lastIssueCutoff.getTime() + periodMillis);
    }

    /** Milliseconds in one period of the given cadence. */
    public static long periodMillisOf(String cadence, ZoneId zone, Date around) {
        if (cadence == null) {
            return 0L;
        }
        return switch (cadence) {
            case "DAILY" -> 24L * 3600_000L;
            case "WEEKLY" -> 7L * 24L * 3600_000L;
            case "MONTHLY" -> monthMillis(zone, around);
            case "YEARLY" -> yearMillis(zone, around);
            default -> 0L;
        };
    }

    /* Months and years are not fixed lengths, so they are measured around the
     * date in question rather than approximated by an average. */
    private static long monthMillis(ZoneId zone, Date around) {
        ZonedDateTime t = at(zone, around);
        return t.plusMonths(1).toInstant().toEpochMilli() - t.toInstant().toEpochMilli();
    }

    private static long yearMillis(ZoneId zone, Date around) {
        ZonedDateTime t = at(zone, around);
        return t.plusYears(1).toInstant().toEpochMilli() - t.toInstant().toEpochMilli();
    }

    private static ZonedDateTime at(ZoneId zone, Date around) {
        return (around == null ? ZonedDateTime.now(zone == null ? ZoneId.of("UTC") : zone)
                : ZonedDateTime.ofInstant(around.toInstant(), zone == null ? ZoneId.of("UTC") : zone));
    }
}
