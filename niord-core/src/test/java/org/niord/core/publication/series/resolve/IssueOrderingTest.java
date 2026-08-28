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

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.BindsRule;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The print order and its sortIndex. Pure, no database. */
public class IssueOrderingTest {

    private static IssueOrdering.Orderable m(String uid, Integer tree, Double areaSort,
                                             Integer year, Integer number, int id) {
        return new IssueOrdering.Orderable(uid, tree, areaSort, year, number, id, null, null, null);
    }

    // -------------------------------------------------------- the fallback chain

    @Test
    public void theSortFallsBackThroughSeriesThenDomainThenArea() {
        IssueOrdering.SortSpec explicit =
                IssueOrdering.resolveSort("PUBLISH_DATE", IssueOrdering.Direction.DESC, "AREA ASC");
        assertEquals(IssueOrdering.SortBy.PUBLISH_DATE, explicit.sortBy());
        assertEquals(IssueOrdering.Direction.DESC, explicit.direction());
        assertEquals("series", explicit.source(), "an explicit series setting must win");

        IssueOrdering.SortSpec fromDomain = IssueOrdering.resolveSort(null, null, "AREA DESC");
        assertEquals(IssueOrdering.SortBy.AREA, fromDomain.sortBy());
        assertEquals(IssueOrdering.Direction.DESC, fromDomain.direction(),
                "the domain value holds BOTH parts in one string; reading only the field loses the direction");
        assertEquals("seriesDomain", fromDomain.source());

        IssueOrdering.SortSpec fallback = IssueOrdering.resolveSort(null, null, null);
        assertEquals(IssueOrdering.SortBy.AREA, fallback.sortBy());
        assertEquals(IssueOrdering.Direction.ASC, fallback.direction());
        assertEquals("default", fallback.source());

        // A domain value with no direction is ASC, not an error.
        assertEquals(IssueOrdering.Direction.ASC,
                IssueOrdering.resolveSort(null, null, "AREA").direction());
    }

    // ------------------------------------------------------- the AREA tuple

    /**
     * Area-less messages sort last, and among themselves by id.
     *
     * Without the id tie-breaker their relative order is whatever the database
     * felt like, and two runs of the same issue produce different documents --
     * which nothing would flag, because both are individually correct.
     */
    @BindsRule({"RI-9"})
    @Test
    public void areaLessMessagesSortLastAndInIdOrder() {
        List<IssueOrdering.Orderable> members = new ArrayList<>(List.of(
                m("no-area-b", null, null, 2026, 5, 200),
                m("has-area", 10, 1.0, 2026, 1, 50),
                m("no-area-a", null, null, 2026, 5, 100)));

        List<IssueOrdering.Orderable> ordered =
                IssueOrdering.order(members, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.ASC, "test"));

