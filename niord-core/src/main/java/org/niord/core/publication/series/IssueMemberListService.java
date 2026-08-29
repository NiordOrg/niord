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
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.IssueOrdering;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.series.vo.LiveMessageStateVo;
import org.niord.core.publication.series.vo.IssueOverrideVo;
import org.niord.core.publication.series.vo.MemberCurationVo;
import org.niord.core.user.User;
import org.niord.model.message.Status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The member list of one issue, with what has moved under it since.
 *
 * A published issue's member rows are frozen and stay frozen: they record what
 * was printed, and the archived document is the proof. But the messages
 * themselves go on living -- a notice is cancelled, a type is corrected, an
 * open-ended validity gets an end date -- and none of that is visible from the
 * snapshot, which is exactly why an admin looking at a three-week-old issue
 * cannot tell whether it still describes the world.
 *
 * So the divergence is COMPUTED and SURFACED, and the snapshot is not touched.
 * Healing it would be the one unrecoverable move here: the row would then agree
 * with today and disagree with the PDF that went out, and nothing would record
 * that they ever differed.
 *
 * It is a service in core rather than logic on the endpoint because the rules --
 * which fields count as drift, whether the list is live or frozen, what a
 * curated row reveals -- are the interesting part and the web layer has no
 * container tests to pin them with.
 */
@ApplicationScoped
public class IssueMemberListService {

    /**
     * How many uids go into one live-facts lookup.
     *
     * The whole point of reading the live side in one query is that a member list
     * runs to hundreds of rows and a per-row lookup would be hundreds of
     * round-trips. The chunk exists only because an IN-list has a practical
     * ceiling, and it is the same bound the resolver already uses.
     */
    private static final int LOOKUP_CHUNK = 1000;

    @Inject
    EntityManager em;

    @Inject
    IssueCurationService curation;

    @Inject
    MemberResolutionService resolver;

    @Inject
    org.niord.core.publication.series.criteria.DomainSeriesExpander domains;

    /**
     * Every curation decision that STANDS on this issue, include and exclude alike.
     *
     * The exclusions are the reason this exists. An excluded message is not a
     * member -- that is what excluding it means -- so it has no row in the member
     * list for a "withdraw this decision" affordance to hang off, and the only
     * other record of it is the audit trail. The trail records what HAPPENED,
     * not what stands: an exclude followed by a clear leaves two entries and no
     * decision, and reading the trail as a state is how a withdrawn exclusion
     * comes back to life on a screen.
     *
     * CURATOR TIER, like the writes it describes. It carries the author and the
     * reason, which is the admin-only half of a why-line.
     */
    @Transactional
    public List<IssueOverrideVo> standingDecisions(PublicationIssue issue) {
        List<IssueOverrideVo> out = new ArrayList<>();
        for (IssueOverride override : curation.forIssue(issue)) {
            IssueOverrideVo vo = new IssueOverrideVo();
            fillCuration(vo, override);
            vo.setMessageUid(override.getMessageUid());
            out.add(vo);
        }
        return out;
    }

