package org.niord.core.publication.series.resolve;

import org.niord.core.publication.series.PublicationException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives an issue's numbers, and expands the name and file-name patterns.
 *
 * Two errors this exists to not reproduce.
 *
 * The issue is named for the ISO week of the EFFECTIVE CUT-OFF -- the end of the
 * window, not its start. Naming from the start produces "Uge 26+27, 2026" where
 * production says "EfS uge 27".
 *
 * And a week-numbered issue takes the ISO WEEK-year, not the calendar year.
 * Legacy pairs a correct ISO week with a calendar year read in the JVM default
 * zone, so a cut-off on 31 December 2025 comes out as "EfS uge 1 - 2025" when it
 * belongs to week 1 of 2026. Everything here derives in the series' own
 * timezone.
 *
 * The converse holds for a publication that is not numbered by week, and it is
 * the reason {@link YearBasis} exists rather than a single rule. An annual
 * edition closing at 31 December 23:59 names the year it CLOSES; answering with
 * the ISO week-year would name it for the following January, and the year is
 * part of its file name and therefore of its public download link.
 */
public final class IssueNaming {

    /**
     * The complete token vocabulary. Nothing else may declare a token, and the
     * API serves this constant rather than a copy -- a menu built from a second
     * list is a second source of truth.
     */
    public static final Set<String> TOKENS = new LinkedHashSet<>(List.of(
            "week", "week-2-digits",
            "weekTo", "weekTo-2-digits",
            "year", "year-2-digits",
            "month", "month-2-digits",
            "day", "day-2-digits",
            "edition"));

