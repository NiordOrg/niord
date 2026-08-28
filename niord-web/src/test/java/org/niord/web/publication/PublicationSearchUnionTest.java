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

package org.niord.web.publication;

import org.junit.jupiter.api.Test;
import org.niord.web.PublicationRestService;
import org.niord.model.search.PagedSearchResultVo;
import org.niord.model.publication.PublicationVo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The publication picker's two halves, merged.
 *
 * An issue is RESOLVABLE by id and was not FINDABLE. PublicationResolver checks
 * the new model before the legacy one, so a citation into an imported id keeps
 * working after cutover -- but /publications/search read the legacy table alone,
 * so an issue with no legacy twin never appeared in the picker. Verified against
 * the deployed API before this was written: a new issue answers on
 * /publications/publication/{id} and returns zero hits from /publications/search.
 *
 * That is not a cosmetic gap. Every issue created AFTER cutover has no legacy
 * twin, which would make the current week's EfS the one publication an editor
 * cannot cite.
 */
public class PublicationSearchUnionTest {

    private static PublicationVo pub(String id) {
        PublicationVo vo = new PublicationVo();
        vo.setPublicationId(id);
        return vo;
    }

    private static PagedSearchResultVo<PublicationVo> legacy(PublicationVo... rows) {
        PagedSearchResultVo<PublicationVo> out = new PagedSearchResultVo<>();
        out.setData(new ArrayList<>(List.of(rows)));
        out.setTotal((long) rows.length);
        out.setSize(rows.length);
        return out;
    }

    /**
     * Issues come first, because that is the order a citation resolves in.
     *
     * Offering the legacy row while the id resolves to the issue would show one
     * title and cite another.
     */
    @Test
    public void issuesLeadTheResults() {
        PublicationVo issue = pub("issue-1");
        var merged = PublicationRestService.merge(
                List.of(issue), List.of("issue-1"), legacy(pub("legacy-1")), 0, 100);

        assertEquals(2, merged.getData().size());
        assertSame(issue, merged.getData().get(0), "the issue half must lead");
        assertEquals("legacy-1", merged.getData().get(1).getPublicationId());
    }

    /**
     * A legacy row an issue has taken over is dropped, not shown twice.
     *
     * An IMPORTED issue borrows its legacy row's id, so the two halves collide by
     * design -- every imported issue in the estate has a twin. Without the dedupe
     * the picker shows each of them twice.
     */
    @Test
    public void acollidingLegacyRowIsDropped() {
        PublicationVo issue = pub("shared-id");
        var merged = PublicationRestService.merge(
                List.of(issue), List.of("shared-id"),
                legacy(pub("shared-id"), pub("legacy-only")), 0, 100);

        assertEquals(2, merged.getData().size(),
                "the legacy twin of an imported issue must not appear alongside it");
        assertSame(issue, merged.getData().get(0));
        assertEquals("legacy-only", merged.getData().get(1).getPublicationId());
    }

    /**
     * The page is taken out of the MERGED order, never out of each half.
     *
     * Truncating first is the failure the public adapter documents: two halves
     * that each truncate independently produce a merge of two wrong lists. Here
     * three rows exist, two are asked for, and the two that survive must be the
     * first two of the MERGED order -- both issues -- rather than one from each
     * half.
     */
    @Test
    public void theCapAppliesToTheMergedListNotToEachHalf() {
        var merged = PublicationRestService.merge(
                List.of(pub("issue-1"), pub("issue-2")),
                List.of("issue-1", "issue-2"),
                legacy(pub("legacy-1")),
                0, 2);

        assertEquals(2, merged.getData().size());
        assertEquals(List.of("issue-1", "issue-2"),
                merged.getData().stream().map(PublicationVo::getPublicationId).toList());
        assertEquals(3L, merged.getTotal(),
                "the total reports what matched, not what fitted -- a caller that pages on it "
                        + "otherwise stops one page early");
    }

    /**
     * The second page is the SECOND slice of the union, not the first one again.
     *
     * This is the defect the paging argument exists for. Prepending the whole
     * issue half to every page put the same issues at the top of page 0 and
     * page 1, so no legacy row past the first page was reachable at all and the
     * archive had roughly a hundred identical pages.
     */
    @Test
    public void thesecondPageContinuesTheUnionRatherThanRepeatingIt() {
        var page0 = PublicationRestService.merge(
                List.of(pub("issue-1"), pub("issue-2")),
                List.of("issue-1", "issue-2"),
                legacy(pub("legacy-1"), pub("legacy-2")),
                0, 2);
        var page1 = PublicationRestService.merge(
                List.of(pub("issue-1"), pub("issue-2")),
                List.of("issue-1", "issue-2"),
                legacy(pub("legacy-1"), pub("legacy-2")),
                1, 2);

        assertEquals(List.of("issue-1", "issue-2"),
                page0.getData().stream().map(PublicationVo::getPublicationId).toList());
        assertEquals(List.of("legacy-1", "legacy-2"),
                page1.getData().stream().map(PublicationVo::getPublicationId).toList(),
                "the second page must continue the union; repeating the issue half makes every "
                        + "legacy row past the first page unreachable");
        assertEquals(4L, page1.getTotal());
    }

    /** A page past the end is empty rather than an index error. */
    @Test
    public void apageBeyondTheEndIsEmpty() {
        var merged = PublicationRestService.merge(
                List.of(pub("issue-1")), List.of("issue-1"), legacy(pub("legacy-1")), 9, 10);

        assertEquals(List.of(), merged.getData().stream()
                .map(PublicationVo::getPublicationId).toList());
        assertEquals(2L, merged.getTotal(), "the total still reports what matched");
    }

    /**
     * An absurd page number does not overflow into a negative index.
     *
     * page * maxSize is computed in long arithmetic for this reason: any caller
     * may send any number, and the int product wraps long before it stops being
     * accepted.
     */
    @Test
    public void anabsurdPageNumberIsEmptyRatherThanAnIndexError() {
        var merged = PublicationRestService.merge(
                List.of(pub("issue-1")), List.of("issue-1"),
                legacy(pub("legacy-1")), Integer.MAX_VALUE, 1000);

        assertEquals(0, merged.getData().size());
        assertEquals(2L, merged.getTotal());
    }

    /** No issues, no change: the legacy rows pass through in order. */
    @Test
    public void anEmptyIssueHalfLeavesTheLegacyResultAlone() {
        var merged = PublicationRestService.merge(
                List.of(), List.of(), legacy(pub("legacy-1"), pub("legacy-2")), 0, 100);

        assertEquals(List.of("legacy-1", "legacy-2"),
                merged.getData().stream().map(PublicationVo::getPublicationId).toList());
    }

    /** A row with no id cannot collide with anything and is kept. */
    @Test
    public void alegacyRowWithNoIdSurvives() {
        var merged = PublicationRestService.merge(
                List.of(pub("issue-1")), List.of("issue-1"), legacy(pub(null)), 0, 100);

        assertEquals(2, merged.getData().size(),
                "a null id matches no issue, so dropping the row would lose data on a guess");
    }
}
