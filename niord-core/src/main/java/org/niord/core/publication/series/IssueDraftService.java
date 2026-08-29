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
import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.resolve.GapDetection;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueDraftVo;
import org.niord.core.publication.series.vo.IssueDraftWarningVo;
import org.niord.core.publication.series.vo.PublicationIssueDescVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueDescVo;
import org.niord.core.service.BaseService;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * S12. The issue that does not exist yet.
 *
 * ONE endpoint serves three screens -- "＋ Ny udgave", the retro-create prefill,
 * and the live preview on a gap row -- because they are three ways of asking the
 * same question: what would the next issue over this interval be? Built
 * separately, the interval one screen prefills and the interval another one
 * shows are two derivations of one bound, and the day they disagree an admin
 * creates a week that overlaps the one before it and finds out at publish.
 *
 * IT WRITES NOTHING. No issue, no desc row, no member, no override. Every field
 * below is computed from the series and its existing issues on each request,
 * which is what makes it safe to call while somebody drags a date around.
 *
 * Two derivations are deliberately NOT re-implemented here. The overlap refusal
 * is {@link IssueLifecycleService#assertNoOverlap}, the same call the create
 * makes, so a draft cannot report a period as free and then be refused on save.
 * The provenance of a chained bound is the list's own
 * {@link IssueListService#cutoffSourceOf}, so the marker in the table and the
 * marker in the form it opens are the same marker.
 */
@ApplicationScoped
public class IssueDraftService extends BaseService {

    @Inject
    MemberResolutionService memberResolver;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    org.niord.core.publication.series.criteria.DomainSeriesExpander domains;

    // ------------------------------------------------------------------ warnings

    /** The interval opens where nothing closed: nobody's chain continues here. */
    public static final String NOT_CHAINED = "INTERVAL_NOT_CHAINED";

    /** Only an ACTIVE series is expected to keep producing issues. */
    public static final String SERIES_NOT_ACTIVE = "SERIES_NOT_ACTIVE";

    /** An IN_FORCE_AT_CUTOFF issue has no lower bound at all -- by rule, not by omission. */
    public static final String IN_FORCE_HAS_NO_LOWER_BOUND = "IN_FORCE_HAS_NO_LOWER_BOUND";

    /** Without a cadence there is no nominal close to propose. */
    public static final String NO_NOMINAL_CLOSE = "NO_NOMINAL_CLOSE";

    /** Nothing anchors the interval: no issue to chain off, and no declared start. */
    public static final String NO_ANCHOR = "NO_ANCHOR";

    /** The series selects nothing by query, so there is no count to take. */
    public static final String NO_MEMBERSHIP_CRITERIA = "NO_MEMBERSHIP_CRITERIA";

    /** The criteria document did not resolve, so the count could not be taken. */
    public static final String CRITERIA_UNRESOLVABLE = "CRITERIA_UNRESOLVABLE";

    /** A one-off holds exactly one issue, and this series already has it. */
    public static final String ONE_OFF_ALREADY_HAS_ITS_ISSUE = "ONE_OFF_ALREADY_HAS_ITS_ISSUE";

    // ------------------------------------------------------------------ the draft

    /**
     * The draft for one series.
     *
     * @param afterPublicId chain the interval off this issue's close, or null
     * @param explicitFrom  an interval start the caller chose, or null
     * @param explicitTo    an interval end the caller chose, or null
     * @param now           the moment the draft is being taken at; never a clock
     *                      read inside, so a report can ask what the draft looked
     *                      like at the instant it was rendered
     */
    public IssueDraftVo draft(PublicationSeries series, String afterPublicId,
                              Date explicitFrom, Date explicitTo, Date now) {
        List<IssueDraftWarningVo> warnings = new ArrayList<>();
        List<PublicationIssue> issues = issuesNewestFirst(series);
        boolean tiles = series.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;
        ZoneId zone = series.cutoffZone();

        // WHERE THE PERIOD OPENS, and how firm that bound is.
        //
        // Three callers, three answers, one rule underneath: the bound is an
        // issue's close wherever one is named, the series' declared first start at
        // the head of the chain, and the admin's own typing when it is neither.
        PublicationIssue predecessor;
        Date from;
        IntervalBoundSource fromSource;

        if (afterPublicId != null && !afterPublicId.isBlank()) {
            predecessor = named(issues, afterPublicId, series);
            from = predecessor.effectiveCutoff();
            fromSource = chainedSource(predecessor);
        } else if (explicitFrom != null) {
            from = explicitFrom;
            // A typed bound that lands exactly on an issue's close IS a chained
            // bound -- that is what the gap row's live preview passes back -- so it
            // inherits that issue's provenance rather than being marked MANUAL and
            // losing the marker the row it came from was showing.
            predecessor = closingAt(issues, from);
            fromSource = predecessor != null ? chainedSource(predecessor)
                    : sameInstant(from, series.getFirstIssueStartsAt())
                            ? IntervalBoundSource.NOMINAL
                            : IntervalBoundSource.MANUAL;
        } else {
            // The newest issue whose period has a CLOSE. An issue still being
            // worked on has none -- nothing stamped it and no nominal bound was
            // written -- and chaining off it would open the next period at an
            // instant that does not exist yet.
            predecessor = newestDated(issues);
            from = predecessor == null ? series.getFirstIssueStartsAt() : predecessor.effectiveCutoff();
            fromSource = predecessor == null
                    // The head of the chain: S-4 requires every interval-based
                    // series to declare where its first period opens, and that
                    // declaration is a nominal bound rather than a recorded one.
                    ? IntervalBoundSource.NOMINAL
                    : chainedSource(predecessor);
        }

        // WHERE IT CLOSES. Explicit wins; otherwise one cadence period from the
        // anchor, which is the same arithmetic the gap rows are tiled with -- so a
        // gap row and the draft it opens describe the same interval to the
        // millisecond, and therefore carry the same name.
        Date to;
        IntervalBoundSource toSource;
        if (explicitTo != null) {
            to = explicitTo;
            toSource = IntervalBoundSource.MANUAL;
        } else {
            String cadence = series.getCadence() == null ? null : series.getCadence().name();
            Date anchor = from;
            long period = GapDetection.periodMillisOf(cadence, zone, anchor == null ? now : anchor);
            if (anchor == null) {
                to = null;
                toSource = null;
                warnings.add(new IssueDraftWarningVo(NO_ANCHOR,
                        "there is no issue to chain off and the series does not say when its first "
                                + "period starts, so no interval can be proposed"));
            } else if (!tiles && predecessor == null) {
                // The first edition of an in-force publication closes AT its
                // declared start: that instant is the validity boundary the edition
                // describes, not a period before it.
                to = anchor;
                toSource = IntervalBoundSource.NOMINAL;
            } else if (period > 0) {
                to = new Date(anchor.getTime() + period);
                toSource = IntervalBoundSource.NOMINAL;
            } else {
                to = null;
                toSource = null;
                warnings.add(new IssueDraftWarningVo(NO_NOMINAL_CLOSE,
                        "this publication has no cadence, so there is no nominal close to propose; "
                                + "choose the cut-off yourself"));
            }
        }

        // An interval that ends before it starts selects nothing, and the issue
        // would publish EMPTY rather than fail -- so it is refused here, where the
        // admin still has the dates in front of them.
        if (tiles && from != null && to != null && !to.after(from)) {
            throw new IssueLifecycleService.TransitionRefusedException("INTERVAL_INVERTED",
                    "the period would close at " + to + ", at or before it opens at " + from
                            + ". An interval that ends before it starts selects nothing, and the issue "
                            + "would publish empty rather than fail.");
        }

        // The same refusal the create makes, taken here so an admin finds out
        // while the dates are still editable rather than on save.
        if (tiles) {
            lifecycle.assertNoOverlap(series, from, null);
        }

        warnings.addAll(observations(series, issues, tiles, predecessor, fromSource));

        IssueNaming.Numbers numbers = to == null ? null
                : IssueNaming.derive(to, tiles ? from : null, zone, nextEdition(series, issues));

        IssueDraftVo vo = new IssueDraftVo();
        vo.setSeriesId(series.getSeriesId());
        // OPEN is what it would be created as. There is no publicId and no
        // repoPath: both are minted at create, and inventing either here would put
        // an id into a form that the created issue then does not have.
        vo.setStatus(IssueStatus.OPEN.name());
        vo.setComputedStatus(IssueStatus.OPEN.name());

        // IN_FORCE_AT_CUTOFF carries NO lower bound -- §4.2 refuses one outright,
        // so the draft must not prefill a form with a value the create rejects.
        // The bound is still used above to work out where the period closes.
        vo.setIntervalFrom(tiles ? from : null);
        vo.setIntervalFromSource(tiles && fromSource != null ? fromSource.name() : null);
        vo.setIntervalTo(to);
        vo.setIntervalToSource(toSource == null ? null : toSource.name());
        // The period's end IS its effective cut-off. Nothing has stamped it, which
        // is what intervalToSource says.
        vo.setEffectiveCutoff(to);
        vo.setSortKey(to == null ? null : to.getTime());

        if (numbers != null) {
            vo.setWeek(numbers.week());
            vo.setWeekTo(numbers.weekTo());
            vo.setYear(numbers.year());
            vo.setEdition(numbers.edition() == null ? null : String.valueOf(numbers.edition()));
        }

        vo.setDescs(suggestedDescs(series, numbers));
        vo.setChainedFromPublicId(predecessor == null ? null : predecessor.getPublicId());
        vo.setPrecedingPublicId(predecessor == null ? null : predecessor.getPublicId());
        vo.setSeriesCriteria(series.getCriteria());

        vo.setWouldMatchCount(wouldMatch(series, tiles ? from : null, to, warnings));
        vo.setWarnings(warnings);
        return vo;
    }

    // ------------------------------------------------------------------ pieces

    /**
     * What an admin should know before pressing create, none of which refuses.
     *
     * Each of these describes a draft that IS creatable and would surprise
     * somebody afterwards: a period nobody's chain leads into, a series nothing is
     * expected from, a lower bound the form will not show, and the one-off that
     * has already had its issue and whose create will be refused.
     */
    private List<IssueDraftWarningVo> observations(PublicationSeries series,
                                                   List<PublicationIssue> issues,
                                                   boolean tiles,
                                                   PublicationIssue predecessor,
                                                   IntervalBoundSource fromSource) {
        List<IssueDraftWarningVo> out = new ArrayList<>();

        if (series.getStatus() != SeriesStatus.ACTIVE) {
            out.add(new IssueDraftWarningVo(SERIES_NOT_ACTIVE,
                    "'" + series.getSeriesId() + "' is " + series.getStatus()
                            + ". Only an active series is expected to keep producing issues."));
        }
        if (tiles && predecessor == null && fromSource == IntervalBoundSource.MANUAL) {
            out.add(new IssueDraftWarningVo(NOT_CHAINED,
                    "this period opens where no issue closed and where the series does not say its "
                            + "first period starts. Nothing is refused, but the chain will have a "
                            + "seam here that no cut-off accounts for."));
        }
        if (!tiles) {
            out.add(new IssueDraftWarningVo(IN_FORCE_HAS_NO_LOWER_BOUND,
                    "issues of this publication describe what is in force at an instant, so they have "
                            + "no period start at all -- only the cut-off below is theirs."));
        }
        if (series.isOneOff() && !issues.isEmpty()) {
            out.add(new IssueDraftWarningVo(ONE_OFF_ALREADY_HAS_ITS_ISSUE,
                    "'" + series.getSeriesId() + "' is a one-off and already has its issue; creating "
                            + "this one is refused. If the publication is going to keep appearing, "
                            + "change its kind to UNSCHEDULED first."));
        }
        return out;
    }

    /**
     * What the criteria would select over this interval, right now.
     *
     * A PROBE, resolved exactly as the criteria preview resolves an unsaved
     * document: same resolver, same interval type, no domain narrowing, nothing
     * written. The count is the point of the endpoint for the gap row -- "11 ville
     * matche" -- and an admin deciding whether a missing week is worth recovering
     * is deciding on this number.
     *
     * A document that does not resolve does NOT fail the draft. The interval, the
     * name and the chain are all still correct and still worth prefilling; the
     * count is the one part that could not be taken, and it says so as a warning
     * and comes back null rather than 0.
     */
    private Integer wouldMatch(PublicationSeries series, Date from, Date cutoff,
                               List<IssueDraftWarningVo> warnings) {
        IssueCriteriaVo document = series.getCriteria();
        if (cutoff == null) {
            return null;   // no interval; the reason is already among the warnings
        }
        if (document == null || series.getTimeRelation() == null) {
            warnings.add(new IssueDraftWarningVo(NO_MEMBERSHIP_CRITERIA,
                    "this publication selects nothing by query -- it is an uploaded file or a link -- "
                            + "so there is no member count to take"));
            return null;
        }
        try {
            ResolvedCriteria resolved = CriteriaResolver.resolve(document, series.getTimeRelation(),
                    Boolean.TRUE.equals(series.getAliveAtCutoff()), domains);
            return memberResolver.resolve(resolved, new Interval(from, cutoff)).members().size();
        } catch (RuntimeException e) {
            warnings.add(new IssueDraftWarningVo(CRITERIA_UNRESOLVABLE,
                    "the criteria could not be run over this interval, so no count was taken: "
                            + e.getMessage()));
            return null;
        }
    }

    /**
     * What each configured language would call this issue, and what its file
     * would be named.
     *
     * FROM THE CUT-OFF -- the interval END. Deriving from the start returns "Uge
     * 26+27, 2026" where production says "EfS uge 27", because every weekly window
     * runs Wednesday to Wednesday and therefore spans two ISO weeks. The start is
     * passed to the derivation all the same, and only there: it is what detects a
     * genuine double week, where the name really is "Uge 15+16".
     *
     * One row per DECLARED language, even where the pattern expands to nothing.
     * A language the series publishes in and the form has no row for is a language
     * whose file name and link can never be filled in.
     */
    private static List<PublicationIssueDescVo> suggestedDescs(PublicationSeries series,
                                                               IssueNaming.Numbers numbers) {
        List<PublicationIssueDescVo> out = new ArrayList<>();
        for (String lang : series.getLanguages()) {
            SystemPublicationIssueDescVo desc = new SystemPublicationIssueDescVo();
            desc.setLang(lang);
            desc.setName(IssueLifecycleService.suggestName(series, lang, numbers));
            desc.setFileName(numbers == null ? null : fileName(series, lang, numbers));
            out.add(desc);
        }
        return out;
    }

    /** The file name pattern of one language, expanded, or null where it cannot be. */
    private static String fileName(PublicationSeries series, String lang, IssueNaming.Numbers numbers) {
        for (PublicationSeriesDesc d : series.getDescs()) {
            if (!lang.equals(d.getLang())) {
                continue;
            }
            String pattern = d.getFileNamePattern();
            // A pattern naming a token the vocabulary does not have would throw,
            // and a draft is not the place to discover a series-validation
            // problem -- S-14 refuses it on the save that carries it.
            if (pattern == null || pattern.isBlank() || !IssueNaming.isExpandable(pattern)) {
                return null;
            }
            return IssueNaming.expand(pattern, numbers);
        }
        return null;
    }

    /**
     * The next edition number, where the numbering scheme has one.
     *
     * Read off the highest edition already recorded rather than off the issue
     * count: a series whose first two editions were imported as 3 and 4 has no
     * edition 1, and counting rows would propose one that already exists.
     */
    private static Integer nextEdition(PublicationSeries series, List<PublicationIssue> issues) {
        NumberingScheme scheme = series.getNumberingScheme();
        if (scheme != NumberingScheme.YEAR_EDITION && scheme != NumberingScheme.EDITION_SEQUENCE) {
            return null;
        }
        int highest = 0;
        for (PublicationIssue i : issues) {
            String edition = i.getEdition();
            if (edition == null || edition.isBlank()) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(edition.trim()));
            } catch (NumberFormatException e) {
                // An edition somebody wrote as words is not a number to count from.
            }
        }
        return highest + 1;
    }

    /**
     * How firm a bound inherited from a neighbouring issue is.
     *
     * The same mapping the gap rows use, and it is a mapping rather than a copy
     * for one reason: a cut-off an admin TYPED is a recorded bound and reads as
     * STAMPED here, because the marker distinguishes a recorded bound from an
     * inferred one. MANUAL would claim somebody authored THIS period, which nobody
     * has -- that is what makes this a draft.
     */
    private static IntervalBoundSource chainedSource(PublicationIssue neighbour) {
        return switch (IssueListService.cutoffSourceOf(neighbour)) {
            case STAMPED, MANUAL -> IntervalBoundSource.STAMPED;
            case RECOVERED -> IntervalBoundSource.RECOVERED;
            case NOMINAL -> IntervalBoundSource.NOMINAL;
        };
    }

    /** The issues of one series, newest effective cut-off first, as the list orders them. */
    private List<PublicationIssue> issuesNewestFirst(PublicationSeries series) {
        return em.createNamedQuery("PublicationIssue.findBySeriesNewestFirst", PublicationIssue.class)
                .setParameter("series", series)
                .getResultList();
    }

    /** The named issue, refused when it belongs to another series or to none. */
    private static PublicationIssue named(List<PublicationIssue> issues, String publicId,
                                          PublicationSeries series) {
        for (PublicationIssue i : issues) {
            if (publicId.equals(i.getPublicId())) {
                return i;
            }
        }
        throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_FOUND",
                "no issue '" + publicId + "' in series '" + series.getSeriesId() + "'; a draft chains "
                        + "off an issue of the series it belongs to");
    }

    /** The newest issue that has a close to chain off. Null where none has one. */
    private static PublicationIssue newestDated(List<PublicationIssue> newestFirst) {
        for (PublicationIssue i : newestFirst) {
            if (i.effectiveCutoff() != null) {
                return i;
            }
        }
        return null;
    }

    /** The issue whose period closed exactly at this instant, if any. */
    private static PublicationIssue closingAt(List<PublicationIssue> issues, Date instant) {
        for (PublicationIssue i : issues) {
            if (sameInstant(i.effectiveCutoff(), instant)) {
                return i;
            }
        }
        return null;
    }

    private static boolean sameInstant(Date a, Date b) {
        return a != null && b != null && a.getTime() == b.getTime();
    }
}
