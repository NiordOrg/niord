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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.PublicationResolver;
import org.niord.core.publication.series.PublicationDomainGuard;
import org.niord.core.publication.series.PublishChecklistService;
import org.niord.core.publication.series.vo.PublicationIssueVo;
import org.niord.core.publication.series.vo.PublicationSeriesVo;
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
                "membershipProvenance", "repoPath")) {
            assertFalse(publicIssueFields.contains(leaky),
                    "PublicationIssueVo declares " + leaky
                            + "; out of context a public reader would take it for the issue period");
        }

        // And the system shapes DO carry them, so the split is a split rather
        // than a deletion.
        assertTrue(declaredFields(SystemPublicationSeriesVo.class).contains("criteria"));
        assertTrue(declaredFields(SystemPublicationIssueVo.class).contains("snapshotIntervalFrom"));
    }

    /**
     * Every field of a release-rail row reaches the wire, under its own name.
     *
     * The rail row is mapped into the response by hand, key by key, so a field
     * added to the record is carried by nothing until somebody adds a line here
     * too -- and the endpoint keeps answering, one field short, with no failure
     * anywhere to say so. A client reading the missing key gets `undefined`,
     * which for `applicable` means every row counts and for `passed` means every
     * row fails; the shape is the contract, so it is asserted rather than
     * assumed.
     *
     * Matched on the ACCESSOR rather than the key, so a key spelled differently
     * from the component it carries is caught as well as an absent one.
     */
    @Test
    public void everyRailRowFieldReachesTheWire() throws IOException {
        String src = Files.readString(
                Paths.get("src/main/java/org/niord/web/publication/PublicationIssueRestService.java"),
                StandardCharsets.UTF_8);

        Pattern emitted = Pattern.compile("row\\.put\\(\\s*\"(\\w+)\"\\s*,\\s*r\\.(\\w+)\\(\\)");
        Map<String, String> keyOf = new LinkedHashMap<>();
        Matcher m = emitted.matcher(src);
        while (m.find()) {
            keyOf.put(m.group(2), m.group(1));
        }

        for (RecordComponent component : PublishChecklistService.CheckRow.class.getRecordComponents()) {
            String key = keyOf.get(component.getName());
            assertNotNull(key, "the rail row declares " + component.getName()
                    + ", which the checklist endpoint never puts on the wire -- the field exists on the "
                    + "server and is invisible to every client");
            assertEquals(component.getName(), key,
                    "the rail row's " + component.getName() + " goes out as \"" + key
                            + "\"; one name, or the client is reading a field nobody sends");
        }

        assertTrue(keyOf.containsKey("applicable"),
                "the rail no longer says which of its rows this issue can even be in; a verdict counted "
                        + "over all fifteen rows counts checks that never ran");
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
                "ISSUE_NOT_DELETABLE", "SERIES_HAS_ISSUES", "CATEGORY_IN_USE")) {
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