    /**
     * The rows, in print order.
     *
     * Live-versus-frozen is keyed off the issue's STATUS and nothing else. A
     * request parameter would let a caller ask a published issue what it would
     * contain today, and that answer looks authoritative while describing a
     * document nobody published.
     *
     * AN OPEN ISSUE HAS NO ROWS, and that is the point of the split. Member rows
     * are written by the freeze, so before an issue is published there are none --
     * and answering an empty list is the worst of the available answers, because
     * "nothing is in this issue" and "this issue has not been frozen yet" look
     * identical on the wire and only one of them is true. So an open issue is
     * resolved live, exactly as the publish would resolve it: same document, same
     * interval, same overrides, same order.
     */
    @Transactional
    public List<IssueMemberVo> members(PublicationIssue issue) {
        boolean frozenList = issue.getStatus() == IssueStatus.PUBLISHED
                || issue.getStatus() == IssueStatus.RETIRED;

        // Every curation decision on this issue, in one query and indexed by uid.
        // The member's own foreign key answers for rows frozen after the override
        // was taken; an imported row carries the decision without the link, and a
        // live list has no member rows to hang a key on at all -- so the uid is
        // the fallback, and it is the key every one of them shares.
        Map<String, IssueOverride> overrides = new LinkedHashMap<>();
        for (IssueOverride o : em.createQuery(
                        "SELECT o FROM IssueOverride o LEFT JOIN FETCH o.author WHERE o.issue = :i",
                        IssueOverride.class)
                .setParameter("i", issue)
                .getResultList()) {
            overrides.put(o.getMessageUid(), o);
        }

        if (!frozenList) {
            return liveMembers(issue, overrides);
        }

        List<IssueMember> frozen = em.createQuery(
                        "SELECT m FROM IssueMember m WHERE m.issue = :i ORDER BY m.sortIndex",
                        IssueMember.class)
                .setParameter("i", issue)
                .getResultList();

        Map<String, LiveFacts> live = liveFacts(frozen);

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

            IssueOverride override = m.getOverride() != null
                    ? m.getOverride() : overrides.get(m.getMessageUid());
            vo.setCuration(curationOf(override));

            applyDrift(vo, m, live.get(m.getMessageUid()));
            out.add(vo);
        }
        return out;
    }

    /**
     * What an OPEN issue would contain if it were published now.
     *
     * A PROBE, not a record. Nothing is written: no member row, no snapshot, no
     * override. The interval closes at NOW rather than at any stamped instant,
     * because an open issue has no cut-off yet -- that is what publishing it
     * decides -- and the set therefore moves as messages are published into it,
     * which is exactly what an admin watching the week fill up needs to see.
     *
     * There is no drift half. Drift is the distance between a frozen fact and the
     * live one, and here there is only the live one.
     */
    private List<IssueMemberVo> liveMembers(PublicationIssue issue, Map<String, IssueOverride> overrides) {
        Set<String> includes = new LinkedHashSet<>();
        Set<String> excludes = new LinkedHashSet<>();
        for (IssueOverride o : overrides.values()) {
            (o.getKind() == OverrideKind.INCLUDE ? includes : excludes).add(o.getMessageUid());
        }

        MemberResolutionService.Resolution resolution = resolve(issue, includes, excludes);
        if (resolution == null) {
            return List.of();
        }

        PublicationSeries series = issue.getSeries();
        IssueOrdering.SortSpec sort = IssueOrdering.resolveSort(
                series == null ? null : series.getMessageSortBy(),
                series == null || series.getMessageSortOrder() == null ? null
                        : IssueOrdering.Direction.valueOf(series.getMessageSortOrder().name()),
                series == null || series.getDomain() == null
                        ? null : series.getDomain().getMessageSortOrder());
        List<IssueOrdering.Orderable> ordered =
                IssueOrdering.order(resolver.orderablesFor(resolution.members()), sort);
        Map<String, Integer> sortIndex = IssueOrdering.assignSortIndex(ordered);

        // The live facts for the rows, in one query, the same way the frozen half
        // reads them. The columns are named "frozen" because that is what they
        // carry on a published issue; on a live list they carry today's values,
        // which is what the row is FOR.
        Map<String, LiveFacts> facts = liveFactsOf(sortIndex.keySet());

        List<IssueMemberVo> out = new ArrayList<>();
        for (IssueOrdering.Orderable o : ordered) {
            String uid = o.uid();
            IssueOverride override = overrides.get(uid);
            LiveFacts fact = facts.get(uid);

            IssueMemberVo vo = new IssueMemberVo();
            vo.setMessageUid(uid);
            vo.setSortIndex(sortIndex.get(uid));
            vo.setFrozenShortId(fact == null ? null : fact.shortId());
            vo.setFrozenType(fact == null ? null : fact.type());
            vo.setFrozenStatus(fact == null ? null : fact.status());
            vo.setFrozenPublishDateFrom(fact == null ? null : fact.publishDateFrom());
            vo.setFrozenPublishDateTo(fact == null ? null : fact.publishDateTo());

            // A row is here because the query selected it, or because somebody put
            // it here. The include overrides are the only ones that can produce a
            // member; an exclude removes one, so it never has a row to be the
            // reason for.
            boolean manual = override != null && override.getKind() == OverrideKind.INCLUDE;
            vo.setSource(manual ? MemberSource.OVERRIDE_INCLUDE.name() : MemberSource.CRITERIA.name());
            vo.setReasonCode(manual ? "MANUAL_INCLUDE"
                    : series != null && series.getTimeRelation() == TimeRelation.IN_FORCE_AT_CUTOFF
                            ? "IN_FORCE_AT_CUTOFF" : "IN_INTERVAL");
            vo.setCuration(curationOf(override));
            out.add(vo);
        }
        return out;
    }

    /**
     * The live resolution for an open issue, or null where the issue has no
     * membership semantics at all.
     *
     * A null criteria document means NO QUERY, which is a different thing from an
     * empty one -- resolving it would either raise or match the whole corpus. The
     * curated branch is what the annexes need: a series with no criteria still has
     * contents when somebody named them by hand.
     */
    private MemberResolutionService.Resolution resolve(PublicationIssue issue,
                                                       Set<String> includes, Set<String> excludes) {
        PublicationSeries series = issue.getSeries();
        boolean queryBacked = series != null
                && series.getContentMode() == ContentMode.GENERATED_FROM_QUERY
                && series.getTimeRelation() != null;
        if (queryBacked) {
            try {
                ResolvedCriteria criteria = EffectiveCriteria.resolvedFor(issue, domains);
                if (criteria != null) {
                    return resolver.resolve(criteria,
                            new Interval(issue.getIntervalFrom(), new Date()), includes, excludes);
                }
            } catch (RuntimeException e) {
                // A document that cannot resolve is a series-configuration problem
                // and is reported as such by the criteria editor and the release
                // rail. It is not a reason for this list to fail.
                return null;
            }
        }
        if (includes.isEmpty()) {
            return null;
        }
        Set<String> curated = new LinkedHashSet<>(includes);
        curated.removeAll(excludes);
        return MemberResolutionService.Resolution.curated(curated);
    }

    /**
     * The reason code for one member.
     *
     * Public and static so it can be checked without a database. It is the one
     * value on the row that is derived rather than read, and a derivation nobody
     * can test directly is a derivation that drifts.
     */
    public static String deriveReason(IssueMember m, PublicationIssue issue) {
        if (m.getSource() == MemberSource.OVERRIDE_INCLUDE) {
            return "MANUAL_INCLUDE";
        }
        if (m.getSource() == MemberSource.IMPORTED) {
            return "IMPORTED";
        }
        return TimeRelation.IN_FORCE_AT_CUTOFF == issue.getSnapshotTimeRelation()
                ? "IN_FORCE_AT_CUTOFF" : "IN_INTERVAL";
    }

    // ------------------------------------------------------------------ drift

    /** The live values membership and the printed row depend on. */
    private record LiveFacts(String shortId, String type, String status,
                             Date publishDateFrom, Date publishDateTo) {
    }

    /**
     * Every member's live row, in ONE query.
     *
     * Reading the message entity per member would be a query per row on a list
     * that regularly runs past two hundred, on a screen that is opened to answer
     * a single question. The projection is deliberate too: three scalars is all
     * the comparison needs, and selecting whole Message entities would drag their
     * parts, areas and geometry along for nothing.
     */
    private Map<String, LiveFacts> liveFacts(List<IssueMember> members) {
        List<String> uids = new ArrayList<>();
        for (IssueMember m : members) {
            if (m.getMessageUid() != null) {
                uids.add(m.getMessageUid());
            }
        }
        return liveFactsOf(uids);
    }

    /** The same projection, for a set of uids that has no member rows yet. */
    private Map<String, LiveFacts> liveFactsOf(Collection<String> uids) {
        List<String> all = new ArrayList<>(uids);
        Map<String, LiveFacts> out = new LinkedHashMap<>();
        for (int from = 0; from < all.size(); from += LOOKUP_CHUNK) {
            List<String> chunk = all.subList(from, Math.min(from + LOOKUP_CHUNK, all.size()));
            for (Object[] row : em.createQuery(
                            "SELECT m.uid, m.shortId, m.type, m.status, m.publishDateFrom, m.publishDateTo "
                                    + "FROM Message m WHERE m.uid IN (:uids)", Object[].class)
                    .setParameter("uids", chunk)
                    .getResultList()) {
                out.put((String) row[0],
                        new LiveFacts((String) row[1],
                                row[2] == null ? null : row[2].toString(),
                                row[3] == null ? null : row[3].toString(),
                                (Date) row[4],
                                (Date) row[5]));
            }
        }
        return out;
    }

    /**
     * What has moved, and what it moved to.
     *
     * The three compared fields are the ones a frozen row stores BECAUSE they are
     * mutable in production: type is editor-writable and unversioned, status
     * changes on every withdrawal, and publishDateTo is null while a notice is
     * still open and gets a value the moment it closes -- which is the single
     * most common drift on the estate and the one a null-unsafe comparison would
     * miss entirely.
     *
     * A message that is GONE drifts too, and says so under its own name rather
     * than reporting three nulls that read like empty values.
     */
    private static void applyDrift(IssueMemberVo vo, IssueMember member, LiveFacts live) {
        List<String> drift = new ArrayList<>();
        LiveMessageStateVo current = new LiveMessageStateVo();

        if (live == null) {
            drift.add("exists");
            current.setExists(false);
            current.setPubliclyVisible(false);
        } else {
            current.setExists(true);
            current.setType(live.type());
            current.setStatus(live.status());
            current.setPubliclyVisible(isPublic(live.status()));

            if (!Objects.equals(member.getFrozenType(), live.type())) {
                drift.add("type");
            }
            if (!Objects.equals(member.getFrozenStatus(), live.status())) {
                drift.add("status");
            }
            // Compared as instants rather than as objects: two Date instances of
            // the same millisecond are not equal under Timestamp's own equals,
            // and the values come back from JDBC as Timestamps.
            if (!sameInstant(member.getFrozenPublishDateTo(), live.publishDateTo())) {
                drift.add("publishDateTo");
            }
        }

        if (drift.isEmpty()) {
            return;
        }
        vo.setDrift(drift);
        vo.setCurrent(current);
    }

    private static boolean sameInstant(Date a, Date b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.getTime() == b.getTime();
    }

    /**
     * Whether the live status is one the public may read.
     *
     * Derived from the enum rather than listed. A literal list drifts the first
     * time a status is added, and the answer here decides whether a member of a
     * published issue is still readable at all.
     */
    private static boolean isPublic(String status) {
        if (status == null) {
            return false;
        }
        try {
            return Status.valueOf(status).isPublic();
        } catch (IllegalArgumentException e) {
            // A status this build does not know is not one it can call public.
            return false;
        }
    }

    // ------------------------------------------------------------------ curation

    /**
     * The curation facts a why-line renders, or null where nobody curated.
     *
     * Null rather than an empty object: "no human touched this row" and "a human
     * touched it and left no reason" are different facts, and the reason column
     * is NOT NULL precisely so the second cannot happen.
     */
    static MemberCurationVo curationOf(IssueOverride override) {
        if (override == null) {
            return null;
        }
        MemberCurationVo vo = new MemberCurationVo();
        fillCuration(vo, override);
        return vo;
    }

    /**
     * The curation facts onto whichever shape is carrying them.
     *
     * One filler for the member row's block and the standing-decisions list, so
     * an EXCLUDE's reason and an INCLUDE's why-line cannot come to disagree about
     * what a decision says.
     */
    static void fillCuration(MemberCurationVo vo, IssueOverride override) {
        vo.setKind(override.getKind() == null ? null : override.getKind().name());
        vo.setAuthor(authorOf(override.getAuthor()));
        vo.setAt(override.getCreated());
        vo.setReason(override.getReason());
    }

    /** A curator by the name a colleague would recognise, falling back to the login. */
    private static String authorOf(User author) {
        if (author == null) {
            return null;
        }
        String name = author.getName();
        return name == null || name.isBlank() ? author.getUsername() : name;
    }
}
