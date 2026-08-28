/*
 * Copyright 2026 Danish Maritime Authority.
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
import jakarta.persistence.FlushModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.core.message.Message;
import org.niord.core.publication.series.resolve.CriteriaMissCode;
import org.niord.core.publication.series.resolve.CriteriaMissVo;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.IssueOrdering;
import org.niord.core.publication.series.resolve.MemberDecision;
import org.niord.core.publication.series.resolve.MembershipPredicate;
import org.niord.core.publication.series.resolve.MembershipReason;
import org.niord.core.publication.series.resolve.MessageFacts;
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.ResolutionWarningVo;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.service.BaseService;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
public class MemberResolutionService extends BaseService {

    @Inject
    AreaService areaService;

    @Inject
    CategoryService categoryService;

    @Inject
    ChartService chartService;

    /** What a resolution produced: every decision, the members, and the diagnostics. */
    public record Resolution(
            Map<String, MemberDecision> decisions,
            List<String> candidateUids,
            Set<String> members,
            List<CriteriaMissVo> misses,
            List<ResolutionWarningVo> warnings) {

        public int candidateCount() {
            return candidateUids.size();
        }

        /** A resolution for a series that has no membership at all. */
        public static Resolution empty() {
            return new Resolution(Map.of(), List.of(), Set.of(), List.of(), List.of());
        }

        /**
         * A resolution whose members were named, not derived.
         *
         * A series with no criteria still has contents when somebody put them
         * there by hand. That is what the NCAGS and Isbilag annexes are: a series
         * holding two live messages a year, each issue naming one of them, where
         * no query of any shape can select one and not the other -- the only
         * discriminator is the message body.
         *
         * Without this, curating such an issue records an audited override and
         * then discards it at publish, and the release checklist reports that
         * every override applied.
         */
        public static Resolution curated(Set<String> uids) {
            List<String> ordered = List.copyOf(uids);
            Map<String, MemberDecision> decisions = new LinkedHashMap<>();
            uids.forEach(uid -> decisions.put(uid, new MemberDecision(uid, true, MembershipReason.MANUAL_INCLUDE)));
            return new Resolution(decisions, ordered, Set.copyOf(uids), List.of(), List.of());
        }

        /** The warning of one code, if it was raised. */
        public Optional<ResolutionWarningVo> warning(ResolutionWarningCode code) {
            return warnings.stream().filter(w -> w.code() == code).findFirst();
        }

        /** The misses of one code. */
        public List<CriteriaMissVo> missesOf(CriteriaMissCode code) {
            return misses.stream().filter(m -> m.code() == code).toList();
        }
    }

    /**
     * How far back to look for messages dropped for having no publishDateFrom.
     * Bounds the scan to roughly a quarter, which covers every observed
     * correction window without walking the whole corpus on every open issue.
     */
    public static final int OMISSION_LOOKBACK_DAYS = 90;

    /**
     * The member-count limit. A product choice, not a technical one: the report
     * pipeline that used to justify a number is no longer on the publish path.
     * The largest live member set is 228.
     */
    public static final int MEMBER_LIMIT = 1000;

    /** An operand that would have produced an always-false or always-true query. */
    public static class UnresolvableOperandException extends PublicationException {
        public UnresolvableOperandException(String message) {
            super("UNRESOLVABLE_OPERAND", message);
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
        // Looked up ONCE, and shared by both queries below. Resolving the same
        // MRN twice would let the two queries disagree if a row moved between
        // them, and it doubles the lookups on every open issue.
        EntityOperands operands = lookUpOperands(criteria);

        List<MessageFacts> candidates = narrow(criteria, operands, interval);

        Map<String, MemberDecision> decisions =
                MembershipPredicate.decideAll(candidates, criteria, interval);
        Set<String> members =
                MembershipPredicate.applyOverrides(decisions, manualIncludes, manualExcludes);

        List<String> candidateUids = new ArrayList<>(candidates.size());
        for (MessageFacts f : candidates) {
            candidateUids.add(f.uid());
        }

        List<CriteriaMissVo> misses = new ArrayList<>();
        for (MessageFacts f : candidates) {
            MemberDecision d = decisions.get(f.uid());
            if (d != null && !d.member()) {
                misses.add(CriteriaMissVo.of(f, d.reason(), interval));
            }
        }

        // Messages with no publishDateFrom never reach the candidate set, because
        // the query excludes them -- they cannot be compared to any bound. They are
        // found separately so their absence is reported rather than silent.
        List<MessageFacts> nullDated = messagesWithNoPublishDate(criteria, operands, interval);
        for (MessageFacts f : nullDated) {
            misses.add(CriteriaMissVo.of(f, MembershipReason.NO_PUBLISH_DATE, interval));
        }

        List<ResolutionWarningVo> warnings =
                warningsFor(candidates, members, nullDated, interval, manualExcludes);

        return new Resolution(decisions, candidateUids, members, misses, warnings);
    }

    /**
     * The warnings one resolution can raise on its own.
     *
     * OVERLAPPING_ISSUE is not among them: it needs the sibling issues, which are
     * not available here. It has its own entry point below, so a caller holding
     * that context can raise it without this method pretending to knowledge it
     * does not have.
     */
    private List<ResolutionWarningVo> warningsFor(List<MessageFacts> candidates,
                                                  Set<String> members,
                                                  List<MessageFacts> nullDated,
                                                  Interval interval,
                                                  Set<String> manualExcludes) {
        List<ResolutionWarningVo> out = new ArrayList<>();

        // The class an exclusions panel is structurally blind to: these messages
        // ARE members, so a curator scanning exclusions never sees them.
        List<String> aliveButWithdrawn = new ArrayList<>();
        for (MessageFacts f : candidates) {
            if (!members.contains(f.uid())) {
                continue;
            }
            boolean withdrawn = f.status() == Status.CANCELLED || f.status() == Status.EXPIRED;
            boolean stillOpenAtCutoff = f.publishDateTo() == null
                    || !f.publishDateTo().before(interval.cutoff());
            if (withdrawn && stillOpenAtCutoff) {
                aliveButWithdrawn.add(f.uid());
            }
        }
        if (!aliveButWithdrawn.isEmpty()) {
            out.add(ResolutionWarningVo.of(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE, aliveButWithdrawn));
        }

        if (!nullDated.isEmpty()) {
            out.add(ResolutionWarningVo.of(ResolutionWarningCode.NULL_PUBLISH_FROM_DROPPED,
                    nullDated.stream().map(MessageFacts::uid).toList()));
        }

        if (members.size() > MEMBER_LIMIT) {
            out.add(ResolutionWarningVo.of(ResolutionWarningCode.LIMIT_EXCEEDED, List.copyOf(members)));
        }

        // An override that no longer refers to anything the criteria would have
        // considered. It still applies -- curation wins -- but it is worth saying.
        Set<String> candidateUidSet = new LinkedHashSet<>();
        for (MessageFacts f : candidates) {
            candidateUidSet.add(f.uid());
        }
        List<String> stale = new ArrayList<>();
        if (manualExcludes != null) {
            for (String uid : manualExcludes) {
                if (!candidateUidSet.contains(uid)) {
                    stale.add(uid);
                }
            }
        }
        if (!stale.isEmpty()) {
            out.add(ResolutionWarningVo.of(ResolutionWarningCode.STALE_OVERRIDE, stale));
        }

        return out;
    }

    /**
     * OVERLAPPING_ISSUE: two issues of one series sharing members.
     *
     * Static, and given both sets, because the caller is the one holding the
     * sibling: the rail compares this resolution against the frozen rows of the
     * issue before it, which is what that issue actually printed.
     */
    public static Optional<ResolutionWarningVo> overlappingIssue(Set<String> these, Set<String> others) {
        List<String> shared = these.stream().filter(others::contains).toList();
        return shared.isEmpty()
                ? Optional.empty()
                : Optional.of(ResolutionWarningVo.of(ResolutionWarningCode.OVERLAPPING_ISSUE, shared));
    }

    /**
     * Messages the criteria would otherwise have matched, but which carry no
     * publishDateFrom. Bounded by the lookback so an open issue does not rescan
     * the whole corpus.
     */
    private List<MessageFacts> messagesWithNoPublishDate(ResolvedCriteria criteria,
                                                         EntityOperands operands,
                                                         Interval interval) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Message> query = cb.createQuery(Message.class);
        Root<Message> message = query.from(Message.class);

        List<Predicate> where = new ArrayList<>();
        where.add(cb.isNull(message.get("publishDateFrom")));

        // The SAME status conjunct the membership predicate applies, and it has to
        // be here or this reports the normal editorial state as a problem. A DRAFT
        // has no publish date yet -- that is what being a draft means -- so without
        // this every draft of the right type inside the lookback is listed as a
        // message the criteria dropped, and the resolution raises a warning that
        // refuses the first publish attempt on a live system every week.
        where.add(message.get("status").in(MembershipPredicate.PUBLIC_STATUSES));

        Date lookbackFrom = new Date(interval.cutoff().getTime()
                - OMISSION_LOOKBACK_DAYS * 24L * 60L * 60L * 1000L);
        where.add(cb.or(
                cb.isNull(message.get("created")),
                cb.greaterThanOrEqualTo(message.<Date>get("created"), lookbackFrom)));

        if (!criteria.messageSeriesIds().isEmpty()) {
            where.add(message.get("messageSeries").get("seriesId").in(criteria.messageSeriesIds()));
        }
        if (!criteria.types().isEmpty()) {
            where.add(message.get("type").in(criteria.types()));
        }
        // The same narrowing as the candidate query. An omission report that
        // ignored the area or chart operands would list messages this series was
        // never going to select, and a curator cannot tell those from the ones it
        // dropped for the reason being reported.
        boolean joined = applyEntityOperands(cb, message, where, criteria, operands);

        query.select(message).where(cb.and(where.toArray(new Predicate[0])));
        if (joined) {
            query.distinct(true);
        }

        return readAll(em.createQuery(query).getResultList(), criteria);
    }

    /** Convenience for callers with no curation. */
    public Resolution resolve(ResolvedCriteria criteria, Interval interval) {
        return resolve(criteria, interval, Set.of(), Set.of());
    }

    /**
     * The ordering facts for a member set, as one projection.
     *
     * Here rather than beside each caller because publish, preview and the live
     * member list must order identically: the list an admin reads on screen and
     * the list the renderer prints are supposed to be the same list, and two
     * copies of the projection is how they stop being.
     *
     * A JPQL projection rather than entity getters, and a LEFT join on purpose.
     * Message carries a year field with no accessor, and "m.area.treeSortOrder"
     * is an IMPLICIT INNER join, which silently drops every message with no
     * primary area -- most of the corpus has none, so the issue would freeze with
     * a member count that does not match its own rows.
     */
    public List<IssueOrdering.Orderable> orderablesFor(Set<String> uids) {
        List<IssueOrdering.Orderable> out = new ArrayList<>();
        if (uids == null || uids.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT m.uid, a.treeSortOrder, m.areaSortOrder, m.year, m.number, m.id, "
                                + "m.publishDateFrom, m.eventDateFrom, m.followUpDate "
                                + "FROM Message m LEFT JOIN m.area a WHERE m.uid IN :uids", Object[].class)
                .setParameter("uids", uids).getResultList();

        for (Object[] r : rows) {
            out.add(new IssueOrdering.Orderable(
                    (String) r[0],
                    (Integer) r[1],
                    r[2] == null ? null : ((Number) r[2]).doubleValue(),
                    (Integer) r[3],
                    (Integer) r[4],
                    (Integer) r[5],
                    (Date) r[6],
                    (Date) r[7],
                    (Date) r[8]));
        }
        return out;
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
        if (criteria.mainTypes().stream().anyMatch(java.util.Objects::isNull)) {
            throw new UnresolvableOperandException(
                    "a null main-type operand reached the query; it would narrow to nothing");
        }
        for (String value : criteria.areaIds()) {
            requireOperand("area", value);
        }
        for (String value : criteria.categoryIds()) {
            requireOperand("category", value);
        }
        for (String value : criteria.chartNumbers()) {
            requireOperand("chart", value);
        }
    }

    private static void requireOperand(String kind, String value) {
        if (value == null || value.isBlank()) {
            throw new UnresolvableOperandException(
                    "a blank " + kind + " operand reached the query; it would narrow to nothing");
        }
    }

    /**
     * The operands that name a row somewhere else, turned into what the query
     * matches on.
     *
     * An area or category criterion is written as an MRN and matched by LINEAGE,
     * so that naming a parent selects everything filed under it -- the same
     * expansion the message search performs, and the reason a criterion has to be
     * resolved to a row before it can become a predicate at all.
     *
     * RI-6, in the form that actually bites. The search resolves each id and
     * filters the misses out of the stream, so an MRN that names nothing shrinks
     * the disjunction, and an operand list where NONE resolve leaves an OR over
     * an empty array -- always false. The issue then resolves to zero members
     * with no error anywhere. Here a miss refuses, loudly, naming the operand.
     */
    private EntityOperands lookUpOperands(ResolvedCriteria criteria) {
        guardOperands(criteria);

        List<String> areaLineages = new ArrayList<>();
        for (String mrn : criteria.areaIds()) {
            Area area = areaService.findByAreaId(mrn);
            if (area == null) {
                throw new UnresolvableOperandException("area operand " + mrn
                        + " resolves to no area; matching on it would select nothing while the issue "
                        + "reports no problem");
            }
            if (area.getLineage() == null || area.getLineage().isBlank()) {
                throw new UnresolvableOperandException("area operand " + mrn
                        + " resolves to an area with no lineage, and the match is by lineage prefix; "
                        + "it would select nothing");
            }
            areaLineages.add(area.getLineage());
        }

        List<String> categoryLineages = new ArrayList<>();
        for (String mrn : criteria.categoryIds()) {
            Category category = categoryService.findByCategoryId(mrn);
            if (category == null) {
                throw new UnresolvableOperandException("category operand " + mrn
                        + " resolves to no category; matching on it would select nothing while the issue "
                        + "reports no problem");
            }
            if (category.getLineage() == null || category.getLineage().isBlank()) {
                throw new UnresolvableOperandException("category operand " + mrn
                        + " resolves to a category with no lineage, and the match is by lineage prefix; "
                        + "it would select nothing");
            }
            categoryLineages.add(category.getLineage());
        }

        // Charts match on the number itself, so nothing has to be expanded -- but a
        // number naming no chart is the same hazard by a shorter route, and it is
        // the one an admin hits by typing a chart that was withdrawn.
        for (String chartNumber : criteria.chartNumbers()) {
            Chart chart = chartService.findByChartNumber(chartNumber);
            if (chart == null) {
                throw new UnresolvableOperandException("chart operand " + chartNumber
                        + " resolves to no chart; matching on it would select nothing while the issue "
                        + "reports no problem");
            }
        }

        return new EntityOperands(areaLineages, categoryLineages);
    }

    /** The lineage prefixes an area or category criterion has been expanded to. */
    private record EntityOperands(List<String> areaLineages, List<String> categoryLineages) {
    }

    /**
     * The operands that need a join, applied identically to both queries.
     *
     * AREAS, NOT AREA. A message carries an ordered list of areas for membership
     * and a single primary "area" that exists so the sort has something to order
     * by; joining the sort field would silently drop every message whose second
     * or third area is the one the criterion names. The publish path's ordering
     * query joins "area" for exactly the opposite reason, and the two must not be
     * confused for one another.
     *
     * @return whether a to-many join was added, which the caller must answer with
     *         DISTINCT: a message in three of the named areas would otherwise come
     *         back three times and be counted three times.
     */
    private boolean applyEntityOperands(CriteriaBuilder cb, Root<Message> message,
                                        List<Predicate> where, ResolvedCriteria criteria,
                                        EntityOperands operands) {
        if (!criteria.mainTypes().isEmpty()) {
            where.add(message.get("mainType").in(criteria.mainTypes()));
        }

        boolean joined = false;

        if (!operands.areaLineages().isEmpty()) {
            Join<Message, Area> areas = message.join("areas", JoinType.LEFT);
            List<Predicate> any = new ArrayList<>();
            for (String lineage : operands.areaLineages()) {
                any.add(cb.like(areas.get("lineage"), lineage + "%"));
            }
            where.add(cb.or(any.toArray(new Predicate[0])));
            joined = true;
        }

        if (!operands.categoryLineages().isEmpty()) {
            Join<Message, Category> categories = message.join("categories", JoinType.LEFT);
            List<Predicate> any = new ArrayList<>();
            for (String lineage : operands.categoryLineages()) {
                any.add(cb.like(categories.get("lineage"), lineage + "%"));
            }
            where.add(cb.or(any.toArray(new Predicate[0])));
            joined = true;
        }

        if (!criteria.chartNumbers().isEmpty()) {
            Join<Message, Chart> charts = message.join("charts", JoinType.LEFT);
            List<Predicate> any = new ArrayList<>();
            for (String chartNumber : criteria.chartNumbers()) {
                any.add(cb.equal(charts.get("chartNumber"), chartNumber));
            }
            where.add(cb.or(any.toArray(new Predicate[0])));
            joined = true;
        }

        return joined;
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
    private List<MessageFacts> narrow(ResolvedCriteria criteria, EntityOperands operands,
                                      Interval interval) {
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
        boolean joined = applyEntityOperands(cb, message, where, criteria, operands);

        // Status is NOT narrowed here. It is a resolver invariant (RI-1) derived
        // from Status.isPublic(), and expressing it as a stored or query-level
        // conjunct is how it drifts -- a PUBLISHED-only conjunct empties every
        // historical issue, since most of the corpus is EXPIRED or CANCELLED.

        query.select(message).where(cb.and(where.toArray(new Predicate[0])));
        if (joined) {
            query.distinct(true);
        }

        // RI-12. No setMaxResults, no paging. The default cap lives in
        // MessageSearchParams.instantiate() and cannot reach a query built here.
        List<Message> rows = em.createQuery(query).getResultList();

        return readAll(rows, criteria);
    }

    /**
     * Reads the facts membership depends on from the message's own row.
     *
     * The collection-backed facets come back null -- "not read", which is not the
     * same as "the message has none". For criteria that select on one of them,
     * read through readAll() or allFactsOf() instead; the predicate refuses facts
     * that are missing something it was asked about, rather than deciding on
     * their absence.
     */
    static MessageFacts factsOf(Message m) {
        return new MessageFacts(
                m.getUid(),
                m.getPublishDateFrom(),
                m.getPublishDateTo(),
                m.getStatus(),
                m.getType(),
                m.getMessageSeries() == null ? null : m.getMessageSeries().getSeriesId(),
                m.getMainType(),
                null, null, null);
    }

    /**
     * Reads a batch of messages as the facts THESE criteria decide on.
     *
     * Only the facets the criteria select on are read, and each is read for the
     * WHOLE batch in one pass rather than per row. Both halves matter: the
     * resolutions in production today select on none of them, so they must keep
     * costing what they cost; and the ones that do select on a facet run over
     * batches of thousands, where a lazy load per row is three thousand queries
     * inside a single transaction -- slow enough to be indistinguishable from a
     * hang, and the reason to do it this way rather than the obvious way.
     *
     * A message absent from a facet map has none of that facet, which is an
     * empty set and NOT a null: null is reserved for "not read", and the
     * predicate raises on it.
     */
    private List<MessageFacts> readAll(List<Message> rows, ResolvedCriteria criteria) {
        List<String> uids = new ArrayList<>(rows.size());
        for (Message m : rows) {
            uids.add(m.getUid());
        }

        Map<String, Set<String>> areas = criteria.readsAreas() ? areaMrnsByUid(uids) : null;
        Map<String, Set<String>> categories = criteria.readsCategories() ? categoryMrnsByUid(uids) : null;
        Map<String, Set<String>> charts = criteria.readsCharts() ? chartNumbersByUid(uids) : null;

        List<MessageFacts> out = new ArrayList<>(rows.size());
        for (Message m : rows) {
            out.add(new MessageFacts(
                    m.getUid(),
                    m.getPublishDateFrom(),
                    m.getPublishDateTo(),
                    m.getStatus(),
                    m.getType(),
                    m.getMessageSeries() == null ? null : m.getMessageSeries().getSeriesId(),
                    m.getMainType(),
                    facet(areas, m.getUid()),
                    facet(categories, m.getUid()),
                    facet(charts, m.getUid())));
        }
        return out;
    }

    /** Not read stays not read; read-and-absent is empty. */
    private static Set<String> facet(Map<String, Set<String>> byUid, String uid) {
        return byUid == null ? null : byUid.getOrDefault(uid, Set.of());
    }

    /**
     * Every attached area's own MRN and its ancestors', keyed by message uid.
     *
     * The ancestors are what makes a criterion naming a parent match a message
     * filed under a child, so the hierarchy is expanded into the FACTS rather
     * than left as a lookup the pure predicate would need a database for. The
     * lineage of one area is walked once however many messages carry it.
     */
    private Map<String, Set<String>> areaMrnsByUid(List<String> uids) {
        Map<Integer, Set<String>> lineage = new HashMap<>();
        return facetByUid(uids, "SELECT m.uid, a FROM Message m JOIN m.areas a WHERE m.uid IN :uids",
                node -> {
                    Area area = (Area) node;
                    return lineage.computeIfAbsent(area.getId(),
                            id -> mrnsOf(area.lineageAsList(), Area::getMrn));
                });
    }

    private Map<String, Set<String>> categoryMrnsByUid(List<String> uids) {
        Map<Integer, Set<String>> lineage = new HashMap<>();
        return facetByUid(uids, "SELECT m.uid, c FROM Message m JOIN m.categories c WHERE m.uid IN :uids",
                node -> {
                    Category category = (Category) node;
                    return lineage.computeIfAbsent(category.getId(),
                            id -> mrnsOf(category.lineageAsList(), Category::getMrn));
                });
    }

    /** Charts have no hierarchy: the number itself is what a criterion names. */
    private Map<String, Set<String>> chartNumbersByUid(List<String> uids) {
        return facetByUid(uids,
                "SELECT m.uid, c.chartNumber FROM Message m JOIN m.charts c "
                        + "WHERE m.uid IN :uids AND c.chartNumber IS NOT NULL",
                node -> Set.of((String) node));
    }

    /** The MRNs of a lineage, skipping nodes that have none. */
    private static <T> Set<String> mrnsOf(List<T> lineage, Function<T, String> mrnOf) {
        Set<String> out = new LinkedHashSet<>();
        for (T node : lineage) {
            String mrn = mrnOf.apply(node);
            if (mrn != null) {
                out.add(mrn);
            }
        }
        return out;
    }

    /**
     * One facet for a batch of messages, chunked.
     *
     * The uid list is the candidate set of an issue in production and the whole
     * corpus when the rule is checked against everything, so it is bounded by
     * neither; an IN-list of ten thousand literals is where MySQL's parser, not
     * the query, becomes the cost.
     *
     * FLUSH MODE COMMIT, and it is not a micro-optimisation. The caller reached
     * here through its own query, which already flushed anything pending; these
     * read association rows nothing in this flow writes. Left on AUTO, every
     * chunk makes Hibernate dirty-check the whole persistence context -- and a
     * resolution over a large candidate set holds thousands of managed entities,
     * so the scan is paid once per chunk and the cost is quadratic in the batch.
     */
    private Map<String, Set<String>> facetByUid(List<String> uids, String jpql,
                                                Function<Object, Set<String>> keysOf) {
        Map<String, Set<String>> out = new HashMap<>();
        for (int from = 0; from < uids.size(); from += FACET_CHUNK) {
            List<String> chunk = uids.subList(from, Math.min(from + FACET_CHUNK, uids.size()));
            for (Object[] row : em.createQuery(jpql, Object[].class)
                    .setFlushMode(FlushModeType.COMMIT)
                    .setParameter("uids", chunk)
                    .getResultList()) {
                out.computeIfAbsent((String) row[0], uid -> new LinkedHashSet<>())
                        .addAll(keysOf.apply(row[1]));
            }
        }
        return out;
    }

    /** How many uids go into one facet lookup. */
    private static final int FACET_CHUNK = 1000;

    /**
     * The facts for ONE message, with every facet read.
     *
     * For the readers that hold a message rather than a criteria document and
     * must be able to answer against ANY series' criteria -- three lazy loads on
     * a single row, against a wrong answer on every series that selects by area,
     * category or chart.
     */
    public static MessageFacts allFactsOf(Message m) {
        Set<String> areas = new LinkedHashSet<>();
        for (Area area : m.getAreas()) {
            areas.addAll(mrnsOf(area.lineageAsList(), Area::getMrn));
        }
        Set<String> categories = new LinkedHashSet<>();
        for (Category category : m.getCategories()) {
            categories.addAll(mrnsOf(category.lineageAsList(), Category::getMrn));
        }
        Set<String> charts = new LinkedHashSet<>();
        for (Chart chart : m.getCharts()) {
            if (chart.getChartNumber() != null) {
                charts.add(chart.getChartNumber());
            }
        }
        return new MessageFacts(
                m.getUid(),
                m.getPublishDateFrom(),
                m.getPublishDateTo(),
                m.getStatus(),
                m.getType(),
                m.getMessageSeries() == null ? null : m.getMessageSeries().getSeriesId(),
                m.getMainType(),
                areas, categories, charts);
    }

    /**
     * Messages read as the facts THESE criteria decide on.
     *
     * The differential test needs it: running the rule over the whole corpus is
     * only a check of the SQL narrowing if both sides see the same facts, and a
     * corpus read without the facets the criteria select on would make the pure
     * side reject everything and the comparison pass for the wrong reason.
     */
    public List<MessageFacts> factsFor(Collection<Message> messages, ResolvedCriteria criteria) {
        return readAll(new ArrayList<>(messages), criteria);
    }

    /**
     * The candidate set alone, for the differential test. Exposed because the
     * whole point of the design is that this is a superset of the answer, and a
     * claim like that is worth being able to check directly.
     */
    public List<MessageFacts> candidatesFor(ResolvedCriteria criteria, Interval interval) {
        return narrow(criteria, lookUpOperands(criteria), interval);
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
