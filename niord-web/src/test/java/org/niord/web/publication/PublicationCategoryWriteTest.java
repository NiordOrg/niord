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

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryDesc;
import org.niord.core.publication.series.IssueLifecycleService;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The category names this resource writes.
 *
 * ONE resource answers /publication-categories. Its PUT used to ignore the descs
 * entirely, so a category created with a typo could not be renamed through the
 * endpoint being served -- and those names are what the public page shows.
 * Create lives beside update now, with the priority defaulting to the back of
 * the page rather than silently ahead of everything.
 */
public class PublicationCategoryWriteTest {

    private static Method endpoint(String name, Class<?>... params) {
        try {
            return PublicationCategoryRestService.class.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Test
    public void theUpdateEndpointIsStillAPutAndStillGuarded() {
        Method update = endpoint("update", String.class, Map.class);
        assertNotNull(update);
        assertNotNull(update.getAnnotation(PUT.class));
        assertNotNull(update.getAnnotation(RolesAllowed.class));
    }

    /** The names arrive, which update could not previously carry at all. */
    @Test
    public void namesAreApplied() {
        PublicationCategory c = new PublicationCategory();
        PublicationCategoryRestService.applyDescs(c, Map.of("descs", List.of(
                Map.of("lang", "da", "name", "Interne publikationer"),
                Map.of("lang", "en", "name", "Internal publications"))));

        assertEquals(2, c.getDescs().size());
        assertEquals("Interne publikationer", nameOf(c, "da"));
        assertEquals("Internal publications", nameOf(c, "en"));
    }

    /**
     * A second apply REUSES the row for a language rather than rebuilding it.
     *
     * The desc table is unique on (lang, entity) and Hibernate orders inserts
     * before deletes within a flush, so clearing and re-adding "da" collides with
     * the "da" still awaiting deletion. The series model was found failing exactly
     * that way on every edit after the first; this asserts the same mistake was not
     * repeated here.
     */
    @Test
    public void asecondApplyKeepsTheSameDescRow() {
        PublicationCategory c = new PublicationCategory();
        PublicationCategoryRestService.applyDescs(c,
                Map.of("descs", List.of(Map.of("lang", "da", "name", "Foerste"))));
        PublicationCategoryDesc first = c.getDescs().get(0);

        PublicationCategoryRestService.applyDescs(c,
                Map.of("descs", List.of(Map.of("lang", "da", "name", "Anden"))));

        assertEquals(1, c.getDescs().size());
        assertTrue(first == c.getDescs().get(0),
                "the da row was replaced rather than updated; on a managed entity that is an "
                        + "insert racing a delete on a unique key");
        assertEquals("Anden", nameOf(c, "da"));
    }

    /** A language the client stopped sending is a language deleted. */
    @Test
    public void alanguageNoLongerSentIsRemoved() {
        PublicationCategory c = new PublicationCategory();
        PublicationCategoryRestService.applyDescs(c, Map.of("descs", List.of(
                Map.of("lang", "da", "name", "Dansk"),
                Map.of("lang", "en", "name", "English"))));

        PublicationCategoryRestService.applyDescs(c,
                Map.of("descs", List.of(Map.of("lang", "da", "name", "Dansk"))));

        assertEquals(List.of("da"), c.getDescs().stream().map(PublicationCategoryDesc::getLang).toList());
    }

    /** A body with no descs at all leaves the existing names alone. */
    @Test
    public void abodyWithoutDescsChangesNothing() {
        PublicationCategory c = new PublicationCategory();
        PublicationCategoryRestService.applyDescs(c,
                Map.of("descs", List.of(Map.of("lang", "da", "name", "Dansk"))));

        PublicationCategoryRestService.applyDescs(c, Map.of("priority", 10));

        assertEquals(1, c.getDescs().size(), "a partial update erased the names");
        assertEquals("Dansk", nameOf(c, "da"));
    }

    // ------------------------------------------------------- the request guards

    /**
     * The path id wins, and a body that disagrees is refused.
     *
     * categoryId is what every series stores and what an import upserts on, so a
     * rename is not a field edit. Ignoring the mismatch answered 200 with the old
     * id -- the caller believed the rename had happened.
     */
    @Test
    public void arenameThroughTheBodyIsRefusedRatherThanIgnored() {
        var e = assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> new PublicationCategoryRestService()
                        .update("dk-nm", Map.of("categoryId", "dk-ntm")));
        assertEquals("CATEGORY_ID_IMMUTABLE", e.code());
        assertEquals(400, PublicationErrorCatalogue.statusOf(e.code()),
                "no change of state makes the same rename correct, so it is not a 409 a client "
                        + "should retry");
    }

    /** A body carrying the SAME id is an ordinary update, not a rename. */
    @Test
    public void abodyRepeatingTheSameIdIsNotARename() {
        // It gets past the guard and fails later for want of a database, which is
        // the point: the guard did not fire.
        assertThrows(Exception.class, () -> new PublicationCategoryRestService()
                .update("dk-nm", Map.of("categoryId", "dk-nm")));
    }

    /** An empty body is a coded refusal, not a NullPointerException. */
    @Test
    public void anemptyBodyIsRefusedWithACode() {
        var onUpdate = assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> new PublicationCategoryRestService().update("dk-nm", null));
        assertEquals("CATEGORY_INVALID", onUpdate.code());

        var onCreate = assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> new PublicationCategoryRestService().create(null));
        assertEquals("CATEGORY_INVALID", onCreate.code(),
                "an absent body used to dereference null and answer 500, which reads to a client "
                        + "as a server fault rather than as a malformed request");
    }

    /**
     * The anonymous list is ALWAYS bounded.
     *
     * It is externally consumed with no credential at all -- the admin export item
     * is a bare href -- so an absent or zero limit meaning "everything" hands
     * anybody an unbounded read of the table. The parameter name is the shipped
     * contract and is deliberately not the maxSize its sibling uses.
     */
    @Test
    public void theAnonymousListCarriesADefaultBound() throws Exception {
        Method all = PublicationCategoryRestService.class
                .getMethod("all", String.class, int.class);
        DefaultValue bound = null;
        for (Annotation[] on : all.getParameterAnnotations()) {
            for (Annotation a : on) {
                if (a instanceof DefaultValue d) {
                    bound = d;
                }
            }
        }
        assertNotNull(bound, "the limit lost its default; absent then means unbounded");
        assertEquals("1000", bound.value());

        boolean named = Arrays.stream(all.getParameters())
                .anyMatch(p -> p.isAnnotationPresent(QueryParam.class)
                        && "limit".equals(p.getAnnotation(QueryParam.class).value()));
        assertTrue(named, "the parameter is named 'limit' in the shipped contract; renaming it to "
                + "match its sibling breaks the documented export link");
    }

    // ------------------------------------------------------------ the read shape

    /**
     * A category with no name in the requested language still answers with one.
     *
     * Falling back is what every other localized read in the product does. An
     * empty descs array left the series form's dropdown rendering the raw slug,
     * and the still-shipped admin list throwing on descs[0].name.
     */
    @Test
    public void arequestForAnAbsentLanguageFallsBackToWhatTheCategoryHas() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("dk-nm");
        PublicationCategoryRestService.applyDescs(c,
                Map.of("descs", List.of(Map.of("lang", "da", "name", "Danske publikationer"))));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> descs =
                (List<Map<String, String>>) PublicationCategoryRestService.toMap(c, "en").get("descs");

        assertEquals(1, descs.size(), "the fallback returned nothing to render a label from");
        assertEquals("Danske publikationer", descs.get(0).get("name"));
    }

    /** And the requested language wins where the category has it. */
    @Test
    public void therequestedLanguageIsTheOneReturned() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("dk-nm");
        PublicationCategoryRestService.applyDescs(c, Map.of("descs", List.of(
                Map.of("lang", "da", "name", "Danske publikationer"),
                Map.of("lang", "en", "name", "Danish publications"))));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> descs =
                (List<Map<String, String>>) PublicationCategoryRestService.toMap(c, "en").get("descs");

        assertEquals(1, descs.size());
        assertEquals("Danish publications", descs.get(0).get("name"));
    }

    private static String nameOf(PublicationCategory c, String lang) {
        return c.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .map(PublicationCategoryDesc::getName)
                .findFirst().orElse(null);
    }
}
