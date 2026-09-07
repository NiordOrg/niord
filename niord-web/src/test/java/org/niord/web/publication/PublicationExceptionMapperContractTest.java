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

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.PublicationResolver;
import org.niord.core.publication.series.IssueDeleteService;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueRenderService;
import org.niord.core.publication.series.MemberResolutionService;
import org.niord.core.publication.series.PublicationException;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.criteria.CriteriaParseException;
import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.criteria.CriterionKind;
import org.niord.core.publication.series.resolve.IssueNaming;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the provider is registered FOR, which decides what it does to everything
 * else in the deployment.
 *
 * The defect these assertions exist for: the mapper was declared against
 * RuntimeException, which in a deployment with no competing mapper makes it the
 * handler for every runtime failure anywhere. It recognised what it knew and
 * rethrew the rest -- and a throw from inside a mapper does not return the
 * exception to the container, it escapes the response pipeline entirely. So a
 * plain {@code WebApplicationException(403)} raised by any unrelated resource,
 * and every request that matched no route, came back as a 500.
 *
 * That is not reproducible here: niord-web has no container tests. What IS
 * checkable, and is what actually decides the behaviour, is the type the provider
 * declares -- so that is what is asserted.
 */
public class PublicationExceptionMapperContractTest {

    /**
     * The provider handles publication failures and nothing else.
     *
     * A WebApplicationException is not a PublicationException, so JAX-RS never
     * offers one to this mapper and its own status survives -- which is the whole
     * fix, expressed as the one fact that produces it.
     */
    @Test
    public void theMapperIsScopedToPublicationFailuresOnly() {
        Type mapped = mappedType();
        assertEquals(PublicationException.class, mapped,
                "the mapper must be declared for PublicationException. Declared for "
                        + "RuntimeException it becomes the handler for every runtime failure in the "
                        + "deployment, and rethrowing what it does not recognise escapes the "
                        + "container's own handling rather than deferring to it.");

        assertFalse(PublicationException.class.isAssignableFrom(WebApplicationException.class),
                "a WebApplicationException must not be a PublicationException, or the mapper would "
                        + "take over the status the caller chose");
    }

    /**
     * A bare 403 from any resource keeps its status.
     *
     * Stated as the concrete case because it is the one that was broken: a
     * legacy endpoint throwing ForbiddenException answered 500, so a client
     * could not tell a permission refusal from a server fault.
     */
    @Test
    public void abareForbiddenIsNotTurnedIntoAServerError() {
        WebApplicationException forbidden = new ForbiddenException("not yours");
        assertEquals(403, forbidden.getResponse().getStatus());
        assertFalse(PublicationException.class.isInstance(forbidden),
                "a ForbiddenException reaching the publication mapper would be re-answered from the "
                        + "error catalogue, which does not know it -- and an unknown code is 500");
        assertNull(PublicationExceptionMapper.codeOf(forbidden),
                "the mapper must not claim a code for an exception that is not ours");

        // And the same for a route that matched nothing, which is the other half
        // of what the RuntimeException registration swallowed.
        assertNull(PublicationExceptionMapper.codeOf(new NotFoundException()));
        assertNull(PublicationExceptionMapper.codeOf(new IllegalStateException("unrelated")));
    }

    /**
     * Every failure the API raises is a PublicationException carrying a
     * catalogued code.
     *
     * The list is spelled out rather than discovered, because the property being
     * asserted is that a failure type added later joins the hierarchy: a new
     * RuntimeException outside it would no longer be mapped at all, and would
     * reach the caller as a bare 500 with a stack trace.
     */
    @Test
    public void everyMappedFailureIsAPublicationExceptionWithACataloguedCode() {
        List<PublicationException> all = List.of(
                new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_OPEN", "…"),
                new IssuePublishService.AlreadyPublishedException(new Date()),
                new IssuePublishService.WarningsNotAcknowledgedException(List.of("STALE_OVERRIDE")),
                new IssuePublishService.ArchiveFailedException("…", new RuntimeException()),
                new IssueRenderService.RenderFailedException("…", new RuntimeException()),
                new MemberResolutionService.UnresolvableOperandException("…"),
                new IssueDeleteService.IssueCitedException("…",
                        List.of(new PublicationResolver.CitingMessage("NM-1-26", "uid-1")), 1),
                new IssueNaming.UnknownTokenException("yeer"),
                new CriteriaResolver.EmptyOperandException(CriterionKind.AREA),
                new CriteriaParseException("…", new RuntimeException()));

        for (PublicationException e : all) {
            assertNotNull(e.code(), e.getClass().getSimpleName() + " carries no code");
            assertTrue(PublicationErrorCatalogue.knows(e.code()),
                    e.getClass().getSimpleName() + " raises the uncatalogued code '" + e.code()
                            + "', which resolves to 500 -- the exact failure the catalogue exists "
                            + "to prevent");
            assertEquals(e.code(), PublicationExceptionMapper.codeOf(e));
        }
    }

