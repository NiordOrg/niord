package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.niord.core.message.Message;
import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.MembershipPredicate;
import org.niord.core.publication.series.resolve.MessageFacts;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.service.BaseService;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which publication issues a message is in, answered from the message.
 *
 * Editors ask "is this already out, and where" constantly, and until now the only
 * way to answer was to open each issue in the admin area and look. The question
 * belongs on the message.
 *
 * TWO HALVES, and they are different KINDS of answer rather than two queries.
 *
 * A published issue froze its membership at publication, so its answer is a fact:
 * read the member rows. An OPEN issue has no member rows at all -- they are
 * written at publish and nowhere else -- so its answer has to be resolved now,
 * and it is a PREDICTION: what this issue would contain if it closed at this
 * moment. Those must not be presented as the same thing, which is why every row
 * carries which one it is.
 *
 * The prediction is the point of the feature. "It is in EfS week 29 (open)" is
 * the sentence an editor needs before deciding whether to amend a message, and it
 * cannot come from stored data because nothing has stored it yet.
 *
 * ONE MESSAGE, NOT ONE ISSUE. The publish screen resolves an issue's whole
 * membership; doing that here, once per open issue, would make opening a message
 * cost a dozen full resolutions -- one of them over a five-year interval. It is
 * not needed. MemberResolutionService documents its SQL narrowing as a SUPERSET
 * of the answer and re-checks every narrowed conjunct in the predicate, so for a
 * single message the predicate alone gives the same answer: one fact read, then
 * pure evaluation per issue. The publish screen stays the authority on an issue's
 * membership; this asks the identical rule a narrower question.
 */
@ApplicationScoped
public class MessageIssueLookup extends BaseService {

    /** How a message came to be in an issue: recorded, or resolved just now. */
    public enum Membership {
        /** Frozen at publication. A fact about a document that exists. */
        FROZEN,
        /** Resolved at read time. What this open issue would contain if it closed now. */
        LIVE
    }

    /** One issue a message is in, and on what basis. */
    public record MessageIssue(PublicationIssue issue, Membership membership) {
    }

    /**
     * Every issue this message is in, frozen first.
     *
     * Frozen first because those are the ones already published: an editor
     * scanning the list wants "this is already out" before "this is due to go
     * out".
     */
    @Transactional
    public List<MessageIssue> forMessage(String messageUid, Date now) {
        if (messageUid == null || messageUid.isBlank()) {
            return List.of();
        }
        Date at = now == null ? new Date() : now;

        List<MessageIssue> out = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();

        for (PublicationIssue issue : frozenIssues(messageUid)) {
            if (issue.getId() != null && seen.add(issue.getId())) {
                out.add(new MessageIssue(issue, Membership.FROZEN));
            }
        }

        MessageFacts facts = factsOf(messageUid);
        Map<Integer, OverrideKind> overrides = overridesFor(messageUid);

        for (PublicationIssue issue : openIssues()) {
            // An issue that already froze this message is reported once, as the
            // fact rather than the prediction. Re-resolving it would also be
            // wrong: a published issue asked what it would contain today gives an
            // authoritative-looking answer about a document that does not exist.
            if (issue.getId() == null || seen.contains(issue.getId())) {
                continue;
            }
            if (wouldContain(issue, facts, overrides.get(issue.getId()), at)) {
                seen.add(issue.getId());
                out.add(new MessageIssue(issue, Membership.LIVE));
            }
        }
        return out;
    }

    /**
     * Issues that recorded this message at publication.
     *
     * The series is fetch-joined because every caller reads it, and this list is
     * not short: an IN_FORCE_AT_CUTOFF series re-lists every message still in
     * force in every edition, so a long-lived notice is a member of a hundred-odd
     * issues of one series. Left lazy, that is a hundred extra round trips on a
     * panel that renders when a message is opened.
     */
    private List<PublicationIssue> frozenIssues(String messageUid) {
        return em.createQuery(
                        "SELECT i FROM IssueMember m JOIN m.issue i JOIN FETCH i.series s "
                                + "WHERE m.messageUid = :uid ORDER BY i.intervalFrom DESC",
                        PublicationIssue.class)
                .setParameter("uid", messageUid)
                .getResultList();
    }

