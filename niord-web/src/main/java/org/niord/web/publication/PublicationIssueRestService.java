package org.niord.web.publication;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.niord.core.publication.series.IssueAuditEntry;
import org.niord.core.publication.series.IssueAuditService;
import org.niord.core.publication.series.IssueCurationService;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueService;
import org.niord.core.publication.series.PublishChecklistService;
import org.niord.core.publication.series.vo.IssueAuditEntryVo;
import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.series.vo.PublicationIssueVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The issues resource.
 *
 * Same tier discipline as the series: the public shape and the editor shape are
 * different types on different endpoints, never one endpoint choosing.
 *
 * The member list is the one place where the response depends on state rather
 * than on the caller: an OPEN issue returns the LIVE resolution, a PUBLISHED one
 * returns the FROZEN rows. That is keyed off status with no request parameter,
 * deliberately -- a parameter would let a caller ask a published issue what it
 * "would" contain now, and that answer looks authoritative while describing a
 * document nobody ever published.
 */
@Path("/publication-issues")
@RequestScoped
@Transactional
@SuppressWarnings("unused")
public class PublicationIssueRestService {

    @Inject
    PublicationIssueService issueService;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueCurationService curation;

    @Inject
    IssueAuditService audit;

    @Inject
    PublishChecklistService checklist;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ reads

    /** I3. Public shape. */
    @GET
    @Path("/issue/{publicId}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public PublicationIssueVo get(@PathParam("publicId") String publicId) {
        return required(publicId).toVo(PublicationIssueVo.class);
    }

    /** I4. Editor shape. */
    @GET
    @Path("/editable-issue/{publicId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo getEditable(@PathParam("publicId") String publicId) {
        return required(publicId).toVo(SystemPublicationIssueVo.class);
    }

    /**
     * I10. The member list.
     *
     * Live while OPEN, frozen once PUBLISHED, and the caller does not get to
     * choose. Asking a published issue what it would contain today produces an
     * authoritative-looking answer about a document that does not exist.
     */
    @GET
    @Path("/issue/{publicId}/members")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<IssueMemberVo> members(@PathParam("publicId") String publicId) {
        PublicationIssue issue = required(publicId);

        List<IssueMember> frozen = em.createQuery(
                        "SELECT m FROM IssueMember m WHERE m.issue = :i ORDER BY m.sortIndex", IssueMember.class)
                .setParameter("i", issue).getResultList();

        List<IssueMemberVo> out = new ArrayList<>();
        for (IssueMember m : frozen) {
            IssueMemberVo vo = new IssueMemberVo();
            vo.setMessageUid(m.getMessageUid());
            vo.setSortIndex(m.getSortIndex());
            vo.setFrozenShortId(m.getFrozenShortId());
            vo.setFrozenType(m.getFrozenType());
            vo.setFrozenStatus(m.getFrozenStatus());
            vo.setFrozenPublishDateFrom(m.getFrozenPublishDateFrom());
            vo.setFrozenPublishDateTo(m.getFrozenPublishDateTo());
            vo.setSource(m.getSource() == null ? null : m.getSource().name());
            // DERIVED from source and the snapshot relation, never a stored column:
            // a stored derivable value is a second source of truth that can
            // disagree with the first.
            vo.setReasonCode(deriveReason(m, issue));
            vo.setReasonNote(m.getReasonNote());
            out.add(vo);
        }
        return out;
    }

    private static String deriveReason(IssueMember m, PublicationIssue issue) {
        if (m.getSource() == org.niord.core.publication.series.MemberSource.OVERRIDE_INCLUDE) {
            return "MANUAL_INCLUDE";
        }
        if (m.getSource() == org.niord.core.publication.series.MemberSource.IMPORTED) {
            return "IMPORTED";
        }
        return "IN_FORCE_AT_CUTOFF".equals(issue.getSnapshotTimeRelation())
                ? "IN_FORCE_AT_CUTOFF" : "IN_INTERVAL";
    }

