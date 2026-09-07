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
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.junit.jupiter.api.Test;
import org.niord.core.user.Roles;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The three consumer endpoints: where they live, who may reach them, and what
 * they are willing to be asked.
 *
 * Each assertion here is about an ANNOTATION, which is exactly the class of
 * mistake a behavioural test cannot catch and a code review reliably misses. A
 * picker that lost its @RolesAllowed still returns perfectly ordinary-looking
 * rows -- to anybody at all. A hydration endpoint that gained one blanks every
 * citation chip on the public site. And a route template that collides with
 * another one is resolved by RESTEasy in an order nothing here specifies.
 *
 * No database and no server: niord-web has no container tests, so what can be
 * pinned from here is pinned from here and everything else lives in core.
 */
public class IssueConsumerEndpointContractTest {

    private static Method endpoint(String name) {
        List<Method> found = new ArrayList<>();
        for (Method m : PublicationIssueRestService.class.getMethods()) {
            if (name.equals(m.getName())) {
                found.add(m);
            }
        }
        assertEquals(1, found.size(),
                "expected exactly one method named " + name + ", found " + found.size());
        return found.get(0);
    }

    private static Set<String> queryParams(Method m) {
        Set<String> names = new LinkedHashSet<>();
        for (Parameter p : m.getParameters()) {
            QueryParam q = p.getAnnotation(QueryParam.class);
            if (q != null) {
                names.add(q.value());
            }
        }
        return names;
    }

    /**
     * The parameter itself, not just its name -- the declared type and the
     * default are where "optional" is either kept or quietly lost.
     */
    private static Parameter queryParam(Method m, String name) {
        for (Parameter p : m.getParameters()) {
            QueryParam q = p.getAnnotation(QueryParam.class);
            if (q != null && name.equals(q.value())) {
                return p;
            }
        }
        return fail(m.getName() + " declares no query parameter named " + name
                + "; it has " + queryParams(m));
    }

    // ------------------------------------------------------------------ I27

    /**
     * I27 lives at /picker and is NOT anonymous.
     *
     * Its default status filter reaches OPEN issues, so an anonymous picker would
     * hand an unauthenticated caller the names and links of every unreleased
     * publication in the estate -- which is the enumeration this redesign
     * deliberately does not reproduce. The old justification, that "the print flow
     * is anonymous", is retired: printing goes through a ticket and all four
     * shipped consumers are logged in.
     */
    @Test
    public void thePickerIsAtItsContractedPathAndRequiresALogin() {
        Method picker = endpoint("pickerSearch");
        assertNotNull(picker.getAnnotation(GET.class), "the picker is a read");
        assertEquals("/picker", picker.getAnnotation(Path.class).value());

        assertNull(picker.getAnnotation(PermitAll.class),
                "the picker became anonymous. With its PUBLISHED+OPEN default that is an "
                        + "enumeration of every unreleased publication, by name and by link");
        RolesAllowed roles = picker.getAnnotation(RolesAllowed.class);
        assertNotNull(roles, "an unguarded picker is an anonymous one");
        assertTrue(Arrays.asList(roles.value()).contains(Roles.USER),
                "expected the user tier, got " + Arrays.toString(roles.value()));
    }

    /**
     * The picker declares `maxSize` and `page`, and does NOT accept `limit`.
     *
     * The shipped frontend sends `limit` where the backend reads `maxSize`, so
     * every publication picker in service today silently caps at its default --
     * including a browse call that asks for two hundred rows and gets one
     * hundred. Accepting `limit` here would carry that defect forward into the
     * replacement under a name that looks like it works.
     */
    @Test
    public void thePickerPagesOnMaxSizeAndNeverOnLimit() {
        Set<String> params = queryParams(endpoint("pickerSearch"));
        assertTrue(params.contains("maxSize"), "the picker declares no maxSize; got " + params);
        assertTrue(params.contains("page"), "the picker declares no page; got " + params);
        assertFalse(params.contains("limit"),
                "the picker accepts `limit`. It is not a paging parameter on a new endpoint, and "
                        + "honouring it would reproduce the silent 100-row cap the frontend already "
                        + "hits");
    }

    /** And no endpoint on this resource accepts `limit` under any spelling. */
    @Test
    public void noEndpointOnTheIssuesResourceAcceptsLimit() {
        List<String> offenders = new ArrayList<>();
        for (Method m : PublicationIssueRestService.class.getMethods()) {
            if (queryParams(m).contains("limit")) {
                offenders.add(m.getName());
            }
        }
        assertTrue(offenders.isEmpty(),
                "these endpoints accept `limit`, which the new surface declares nowhere: " + offenders);
    }

