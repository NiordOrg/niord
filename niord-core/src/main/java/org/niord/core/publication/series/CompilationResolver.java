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
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.MemberDecision;
import org.niord.core.publication.series.resolve.MembershipReason;
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.ResolutionWarningVo;
import org.niord.core.service.BaseService;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RI-16. The members of a compilation: the union of what its source series
 * already published, week by week.
 *
 * This is the second derivation in the system, and it deliberately shares
 * nothing with the first. {@link MemberResolutionService} decides membership by
 * running a predicate over message facts; this one runs no predicate at all. Its
 * answer is a fact about OTHER ISSUES -- which messages they froze -- so it
 * reads IssueMember rows and never Message rows, and a compilation therefore
 * never reaches the candidate narrowing, the pure predicate or the differential
 * test that guards them.
 *
 * That is the point of the regime rather than an implementation detail. An
 * accumulated annual is supposed to reproduce what the year's weeklies actually
 * printed -- the curation of those weeklies included, and judged against each
 * week's own cut-off. Re-running the weekly criteria over the year would produce
 * a different document: one where a message cancelled in March is missing from
 * the year that published it in February.
 *
 * FOUR RULES, and each of them is a decision:
 *
 * PUBLISHED SOURCES ONLY. A retired weekly is the statement that what went out
 * for its period should not stand, so its rows are not something to compile; its
 * replacement, once published, is. An OPEN weekly has no frozen rows to read at
 * all -- they are written by the freeze -- so it contributes nothing and is
 * reported instead, which is what SOURCE_ISSUES_INCOMPLETE exists for.
 *
 * A SOURCE BELONGS TO THE PERIOD ITS CUT-OFF FALLS IN, and its own week and year
 * numbering is never consulted. The two boundary notices at a new year are in
 * week 1 of both years by that numbering, and the cut-off is the only thing that
 * says which annual they went out under.
 *
 * A REPEATED UID IS OWNED BY THE EARLIEST SOURCE. Fourteen memberships in the
 * live estate appear in more than one weekly, and the member table's unique key
 * on (issue, messageUid) forbids keeping both rows anyway -- so the only question
 * is which week the row is printed under, and the answer is the week that
 * printed it first.
 *
 * CURATION APPLIES ON TOP, by RI-10, exactly as it does to a query: an exclude
 * removes a row the sources carried, an include adds one no source did.
 */
@ApplicationScoped
public class CompilationResolver extends BaseService {

    /**
     * Which source issue owns one compiled uid.
     *
     * The cut-off and the sortIndex travel with the publicId because the ORDER
     * is derived from them (RI-17) and the ordering runs after the rows have been
     * separated from the query that produced them. Reading them back per row
     * would be a lookup per member on a list that runs past a thousand.
     */
    public record SourceRef(String publicId, Date cutoff, int sortIndex) {
    }

    /**
     * One source-series issue as the survey saw it.
     *
     * @param status the issue's status name, or {@link #MISSING} for a stretch of
     *               the period that no issue covers -- a synthesised row, with no
     *               publicId and no member count, whose interval is the gap
     */
    public record SourceIssue(
            String publicId,
            Integer week,
            Integer weekTo,
            Integer year,
            Date cutoff,
            String status,
            Integer memberCount,
            Date intervalFrom) {
    }

    /** The status a synthesised row carries: a stretch of the period nothing covers. */
    public static final String MISSING = "MISSING";

    /**
     * What the source series looked like over one window.
     *
     * ONE PRODUCER for the three readers that must agree -- the sources panel,
     * the SOURCE_ISSUES_COMPLETE row and the warning the publish gate enforces.
     * Two of them computing "is the year covered" separately is two answers, and
     * the one an admin is looking at would not be the one the release enforced.
     *
     * @param sources every PUBLISHED and OPEN source issue in the window plus the
     *                synthesised MISSING stretches, in cut-off order
     * @param open    the OPEN ones alone
     * @param missing the synthesised stretches alone
     */
    public record Survey(List<SourceIssue> sources, List<SourceIssue> open, List<SourceIssue> missing) {

        /** Whether every period inside the window is covered by a published issue. */
        public boolean complete() {
            return open.isEmpty() && missing.isEmpty();
        }

        /** The PUBLISHED sources, in order: what the snapshot header records. */
        public List<SourceIssue> published() {
            return sources.stream()
                    .filter(s -> IssueStatus.PUBLISHED.name().equals(s.status()))
                    .toList();
        }
    }

