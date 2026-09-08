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

package org.niord.core.publication.series.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preview rows' WIRE SHAPE, checked without a database.
 *
 * Plain JUnit rather than one more case in the workbench suite: everything here
 * is a property of two types and a Jackson mapper, and the workbench suite is a
 * {@code @QuarkusTest} gated on a local MySQL -- so on a machine without one
 * these guards would be skipped silently while reading as green.
 *
 * Both properties below are what a client actually binds to. Three keys and a
 * list that is empty rather than absent is the whole contract; either one moving
 * leaves the issue screen offering to render a preview the server already holds.
 */
public class IssuePreviewVoWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Exactly three keys, spelled the way the preview endpoints spell them.
     *
     * The generating endpoint builds its rows by hand as `lang` and `renderedAt`,
     * and this row is that shape plus `stale`. A client reads the two responses
     * with one type, so a key renamed on this side -- by a record component
     * renamed in an ordinary tidy-up, which is all it takes -- silently produces
     * rows whose language and timestamp are both undefined.
     */
    @Test
    public void thePreviewRowCarriesExactlyLangRenderedAtAndStale() throws Exception {
        JsonNode json = MAPPER.valueToTree(new IssuePreviewVo("da", 1_755_424_218_000L, false));

        Set<String> keys = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("lang", "renderedAt", "stale"), keys,
                "the preview row's keys are " + keys + "; the issue screen binds these three by name");

        assertEquals("da", json.get("lang").asText());
        assertTrue(json.get("renderedAt").isNumber(),
                "renderedAt left as something other than epoch milliseconds; every other instant on "
                        + "these payloads is a number and a client parsing one field differently is a "
                        + "date that renders as Invalid Date");
        assertEquals(1_755_424_218_000L, json.get("renderedAt").asLong());
        assertFalse(json.get("stale").asBoolean());
    }

    /**
     * Nothing stored is an EMPTY list on the wire, not a missing key.
     *
     * The envelope suppresses null properties -- two of its parts are deliberately
     * absent -- so a previews list left null would vanish from the response
     * entirely. The screen seeds its preview rows from every read: an absent key
     * is "unknown" and a client cannot tell it from "none", which is the state
     * that has it offer to generate a preview beside a rail reporting the stored
     * one as current.
     */
    @Test
    public void anIssueWithNoPreviewsStillCarriesTheKey() throws Exception {
        JsonNode json = MAPPER.valueToTree(new IssueWorkbenchVo());

        assertTrue(json.has("previews"),
                "the previews key is absent on an envelope carrying none; the project's serializer "
                        + "drops nulls, so this list must never be one");
        assertTrue(json.get("previews").isArray());
        assertEquals(0, json.get("previews").size());

        IssueWorkbenchVo filled = new IssueWorkbenchVo();
        filled.setPreviews(List.of(new IssuePreviewVo("da", 1_755_424_218_000L, true)));
        assertEquals(1, MAPPER.valueToTree(filled).get("previews").size(),
                "the envelope did not carry the rows it was given");
    }
}