    // ------------------------------------------------------------------ I28

    /**
     * I28 lives at /by-ids and IS anonymous.
     *
     * That is not enumeration: it resolves only ids the caller already holds, and
     * those ids are bytes inside published message HTML. A citation chip on the
     * public site cannot render its title otherwise.
     */
    @Test
    public void hydrationByIdIsAnonymousAndAtItsOwnPath() {
        Method byIds = endpoint("byIds");
        assertNotNull(byIds.getAnnotation(GET.class));
        assertEquals("/by-ids", byIds.getAnnotation(Path.class).value());
        assertNotNull(byIds.getAnnotation(PermitAll.class),
                "hydration by id requires a login. Every citation chip on the public site then "
                        + "renders as a bare id");
        assertNull(byIds.getAnnotation(RolesAllowed.class),
                "a @RolesAllowed beside @PermitAll makes which one wins a matter of the runtime");
    }

    /**
     * I3 and I28 are DIFFERENT methods, and no `/issue/{publicIds}` template exists.
     *
     * A comma-separated hydration under the single-issue path would collide with
     * the single read exactly the way two series templates did, and which one
     * RESTEasy matched would be undefined -- so hydration lives at its own address
     * and nowhere else.
     */
    @Test
    public void thereIsNoRouteTemplateThatCollidesWithTheSingleIssueRead() {
        Method single = endpoint("get");
        Method byIds = endpoint("byIds");
        assertNotEquals(single, byIds,
                "the single read and the id hydration resolved to one method");
        assertEquals("/issue/{publicId}", single.getAnnotation(Path.class).value());

        for (Method m : PublicationIssueRestService.class.getMethods()) {
            Path path = m.getAnnotation(Path.class);
            if (path == null) {
                continue;
            }
            assertNotEquals("/issue/{publicIds}", path.value(),
                    m.getName() + " declares /issue/{publicIds}, which collides with the single read "
                            + "at /issue/{publicId}");
            // And nothing else may claim the single-read template either.
            if (!m.equals(single)) {
                assertFalse("/issue/{publicId}".equals(path.value())
                                && m.isAnnotationPresent(GET.class),
                        m.getName() + " is a second GET at /issue/{publicId}");
            }
        }
    }

    // ------------------------------------------------------------------ I29

    /** I29 lives at /recent, is editor-tier, and takes the bounds it is documented with. */
    @Test
    public void theTimelineIsAtItsContractedPathAndTier() {
        Method recent = endpoint("recent");
        assertNotNull(recent.getAnnotation(GET.class));
        assertEquals("/recent", recent.getAnnotation(Path.class).value());

        assertNull(recent.getAnnotation(PermitAll.class),
                "the dashboard strip became anonymous; it carries issue names and member counts");
        RolesAllowed roles = recent.getAnnotation(RolesAllowed.class);
        assertNotNull(roles);
        assertTrue(Arrays.asList(roles.value()).contains(Roles.USER));

        Set<String> params = queryParams(recent);
        assertTrue(params.contains("publicationSeriesId"),
                "the strip must be told which series to render; got " + params);
        assertTrue(params.contains("periods"));
        assertFalse(params.contains("maxSize"),
                "the strip is bounded by its own arguments rather than paged; a maxSize here is a "
                        + "second, disagreeing bound");
    }

    /** The bounds are the documented ones, and they are constants rather than literals in a branch. */
    @Test
    public void theTimelineBoundsAreTheDocumentedOnes() {
        assertEquals(50, PublicationIssueRestService.MAX_TIMELINE_SERIES);
        assertEquals(52, PublicationIssueRestService.MAX_TIMELINE_PERIODS);
        assertEquals(200, PublicationIssueRestService.MAX_TIMELINE_ISSUES);
    }

