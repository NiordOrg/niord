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
import org.niord.core.publication.series.resolve.GapDetection;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.service.BaseService;

import java.util.Date;
import java.util.List;

/**
 * The shape a new issue is born with, and the shape a published one settles into.
 *
 * FOUR THINGS AN ISSUE HAS TO CARRY, and every one of them was missing on a
 * natively created issue: the nominal end of its period, where that end came
 * from, the week and year it is numbered by, and the name each language calls it.
 * Without the first two an open issue has no effective cut-off at all, so it
 * sorts below every dated issue in its own series and the gap detector proposes
 * a "missing" period for exactly the weeks it covers. Without the numbers the
 * report header prints "Uge , " and the wire serves nulls.
 *
 * ONE PLACE, because the alternative was four. The create path, the retro-create,
 * the new edition and the successor the publish transaction mints each built an
 * issue by hand, and they disagreed: three set no upper bound and the fourth set
 * one the numbers were never derived from. The draft (S12) answers the same
 * question for an issue that does not exist yet, and it must give the same
 * answer -- an admin who is shown "EfS uge 30, 1 Jul - 8 Jul" in the create form
 * and then gets "EfS uge 29" on the row has been told two different things about
 * one decision.
 *
 * THE NAME COMES FROM THE CUT-OFF, never from the interval start. An ordinary
 * weekly window runs Wednesday to Wednesday and therefore spans two ISO weeks, so
 * naming from the start calls every issue after the week before the one it
 * closes: "EfS uge 26" on the issue production knows as uge 27.
 */
@ApplicationScoped
public class IssueShape extends BaseService {

    /**
     * Gives a newly built issue its nominal close, its numbers and its names.
     *
     * Called with the issue still detached, before it is persisted, so a refusal
     * further along leaves nothing behind. Everything it reads about the series'
     * other issues is read here rather than passed in, because the four callers
     * would otherwise each have to work out which neighbour anchors the period --
     * and that is the derivation they were already getting different answers to.
     *
     * @param now used only when a cadence has to be measured and there is no bound
     *            to measure it around; never a clock read inside
     */
    public void apply(PublicationIssue issue, PublicationSeries series, Date now) {
        shapeInterval(issue, series, now);
        number(issue, series, false);
    }

    /**
     * Re-derives the numbers and the suggested names from the stamped cut-off.
     *
     * Run at publish, after the stamp and before anything is frozen. Until the
     * stamp exists the issue is numbered by its NOMINAL close, which is a
     * prediction: a week released five days late closes five days late, and the
     * document, its file name and the citation format all have to say so.
     *
     * A name somebody TYPED is left alone. It is a decision rather than a
     * rendering of the period, and re-deriving over it would discard it with
     * nothing to say it had ever been made.
     */
    public void restamp(PublicationIssue issue, PublicationSeries series) {
        number(issue, series, true);
    }

    /**
     * Re-derives the numbers and the suggested names after the period moved.
     *
     * The edit path's half of {@link #restamp}. It differs in exactly one way, and
     * the difference is the right one for an edit: whether a name is a decision or
     * a rendering is asked per LANGUAGE here, off the flag the rename itself sets,
     * rather than issue-wide off the trail. An admin renaming the Danish row has
     * said nothing about the English one, and the English one should still follow
     * the period it renders.
     */
    public void renumber(PublicationIssue issue, PublicationSeries series) {
        number(issue, series, false);
    }

    /**
     * Which year ${year} means for this publication.
     *
     * A week-numbered publication takes the ISO week-based year, because that is
     * the year an ISO week belongs to. Everything else takes the calendar year the
     * cut-off falls in: an annual edition closing at 31 December 23:59 is the
     * edition for the year it closes, and the week-based answer would name it for
     * the January after it -- in its title, in its file name, and therefore in the
     * public download link it is cited by.
     *
     * The cut-off default is consulted as well as the scheme, and deliberately
     * first: a series whose cut-off falls on a period boundary is by definition
     * numbered by that period rather than by the week the boundary happens to
     * land in.
     */
    public static IssueNaming.YearBasis yearBasisOf(PublicationSeries series) {
        if (series == null) {
            return IssueNaming.YearBasis.ISO_WEEK_YEAR;
        }
        CutoffDefault cutoffDefault = series.getCutoffDefault();
        if (cutoffDefault != null && cutoffDefault != CutoffDefault.RELEASE_MOMENT) {
            return IssueNaming.YearBasis.CALENDAR_YEAR;
        }
        NumberingScheme scheme = series.getNumberingScheme();
        return scheme == null || scheme == NumberingScheme.ISO_WEEK_YEAR
                ? IssueNaming.YearBasis.ISO_WEEK_YEAR
                : IssueNaming.YearBasis.CALENDAR_YEAR;
    }