    /**
     * A refused deletion puts the citing messages ON THE WIRE, not only in the
     * sentence.
     *
     * This is the whole deliverable of that refusal. Told "some messages cite
     * this", an admin has to search the estate by hand for something that is not
     * in any list -- the citation lives inside message HTML. Told WHICH, the
     * dialog renders a row per message that opens straight into it.
     *
     * The keys are asserted rather than assumed because they are a contract with
     * a client written in another repository: renaming one here breaks the
     * dialog, and nothing in either build would say so.
     *
     * The count is the true total while the list is bounded, so an issue cited by
     * three hundred messages produces a response somebody can render and a
     * sentence somebody can read. Asserted with a total LARGER than the sample,
     * because a fixture where they happen to be equal would pass just as well
     * against a body that reported the list length.
     */
    @Test
    public void aCitationRefusalNamesTheMessagesAndCountsThemAll() {
        Response response = new PublicationExceptionMapper().toResponse(
                new IssueDeleteService.IssueCitedException("cited by 37 messages",
                        List.of(new PublicationResolver.CitingMessage("NM-1-26", "uid-1"),
                                new PublicationResolver.CitingMessage("uid-2", "uid-2")),
                        37));

        assertEquals(409, response.getStatus(),
                "a deletion refused because the estate points at the issue is a state conflict, "
                        + "not a malformed request");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("ISSUE_CITED", body.get("code"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("citingMessages");
        assertNotNull(messages, "the refusal carries no citingMessages, so the dialog has nothing "
                + "to list and the admin is sent to search by hand");
        assertEquals(2, messages.size());
        assertEquals("NM-1-26", messages.get(0).get("messageId"));
        assertEquals("uid-1", messages.get(0).get("uid"));
        // A message with no number yet is named by its uid, which is still a
        // thing a person can look up -- a blank row would not be.
        assertEquals("uid-2", messages.get(1).get("messageId"));
        assertEquals("uid-2", messages.get(1).get("uid"));

        assertEquals(37L, body.get("citingCount"),
                "the count must be the true total rather than the length of the bounded list, or "
                        + "the dialog tells the admin there are two when there are thirty-seven");
    }

    /** The field errors survive the move onto the base type. */
    @Test
    public void aValidationRefusalStillCarriesItsFieldErrors() {
        var refusal = new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID", "2 fail",
                List.of(new SeriesValidator.FieldError("S-22", "releaseMode", "not supported"),
                        new SeriesValidator.FieldError("S-23", "reportParams.week", "injected")));

        assertEquals(2, refusal.fieldErrors().size(),
                "a form cannot render \"two rules fail\" against a control; the rules already say "
                        + "which field each belongs to");
        assertEquals("releaseMode", refusal.fieldErrors().get(0).field());

        // And an ordinary refusal carries an empty list rather than null, so the
        // mapper does not have to guard every read.
        assertTrue(new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_OPEN", "…")
                .fieldErrors().isEmpty());
    }

    /** The class is still a registered provider. */
    @Test
    public void theMapperIsStillRegistered() {
        assertTrue(PublicationExceptionMapper.class.isAnnotationPresent(Provider.class),
                "without @Provider the mapper is not registered at all and every coded refusal "
                        + "becomes a 500");
    }

    /** The type argument of the ExceptionMapper the class implements. */
    private static Type mappedType() {
        for (Type iface : PublicationExceptionMapper.class.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType p
                    && p.getRawType() == ExceptionMapper.class) {
                return p.getActualTypeArguments()[0];
            }
        }
        throw new AssertionError("PublicationExceptionMapper no longer implements ExceptionMapper");
    }
}