    /**
     * The desk strip is ADMIN, is bounded by a domain, and carries a bound for
     * each kind of series it can answer for.
     *
     * The two counts mean different things -- weeks of calendar against editions
     * of an archive -- so one parameter could not bound both, and a desk read that
     * answers for both kinds in one request has to hold both at once.
     */
    @Test
    public void theDeskStripIsAdminAndBoundsEachKindSeparately() {
        Method byDomain = endpoint("recentByDomain");
        assertNotNull(byDomain.getAnnotation(GET.class));
        assertEquals("/recent-by-domain", byDomain.getAnnotation(Path.class).value());

        assertNull(byDomain.getAnnotation(PermitAll.class),
                "the desk strip became anonymous; 'which series does this desk own' is an "
                        + "admin question and this answers it");
        RolesAllowed roles = byDomain.getAnnotation(RolesAllowed.class);
        assertNotNull(roles, "an unguarded desk strip is an anonymous one");
        assertTrue(Arrays.asList(roles.value()).contains(Roles.ADMIN),
                "expected the admin tier, got " + Arrays.toString(roles.value()));

        Set<String> params = queryParams(byDomain);
        assertTrue(params.contains("domain"),
                "the desk strip must be told which desk; without it this scans the estate");
        assertFalse(params.contains("publicationSeriesId"),
                "naming the series here reintroduces the wait on the series list this route "
                        + "exists to remove");
        assertTrue(params.contains("periods"),
                "the scheduled half is bounded in periods; got " + params);
        assertTrue(params.contains("unscheduledIssues"),
                "the calendar-less half is bounded in editions, and periods cannot mean both; "
                        + "got " + params);
    }

    /**
     * `cadenced` is OPTIONAL, and optional means the parameter has no default.
     *
     * A boxed Boolean with no @DefaultValue is the only shape that lets the
     * endpoint tell "narrow to the scheduled series" apart from "answer for the
     * whole desk". A primitive, or a default of any kind, silently turns the
     * unnarrowed request back into a narrowed one -- and the caller gets a
     * perfectly ordinary answer that is missing half the desk.
     */
    @Test
    public void theDeskStripTreatsAnAbsentKindAsBothKinds() {
        // Missing entirely fails inside the lookup: old callers that narrow to one
        // kind send this parameter, and dropping it breaks them.
        Parameter cadenced = queryParam(endpoint("recentByDomain"), "cadenced");

        assertEquals(Boolean.class, cadenced.getType(),
                "a primitive cannot be absent, so an unnarrowed request would be read as "
                        + "cadenced=false and answer for half the desk");
        assertNull(cadenced.getAnnotation(DefaultValue.class),
                "a default makes the parameter present on every request, which is the same "
                        + "defect as a primitive wearing a different annotation");
    }

    /** The counts that bound the desk strip default to the documented ones. */
    @Test
    public void theDeskStripCountsCarryTheirDocumentedDefaults() {
        Method byDomain = endpoint("recentByDomain");
        assertEquals("8", queryParam(byDomain, "periods").getAnnotation(DefaultValue.class).value(),
                "the strip's default width changed; the dashboard renders a different card");
        assertEquals("100",
                queryParam(byDomain, "unscheduledIssues").getAnnotation(DefaultValue.class).value(),
                "the archive default changed; the card counts what comes back as the series' "
                        + "whole issue count");
    }

    // ------------------------------------------------------------------ curation

    /**
     * The standing curation decisions are readable, at the curator tier.
     *
     * Same tier as the writes that produce them, because the payload carries the
     * author and the reason -- the admin-only half of a why-line. Without a read
     * endpoint the exclusions are visible from no surface at all: an excluded
     * message is not a member, so nothing in the member list can show it.
     */
    @Test
    public void theStandingCurationDecisionsAreReadableAtTheCuratorTier() {
        Method overrides = endpoint("overrides");
        assertNotNull(overrides.getAnnotation(GET.class));
        assertEquals("/issue/{publicId}/overrides", overrides.getAnnotation(Path.class).value());

        assertNull(overrides.getAnnotation(PermitAll.class),
                "the curation decisions carry an author and a reason; they are not anonymous");
        RolesAllowed roles = overrides.getAnnotation(RolesAllowed.class);
        assertNotNull(roles);
        assertTrue(Arrays.asList(roles.value()).contains("publication-curate")
                        && Arrays.asList(roles.value()).contains("admin"),
                "expected the curator tier, got " + Arrays.toString(roles.value()));
    }

    // ------------------------------------------------------------------ catalogue

    /**
     * Every code these three endpoints can raise has a status.
     *
     * An uncatalogued code returns 500, so a caller naming fifty-one series would
     * be told the server broke -- and a client that retries on 5xx would retry
     * something that can never succeed.
     */
    @Test
    public void theCodesTheseEndpointsRaiseAreCatalogued() {
        assertEquals(400, PublicationErrorCatalogue.statusOf("TOO_MANY_IDS"));
        assertEquals(400, PublicationErrorCatalogue.statusOf("NO_SERIES_IDS"));
        assertEquals(400, PublicationErrorCatalogue.statusOf("TOO_MANY_SERIES_IDS"));
        assertEquals(400, PublicationErrorCatalogue.statusOf("INVALID_FILTER_VALUE"));
        assertEquals(400, PublicationErrorCatalogue.statusOf("INVALID_STATUS"));
    }
}
