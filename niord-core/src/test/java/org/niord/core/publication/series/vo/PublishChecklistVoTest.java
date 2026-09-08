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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.MemberResolutionService;
import org.niord.core.publication.series.PublishChecklistService;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release rail's WIRE SHAPE, checked without a database.
 *
 * Deliberately a plain JUnit class rather than one more case in the workbench
 * suite. Everything asserted here is a property of two types and a Jackson
 * mapper, and the workbench suite is a {@code @QuarkusTest} gated on a local
 * MySQL -- so on a build machine with no database these guards would be SKIPPED,
 * silently, while reading as green. The guard this replaces (a source scan in
 * niord-web's contract test) ran everywhere, and the property it pinned is the
 * one that breaks the publish dialog: a rail whose keys moved is read by that
 * dialog as a refusal for a code nobody could tick.
 */
public class PublishChecklistVoTest {

    /**
     * Every field of the rail row reaches the wire.
     *
     * The row used to be mapped into the response by hand, key by key, in the
     * endpoint -- so a component added to the record was carried by nothing until
     * somebody remembered the mapping too, and the endpoint went on answering one
     * field short with no failure to say so. A client reading the missing key gets
     * `undefined`, which for `applicable` means every row counts and for `passed`
     * means every row fails.
     *
     * Asserted against the VO's own accessors, so a key spelled differently from
     * the component it carries is caught as well as an absent one.
     */
    @Test
    public void everyRailRowComponentHasAPropertyOnTheWireShape() {
        Set<String> onTheVo = accessorsOf(PublishCheckRowVo.class);

        for (RecordComponent component : PublishChecklistService.CheckRow.class.getRecordComponents()) {
            assertTrue(onTheVo.contains(component.getName()),
                    "the rail row declares " + component.getName() + ", which "
                            + PublishCheckRowVo.class.getSimpleName() + " never puts on the wire -- the "
                            + "field exists on the server and is invisible to every client");
        }
        assertTrue(onTheVo.contains("applicable"),
                "the rail no longer says which of its rows this issue can even be in; a verdict "
                        + "counted over all fifteen rows counts checks that never ran");
    }

    /**
     * The row's null-keeping and its key order are DECLARED, not incidental.
     *
     * Both are one annotation away from being lost by an ordinary tidy-up.
     * Dropping {@code @JsonInclude(ALWAYS)} -- or making the type an
     * {@code IJsonSerializable}, which the rest of this package is -- suppresses
     * `acknowledgeCode` on the fourteen rows where it is null, and the client
     * reads that key's absence as a different fact than its null. Dropping the
     * order leaves Jackson emitting whatever it discovers.
     */
    @Test
    public void theRowDeclaresItsNullKeepingAndItsOrder() {
        JsonInclude include = PublishCheckRowVo.class.getAnnotation(JsonInclude.class);
        assertNotNull(include, PublishCheckRowVo.class.getSimpleName()
                + " no longer declares @JsonInclude; the project default suppresses nulls, and a "
                + "suppressed acknowledgeCode is a key the publish dialog reads as undefined");
        assertEquals(JsonInclude.Include.ALWAYS, include.value(),
                "the rail row stopped keeping its null properties");

        JsonPropertyOrder order = PublishCheckRowVo.class.getAnnotation(JsonPropertyOrder.class);
        assertNotNull(order, "the rail row's key order is no longer pinned; it was the insertion order "
                + "of a hand-built map, and Jackson's discovery order is not it");
        Set<String> ordered = new LinkedHashSet<>(List.of(order.value()));
        for (RecordComponent component : PublishChecklistService.CheckRow.class.getRecordComponents()) {
            assertTrue(ordered.contains(component.getName()),
                    "the rail row's " + component.getName() + " is missing from @JsonPropertyOrder, so "
                            + "its position on the wire is whatever Jackson happens to discover");
        }
    }

    /** And the envelope carries every part of the rail the endpoint answered with. */
    @Test
    public void theChecklistEnvelopeCarriesEveryPartOfTheRail() {
        Set<String> onTheVo = accessorsOf(PublishChecklistVo.class);
        for (String part : List.of("rows", "canPublish", "blockingCodes", "memberCount")) {
            assertTrue(onTheVo.contains(part),
                    "the checklist envelope no longer carries " + part + "; the publish dialog reads "
                            + "all four, treats a missing canPublish as a refusal, and has no other "
                            + "source for the number of messages the release it is offering would carry");
        }
    }

    /**
     * The member count is the count of the resolution the rail took.
     *
     * The dialog asks for a rail at the instant it is offering to stamp and prints
     * this number as its headline. Computed from anything but that rail's own
     * resolution it would be a count for a different instant -- which is what the
     * dialog did while the field did not exist, showing the count from the screen
     * behind it and leaving the headline unchanged as an admin moved the cut-off.
     */
    @Test
    public void theEnvelopeCountsTheMembersTheRailItselfResolved() {
        PublishChecklistService.Checklist noResolve = new PublishChecklistService.Checklist(
                List.of(), false, List.of(), null);
        assertEquals(0, PublishChecklistVo.of(noResolve).getMemberCount(),
                "an issue whose contents raise no membership question reported a member count; the "
                        + "rail resolved nothing, and any number here is invented");

        MemberResolutionService.Resolution resolved =
                MemberResolutionService.Resolution.curated(new LinkedHashSet<>(List.of("a", "b", "c")));
        PublishChecklistService.Checklist rail = new PublishChecklistService.Checklist(
                List.of(), true, List.of(), resolved);
        assertEquals(3, PublishChecklistVo.of(rail).getMemberCount(),
                "the envelope counted a different member set than the resolution the rail was built "
                        + "from; the dialog's headline and the rows under it would then disagree");
    }

    /**
     * The rail's wire shape is byte for byte what the hand-built map emitted.
     *
     * The checklist endpoint used to compose a LinkedHashMap key by key. Replacing
     * it with a VO is only safe if the JSON does not move, and two details make
     * that non-obvious: `acknowledgeCode` is NULL on fourteen of the fifteen rows
     * and the client reads that as a value -- "this row cannot be acknowledged" --
     * where the project's IJsonSerializable would suppress the key entirely; and a
     * map preserves insertion order where a bean's is whatever Jackson discovers.
     * Both are pinned here against the map itself rather than described.
     *
     * The envelope has since gained `memberCount`, and it is APPENDED: the map's
     * three keys are still emitted first, in their old order and their old
     * positions, which is asserted separately from the whole-body comparison so
     * that a key added in the middle fails as the distinct thing it is.
     */
    @Test
    public void theRailSerialisesExactlyAsTheHandBuiltMapDid() throws Exception {
        List<PublishChecklistService.CheckRow> rows = List.of(
                new PublishChecklistService.CheckRow("ISSUE_OPEN",
                        PublishChecklistService.Severity.BLOCK, true, false, null, "status is OPEN"),
                new PublishChecklistService.CheckRow("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF",
                        PublishChecklistService.Severity.WARN, false, true, true,
                        "CANCELLED_BUT_DATE_ALIVE", "2 member(s) cancelled"),
                new PublishChecklistService.CheckRow("MEMBER_LIMIT",
                        PublishChecklistService.Severity.BLOCK, true, false, false, null,
                        "not applicable: the series resolves no member list"));
        PublishChecklistService.Checklist checklist = new PublishChecklistService.Checklist(
                rows, false, List.of("ISSUE_OPEN"), null);

        // The endpoint's old body, verbatim.
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (PublishChecklistService.CheckRow r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", r.code());
            row.put("severity", r.severity().name());
            row.put("passed", r.passed());
            row.put("applicable", r.applicable());
            row.put("acknowledgeable", r.acknowledgeable());
            row.put("acknowledgeCode", r.acknowledgeCode());
            row.put("detail", r.detail());
            mapped.add(row);
        }
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("rows", mapped);
        old.put("canPublish", checklist.canPublish());
        old.put("blockingCodes", checklist.blockingCodes());

        ObjectMapper json = new ObjectMapper();
        String before = json.writeValueAsString(old);
        String after = json.writeValueAsString(PublishChecklistVo.of(checklist));

        assertTrue(after.startsWith(before.substring(0, before.length() - 1) + ","),
                "the release rail's original keys moved. Everything the hand-built map emitted must "
                        + "still come first, in that order and at those positions; anything added to "
                        + "the envelope is appended after them.\n  was: " + before + "\n  now: " + after);

        // This checklist resolved nothing, so the count it appends is zero.
        old.put("memberCount", 0);
        assertEquals(json.writeValueAsString(old), after,
                "the release rail changed shape on the wire. The publish dialog reads these keys by "
                        + "name and reads a MISSING one as undefined -- which for acknowledgeCode means "
                        + "a refusal for a code nobody could tick");
        assertTrue(after.contains("\"acknowledgeCode\":null"),
                "the rail row dropped its null acknowledgeCode: " + after);
    }

    /** The property names a bean exposes, read the way Jackson reads them. */
    private static Set<String> accessorsOf(Class<?> type) {
        Set<String> out = new LinkedHashSet<>();
        for (Method m : type.getDeclaredMethods()) {
            String name = m.getName();
            if (m.getParameterCount() != 0) {
                continue;
            }
            if (name.startsWith("get") && name.length() > 3) {
                out.add(Character.toLowerCase(name.charAt(3)) + name.substring(4));
            } else if (name.startsWith("is") && name.length() > 2) {
                out.add(Character.toLowerCase(name.charAt(2)) + name.substring(3));
            }
        }
        return out;
    }
}
