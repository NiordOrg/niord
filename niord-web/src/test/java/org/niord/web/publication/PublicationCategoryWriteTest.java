package org.niord.web.publication;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryDesc;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The categories write surface, which was missing the half that matters.
 *
 * There was no way to CREATE a publication category. Harmless while nothing
 * required one; the moment S-19 made a category mandatory to save a series, an
 * admin could be blocked by a required field whose legal values nothing in the
 * product could extend. Update existed but carried only priority and publish, so
 * a category created with a typo could never be renamed either -- and these names
 * are what the public page shows.
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
    public void theCreateEndpointExistsAndIsAdminGuarded() {
        Method create = endpoint("create", Map.class);
        assertNotNull(create, "there is no way to create a publication category, and S-19 makes one "
                + "mandatory before a series can be saved");
        assertNotNull(create.getAnnotation(POST.class), "creating is a POST");

        RolesAllowed roles = create.getAnnotation(RolesAllowed.class);
        assertNotNull(roles, "an unguarded create lets anyone add a category to the public page");
        assertTrue(Arrays.asList(roles.value()).contains("admin"),
                "expected admin, got " + Arrays.toString(roles.value()));
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

    private static String nameOf(PublicationCategory c, String lang) {
        return c.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .map(PublicationCategoryDesc::getName)
                .findFirst().orElse(null);
    }
}
