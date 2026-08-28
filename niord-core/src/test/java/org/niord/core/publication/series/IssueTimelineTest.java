package org.niord.core.publication.series;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueListResultVo;
import org.niord.core.publication.series.vo.IssueTimelineRowVo;
import org.niord.core.publication.series.vo.IssueTimelineVo;

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
}
