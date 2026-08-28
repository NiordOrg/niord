package org.niord.web.publication;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import org.niord.core.user.Roles;
import org.niord.web.PublicationRestService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every publication endpoint, every caller, one declared table.
 *
 * WHY A TABLE RATHER THAN A RULE. The tier of an endpoint is a decision, not
 * something derivable from its shape: /by-ids is anonymous on purpose and
 * /picker is not, though they return the same rows. A rule expressive enough to
 * cover both would be a restatement of the table with the exceptions hidden
 * inside it. So the table is written out, and this test asserts the code against
 * it -- which also means adding an endpoint FAILS here until somebody writes down
 * what tier it is, and that is the property worth having. The gap this closes is
 * an endpoint that was added later and forgot: the existing contract test only
 * looks at endpoints returning a system shape, so a lean read left anonymous by
 * accident was invisible to it.
 *
 * Reflection over annotations, no server and no database. What is checked is the
 * GATE, which is what the container enforces -- not the body of the method, which
 * cannot let a caller past the gate.
 */
public class PublicationTierMatrixTest {

    // ------------------------------------------------------------------ tiers

    /**
     * The four tiers, and who satisfies each.
     *
     * The role constants are COMPOSITE in Keycloak -- sysadmin implies admin
     * implies editor implies user -- so a tier names the weakest role that gets
     * in and every stronger one follows. PUBLICATION_CURATE sits outside that
     * ladder: it is a client role granted on top, so a curator is modelled here
     * as an ordinary user who has been given it.
     */
    private enum Tier {
        /** No credential at all. */
        ANONYMOUS,
        /** Any logged-in user, which is every editor, admin and sysadmin. */
        EDITOR,
        /** Admin and above. */
        ADMIN,
        /** Admin, or a user holding the curation role. */
        CURATOR,
        /**
         * Sysadmin only. No publication endpoint declares it, and that is the
         * point of naming it: the tier exists in the codebase, so "nothing here
         * needs it" has to be something the table can say rather than something
         * a reader infers from its absence.
         */
        SYSADMIN
    }

    /** The five callers the matrix is asserted over. */
    private enum Caller {
        ANONYMOUS(Set.of()),
        USER(Set.of(Roles.USER)),
        CURATE(Set.of(Roles.USER, Roles.PUBLICATION_CURATE)),
        ADMIN(Set.of(Roles.USER, Roles.EDITOR, Roles.ADMIN)),
        SYSADMIN(Set.of(Roles.USER, Roles.EDITOR, Roles.ADMIN, Roles.SYSADMIN));

        final Set<String> roles;

        Caller(Set<String> roles) {
            this.roles = roles;
        }
    }

    /** Whether a tier admits a caller. The expected half of the matrix. */
    private static boolean admits(Tier tier, Caller caller) {
        return switch (tier) {
            case ANONYMOUS -> true;
            case EDITOR -> caller.roles.contains(Roles.USER);
            case ADMIN -> caller.roles.contains(Roles.ADMIN);
            case CURATOR -> caller.roles.contains(Roles.ADMIN)
                    || caller.roles.contains(Roles.PUBLICATION_CURATE);
            case SYSADMIN -> caller.roles.contains(Roles.SYSADMIN);
        };
    }

    // ------------------------------------------------------- the declared table