    /** I11. The Historik panel. */
    @GET
    @Path("/issue/{publicId}/audit")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<IssueAuditEntryVo> auditTrail(@PathParam("publicId") String publicId) {
        List<IssueAuditEntryVo> out = new ArrayList<>();
        for (IssueAuditEntry e : audit.forIssue(required(publicId))) {
            IssueAuditEntryVo vo = new IssueAuditEntryVo();
            vo.setId(e.getId());
            vo.setAction(e.getAction());
            vo.setActorKind(e.getActorKind() == null ? null : e.getActorKind().name());
            vo.setActorLabel(e.getUser() == null ? e.getActorLabel() : e.getUser().getUsername());
            vo.setCreated(e.getCreated());
            vo.setReason(e.getReason());
            vo.setArchivePath(e.getArchivePath());
            vo.setDetail(e.getDetail());
            out.add(vo);
        }
        return out;
    }

    /** I15. The release rail. */
    @GET
    @Path("/issue/{publicId}/publish-checklist")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> publishChecklist(@PathParam("publicId") String publicId,
                                                @QueryParam("allowFuture") boolean allowFuture) {
        PublicationIssue issue = required(publicId);
        PublishChecklistService.Checklist result =
                checklist.compute(issue, new Date(), allowFuture, false);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PublishChecklistService.CheckRow r : result.rows()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", r.code());
            row.put("severity", r.severity().name());
            row.put("passed", r.passed());
            row.put("acknowledgeable", r.acknowledgeable());
            row.put("detail", r.detail());
            rows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("canPublish", result.canPublish());
        out.put("blockingCodes", result.blockingCodes());
        return out;
    }

    // ------------------------------------------------------------------ actions

    /** I16. Publish. */
    @PUT
    @Path("/issue/{publicId}/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> publish(@PathParam("publicId") String publicId,
                                       Map<String, Object> params) {
        PublicationIssue issue = required(publicId);

        @SuppressWarnings("unchecked")
        List<String> acknowledged = params == null ? List.of()
                : (List<String>) params.getOrDefault("acknowledgedWarnings", List.of());
        boolean regenerate = params == null || Boolean.TRUE.equals(params.getOrDefault("regenerate", true));

        IssuePublishService.PublishResult result = publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(regenerate, Set.copyOf(acknowledged), null, null));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publicId", publicId);
        out.put("stampedAt", result.stampedAt().getTime());
        out.put("memberCount", result.memberCount());
        out.put("unacknowledgedWarnings", result.unacknowledgedWarnings());
        out.put("successorId", result.successorId());
        return out;
    }

    /** I18 and I19. */
    @PUT
    @Path("/issue/{publicId}/retire")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo retire(@PathParam("publicId") String publicId,
                                           @QueryParam("reason") String reason) {
        return lifecycle.retire(required(publicId), null, reason).toVo(SystemPublicationIssueVo.class);
    }

    @PUT
    @Path("/issue/{publicId}/reactivate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo reactivate(@PathParam("publicId") String publicId,
                                               @QueryParam("reason") String reason) {
        return lifecycle.reactivate(required(publicId), null, reason).toVo(SystemPublicationIssueVo.class);
    }

    /** I9. Delete, guarded. */
    @DELETE
    @Path("/issue/{publicId}")
    @RolesAllowed("admin")
    public void delete(@PathParam("publicId") String publicId) {
        lifecycle.deleteIssue(required(publicId), null);
    }

    // ------------------------------------------------------------------ curation

    /** I12, I13, I14. Curation requires the curate permission, not merely edit rights. */
    @PUT
    @Path("/issue/{publicId}/overrides/include")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"publication-curate", "admin"})
    public void includeMember(@PathParam("publicId") String publicId, Map<String, String> body) {
        curation.include(required(publicId), body.get("messageUid"), null, body.get("reason"));
    }

    @PUT
    @Path("/issue/{publicId}/overrides/exclude")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"publication-curate", "admin"})
    public void excludeMember(@PathParam("publicId") String publicId, Map<String, String> body) {
        curation.exclude(required(publicId), body.get("messageUid"), null, body.get("reason"));
    }

    private PublicationIssue required(String publicId) {
        PublicationIssue issue = issueService.findByPublicId(publicId);
        if (issue == null) {
            throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_FOUND",
                    "no issue with public id " + publicId);
        }
        return issue;
    }
}
