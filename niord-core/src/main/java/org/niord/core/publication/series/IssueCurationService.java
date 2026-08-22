package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.List;

/**
 * Curation: a person adding a message to an issue, or removing one.
 *
 * Overrides are recorded rather than applied destructively. The query result is
 * never edited; the override sits beside it and the resolver composes the two.
 * That is what makes "why is this here" answerable after the fact, and what lets
 * an override be withdrawn without re-deriving anything.
 *
 * Curation is only legal while the issue is still OPEN. Once frozen, the member
 * set is a historical record -- changing it would rewrite what was published.
 */
@ApplicationScoped
public class IssueCurationService extends BaseService {

    @Inject
    IssueAuditService audit;

    @Transactional
    public IssueOverride include(PublicationIssue issue, String messageUid, User author, String reason) {
        return addOverride(issue, messageUid, OverrideKind.INCLUDE, author, reason, "OVERRIDE_INCLUDED");
    }

    @Transactional
    public IssueOverride exclude(PublicationIssue issue, String messageUid, User author, String reason) {
        return addOverride(issue, messageUid, OverrideKind.EXCLUDE, author, reason, "OVERRIDE_EXCLUDED");
    }

    @Transactional
    public void remove(IssueOverride override, User actor) {
        assertOpen(override.getIssue());
        PublicationIssue issue = override.getIssue();
        String uid = override.getMessageUid();
        em.remove(em.contains(override) ? override : em.merge(override));
        audit.override(issue, actor, "OVERRIDE_REMOVED", uid, null);
    }

    public List<IssueOverride> forIssue(PublicationIssue issue) {
        return em.createQuery("SELECT o FROM IssueOverride o WHERE o.issue = :i ORDER BY o.id",
                        IssueOverride.class)
                .setParameter("i", issue).getResultList();
    }

    private IssueOverride addOverride(PublicationIssue issue, String messageUid, OverrideKind kind,
                                      User author, String reason, String action) {
        assertOpen(issue);
        if (reason == null || reason.isBlank()) {
            throw new IssueLifecycleService.TransitionRefusedException("OVERRIDE_REASON_REQUIRED",
                    "a curation decision must say why; an unexplained override is unreviewable later");
        }

        // One override per message per issue: two would be either redundant or
        // contradictory, and neither is something to store.
        List<IssueOverride> existing = em.createQuery(
                        "SELECT o FROM IssueOverride o WHERE o.issue = :i AND o.messageUid = :uid",
                        IssueOverride.class)
                .setParameter("i", issue).setParameter("uid", messageUid).getResultList();
        for (IssueOverride old : existing) {
            em.remove(old);
        }

        IssueOverride override = new IssueOverride();
        override.setIssue(issue);
        override.setMessageUid(messageUid);
        override.setKind(kind);
        override.setAuthor(author);
        override.setReason(reason);
        em.persist(override);

        audit.override(issue, author, action, messageUid, reason);
        return override;
    }

    private static void assertOpen(PublicationIssue issue) {
        if (issue.getStatus() != IssueStatus.OPEN) {
            throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_OPEN",
                    "the member set is frozen; changing it now would rewrite what was published");
        }
    }
}