    /**
     * The contract, written out. Key is "VERB /full/path".
     *
     * Read it as the answer to "who may call this", not as a description of what
     * the code does -- the point of the file is that the two are compared.
     */
    private static Map<String, Tier> declaredTable() {
        Map<String, Tier> t = new LinkedHashMap<>();

        // ---- series -----------------------------------------------------
        // Reads are EDITOR: a series list names publications and their
        // categories, and enumerating those in every lifecycle state to an
        // unauthenticated caller is the catalogue leak the redesign removes.
        t.put("GET /publication-series/search", Tier.EDITOR);
        t.put("GET /publication-series/series/{seriesId}", Tier.EDITOR);
        t.put("GET /publication-series/series-by-ids/{seriesIds}", Tier.EDITOR);
        // A fixed vocabulary of placeholder names; it discloses no series.
        t.put("GET /publication-series/name-tokens", Tier.EDITOR);
        // Everything below returns the system shape or changes something.
        t.put("GET /publication-series/search-details", Tier.ADMIN);
        t.put("GET /publication-series/editable-series/{seriesId}", Tier.ADMIN);
        t.put("GET /publication-series/new-series-template", Tier.ADMIN);
        t.put("GET /publication-series/copy-series-template/{seriesId}", Tier.ADMIN);
        t.put("GET /publication-series/series/{seriesId}/issue-draft", Tier.ADMIN);
        t.put("POST /publication-series/resolve-preview", Tier.ADMIN);
        t.put("POST /publication-series/series/", Tier.ADMIN);
        t.put("PUT /publication-series/series/{seriesId}", Tier.ADMIN);
        t.put("PUT /publication-series/series/{seriesId}/status", Tier.ADMIN);
        t.put("PUT /publication-series/series/{seriesId}/public-authority", Tier.ADMIN);
        t.put("PUT /publication-series/public-authority", Tier.ADMIN);
        t.put("DELETE /publication-series/series/{seriesId}", Tier.ADMIN);
        t.put("POST /publication-series/validate", Tier.ADMIN);
        t.put("POST /publication-series/import-legacy/validate", Tier.ADMIN);
        t.put("POST /publication-series/import-legacy", Tier.ADMIN);
        t.put("DELETE /publication-series/import-legacy", Tier.ADMIN);
        t.put("GET /publication-series/diagnostic-report", Tier.ADMIN);
        t.put("POST /publication-series/shadow-diff/run", Tier.ADMIN);
        t.put("POST /publication-series/shadow-diff/reset", Tier.ADMIN);
        t.put("GET /publication-series/shadow-diff", Tier.ADMIN);
        t.put("GET /publication-series/cutover-preflight", Tier.ADMIN);
        t.put("GET /publication-series/export", Tier.ADMIN);
        // The import matches the export beside it: an admin who can produce the
        // file, and can author every series in it by hand, gains nothing from
        // being refused the upload.
        t.put("POST /publication-series/upload-series", Tier.ADMIN);

        // ---- issues -----------------------------------------------------
        // The three EDITOR reads are the ones an editor needs while writing a
        // message: which publication is this, which can I cite, where has this
        // message been published.
        t.put("GET /publication-issues/issue/{publicId}", Tier.EDITOR);
        t.put("GET /publication-issues/picker", Tier.EDITOR);
        t.put("GET /publication-issues/recent", Tier.EDITOR);
        t.put("GET /publication-issues/by-message/{messageUid}", Tier.EDITOR);
        // Hydration of ids the caller already holds. Anonymous on purpose: a
        // citation chip for a RETIRED issue has to render its title on the
        // public site, and that is not enumeration -- it resolves nothing the
        // caller was not already holding.
        t.put("GET /publication-issues/by-ids", Tier.ANONYMOUS);
        // The curation surface: the three writes, the standing decisions, and
        // the three reads a curator needs to make a decision at all.
        t.put("GET /publication-issues/editable-issue/{publicId}", Tier.CURATOR);
        t.put("GET /publication-issues/issue/{publicId}/members", Tier.CURATOR);
        t.put("GET /publication-issues/issue/{publicId}/audit", Tier.CURATOR);
        t.put("GET /publication-issues/issue/{publicId}/overrides", Tier.CURATOR);
        t.put("PUT /publication-issues/issue/{publicId}/overrides/include", Tier.CURATOR);
        t.put("PUT /publication-issues/issue/{publicId}/overrides/exclude", Tier.CURATOR);
        t.put("DELETE /publication-issues/issue/{publicId}/overrides/{messageUid}", Tier.CURATOR);
        // Everything that decides what the public reads.
        t.put("GET /publication-issues/series/{seriesId}", Tier.ADMIN);
        t.put("POST /publication-issues/issue", Tier.ADMIN);
        t.put("PUT /publication-issues/issue/{publicId}", Tier.ADMIN);
        t.put("DELETE /publication-issues/issue/{publicId}", Tier.ADMIN);
        t.put("POST /publication-issues/issue/{publicId}/preview", Tier.ADMIN);
        t.put("GET /publication-issues/issue/{publicId}/preview/{lang}", Tier.ADMIN);
        t.put("GET /publication-issues/issue/{publicId}/publish-checklist", Tier.ADMIN);
        t.put("PUT /publication-issues/issue/{publicId}/publish", Tier.ADMIN);
        t.put("PUT /publication-issues/issue/{publicId}/amend", Tier.ADMIN);
        t.put("POST /publication-issues/issue/{publicId}/new-edition", Tier.ADMIN);
        t.put("PUT /publication-issues/issue/{publicId}/retire", Tier.ADMIN);
        t.put("PUT /publication-issues/issue/{publicId}/reactivate", Tier.ADMIN);
        t.put("POST /publication-issues/issue/{publicId}/file/{lang}", Tier.ADMIN);
        t.put("DELETE /publication-issues/issue/{publicId}/file/{lang}", Tier.ADMIN);
        t.put("PUT /publication-issues/issue/{publicId}/link/{lang}", Tier.ADMIN);

        // ---- categories -------------------------------------------------
        // The two anonymous reads are INHERITED and must stay that way: the
        // admin "Export…" item is a bare href with no role and no ticket, and
        // external consumers read the category list anonymously. A category is a
        // section heading, not a publication.
        t.put("GET /publication-categories/all", Tier.ANONYMOUS);
        t.put("GET /publication-categories/publication-category/{categoryId}", Tier.ANONYMOUS);
        t.put("POST /publication-categories/publication-category/", Tier.ADMIN);
        t.put("PUT /publication-categories/publication-category/{categoryId}", Tier.ADMIN);
        t.put("DELETE /publication-categories/publication-category/{categoryId}", Tier.ADMIN);
        t.put("POST /publication-categories/upload-publication-categories", Tier.ADMIN);

        // ---- one-offs ---------------------------------------------------
        t.put("GET /one-off-publications/", Tier.ADMIN);
        t.put("POST /one-off-publications/", Tier.ADMIN);
        t.put("PUT /one-off-publications/{seriesId}", Tier.ADMIN);

        // ---- the legacy publication resource ----------------------------
        // FROZEN. These are not the redesign's endpoints and their tiers are the
        // shipped external contract; they are in the table so that a change to
        // one is a deliberate edit here rather than a silent drift.
        //
        // /publication/{ids} is the citation renderer, and it resolves at the
        // INTERNAL audience unconditionally -- not derived from the caller. Its
        // job is to decide whether a stored citation renders a title, not what
        // content anybody is served; the tier gate that decides content is on
        // the message search.
        t.put("GET /publications/search", Tier.ANONYMOUS);
        t.put("GET /publications/all", Tier.ANONYMOUS);
        t.put("GET /publications/publication/{publicationIds}", Tier.ANONYMOUS);
        t.put("GET /publications/export", Tier.ANONYMOUS);
        t.put("GET /publications/search-details", Tier.EDITOR);
        t.put("GET /publications/editable-publication/{publicationId}", Tier.ADMIN);
        t.put("GET /publications/new-publication-template", Tier.ADMIN);
        t.put("GET /publications/copy-publication-template/{publicationId}", Tier.ADMIN);
        t.put("POST /publications/publication/", Tier.ADMIN);
        t.put("PUT /publications/publication/{publicationId}", Tier.ADMIN);
        t.put("DELETE /publications/publication/{publicationId}", Tier.ADMIN);
        t.put("PUT /publications/update-status", Tier.ADMIN);
        t.put("POST /publications/generate-publication-report/{folder:.+}", Tier.ADMIN);
        t.put("PUT /publications/release-publication/{publicationId}", Tier.ADMIN);
        t.put("POST /publications/upload-publication-file/{folder:.+}", Tier.ADMIN);
        t.put("POST /publications/upload-publications", Tier.ADMIN);

        return t;
    }

