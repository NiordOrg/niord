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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.NamedMessageVo;
import org.niord.core.publication.PublicationResolver;
import org.niord.core.publication.series.IssueEditService;
import org.niord.core.publication.series.PublicationDomainGuard;
import org.niord.core.publication.series.PublishChecklistService;
import org.niord.core.publication.series.vo.PublicationIssueVo;
import org.niord.core.publication.series.vo.PublishCheckRowVo;
import org.niord.core.publication.series.vo.PublicationSeriesVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueDescVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.niord.model.message.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The API contract: the tier split, and the error catalogue.
 *
 * Both are properties of the whole surface rather than of any one endpoint, so
 * they are checked over the surface rather than endpoint by endpoint -- which is
 * also the only way to catch the endpoint that was added later and forgot.
 *
 * No database and no server: this reads annotations and source.
 */
public class PublicationApiContractTest {

    /**
     * The tier split is enforced by TYPE, and every endpoint returning a system
     * shape must require a role.
     *
     * A public endpoint that returns a system VO leaks the criteria document,
     * the cutover switch and the whole schedule to an anonymous caller -- and the
     * response looks entirely ordinary, so nothing would surface it.
     */
    @Test
    public void noPublicEndpointReturnsASystemShape() {
        List<String> offenders = new ArrayList<>();

        for (Class<?> resource : List.of(
                PublicationSeriesRestService.class,
                PublicationIssueRestService.class,
                PublicationCategoryRestService.class)) {

            for (Method m : resource.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Path.class) && !hasHttpVerb(m)) {
                    continue;
                }
                boolean systemShape = returnsSystemShape(m);
                boolean anonymous = m.isAnnotationPresent(PermitAll.class);

                if (systemShape && anonymous) {
                    offenders.add(resource.getSimpleName() + "#" + m.getName()
                            + " returns a system VO and is @PermitAll");
                }
                if (systemShape && !m.isAnnotationPresent(RolesAllowed.class)) {
                    offenders.add(resource.getSimpleName() + "#" + m.getName()
                            + " returns a system VO with no @RolesAllowed");
                }
            }
        }

        if (!offenders.isEmpty()) {
            fail("the tier split is broken:\n  " + String.join("\n  ", offenders));
        }
    }

    /** And the public types genuinely do not carry the operational fields. */
    @Test
    public void thePublicShapesCannotCarryOperationalFields() {
        Set<String> publicSeriesFields = declaredFields(PublicationSeriesVo.class);
        for (String leaky : List.of("criteria", "publicAuthority", "releaseMode", "timeRelation",
                "reportId", "legacyTemplateId")) {
            assertFalse(publicSeriesFields.contains(leaky),
                    "PublicationSeriesVo declares " + leaky + "; a field that is not there cannot leak, "
                            + "which is the whole reason for the split");
        }

        Set<String> publicIssueFields = declaredFields(PublicationIssueVo.class);
        for (String leaky : List.of("intervalFrom", "cutoffStampedAt", "snapshotIntervalFrom",
                "membershipProvenance", "repoPath",
                // The per-edition overrides are administration, on the same terms
                // as the series' own reportId beside them: which template sets a
                // document out, and what an admin typed into its parameters, are
                // answers to questions a public reader is not asking and a
                // parameter map is a place secrets get typed.
                "reportId", "reportParams", "seriesReportParams", "seriesReportId")) {
            assertFalse(publicIssueFields.contains(leaky),
                    "PublicationIssueVo declares " + leaky
                            + "; out of context a public reader would take it for the issue period");
        }

        // And the system shapes DO carry them, so the split is a split rather
        // than a deletion.
        assertTrue(declaredFields(SystemPublicationSeriesVo.class).contains("criteria"));
        assertTrue(declaredFields(SystemPublicationIssueVo.class).contains("snapshotIntervalFrom"));

        // The read side of the per-edition overrides. A drawer that can WRITE a
        // field and cannot READ it back can only send the whole form and hope,
        // and every mark the design puts on an overridden field is a comparison
        // the server already made -- emitted, so no client makes it differently.
        Set<String> systemIssueFields = declaredFields(SystemPublicationIssueVo.class);
        for (String required : List.of("weekLabel", "yearLabel",
                "printedWeek", "printedWeekTo", "printedYear", "numberingOverridden",
                "reportId", "seriesReportId", "reportOverridden",
                "reportParams", "seriesReportParams")) {
            assertTrue(systemIssueFields.contains(required),
                    "SystemPublicationIssueVo no longer declares " + required
                            + "; the drawer can write the field and cannot read it back");
        }
        Set<String> systemDescFields = declaredFields(SystemPublicationIssueDescVo.class);
        for (String required : List.of("nameOverridden", "fileNameOverridden", "suggestedFileName")) {
            assertTrue(systemDescFields.contains(required),
                    "SystemPublicationIssueDescVo no longer declares " + required
                            + "; the per-language file-name section has nothing to mark or to suggest");
        }
    }

    /**
     * Every field of a release-rail row reaches the wire, under its own name.
     *
     * The row is no longer mapped into the response by hand -- the endpoint
     * returns {@link PublishCheckRowVo} -- but the failure this guards is the same
     * one and it is still a mapping: a component added to the record is carried by
     * nothing until somebody adds a property, and the endpoint keeps answering,
     * one field short, with no failure anywhere to say so. A client reading the
     * missing key gets `undefined`, which for `applicable` means every row counts
     * and for `passed` means every row fails.
     *
     * Asserted on the SERIALISED shape rather than on the accessors alone, because
     * a property Jackson never emits -- ignored, renamed, suppressed as null -- is
     * exactly as invisible to the client as one that was never written.
     *
     * DB-free and container-free, deliberately: this is the one place the rail's
     * shape is checked on a build machine with no MySQL.
     */
    @Test
    public void everyRailRowFieldReachesTheWire() {
        // A row with a value in every component, so nothing is dropped merely for
        // being null -- and one component (acknowledgeCode) that is null on
        // thirteen of the fourteen real rows, checked separately below.
        PublishChecklistService.CheckRow row = new PublishChecklistService.CheckRow(
                "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF", PublishChecklistService.Severity.WARN,
                false, true, true, "CANCELLED_BUT_DATE_ALIVE",
                new PublishChecklistService.Detail("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF.count",
                        Map.of("count", 2), "2 member(s) cancelled"));

        JsonNode wire = new ObjectMapper().valueToTree(PublishCheckRowVo.of(row));
        for (RecordComponent component : PublishChecklistService.CheckRow.class.getRecordComponents()) {
            assertTrue(wire.has(component.getName()),
                    "the rail row declares " + component.getName()
                            + ", which the checklist endpoint never puts on the wire -- the field exists "
                            + "on the server and is invisible to every client. On the wire: " + wire);
        }
        assertTrue(wire.has("applicable"),
                "the rail no longer says which of its rows this issue can even be in; a verdict counted "
                        + "over all fourteen rows counts checks that never ran");

        // The row's detail travels in BOTH forms, and the client needs the coded
        // one: `detail` is English, composed on the server, and a Danish screen
        // that renders it shows one English sentence under a translated heading.
        // The key is per SENTENCE VARIANT -- a row says several different things --
        // and the params are the values that sentence interpolates.
        assertEquals("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF.count", wire.get("detailCode").asText(),
                "the rail row's detailCode did not reach the wire; the frontend has nothing to "
                        + "translate and falls back to the English detail on every row");
        assertTrue(wire.get("detailParams").isObject()
                        && wire.get("detailParams").get("count").asInt() == 2,
                "the rail row's detailParams did not reach the wire as an object carrying the "
                        + "sentence's values; a translated sentence with no params states no number: "
                        + wire.get("detailParams"));

        // Empty rather than absent where a sentence takes no values, so a client
        // interpolates unconditionally instead of guarding every row.
        JsonNode noParams = new ObjectMapper().valueToTree(PublishCheckRowVo.of(
                new PublishChecklistService.CheckRow("REFERENCE_FORMAT_COMPLETE",
                        PublishChecklistService.Severity.BLOCK, true, false, null,
                        new PublishChecklistService.Detail("REFERENCE_FORMAT_COMPLETE.citable",
                                Map.of(), "series is citable"))));
        assertTrue(noParams.has("detailParams") && noParams.get("detailParams").isObject()
                        && noParams.get("detailParams").isEmpty(),
                "a rail row whose sentence takes no values dropped detailParams instead of sending "
                        + "an empty object: " + noParams);

        // And the null-valued component keeps its key, UNDER A MAPPER THAT DROPS
        // NULLS -- which is the project's own default, applied to every
        // IJsonSerializable VO in this package. The client reads the null as a
        // value, "this row cannot be acknowledged", and a suppressed key reads as
        // undefined: a refusal for a code nobody could tick.
        ObjectMapper compact = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        JsonNode unacknowledgeable = compact.valueToTree(PublishCheckRowVo.of(
                new PublishChecklistService.CheckRow("ISSUE_OPEN",
                        PublishChecklistService.Severity.BLOCK, true, false, null,
                        new PublishChecklistService.Detail("ISSUE_OPEN.status",
                                Map.of("status", "OPEN", "seriesStatus", "ACTIVE"),
                                "status is OPEN, series is ACTIVE"))));
        assertTrue(unacknowledgeable.has("acknowledgeCode")
                        && unacknowledgeable.get("acknowledgeCode").isNull(),
                "the rail row dropped its null acknowledgeCode: " + unacknowledgeable);
        JsonInclude include = PublishCheckRowVo.class.getAnnotation(JsonInclude.class);
        assertNotNull(include, "the rail row no longer declares @JsonInclude, so it keeps its nulls "
                + "only for as long as nobody makes it an IJsonSerializable");
        assertEquals(JsonInclude.Include.ALWAYS, include.value(),
                "the rail row stopped keeping its null properties");

        // The order is the hand-built map's insertion order and is declared rather
        // than discovered, so it survives a field being added or moved.
        JsonPropertyOrder order = PublishCheckRowVo.class.getAnnotation(JsonPropertyOrder.class);
        assertNotNull(order, "the rail row's key order is no longer pinned");
        Set<String> ordered = new LinkedHashSet<>(Arrays.asList(order.value()));
        for (RecordComponent component : PublishChecklistService.CheckRow.class.getRecordComponents()) {
            assertTrue(ordered.contains(component.getName()),
                    "the rail row's " + component.getName() + " is missing from @JsonPropertyOrder");
        }
    }

    /**
     * Every error code thrown anywhere is in the catalogue.
     *
     * A code that is not mapped falls through to 500, so a perfectly ordinary
     * state conflict reads to a client as "the server broke" -- and a client that
     * retries on 5xx will retry something that can never succeed.
     */
    @Test
    public void everyThrownErrorCodeIsInTheCatalogue() throws IOException {
        // Two forms, because a code passed as a constant would otherwise slip
        // past the scan -- and a code that slips past returns 500.
        Pattern thrownLiteral = Pattern.compile("TransitionRefusedException\\(\\s*\"([A-Z_]+)\"");
        Pattern thrownConstant = Pattern.compile("TransitionRefusedException\\(\\s*([A-Z][A-Z0-9_]{3,})\\b");
        Pattern constantValue = Pattern.compile("String\\s+([A-Z][A-Z0-9_]{3,})\\s*=\\s*\"([A-Z_]+)\"");
        Set<String> codes = new LinkedHashSet<>();

        for (String root : List.of("../niord-core/src/main/java", "src/main/java")) {
            java.nio.file.Path dir = Paths.get(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<java.nio.file.Path> files = Files.walk(dir)) {
                for (java.nio.file.Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(f, StandardCharsets.UTF_8);

                    Matcher m = thrownLiteral.matcher(src);
                    while (m.find()) {
                        codes.add(m.group(1));
                    }

                    // Resolve constants declared in the same file. Cross-file
                    // constants are not followed; if one turns up, this says so
                    // rather than quietly leaving the code out of the scan.
                    Map<String, String> constants = new LinkedHashMap<>();
                    Matcher c = constantValue.matcher(src);
                    while (c.find()) {
                        constants.put(c.group(1), c.group(2));
                    }
                    Matcher k = thrownConstant.matcher(src);
                    while (k.find()) {
                        String resolved = constants.get(k.group(1));
                        assertNotNull(resolved,
                                f.getFileName() + " throws TransitionRefusedException with the constant "
                                        + k.group(1) + ", which is not declared in that file. The scan "
                                        + "cannot follow it, so the code could be missing from the "
                                        + "catalogue and return 500 unnoticed.");
                        codes.add(resolved);
                    }
                }
            }
        }

        assertTrue(codes.size() >= 10,
                "only " + codes.size() + " thrown codes were found; the scan looks broken");

        List<String> unmapped = codes.stream()
                .filter(c -> !PublicationErrorCatalogue.knows(c))
                .toList();

        if (!unmapped.isEmpty()) {
            fail("these codes are thrown but not in the catalogue, so they would return 500:\n  "
                    + String.join("\n  ", unmapped));
        }
    }

    /** One status per code. A code meaning 409 here and 400 there is two codes wearing one name. */
    @Test
    public void everyCodeHasExactlyOneStatus() {
        var all = PublicationErrorCatalogue.all();
        assertFalse(all.isEmpty());

        for (var entry : all.entrySet()) {
            int status = entry.getValue();
            assertTrue(status == 400 || status == 403 || status == 404 || status == 409
                            || status == 500,
                    entry.getKey() + " maps to an unexpected status " + status);
            assertEquals(status, PublicationErrorCatalogue.statusOf(entry.getKey()),
                    entry.getKey() + " does not resolve to its own mapping");
        }

        // An unmapped code is 500 rather than a guess, so a missing mapping is
        // visible as a server error rather than silently becoming a 400.
        assertEquals(500, PublicationErrorCatalogue.statusOf("A_CODE_NOBODY_REGISTERED"));
    }

    /** The state-conflict codes are 409, not 400: the same request may succeed later. */
    @Test
    public void stateConflictsAreNotClientErrors() {
        for (String code : List.of("ISSUE_ALREADY_PUBLISHED", "ISSUE_NOT_PUBLISHED", "ISSUE_NOT_OPEN",
                "ISSUE_PUBLISHED_NOT_DELETABLE", "ISSUE_CITED", "SERIES_HAS_ISSUES", "CATEGORY_IN_USE")) {
            assertEquals(409, PublicationErrorCatalogue.statusOf(code),
                    code + " is a state conflict; as a 400 a client would stop retrying something that "
                            + "will succeed once the state changes");
        }

        // And the archive failure is a 500, because the caller did nothing wrong
        // and retrying the same request would fail the same way.
        assertEquals(500, PublicationErrorCatalogue.statusOf("ARCHIVE_FAILED"));
    }

    /**
     * Writing another domain's publication is a 403, and it is CATALOGUED.
     *
     * Named on its own because of what each near-miss would cost. Uncatalogued it
     * falls through to 500, and an admin who merely has the wrong domain selected
     * would be told the server broke. As a 404 it would contradict the screen the
     * caller is looking at, which is listing the series. As a 409 a client would
     * retry it forever: no change of state makes the request right, only a change
     * of domain.
     */
    @Test
    public void writingOutsideTheCallersDomainIsAForbidden() {
        assertTrue(PublicationErrorCatalogue.knows(PublicationDomainGuard.NOT_IN_DOMAIN),
                "NOT_IN_DOMAIN is not in the catalogue, so a write aimed at another domain "
                        + "would come back as a 500");
        assertEquals(403, PublicationErrorCatalogue.statusOf(PublicationDomainGuard.NOT_IN_DOMAIN));

        // The mapper reads the code off the exception, so the refusal the guard
        // raises has to be one it recognises -- a refusal outside the base type
        // is not mapped at all.
        assertEquals(PublicationDomainGuard.NOT_IN_DOMAIN,
                PublicationExceptionMapper.codeOf(
                        new PublicationDomainGuard.NotInDomainException("not yours")));
    }

    /**
     * An unresolvable publication= id is a 400, and it is CATALOGUED.
     *
     * Named on its own because of what the alternative costs. With no mapping it
     * falls through to 500, and it is thrown from the public message API -- so a
     * caller with a typo would be told the server broke, and would retry.
     */
    @Test
    public void anUnresolvablePublicationIsAClientError() {
        assertTrue(PublicationErrorCatalogue.knows(PublicationResolver.UNRESOLVABLE),
                "PUBLICATION_UNRESOLVABLE is not in the catalogue, so a bad publication= id would "
                        + "return 500 from an anonymous endpoint");
        assertEquals(400, PublicationErrorCatalogue.statusOf(PublicationResolver.UNRESOLVABLE));
    }

    // ================================================== the widening trigger

    /**
     * The widening is triggered by the DESIGNATION, not by tag presence.
     *
     * Tag presence was the old trigger, and it conflated "no publication was
     * named" with "a publication was named and it produced no tag". Five sites
     * branched on it. Any one of them left behind re-opens the defect for the
     * endpoint it guards, and the endpoint goes on returning 200 with a
     * plausible-looking list, so nothing surfaces it.
     *
     * Source-level rather than behavioural because that is what makes it
     * exhaustive: it catches the sixth site somebody adds later.
     */
    @Test
    public void noWideningStillBranchesOnTagPresence() throws IOException {
        String api = read("src/main/java/org/niord/web/api/AbstractApiService.java");
        assertTrue(api.contains("memberSetDesignated"),
                "AbstractApiService no longer consults the designation");
        assertFalse(api.contains("params.getTags().isEmpty()"),
                "AbstractApiService still branches on tag presence. /rest/public/v1/messages declares "
                        + "no tag parameter at all, so tags there can only have come from a "
                        + "publication -- which is precisely the conflation being removed.");
        assertFalse(api.contains("tagsSpecified"),
                "the tagsSpecified flag survived; it is the old trigger under its old name");

        String search = read("src/main/java/org/niord/web/MessageSearchRestService.java");
        assertTrue(search.contains("memberSetDesignated"),
                "MessageSearchRestService no longer consults the designation");
        assertFalse(search.contains("if (params.getTags().isEmpty())"),
                "a widening branch in MessageSearchRestService still tests tag presence alone. It has "
                        + "to be the UNION -- !memberSetDesignated && getTags().isEmpty() -- because "
                        + "tag= reaches that endpoint directly, and a plain replacement re-imposes "
                        + "the restriction block on every ordinary tag= search.");
        assertTrue(search.contains("!memberSetDesignated && params.getTags().isEmpty()"),
                "the union form is missing from MessageSearchRestService");
    }

    /**
     * And neither file converts publications to tags any more.
     *
     * Two independent copies of that conversion is how the two endpoints drifted
     * apart in the first place.
     */
    @Test
    public void neitherEndpointResolvesPublicationsToTagsItself() throws IOException {
        for (String file : List.of("src/main/java/org/niord/web/api/AbstractApiService.java",
                "src/main/java/org/niord/web/MessageSearchRestService.java")) {
            assertFalse(read(file).contains("findTagsByPublicationIds"),
                    file + " still resolves publications to tags on its own; there is one resolver "
                            + "now, and a second copy is a second set of rules");
        }
    }

    /**
     * The status widening is DERIVED from Status.isPublic(), not listed.
     *
     * All 2,324 members of the blank-era issues are EXPIRED or CANCELLED and not
     * one is PUBLISHED. A literal "PUBLISHED only" filter empties every
     * historical issue -- and an empty issue still renders, so the failure is a
     * document with no content rather than an error.
     */
    @Test
    public void theStatusWideningIsDerived() throws IOException {
        String api = read("src/main/java/org/niord/web/api/AbstractApiService.java");
        assertTrue(api.contains("Status::isPublic"),
                "the widened status set is no longer derived from Status.isPublic()");

        Set<Status> derived = Arrays.stream(Status.values())
                .filter(Status::isPublic)
                .collect(Collectors.toSet());

        assertTrue(derived.contains(Status.EXPIRED) && derived.contains(Status.CANCELLED),
                "Status.isPublic() no longer covers EXPIRED and CANCELLED, so every historical issue "
                        + "would come back empty");
        assertTrue(derived.contains(Status.PUBLISHED));
        assertFalse(derived.contains(Status.DRAFT),
                "DRAFT became public; the derived set feeds an anonymous endpoint");
    }

    /**
     * A release renders. There is no option not to, and no endpoint reads one.
     *
     * The option let a release ship bytes that were rendered at some earlier
     * moment -- before a member was curated in or out -- so a published document
     * and the frozen member rows of the same issue could disagree, with nothing
     * to say which one was the publication. Removing it from the service is only
     * half the job: an endpoint still reading the key would go on accepting a
     * body that says something the system no longer does, and a client would
     * keep sending it believing it meant something.
     *
     * Source-level, because this module has no container tests: there is nowhere
     * else a body key that is read but ignored could be caught.
     *
     * CODE only -- the comments are stripped first. What is forbidden is an
     * endpoint that READS the key, in any of the shapes that can read one: a
     * quoted key off a map body, a record component, a parameter. Prose is free to
     * explain why the option is gone, and the sticky-upload note nearby has to.
     */
    @Test
    public void noEndpointReadsARegenerateOption() throws IOException {
        // Any inflection, because the code has no reason to name the thing at all
        // -- and "regenerated" reading the same body key would otherwise slip past
        // a guard that only knew the infinitive.
        Pattern key = Pattern.compile("\\bregenerat\\w*", Pattern.CASE_INSENSITIVE);
        for (String file : List.of("src/main/java/org/niord/web/publication/PublicationIssueRestService.java",
                "src/main/java/org/niord/web/publication/OneOffRestService.java")) {
            String code = withoutComments(read(file));
            assertFalse(key.matcher(code).find(),
                    file + " still reads a regenerate option. A release always renders the member "
                            + "list it freezes; a body key that is read and ignored is worse than one "
                            + "that is refused, because the caller cannot tell.");
        }
    }

    /**
     * The source with its comments blanked out, so a source-level guard checks
     * what the code does rather than how it is spelled in prose.
     *
     * The line-comment rule skips a "//" that follows a colon, which is the one
     * place a scheme in a string literal looks like the start of a comment.
     */
    private static String withoutComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)(?<!:)//.*$", " ");
    }

    /**
     * Both dialogs that can name an issue have somewhere to put the name.
     *
     * The create dialog prefills the series' suggestions and the release dialog
     * is the last moment a name can still change -- from there it is on the
     * document and in every citation. The create body is a record, so its shape
     * is readable here; the publish body is a map, so what is pinned is that the
     * endpoint reads the key at all.
     */
    @Test
    public void theCreateAndReleaseBodiesBothCarryNames() throws IOException {
        List<String> components =
                Arrays.stream(PublicationIssueRestService.CreateIssueRequest.class.getRecordComponents())
                        .map(RecordComponent::getName).toList();
        assertTrue(components.contains("names"),
                "the create body carries no names, so an issue can only be renamed by a second request "
                        + "-- and between the two it is listed under a name nobody chose");

        String issues = read("src/main/java/org/niord/web/publication/PublicationIssueRestService.java");
        assertTrue(issues.contains("nameMap(params)"),
                "the publish endpoint no longer reads the names off its body, so the release dialog's "
                        + "name field would be accepted and dropped");
    }

    /**
     * The edit body carries the edition, and it reaches the service.
     *
     * The column was write-once at import: the read surfaces were built -- a
     * sortable column, "2026 (2)" in the timeline strip -- while nothing on the
     * wire could set it, so the one row in the archive carrying a mistyped value
     * was uncorrectable through the product that displays it.
     *
     * Both halves are asserted because either alone passes while the field is
     * dead: a component nothing maps is accepted and dropped, and a mapping with
     * no component does not compile but a renamed one silently would.
     */
    @Test
    public void theEditBodyCarriesTheEdition() throws IOException {
        List<String> components =
                Arrays.stream(PublicationIssueRestService.UpdateIssueRequest.class.getRecordComponents())
                        .map(RecordComponent::getName).toList();
        assertTrue(components.contains("edition"),
                "the edit body carries no edition, so the field the issue list sorts on can only "
                        + "ever hold what the import put there");

        String issues = read("src/main/java/org/niord/web/publication/PublicationIssueRestService.java");
        assertTrue(issues.contains("request.edition()"),
                "editOf does not pass the edition through, so a client sending one is answered 200 "
                        + "and nothing changes");
    }

    /**
     * Every per-edition override reaches the service, under its own name.
     *
     * The same two halves as the edition above, for the same reason: a component
     * nothing maps is accepted and dropped -- the client is answered 200 and
     * nothing changes -- and a mapping with a renamed component would not compile
     * but a re-ORDERED one silently would, because these are all strings.
     *
     * Four fields, and each is a thing the series decides that one edition
     * occasionally has to decide for itself: what it is CALLED where its derived
     * week and year print, what its documents are filed under, and which report
     * sets it out.
     */
    @Test
    public void theEditBodyCarriesEveryPerEditionOverride() throws IOException {
        List<String> components =
                Arrays.stream(PublicationIssueRestService.UpdateIssueRequest.class.getRecordComponents())
                        .map(RecordComponent::getName).toList();
        String issues = read("src/main/java/org/niord/web/publication/PublicationIssueRestService.java");

        for (String field : List.of("weekLabel", "yearLabel", "fileNames", "reportId")) {
            assertTrue(components.contains(field),
                    "the edit body carries no " + field + ", so the drawer that edits it has no way "
                            + "to save it");
            assertTrue(issues.contains("request." + field + "()"),
                    "editOf does not pass " + field + " through, so a client sending one is answered "
                            + "200 and nothing changes");
        }
    }

    /**
     * And they reach the SERVICE record in the same order they are read off the
     * body.
     *
     * Four consecutive components of one record, three of them String: a
     * transposition compiles, passes every type check, and swaps the year label
     * with the week label on the cover of a published document. The order is
     * asserted rather than trusted.
     */
    @Test
    public void theServiceEditCarriesTheOverridesInTheOrderTheyAreMapped() {
        List<String> components =
                Arrays.stream(IssueEditService.IssueEdit.class.getRecordComponents())
                        .map(RecordComponent::getName).toList();
        assertEquals(List.of("weekLabel", "yearLabel", "fileNames", "reportId"),
                components.subList(components.size() - 4, components.size()),
                "the edit record's override fields moved; editOf passes them positionally, so a "
                        + "transposition here silently prints the year where the week goes");
    }

    /**
     * The probe names the messages it samples.
     *
     * The criteria editor exists to let an admin judge a document before it is
     * saved onto a series, and the sample is the half of that answer that is not
     * a number. Listed by uid it is a column of uuids -- nothing an admin can
     * recognise, quote or open -- so the sample carries the same three fields
     * every other list of messages in this API carries, filled in one query.
     */
    @Test
    public void theProbeSampleNamesEveryMessageItLists() throws IOException {
        List<String> components = Arrays.stream(NamedMessageVo.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of("messageUid", "messageId", "title"), components,
                "a sampled message is named by uid, short id and title together: the uid is the "
                        + "only one that addresses it, the short id is the only one that can be cited, "
                        + "and the title is the only one an unnumbered message has");

        String series = read("src/main/java/org/niord/web/publication/PublicationSeriesRestService.java");
        assertTrue(series.contains("naming.namedList("),
                "the probe no longer fills the names of its sample, so the criteria panel would list "
                        + "uids again -- and the count beside them would be the only readable thing on it");
        assertFalse(series.contains("List<String> sample"),
                "the probe went back to a sample of bare uids");
    }

    // ------------------------------------------------------------------ helpers

    private static String read(String path) throws IOException {
        java.nio.file.Path file = Paths.get(path);
        assertTrue(Files.isRegularFile(file), "missing source file " + path
                + " -- this test reads sources, so a move breaks it silently otherwise");
        return Files.readString(file, StandardCharsets.UTF_8);
    }


    private static boolean hasHttpVerb(Method m) {
        return m.isAnnotationPresent(jakarta.ws.rs.GET.class)
                || m.isAnnotationPresent(jakarta.ws.rs.POST.class)
                || m.isAnnotationPresent(jakarta.ws.rs.PUT.class)
                || m.isAnnotationPresent(jakarta.ws.rs.DELETE.class);
    }

    private static boolean returnsSystemShape(Method m) {
        Class<?> returned = m.getReturnType();
        if (SystemPublicationSeriesVo.class.isAssignableFrom(returned)
                || SystemPublicationIssueVo.class.isAssignableFrom(returned)) {
            return true;
        }
        // A List<SystemXVo> hides the element type at runtime, so read the generic.
        String generic = m.getGenericReturnType().getTypeName();
        if (generic.contains("SystemPublicationSeriesVo") || generic.contains("SystemPublicationIssueVo")) {
            return true;
        }

        // An ENVELOPE hides them one level further down. This matters more than
        // it looks: wrapping a list of system VOs in a result type is an ordinary
        // refactor, and without this the endpoint silently drops out of the tier
        // check -- the check keeps passing, over one endpoint fewer.
        return carriesSystemShape(returned);
    }

    /** Whether a type declares a field that is, or contains, a system VO. */
    private static boolean carriesSystemShape(Class<?> type) {
        if (type == null || type.getName().startsWith("java.")) {
            return false;
        }
        for (var f : type.getDeclaredFields()) {
            String declared = f.getGenericType().getTypeName();
            if (declared.contains("SystemPublicationSeriesVo")
                    || declared.contains("SystemPublicationIssueVo")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The expected public window reaches the wire, under both names, as epoch
     * milliseconds -- and only on the editor shape.
     *
     * IT IS A FORECAST BESIDE A RECORD, and the pair only works if a client can
     * tell them apart. publicFrom and publicTo are what happened; expectedPublicFrom
     * and expectedPublicTo are what is planned, projected by the issue list for the
     * two rows that have no window to show -- the issue still being assembled, and
     * the current edition whose end nothing has decided yet. A client that could
     * not distinguish them would render a plan as a fact and put a publication on
     * the public site, on screen, from a date nobody chose.
     *
     * ABSENT RATHER THAN NULL WHERE THERE IS NO FORECAST, which is the inherited
     * policy and is asserted here because this pair is the one where a null would
     * be read as an answer.
     *
     * AND NOT ON THE PUBLIC SHAPE AT ALL. A public reader asking what is current
     * gets the window; an expectation about an unpublished edition is an editorial
     * plan, and a field that is not there cannot leak.
     */
    @Test
    public void theExpectedPublicWindowIsOnTheEditorShapeAlone() throws Exception {
        Set<String> publicIssueFields = declaredFields(PublicationIssueVo.class);
        for (String forecast : List.of("expectedPublicFrom", "expectedPublicTo")) {
            assertFalse(publicIssueFields.contains(forecast),
                    "PublicationIssueVo declares " + forecast + "; a public reader would take an "
                            + "editorial plan for the window the publication is actually on");
            assertTrue(declaredFields(SystemPublicationIssueVo.class).contains(forecast),
                    "the editor shape has lost " + forecast + ", so the issue list has nowhere to "
                            + "put the forecast it computes");
        }

        ObjectMapper mapper = new ObjectMapper();

        SystemPublicationIssueVo forecast = new SystemPublicationIssueVo();
        forecast.setPublicId("next");
        forecast.setExpectedPublicFrom(new java.util.Date(1_787_133_600_000L));
        forecast.setExpectedPublicTo(new java.util.Date(1_787_738_400_000L));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(forecast));

        assertEquals(1_787_133_600_000L, json.get("expectedPublicFrom").asLong(),
                "the forecast must travel as epoch milliseconds, like every other instant here");
        assertEquals(1_787_738_400_000L, json.get("expectedPublicTo").asLong());

        SystemPublicationIssueVo published = new SystemPublicationIssueVo();
        published.setPublicId("current");
        published.setPublicFrom(new java.util.Date(1_786_528_800_000L));
        JsonNode stored = mapper.readTree(mapper.writeValueAsString(published));

        assertTrue(stored.has("publicFrom"), "the fixture is pointless if nothing is emitted");
        assertFalse(stored.has("expectedPublicFrom"),
                "a row with no forecast emits the key anyway; a null there reads as 'we looked and "
                        + "there is no plan', which is a different claim from 'this row has none'");
        assertFalse(stored.has("expectedPublicTo"));
    }

    private static Set<String> declaredFields(Class<?> type) {
        Set<String> out = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var f : c.getDeclaredFields()) {
                out.add(f.getName());
            }
        }
        return out;
    }
}
