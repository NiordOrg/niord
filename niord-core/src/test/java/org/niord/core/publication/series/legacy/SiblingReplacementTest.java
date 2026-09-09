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

package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.PublicationIssue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A withdrawal and its replacement are ONE period, however long the correction
 * took.
 *
 * WHAT WENT WRONG. A weekly edition went out on 31 December carrying the
 * PREVIOUS year's title and the previous year's member list. Six days later
 * somebody set it inactive and published a replacement, backdating the
 * replacement's window to three hours after the withdrawn one had opened. Three
 * hours is outside the five-minute agreement window, so the two read as
 * consecutive releases: the replacement was chained off the WITHDRAWN row's
 * cut-off and given a three-hour content period, and the week it was published to
 * cover then read as never covered at all -- a MISSING row offering a
 * retro-create for a week that had gone out twice.
 *
 * THE RULE, in two disjuncts. Within five minutes, which is one action seen
 * twice; or the outgoing row was WITHDRAWN and this one went out inside its
 * window. The second disjunct's INACTIVE guard is what makes it safe: the weekly
 * windows overlap by construction, so "released inside the predecessor's window"
 * on its own matches 67 consecutive pairs and collapses years of the chain into
 * one release group.
 *
 * Driven from the captured estate, through the real plan, because which rows are
 * ADJACENT is decided by the series each one is filed into -- and the two rows of
 * the weekly pair carry different legacy templates, so any chain built by
 * template puts them in different chains and the pair cannot be seen at all.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SiblingReplacementTest {

    /** The four pairs the withdrawal disjunct adds, as "outgoing>incoming". */
    private static final List<String> WITHDRAWN_AND_REPLACED = List.of(
            // The weekly NtM turnover: released with the previous year's title and
            // tag, withdrawn six days later, replaced from a throwaway template.
            "21868ad4-b2dd-48dc-b15c-8fef153322be>ac94e6ea-7c65-40a8-8769-96b49338113b",
            // The same incident on the P&T weekly, the same day.
            "040756cf-e2ea-4c52-a29c-693c6b06c7bf>37959c90-fa05-4467-b54a-5061c49ad6d3",
            // Two annual editions replaced five days into their year.
            "745fb228-cf58-4eeb-a164-20640071e4d5>66e62bf2-4bdd-4581-9e75-a248ae2b9777",
            "dd1f3280-17c4-4ba9-b664-e4ec82ca6e52>901fca3d-b36e-4ff1-b11c-a91c5f4f0be9");

    /**
     * The eight pairs the five-minute rule already matched, all released at once.
     *
     * Written in CHAIN order, which for rows released at the same instant is the
     * publicationId -- so the withdrawn row is not always the left-hand one. The
     * rule is symmetric and does not care; the chain is what decides which row
     * opens the group.
     *
     * Two weekly NtM turnovers and the two P&T ones beside them, the 2022 firing
     * trio, and the three NCAGS editions of 2023 -- every one of them released in
     * a single action, which is why the five minutes already saw them.
     */
    private static final List<String> RELEASED_TOGETHER = List.of(
            "131ef432-26f3-4752-a405-1a5e91dd6e82>c373d5ae-6bfd-4530-887e-bf6ef265eb5a",
            "41bf44a1-d183-4afc-8db8-cfcd6b62c839>98257761-971f-4b82-af9e-616d676e9740",
            "18dc986f-44ac-4d28-b73f-c1580ee7d665>45938471-4421-4767-8940-3b53fcfacca2",
            "2456d497-f026-4167-a5c8-d9c50c1f8ba0>71ef4924-8edd-43ae-ab5f-5d34e1014f62",
            "19546efb-8f21-42e1-a124-02bd1901b6e6>600578fb-d72e-456c-b9d7-9a07e88b6226",
            "600578fb-d72e-456c-b9d7-9a07e88b6226>76bee094-4959-4a0e-bae0-b55ae80a9e17",
            "1d406b2a-82ee-430f-8a69-bb80a2e3742c>8ebc1e8d-62e6-4b0f-b24d-12a9a768a908",
            "8ebc1e8d-62e6-4b0f-b24d-12a9a768a908>9796f07b-35a4-45c7-8e63-825bb793a662");

    private static final String NTM_WEEK_52_2025 = "26dcc15e-8bde-4646-8337-3f08ed363bc2";
    private static final String NTM_WEEK_1_2026_REPLACEMENT = "ac94e6ea-7c65-40a8-8769-96b49338113b";

    @Inject
    LegacyImportService importService;

    private static LegacyImportService.Plan plan;
    private static List<Publication> publications;

    private LegacyImportService.Plan plan() {
        if (plan == null) {
            publications = LegacyEstateFixture.publications();
            plan = importService.planFrom(LegacyEstateFixture.templates(), publications);
        }
        return plan;
    }

    /**
     * The chains the importer builds, rebuilt from the plan.
     *
     * Keyed on the series each publication was FILED INTO rather than on its
     * legacy template: the double-week rows and the throwaway clones carry
     * templates of their own and are filed into the weekly series by ruling, so a
     * chain built by template has the wrong neighbours in exactly the places this
     * test is about. Ordered by the public window's start with the id as the
     * tiebreak, which is the importer's own order.
     */
    private Map<String, List<Publication>> chains() {
        LegacyImportService.Plan p = plan();
        Map<String, List<Publication>> out = new LinkedHashMap<>();
        for (Publication row : publications) {
            PublicationIssue issue = p.issues().get(row.getPublicationId());
            if (issue == null || issue.getSeries() == null) {
                continue;
            }
            out.computeIfAbsent(issue.getSeries().getSeriesId(), k -> new ArrayList<>()).add(row);
        }
        Comparator<Publication> order = Comparator
                .comparing((Publication row) -> row.getPublishDateFrom() == null
                        ? new Date(0) : row.getPublishDateFrom())
                .thenComparing(Publication::getPublicationId);
        out.values().forEach(chain -> chain.sort(order));
        return out;
    }

    /**
     * Every sibling pair in the estate, and there are exactly twelve.
     *
     * The set is the property worth having: the second disjunct is one clause
     * away from matching whole years of the weekly chain, and a rule that matched
     * thirteen pairs would be as wrong as one that matched sixty-seven. Eight of
     * the twelve were already matching before the disjunct existed.
     */
    @Test
    public void theEstateHoldsTwelveSiblingPairsAndTheyAreTheNamedOnes() {
        Set<String> matched = new LinkedHashSet<>();
        for (List<Publication> chain : chains().values()) {
            for (int i = 1; i < chain.size(); i++) {
                if (LegacyImportService.isSibling(chain.get(i - 1), chain.get(i))) {
                    matched.add(chain.get(i - 1).getPublicationId() + ">"
                            + chain.get(i).getPublicationId());
                }
            }
        }

        Set<String> expected = new LinkedHashSet<>(RELEASED_TOGETHER);
        expected.addAll(WITHDRAWN_AND_REPLACED);
        assertEquals(expected, matched,
                "the sibling rule matches a different set of pairs than the estate says it should");
    }

    /**
     * The six that already matched still match on the five minutes alone.
     *
     * The added disjunct is meant to be additive. If one of these had started
     * relying on it, the five-minute rule would have stopped describing the
     * archive and nothing else would have said so.
     */
    @Test
    public void thePairsReleasedInOneActionStillMatchOnTheAgreementWindowAlone() {
        for (String pair : RELEASED_TOGETHER) {
            Publication outgoing = row(pair.split(">")[0]);
            Publication incoming = row(pair.split(">")[1]);
            long apart = Math.abs(incoming.getPublishDateFrom().getTime()
                    - outgoing.getPublishDateFrom().getTime());
            assertTrue(apart <= CutoffRecovery.AGREEMENT_WINDOW_MS,
                    pair + " is " + apart + " ms apart and no longer matches on the five minutes");
        }
    }

    /**
     * The four new ones do NOT, which is the whole reason they were invisible.
     *
     * Three hours on the two weeklies, five days on the two annuals.
     */
    @Test
    public void theWithdrawnAndReplacedPairsAreHoursOrDaysApart() {
        for (String pair : WITHDRAWN_AND_REPLACED) {
            Publication outgoing = row(pair.split(">")[0]);
            Publication incoming = row(pair.split(">")[1]);
            long apart = Math.abs(incoming.getPublishDateFrom().getTime()
                    - outgoing.getPublishDateFrom().getTime());
            assertTrue(apart > CutoffRecovery.AGREEMENT_WINDOW_MS,
                    pair + " is only " + apart + " ms apart, so it is not the case this rule is for");
            assertTrue(LegacyImportService.isSibling(outgoing, incoming),
                    pair + " is a withdrawal and its replacement and must read as one period");
        }
    }

    /**
     * The consequence, on the row it was found on: the replacement opens where the
     * row BEFORE the pair closed, and covers the whole week.
     *
     * It used to open at the withdrawn row's cut-off, three hours before its own
     * window, and the week between read as uncovered.
     */
    @Test
    public void theReplacementOpensWhereTheWeekBeforeItClosed() {
        PublicationIssue previous = issue(NTM_WEEK_52_2025);
        PublicationIssue replacement = issue(NTM_WEEK_1_2026_REPLACEMENT);

        assertEquals(Date.from(Instant.parse("2025-12-24T11:04:28Z")), previous.effectiveCutoff(),
                "the week before the pair closes where it always did");
        assertEquals(previous.effectiveCutoff(), replacement.getIntervalFrom(),
                "the replacement chains off the row before the pair, not off the row it replaced");

        PublicationIssue withdrawn = issue("21868ad4-b2dd-48dc-b15c-8fef153322be");
        assertEquals(previous.effectiveCutoff(), withdrawn.getIntervalFrom(),
                "and so does the withdrawn row: they are one period, seen twice");
        assertFalse(replacement.getIntervalFrom().after(withdrawn.effectiveCutoff()),
                "a replacement opening after the row it replaced closed is the mis-chaining itself");
    }

    private Publication row(String publicationId) {
        plan();
        for (Publication p : publications) {
            if (publicationId.equals(p.getPublicationId())) {
                return p;
            }
        }
        throw new IllegalStateException(publicationId + " is not in the estate fixture");
    }

    private PublicationIssue issue(String publicationId) {
        PublicationIssue issue = plan().issues().get(publicationId);
        assertNotNull(issue, publicationId + " is not in the plan");
        return issue;
    }
}