    /** The four resources the redesign owns, plus the legacy one it unions with. */
    private static final List<Class<?>> RESOURCES = List.of(
            PublicationSeriesRestService.class,
            PublicationIssueRestService.class,
            PublicationCategoryRestService.class,
            OneOffRestService.class,
            PublicationRestService.class);

    /** The redesign's own resources: the ones the no-enumeration rule binds. */
    private static final List<Class<?>> NEW_RESOURCES = List.of(
            PublicationSeriesRestService.class,
            PublicationIssueRestService.class,
            PublicationCategoryRestService.class,
            OneOffRestService.class);

    // -------------------------------------------------------------- the matrix

    /**
     * Every endpoint is in the table, and the table is not stale.
     *
     * Both directions. A missing entry means an endpoint shipped without anybody
     * deciding its tier; a leftover one means the table describes a route that no
     * longer exists, and a table nobody has to keep true stops being read.
     */
    @Test
    public void theTableCoversExactlyTheEndpointsThatExist() {
        Set<String> found = new TreeSet<>();
        for (Class<?> resource : RESOURCES) {
            for (Endpoint e : endpointsOf(resource)) {
                found.add(e.key);
            }
        }
        Set<String> declared = new TreeSet<>(declaredTable().keySet());

        Set<String> undeclared = new TreeSet<>(found);
        undeclared.removeAll(declared);
        Set<String> phantom = new TreeSet<>(declared);
        phantom.removeAll(found);

        if (!undeclared.isEmpty() || !phantom.isEmpty()) {
            fail("the tier table and the code disagree.\n  endpoints with no declared tier:\n    "
                    + String.join("\n    ", undeclared)
                    + "\n  declared tiers with no endpoint:\n    "
                    + String.join("\n    ", phantom));
        }
    }