    // ------------------------------------------------------------------ interval

    /**
     * Where the period closes, and how firm that bound is.
     *
     * The arithmetic is the draft's, to the millisecond, because a gap row, the
     * form it opens and the issue that form creates are three renderings of one
     * interval. A caller that already decided the close -- a retro-creation
     * recovering a known period -- keeps it; this only fills a bound nobody set.
     */
    private void shapeInterval(PublicationIssue issue, PublicationSeries series, Date now) {
        boolean tiles = series != null && series.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;

        // The instant the period hangs off: the bound the caller supplied, else
        // the close of the newest issue that has one, else the series' declared
        // first start. An issue still being worked on has no close, and chaining
        // off it would open the next period at an instant that does not exist yet.
        Date anchor = issue.getIntervalFrom();
        PublicationIssue chained = anchor == null
                ? newestDated(series, issue)
                : closingAt(series, issue, anchor);
        if (anchor == null) {
            anchor = chained != null ? chained.effectiveCutoff()
                    : series == null ? null : series.getFirstIssueStartsAt();
        }

        if (!tiles) {
            // ONE BOUND, and that is a fact rather than an omission. An in-force
            // issue says what stood at an instant, so the instant IS its period:
            // 531 production issues carry no lower bound at all, and a lower bound
            // here would make the resolver ask for messages published in a window
            // this publication does not have.
            issue.setIntervalFrom(null);
            issue.setIntervalFromSource(null);
        }

        if (issue.getIntervalTo() == null && series != null && anchor != null) {
            long period = GapDetection.periodMillisOf(
                    series.getCadence() == null ? null : series.getCadence().name(),
                    series.cutoffZone(), anchor);
            Date close = null;
            if (!tiles && chained == null) {
                // The first edition of an in-force publication closes AT its
                // declared boundary: that instant is the validity date the edition
                // describes, not a period before it.
                close = anchor;
            } else if (period > 0) {
                close = new Date(anchor.getTime() + period);
            }
            // A publication with no cadence has no nominal close to propose, and
            // inventing one would put a cut-off on the row that nothing decided.
            if (close != null) {
                issue.setIntervalTo(close);
                issue.setIntervalToSource(IntervalBoundSource.NOMINAL);
            }
        }

        // Each bound says where it came from, and only where there is a bound to
        // say it about: a source beside a null bound describes a decision about
        // nothing, and a bound with no source loses the stamped-versus-nominal
        // marker the issue list puts on every interval it shows.
        if (issue.getIntervalTo() == null) {
            issue.setIntervalToSource(null);
        } else if (issue.getIntervalToSource() == null) {
            issue.setIntervalToSource(IntervalBoundSource.NOMINAL);
        }
        if (issue.getIntervalFrom() == null) {
            issue.setIntervalFromSource(null);
        }
    }

    // ------------------------------------------------------------------- numbers

    /**
     * The week, the weeks and the year, from the issue's own cut-off. No names.
     *
     * Silent about the numbers when there is no cut-off to derive them from -- an
     * issue with neither a stamp nor a nominal close has no week yet, and reading
     * the interval START instead would name it for the wrong end of its own
     * window.
     *
     * STATIC AND SEPARATE, because there is a second caller that must reach the
     * numbers WITHOUT reaching the names. An issue recovered from the archive
     * carries the title the archive gave it, and that title is a fact rather than
     * a rendering of the period -- so it takes the numbers from here and nothing
     * else. Two derivations would drift the moment one of them was corrected, and
     * an issue whose stored week disagrees with the week its own cut-off falls in
     * is unfindable by either.
     *
     * @return the numbers, or null where the issue has no cut-off to number by
     */
    public static IssueNaming.Numbers applyNumbers(PublicationIssue issue, PublicationSeries series) {
        return applyNumbers(issue, series, issue.getIntervalFrom());
    }

