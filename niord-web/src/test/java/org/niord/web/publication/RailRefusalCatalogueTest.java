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

package org.niord.web.publication;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.PublishChecklistService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every BLOCK row of the release rail refuses with a code a client can branch on.
 *
 * The rail names the CONDITION that has to hold and the error names the
 * VIOLATION, so the two vocabularies are deliberately different strings -- which
 * is exactly why the mapping needs holding down. A row whose refusal code is not
 * in the catalogue answers 500 from every endpoint, and 500 is the one status a
 * client is entitled to retry.
 *
 * Pure: the catalogue and the rail's code list are both constants.
 */
public class RailRefusalCatalogueTest {

    /**
     * The BLOCK rows, and the code each refuses with.
     *
     * Written out rather than derived, because it IS the mapping: a table built
     * from the same expression the production code uses would agree with itself
     * whatever either of them said.
     */
    private static final Map<String, String> REFUSALS = new LinkedHashMap<>();

    static {
        // A row can refuse two ways -- the issue is not open, or its series is not
        // one a publication may go out from.
        REFUSALS.put("ISSUE_OPEN", "ISSUE_NOT_OPEN");
        REFUSALS.put("INTERVAL_PRESENT", "INTERVAL_INVALID");
        REFUSALS.put("FILE_PRESENT_PER_LANGUAGE", "MISSING_FILE_FOR_LANGUAGE");
        REFUSALS.put("REPORT_CONFIGURED", "REPORT_NOT_CONFIGURED");
        REFUSALS.put("REFERENCE_FORMAT_COMPLETE", "REFERENCE_FORMAT_MISSING_LANGUAGE");
        REFUSALS.put("CUTOFF_AFTER_PREVIOUS", "CUTOFF_BEFORE_PREVIOUS");
        REFUSALS.put("CUTOFF_BEFORE_SUCCESSOR", "CUTOFF_AFTER_SUCCESSOR");
        REFUSALS.put("CUTOFF_NOT_FUTURE", "CUTOFF_IN_FUTURE");
        REFUSALS.put("MEMBER_LIMIT", "MEMBER_LIMIT_EXCEEDED");
    }

    /** The second code the ISSUE_OPEN row can raise, which is about the series. */
    private static final String SERIES_REFUSAL = "SERIES_NOT_ACTIVE";

    @Test
    public void everyBlockingRowRefusesWithACataloguedCode() {
        for (Map.Entry<String, String> refusal : REFUSALS.entrySet()) {
            assertTrue(PublicationErrorCatalogue.knows(refusal.getValue()),
                    refusal.getKey() + " refuses with " + refusal.getValue()
                            + ", which is not in the catalogue -- so it answers 500, and a client "
                            + "is entitled to retry a 500 forever");
        }
        assertTrue(PublicationErrorCatalogue.knows(SERIES_REFUSAL));
    }

    /**
     * And every refusal is a 4xx: the caller can act on all of them.
     *
     * A rail row describes something an admin can fix -- attach the file, name the
     * report, choose another instant. Presenting one as a server failure invites
     * the retry that fails the same way.
     */
    @Test
    public void everyRefusalIsAClientError() {
        for (String code : REFUSALS.values()) {
            int status = PublicationErrorCatalogue.statusOf(code);
            assertTrue(status >= 400 && status < 500,
                    code + " is " + status + "; a rail refusal names something the caller can change");
        }
        assertEquals(409, PublicationErrorCatalogue.statusOf(SERIES_REFUSAL));
    }

    /**
     * A row applies unless it says otherwise.
     *
     * The rail is fifteen rows on every issue and only a few of them can be in a
     * condition this issue is capable of. The default therefore has to be the
     * safe direction -- counted -- so that a row added later is counted until
     * somebody deliberately says it does not apply, rather than silently dropping
     * out of every verdict drawn from the rail.
     */
    @Test
    public void aRowAppliesUnlessItSaysOtherwise() {
        PublishChecklistService.CheckRow row = new PublishChecklistService.CheckRow(
                "ISSUE_OPEN", PublishChecklistService.Severity.BLOCK, true, false, null, "status is OPEN");

        assertTrue(row.applicable(),
                "a row built without saying anything about applicability must count");
    }

    /**
     * The rail declares no BLOCK row this table has not accounted for.
     *
     * The list of codes the rail can emit is a constant, so a row added without a
     * refusal code fails here rather than reaching an admin as an uncoded 500.
     */
    @Test
    public void theTableAccountsForEveryRowTheRailDeclares() {
        List<String> declared = PublishChecklistService.CODES;
        for (String code : REFUSALS.keySet()) {
            assertTrue(declared.contains(code),
                    code + " is not a row the rail emits; the table describes a check that does not exist");
        }
        // The rows this table deliberately omits are the ones that never block: a
        // WARN describes the release rather than refusing it.
        List<String> warnings = List.of("INTERVAL_CHAINED", "MEMBERS_RESOLVED", "PREVIEW_FRESH",
                "NO_INEFFECTIVE_OVERRIDES", "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF", "OVERLAPPING_ISSUE");
        for (String code : declared) {
            assertTrue(REFUSALS.containsKey(code) || warnings.contains(code),
                    code + " is neither a warning nor a row with a refusal code; if it can block a "
                            + "publish it needs one, and if it cannot it belongs in the warning list");
        }
    }
}
