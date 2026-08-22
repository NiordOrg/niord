package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.niord.core.message.Message;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.MemberDecision;
import org.niord.core.publication.series.resolve.MembershipPredicate;
import org.niord.core.publication.series.resolve.MembershipReason;
import org.niord.core.publication.series.resolve.MessageFacts;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.service.BaseService;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the members of an issue: SQL narrows coarsely, the pure predicate decides.
 *
 * The split is the design, not an implementation detail. SQL is deliberately
 * LOOSER than the rule -- it uses >= where the rule uses >, and it does not
 * apply the liveness clause at all -- so the candidate set is always a superset
 * of the answer and the predicate makes every final call. That puts the pure
 * function on the production path rather than beside it, and it is what the
 * differential test in B1.2 checks.
 *
 * Four things this deliberately does NOT do:
 *
 * RI-5. It never routes through MessageSearchRestService. That layer calls
 * adjustDateInterval, which snaps the interval to whole days and destroys
 * release-moment precision -- week 23 would become 27.05 00:00 to 03.06 23:59
 * and pull in the three messages that belong to week 24. It also rewrites
 * seriesIds and forces PUBLISHED-only, and its notion of the current domain is
 * a thread-local from a request header, so server-side callers lose it silently.
 *
 * RI-6. It never hands an empty collection to a SQL IN. CriteriaHelper.in()
 * emits a disjunction that is always false, and the area/category paths are
 * worse -- an unresolvable id becomes null, the stream filters it out, and the
 * result is an OR over nothing. Both silently empty the issue. Operands are
 * checked here and raise.
 *
 * RI-12. No paging and no cap. The query is built directly, so the 100-row
 * default that lives in MessageSearchParams.instantiate() cannot apply.
 *
 * RI-13. Pure JPA, no Lucene. Lucene is reached only for a non-blank free-text
 * query, and the criteria vocabulary has no free-text node. That is what makes
 * a mass historical replay deterministic; adding free text later reopens it.
 */
@ApplicationScoped
@Transactional
public class MemberResolutionService extends BaseService {

    /** What a resolution produced: every decision, and the members that survived. */
    public record Resolution(
            Map<String, MemberDecision> decisions,
            List<String> candidateUids,
            Set<String> members) {

        public int candidateCount() {
            return candidateUids.size();
        }
    }

    /** An operand that would have produced an always-false or always-true query. */
    public static class UnresolvableOperandException extends RuntimeException {
        public UnresolvableOperandException(String message) {
            super(message);
        }
    }

    /**
     * Resolves an issue's membership.
     *
     * @param criteria the series' criteria, already resolved and expanded
     * @param interval the issue's window
     * @param manualIncludes uids a curator added, or null
     * @param manualExcludes uids a curator removed, or null
     */
    public Resolution resolve(ResolvedCriteria criteria, Interval interval,
                              Set<String> manualIncludes, Set<String> manualExcludes) {
        if (criteria == null || interval == null) {
            throw new IllegalArgumentException("resolve() takes no nulls");
        }
        guardOperands(criteria);

        List<MessageFacts> candidates = narrow(criteria, interval);

        Map<String, MemberDecision> decisions =
                MembershipPredicate.decideAll(candidates, criteria, interval);
        Set<String> members =
                MembershipPredicate.applyOverrides(decisions, manualIncludes, manualExcludes);

        List<String> candidateUids = new ArrayList<>(candidates.size());
        for (MessageFacts f : candidates) {
            candidateUids.add(f.uid());
        }
        return new Resolution(decisions, candidateUids, members);
    }

    /** Convenience for callers with no curation. */
    public Resolution resolve(ResolvedCriteria criteria, Interval interval) {
        return resolve(criteria, interval, Set.of(), Set.of());
    }

    /**
     * RI-6. An operand list that reached this point empty would become a SQL IN
     * over nothing, which is always false, and the issue would resolve empty
     * while looking perfectly healthy.
     */
    private void guardOperands(ResolvedCriteria criteria) {
        if (criteria.messageSeriesIds().stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new UnresolvableOperandException(
                    "a blank message-series operand reached the query; it would narrow to nothing");
        }
        if (criteria.types().stream().anyMatch(java.util.Objects::isNull)) {
            throw new UnresolvableOperandException(
                    "a null message-type operand reached the query; it would narrow to nothing");
        }
    }

