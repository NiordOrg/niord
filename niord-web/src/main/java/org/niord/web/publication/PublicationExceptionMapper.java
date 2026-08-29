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

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.PublicationException;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.StaleVersionGuard;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the publication failures into the wire shape, once.
 *
 * Without this every endpoint catches the same handful of exceptions and picks
 * its own status and body, and they drift -- so the same failure looks different
 * depending which endpoint you hit, and a client cannot handle it generically.
 *
 * Every response carries a CODE, not just a message. The message is for a human
 * reading a log; the code is what a client branches on, and a client that has to
 * match on message text breaks the first time somebody improves the wording.
 *
 * THE TYPE PARAMETER IS LOAD-BEARING. It is {@link PublicationException} and it
 * must stay that. A provider declared for RuntimeException is a provider for
 * every runtime failure in the deployment -- JAX-RS selects the mapper whose type
 * parameter is the nearest supertype of what was thrown, and with no competing
 * mapper that is the RuntimeException one for everything. Recognising nothing and
 * rethrowing does not undo it either: a rethrow from inside a mapper escapes the
 * container's own handling, so a bare {@code WebApplicationException(403)} raised
 * anywhere else, and every request that matched no route, came back as a 500.
 * Narrowing the parameter is what leaves those alone.
 */
@Provider
public class PublicationExceptionMapper implements ExceptionMapper<PublicationException> {

    private static final Logger log = LoggerFactory.getLogger(PublicationExceptionMapper.class);

    @Override
    public Response toResponse(PublicationException e) {
        String code = e.code();
        int status = PublicationErrorCatalogue.statusOf(code);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", e.getMessage());

        // The stamp the winner recorded, so a losing client can show it rather
        // than guess.
        if (e instanceof IssuePublishService.AlreadyPublishedException already
                && already.stampedAt() != null) {
            body.put("stampedAt", already.stampedAt().getTime());
        }
        if (e instanceof IssueNaming.UnknownTokenException unknown) {
            body.put("token", unknown.token());
        }
        // Both revisions, so the client can say "you are three saves behind"
        // rather than "somebody changed something" -- and so it knows which
        // revision to re-read against without guessing.
        if (e instanceof StaleVersionGuard.StaleVersionException stale) {
            body.put("storedVersion", stale.stored());
            body.put("submittedVersion", stale.submitted());
        }
        // The codes the admin has to acknowledge, so the dialog can list them
        // rather than send the admin back to the checklist to find out.
        if (e instanceof IssuePublishService.WarningsNotAcknowledgedException warnings) {
            body.put("unacknowledgedWarnings", warnings.codes());
        }
        // Which fields failed, so a form can put each message beside the control
        // that caused it. The message stays a readable sentence for a log; this
        // is the same information in the shape a client can act on.
        if (!e.fieldErrors().isEmpty()) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (SeriesValidator.FieldError fe : e.fieldErrors()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("rule", fe.rule());
                one.put("field", fe.field());
                one.put("message", fe.message());
                fields.add(one);
            }
            body.put("fieldErrors", fields);
        }

        if (status >= 500) {
            log.error("publication error {} -> {}", code, status, e);
        } else {
            log.debug("publication error {} -> {}: {}", code, status, e.getMessage());
        }

        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * The catalogue code of a failure, or null when it is not one of ours.
     *
     * Kept as a static helper because the guard test asserts the code of every
     * mapped type against the catalogue without standing a container up.
     */
    static String codeOf(Throwable e) {
        return e instanceof PublicationException pe ? pe.code() : null;
    }
}
