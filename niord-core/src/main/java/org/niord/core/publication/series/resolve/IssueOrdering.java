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

package org.niord.core.publication.series.resolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The order members are printed in, and the dense sortIndex that records it.
 *
 * Pure, and it reproduces the search's ORDER BY tuples exactly rather than
 * approximating them. That matters because the order IS the document: a reprint
 * that orders differently is a different publication, and nothing about it would
 * look broken.
 *
 * The id tie-breaker is on every branch for that reason. Without it, two members
 * equal on every other key come back in whatever order the database felt like,
 * and two runs of the same issue differ.
 */
public final class IssueOrdering {

    /** Where the sort came from. Recorded because "unset" and "explicitly AREA" are different facts. */
    public enum SortBy {
        AREA, PUBLISH_DATE, EVENT_DATE, FOLLOW_UP_DATE, ID
    }

    public enum Direction {
        ASC, DESC
    }

    /** A resolved sort, and where it came from. */
    public record SortSpec(SortBy sortBy, Direction direction, String source) {
    }

    /**
     * Everything the ORDER BY tuples read. Wider than MessageFacts on purpose:
     * membership and ordering need different things, and conflating them would
     * make the predicate carry fields it never looks at.
     */
    public record Orderable(
            String uid,
            Integer treeSortOrder,
            Double areaSortOrder,
            Integer year,
            Integer number,
            Integer id,
            Date publishDateFrom,
            Date eventDateFrom,
            Date followUpDate) {
    }

    /** Area-less messages sort last. This is the value the search substitutes for a null. */
    public static final int NULL_TREE_SORT_ORDER = 999_999;

    private IssueOrdering() {
    }

    /**
     * The fallback chain: an explicit series setting, then the series domain's
     * setting, then AREA/ASC.
     *
     * The domain value is a single string holding both parts, split on a space --
     * "AREA ASC". Treating it as just the field silently loses the direction.
     *
     * The domain here is the ISSUE'S SERIES domain, never the caller's
     * thread-local: a resolution running server-side has no caller domain, and
     * taking one would make the printed order depend on who triggered the run.
     */
    public static SortSpec resolveSort(String seriesSortBy, Direction seriesDirection,
                                       String seriesDomainSortOrder) {
        if (seriesSortBy != null && !seriesSortBy.isBlank()) {
            return new SortSpec(parseSortBy(seriesSortBy),
                    seriesDirection == null ? Direction.ASC : seriesDirection,
                    "series");
        }
        if (seriesDomainSortOrder != null && !seriesDomainSortOrder.isBlank()) {
            String[] parts = seriesDomainSortOrder.trim().split("\\s+");
            SortBy by = parseSortBy(parts[0]);
            Direction dir = parts.length > 1 && "DESC".equalsIgnoreCase(parts[1])
                    ? Direction.DESC : Direction.ASC;
            return new SortSpec(by, dir, "seriesDomain");
        }
        return new SortSpec(SortBy.AREA, Direction.ASC, "default");
    }

    private static SortBy parseSortBy(String raw) {
        return switch (raw.trim().toUpperCase()) {
            case "AREA" -> SortBy.AREA;
            case "PUBLISH_DATE", "PUBLISHDATE" -> SortBy.PUBLISH_DATE;
            case "EVENT_DATE", "EVENTDATE" -> SortBy.EVENT_DATE;
            case "FOLLOW_UP_DATE", "FOLLOWUPDATE" -> SortBy.FOLLOW_UP_DATE;
            case "ID" -> SortBy.ID;
            default -> throw new IllegalArgumentException("unknown sort field: " + raw);
        };
    }

    /** Orders members, reproducing the search's tuples for the chosen field. */
    public static List<Orderable> order(List<Orderable> members, SortSpec spec) {
        Comparator<Orderable> comparator = switch (spec.sortBy()) {
            case AREA -> Comparator
                    .comparingInt((Orderable o) -> o.treeSortOrder() == null
                            ? NULL_TREE_SORT_ORDER : o.treeSortOrder())
                    .thenComparing(o -> o.areaSortOrder() == null ? 0.0 : o.areaSortOrder())
                    .thenComparing(nullsLast(Orderable::year))
                    .thenComparing(nullsLast(Orderable::number));
            case PUBLISH_DATE -> Comparator.comparing(Orderable::publishDateFrom,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case EVENT_DATE -> Comparator.comparing(Orderable::eventDateFrom,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case FOLLOW_UP_DATE -> Comparator.comparing(Orderable::followUpDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case ID -> nullsLast(Orderable::id);
        };

        if (spec.direction() == Direction.DESC) {
            comparator = comparator.reversed();
        }

        // The tie-breaker sits OUTSIDE the reversal: it exists to make the order
        // deterministic, not to be part of what the user asked to sort by. Inside,
        // a descending sort would reverse it too and two equal members would still
        // be stable, but stability is not the point -- reproducibility is.
        comparator = comparator.thenComparing(nullsLast(Orderable::id));

        List<Orderable> out = new ArrayList<>(members);
        out.sort(comparator);
        return out;
    }

    private static <T extends Comparable<T>> Comparator<Orderable> nullsLast(
            java.util.function.Function<Orderable, T> key) {
        return Comparator.comparing(key, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Assigns a dense, zero-based sortIndex over the POST-OVERRIDE union.
     *
     * Over the union, not over the query result, because an override-included
     * member is ordered in place like any other -- appending it would put a
     * curator's addition at the end of the document regardless of where it
     * belongs (RI-10).
     */
    public static Map<String, Integer> assignSortIndex(List<Orderable> ordered) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int index = 0;
        for (Orderable o : ordered) {
            out.put(o.uid(), index++);
        }
        return out;
    }
}
