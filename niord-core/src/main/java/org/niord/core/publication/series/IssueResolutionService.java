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

import org.niord.core.publication.series.criteria.DomainSeriesExpander;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.ResolvedCriteria;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * ONE resolve of one issue, and everything every reader of it needs.
 *
 * The issue screen asks three questions -- what is in this issue, what did the
 * criteria drop, may it be published -- and each of them used to take its own
 * resolve. They were the same call: the same effective document, the same
 * interval, the same includes and excludes. On the P&T series a resolve costs
 * about a second cold, because an in-force document's candidate set is the
 * series' whole published history, so opening one issue paid for three.
 *
 * Taking it once is not only cheaper, it is the only way the three answers can
 * agree. Resolved separately, the rail could say 214 members beside a list
 * showing 213 and an omissions panel answering for a third instant, with nothing
 * to say which of them was right -- and the difference would be a few
 * milliseconds of message traffic, which is exactly the kind of disagreement
 * nobody can reproduce.
 *
 * "at" is a parameter and never a clock read inside, for the same reason it is a
 * parameter on the issue list: the release dialog offers to publish at an
 * instant the admin chose, and a rail computed for NOW describes a release
 * nobody is about to make.
 */
@ApplicationScoped
public class IssueResolutionService {

    @Inject
    EntityManager em;

    @Inject
    MemberResolutionService resolver;

    @Inject
    DomainSeriesExpander domains;

    /**
     * What one resolve produced, for every reader of it.
     *
     * The overrides travel as ENTITIES rather than as two uid sets, because the
     * member list and the standing-decisions list both render the author and the
     * reason off them -- and reading those back per row is a query per row on a
     * list that regularly runs past two hundred.
     *
     * `membership` is not "did a resolution come back". A resolve that was
     * ATTEMPTED and failed is a finding worth warning about; a series whose
     * content is a file somebody uploaded raises no membership question at all,
     * and answering "0 members" for it is a warning nobody can clear.
     *
     * @param from       the lower bound membership was decided over -- the issue's
     *                   own period start, or the what-if start a caller named in
     *                   its place. It is carried rather than re-read because the
     *                   two differ exactly when somebody is asking what a period
     *                   they have not saved yet would contain, and a screen that
     *                   re-read the stored start would then label the answer with
     *                   the wrong period. Null where the issue has no lower bound
     *                   at all, which every in-force issue is
     * @param at         the instant membership was decided at
     * @param frozen     PUBLISHED or RETIRED: the rows are a record, and no live
     *                   resolve was taken
     * @param overrides  every standing curation decision by message uid, author
     *                   join-fetched, in the order they were taken
     * @param resolution null where no resolve could be taken at all
     * @param membership whether this issue HAS a member list to be asked about
     */
    public record IssueResolution(
            Date from,
            Date at,
            boolean frozen,
            Map<String, IssueOverride> overrides,
            Set<String> includes,
            Set<String> excludes,
            MemberResolutionService.Resolution resolution,
            boolean membership) {
    }

    /**
     * Every curation decision on this issue, in one query, indexed by uid.
     *
     * SPLIT FROM {@link #forIssue}, and the split is the whole point. The
     * standing-decisions list answers off these rows and nothing else, so folding
     * it into the shared resolution would have made a screen that lists the
     * decisions -- and no members -- pay for a full candidate narrowing it never
     * reads. That is the opposite of what sharing one resolve is for.
     *
     * THE ORDER IS PART OF THE CONTRACT: the standing-decisions list is rendered
     * from this map, and it has always come from a query that ordered by id.
     * Sharing one read without saying so would have changed the wire order of
     * GET /overrides as a side effect of a performance change.
     *
     * The author is join-fetched because both lists render a why-line off it, and
     * a lazy association there is one select per decision.
     */
    @Transactional
    public Map<String, IssueOverride> overridesOf(PublicationIssue issue) {
        Map<String, IssueOverride> overrides = new LinkedHashMap<>();
        for (IssueOverride o : em.createQuery(
                        "SELECT o FROM IssueOverride o LEFT JOIN FETCH o.author "
                                + "WHERE o.issue = :i ORDER BY o.id",
                        IssueOverride.class)
                .setParameter("i", issue)
                .getResultList()) {
            overrides.put(o.getMessageUid(), o);
        }
        return overrides;
    }

    /**
     * Whether this issue's contents are a record rather than a live question.
     *
     * The one definition of it, because more than one reader has to ask before it
     * has a resolution in hand: a screen deciding whether an instant the caller
     * named applies at all has to know this BEFORE it resolves anything, and a
     * second copy of the status test is a second answer that can drift from this
     * one the day a status is added.
     */
    public static boolean isFrozen(PublicationIssue issue) {
        return issue.getStatus() == IssueStatus.PUBLISHED
                || issue.getStatus() == IssueStatus.RETIRED;
    }