    /**
     * The open issues on a live series.
     *
     * Not narrowed to query-backed ones. A series with no criteria still has
     * members -- the annex issues name theirs by hand -- and those are exactly the
     * memberships an editor has no other way to discover, since no query explains
     * them. A DRAFT series is excluded because it publishes nothing: reporting a
     * membership in a definition somebody is still drafting is noise, not
     * transparency.
     */
    private List<PublicationIssue> openIssues() {
        return em.createQuery(
                        "SELECT i FROM PublicationIssue i JOIN FETCH i.series s "
                                + "WHERE i.status = org.niord.core.publication.series.IssueStatus.OPEN "
                                + "AND s.status = org.niord.core.publication.series.SeriesStatus.ACTIVE "
                                + "ORDER BY i.intervalFrom DESC", PublicationIssue.class)
                .getResultList();
    }

    /**
     * This message's curator overrides, by issue.
     *
     * One query for the whole answer rather than one per issue: an override names
     * a message directly, so asking by message is the natural direction and the
     * result is a handful of rows.
     */
    private Map<Integer, OverrideKind> overridesFor(String messageUid) {
        Map<Integer, OverrideKind> out = new HashMap<>();
        List<IssueOverride> rows = em.createQuery(
                        "SELECT o FROM IssueOverride o WHERE o.messageUid = :uid", IssueOverride.class)
                .setParameter("uid", messageUid)
                .getResultList();
        for (IssueOverride o : rows) {
            if (o.getIssue() != null && o.getIssue().getId() != null) {
                out.put(o.getIssue().getId(), o.getKind());
            }
        }
        return out;
    }

    /** The facts membership turns on, or null when the message is gone. */
    private MessageFacts factsOf(String messageUid) {
        List<Message> rows = em.createQuery(
                        "SELECT m FROM Message m WHERE m.uid = :uid", Message.class)
                .setParameter("uid", messageUid)
                .getResultList();
        return rows.isEmpty() ? null : MemberResolutionService.factsOf(rows.get(0));
    }

    /**
     * Whether this open issue would contain the message if it closed now.
     *
     * The same interval the publish checklist uses -- the issue's own start to the
     * proposed cut-off -- so the two cannot disagree about what is in an issue.
     * Answering over a different window would mean the message detail and the
     * publish screen tell an editor different things about the same issue.
     *
     * An override settles it outright, in both directions and for every series
     * shape. That is the publish path's own order: overrides constitute membership
     * on their own, so a named message is in a criteria-less annex issue and an
     * excluded one is out of an issue whose query still selects it.
     *
     * A criteria document that cannot resolve is NOT a membership answer, so it
     * returns false rather than guessing. The series validation surface is where
     * that gets reported; inventing membership here would put a wrong sentence on
     * a message.
     *
     * Static and free of the entity manager so a plain unit test can pin it. The
     * override precedence in particular is one deleted branch away from telling an
     * editor a message is in an issue a curator explicitly removed it from.
     */
    static boolean wouldContain(PublicationIssue issue, MessageFacts facts,
                                OverrideKind override, Date now) {
        if (override == OverrideKind.EXCLUDE) {
            return false;
        }
        if (override == OverrideKind.INCLUDE) {
            return true;
        }

        PublicationSeries series = issue.getSeries();
        if (facts == null || series == null
                || series.getContentMode() != ContentMode.GENERATED_FROM_QUERY
                || series.getCriteria() == null || series.getTimeRelation() == null
                || issue.getIntervalFrom() == null) {
            return false;
        }
        try {
            ResolvedCriteria criteria = CriteriaResolver.resolve(
                    series.getCriteria(),
                    series.getTimeRelation(),
                    Boolean.TRUE.equals(series.getAliveAtCutoff()),
                    CriteriaResolver.NO_DOMAINS);
            return MembershipPredicate
                    .decide(facts, criteria, new Interval(issue.getIntervalFrom(), now))
                    .member();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
