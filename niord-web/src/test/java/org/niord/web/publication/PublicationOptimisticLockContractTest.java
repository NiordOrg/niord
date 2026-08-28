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

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.StaleVersionGuard;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every write on the publication surface asks whose revision it is writing over.
 *
 * The guard it enforces is unusual in that a missing call has no symptom. The
 * endpoint works; the save returns 200; the only evidence that one admin's
 * correction was reverted by another's stale form is a diff nobody reads. So the
 * absence has to be made visible some other way, and this is it: a method that
 * changes a series or an issue must either carry {@link VersionChecked} or be
 * named, with a reason, on the list below -- and a method that carries the
 * annotation must actually call the guard, which is read out of the source
 * because an annotation on its own is a comment that can go stale.
 *
 * Reflection and text. No server, no database -- the same shape as the tier
 * matrix beside it, whose source scanner this reuses rather than copying, so
 * there is one answer to "what is this method's body".
 */
public class PublicationOptimisticLockContractTest {

    /**
     * The three resources that write a series or an issue.
     *
     * Categories are deliberately absent: they are not part of this surface's
     * concurrency story, they carry none of the derived state a stale save could
     * revert, and adding them here would mean either guarding them or writing an
     * exemption for four endpoints nobody has ever collided on.
     */
    private static final List<Class<?>> RESOURCES = List.of(
            PublicationSeriesRestService.class,
            PublicationIssueRestService.class,
            OneOffRestService.class);

    /**
     * The writes that name no stored revision, and why each one does not.
     *
     * Written down rather than derived. "Does this write target a row the caller
     * has already read" is a fact about what the endpoint IS, and every rule
     * general enough to compute it would end up being this list with the reasons
     * deleted. Spelling them out is also what makes adding an unguarded write a
     * deliberate act: the test stays red until somebody says here why the new one
     * needs no revision.
     */
    private static Map<String, String> unversionedWrites() {
        Map<String, String> t = new LinkedHashMap<>();

        // Creates. There is no stored row yet, so there is nothing a caller could
        // hold a stale revision OF. The collision a create can have is a duplicate
        // id, and that is answered by SERIES_ID_TAKEN.
        String noRowYet = "a create: there is no stored row to hold a revision of";
        t.put("POST /publication-series/series/", noRowYet);
        t.put("POST /publication-issues/issue", noRowYet);
        t.put("POST /one-off-publications/", noRowYet);

        // Persist nothing. A POST because the input is a document too large for a
        // query string, not because anything changes.
        String dryRun = "persists nothing -- a dry run";
        t.put("POST /publication-series/resolve-preview", dryRun);
        t.put("POST /publication-series/validate", dryRun);
        t.put("POST /publication-series/import-legacy/validate", dryRun);

        // Estate operations. Each one sweeps the whole catalogue by definition, so
        // there is no single row whose revision could be named -- and a revision
        // per row would be a map the caller has no way to assemble honestly.
        t.put("POST /publication-series/import-legacy", "estate-wide: the file IS every series");
        t.put("DELETE /publication-series/import-legacy", "undoes that import, same reach");
        t.put("POST /publication-series/upload-series", "the interchange import, upserting whatever the file names");
        t.put("POST /publication-series/shadow-diff/run", "writes comparison runs, not publications");
        t.put("POST /publication-series/shadow-diff/reset", "discards those comparison runs");
        t.put("PUT /publication-series/public-authority",
                "the bulk flip: one request over every series in the estate, so one revision "
                        + "token could only be right about one of them");

        // Derives rather than changes. A preview is rendered FROM the issue's
        // current state; running it against a state the caller has not seen
        // produces the same bytes the next reader would get anyway, and there is
        // no edit of theirs for it to revert.
        t.put("POST /publication-issues/issue/{publicId}/preview",
                "renders from the issue's current state; it reverts nothing a caller could have "
                        + "composed against an older one");

        return t;
    }

    // ------------------------------------------------------------ the contract