    /*
     * The zero-padded variants are IN.
     *
     * Production proves they are used -- nm-w01-2026 through nm-w34-2026 -- and
     * without them a file-name pattern that wants "uge 07" cannot be expressed at
     * all.
     *
     * The day tokens are in for a related reason. Without them a series
     * with cadence = DAILY has no way to name its issues, and shipping a cadence
     * that cannot produce a name is worse than either alternative. Adding tokens
     * is additive and costs nothing; removing DAILY from the cadence enum would
     * mean an ALTER TABLE on a native ENUM column later.
     */

    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]*)\\}");

    /** The numbers an issue derives from its cut-off. */
    public record Numbers(int week, Integer weekTo, int year, int month, int day, Integer edition) {
    }

    /**
     * Which year ${year} means.
     *
     * Declared here, as a plain choice, rather than taken from the series'
     * numbering scheme directly: this class knows about instants and patterns and
     * deliberately not about series settings, and the mapping from a scheme to a
     * basis is a publication rule that belongs beside the other publication rules.
     */
    public enum YearBasis {
        /** The ISO week-based year, which is what pairs correctly with an ISO week. */
        ISO_WEEK_YEAR,
        /** The calendar year the cut-off falls in, which is what an annual edition is named for. */
        CALENDAR_YEAR
    }

    private IssueNaming() {
    }

    /** A pattern referenced a token that does not exist. */
    public static class UnknownTokenException extends PublicationException {
        private final String token;

        public UnknownTokenException(String token) {
            super("UNKNOWN_TOKEN", "unknown token ${" + token + "}; the vocabulary is " + TOKENS);
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /**
     * Derives the numbers for an issue closing at the given cut-off.
     *
     * @param cutoff the EFFECTIVE cut-off -- the end of the window
     * @param intervalFrom the start, used only to detect a multi-week issue
     * @param zone the series' nominal cut-off timezone; never the JVM default
     * @param edition the edition number, where the scheme has one
     */
    public static Numbers derive(Date cutoff, Date intervalFrom, ZoneId zone, Integer edition) {
        return derive(cutoff, intervalFrom, zone, edition, YearBasis.ISO_WEEK_YEAR);
    }

    /**
     * Derives the numbers for an issue closing at the given cut-off.
     *
     * @param cutoff the EFFECTIVE cut-off -- the end of the window
     * @param intervalFrom the start, used only to detect a multi-week issue
     * @param zone the series' nominal cut-off timezone; never the JVM default
     * @param edition the edition number, where the scheme has one
     * @param yearBasis which year ${year} means for this publication
     */
    public static Numbers derive(Date cutoff, Date intervalFrom, ZoneId zone, Integer edition,
                                 YearBasis yearBasis) {
        if (cutoff == null) {
            throw new IllegalArgumentException("an issue always has a cut-off to derive from");
        }
        ZoneId z = zone == null ? ZoneId.of("UTC") : zone;
        ZonedDateTime end = Instant.ofEpochMilli(cutoff.getTime()).atZone(z);

        WeekFields iso = WeekFields.ISO;
        int week = end.get(iso.weekOfWeekBasedYear());

        // Two answers, and which one is right depends on what the publication is
        // numbered by. For a week-numbered issue it is the ISO week-BASED year: a
        // cut-off on 31 December can belong to week 1 of the following year, and
        // the calendar year would name it wrongly. For everything else it is the
        // calendar year the cut-off falls in -- an annual edition closing at 31
        // December 23:59 is the edition for THAT year, and the week-based answer
        // would name it for the January after it.
        int year = yearBasis == YearBasis.CALENDAR_YEAR
                ? end.getYear()
                : end.get(iso.weekBasedYear());

        Integer weekTo = null;
        if (intervalFrom != null) {
            // An issue is named for the week it CLOSED in. An ordinary weekly
            // window runs Wednesday to Wednesday and therefore straddles two ISO
            // weeks, so "the start falls in a different week" is true of every
            // ordinary week and names nothing. A multi-week issue -- "Uge 15+16",
            // the double week over a holiday -- is one whose window spans more
            // than one cadence PERIOD, and it is named for the weeks it closed:
            // the cut-off week and the ones before it that no issue closed.
            long days = (cutoff.getTime() - intervalFrom.getTime()) / 86_400_000L;
            // A quarter-period of tolerance, not rounding: a single week released up
            // to five days late is still one period, and only a window that has
            // genuinely swallowed most of a second one is named for two.
            long periods = (long) Math.floor(days / 7.0 + 0.25);
            if (periods >= 2) {
                ZonedDateTime firstClosed = end.minusWeeks(periods - 1);
                weekTo = week;
                week = firstClosed.get(iso.weekOfWeekBasedYear());
            }
        }

        return new Numbers(week, weekTo, year, end.getMonthValue(), end.getDayOfMonth(), edition);
    }

    /** The values each token expands to, for a given set of numbers. */
    public static Map<String, String> valuesOf(Numbers n) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("week", String.valueOf(n.week()));
        v.put("week-2-digits", pad(n.week()));
        v.put("weekTo", n.weekTo() == null ? "" : String.valueOf(n.weekTo()));
        v.put("weekTo-2-digits", n.weekTo() == null ? "" : pad(n.weekTo()));
        v.put("year", String.valueOf(n.year()));
        v.put("year-2-digits", pad(n.year() % 100));
        v.put("month", String.valueOf(n.month()));
        v.put("month-2-digits", pad(n.month()));
        v.put("day", String.valueOf(n.day()));
        v.put("day-2-digits", pad(n.day()));
        v.put("edition", n.edition() == null ? "" : String.valueOf(n.edition()));
        return v;
    }

    private static String pad(int value) {
        return String.format(Locale.ROOT, "%02d", value);
    }

    /**
     * Expands a pattern.
     *
     * S-14: nothing of the form ${...} may survive. Production serves a real PDF
     * at .../Skydeomraader-%24%7Byear%7D.pdf today because an unexpanded token
     * reached a file name and then a URL, so this is asserted rather than assumed.
     */
    public static String expand(String pattern, Numbers numbers) {
        if (pattern == null) {
            return null;
        }
        Map<String, String> values = valuesOf(numbers);

        Matcher m = TOKEN.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            if (!TOKENS.contains(token)) {
                // Loudly. A surviving token becomes part of a file name, and then
                // part of a public URL.
                throw new UnknownTokenException(token);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(token, "")));
        }
        m.appendTail(out);

        String expanded = out.toString();
        if (expanded.contains("${")) {
            throw new IllegalStateException(
                    "a token survived expansion in [" + expanded + "]; this is how a literal ${year} "
                            + "reaches a published file name");
        }
        return expanded;
    }

    /**
     * The one token that is expanded LATER, by the citation layer.
     *
     * A citation format reads "EfS ${week}/${year} ${parameters}": the naming
     * tokens belong to the issue and are fixed the moment it is published, but
     * ${parameters} is whatever the editor types at the moment of citing. It
     * cannot be resolved here, and it must not be treated as unknown.
     */
    public static final String DEFERRED_TOKEN = "parameters";

    /**
     * Expands a citation format, leaving ${parameters} for the citation layer.
     *
     * Separate from expand() rather than a flag on it, because for every OTHER
     * use a surviving ${...} is a bug -- production serves a real PDF at
     * .../Skydeomraader-%24%7Byear%7D.pdf because an unexpanded token reached a
     * file name and then a URL. This is the single place where one surviving
     * token is correct, and it survives by name.
     */
    public static String expandCitation(String pattern, Numbers numbers) {
        if (pattern == null) {
            return null;
        }
        Map<String, String> values = valuesOf(numbers);

        Matcher m = TOKEN.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            if (DEFERRED_TOKEN.equals(token)) {
                // Put it back verbatim.
                m.appendReplacement(out, Matcher.quoteReplacement("${" + DEFERRED_TOKEN + "}"));
                continue;
            }
            if (!TOKENS.contains(token)) {
                throw new UnknownTokenException(token);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(token, "")));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * True when every token in the pattern is one this vocabulary declares.
     *
     * STRICT. ${parameters} is NOT accepted here, and that is the point: this
     * validates file-name, name and link patterns, where a surviving token
     * becomes part of a public URL. Production already serves a real PDF at
     * .../Skydeomraader-%24%7Byear%7D.pdf because one did.
     *
     * Citation formats are validated by {@link #isCitationExpandable} instead.
     */
    public static boolean isExpandable(String pattern) {
        return expandableWith(pattern, false);
    }

    /**
     * True when a CITATION format is expandable -- ${parameters} included.
     *
     * Separate from {@link #isExpandable} because the two answers genuinely
     * differ, and conflating them breaks in one direction or the other: reject
     * ${parameters} here and no citable series can be configured at all, since
     * S-13 requires a reference format for any series that may be cited and the
     * legacy convention puts ${parameters} in it. Accept it there and it reaches
     * a file name.
     */
    public static boolean isCitationExpandable(String pattern) {
        return expandableWith(pattern, true);
    }

    private static boolean expandableWith(String pattern, boolean allowDeferred) {
        if (pattern == null) {
            return true;
        }
        Matcher m = TOKEN.matcher(pattern);
        while (m.find()) {
            String token = m.group(1);
            if (allowDeferred && DEFERRED_TOKEN.equals(token)) {
                continue;
            }
            if (!TOKENS.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
