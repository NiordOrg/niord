package org.niord.core.publication.series;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueListResultVo;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the issue list actually puts on the wire.
 *
 * Asserted on SERIALIZED JSON rather than on the VO, because the distinction the
 * whole envelope exists for is a distinction between an absent key and a present
 * one. A getter returning null and a key that is not there look identical from
 * Java and completely different from a client, and it is the client that has to
 * tell "no gaps" from "nobody looked".
 *
 * No Quarkus and no database: the mapper is configured the same way the running
 * server configures it (the customizer touches only promulgation polymorphism),
 * so a plain ObjectMapper is faithful here and the assertions keep running on a
 * build machine with no MySQL.
 */
public class IssueListWireTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");
    private static final long WEEK = 7L * 24 * 3600_000L;

    /** A Wednesday noon in 2026, by ISO week. */
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
        s.setNominalCutoffTimeZone("Europe/Copenhagen");
        PublicationSeriesDesc desc = s.createDesc("en");
        desc.setNameSuggestionPattern("NtM Week ${week} - ${year}");
        desc.setFileNamePattern("ntm-${year}-${week}.pdf");
        return s;
    }

    /** A published issue whose cut-off was stamped at that Wednesday. */
    private static PublicationIssue published(PublicationSeries series, String publicId, Date cutoff) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setPublicId(publicId);
        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(cutoff);
        i.setIntervalTo(cutoff);
        i.setIntervalToSource(IntervalBoundSource.STAMPED);
        return i;
    }

    /** Newest first, as the query returns them and as build() expects them. */
    private static List<PublicationIssue> newestFirst(PublicationIssue... issues) {
        List<PublicationIssue> out = new ArrayList<>(List.of(issues));
        out.sort((a, b) -> b.effectiveCutoff().compareTo(a.effectiveCutoff()));
        return out;
    }

    private static JsonNode wire(IssueListResultVo result) throws Exception {
        return JSON.readTree(JSON.writeValueAsString(result));
    }

    // ------------------------------------------------- the pair this exists for

    /**
     * A DRAFT series says detection did not run. It does NOT say zero gaps.
     *
     * This is the branch that runs for the entire imported estate -- every one of
     * the twenty series lands as DRAFT -- so it is not an edge case, it is the
     * default view of the archive on the day of cutover. A gapCount of 0 here
     * would render as a clean bill of health for an archive nothing examined,
     * which is exactly the reassurance nobody is entitled to.
     */
    @Test
    public void aDraftSeriesReportsThatGapDetectionDidNotRunRatherThanZeroGaps() throws Exception {
        PublicationSeries draft = series(SeriesStatus.DRAFT, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);

        JsonNode json = wire(IssueListService.build(draft,
                newestFirst(published(draft, "a", wed(10)), published(draft, "b", wed(14))),
                wed(15)));

        assertFalse(json.has("gapCount"),
                "gapCount must be ABSENT when the gate is closed. Present as 0 it reads as "
                        + "\"every period was checked and none was missing\", and nothing was checked");
        assertTrue(json.get("gapDetection").get("enabled").isBoolean());
        assertFalse(json.get("gapDetection").get("enabled").asBoolean());
        assertEquals("SERIES_NOT_ACTIVE", json.get("gapDetection").get("reasonCode").asText(),
                "the reason is a code a UI can translate, not a sentence it has to match on");
        assertFalse(json.get("gapDetection").get("reason").asText().isBlank());

        // And no pseudo-rows leaked past the closed gate: weeks 11 to 13 are
        // plainly absent from that fixture, and a closed gate must not report them.
        for (JsonNode row : json.get("data")) {
            assertFalse(row.has("pseudo"), "a closed gate synthesized a row anyway");
        }
        assertEquals(2, json.get("total").asInt());
    }

    /**
     * An ACTIVE series with nothing missing reports zero gaps, with detection on.
     *
     * The other half of the pair. Zero here is a finding -- somebody looked at
     * every period and every one had an issue -- and it has to be distinguishable
     * from the case above by the JSON alone.
     */
    @Test
    public void aGapFreeActiveSeriesReportsZeroGapsWithDetectionEnabled() throws Exception {
        PublicationSeries active = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);

        // Consecutive weeks, and "now" inside the period after the newest, so the
        // only synthesized row is the UPCOMING one -- which is not a gap.
        JsonNode json = wire(IssueListService.build(active,
                newestFirst(published(active, "a", wed(20)), published(active, "b", wed(21)),
                        published(active, "c", wed(22))),
                new Date(wed(23).getTime() - 3600_000L)));

        assertTrue(json.has("gapCount"), "detection ran, so the count is a real answer and is present");
        assertEquals(0, json.get("gapCount").asInt());
        assertTrue(json.get("gapDetection").get("enabled").asBoolean());
        assertEquals("TILING_SERIES", json.get("gapDetection").get("reasonCode").asText());

        assertEquals(1, count(json, "UPCOMING"), "the period being worked toward is shown");
        assertEquals(0, count(json, "MISSING"));
        assertEquals(3, json.get("total").asInt(), "total counts real issues, never the pseudo-row");
    }

    // ------------------------------------------------------------ the pseudo-row

    /**
     * A missing week arrives as a full row with no publicId and no gap vocabulary
     * on its neighbours.
     *
     * The absent-versus-null discipline runs both ways: a real row must carry no
     * trace of the pseudo fields, or a client testing for their presence to spot a
     * gap would flag every issue in the list.
     */
    @Test
    public void aMissingWeekIsAFullRowAndRealRowsCarryNoTraceOfIt() throws Exception {
        PublicationSeries active = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);

        JsonNode json = wire(IssueListService.build(active,
                newestFirst(published(active, "before", wed(30)), published(active, "after", wed(32))),
                new Date(wed(33).getTime() - 3600_000L)));

        assertEquals(1, json.get("gapCount").asInt(), "week 31 came and went");

        JsonNode gap = null;
        for (JsonNode row : json.get("data")) {
            if ("MISSING".equals(row.path("pseudo").asText(null))) {
                gap = row;
            } else if (!row.has("pseudo")) {
                assertFalse(row.has("suggestedDescs"));
                assertFalse(row.has("precedingPublicId"));
                assertFalse(row.has("followingPublicId"));
            }
        }

        assertNotNull(gap, "the missing week produced no row");
        assertFalse(gap.has("publicId"),
                "a gap has no entity, so no public id is emitted at all -- every VO here omits its "
                        + "nulls, and a client must not be handed a key it could read as an id");
        assertEquals("MISSING", gap.get("computedStatus").asText());
        assertEquals("before", gap.get("precedingPublicId").asText());
        assertEquals("after", gap.get("followingPublicId").asText());
        assertEquals("weekly-ntm", gap.get("seriesId").asText());
        assertFalse(gap.has("memberCount"),
                "a period nobody published has no membership; 0 would read as \"resolved, empty\"");

        JsonNode suggestion = gap.get("suggestedDescs").get(0);
        assertEquals("en", suggestion.get("lang").asText());
        assertFalse(suggestion.get("name").asText().isBlank());
        assertFalse(suggestion.get("fileName").asText().isBlank());
    }

    /**
     * Real and synthesized rows arrive as ONE descending sequence.
     *
     * They share a sort key space so a client renders one list. If the merge were
     * wrong the gap would appear at the end of the archive rather than between the
     * two issues it sits between, and it would read as an old gap rather than a
     * recent one.
     */
    @Test
    public void theMergedListIsOneSequenceNewestFirst() throws Exception {
        PublicationSeries active = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);

        JsonNode json = wire(IssueListService.build(active,
                newestFirst(published(active, "before", wed(40)), published(active, "after", wed(42))),
                new Date(wed(43).getTime() - 3600_000L)));

        long previous = Long.MAX_VALUE;
        List<String> order = new ArrayList<>();
        for (JsonNode row : json.get("data")) {
            long key = row.get("sortKey").asLong();
            assertTrue(key <= previous, "the merged list is not sorted newest first");
            previous = key;
            order.add(row.path("pseudo").asText(row.path("publicId").asText()));
        }
        assertEquals(List.of("UPCOMING", "after", "MISSING", "before"), order);
    }

    // ------------------------------------------------------------- the other gates

    /**
     * An IN_FORCE_AT_CUTOFF series is refused by name, with the reason a UI can act on.
     *
     * Its issues overlap rather than tile -- the 2026 and 2027 firing-areas issues
     * share 31 of their 32 members -- so a "missing year" between them is a
     * category error, and the row would offer to retro-create something that was
     * never absent.
     */
    @Test
    public void anInForceSeriesIsGatedOffWithTheOverlapAsTheStatedReason() throws Exception {
        PublicationSeries inForce = series(SeriesStatus.ACTIVE, SeriesCadence.YEARLY,
                TimeRelation.IN_FORCE_AT_CUTOFF);

        JsonNode json = wire(IssueListService.build(inForce,
                newestFirst(published(inForce, "y2026", wed(2)), published(inForce, "y2027", wed(54))),
                wed(60)));

        assertFalse(json.has("gapCount"));
        assertEquals("RELATION_NOT_TILING", json.get("gapDetection").get("reasonCode").asText());
        assertEquals(2, json.get("data").size(), "no pseudo-row was synthesized");
    }

    /**
     * A series nobody has published to in months says DORMANT, not "n gaps".
     *
     * Listing every week since as its own MISSING row would bury the one fact that
     * matters, which is that the series stopped.
     */
    @Test
    public void aDormantSeriesSaysSoInsteadOfListingEveryMissedWeek() throws Exception {
        PublicationSeries active = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);

        JsonNode json = wire(IssueListService.build(active,
                newestFirst(published(active, "last", wed(10))),
                new Date(wed(10).getTime() + 10 * WEEK)));

        assertFalse(json.has("gapCount"));
        assertEquals("SERIES_DORMANT", json.get("gapDetection").get("reasonCode").asText());
        assertEquals(1, json.get("data").size());
    }

    /**
     * A cadence-less publication has no period to be missing one of.
     *
     * WITH NO TIME RELATION, which is the shape S-1 actually produces and the
     * reason this assertion used to pass while the running system got it wrong.
     * Passing PUBLISHED_IN_INTERVAL here built a series that cannot exist, and
     * that impossible input was the only one that reached NO_CADENCE: every real
     * cadence-less series has a null relation, so the gate answered it with
     * RELATION_NOT_TILING and an explanation about overlapping issues.
     */
    @Test
    public void aOneOffIsGatedOffBecauseThereIsNoPeriodToMiss() throws Exception {
        PublicationSeries oneOff = series(SeriesStatus.ACTIVE, SeriesCadence.NONE, null);

        JsonNode json = wire(IssueListService.build(oneOff,
                newestFirst(published(oneOff, "only", wed(10))), wed(30)));

        assertFalse(json.has("gapCount"));
        assertEquals("NO_CADENCE", json.get("gapDetection").get("reasonCode").asText());
    }

    /** An empty series still answers the question rather than returning a bare array. */
    @Test
    public void aSeriesWithNoIssuesStillReportsWhetherAnybodyLooked() throws Exception {
        PublicationSeries active = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);

        JsonNode json = wire(IssueListService.build(active, List.of(), wed(10)));

        assertEquals(0, json.get("total").asInt());
        assertEquals(0, json.get("data").size());
        assertNotNull(json.get("gapDetection"), "the gate is reported even when there is nothing to gate");
        assertTrue(json.get("gapDetection").get("enabled").asBoolean());
        // Nothing has been published, so there is no cut-off to count periods from
        // and nothing to call missing -- as opposed to nobody having looked.
        assertEquals(0, json.get("gapCount").asInt());
    }

    // ------------------------------------------------------------------- ordering

    /**
     * An OPEN issue is the newest row of its series, not the oldest.
     *
     * It has no stamped cut-off, so ordering on the stamp alone sorted the issue
     * being worked on below issues published years earlier -- at the bottom of its
     * own archive. The list orders on the effective cut-off instead.
     */
    @Test
    public void theOpenIssueSortsAsTheNewestRowRatherThanTheOldest() {
        PublicationSeries active = series(SeriesStatus.ACTIVE, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL);

        PublicationIssue open = new PublicationIssue();
        open.setSeries(active);
        open.setPublicId("open");
        open.setStatus(IssueStatus.OPEN);
        open.setIntervalTo(wed(51));
        open.setIntervalToSource(IntervalBoundSource.NOMINAL);
        assertNull(open.getCutoffStampedAt(), "the fixture is pointless if it has a stamp");

        IssueListResultVo result = IssueListService.build(active,
                newestFirst(published(active, "older", wed(50)), open),
                new Date(wed(51).getTime() - 3600_000L));

        assertEquals("open", result.getData().get(0).getPublicId());
        assertEquals("OPEN", result.getData().get(0).getComputedStatus());
    }

    private static int count(JsonNode json, String pseudoKind) {
        int n = 0;
        for (JsonNode row : json.get("data")) {
            if (pseudoKind.equals(row.path("pseudo").asText(null))) {
                n++;
            }
        }
        return n;
    }
}