    /**
     * The same, told where the period this issue covers began.
     *
     * SEPARATE FROM THE ISSUE'S OWN LOWER BOUND, because for half the estate
     * there is no such bound to read. A double week is one whose window swallowed
     * a period no issue closed -- "uge 36 og 37", the week that went out late --
     * and an in-force publication expresses exactly the same fact while carrying
     * only ONE bound: its own instant. Reading the span off intervalFrom therefore
     * asked a question the row can never answer, and 503 issues of the largest
     * in-force weekly in the estate were numbered for the week they closed in
     * while their titles, typed by hand every time it happened, said two weeks.
     *
     * The span start is the caller's, not this method's, because working out
     * which neighbour a period hangs off needs the series' other issues -- and
     * this method is deliberately reachable without a persistence context.
     *
     * @param spanStart when the period covered began, or null where nothing says
     */
    public static IssueNaming.Numbers applyNumbers(PublicationIssue issue, PublicationSeries series,
                                                   Date spanStart) {
        IssueNaming.Numbers numbers = null;
        Date cutoff = issue.effectiveCutoff();
        if (cutoff != null && series != null) {
            try {
                numbers = IssueNaming.derive(cutoff, spanStart, series.cutoffZone(),
                        editionOf(issue), yearBasisOf(series));
            } catch (RuntimeException e) {
                // An instant that cannot be read as a cut-off leaves the numbers
                // unset rather than refusing the issue outright.
                numbers = null;
            }
        }
        if (numbers != null) {
            issue.setWeek(numbers.week());
            issue.setWeekTo(numbers.weekTo());
            issue.setYear(numbers.year());
        }
        return numbers;
    }

    /**
     * The numbers, and then the per-language names.
     *
     * The names are set regardless of whether the numbers came out: the column is
     * NOT NULL precisely because a nameless issue is unfindable in every list that
     * shows it, and the suggestion falls back through the series name to the
     * series id rather than to a placeholder.
     */
    private void number(PublicationIssue issue, PublicationSeries series, boolean keepChangedNames) {
        IssueNaming.Numbers numbers = applyNumbers(issue, series, spanStart(issue, series));
        if (series == null) {
            return;
        }

        // A name an admin typed is kept, and the per-language flag is the record of
        // that. The trail is consulted only where NO language carries the flag: it
        // exists for issues renamed before the flag did, and asking it issue-wide
        // would make one corrected language freeze all the others -- correct the
        // Danish name in the create dialog and the English one would keep the
        // nominal week forever, while the numbers beside it restamped to the week
        // that actually went out.
        boolean renamedByHand = keepChangedNames
                && issue.getDescs().stream().noneMatch(PublicationIssueDesc::isNameOverridden)
                && wasRenamedByHand(issue);
        for (PublicationIssueDesc desc : issue.getDescs()) {
            if (desc.isNameOverridden() || renamedByHand) {
                continue;
            }
            String suggested = IssueLifecycleService.suggestName(series, desc.getLang(), numbers);
            if (suggested != null && !suggested.isBlank()) {
                desc.setName(suggested);
            }
        }
    }

    // ------------------------------------------------------------------ edition

    /**
     * The edition a FRESH period's issue carries.
     *
     * ONE for every series, because the first issue of a period IS its first
     * edition -- and a period that has only ever had one edition should say so
     * rather than say nothing. The column was write-once at import: 1,048 of the
     * 1,077 archived rows carry it because the previous system defaulted it on
     * every create, while every issue created here was born with no edition at
     * all. A publication whose file-name pattern names the edition then published
     * as "EfS-A-v-2027.pdf": the token expanded to the empty string, which is not
     * an unresolved token, so nothing refused it.
     *
     * The draft proposes this and the create writes it, from here, so the number
     * on the form is the number that lands.
     */
    public static final Integer FIRST_EDITION = 1;

    /** The same, as the free text the column stores. */
    public static final String FIRST_EDITION_TEXT = String.valueOf(FIRST_EDITION);

    /**
     * The edition that SUCCEEDS one: the predecessor's number plus one.
     *
     * The new-edition action exists precisely because two issues share a period,
     * and the edition is the column that tells them apart -- so a successor left
     * with the predecessor's number, or with none, is the one row the action was
     * taken to distinguish showing nothing.
     *
     * A predecessor whose edition is absent or is not a number counts as the
     * first: there is nothing to count from, and the successor is the second
     * edition of that period whatever the first one was called.
     */
    public static String editionAfter(PublicationIssue predecessor) {
        Integer previous = predecessor == null ? null : editionOf(predecessor);
        return String.valueOf(previous == null ? FIRST_EDITION + 1 : previous + 1);
    }