    // ===================================================================

    /**
     * The compiled membership of one window, curated.
     *
     * @param source   the series being compiled
     * @param window   the compilation issue's period; a null lower bound means no
     *                 lower bound at all, which is what the first issue of a
     *                 compilation has
     * @param includes uids a curator added, or null
     * @param excludes uids a curator removed, or null
     */
    public MemberResolutionService.Resolution resolve(PublicationSeries source, Interval window,
                                                      Set<String> includes, Set<String> excludes) {
        if (source == null || window == null) {
            throw new IllegalArgumentException("resolve() takes no nulls");
        }
        Set<String> in = includes == null ? Set.of() : includes;
        Set<String> out = excludes == null ? Set.of() : excludes;

        List<PublicationIssue> sources = publishedSources(source, window);

        // ONE QUERY for the whole union, not one per source issue. A year is fifty
        // sources and upwards of a thousand rows; per-issue reads would be fifty
        // round trips on a screen that opens to answer a single question.
        //
        // The owner rule is applied HERE rather than in SQL, because the rows come
        // back keyed by issue and the "earliest source wins" test needs the source
        // ORDER -- which the query above established and a GROUP BY would have to
        // rediscover.
        Map<String, SourceRef> owners = new LinkedHashMap<>();
        if (!sources.isEmpty()) {
            Map<Integer, Date> cutoffById = new LinkedHashMap<>();
            for (PublicationIssue s : sources) {
                cutoffById.put(s.getId(), s.effectiveCutoff());
            }
            for (Object[] row : em.createQuery(
                            "SELECT m.messageUid, m.issue.id, m.sortIndex, m.issue.publicId "
                                    + "FROM IssueMember m WHERE m.issue IN :sources", Object[].class)
                    .setParameter("sources", sources)
                    .getResultList()) {
                String uid = (String) row[0];
                Date cutoff = cutoffById.get((Integer) row[1]);
                int sortIndex = row[2] == null ? 0 : ((Number) row[2]).intValue();
                SourceRef candidate = new SourceRef((String) row[3], cutoff, sortIndex);
                SourceRef held = owners.get(uid);
                // THE EARLIEST SOURCE OWNS IT. Ties on the cut-off cannot happen on
                // a tiling chain, and the guard is here anyway: without it the
                // owner of a repeated uid would be whichever row the database
                // handed over first, which is not a decision anybody took.
                if (held == null || candidate.cutoff().before(held.cutoff())) {
                    owners.put(uid, candidate);
                }
            }
        }

        // The union as the sources gave it, kept separately from the owner map
        // below: what the derivation CONSIDERED does not change when a curator
        // takes a row over.
        Set<String> union = new LinkedHashSet<>(owners.keySet());

        Set<String> members = new LinkedHashSet<>(union);
        members.removeAll(out);
        members.addAll(in);

        Map<String, MemberDecision> decisions = new LinkedHashMap<>();
        for (String uid : union) {
            decisions.put(uid, out.contains(uid)
                    ? new MemberDecision(uid, false, MembershipReason.MANUAL_EXCLUDE)
                    : new MemberDecision(uid, true, MembershipReason.FROM_SOURCE_ISSUE));
        }
        for (String uid : in) {
            decisions.put(uid, new MemberDecision(uid, true, MembershipReason.MANUAL_INCLUDE));
            // AN INCLUDE WINS THE ROW OUTRIGHT, the ownership included. A message
            // a curator added while its week was still open, and that the week
            // then printed, is in both -- and everything downstream reads one of
            // the two answers: the freeze keys on the decision, the print order
            // and the document's sections key on this map. Split answers put the
            // notice inside a week on the screen and under "added by hand" in the
            // document that was released.
            owners.remove(uid);
        }

        // The union uids are the candidates. That is the same relationship the
        // query path has -- what the derivation considered, before curation -- so
        // appliedAtPublish and the stale-override test read it unchanged.
        List<String> candidateUids = List.copyOf(union);

        Survey survey = surveyOf(source, window, sources);

        List<ResolutionWarningVo> warnings = new ArrayList<>();
        // An exclude naming a uid no source carried. It still applies -- curation
        // wins -- and it is worth saying, exactly as it is on the query path.
        List<String> stale = new ArrayList<>();
        for (String uid : out) {
            if (!union.contains(uid)) {
                stale.add(uid);
            }
        }
        if (!stale.isEmpty()) {
            warnings.add(ResolutionWarningVo.of(ResolutionWarningCode.STALE_OVERRIDE, stale));
        }
        // The one warning this regime raises on its own. The publish gate refuses
        // an unacknowledged release on it, so a year released before its last week
        // is out cannot go out unnoticed.
        if (!survey.complete()) {
            List<String> incomplete = new ArrayList<>();
            for (SourceIssue s : survey.open()) {
                incomplete.add(s.publicId());
            }
            for (SourceIssue s : survey.missing()) {
                incomplete.add(MISSING + ":" + s.intervalFrom().getTime() + "-" + s.cutoff().getTime());
            }
            warnings.add(ResolutionWarningVo.of(
                    ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE, incomplete));
        }

        // No misses, and never LIMIT_EXCEEDED, CANCELLED_BUT_DATE_ALIVE or
        // NULL_PUBLISH_FROM_DROPPED. Each of those is a statement about candidates
        // a query considered and rejected, and a compilation rejects nothing: its
        // candidates are rows another issue already published, which were judged
        // against that issue's own cut-off at the time.
        return new MemberResolutionService.Resolution(
                decisions, candidateUids, members, List.of(), warnings, owners, survey);
    }