    /** Every write is version-checked, or says here why it is not. */
    @Test
    public void everyWriteIsVersionCheckedOrDeclaredExempt() {
        Map<String, String> exempt = unversionedWrites();
        List<String> offenders = new ArrayList<>();
        Set<String> seenExempt = new TreeSet<>();
        int checked = 0;

        for (Class<?> resource : RESOURCES) {
            for (Map.Entry<String, Method> e : writesOf(resource).entrySet()) {
                boolean guarded = e.getValue().isAnnotationPresent(VersionChecked.class);
                boolean declaredExempt = exempt.containsKey(e.getKey());

                if (guarded) {
                    checked++;
                }
                if (declaredExempt) {
                    seenExempt.add(e.getKey());
                }
                if (!guarded && !declaredExempt) {
                    offenders.add(e.getKey() + " changes a stored series or issue and neither carries "
                            + "@VersionChecked nor appears on the unversioned list");
                }
                if (guarded && declaredExempt) {
                    offenders.add(e.getKey() + " is both @VersionChecked and declared exempt; one of "
                            + "the two is a leftover and a reader cannot tell which");
                }
            }
        }

        Set<String> phantom = new TreeSet<>(exempt.keySet());
        phantom.removeAll(seenExempt);
        for (String gone : phantom) {
            offenders.add(gone + " is on the unversioned list but is not a write on these resources; "
                    + "a standing exemption for a route that no longer exists would silently cover "
                    + "the next endpoint that reuses the path");
        }

        assertTrue(checked >= 15,
                "only " + checked + " writes carry @VersionChecked; the reflection scan looks broken, "
                        + "and a broken scan passes over nothing rather than failing");

        if (!offenders.isEmpty()) {
            offenders.sort(Comparator.naturalOrder());
            fail("the optimistic locking of the publication writes is not declared:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    /**
     * And a method that DECLARES the check actually makes it.
     *
     * The annotation does nothing at runtime, so on its own it is a claim rather
     * than a fact. This reads the source of the body it is attached to, which is
     * the one thing that cannot drift from what the code does.
     *
     * ONE level of delegation is followed: the two curation endpoints are a single
     * line each handing off to a shared private body, and putting a copy of the
     * comparison in each of them instead is how one copy ends up missing.
     */
    @Test
    public void everyVersionCheckedMethodAsksTheGuard() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;

        for (Class<?> resource : RESOURCES) {
            String src = PublicationTierMatrixTest.sourceOf(resource);
            for (Method m : resource.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(VersionChecked.class)) {
                    continue;
                }
                checked++;
                String body = PublicationTierMatrixTest.bodyOf(src, m.getName());
                if (body == null) {
                    offenders.add(resource.getSimpleName() + "#" + m.getName()
                            + ": the source scan could not find the method body, so it cannot say "
                            + "whether the guard is called");
                    continue;
                }
                if (!asksTheGuard(src, body, 1)) {
                    offenders.add(resource.getSimpleName() + "#" + m.getName()
                            + " is annotated @VersionChecked but never calls StaleVersionGuard.check");
                }
            }
        }

        assertTrue(checked >= 15,
                "only " + checked + " methods carry @VersionChecked; the reflection scan looks broken");

        if (!offenders.isEmpty()) {
            fail("a declared version check is not enforced:\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * The check runs BEFORE the write, on every guarded method.
     *
     * Order is the whole value of the guard. A comparison made after the body has
     * been read onto the entity refuses a change that has already happened, which
     * is worse than no guard at all -- it reports the damage and then rolls back
     * a transaction whose failure the caller now has to interpret. So the call is
     * required to appear before anything that persists.
     */
    @Test
    public void theCheckComesBeforeTheWrite() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Class<?> resource : RESOURCES) {
            String src = PublicationTierMatrixTest.sourceOf(resource);
            for (Method m : resource.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(VersionChecked.class)) {
                    continue;
                }
                // Comments stripped first: several of these bodies EXPLAIN the
                // order in prose above the call, and a scan that read the
                // explanation as code would report the method it describes.
                String body = withoutComments(
                        String.valueOf(PublicationTierMatrixTest.bodyOf(src, m.getName())));
                if (!body.contains("StaleVersionGuard.check")) {
                    continue; // the delegating shapes; the test above covers them
                }
                int guard = body.indexOf("StaleVersionGuard.check");
                for (String writer : List.of("updateFromVo(", "Service.update(", "Service.create(",
                        "lifecycle.", "publishService.", "fileService.", "curation.")) {
                    int write = body.indexOf(writer);
                    if (write >= 0 && write < guard) {
                        offenders.add(resource.getSimpleName() + "#" + m.getName()
                                + " calls " + writer + " before StaleVersionGuard.check; a refused "
                                + "write must leave nothing behind");
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            fail("a version check runs too late:\n  " + String.join("\n  ", offenders));
        }
    }

    // ------------------------------------------------------------- the wire

    /** The refusal is a 409, and the catalogue is where that is decided. */
    @Test
    public void aStaleVersionIsA409() {
        assertTrue(PublicationErrorCatalogue.knows(StaleVersionGuard.STALE_VERSION),
                "STALE_VERSION is not in the catalogue, so it would come back as a 500");
        assertEquals(409, PublicationErrorCatalogue.statusOf(StaleVersionGuard.STALE_VERSION));
    }

    /**
     * And the response says which two revisions disagreed.
     *
     * Without them a client can only say "somebody changed something". With them
     * it can re-read against a revision it knows, and tell the caller how far
     * behind their form was.
     */
    @Test
    public void theRefusalCarriesBothRevisions() {
        Response response = new PublicationExceptionMapper().toResponse(
                new StaleVersionGuard.StaleVersionException("moved on", 9, 7));

        assertEquals(409, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals(StaleVersionGuard.STALE_VERSION, body.get("code"));
        assertEquals(9, body.get("storedVersion"));
        assertEquals(7, body.get("submittedVersion"));
    }

    /**
     * The revision is on the two admin shapes, and nowhere else.
     *
     * It belongs where a write can present it. On a shape nothing may be saved
     * back from it would be a token with no endpoint to give it to, and a client
     * would reasonably read its presence as a promise that one exists.
     */
    @Test
    public void theRevisionIsOnTheAdminShapesOnly() throws Exception {
        assertNotNull(org.niord.core.publication.series.vo.SystemPublicationSeriesVo.class
                .getDeclaredMethod("getVersion"));
        assertNotNull(org.niord.core.publication.series.vo.SystemPublicationIssueVo.class
                .getDeclaredMethod("getVersion"));

        for (Class<?> lean : List.of(org.niord.core.publication.series.vo.PublicationSeriesVo.class,
                org.niord.core.publication.series.vo.PublicationIssueVo.class)) {
            for (java.lang.reflect.Field f : lean.getDeclaredFields()) {
                assertTrue(!"version".equals(f.getName()),
                        lean.getSimpleName() + " declares a version field; the lean shape is not "
                                + "saved back and would be promising an endpoint that does not exist");
            }
        }
    }

    /**
     * The entity never takes the revision from the body.
     *
     * It is compared and dropped. Assigning it would let a client name any
     * revision it liked -- including the one it is about to overwrite -- which
     * makes the guard a field the caller controls, and a guard the caller
     * controls is not a guard.
     */
    @Test
    public void theEntityNeverAssignsTheRevisionFromABody() throws IOException {
        java.nio.file.Path file = java.nio.file.Paths.get("..", "niord-core", "src", "main", "java",
                "org", "niord", "core", "publication", "series", "PublicationSeries.java");
        assertTrue(java.nio.file.Files.isRegularFile(file),
                "missing source file " + file + " -- this test reads sources, so a move would "
                        + "otherwise turn it green over nothing");
        String src = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        String body = PublicationTierMatrixTest.bodyOf(src, "updateFromVo");
        assertNotNull(body, "updateFromVo was not found; the scan cannot say what it assigns");
        // The comments are stripped first, because the WHY note in that method
        // names the getter it deliberately does not call -- and a scan that could
        // not tell the two apart would be red for the explanation of why it is
        // green.
        String code = withoutComments(body);
        assertTrue(!code.contains("setVersion(") && !code.contains("vo.getVersion()"),
                "updateFromVo reads the revision off the body; the guard would then be a field "
                        + "the caller controls");
    }

    /** Java source with its line and block comments blanked out. */
    private static String withoutComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }

    // ----------------------------------------------------------------- helpers

    /** Whether a body calls the guard, or calls something in the same file that does. */
    private static boolean asksTheGuard(String src, String body, int depth) {
        if (body.contains("StaleVersionGuard.check")) {
            return true;
        }
        if (depth <= 0) {
            return false;
        }
        java.util.regex.Matcher calls =
                java.util.regex.Pattern.compile("\\b([a-z][A-Za-z0-9]*)\\s*\\(").matcher(body);
        while (calls.find()) {
            if (calls.start() > 0 && body.charAt(calls.start() - 1) == '.') {
                continue;
            }
            String calleeBody = PublicationTierMatrixTest.bodyOf(src, calls.group(1));
            if (calleeBody != null && !calleeBody.equals(body)
                    && asksTheGuard(src, calleeBody, depth - 1)) {
                return true;
            }
        }
        return false;
    }

    /** Every non-GET endpoint on a resource, keyed "VERB /full/path". */
    private static Map<String, Method> writesOf(Class<?> resource) {
        String base = resource.isAnnotationPresent(Path.class)
                ? resource.getAnnotation(Path.class).value() : "";
        Map<String, Method> out = new LinkedHashMap<>();
        for (Method m : resource.getDeclaredMethods()) {
            String verb = verbOf(m);
            if (verb == null || "GET".equals(verb)) {
                continue;
            }
            String suffix = m.isAnnotationPresent(Path.class) ? m.getAnnotation(Path.class).value() : "";
            out.put(verb + " " + join(base, suffix), m);
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

    private static String join(String base, String suffix) {
        if (suffix.isEmpty()) {
            return base;
        }
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base + suffix.substring(1);
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }
}