        assertEquals(List.of("has-area", "no-area-a", "no-area-b"),
                ordered.stream().map(IssueOrdering.Orderable::uid).toList(),
                "area-less members must sort last, then by id");
    }

    @Test
    public void theAreaTupleOrdersByTreeThenAreaSortThenYearThenNumber() {
        List<IssueOrdering.Orderable> members = new ArrayList<>(List.of(
                m("d", 10, 2.0, 2026, 1, 4),
                m("b", 10, 1.0, 2026, 2, 2),
                m("a", 10, 1.0, 2026, 1, 1),
                m("c", 5, 9.0, 2026, 9, 3)));

        List<String> order = IssueOrdering.order(members, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.ASC, "test"))
                .stream().map(IssueOrdering.Orderable::uid).toList();

        assertEquals(List.of("c", "a", "b", "d"), order);
    }

    // ---------------------------------------------------------- reproducibility

    @Test
    public void theOrderIsStableAcrossRuns() {
        List<IssueOrdering.Orderable> members = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            // Deliberately identical on every key except id.
            members.add(m("uid-" + i, null, null, null, null, 1000 - i));
        }

        List<String> first = IssueOrdering.order(members, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.ASC, "test"))
                .stream().map(IssueOrdering.Orderable::uid).toList();

        java.util.Collections.shuffle(members, new java.util.Random(42));

        List<String> second = IssueOrdering.order(members, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.ASC, "test"))
                .stream().map(IssueOrdering.Orderable::uid).toList();

        assertEquals(first, second,
                "the order changed when the input order changed; a reprint would differ from the original");
    }

    // ------------------------------------------------------------- sortIndex

    @BindsRule({"RI-11"})

    @Test
    public void sortIndexIsDenseZeroBasedAndUnique() {
        List<IssueOrdering.Orderable> members = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            members.add(m("uid-" + i, i, 0.0, 2026, i, i));
        }

        Map<String, Integer> index = IssueOrdering.assignSortIndex(
                IssueOrdering.order(members, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.ASC, "test")));

        assertEquals(25, index.size());
        assertEquals(0, index.values().stream().min(Integer::compare).orElseThrow(), "not zero-based");
        assertEquals(24, index.values().stream().max(Integer::compare).orElseThrow(), "not dense");
        assertEquals(25, index.values().stream().distinct().count(), "indices are not unique");
    }

    /**
     * RI-10. A curator's addition is ordered in place, not appended.
     *
     * Appending would put it at the end of the printed document regardless of
     * where it belongs, which is visible to every reader and correct nowhere.
     */
    @BindsRule({"M-2"})
    @Test
    public void anOverrideIncludedMemberIsOrderedInPlace() {
        List<IssueOrdering.Orderable> queryMatched = new ArrayList<>(List.of(
                m("first", 1, 0.0, 2026, 1, 1),
                m("third", 3, 0.0, 2026, 3, 3)));

        // The union, as the caller builds it: query result plus the override.
        List<IssueOrdering.Orderable> union = new ArrayList<>(queryMatched);
        union.add(m("second-by-hand", 2, 0.0, 2026, 2, 2));

        List<String> order = IssueOrdering.order(union, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.ASC, "test"))
                .stream().map(IssueOrdering.Orderable::uid).toList();

        assertEquals(List.of("first", "second-by-hand", "third"), order,
                "the manually added member was not ordered in place");
        assertNotEquals("second-by-hand", order.get(order.size() - 1),
                "it was appended at the end instead of sorted into position");
    }

    @Test
    public void descendingReversesTheKeysButKeepsTheOrderDeterministic() {
        List<IssueOrdering.Orderable> members = new ArrayList<>(List.of(
                m("a", 1, 0.0, 2026, 1, 1),
                m("b", 2, 0.0, 2026, 2, 2),
                m("c", 3, 0.0, 2026, 3, 3)));

        List<String> desc = IssueOrdering.order(members, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.DESC, "test"))
                .stream().map(IssueOrdering.Orderable::uid).toList();

        assertEquals(List.of("c", "b", "a"), desc);

        // Still deterministic when every key ties.
        List<IssueOrdering.Orderable> tied = new ArrayList<>(List.of(
                m("x", null, null, null, null, 2),
                m("y", null, null, null, null, 1)));
        assertEquals(
                IssueOrdering.order(tied, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.DESC, "test")),
                IssueOrdering.order(tied, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.AREA, IssueOrdering.Direction.DESC, "test")));
    }

    @Test
    public void publishDateOrderingUsesTheDateNotTheDay() {
        Date base = new Date(1_700_000_000_000L);
        List<IssueOrdering.Orderable> members = new ArrayList<>(List.of(
                new IssueOrdering.Orderable("later", null, null, null, null, 1,
                        new Date(base.getTime() + 1000), null, null),
                new IssueOrdering.Orderable("earlier", null, null, null, null, 2,
                        base, null, null)));

        List<String> order = IssueOrdering.order(members, new IssueOrdering.SortSpec(
                        IssueOrdering.SortBy.PUBLISH_DATE, IssueOrdering.Direction.ASC, "test"))
                .stream().map(IssueOrdering.Orderable::uid).toList();

        assertEquals(List.of("earlier", "later"), order,
                "a one-second difference did not order; the comparison is snapping to the day");
        assertTrue(true);
    }
}
