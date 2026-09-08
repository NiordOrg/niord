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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueListResultVo;
import org.niord.core.publication.series.vo.IssueTimelineRowVo;
import org.niord.core.publication.series.vo.IssueTimelineVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's recent-periods strip.
 *
 * The one property worth more than all the others here is that the strip and the
 * issue list agree. They answer the same question on two screens -- which periods
 * this series has an issue for -- and the failure mode of building them
 * separately is not a crash: it is a list offering "create the missing week 12"
 * beside a strip showing week 12 as present, with nothing to say which is right.
 * So the comparison against the list is asserted directly rather than left to
 * both sides being individually plausible.
 *
 * No Quarkus and no database. Everything here is about what the rows SAY, and a
 * test that needs MySQL to say it stops running on a build machine that has none.
 */
public class IssueTimelineTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** A Wednesday noon, by ISO week of 2026. */
    private static Date wed(int isoWeek) {
        return Date.from(ZonedDateTime.of(2026, 1, 7, 12, 0, 0, 0, ZoneId.of("UTC"))
                .plusWeeks(isoWeek - 2L).toInstant());
    }

    private static PublicationSeries series(SeriesStatus status, SeriesCadence cadence,
                                            TimeRelation relation) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("weekly-ntm");
        s.setStatus(status);
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        PublicationSeriesDesc desc = s.createDesc("en");
        desc.setNameSuggestionPattern("NtM Week ${week} - ${year}");
        desc.setFileNamePattern("ntm-${year}-${week}.pdf");
        return s;
    }

    private static PublicationIssue published(PublicationSeries series, String publicId, Date cutoff) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setPublicId(publicId);
        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(cutoff);
        i.setIntervalTo(cutoff);
        i.setIntervalToSource(IntervalBoundSource.STAMPED);
        i.setMemberCount(7);
        i.createDesc("en").setName("NtM Week " + publicId);
        return i;
    }

    private static List<PublicationIssue> newestFirst(PublicationIssue... issues) {
        List<PublicationIssue> out = new ArrayList<>(List.of(issues));
        out.sort((a, b) -> b.effectiveCutoff().compareTo(a.effectiveCutoff()));
        return out;
    }

    // ------------------------------------------------- the property it exists for

    /**
     * The strip's missing cells ARE the list's missing rows.
     *
     * Same periods, same bounds, from the same synthesizer. If this ever fails,
     * one of the two screens is lying about the archive and no amount of testing
     * either of them alone would show it.
     */
    @Test
    public void theStripAndTheIssueListReportTheSameMissingPeriods() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        List<PublicationIssue> issues = newestFirst(
                published(s, "a", wed(10)), published(s, "b", wed(14)));

        IssueListResultVo list = IssueListService.build(s, issues, wed(15));
        IssueTimelineVo strip = IssueListService.buildRecent(s, issues, 52, wed(15), "en");

        Set<Long> listMissing = new LinkedHashSet<>();
        list.getData().stream()
                .filter(r -> "MISSING".equals(r.getComputedStatus()))
                .forEach(r -> listMissing.add(r.getIntervalTo().getTime()));

        Set<Long> stripMissing = new LinkedHashSet<>();
        strip.getRows().stream()
                .filter(r -> "MISSING".equals(r.getComputedStatus()))
                .forEach(r -> stripMissing.add(r.getIntervalTo().getTime()));

        assertFalse(listMissing.isEmpty(), "the fixture is missing weeks 11 to 13; the list found none");
        assertEquals(listMissing, stripMissing,
                "the strip and the list disagree about which periods are missing. They are one fact "
                        + "produced by one synthesizer, and two answers means a second one crept in");
        assertEquals(list.getGapDetection().getReasonCode(), strip.getGapDetection().getReasonCode());
    }

    // ------------------------------------------------------ a withdrawn period

    /**
     * A retired week is BOTH: still its own row, and a period that is missing again.
     *
     * That pair is how a wrong published issue gets corrected. An issue carried
     * over from the previous system can never be amended, so the remedy is to
     * withdraw it and publish a new issue for the same period -- and the affordance
     * that creates one lives on a MISSING row. The withdrawn issue itself changes
     * in no way: it keeps its file, its window and every citation of it, and it
     * stays in the list as a RETIRED row so the record of what went out survives.
     *
     * Asserted on the list AND the strip, because they are the two screens an admin
     * reaches for after retiring something and they run off one synthesizer.
     */
    @Test
    public void aRetiredWeekStaysARowAndItsPeriodComesBackAsMissing() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        List<PublicationIssue> issues = newestFirst(
                week(s, "w33", 33), retiredWeek(s, "w34", 34), week(s, "w35", 35));

        IssueListResultVo list = IssueListService.build(s, issues, wed(35));
        IssueTimelineVo strip = IssueListService.buildRecent(s, issues, 52, wed(35), "en");

        SystemPublicationIssueVo withdrawn = list.getData().stream()
                .filter(r -> "w34".equals(r.getPublicId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the retired issue left the list; retiring withdraws a document from the "
                                + "workflow, it does not unmake it"));
        assertEquals("RETIRED", withdrawn.getComputedStatus());
        assertNull(withdrawn.getPseudo(), "the retired issue is a real row, not a synthesized one");

        List<SystemPublicationIssueVo> missing = list.getData().stream()
                .filter(r -> "MISSING".equals(r.getComputedStatus()))
                .toList();
        assertEquals(1, missing.size(),
                "the withdrawn week is still counted as covered, so there is no row to "
                        + "retro-create its replacement from");
        assertEquals(wed(33), missing.get(0).getIntervalFrom(),
                "the recovered period must open where the week before it closed");
        assertEquals(wed(34), missing.get(0).getIntervalTo(),
                "and close where the week after it opened -- the retired issue's own period");
        assertNull(missing.get(0).getPublicId(), "a synthesized row has no entity behind it");
        assertEquals("w33", missing.get(0).getPrecedingPublicId());
        assertEquals("w35", missing.get(0).getFollowingPublicId(),
                "the row chains between the issues that still cover their periods");
        assertEquals(Integer.valueOf(1), list.getGapCount());

        IssueTimelineRowVo cell = strip.getRows().stream()
                .filter(r -> "w34".equals(r.getPublicId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the retired issue has no cell on the strip"));
        assertEquals("RETIRED", cell.getComputedStatus());
        List<IssueTimelineRowVo> stripMissing = strip.getRows().stream()
                .filter(r -> "MISSING".equals(r.getComputedStatus()))
                .toList();
        assertEquals(1, stripMissing.size(),
                "the strip and the list disagree about the withdrawn week");
        assertEquals(wed(34), stripMissing.get(0).getIntervalTo());
    }

    /** The control: with nothing withdrawn, the same three weeks have no gap. */
    @Test
    public void aPublishedWeekBetweenTwoOthersIsNotMissing() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        IssueListResultVo list = IssueListService.build(s,
                newestFirst(week(s, "w33", 33), week(s, "w34", 34), week(s, "w35", 35)), wed(35));

        assertEquals(Integer.valueOf(0), list.getGapCount(), "a tidy chain reported a gap");
    }

    /**
     * Retiring the NEWEST issue leaves its period missing too.
     *
     * The forward pass anchors on the newest issue that still covers its period,
     * so withdrawing the head of the chain moves the anchor back a week and the
     * withdrawn period is tiled like any other overdue one. Anchored on the
     * retired row instead, the head of the list would show nothing missing -- which
     * is exactly where somebody who has just retired an issue is looking.
     */
    @Test
    public void retiringTheNewestIssueLeavesItsOwnPeriodMissing() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        IssueListResultVo list = IssueListService.build(s,
                newestFirst(week(s, "w33", 33), week(s, "w34", 34), retiredWeek(s, "w35", 35)),
                new Date(wed(35).getTime() + 3600_000L));

        List<SystemPublicationIssueVo> missing = list.getData().stream()
                .filter(r -> "MISSING".equals(r.getComputedStatus()))
                .toList();
        assertEquals(1, missing.size(), "the withdrawn head week is still counted as covered");
        assertEquals(wed(34), missing.get(0).getIntervalFrom());
        assertEquals(wed(35), missing.get(0).getIntervalTo());
        assertEquals("w34", missing.get(0).getPrecedingPublicId());
    }

    /**
     * A synthesized cell carries NO publicId, and its absence is what identifies it.
     *
     * Asserted on the JSON rather than the getter: a null field and an absent key
     * look identical from Java and completely different from a client, and the
     * client is the one that has to tell a period from an issue.
     */
    @Test
    public void aSynthesizedCellHasNoPublicIdOnTheWire() throws Exception {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        IssueTimelineVo strip = IssueListService.buildRecent(s,
                newestFirst(published(s, "a", wed(10)), published(s, "b", wed(14))),
                52, wed(15), "en");

        JsonNode json = JSON.readTree(JSON.writeValueAsString(strip));
        boolean sawSynthesized = false;
        for (JsonNode row : json.get("rows")) {
            if ("MISSING".equals(row.get("computedStatus").asText())) {
                sawSynthesized = true;
                assertFalse(row.has("publicId"),
                        "a synthesized cell carries a publicId; a client cannot then tell a period "
                                + "nobody published from an issue somebody did");
                assertFalse(row.has("memberCount"),
                        "a period nobody published reported a member count. Zero here reads as "
                                + "\"resolved, and empty\", which is a different claim entirely");
            }
        }
        assertTrue(sawSynthesized, "no synthesized cell in a fixture that is missing three weeks");
    }

    /**
     * A cell is named after the week its period CLOSES in.
     *
     * Every weekly period runs Wednesday to Wednesday and therefore spans two ISO
     * weeks. Deriving the name from the period's START returns the double-week
     * form -- "week 11+12" -- for every gap there has ever been, where production
     * names an issue after the week it closed in.
     */
    @Test
    public void cellsAreNamedAfterTheWeekThePeriodCloses() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        IssueTimelineVo strip = IssueListService.buildRecent(s,
                newestFirst(published(s, "a", wed(10)), published(s, "b", wed(14))),
                52, wed(15), "en");

        IssueTimelineRowVo first = strip.getRows().stream()
                .filter(r -> "MISSING".equals(r.getComputedStatus()))
                .reduce((a, b) -> b)
                .orElseThrow();

        assertNotNull(first.getLabel());
        assertFalse(first.getLabel().contains("+"),
                "a single missing week was labelled as a double week (" + first.getLabel() + "), "
                        + "which is what deriving the name from the period's start produces");
        assertEquals(first.getWeek().intValue(),
                Integer.parseInt(first.getLabel().replaceAll("\\D+", "").substring(0, 2)),
                "the label and the week field describe different weeks");
    }

    /**
     * A cadence-less series returns its issues and synthesizes nothing.
     *
     * There is no period for it to be missing one of, and the gate says so under
     * its own name rather than by returning an empty list a caller would read as
     * "nothing wrong here".
     */
    @Test
    public void aCadencelessSeriesGetsItsIssuesAndNoSynthesizedCells() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.NONE, null);
        IssueTimelineVo strip = IssueListService.buildRecent(s,
                newestFirst(published(s, "a", wed(10)), published(s, "b", wed(14))),
                8, wed(20), "en");

        assertEquals(2, strip.getRows().size());
        for (IssueTimelineRowVo row : strip.getRows()) {
            assertNotNull(row.getPublicId(), "a cadence-less series synthesized a cell");
            assertEquals("PUBLISHED", row.getComputedStatus());
        }
        assertFalse(strip.getGapDetection().isEnabled());
        assertEquals("NO_CADENCE", strip.getGapDetection().getReasonCode());
        // Newest first: the strip reads right to left from the current period.
        assertEquals("b", strip.getRows().get(0).getPublicId());
    }

    /**
     * An IN_FORCE_AT_CUTOFF series synthesizes nothing either, and for a different
     * reason: its issues OVERLAP rather than tile, so a "missing year" between two
     * editions sharing thirty-one of thirty-two members is a category error.
     */
    @Test
    public void anOverlappingSeriesSynthesizesNoCells() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.YEARLY,
                TimeRelation.IN_FORCE_AT_CUTOFF);
        IssueTimelineVo strip = IssueListService.buildRecent(s,
                newestFirst(published(s, "a", wed(2)), published(s, "b", wed(40))),
                8, wed(45), "en");

        for (IssueTimelineRowVo row : strip.getRows()) {
            assertNotNull(row.getPublicId(),
                    "an overlapping series produced a MISSING cell for a period that was never absent");
        }
        assertEquals("RELATION_NOT_TILING", strip.getGapDetection().getReasonCode());
    }

    /** The strip is capped at the periods asked for, newest end kept. */
    @Test
    public void theStripIsCappedAtTheRequestedNumberOfPeriods() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        IssueTimelineVo strip = IssueListService.buildRecent(s,
                newestFirst(published(s, "a", wed(2)), published(s, "b", wed(20))),
                4, wed(21), "en");

        assertEquals(4, strip.getRows().size());
        // Newest first, so the cap drops the OLD end. A strip that kept the oldest
        // four periods would show an archive and call it recent.
        assertTrue(strip.getRows().get(0).getIntervalTo().getTime()
                        >= strip.getRows().get(3).getIntervalTo().getTime(),
                "the strip is not newest-first");
        assertTrue(strip.getRows().get(3).getIntervalTo().getTime() > wed(15).getTime(),
                "the cap kept the oldest periods rather than the most recent ones");
    }

    /**
     * A real row is called what the issue is called.
     *
     * An admin may have renamed an issue, and the strip sitting beside the list
     * has to agree with it. Only a cell with no issue behind it is named by
     * derivation.
     */
    @Test
    public void aRealCellCarriesTheIssuesOwnName() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = published(s, "a", wed(10));
        issue.getDescs().get(0).setName("EfS uge 10 (rettet)");

        IssueTimelineRowVo row = rowFor(
                IssueListService.buildRecent(s, List.of(issue), 8, wed(10), "en"), "a");

        assertEquals("EfS uge 10 (rettet)", row.getLabel());
        assertEquals(Integer.valueOf(7), row.getMemberCount());
    }

    /**
     * A publication with no membership semantics reports NO member count.
     *
     * Zero would say the query ran and selected nothing, and for a PDF or a link
     * nothing ever ran at all -- which is the distinction the provenance column
     * exists to keep.
     */
    @Test
    public void aPublicationWithNoMembershipReportsNoCount() {
        PublicationSeries s = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = published(s, "a", wed(10));
        issue.setMembershipProvenance(MembershipProvenance.NO_MEMBERSHIP);

        assertNull(rowFor(IssueListService.buildRecent(s, List.of(issue), 8, wed(10), "en"), "a")
                .getMemberCount());
    }

    private static IssueTimelineRowVo rowFor(IssueTimelineVo strip, String publicId) {
        return strip.getRows().stream()
                .filter(r -> publicId.equals(r.getPublicId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no cell for issue " + publicId));
    }

    // ------------------------------------------------- the bounded read is the same read

    /**
     * The newest few issues draw the same strip as the whole archive.
     *
     * `recent()` used to select every issue a series has ever had -- about five
     * hundred on weekly-ntm, each with its desc rows -- and then throw all but
     * eight cells away, at roughly 0.2 s per series on a dashboard that draws one
     * strip per series a desk owns. It now reads the newest `periods + 2` and
     * hands those to the same builder.
     *
     * This is the assertion that makes the bound safe, and there was nothing else:
     * every existing test here runs the STATIC builder over a list the caller
     * supplied, so the query has never been pinned in either module. An off-by-one
     * in the window silently drops MISSING cells from the dashboard, and nothing
     * anywhere would fail.
     *
     * Pure, and deliberately so -- it must run on a build machine with no MySQL,
     * which is where a regression in this bound would otherwise land unnoticed.
     */
    @Test
    public void theNewestFewIssuesDrawTheSameStripAsTheWholeArchive() {
        // A long tidy weekly archive: the ordinary case, and the one whose cost
        // the bound exists to remove.
        PublicationSeries tidy = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        List<PublicationIssue> weekly = new ArrayList<>();
        for (int week = 1; week <= 34; week++) {
            weekly.add(chained(tidy, "w" + week, wed(week), wed(week + 1)));
        }
        assertWindowDrawsTheSameStrip("a tidy weekly archive", tidy, newestFirst(weekly), 8, wed(36));

        // THE CASE THE PREDECESSOR EXISTS FOR: the whole window is filled by cells
        // produced by ONE pair, and the older member of that pair is twenty-eight
        // weeks back. A window that kept only the periods it draws would lose it
        // and seven of the eight cells with it.
        PublicationSeries wide = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        List<PublicationIssue> sparse = new ArrayList<>();
        for (int week = 1; week <= 10; week++) {
            sparse.add(chained(wide, "g" + week, wed(week), wed(week + 1)));
        }
        sparse.add(chained(wide, "resumed", wed(39), wed(40)));
        assertWindowDrawsTheSameStrip("a twenty-eight-week gap", wide, newestFirst(sparse), 8, wed(40));

        // A double-week issue at the window edge: it opened where its predecessor
        // closed and carried two periods, which is not a gap. The window must not
        // turn it into one by losing the issue its interval chains from.
        PublicationSeries doubled = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        List<PublicationIssue> chain = new ArrayList<>();
        for (int week = 1; week <= 14; week++) {
            chain.add(chained(doubled, "d" + week, wed(week), wed(week + 1)));
        }
        chain.add(chained(doubled, "double", wed(15), wed(17)));
        assertWindowDrawsTheSameStrip("a double week at the window edge", doubled,
                newestFirst(chain), 8, wed(17));

        // An OPEN newest issue: the forward pass stays suppressed, because the
        // period being worked toward is late rather than missing.
        PublicationSeries working = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        List<PublicationIssue> withOpen = new ArrayList<>();
        for (int week = 1; week <= 20; week++) {
            withOpen.add(chained(working, "o" + week, wed(week), wed(week + 1)));
        }
        withOpen.add(open(working, "current", wed(21), wed(22)));
        assertWindowDrawsTheSameStrip("an open newest issue", working,
                newestFirst(withOpen), 8, wed(22));

        // A dormant series: the gate is closed, so there are no pseudo rows at all
        // and the strip is the issues themselves.
        PublicationSeries dormant = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);
        List<PublicationIssue> stopped = new ArrayList<>();
        for (int week = 1; week <= 15; week++) {
            stopped.add(chained(dormant, "s" + week, wed(week), wed(week + 1)));
        }
        assertWindowDrawsTheSameStrip("a dormant series", dormant, newestFirst(stopped), 8, wed(90));

        // A cadence-less series at ten periods: no synthesis, and the strip is
        // simply its newest issues -- the form the unscheduled cards read.
        PublicationSeries unscheduled = series(SeriesStatus.ACTIVE, SeriesCadence.NONE, null);
        List<PublicationIssue> editions = new ArrayList<>();
        for (int week = 1; week <= 14; week++) {
            editions.add(published(unscheduled, "e" + week, wed(week * 2)));
        }
        assertWindowDrawsTheSameStrip("a cadence-less archive", unscheduled,
                newestFirst(editions), 10, wed(40));
    }

    /**
     * The bounded list and the whole archive produce the same cells, one for one.
     *
     * Compared on every field a cell carries, not on the count: a window that lost
     * a predecessor produces the right NUMBER of cells and the wrong ones, because
     * the synthesizer fills the space with periods it thinks nobody covered.
     */
    private static void assertWindowDrawsTheSameStrip(String fixture, PublicationSeries series,
                                                      List<PublicationIssue> newestFirst,
                                                      int periods, Date now) {
        int window = Math.min(newestFirst.size(), periods + IssueListService.TIMELINE_WINDOW_SLACK);
        // Every fixture here must actually be truncated by the window, or the
        // comparison below is a list against itself and proves nothing.
        assertTrue(window < newestFirst.size(),
                fixture + ": the window (" + window + ") covers the whole fixture ("
                        + newestFirst.size() + " issues), so this comparison is vacuous");
        IssueTimelineVo whole = IssueListService.buildRecent(series, newestFirst, periods, now, "en");
        IssueTimelineVo bounded = IssueListService.buildRecent(series,
                newestFirst.subList(0, window), periods, now, "en");

        assertEquals(whole.getRows().size(), bounded.getRows().size(),
                fixture + ": the bounded read drew a different number of cells. The window is "
                        + window + " issues of " + newestFirst.size());
        assertEquals(whole.getGapDetection().getReasonCode(), bounded.getGapDetection().getReasonCode(),
                fixture + ": the bounded read reported a different gap-detection gate");

        for (int i = 0; i < whole.getRows().size(); i++) {
            IssueTimelineRowVo a = whole.getRows().get(i);
            IssueTimelineRowVo b = bounded.getRows().get(i);
            String at = fixture + ": cell " + i + " differs";
            assertEquals(a.getPublicId(), b.getPublicId(), at + " in publicId");
            assertEquals(a.getComputedStatus(), b.getComputedStatus(), at + " in computedStatus");
            assertEquals(a.getLabel(), b.getLabel(), at + " in label");
            assertEquals(a.getIntervalFrom(), b.getIntervalFrom(), at + " in intervalFrom");
            assertEquals(a.getIntervalTo(), b.getIntervalTo(), at + " in intervalTo");
            assertEquals(a.getMemberCount(), b.getMemberCount(), at + " in memberCount");
            assertEquals(a.getWeek(), b.getWeek(), at + " in week");
            assertEquals(a.getYear(), b.getYear(), at + " in year");
        }
    }

    /** A released issue whose content period opened where the previous one closed. */
    private static PublicationIssue chained(PublicationSeries series, String publicId,
                                            Date openedAt, Date cutoff) {
        PublicationIssue i = published(series, publicId, cutoff);
        i.setIntervalFrom(openedAt);
        return i;
    }

    /** A released weekly issue closing in the named ISO week. */
    private static PublicationIssue week(PublicationSeries series, String publicId, int isoWeek) {
        return chained(series, publicId, wed(isoWeek - 1), wed(isoWeek));
    }

    /** The same week, withdrawn: still a row, no longer coverage. */
    private static PublicationIssue retiredWeek(PublicationSeries series, String publicId, int isoWeek) {
        PublicationIssue i = week(series, publicId, isoWeek);
        i.setStatus(IssueStatus.RETIRED);
        return i;
    }

    /** The issue being worked on: no stamp, and the nominal close it is working toward. */
    private static PublicationIssue open(PublicationSeries series, String publicId,
                                         Date openedAt, Date nominalClose) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setPublicId(publicId);
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(openedAt);
        i.setIntervalTo(nominalClose);
        i.setIntervalToSource(IntervalBoundSource.NOMINAL);
        i.createDesc("en").setName("NtM Week " + publicId);
        return i;
    }

    private static List<PublicationIssue> newestFirst(List<PublicationIssue> issues) {
        return newestFirst(issues.toArray(new PublicationIssue[0]));
    }
}
