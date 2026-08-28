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
import jakarta.persistence.EntityManager;

import org.niord.core.publication.series.resolve.GapDetection;
import org.niord.core.publication.series.resolve.GapSynthesis;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.series.vo.GapDetectionVo;
import org.niord.core.publication.series.vo.IssueListResultVo;
import org.niord.core.publication.series.vo.IssueTimelineRowVo;
import org.niord.core.publication.series.vo.IssueTimelineVo;
import org.niord.core.publication.series.vo.PublicationIssueDescVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One series' issue list: the rows that exist, the periods that do not, and
 * whether anybody looked.
 *
 * It is a service rather than a method on the issues endpoint because two
 * screens ask the same question. The issue list wants the rows; the dashboard
 * timeline wants a cell per recent period across many series at once. Built
 * twice, the named gap week in the list and the missing cell in the strip drift
 * apart, and they are the same fact.
 *
 * "now" is a parameter, never a clock read inside. Which period is UPCOMING and
 * whether a series is dormant are both answers about the calendar, and a service
 * that reads the clock itself cannot be asked what the list looked like at the
 * moment a report was rendered.
 */
@ApplicationScoped
public class IssueListService {

    @Inject
    EntityManager em;

    /**
     * The merged list for one series.
     *
     * Real rows come from the database in order; pseudo-rows are synthesized and
     * merged into the same sequence by sort key, so a caller pages one list
     * rather than reconciling two.
     */
    public IssueListResultVo forSeries(PublicationSeries series, Date now) {
        return build(series, issuesOf(series), now);
    }

    /**
     * The same list, one page of it.
     *
     * Gap rows are synthesized over the span the PAGE covers and are counted
     * separately, so a page can render more rows than its own size and `total`
     * stays a count of real issues. That is deliberate and visible: the
     * alternative is a second call the client merges, which doubles the requests
     * on the busiest screen to make one number tidier.
     */
    public IssueListResultVo forSeries(PublicationSeries series, Date now, int page, int maxSize) {
        List<PublicationIssue> all = issuesOf(series);
        int size = maxSize <= 0 ? Integer.MAX_VALUE : maxSize;
        // In long arithmetic: page * size overflows int at any page number a
        // client can put in a query string, and an overflowed offset wraps
        // negative and hands back the FIRST page for a request that asked for a
        // page past the end.
        long offset = (long) Math.max(0, page) * size;
        int from = (int) Math.min(offset, all.size());
        int to = (int) Math.min(offset + size, all.size());

        IssueListResultVo out = build(series, all.subList(from, to), now);
        // total is the whole archive, not the page: a pager divides this number
        // into pages, and reporting the page's own length would leave every list
        // claiming to be one page long.
        out.setTotal(all.size());
        out.setSize(size);
        return out;
    }

    /**
     * The dashboard's strip of recent periods for one series.
     *
     * The SAME gate and the SAME synthesizer the issue list uses. That is the
     * whole design of this class: the named gap week in the list and the missing
     * cell in the strip are one fact, and two producers of one fact drift apart
     * quietly -- the list would offer a retro-create for a week the strip shows
     * as present, and nothing would say which was right.
     *
     * Counted back in the series' own cadence from its newest period, which for a
     * cadenced series means the synthesized cells fill the holes and the count is
     * of PERIODS rather than of issues. A series with no cadence has no period to
     * count, so it returns its most recent issues and no synthesized cells at
     * all -- the gate closes on NO_CADENCE and says so.
     */
    public IssueTimelineVo recent(PublicationSeries series, int periods, Date now, String lang) {
        return buildRecent(series, issuesOf(series), periods, now, lang);
    }

