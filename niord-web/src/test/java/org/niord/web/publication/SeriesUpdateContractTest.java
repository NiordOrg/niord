package org.niord.web.publication;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S9 exists, is guarded, and is shaped the way the contract says.
 *
 * It went missing for the whole of phase B without anything noticing, because
 * every test of the series surface was a test of reading it. The settings screen
 * has nowhere to save to without this, so every configuration an imported series
 * needs before review and activation -- criteria included -- was unreachable,
 * and the gap only surfaced when somebody tried to build the screen.
 *
 * No database and no server: this reads annotations, the same way the tier split
 * is checked. What the endpoint DOES with a body belongs in an integration test;
 * what this pins is that it is there at all and cannot be reached anonymously.
 */
public class SeriesUpdateContractTest {

    private static Method endpoint(String name, Class<?>... params) {
        try {
            return PublicationSeriesRestService.class.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * S9 is `PUT /series/{seriesId}`, and it is that verb and path deliberately.
     *
     * A save posted to the collection would create a second series; a save
     * against `/status` would route around the transition validation S10 owns.
     */
    @Test
    public void theUpdateEndpointExistsAtTheContractedVerbAndPath() {
        Method update = endpoint("update", String.class, SystemPublicationSeriesVo.class);

        assertNotNull(update,
                "S9 PUT /series/{seriesId} is missing. Without it the settings screen has nowhere to "
                        + "save to, and a query-backed series can never be given the criteria S-1 "
                        + "requires before it will activate");

        assertNotNull(update.getAnnotation(PUT.class), "S9 is a PUT: it replaces one series");
        assertEquals("/series/{seriesId}", update.getAnnotation(Path.class).value());
    }

    /** And it is admin-only, like every other write on this resource. */
    @Test
    public void theUpdateEndpointIsAdminGuarded() {
        Method update = endpoint("update", String.class, SystemPublicationSeriesVo.class);
        assertNotNull(update);

        RolesAllowed roles = update.getAnnotation(RolesAllowed.class);
        assertNotNull(roles, "an unguarded update lets anyone rewrite what a publication contains");
        assertTrue(Arrays.asList(roles.value()).contains("admin"),
                "expected admin, got " + Arrays.toString(roles.value()));
    }

    /**
     * S6 hands back a DRAFT with a desc row per configured language.
     *
     * The status matters: ACTIVE is what puts a series in the picker and S-17
     * requires a complete one, so a create form must not be able to ask for it.
     * The desc rows matter because C5 makes a payload narrowed to one language
     * uneditable -- a form with no row for a language cannot fill it in.
     */
    @Test
    public void theNewSeriesTemplateIsADraftAndIsAdminOnly() {
        Method template = endpoint("newSeriesTemplate");
        assertNotNull(template, "S6 GET /new-series-template is missing; a create form would have "
                + "to guess the defaults, and a guess that drifts from the server produces a "
                + "series that validates in the browser and is refused on save");
        assertEquals("/new-series-template", template.getAnnotation(Path.class).value());
        assertNotNull(template.getAnnotation(RolesAllowed.class));
    }

    /**
     * S13 is a POST that persists nothing.
     *
     * POST because the document travels in the body -- a criteria document does not
     * fit in a query string, and putting it there would cap what can be previewed
     * at whatever the proxy allows. It writes nothing, which is what makes it safe
     * to call while somebody is still typing.
     */
    @Test
    public void theResolvePreviewProbeExistsAndIsAdminOnly() {
        Method probe = null;
        for (Method m : PublicationSeriesRestService.class.getMethods()) {
            if ("resolvePreview".equals(m.getName())) {
                probe = m;
            }
        }
        assertNotNull(probe, "S13 POST /resolve-preview is missing; the criteria editor has no way "
                + "to show what a document would select short of saving it onto a series");
        assertEquals("/resolve-preview", probe.getAnnotation(Path.class).value());
        assertNotNull(probe.getAnnotation(jakarta.ws.rs.POST.class));
        assertNotNull(probe.getAnnotation(RolesAllowed.class));
    }

    /**
     * The write surface is complete: create, update, status, delete.
     *
     * Listed together because the gap that shipped was not a broken endpoint but
     * an absent one, and absence is only visible against the set it belongs to.
     */
    @Test
    public void theSeriesWriteSurfaceIsComplete() {
        assertNotNull(endpoint("create", SystemPublicationSeriesVo.class), "S8 create");
        assertNotNull(endpoint("update", String.class, SystemPublicationSeriesVo.class), "S9 update");
        // The reason travels beside the status: leaving ACTIVE, or returning to
        // it, changes what editors may cite and what the site lists.
        // The revision travels beside them, so a status change composed against a
        // series somebody else has since edited is refused rather than applied.
        assertNotNull(endpoint("setStatus", String.class, String.class, Integer.class, String.class),
                "S10 status");
        // And which model answers the public, which is a different decision from
        // whether the series is active at all -- one endpoint each, so neither
        // can be reached by a save of the other.
        assertNotNull(endpoint("setPublicAuthority", String.class, java.util.Map.class),
                "the cutover flip, per series");
        assertNotNull(endpoint("setPublicAuthorityForAll", java.util.Map.class),
                "the cutover flip, whole estate, all or nothing");
        assertNotNull(endpoint("delete", String.class, Integer.class), "S11 delete");
    }
}