    /** The edition as a number, where somebody wrote one. Free text otherwise. */
    public static Integer editionOf(PublicationIssue issue) {
        String edition = issue.getEdition();
        if (edition == null || !edition.trim().matches("\\d+")) {
            return null;
        }
        try {
            return Integer.valueOf(edition.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean wasRenamedByHand(PublicationIssue issue) {
        if (issue.getId() == null) {
            return false;
        }
        return em.createQuery(
                        "SELECT COUNT(a) FROM IssueAuditEntry a WHERE a.issue = :i AND a.action = :action",
                        Long.class)
                .setParameter("i", issue)
                .setParameter("action", AuditAction.NAME_CHANGED)
                .getSingleResult() > 0;
    }

    /**
     * When the period this issue covers began -- the span a double week is read
     * from.
     *
     * TWO ANSWERS, one question. Where the periods TILE the issue carries its own
     * lower bound and that bound IS the span. Where they do not, the issue has one
     * instant and no window, and the period it covers is nevertheless real: it
     * runs from wherever the previous issue closed. That is the same instant
     * {@link #shapeInterval} anchors the nominal close on, and until now it was
     * computed and thrown away -- which is why a publication that skipped a week
     * had to be renamed by hand, in every language, every time.
     *
     * ONLY FOR A WEEKLY CADENCE, and that is the scope of the rule rather than a
     * limitation of this method. A double week is expressed as a pair of WEEK
     * numbers -- there is no column that could hold "two months" or "two years" --
     * and the multi-period test downstream counts weeks accordingly. Handing it a
     * yearly or monthly predecessor would report an ordinary annual edition as
     * spanning fifty-two periods and renumber it for the week before the one it
     * closes in, in its title, its file name and therefore the link it is cited
     * by. A weekly series is the only one whose period and whose counting unit are
     * the same thing, so a publication on any other cadence carries a single
     * number and nothing is lost: widening this means first counting in the
     * series' own period, which moves the threshold's unit and is a separate
     * question about every publication in the estate.
     */
    private Date spanStart(PublicationIssue issue, PublicationSeries series) {
        if (series == null || series.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL) {
            return issue.getIntervalFrom();
        }
        if (series.getCadence() != SeriesCadence.WEEKLY) {
            return null;
        }
        PublicationIssue predecessor = newestDatedBefore(series, issue, issue.effectiveCutoff());
        return predecessor == null ? null : predecessor.effectiveCutoff();
    }

    // ----------------------------------------------------------------- neighbours

    /**
     * The newest sibling that closed BEFORE this instant, or null.
     *
     * Strictly before, because "the previous issue" is the question: the newest
     * dated sibling of an issue recovered into the middle of an archive is a row
     * years AFTER it, and measuring a span backwards from that would be a
     * negative period rather than a predecessor.
     */
    private PublicationIssue newestDatedBefore(PublicationSeries series, PublicationIssue issue, Date instant) {
        if (instant == null) {
            return null;
        }
        for (PublicationIssue sibling : siblings(series, issue)) {
            Date close = sibling.effectiveCutoff();
            if (close != null && close.before(instant)) {
                return sibling;
            }
        }
        return null;
    }

    /** The newest sibling that has a close to chain off, or null. */
    private PublicationIssue newestDated(PublicationSeries series, PublicationIssue issue) {
        for (PublicationIssue sibling : siblings(series, issue)) {
            if (sibling.effectiveCutoff() != null) {
                return sibling;
            }
        }
        return null;
    }

    /** The sibling whose period closed exactly at this instant, if any. */
    private PublicationIssue closingAt(PublicationSeries series, PublicationIssue issue, Date instant) {
        for (PublicationIssue sibling : siblings(series, issue)) {
            Date close = sibling.effectiveCutoff();
            if (close != null && close.getTime() == instant.getTime()) {
                return sibling;
            }
        }
        return null;
    }

    /**
     * The series' other issues, newest close first -- the order the list shows.
     *
     * The issue being shaped is excluded by identity rather than by id, because it
     * usually has none yet: this runs before the row is persisted, which is what
     * lets a refusal further along leave nothing behind.
     */
    private List<PublicationIssue> siblings(PublicationSeries series, PublicationIssue issue) {
        if (series == null || series.getId() == null) {
            return List.of();
        }
        return em.createNamedQuery("PublicationIssue.findBySeriesNewestFirst", PublicationIssue.class)
                .setParameter("series", series)
                .getResultList()
                .stream()
                .filter(other -> other != issue
                        && (issue.getId() == null || !issue.getId().equals(other.getId())))
                .toList();
    }
}