    /**
     * The matrix itself: every endpoint against every caller.
     *
     * The expected side comes from the table; the actual side comes from the
     * annotations the container enforces. Both are computed the same way for all
     * five callers, so the assertion is symmetric -- it catches a gate that is
     * too tight as loudly as one that is too loose, and an over-tight gate is the
     * failure that ships as a button answering 403.
     */
    @Test
    public void everyEndpointAdmitsExactlyTheCallersItsTierNames() {
        Map<String, Tier> table = declaredTable();
        List<String> offenders = new ArrayList<>();

        for (Class<?> resource : RESOURCES) {
            for (Endpoint e : endpointsOf(resource)) {
                Tier tier = table.get(e.key);
                if (tier == null) {
                    continue; // reported by the coverage test above
                }
                for (Caller caller : Caller.values()) {
                    boolean expected = admits(tier, caller);
                    boolean actual = e.admits(caller);
                    if (expected != actual) {
                        offenders.add(e.key + " is declared " + tier + " but "
                                + (actual ? "ADMITS " : "REFUSES ") + caller
                                + " (gate: " + e.gate() + ")");
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            offenders.sort(Comparator.naturalOrder());
            fail("the tier matrix does not hold:\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * Anonymous enumeration of series or issues is impossible.
     *
     * The whole point of the tier work, stated as one assertion over the surface
     * rather than endpoint by endpoint -- which is the only form that catches the
     * endpoint added next year. Exactly two anonymous routes survive on the
     * redesign's resources and both are inherited contracts: the category list,
     * which is a set of section headings behind a documented anonymous export
     * link, and hydration by explicit id, which resolves nothing the caller did
     * not already hold.
     *
     * The legacy /publications resource is deliberately out of scope. It is
     * anonymous today, it is the frozen external contract, and tightening it
     * would break the public site and every stored citation -- the redesign
     * replaces what it serves rather than re-gating it.
     */
    @Test
    public void nothingElseIsAnonymousOnTheNewResources() {
        Set<String> anonymous = new TreeSet<>();
        for (Class<?> resource : NEW_RESOURCES) {
            for (Endpoint e : endpointsOf(resource)) {
                if (e.admits(Caller.ANONYMOUS)) {
                    anonymous.add(e.key);
                }
            }
        }

        assertEquals(Set.of(
                        "GET /publication-categories/all",
                        "GET /publication-categories/publication-category/{categoryId}",
                        "GET /publication-issues/by-ids"),
                anonymous,
                "an anonymous route appeared on the publication surface, or one of the two "
                        + "inherited exceptions was tightened. Both are contract changes and neither "
                        + "may happen by accident: a new anonymous read enumerates the catalogue, "
                        + "and tightening /publication-categories/all breaks a documented export "
                        + "link that carries no credential at all.");
    }

    /** And no series or issue route is reachable without a credential. */
    @Test
    public void noSeriesOrIssueRouteIsReachableAnonymously() {
        for (Class<?> resource : List.of(PublicationSeriesRestService.class,
                PublicationIssueRestService.class, OneOffRestService.class)) {
            for (Endpoint e : endpointsOf(resource)) {
                if (e.key.equals("GET /publication-issues/by-ids")) {
                    continue;
                }
                assertFalse(e.admits(Caller.ANONYMOUS),
                        e.key + " is reachable anonymously. A series or issue route that answers "
                                + "without a credential lets anybody walk the catalogue, including "
                                + "the drafts and the withdrawn ones.");
            }
        }
    }

    // ------------------------------------------------------- what EDITOR sees

    /**
     * Field names that must not appear anywhere an editor-tier response can reach.
     *
     * Each is out for its own reason, and the reasons are different in kind: the
     * criteria documents are the authoring surface, the paths describe the layout
     * of a repository that is served anonymously, the render settings are
     * operational configuration, the automation columns are the cutover controls,
     * and the curation author and reason are somebody's name attached to an
     * editorial decision. What they share is that no editor-tier screen consumes
     * any of them, so carrying them is cost with no reader.
     */
    private static final Set<String> HIDDEN_FROM_EDITOR = new LinkedHashSet<>(List.of(
            // the authoring surface
            "criteria", "criteriaOverride", "criteriaSnapshot",
            // the filesystem layout of a repository anyone can read
            "repoPath", "filePath", "fileHash", "fileSize", "archivePath",
            // render configuration
            "reportId", "pageSize", "pageOrientation", "mapThumbnails", "reportParams",
            "messageSortBy", "messageSortOrder",
            // automation and cutover
            "releaseMode", "nextIssueCreation", "publicAuthority", "firstIssueStartsAt",
            // operator identity and provenance
            "statusChangedBy", "statusChangeReason", "publishedBy", "retiredBy", "retiredReason",
            "importSource", "legacyTemplateId", "legacyPublicationId", "membershipProvenanceNote"));

    /**
     * Nothing an editor-tier endpoint returns declares a hidden field.
     *
     * Structural rather than behavioural, and that is the stronger form: a field
     * that is not on the class cannot leak through a code path somebody adds
     * later, and the type is the same thing the tier split is built on.
     *
     * The whole reachable graph is walked, not just the top-level type. An
     * envelope around a list of value objects is an ordinary refactor, and a
     * check that stopped at the envelope would keep passing over one type fewer.
     */
    @Test
    public void noEditorTierResponseCanCarryAHiddenField() {
        Map<String, Tier> table = declaredTable();
        List<String> offenders = new ArrayList<>();

        for (Class<?> resource : NEW_RESOURCES) {
            for (Endpoint e : endpointsOf(resource)) {
                if (table.get(e.key) != Tier.EDITOR && table.get(e.key) != Tier.ANONYMOUS) {
                    continue;
                }
                Set<Class<?>> graph = new LinkedHashSet<>();
                collectTypes(e.method.getGenericReturnType(), graph, 0);
                for (Class<?> type : graph) {
                    for (String field : declaredFields(type)) {
                        if (HIDDEN_FROM_EDITOR.contains(field)) {
                            offenders.add(e.key + " returns " + type.getSimpleName()
                                    + ", which declares '" + field + "'");
                        }
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            fail("the editor tier can reach a field it is not meant to:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    /**
     * The curation author and reason are not on the shape an editor reads.
     *
     * Named separately because it is the one hidden field that is about a person
     * rather than about configuration: "who excluded this message, and what did
     * they say about it" is an editorial conversation, and the tier that reads it
     * is the tier that takes part in it.
     */
    @Test
    public void theCurationAuthorAndReasonAreNotOnAnEditorTierShape() {
        Map<String, Tier> table = declaredTable();
        for (Class<?> resource : NEW_RESOURCES) {
            for (Endpoint e : endpointsOf(resource)) {
                if (table.get(e.key) != Tier.EDITOR) {
                    continue;
                }
                Set<Class<?>> graph = new LinkedHashSet<>();
                collectTypes(e.method.getGenericReturnType(), graph, 0);
                for (Class<?> type : graph) {
                    assertFalse(type.getSimpleName().contains("Curation")
                                    || type.getSimpleName().equals("IssueOverrideVo"),
                            e.key + " returns " + type.getSimpleName()
                                    + ", which carries the curation author and reason");
                }
            }
        }
    }

    /**
     * The lean series shape carries no authoring pattern VALUES.
     *
     * The fields stay on the class -- the system shape and the save path both use
     * them -- so the split here is in the mapping rather than in the type, and a
     * mapping is exactly the kind of thing that gets undone by a well-meaning
     * refactor. Asserted on a real conversion for that reason.
     */
    @Test
    public void theLeanSeriesShapeCarriesNoPatterns() {
        org.niord.core.publication.series.PublicationSeries s =
                new org.niord.core.publication.series.PublicationSeries();
        s.setSeriesId("tier-matrix-fixture");
        org.niord.core.publication.series.PublicationSeriesDesc d = s.createDesc("da");
        d.setName("Efterretninger for Søfarende");
        d.setNameSuggestionPattern("EfS uge ${week}, ${year}");
        d.setFileNamePattern("efs-${week}-${year}.pdf");
        d.setLinkPattern("/publications/efs-${week}.pdf");
        d.setMessageReferenceFormat("EfS ${week}/${year}");

        var lean = s.toVo(org.niord.core.publication.series.vo.PublicationSeriesVo.class);
        var desc = lean.getDescs().get(0);
        assertEquals("Efterretninger for Søfarende", desc.getName(),
                "the name is what the lean shape is FOR; hiding it would empty every picker");
        String why = "the lean series shape carries an authoring pattern. A pattern is the recipe "
                + "every future issue's name, file and link are minted from -- an editor reads the "
                + "EXPANDED value off the issue and has no use for the recipe.";
        assertNull(desc.getNameSuggestionPattern(), why);
        assertNull(desc.getFileNamePattern(), why);
        assertNull(desc.getLinkPattern(), why);
        assertNull(desc.getMessageReferenceFormat(), why);

        var system = s.toVo(org.niord.core.publication.series.vo.SystemPublicationSeriesVo.class);
        assertEquals("EfS uge ${week}, ${year}", system.getDescs().get(0).getNameSuggestionPattern(),
                "the system shape must still carry them, or the settings form has nothing to edit");
    }

    // ------------------------------------------------------------------ engine

    /** One routed method, with its full path and its effective gate. */
    private record Endpoint(String key, Method method, PermitAll permitAll,
                            RolesAllowed rolesAllowed, DenyAll denyAll) {

        boolean admits(Caller caller) {
            if (denyAll != null) {
                return false;
            }
            if (rolesAllowed != null) {
                return Arrays.stream(rolesAllowed.value()).anyMatch(caller.roles::contains);
            }
            // No annotation at all is the container's default, which for these
            // resources is "deny" -- but every route here carries one, and the
            // coverage test would have named it if not.
            return permitAll != null;
        }

        String gate() {
            if (denyAll != null) {
                return "@DenyAll";
            }
            if (rolesAllowed != null) {
                return "@RolesAllowed" + Arrays.toString(rolesAllowed.value());
            }
            return permitAll != null ? "@PermitAll" : "(none)";
        }
    }

    /**
     * Every routed method of a resource, with the class-level gate inherited.
     *
     * Inherited the way the container inherits it: a method annotation replaces
     * the class one entirely rather than adding to it, so a method that declares
     * @RolesAllowed on a @PermitAll class is NOT anonymous.
     */
    private static List<Endpoint> endpointsOf(Class<?> resource) {
        String base = resource.isAnnotationPresent(Path.class)
                ? resource.getAnnotation(Path.class).value() : "";
        PermitAll classPermit = resource.getAnnotation(PermitAll.class);
        RolesAllowed classRoles = resource.getAnnotation(RolesAllowed.class);
        DenyAll classDeny = resource.getAnnotation(DenyAll.class);

        List<Endpoint> out = new ArrayList<>();
        for (Method m : resource.getDeclaredMethods()) {
            String verb = verbOf(m);
            if (verb == null) {
                continue;
            }
            String suffix = m.isAnnotationPresent(Path.class) ? m.getAnnotation(Path.class).value() : "";
            String path = join(base, suffix);

            boolean declaresOwn = m.isAnnotationPresent(PermitAll.class)
                    || m.isAnnotationPresent(RolesAllowed.class)
                    || m.isAnnotationPresent(DenyAll.class);

            out.add(new Endpoint(verb + " " + path, m,
                    declaresOwn ? m.getAnnotation(PermitAll.class) : classPermit,
                    declaresOwn ? m.getAnnotation(RolesAllowed.class) : classRoles,
                    declaresOwn ? m.getAnnotation(DenyAll.class) : classDeny));
        }
        return out;
    }

    private static String verbOf(Method m) {
        if (m.isAnnotationPresent(GET.class)) {
            return "GET";
        }
        if (m.isAnnotationPresent(POST.class)) {
            return "POST";
        }
        if (m.isAnnotationPresent(PUT.class)) {
            return "PUT";
        }
        if (m.isAnnotationPresent(DELETE.class)) {
            return "DELETE";
        }
        return null;
    }

    /** "/a" + "/b" without doubling or dropping the separator, keeping a trailing one. */
    private static String join(String base, String suffix) {
        if (suffix.isEmpty()) {
            return base;
        }
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String s = suffix.startsWith("/") ? suffix : "/" + suffix;
        return b + s;
    }

    /**
     * Every publication type reachable from a declared type, to a bounded depth.
     *
     * Bounded rather than exhaustive because the graph reaches the message model
     * and beyond; four levels covers an envelope holding a list of value objects
     * holding a list of desc rows, which is the deepest shape on this surface.
     */
    private static void collectTypes(Type type, Set<Class<?>> into, int depth) {
        if (depth > 4) {
            return;
        }
        if (type instanceof ParameterizedType p) {
            collectTypes(p.getRawType(), into, depth);
            for (Type arg : p.getActualTypeArguments()) {
                collectTypes(arg, into, depth);
            }
            return;
        }
        if (!(type instanceof Class<?> c) || !ours(c) || !into.add(c)) {
            return;
        }
        for (Class<?> up = c.getSuperclass(); up != null && ours(up); up = up.getSuperclass()) {
            into.add(up);
        }
        for (Field f : allFields(c)) {
            collectTypes(f.getGenericType(), into, depth + 1);
        }
    }

    /** Only this repository's own types; the JDK and the message model are not the subject. */
    private static boolean ours(Class<?> c) {
        return c.getName().startsWith("org.niord.core.publication")
                || c.getName().startsWith("org.niord.model.publication");
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            out.addAll(List.of(c.getDeclaredFields()));
        }
        return out;
    }

    private static Set<String> declaredFields(Class<?> type) {
        Set<String> out = new LinkedHashSet<>();
        for (Field f : allFields(type)) {
            out.add(f.getName());
        }
        return out;
    }
}
