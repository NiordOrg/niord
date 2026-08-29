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

package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Date;
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

    @Inject
    MemberResolutionService resolver;

    @Inject
    org.niord.core.publication.series.criteria.DomainSeriesExpander domains;

    /**
     * O-4. Including a message the query already selects is refused.
     *
     * Not harmless duplication: the override would sit in the audit trail
     * claiming somebody decided to add a message that was already there, and if
     * the criteria later narrow, that stale INCLUDE quietly keeps a message the
     * query no longer selects -- a membership nobody chose, explained by a
     * decision nobody made.
     *
     * ASKED OF THE LIVE RESOLUTION, because there is nothing else to ask. Member
     * rows are written by the freeze and curation is only legal while the issue is
     * OPEN, so the two conditions never hold at once: counting rows meant counting
     * zero, every time, on every issue this rule could apply to. The rule was
     * unenforceable in the shape it was written in, and read as enforced.
     */
    @Transactional
    public IssueOverride include(PublicationIssue issue, String messageUid, User author, String reason) {
        if (selectedByCriteria(issue, messageUid)) {
            throw new IssueLifecycleService.TransitionRefusedException("OVERRIDE_ALREADY_A_MEMBER",
                    "message '" + messageUid + "' is already selected by this issue's criteria. An "
                            + "INCLUDE on top of it records a decision nobody made, and would keep "
                            + "the message if the criteria later narrowed.");
        }
        return addOverride(issue, messageUid, OverrideKind.INCLUDE, author, reason, AuditAction.OVERRIDE_INCLUDED);
    }

    /**
     * Whether the issue's own criteria already select this message.
     *
     * The frozen rows for an issue that has them, the live resolution for one that
     * does not. A resolution that cannot be taken -- an unresolvable document, a
     * series with no query -- answers "no": refusing a curation because the
     * criteria could not be run would block the one action that still works on a
     * misconfigured series.
     */
    private boolean selectedByCriteria(PublicationIssue issue, String messageUid) {
        Long frozen = em.createQuery(
                        "SELECT COUNT(m) FROM IssueMember m WHERE m.issue = :i AND m.messageUid = :uid "
                                + "AND m.source = :criteria", Long.class)
                .setParameter("i", issue)
                .setParameter("uid", messageUid)
                .setParameter("criteria", MemberSource.CRITERIA)
                .getSingleResult();
        if (frozen > 0) {
            return true;
        }

        PublicationSeries series = issue.getSeries();
        if (series == null || series.getContentMode() != ContentMode.GENERATED_FROM_QUERY
                || series.getTimeRelation() == null) {
            return false;
        }
        try {
            ResolvedCriteria criteria = EffectiveCriteria.resolvedFor(issue, domains);
            if (criteria == null) {
                return false;
            }
            // Resolved WITHOUT the curation: the question is what the query
            // selects, and folding the existing overrides in would make an earlier
            // include look like a criteria match.
            return resolver.resolve(criteria, new Interval(issue.getIntervalFrom(), new Date()))
                    .members().contains(messageUid);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Transactional
    public IssueOverride exclude(PublicationIssue issue, String messageUid, User author, String reason) {
        return addOverride(issue, messageUid, OverrideKind.EXCLUDE, author, reason, AuditAction.OVERRIDE_EXCLUDED);
    }

    /**
     * The same decision over a selection, and it is ALL OR NOTHING.
     *
     * A curator excluding eleven messages is taking one decision about a list,
     * not eleven decisions that happen to arrive together. Applying the nine that
     * resolve and refusing the two that do not would leave the issue in a state
     * nobody asked for and no single message would say so -- so every uid is
     * checked before any override is written, and the refusal names EVERY
     * offender rather than the first, because a curator who has to discover them
     * one request at a time will give up before the list is clean.
     */
    @Transactional
    public List<IssueOverride> curate(PublicationIssue issue, List<String> messageUids,
                                      OverrideKind kind, User author, String reason) {
        assertOpen(issue);
        assertReason(reason);
        if (messageUids == null || messageUids.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("NO_MESSAGES",
                    "a curation decision names at least one message");
        }
        if (messageUids.size() > MAX_CURATED_AT_ONCE) {
            throw new IssueLifecycleService.TransitionRefusedException("TOO_MANY_MESSAGES",
                    "at most " + MAX_CURATED_AT_ONCE + " messages in one decision; " + messageUids.size()
                            + " were named");
        }

        List<String> unique = messageUids.stream().distinct().toList();
        List<String> unknown = unique.stream().filter(uid -> !messageExists(uid)).toList();
        if (!unknown.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("OVERRIDE_MESSAGE_NOT_FOUND",
                    "no message has uid " + String.join(", ", unknown)
                            + ". Nothing was recorded: a decision about a list is taken over the whole "
                            + "list, and a partly applied one is a state nobody asked for.");
        }

        List<IssueOverride> written = new ArrayList<>();
        for (String uid : unique) {
            written.add(kind == OverrideKind.INCLUDE
                    ? include(issue, uid, author, reason)
                    : exclude(issue, uid, author, reason));
        }
        return written;
    }

    /**
     * Withdraw a curation decision, by the message it was about.
     *
     * Addressed by message rather than by override id because that is what the
     * caller is looking at: a row in the member list with a why-line saying
     * somebody put it there. An override that is not there is not an error worth
     * refusing over -- the desired state is "no override for this message", and
     * it already holds.
     */
    @Transactional
    public void clear(PublicationIssue issue, String messageUid, User actor, String reason) {
        assertOpen(issue);
        assertReason(reason);
        List<IssueOverride> existing = em.createQuery(
                        "SELECT o FROM IssueOverride o WHERE o.issue = :i AND o.messageUid = :uid",
                        IssueOverride.class)
                .setParameter("i", issue).setParameter("uid", messageUid).getResultList();
        if (existing.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("OVERRIDE_NOT_FOUND",
                    "no curation decision on this issue names message '" + messageUid + "'");
        }
        for (IssueOverride override : existing) {
            remove(override, actor, reason.trim());
        }
    }

    /** One decision, at most this many messages. */
    public static final int MAX_CURATED_AT_ONCE = 500;

    private boolean messageExists(String uid) {
        return em.createQuery("SELECT COUNT(m) FROM Message m WHERE m.uid = :uid", Long.class)
                .setParameter("uid", uid).getSingleResult() > 0;
    }

    /**
     * A reason, of a length somebody can actually read.
     *
     * The floor is what separates a reason from a keystroke: "x" records that
     * somebody typed something, which is worse than nothing because it looks like
     * a decision was explained. The ceiling keeps a rail readable.
     */
    private static void assertReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < MIN_REASON || trimmed.length() > MAX_REASON) {
            throw new IssueLifecycleService.TransitionRefusedException("OVERRIDE_REASON_REQUIRED",
                    "a curation decision must say why, in between " + MIN_REASON + " and " + MAX_REASON
                            + " characters; an unexplained override is unreviewable later");
        }
    }

    static final int MIN_REASON = 3;
    static final int MAX_REASON = 512;

    @Transactional
    public void remove(IssueOverride override, User actor) {
        remove(override, actor, null);
    }

    /** Withdraw one override, recording why where a reason was given. */
    @Transactional
    public void remove(IssueOverride override, User actor, String reason) {
        assertOpen(override.getIssue());
        PublicationIssue issue = override.getIssue();
        String uid = override.getMessageUid();
        em.remove(em.contains(override) ? override : em.merge(override));
        // The member set changed, and the issue's own stamp is what says so --
        // the preview's freshness is read against it.
        issue.setUpdated(new Date());
        em.merge(issue);
        // And its revision moves with it. A child row coming or going does not
        // touch the parent's counter on its own, so without this two curators who
        // both loaded the issue at revision 7 would both commit at revision 7 and
        // the second decision would silently replace the first.
        StaleVersionGuard.forceIncrement(em, issue);
        audit.override(issue, actor, AuditAction.OVERRIDE_REMOVED, uid, reason);
    }

    public List<IssueOverride> forIssue(PublicationIssue issue) {
        return em.createQuery("SELECT o FROM IssueOverride o WHERE o.issue = :i ORDER BY o.id",
                        IssueOverride.class)
                .setParameter("i", issue).getResultList();
    }

    private IssueOverride addOverride(PublicationIssue issue, String messageUid, OverrideKind kind,
                                      User author, String reason, AuditAction action) {
        assertOpen(issue);
        assertReason(reason);

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
        // as a data-model rule and asserted here all along ("two overrides for one
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
        // The member set changed; see remove().
        issue.setUpdated(new Date());
        em.merge(issue);
        StaleVersionGuard.forceIncrement(em, issue);

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