    /**
     * What the source series looks like over one window, for the panel and the row.
     *
     * Public so the checklist and the workbench read the same answer the
     * resolution carries, and so a caller that needs only the coverage question
     * does not pay for the union.
     */
    public Survey survey(PublicationSeries source, Interval window) {
        if (source == null || window == null) {
            throw new IllegalArgumentException("survey() takes no nulls");
        }
        return surveyOf(source, window, publishedSources(source, window));
    }

    /**
     * Whether one message is in the compiled union of one window.
     *
     * ONE existence query, and it never loads the union. The caller is the message
     * editor's "which issues is this in" panel, which asks once per open issue on
     * every message it renders -- and the union of a running annual is a thousand
     * rows it would throw away.
     *
     * Curation is NOT applied here: the question this answers is what the
     * derivation selects, which is what an override is then recorded against.
     */
    public boolean contains(PublicationSeries source, Interval window, String messageUid) {
        if (source == null || window == null || messageUid == null || messageUid.isBlank()) {
            return false;
        }
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(m) FROM IssueMember m WHERE m.messageUid = :uid "
                        + "AND m.issue.series = :s AND m.issue.status = :published "
                        + "AND COALESCE(m.issue.cutoffStampedAt, m.issue.intervalTo) <= :to");
        if (window.previousCutoff() != null) {
            jpql.append(" AND COALESCE(m.issue.cutoffStampedAt, m.issue.intervalTo) > :from");
        }
        var query = em.createQuery(jpql.toString(), Long.class)
                .setParameter("uid", messageUid)
                .setParameter("s", source)
                .setParameter("published", IssueStatus.PUBLISHED)
                .setParameter("to", window.cutoff());
        if (window.previousCutoff() != null) {
            query.setParameter("from", window.previousCutoff());
        }
        return query.getSingleResult() > 0;
    }

    // ===================================================================

    /**
     * The PUBLISHED source issues whose effective cut-off falls in the window,
     * in cut-off order.
     *
     * COALESCE(cutoffStampedAt, intervalTo) is PublicationIssue.effectiveCutoff
     * expressed in JPQL. It is the one coalesce in the system and it has to be
     * the same one here: a source whose stamp and whose nominal close disagree
     * would otherwise be compiled into one year by the list and another by the
     * arithmetic.
     *
     * Half-open, like every other period in this system: strictly after the lower
     * bound, at or before the upper, so a weekly stamped exactly on the annual's
     * boundary belongs to the earlier annual and to exactly one.
     */
    private List<PublicationIssue> publishedSources(PublicationSeries source, Interval window) {
        String bound = window.previousCutoff() == null ? ""
                : "AND COALESCE(i.cutoffStampedAt, i.intervalTo) > :from ";
        var query = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s AND i.status = :published "
                                + "AND COALESCE(i.cutoffStampedAt, i.intervalTo) IS NOT NULL "
                                + "AND COALESCE(i.cutoffStampedAt, i.intervalTo) <= :to " + bound
                                + "ORDER BY COALESCE(i.cutoffStampedAt, i.intervalTo) ASC",
                        PublicationIssue.class)
                .setParameter("s", source)
                .setParameter("published", IssueStatus.PUBLISHED)
                .setParameter("to", window.cutoff());
        if (window.previousCutoff() != null) {
            query.setParameter("from", window.previousCutoff());
        }
        return query.getResultList();
    }

    /**
     * The survey, given the published sources already read.
     *
     * The overload exists so a resolve pays for that query once. Taking it again
     * here would also let the two disagree: a weekly published between the union
     * query and the coverage query would be compiled and then reported missing.
     */
    private Survey surveyOf(PublicationSeries source, Interval window,
                            List<PublicationIssue> published) {
        List<SourceIssue> open = new ArrayList<>();
        for (PublicationIssue i : openSources(source, window)) {
            open.add(rowOf(i));
        }

        // A MISSING stretch is a hole BETWEEN two published sources, and only
        // that.
        //
        // Nothing is missing before the first source: the compilation's period may
        // legitimately open before the source series produced anything, and the
        // first annual of a compilation would otherwise report its whole run-up as
        // a gap. Nothing is missing after the last one either, and that is the
        // half worth stating: a weekly whose cut-off falls after this annual's
        // belongs to the NEXT annual by the cut-off rule, so calling the tail
        // missing would report every compilation as incomplete for ever.
        List<SourceIssue> missing = new ArrayList<>();
        for (int i = 1; i < published.size(); i++) {
            PublicationIssue previous = published.get(i - 1);
            PublicationIssue next = published.get(i);
            Date closed = previous.effectiveCutoff();
            Date opened = next.getIntervalFrom();
            if (closed != null && opened != null && opened.after(closed)) {
                missing.add(new SourceIssue(null, null, null, null, opened,
                        MISSING, null, closed));
            }
        }

        List<SourceIssue> sources = new ArrayList<>();
        for (PublicationIssue i : published) {
            sources.add(rowOf(i));
        }
        sources.addAll(open);
        sources.addAll(missing);
        // Cut-off order over the whole list, so the panel reads as the period
        // does: a missing stretch sits between the weeks that bracket it, and an
        // open week sits where it will fall once it is released.
        sources.sort((a, b) -> {
            if (a.cutoff() == null || b.cutoff() == null) {
                return a.cutoff() == null ? (b.cutoff() == null ? 0 : 1) : -1;
            }
            return a.cutoff().compareTo(b.cutoff());
        });

        return new Survey(List.copyOf(sources), List.copyOf(open), List.copyOf(missing));
    }

    /**
     * The OPEN source issues whose effective cut-off falls in the window.
     *
     * They contribute NOTHING to the union -- an open issue has no frozen rows --
     * and they are read so the gap between "the year is complete" and "the year is
     * as complete as it can be right now" can be stated rather than left for
     * somebody to notice in a list of a thousand rows.
     *
     * AN OPEN ISSUE WITH NO EFFECTIVE CUT-OFF IS NOT REPORTED, because there is
     * nothing to say which period it will land in: a week whose close is still to
     * be chosen could be this year's or the next one's, and placing it by guess
     * would put a row in the panel that moves when somebody types a date. It is
     * the same acceptance the tail after the last source rests on -- a week that
     * has not happened yet is not a hole in the year.
     */
    private List<PublicationIssue> openSources(PublicationSeries source, Interval window) {
        String bound = window.previousCutoff() == null ? ""
                : "AND COALESCE(i.cutoffStampedAt, i.intervalTo) > :from ";
        var query = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s AND i.status = :open "
                                + "AND COALESCE(i.cutoffStampedAt, i.intervalTo) IS NOT NULL "
                                + "AND COALESCE(i.cutoffStampedAt, i.intervalTo) <= :to " + bound
                                + "ORDER BY COALESCE(i.cutoffStampedAt, i.intervalTo) ASC",
                        PublicationIssue.class)
                .setParameter("s", source)
                .setParameter("open", IssueStatus.OPEN)
                .setParameter("to", window.cutoff());
        if (window.previousCutoff() != null) {
            query.setParameter("from", window.previousCutoff());
        }
        return query.getResultList();
    }

    /** One real issue as a survey row. */
    private static SourceIssue rowOf(PublicationIssue i) {
        return new SourceIssue(
                i.getPublicId(),
                i.getWeek(),
                i.getWeekTo(),
                i.getYear(),
                i.effectiveCutoff(),
                i.getStatus() == null ? null : i.getStatus().name(),
                i.getMemberCount(),
                i.getIntervalFrom());
    }
}