    /**
     * The same list, over issues somebody already has.
     *
     * Split from the query so the answer can be checked without a database. The
     * interesting behaviour here is entirely about what the JSON says -- an
     * absent gapCount, a reason code, a pseudo-row carrying no publicId -- and a
     * test that needs MySQL to see it does not run on the build machine, which
     * is how the last set of assertions about this feature stopped running.
     *
     * @param issues ordered NEWEST first by effective cut-off
     */
    public static IssueListResultVo build(PublicationSeries series, List<PublicationIssue> issues,
                                          Date now) {
        Synthesis synthesis = synthesize(series, issues, now);

        List<SystemPublicationIssueVo> rows = new ArrayList<>();
        for (PublicationIssue issue : issues) {
            rows.add(issue.toVo(SystemPublicationIssueVo.class));
        }
        for (GapSynthesis.Row row : synthesis.pseudo()) {
            rows.add(toVo(row, series));
        }
        rows.sort(NEWEST_FIRST);

        IssueListResultVo out = new IssueListResultVo();
        out.setPublicationSeriesId(series.getSeriesId());
        out.setData(rows);
        out.setTotal(issues.size());
        out.setGapDetection(gateVo(synthesis.gate()));
        // Absent, not zero, when the gate is closed. Zero is a finding about every
        // period; a closed gate is the absence of a finding, and the two must not
        // arrive at a screen looking identical.
        out.setGapCount(synthesis.gate().enabled()
                ? (int) synthesis.pseudo().stream()
                        .filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).count()
                : null);
        return out;
    }

    /**
     * One pass of the shared synthesizer over a series' issues.
     *
     * Extracted so the list and the timeline cannot each compute the gate their
     * own way. Everything downstream -- which periods are missing, whether one is
     * upcoming, whether anybody looked -- follows from this, and two copies of it
     * would disagree the first time either was corrected.
     */
    private record Synthesis(GapDetection.Gate gate, List<GapSynthesis.Row> pseudo,
                             ZoneId zone, long periodMillis) {
    }

    private static Synthesis synthesize(PublicationSeries series, List<PublicationIssue> issues,
                                        Date now) {
        ZoneId zone = series.cutoffZone();
        String cadence = series.getCadence() == null ? null : series.getCadence().name();

        // The newest effective cut-off, which the query has already sorted to the
        // front. Dormancy and the period length are both measured around it
        // rather than around today: a YEARLY series' period depends on which year
        // it is being counted in.
        Date newest = issues.isEmpty() ? null : issues.get(0).effectiveCutoff();
        long periodMillis = GapDetection.periodMillisOf(cadence, zone, newest == null ? now : newest);
        boolean dormant = GapDetection.isDormant(newest, now, periodMillis);

        GapDetection.Gate gate = GapDetection.gate(series.getTimeRelation(), cadence,
                series.getStatus() == SeriesStatus.ACTIVE, dormant, periodMillis);

        return new Synthesis(gate, GapSynthesis.synthesize(gate, series.getSeriesId(),
                ascending(issues), periodMillis, zone, patternsOf(series),
                series.getFirstIssueStartsAt(), now), zone, periodMillis);
    }

    /**
     * The recent-periods strip, over issues somebody already has.
     *
     * Split from the query for the same reason as build(): everything that can go
     * wrong here is about what the rows SAY -- which cell has no publicId, which
     * week a cell is named after, what a cadence-less series does instead -- and a
     * test that needs MySQL to see it stops running the moment the build machine
     * has none.
     *
     * @param issues ordered NEWEST first by effective cut-off
     */
    public static IssueTimelineVo buildRecent(PublicationSeries series, List<PublicationIssue> issues,
                                              int periods, Date now, String lang) {
        Synthesis synthesis = synthesize(series, issues, now);

        List<IssueTimelineRowVo> rows = new ArrayList<>();
        for (PublicationIssue issue : issues) {
            rows.add(rowOf(issue, series, synthesis.zone(), lang));
        }
        for (GapSynthesis.Row row : synthesis.pseudo()) {
            rows.add(rowOf(row, series, synthesis.zone(), lang));
        }
        // Newest period first, and a total order for the same reason the issue
        // list needs one: two cells sharing a close would otherwise swap places
        // between requests. A row with no close at all sorts last -- its period is
        // unknown, which is not the same as recent.
        rows.sort(Comparator
                .comparing((IssueTimelineRowVo r) -> r.getIntervalTo() == null
                        ? null : r.getIntervalTo().getTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.getPublicId() == null ? "" : r.getPublicId(),
                        Comparator.reverseOrder()));

        IssueTimelineVo out = new IssueTimelineVo();
        out.setPublicationSeriesId(series.getSeriesId());
        out.setGapDetection(gateVo(synthesis.gate()));
        out.setRows(rows.size() <= periods ? rows : new ArrayList<>(rows.subList(0, periods)));
        return out;
    }

    /** A real issue as a timeline cell. */
    private static IssueTimelineRowVo rowOf(PublicationIssue issue, PublicationSeries series,
                                            ZoneId zone, String lang) {
        IssueTimelineRowVo row = new IssueTimelineRowVo();
        row.setPublicId(issue.getPublicId());
        row.setComputedStatus(issue.getStatus() == null ? null : issue.getStatus().name());
        row.setIntervalFrom(issue.getIntervalFrom());
        row.setIntervalTo(issue.effectiveCutoff());
        // The tri-state the issue VO already emits: a publication with no
        // membership semantics reports no count rather than a zero, which would
        // read as "resolved, and empty".
        row.setMemberCount(issue.getMembershipProvenance() == MembershipProvenance.NO_MEMBERSHIP
                ? null : issue.getMemberCount());

        // The STORED week and year win where the issue has them: they were fixed
        // when it was published, and re-deriving would let a later change to the
        // series' timezone renumber a cell whose issue is already in print.
        if (issue.getWeek() != null && issue.getYear() != null) {
            row.setWeek(issue.getWeek());
            row.setYear(issue.getYear());
        } else {
            IssueNaming.Numbers numbers = numbersOf(issue.effectiveCutoff(), zone);
            if (numbers != null) {
                row.setWeek(numbers.week());
                row.setYear(numbers.year());
            }
        }

        // What the issue is CALLED, because somebody may have renamed it and the
        // strip has to agree with the list beside it.
        row.setLabel(nameOf(issue, lang));
        if (row.getLabel() == null) {
            row.setLabel(fallbackLabel(series, numbersOf(issue.effectiveCutoff(), zone)));
        }
        return row;
    }

    /** A synthesized period as a timeline cell. */
    private static IssueTimelineRowVo rowOf(GapSynthesis.Row pseudo, PublicationSeries series,
                                            ZoneId zone, String lang) {
        IssueTimelineRowVo row = new IssueTimelineRowVo();
        // publicId and memberCount both stay null. There is no entity behind this
        // cell, and a zero count would claim a period was resolved and found empty.
        row.setComputedStatus(pseudo.kind().name());
        row.setIntervalFrom(pseudo.intervalFrom());
        row.setIntervalTo(pseudo.intervalTo());

        IssueNaming.Numbers numbers = numbersOf(pseudo.intervalTo(), zone);
        if (numbers != null) {
            row.setWeek(numbers.week());
            row.setYear(numbers.year());
        }

        // The suggestion the synthesizer already produced, which is derived from
        // the period's CLOSE. Deriving from the start would label every weekly
        // cell as a double week, because every weekly period spans two ISO weeks.
        GapSynthesis.Suggestion suggestion = suggestionOf(pseudo, lang);
        row.setLabel(suggestion != null && suggestion.name() != null
                ? suggestion.name() : fallbackLabel(series, numbers));
        return row;
    }

    /** The naming numbers for a period's close, or null where there is no close. */
    private static IssueNaming.Numbers numbersOf(Date close, ZoneId zone) {
        // intervalFrom is deliberately not passed: it is what turns a name into
        // the "week 26+27" double form, and that belongs to an issue somebody
        // authored, not to a cell describing one period.
        return close == null ? null : IssueNaming.derive(close, null, zone, null);
    }

    /** The issue's name in the requested language, or in whichever it has. */
    private static String nameOf(PublicationIssue issue, String lang) {
        String any = null;
        for (PublicationIssueDesc desc : issue.getDescs()) {
            if (desc.getName() == null || desc.getName().isBlank()) {
                continue;
            }
            if (lang != null && lang.equals(desc.getLang())) {
                return desc.getName();
            }
            if (any == null) {
                any = desc.getName();
            }
        }
        return any;
    }

    private static GapSynthesis.Suggestion suggestionOf(GapSynthesis.Row row, String lang) {
        if (row.suggestions().isEmpty()) {
            return null;
        }
        GapSynthesis.Suggestion wanted = lang == null ? null : row.suggestions().get(lang);
        return wanted != null ? wanted : row.suggestions().values().iterator().next();
    }

    /**
     * What to call a cell when the series declares no naming pattern.
     *
     * Numeric rather than worded, in every language: a label invented here would
     * be one more place a translation lives, and it would be wrong in every
     * deployment that does not speak the language it was written in. The shape
     * follows the cadence because "33/2026" is meaningless on a yearly series and
     * a bare year is meaningless on a weekly one.
     */
    private static String fallbackLabel(PublicationSeries series, IssueNaming.Numbers numbers) {
        if (numbers == null) {
            return null;
        }
        SeriesCadence cadence = series.getCadence() == null ? SeriesCadence.NONE : series.getCadence();
        return switch (cadence) {
            case YEARLY -> String.valueOf(numbers.year());
            case MONTHLY -> String.format("%04d-%02d", numbers.year(), numbers.month());
            case DAILY -> String.format("%04d-%02d-%02d", numbers.year(), numbers.month(), numbers.day());
            case WEEKLY, NONE -> numbers.week() + "/" + numbers.year();
        };
    }

    /**
     * Newest first, and the order is TOTAL.
     *
     * Rows sharing a cut-off -- a retire-and-republish pair -- would otherwise
     * swap places between requests and the list would appear to reshuffle itself
     * while nobody touched it. A row with no cut-off at all sorts last rather than
     * first: it is an issue whose period is unknown, not the most recent one.
     */
    private static final Comparator<SystemPublicationIssueVo> NEWEST_FIRST =
            Comparator.comparing(SystemPublicationIssueVo::getSortKey,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(v -> v.getPublicId() == null ? "" : v.getPublicId(),
                            Comparator.reverseOrder());

    private List<PublicationIssue> issuesOf(PublicationSeries series) {
        // Ordered in the database rather than in Java: a series with a decade of
        // weeklies has ~500 rows.
        //
        // On the COALESCE rather than on cutoffStampedAt alone. An OPEN issue has
        // no stamp, so ordering by the stamp sorted the issue being worked on to
        // the BOTTOM of its own series -- below issues published years earlier.
        //
        // THE DESCS COME WITH THEM. Every row is mapped to a value object that
        // reads its per-language names, file names and links, so a lazy
        // collection means one extra SELECT per issue: on the weekly series that
        // is five hundred round trips to render one screen. The fetch join is a
        // LEFT one because an issue with no desc row must still appear -- an inner
        // join would silently drop exactly the rows that look wrong. DISTINCT
        // because the join multiplies each issue by its language count.
        return em.createQuery(
                        "SELECT DISTINCT i FROM PublicationIssue i LEFT JOIN FETCH i.descs "
                                + "WHERE i.series = :s "
                                + "ORDER BY COALESCE(i.cutoffStampedAt, i.intervalTo) DESC, i.publicId DESC",
                        PublicationIssue.class)
                .setParameter("s", series)
                .getResultList();
    }

    /** What the synthesizer needs, oldest first, as it expects. */
    private static List<GapSynthesis.Issue> ascending(List<PublicationIssue> newestFirst) {
        List<GapSynthesis.Issue> out = new ArrayList<>();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            PublicationIssue issue = newestFirst.get(i);
            Date cutoff = issue.effectiveCutoff();
            if (cutoff == null) {
                // No bound at all: it cannot anchor a gap, and including it would
                // let the arithmetic run off a null.
                continue;
            }
            out.add(new GapSynthesis.Issue(issue.getPublicId(), cutoff, cutoffSourceOf(issue),
                    issue.getIntervalFrom(), issue.getStatus() == IssueStatus.OPEN));
        }
        return out;
    }

    /**
     * How firm an issue's cut-off is, for a neighbouring gap row to inherit.
     *
     * A stamped cut-off is the publish action's own record and outranks whatever
     * the nominal bound says, which is why it is checked first.
     *
     * Package-visible because the draft endpoint inherits the same marker when it
     * chains an interval off an issue's close. The list and the draft describe the
     * same bound, and two answers about how firm it is would show one marker in the
     * table and a different one in the form the row opens.
     */
    static IntervalBoundSource cutoffSourceOf(PublicationIssue issue) {
        if (issue.getCutoffStampedAt() != null) {
            return IntervalBoundSource.STAMPED;
        }
        return issue.getIntervalToSource() == null ? IntervalBoundSource.NOMINAL : issue.getIntervalToSource();
    }

    /** The naming patterns per language the series declares. */
    private static Map<String, GapSynthesis.Patterns> patternsOf(PublicationSeries series) {
        Map<String, GapSynthesis.Patterns> out = new LinkedHashMap<>();
        for (PublicationSeriesDesc desc : series.getDescs()) {
            if (desc.getLang() == null || desc.getLang().isBlank()) {
                continue;
            }
            out.put(desc.getLang(),
                    new GapSynthesis.Patterns(desc.getNameSuggestionPattern(), desc.getFileNamePattern()));
        }
        return out;
    }

    /**
     * A pseudo-row in the shape of a real one.
     *
     * It is the same type on purpose. A separate type would let a client render
     * one list from two arrays and get the interleaving wrong, and every field a
     * gap genuinely has -- interval, bounds, provenance -- means the same thing
     * here as it does on a real row. The fields it does NOT have are absent from
     * the JSON rather than null, so "no publicId" cannot be read as a value.
     */
    private static SystemPublicationIssueVo toVo(GapSynthesis.Row row, PublicationSeries series) {
        SystemPublicationIssueVo vo = new SystemPublicationIssueVo();
        vo.setSeriesId(row.seriesId());
        vo.setPseudo(row.kind().name());
        vo.setComputedStatus(row.kind().name());
        vo.setIntervalFrom(row.intervalFrom());
        vo.setIntervalTo(row.intervalTo());
        vo.setIntervalFromSource(row.intervalFromSource() == null ? null : row.intervalFromSource().name());
        vo.setIntervalToSource(row.intervalToSource() == null ? null : row.intervalToSource().name());
        // The period's end IS its effective cut-off. Nothing stamped it, which is
        // what intervalToSource says.
        vo.setEffectiveCutoff(row.intervalTo());
        vo.setSortKey(row.sortKey());
        vo.setPrecedingPublicId(row.precedingPublicId());
        vo.setFollowingPublicId(row.followingPublicId());

        List<PublicationIssueDescVo> suggested = new ArrayList<>();
        for (Map.Entry<String, GapSynthesis.Suggestion> e : row.suggestions().entrySet()) {
            PublicationIssueDescVo desc = new PublicationIssueDescVo();
            desc.setLang(e.getKey());
            desc.setName(e.getValue().name());
            desc.setFileName(e.getValue().fileName());
            suggested.add(desc);
        }
        vo.setSuggestedDescs(suggested.isEmpty() ? null : suggested);

        // memberCount stays null. A period nobody published has no membership, and
        // a 0 here would read as "resolved, empty" -- which is a different claim.
        return vo;
    }

    private static GapDetectionVo gateVo(GapDetection.Gate gate) {
        GapDetectionVo vo = new GapDetectionVo();
        vo.setEnabled(gate.enabled());
        vo.setReasonCode(gate.code() == null ? null : gate.code().name());
        vo.setReason(gate.reason());
        return vo;
    }

}