    /**
     * The issue's membership as of one instant, resolved once.
     *
     * A frozen issue takes NO resolve. Its member rows are what was printed and
     * the archived document is the proof, so resolving it live would produce an
     * authoritative-looking answer about a document nobody published -- and it
     * would pay the full cost of doing so on every published issue anybody opens.
     */
    @Transactional
    public IssueResolution forIssue(PublicationIssue issue, Date at) {
        return forIssue(issue, at, null);
    }

    /**
     * The same resolve, over a period start the caller named instead.
     *
     * A WHAT-IF, and nothing about it is written down. An admin editing an open
     * issue's period is asking what that period would contain before saving it,
     * and the only honest way to answer is to resolve over the period being
     * typed rather than over the one on disk -- otherwise the count beside the
     * form answers for the saved period while the form shows another, and the
     * two disagree until somebody presses save.
     *
     * The named start is free to fall EARLIER or LATER than the stored one: both
     * are legitimate questions about a period that does not exist yet. What it
     * cannot do is reach past the instant being resolved at, and the caller that
     * accepts it from the wire is where that is refused -- an interval whose
     * lower bound does not precede its upper describes no window at all.
     *
     * IGNORED ON A FROZEN ISSUE, like the instant: those rows are what was
     * printed, no resolve is taken, and the interval echoed back is the stored
     * one so a reader is never told a published issue covered a period it did
     * not.
     *
     * @param from the period start to resolve over, or null for the issue's own
     */
    @Transactional
    public IssueResolution forIssue(PublicationIssue issue, Date at, Date from) {
        Map<String, IssueOverride> overrides = overridesOf(issue);

        Set<String> includes = new LinkedHashSet<>();
        Set<String> excludes = new LinkedHashSet<>();
        for (IssueOverride o : overrides.values()) {
            (o.getKind() == OverrideKind.INCLUDE ? includes : excludes).add(o.getMessageUid());
        }

        PublicationSeries series = issue.getSeries();
        boolean queryBacked = series != null
                && series.getContentMode() == ContentMode.GENERATED_FROM_QUERY
                && series.getTimeRelation() != null;
        // Whether this issue raises a membership question at all -- which is
        // exactly the condition under which a resolve is attempted below.
        boolean membership = queryBacked || !includes.isEmpty();

        boolean frozen = isFrozen(issue);
        // The lower bound this resolve is actually taken over, decided once and
        // then carried on the result: every reader of it -- the member list, the
        // rail, the screen that names the period it answered for -- has to agree
        // on which period was asked about, and a second reader defaulting the
        // null for itself is how they stop agreeing.
        Date start = frozen || from == null ? issue.getIntervalFrom() : from;
        if (frozen) {
            return new IssueResolution(start, at, true, overrides, includes, excludes, null,
                    membership);
        }

        return new IssueResolution(start, at, false, overrides, includes, excludes,
                resolutionOf(issue, start, at, includes, excludes), membership);
    }

    /**
     * The resolution alone, for a caller that already holds the decisions.
     *
     * Split out so the live COUNT -- which the dashboard asks for once per open
     * issue and serves from a short-lived cache -- can share this one definition
     * of the rule without paying for the author join-fetch it never renders.
     *
     * A null criteria document means NO QUERY, which is a different thing from an
     * empty one: resolving an empty document would either raise or match the
     * whole corpus. The curated branch is what the annexes need -- a series with
     * no criteria still has contents when somebody named them by hand.
     *
     * @return null where the issue has no membership semantics at all, or where
     *         its document cannot resolve
     */
    public MemberResolutionService.Resolution resolutionOf(PublicationIssue issue, Date at,
                                                           Set<String> includes, Set<String> excludes) {
        return resolutionOf(issue, issue.getIntervalFrom(), at, includes, excludes);
    }

    /**
     * The same rule over a lower bound the caller decided.
     *
     * The bound arrives already decided rather than as "null means the issue's
     * own": the issue HAS a null start -- every in-force issue does -- so a null
     * here that meant "default it" would be indistinguishable from the real
     * thing, and the one caller that names a bound would be defaulting it twice.
     *
     * @param from the interval's lower bound; null is a genuine absence of one
     */
    public MemberResolutionService.Resolution resolutionOf(PublicationIssue issue, Date from, Date at,
                                                           Set<String> includes, Set<String> excludes) {
        PublicationSeries series = issue.getSeries();
        boolean queryBacked = series != null
                && series.getContentMode() == ContentMode.GENERATED_FROM_QUERY
                && series.getTimeRelation() != null;
        if (queryBacked) {
            try {
                ResolvedCriteria criteria = EffectiveCriteria.resolvedFor(issue, domains);
                if (criteria != null) {
                    return resolver.resolve(criteria, new Interval(from, at), includes, excludes);
                }
            } catch (RuntimeException e) {
                // A document that cannot resolve is a series-configuration problem
                // and is reported as such by the criteria editor and the release
                // rail. It is not a reason for the screen around it to fail.
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
}
