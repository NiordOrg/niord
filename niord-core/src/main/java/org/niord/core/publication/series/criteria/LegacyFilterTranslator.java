/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.core.publication.series.criteria;

import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.List;

/**
 * Translates a legacy messageTagFilter string into the criteria shape it means.
 *
 * There are exactly FOUR distinct filter shapes across the whole estate, carried
 * in FIVE distinct column values -- the blank shape exists as both NULL and the
 * empty string. That is why this is a lookup table and not an expression parser:
 * the legacy strings are FreeMarker-ish fragments, and a parser would accept far
 * more than five inputs while getting the semantics of each no more right.
 *
 * THE TABLE MATCHES STORED BYTES. An earlier version of this file matched the
 * abbreviations the consent document uses -- msg.type==T, msg.status==PUBLISHED
 * -- against an estate that stores msg.type == Type.TEMPORARY_NOTICE and
 * msg.status == Status.PUBLISHED. Every one of those keys missed, so all 917
 * filter-carrying publications would have failed the import, and the unit tests
 * agreed with the code because they hand-typed the same abbreviations. The four
 * strings now live in a resource generated from the captured estate
 * (fixtures/legacy-estate/message-tag-filters.json) and the test drives this
 * table from that resource rather than from anything typed by hand.
 *
 * ORDER IS PART OF THE ANSWER. messageTypes is a List because the values are
 * written into a stored criteria document and compared byte-for-byte by the
 * export round trip and by the importer's byte-identical dry run. Set.of randomises its
 * iteration order per JVM run, so a set here made two imports of one estate
 * produce two different documents. The order is the legacy filter's own.
 *
 * Anything outside the table FAILS LOUDLY. A best-effort translation of an
 * unrecognised filter would produce a plausible-looking series with the wrong
 * membership, which is the worst available outcome -- worse than refusing to
 * import it and saying so.
 */
public final class LegacyFilterTranslator {

    /** Which of the four shapes a filter is. Named so a report can name it. */
    public enum Shape {
        /** No filter at all: NULL or the empty string. The sticky regime. */
        BLANK,
        /** The recorder trigger: data.phase == 'msg-status-change'. */
        PHASE,
        /** Status alone: the in-force-at-cut-off regime. */
        STATUS,
        /** The P&T disjunction over message type. */
        TYPE_AND_STATUS
    }

    /**
     * What a legacy filter means, independent of which series it belongs to.
     *
     * timeRelation and aliveAtCutoff travel together and are derived HERE rather
     * than read off the owning series, because a series outlives its own filter:
     * four templates sit behind both the blank era and the phase era, so the
     * series row says aliveAtCutoff = true while 122 of that same series' own
     * issues need false.
     */
    public record Translation(Shape shape, boolean hasMembership, TimeRelation timeRelation,
                              boolean aliveAtCutoff, List<String> messageTypes, String note) {
    }

    private LegacyFilterTranslator() {
    }

    /**
     * The declared normalisation, and the whole of it: trim, then collapse runs
     * of whitespace to a single space. Case-sensitive, no parsing, no prefix
     * stripping, no fuzzy matching.
     *
     * Runs collapse to one space rather than to nothing, so that token
     * boundaries survive -- "a b" must not normalise onto "ab".
     */
    static String normalise(String filter) {
        return filter == null ? "" : filter.trim().replaceAll("\\s+", " ");
    }

    // The four shapes, spelled as the estate spells them. Normalised at compare
    // time, so the spacing here is the stored spacing and can be read against
    // the resource directly.
    private static final String PHASE =
            "data.phase == 'msg-status-change' && msg.status == Status.PUBLISHED";
    private static final String STATUS =
            "msg.status == Status.PUBLISHED";
    private static final String TYPE_AND_STATUS =
            "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) "
                    + "&& msg.status == Status.PUBLISHED";

    /**
     * The status conjunct in three of these strings is NOT translated into a
     * criterion. Status is a resolver invariant (RI-1, C-5) and storing it would
     * let an edit weaken it. It is dropped deliberately, and that is recorded
     * rather than silent.
     */
    public static Translation translate(String legacyFilter) {
        String f = normalise(legacyFilter);

        if (f.isEmpty()) {
            // NULL and the empty string are two distinct column values and one
            // shape. Keyed on exact equality the empty string would be an unknown
            // sixth case failing loudly on a live ACTIVE publication; normalised
            // it reads as what it is, a filter that selects on nothing.
            return new Translation(Shape.BLANK, true, TimeRelation.PUBLISHED_IN_INTERVAL, false,
                    List.of(), "blank filter - the sticky regime; scope comes from the series alone");
        }

        if (f.equals(normalise(PHASE))) {
            // The phase guard is a recorder trigger, not a membership predicate:
            // it says WHEN the tag was written, not WHICH messages belong.
            return new Translation(Shape.PHASE, true, TimeRelation.PUBLISHED_IN_INTERVAL, true,
                    List.of(), "phase guard is a recorder trigger, not membership; status conjunct is RI-1");
        }

        if (f.equals(normalise(STATUS))) {
            return new Translation(Shape.STATUS, true, TimeRelation.IN_FORCE_AT_CUTOFF, true,
                    List.of(), "in-force-at-cutoff regime; status conjunct is RI-1");
        }

        if (f.equals(normalise(TYPE_AND_STATUS))) {
            return new Translation(Shape.TYPE_AND_STATUS, true, TimeRelation.IN_FORCE_AT_CUTOFF, true,
                    List.of("TEMPORARY_NOTICE", "PRELIMINARY_NOTICE"),
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
