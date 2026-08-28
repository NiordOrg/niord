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

import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.resolve.ResolvedCriteria;

import java.util.Objects;

/**
 * Which criteria document actually decides an issue's membership.
 *
 * `criteriaOverride ?? series.criteria` for a live issue, and the frozen
 * `criteriaSnapshot` for one that has been published. One function, because the
 * alternative is the same expression written out at every site that resolves --
 * and there are six of them, each with its own answer about whether an override
 * applies. Six copies of a rule is six chances for one of them to be a different
 * rule.
 *
 * WHAT AN OVERRIDE OVERRIDES: the document, and nothing else. `timeRelation` and
 * `aliveAtCutoff` stay the series'. They describe how the series RELATES its
 * issues to time -- whether they tile or overlap -- and an issue whose relation
 * differed from its siblings would not be an issue of that series in any
 * meaningful sense. The override says "select something different for this
 * edition", not "be a different kind of publication".
 *
 * NOT EVERY READER SHOULD USE THIS. ReplayHarness deliberately does not: it
 * reproduces what the legacy engine produced, and an override is a decision
 * somebody made afterwards, in this system. Applying it retroactively would make
 * the replay diff against a document legacy never had.
 */
public final class EffectiveCriteria {

    private EffectiveCriteria() {
    }

    /**
     * The document that decides this issue, or null when nothing does.
     *
     * A published issue answers from its snapshot. That is not a nicety: the
     * series' criteria are editable and the override is not frozen anywhere else,
     * so asking a published issue what it selected has exactly one truthful
     * source, and it is the copy taken at the moment it was published.
     */
    public static IssueCriteriaVo documentOf(PublicationIssue issue) {
        if (issue == null) {
            return null;
        }
        if (issue.getStatus() != IssueStatus.OPEN && issue.getCriteriaSnapshot() != null) {
            return issue.getCriteriaSnapshot();
        }
        if (issue.getCriteriaOverride() != null) {
            return issue.getCriteriaOverride();
        }
        return issue.getSeries() == null ? null : issue.getSeries().getCriteria();
    }

    /**
     * Whether this issue is selecting something other than what its series says.
     *
     * The panel labels itself off this -- "fra serien" against "tilpasset for
     * denne udgave" -- so it has to be answerable without diffing two documents
     * on the client.
     *
     * A published issue is compared rather than flagged, because the override that
     * produced it may since have been edited or removed, and what matters is
     * whether the DOCUMENT IT WENT OUT WITH differed from the series'. That
     * comparison is by value: a snapshot equal to the series' criteria is not an
     * override, however it came to be taken.
     */
    public static boolean isOverridden(PublicationIssue issue) {
        if (issue == null || issue.getSeries() == null) {
            return false;
        }
        if (issue.getStatus() == IssueStatus.OPEN) {
            return issue.getCriteriaOverride() != null;
        }
        IssueCriteriaVo snapshot = issue.getCriteriaSnapshot();
        return snapshot != null && !Objects.equals(snapshot, issue.getSeries().getCriteria());
    }

    /**
     * Whether somebody DELIBERATELY tailored this issue.
     *
     * Not the same question as isOverridden, and the difference is not academic.
     * isOverridden asks "does this select something other than the series says",
     * which for a published issue is a comparison against a snapshot -- and an
     * IMPORTED issue's snapshot differs from its series routinely, because the
     * importer records what each release actually selected and a series that
     * spans two legacy filter eras carries one setting while 122 of its issues
     * need the other. Nobody tailored those. They are history, recorded
     * faithfully.
     *
     * So a reader asking "should I treat this issue as a human decision" must ask
     * THIS, which is answered by a column only the edit path ever writes. Asking
     * isOverridden instead made the shadow diff skip the entire imported estate,
     * and it was the replay test that noticed: nothing was compared at all.
     */
    public static boolean hasOwnCriteria(PublicationIssue issue) {
        return issue != null && issue.getCriteriaOverride() != null;
    }

    /**
     * The effective document, resolved and ready for the member resolver.
     *
     * Null when the issue has no membership at all -- roughly 48 legacy
     * publications do not, and a null document means NO QUERY rather than an
     * empty one. Returning null keeps those two apart at every call site, where
     * resolving an empty document would either raise or match the whole corpus.
     */
    public static ResolvedCriteria resolvedFor(PublicationIssue issue) {
        return resolvedFor(issue, CriteriaResolver.NO_DOMAINS);
    }

    /**
     * The effective document, resolved with a domain macro that can expand.
     *
     * The overload exists because this class is a pure function and the expansion
     * needs the database. Every caller that HAS one passes it; the pair above is
     * for callers with no persistence context, and a document carrying a domain
     * node refuses there rather than resolving to a narrower set than it names.
     */
    public static ResolvedCriteria resolvedFor(PublicationIssue issue,
                                               CriteriaResolver.DomainExpander expander) {
        IssueCriteriaVo document = documentOf(issue);
        PublicationSeries series = issue == null ? null : issue.getSeries();
        if (document == null || series == null || series.getTimeRelation() == null) {
            return null;
        }
        return CriteriaResolver.resolve(
                document,
                series.getTimeRelation(),
                Boolean.TRUE.equals(series.getAliveAtCutoff()),
                expander == null ? CriteriaResolver.NO_DOMAINS : expander);
    }
}
