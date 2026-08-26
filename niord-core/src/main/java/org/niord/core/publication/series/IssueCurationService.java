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

    /**
     * O-4. Including a message the query already selects is refused.
     *
     * Not harmless duplication: the override would sit in the audit trail
     * claiming somebody decided to add a message that was already there, and if
     * the criteria later narrow, that stale INCLUDE quietly keeps a message the
     * query no longer selects -- a membership nobody chose, explained by a
     * decision nobody made.
     *
     * Checked against the members already resolved onto the issue rather than by
     * re-running the query: an OPEN issue's rows are the live resolution, and
     * re-resolving here would double the cost of every curation click.
     */
    @Transactional
    public IssueOverride include(PublicationIssue issue, String messageUid, User author, String reason) {
        Long alreadyAMember = em.createQuery(
                        "SELECT COUNT(m) FROM IssueMember m WHERE m.issue = :i AND m.messageUid = :uid "
                                + "AND m.source = :criteria", Long.class)
                .setParameter("i", issue)
                .setParameter("uid", messageUid)
                .setParameter("criteria", MemberSource.CRITERIA)
                .getSingleResult();
        if (alreadyAMember > 0) {
            throw new IssueLifecycleService.TransitionRefusedException("OVERRIDE_ALREADY_A_MEMBER",
                    "message '" + messageUid + "' is already selected by this issue's criteria. An "
                            + "INCLUDE on top of it records a decision nobody made, and would keep "
                            + "the message if the criteria later narrowed.");
        }
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
        // O-6. An override naming a message that does not exist is a hard error.
        // Silently keeping it would leave a curation decision that can never
        // apply, and the annex report takes its heading from the first member --
        // so the visible result of a bad uid is an untitled PDF rather than a
        // complaint.
        Long messages = em.createQuery(
                        "SELECT COUNT(m) FROM Message m WHERE m.uid = :uid", Long.class)
                .setParameter("uid", messageUid)
                .getSingleResult();
        if (messages == 0) {
            throw new IssueLifecycleService.TransitionRefusedException("OVERRIDE_MESSAGE_NOT_FOUND",
                    "no message has uid '" + messageUid + "'. An override that names nothing can "
                            + "never apply, and would sit in the audit trail looking like a decision.");
        }

        List<IssueOverride> existing = em.createQuery(
                        "SELECT o FROM IssueOverride o WHERE o.issue = :i AND o.messageUid = :uid",
                        IssueOverride.class)
                .setParameter("i", issue).setParameter("uid", messageUid).getResultList();
        for (IssueOverride old : existing) {
            em.remove(old);
        }
        // FLUSHED before the replacement is persisted, and this is load-bearing.
        // Hibernate orders its action queue inserts-before-deletes, so without it
        // the new row reaches the database while the old one is still there --
        // which is a silent no-op until UNIQUE (issue_id, messageUid) exists, and
        // a constraint violation the moment it does. The invariant was specified
        // in DATA-MODEL §8.3 and asserted here all along ("two overrides for one
        // message were kept" is the failure message); only the ordering that makes
        // it survivable at the database level was missing.
        em.flush();

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
