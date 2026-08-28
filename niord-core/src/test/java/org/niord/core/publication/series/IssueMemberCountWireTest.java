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

package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * memberCount is tri-state on the wire while the column stays NOT NULL.
 *
 * `0` means "resolved, and nothing matched". `null` means "this publication has
 * no membership semantics at all" -- it is a PDF or a link, and no query was
 * ever run for it. Emitting the column's 0 for the second case says the first,
 * which is exactly the confusion membershipProvenance exists to prevent,
 * reintroduced one field along. Roughly 48 publications in the estate are
 * NO_MEMBERSHIP, so this is the common case rather than a corner.
 *
 * No database and no Quarkus: this is a mapping, and mapping tests that need
 * MySQL are mapping tests that stop running.
 */
public class IssueMemberCountWireTest {

    private static PublicationIssue issue(MembershipProvenance provenance, Integer count) {
        PublicationIssue i = new PublicationIssue();
        i.setStatus(IssueStatus.PUBLISHED);
        i.setMembershipProvenance(provenance);
        i.setMemberCount(count);
        return i;
    }

    /** A publication with no membership semantics reports null, not zero. */
    @Test
    public void noMembershipEmitsNull() {
        SystemPublicationIssueVo vo = issue(MembershipProvenance.NO_MEMBERSHIP, 0)
                .toVo(SystemPublicationIssueVo.class);

        assertNull(vo.getMemberCount(),
                "a NO_MEMBERSHIP publication emitted a count, which reads as 'resolved, empty' "
                        + "-- the distinction membershipProvenance exists to draw");
    }

    /** A resolved issue that matched nothing reports 0, and that is a different fact. */
    @Test
    public void resolvedAndEmptyEmitsZero() {
        SystemPublicationIssueVo vo = issue(MembershipProvenance.EXACT, 0)
                .toVo(SystemPublicationIssueVo.class);

        assertEquals(0, vo.getMemberCount(),
                "a resolved issue that selected nothing must still say so; null would claim it "
                        + "has no membership semantics");
    }

    /** An ordinary resolved issue passes the column through untouched. */
    @Test
    public void aresolvedIssuePassesTheColumnThrough() {
        SystemPublicationIssueVo vo = issue(MembershipProvenance.EXACT, 27)
                .toVo(SystemPublicationIssueVo.class);

        assertEquals(27, vo.getMemberCount());
    }

    /**
     * The column itself is untouched by any of this.
     *
     * Invariant I-11 compares member rows to the COLUMN, so it is unaffected by
     * the wire rule -- and it would be a real problem if the tri-state leaked
     * back into storage.
     */
    @Test
    public void thecolumnKeepsItsValue() {
        PublicationIssue i = issue(MembershipProvenance.NO_MEMBERSHIP, 0);
        i.toVo(SystemPublicationIssueVo.class);

        assertEquals(0, i.getMemberCount(),
                "the wire rule changed the stored value; I-11 compares against this column");
    }
}