    /**
     * The coarse narrowing.
     *
     * Every bound here is at least as permissive as the rule:
     *  - the upper bound matches (closed, at the cut-off)
     *  - the lower bound uses >= where the rule uses >, so a message stamped
     *    exactly on the previous cut-off is a CANDIDATE and the predicate is the
     *    thing that rejects it. Never between(), which is closed at both ends.
     *  - liveness is not applied at all: its NULL-safety is the hazard, and it
     *    belongs in one place.
     */
    private List<MessageFacts> narrow(ResolvedCriteria criteria, Interval interval) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Message> query = cb.createQuery(Message.class);
        Root<Message> message = query.from(Message.class);

        List<Predicate> where = new ArrayList<>();

        // RI-3's SQL half. Messages with no publishDateFrom cannot be compared to
        // any bound; they are surfaced separately as omissions rather than here.
        where.add(cb.isNotNull(message.get("publishDateFrom")));

        // Closed at the cut-off, for both time relations.
        where.add(cb.lessThanOrEqualTo(
                message.<Date>get("publishDateFrom"), interval.cutoff()));

        // Deliberately loose: >= against the rule's >. Only PUBLISHED_IN_INTERVAL
        // has a lower bound at all -- IN_FORCE_AT_CUTOFF never reads the previous
        // issue (RI-7), so applying one here would be wrong, not merely tight.
        if (interval.previousCutoff() != null
                && criteria.timeRelation() == org.niord.core.publication.series.resolve.TimeRelation.PUBLISHED_IN_INTERVAL) {
            where.add(cb.greaterThanOrEqualTo(
                    message.<Date>get("publishDateFrom"), interval.previousCutoff()));
        }

        if (!criteria.messageSeriesIds().isEmpty()) {
            where.add(message.get("messageSeries").get("seriesId").in(criteria.messageSeriesIds()));
        }
        if (!criteria.types().isEmpty()) {
            where.add(message.get("type").in(criteria.types()));
        }

        // Status is NOT narrowed here. It is a resolver invariant (RI-1) derived
        // from Status.isPublic(), and expressing it as a stored or query-level
        // conjunct is how it drifts -- a PUBLISHED-only conjunct empties every
        // historical issue, since most of the corpus is EXPIRED or CANCELLED.

        query.select(message).where(cb.and(where.toArray(new Predicate[0])));

        // RI-12. No setMaxResults, no paging. The default cap lives in
        // MessageSearchParams.instantiate() and cannot reach a query built here.
        List<Message> rows = em.createQuery(query).getResultList();

        List<MessageFacts> facts = new ArrayList<>(rows.size());
        for (Message m : rows) {
            facts.add(factsOf(m));
        }
        return facts;
    }

    /** Reads the facts membership depends on, and nothing else. */
    static MessageFacts factsOf(Message m) {
        return new MessageFacts(
                m.getUid(),
                m.getPublishDateFrom(),
                m.getPublishDateTo(),
                m.getStatus(),
                m.getType(),
                m.getMessageSeries() == null ? null : m.getMessageSeries().getSeriesId());
    }

    /**
     * The candidate set alone, for the differential test. Exposed because the
     * whole point of the design is that this is a superset of the answer, and a
     * claim like that is worth being able to check directly.
     */
    public List<MessageFacts> candidatesFor(ResolvedCriteria criteria, Interval interval) {
        guardOperands(criteria);
        return narrow(criteria, interval);
    }

    /** The reasons, for the why-lines. */
    public Map<String, MembershipReason> reasons(Resolution resolution,
                                                 Set<String> manualIncludes, Set<String> manualExcludes) {
        Map<String, MembershipReason> out = new LinkedHashMap<>();
        for (String uid : resolution.members()) {
            out.put(uid, MembershipPredicate.reasonAfterOverrides(
                    uid, resolution.decisions(), manualIncludes, manualExcludes));
        }
        return out;
    }
}
