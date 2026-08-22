package org.niord.core.publication.series.criteria;

/**
 * Translates a legacy messageTagFilter string into the criteria shape it means.
 *
 * There are exactly FOUR distinct filter strings across the whole estate, plus
 * publications with no membership at all. That is why this is a lookup table and
 * not an expression parser: the legacy strings are FreeMarker-ish fragments, and
 * a parser would accept far more than five inputs while getting the semantics of
 * each no more right.
 *
 * Anything outside the table FAILS LOUDLY. A best-effort translation of an
 * unrecognised filter would produce a plausible-looking series with the wrong
 * membership, which is the worst available outcome -- worse than refusing to
 * import it and saying so.
 */
public final class LegacyFilterTranslator {

    /** What a legacy filter means, independent of which series it belongs to. */
    public record Translation(boolean hasMembership, boolean aliveAtCutoff, java.util.Set<String> messageTypes,
                              String note) {
    }

    private LegacyFilterTranslator() {
    }

    /**
     * The status conjunct in three of these strings is NOT translated into a
     * criterion. Status is a resolver invariant (RI-1, C-5) and storing it would
     * let an edit weaken it. It is dropped deliberately, and that is recorded
     * rather than silent.
     */
    public static Translation translate(String legacyFilter) {
        String f = legacyFilter == null ? "" : legacyFilter.trim();

        if (f.isEmpty()) {
            // The sticky regime: no filter at all. 160 weekly EfS publications,
            // 2017-01 to mid-2019.
            return new Translation(true, false, java.util.Set.of(),
                    "blank filter - the sticky regime; scope comes from the series alone");
        }

        if (f.equals("data.phase=='msg-status-change' && msg.status==PUBLISHED")) {
            // The phase guard is a recorder trigger, not a membership predicate:
            // it says WHEN the tag was written, not WHICH messages belong.
            return new Translation(true, false, java.util.Set.of(),
                    "phase guard is a recorder trigger, not membership; status conjunct is RI-1");
        }

        if (f.replaceAll("\s+", "").equals("msg.status==PUBLISHED")) {
            return new Translation(true, true, java.util.Set.of(),
                    "in-force-at-cutoff regime; status conjunct is RI-1");
        }

        if (f.replaceAll("\s+", "").equals("(msg.type==T||msg.type==P)&&msg.status==PUBLISHED")) {
            return new Translation(true, false, java.util.Set.of("TEMPORARY_NOTICE", "PRELIMINARY_NOTICE"),
                    "the P&T series; the disjunction becomes a set-valued messageType node");
        }

        throw new UnknownLegacyFilterException(f);
    }

    /** A filter string outside the four known ones. */
    public static class UnknownLegacyFilterException extends RuntimeException {
        public UnknownLegacyFilterException(String filter) {
            super("unrecognised legacy messageTagFilter, refusing to guess: [" + filter + "]. "
                    + "Exactly four distinct filters exist across the estate; a fifth means either new data "
                    + "or a wrong assumption, and both need a human.");
        }
    }
}
