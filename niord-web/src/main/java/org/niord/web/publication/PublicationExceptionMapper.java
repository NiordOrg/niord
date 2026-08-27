package org.niord.web.publication;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueRenderService;
import org.niord.core.publication.series.MemberResolutionService;
import org.niord.core.publication.series.criteria.CriteriaParseException;
import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the domain exceptions into the wire shape, once.
 *
 * Without this every endpoint catches the same handful of exceptions and picks
 * its own status and body, and they drift -- so the same failure looks different
 * depending which endpoint you hit, and a client cannot handle it generically.
 *
 * Every response carries a CODE, not just a message. The message is for a human
 * reading a log; the code is what a client branches on, and a client that has to
 * match on message text breaks the first time somebody improves the wording.
 */
@Provider
public class PublicationExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger log = LoggerFactory.getLogger(PublicationExceptionMapper.class);

    @Override
    public Response toResponse(RuntimeException e) {
        String code = codeOf(e);
        if (code == null) {
            // Not ours. Let it fall through rather than dressing an unrelated
            // failure up as a publication error.
            throw e;
        }

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
        // The codes the admin has to acknowledge, so the dialog can list them
        // rather than send the admin back to the checklist to find out.
        if (e instanceof IssuePublishService.WarningsNotAcknowledgedException warnings) {
            body.put("unacknowledgedWarnings", warnings.codes());
        }

        if (status >= 500) {
            log.error("publication error {} -> {}", code, status, e);
        } else {
            log.debug("publication error {} -> {}: {}", code, status, e.getMessage());
        }

        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON).build();
    }

    /** Maps an exception to its catalogue code, or null when it is not ours. */
    static String codeOf(RuntimeException e) {
        if (e instanceof IssuePublishService.AlreadyPublishedException already) {
            return already.code();
        }
        if (e instanceof IssuePublishService.ArchiveFailedException archive) {
            return archive.code();
        }
        if (e instanceof IssuePublishService.WarningsNotAcknowledgedException warnings) {
            return warnings.code();
        }
        if (e instanceof IssueLifecycleService.TransitionRefusedException refused) {
            return refused.code();
        }
        if (e instanceof IssueNaming.UnknownTokenException unknown) {
            return unknown.code();
        }
        if (e instanceof CriteriaResolver.EmptyOperandException) {
            return "EMPTY_OPERAND";
        }
        if (e instanceof MemberResolutionService.UnresolvableOperandException) {
            return "UNRESOLVABLE_OPERAND";
        }
        if (e instanceof CriteriaParseException) {
            return "CRITERIA_INVALID";
        }
        if (e instanceof IssueRenderService.RenderFailedException) {
            return "RENDER_FAILED";
        }
        return null;
    }
}
