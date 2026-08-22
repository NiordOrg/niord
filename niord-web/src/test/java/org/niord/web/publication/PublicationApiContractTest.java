package org.niord.web.publication;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.vo.PublicationIssueVo;
import org.niord.core.publication.series.vo.PublicationSeriesVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Every error code thrown anywhere is in the catalogue.
     *
     * A code that is not mapped falls through to 500, so a perfectly ordinary
     * state conflict reads to a client as "the server broke" -- and a client that
     * retries on 5xx will retry something that can never succeed.
     */
    @Test
    public void everyThrownErrorCodeIsInTheCatalogue() throws IOException {
        Pattern thrown = Pattern.compile("TransitionRefusedException\\(\\s*\"([A-Z_]+)\"");
        Set<String> codes = new LinkedHashSet<>();

        for (String root : List.of("../niord-core/src/main/java", "src/main/java")) {
            java.nio.file.Path dir = Paths.get(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<java.nio.file.Path> files = Files.walk(dir)) {
                for (java.nio.file.Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher m = thrown.matcher(Files.readString(f, StandardCharsets.UTF_8));
                    while (m.find()) {
                        codes.add(m.group(1));
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
            assertTrue(status == 400 || status == 404 || status == 409 || status == 500,
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

    // ------------------------------------------------------------------ helpers

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
        return generic.contains("SystemPublicationSeriesVo") || generic.contains("SystemPublicationIssueVo");
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
