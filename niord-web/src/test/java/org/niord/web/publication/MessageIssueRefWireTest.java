package org.niord.web.publication;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.MessageIssueLookup;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.vo.MessageIssueRefVo;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire shape of "which issues is this message in".
 *
 * Two things are pinned here and they are both about what the row MEANS rather
 * than about mapping mechanics.
 *
 * FROZEN versus LIVE survives the mapping. A published issue's membership is a
 * record; an open issue's is a prediction resolved at read time. The frontend
 * renders them differently -- one says the message is out, the other says it is
 * due to go out -- and if the flag were dropped every row would read as the
 * stronger claim.
 *
 * The payload stays scoped. This endpoint is readable by anyone who may open a
 * message, which is a wider audience than series administrators, so the series'
 * naming and file patterns must not ride along on it.
 */
public class MessageIssueRefWireTest {

    // ------------------------------------------------------------- builders

    private static PublicationSeries series() {
        return series("dma-efs");
    }

    private static PublicationSeries series(String seriesId) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(seriesId);

        PublicationSeriesDesc da = new PublicationSeriesDesc();
        da.setLang("da");
        da.setName("Efterretninger for Sofarende");
        da.setNameSuggestionPattern("EfS uge ${week}, ${year}");
        da.setFileNamePattern("efs-${week}-${year}.pdf");
        da.setMessageReferenceFormat("EfS ${week}/${year}");
        s.getDescs().add(da);
        return s;
    }

    private static PublicationIssue issue(IssueStatus status) {
        return issue(status, series(), "EfS uge 29, 2026", 1_700_000_000_000L);
    }

    private static PublicationIssue issue(IssueStatus status, PublicationSeries series,
                                          String name, long intervalFrom) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setStatus(status);
        i.setIntervalFrom(new Date(intervalFrom));
        i.setPublishedAt(status == IssueStatus.PUBLISHED ? new Date(intervalFrom + 500_000_000L) : null);

        PublicationIssueDesc da = new PublicationIssueDesc();
        da.setLang("da");
        da.setName(name);
        da.setLink("/publications/efs-29-2026.pdf");
        i.getDescs().add(da);
        return i;
    }

    private static MessageIssueLookup.MessageIssue frozen(PublicationIssue issue) {
        return new MessageIssueLookup.MessageIssue(issue, MessageIssueLookup.Membership.FROZEN);
    }

    private static MessageIssueLookup.MessageIssue live(PublicationIssue issue) {
        return new MessageIssueLookup.MessageIssue(issue, MessageIssueLookup.Membership.LIVE);
    }

    // ------------------------------------------------------------ assertions

    /** A published issue's row says the message is already out. */
    @Test
    public void afrozenRowKeepsItsBasis() {
        MessageIssueRefVo vo = PublicationIssueRestService.refOf(new MessageIssueLookup.MessageIssue(
                issue(IssueStatus.PUBLISHED), MessageIssueLookup.Membership.FROZEN));

        assertEquals(MessageIssueRefVo.Membership.FROZEN, vo.getMembership());
        assertEquals(IssueStatus.PUBLISHED, vo.getStatus());
        assertEquals("dma-efs", vo.getSeriesId());
        assertEquals("EfS uge 29, 2026", vo.getNames().get("da"));
        assertEquals("Efterretninger for Sofarende", vo.getSeriesNames().get("da"));
        assertEquals("/publications/efs-29-2026.pdf", vo.getLinks().get("da"));
    }

    /**
     * An open issue's row says the message is DUE to go out, not that it has.
     *
     * The status and the membership carry different halves of that and both are
     * needed: a row could in principle be FROZEN on an issue that was reopened,
     * so the frontend reads the basis rather than inferring it from the status.
     */
    @Test
    public void aliveRowIsMarkedAsAPrediction() {
        MessageIssueRefVo vo = PublicationIssueRestService.refOf(new MessageIssueLookup.MessageIssue(
                issue(IssueStatus.OPEN), MessageIssueLookup.Membership.LIVE));

        assertEquals(MessageIssueRefVo.Membership.LIVE, vo.getMembership());
        assertEquals(IssueStatus.OPEN, vo.getStatus());
        assertNull(vo.getPublishedAt(), "an open issue has not been published");
    }

    /**
     * The series' editing surface does not travel to the message editor.
     *
     * Asserted against the serialized JSON rather than against absent getters,
     * because the failure this guards against is somebody widening the VO to
     * reuse PublicationSeriesDescVo -- which would compile, pass every mapping
     * assertion above, and quietly ship the patterns.
     */
    @Test
    public void thePayloadCarriesNoSeriesEditingSurface() throws Exception {
        MessageIssueRefVo vo = PublicationIssueRestService.refOf(new MessageIssueLookup.MessageIssue(
                issue(IssueStatus.PUBLISHED), MessageIssueLookup.Membership.FROZEN));

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(vo);

        assertTrue(json.contains("EfS uge 29, 2026"), "the issue name is the point of the row");
        for (String leaked : new String[] {
                "nameSuggestionPattern", "fileNamePattern", "messageReferenceFormat", "linkPattern" }) {
            assertEquals(-1, json.indexOf(leaked),
                    leaked + " belongs to the series editor, not to a message screen");
        }
    }

    // -------------------------------------------------------------- grouping

    /**
     * A message re-listed in every edition of one series is ONE row, not a hundred.
     *
     * This is the shape the estate actually has: an IN_FORCE_AT_CUTOFF series
     * lists every message still in force in every weekly edition, so a notice in
     * force for two years is a member of a hundred-odd issues of it. Ungrouped,
     * those hundred rows push the single EfS row an editor came to find off the
     * bottom of the panel, and the response carries a hundred near-identical
     * objects to achieve it.
     */
    @Test
    public void manyIssuesOfOneSeriesCollapseToOneCountedRow() {
        PublicationSeries pt = series("dma-pt");
        var rows = PublicationIssueRestService.group(List.of(
                frozen(issue(IssueStatus.PUBLISHED, pt, "P&T uge 27", 1_700_000_000_000L)),
                frozen(issue(IssueStatus.PUBLISHED, pt, "P&T uge 29", 1_701_200_000_000L)),
                frozen(issue(IssueStatus.PUBLISHED, pt, "P&T uge 28", 1_700_600_000_000L))));

        assertEquals(1, rows.size());
        assertEquals(3, rows.get(0).getIssueCount());
        assertEquals("P&T uge 29", rows.get(0).getNames().get("da"),
                "the group is represented by its LATEST issue -- 'has this gone out' is a "
                        + "question about the most recent edition");
    }

    /**
     * Published-in-one-issue and pending-in-another do not merge.
     *
     * This is the normal state of a weekly series, not an edge case: the message
     * went out in last week's issue and is due again in this week's. Merging on
     * the series alone would report one of the two and silently drop the other --
     * and which one it dropped would depend on the order the rows arrived in.
     */
    @Test
    public void thetwoBasesOfOneSeriesStayApart() {
        PublicationSeries efs = series("dma-efs");
        var rows = PublicationIssueRestService.group(List.of(
                frozen(issue(IssueStatus.PUBLISHED, efs, "EfS uge 28", 1_700_000_000_000L)),
                live(issue(IssueStatus.OPEN, efs, "EfS uge 29", 1_700_600_000_000L))));

        assertEquals(2, rows.size());
        assertEquals(MessageIssueRefVo.Membership.FROZEN, rows.get(0).getMembership());
        assertEquals(MessageIssueRefVo.Membership.LIVE, rows.get(1).getMembership());
        assertEquals(1, rows.get(0).getIssueCount());
        assertEquals(1, rows.get(1).getIssueCount());
    }

    /** Different series stay separate rows, in the order the lookup produced them. */
    @Test
    public void separateSeriesRemainSeparateRows() {
        var rows = PublicationIssueRestService.group(List.of(
                frozen(issue(IssueStatus.PUBLISHED, series("dma-efs"), "EfS uge 29", 1_700_000_000_000L)),
                frozen(issue(IssueStatus.PUBLISHED, series("dma-pt"), "P&T uge 29", 1_700_000_000_000L))));

        assertEquals(List.of("dma-efs", "dma-pt"),
                rows.stream().map(MessageIssueRefVo::getSeriesId).toList());
    }

    /**
     * Issues with no series do not all collapse into one row.
     *
     * A null seriesId is not a shared identity. Keying on it would merge every
     * seriesless issue into a single row claiming a count it never earned.
     */
    @Test
    public void seriesLessIssuesDoNotMergeWithEachOther() {
        PublicationIssue a = issue(IssueStatus.PUBLISHED, null, "Bilag A", 1_700_000_000_000L);
        PublicationIssue b = issue(IssueStatus.PUBLISHED, null, "Bilag B", 1_700_600_000_000L);
        a.setPublicId("annex-a");
        b.setPublicId("annex-b");

        var rows = PublicationIssueRestService.group(List.of(frozen(a), frozen(b)));

        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).getIssueCount());
        assertEquals(1, rows.get(1).getIssueCount());
    }

    /** An issue with no interval never wins the group over one that has a date. */
    @Test
    public void anUndatedIssueDoesNotWinTheGroup() {
        PublicationSeries efs = series("dma-efs");
        PublicationIssue undated = issue(IssueStatus.PUBLISHED, efs, "Udateret", 1_700_000_000_000L);
        undated.setIntervalFrom(null);

        var rows = PublicationIssueRestService.group(List.of(
                frozen(issue(IssueStatus.PUBLISHED, efs, "EfS uge 29", 1_700_600_000_000L)),
                frozen(undated)));

        assertEquals(1, rows.size());
        assertEquals("EfS uge 29", rows.get(0).getNames().get("da"));
        assertEquals(2, rows.get(0).getIssueCount(), "it still counts, it just does not represent");
    }

    /** A series that was never loaded leaves the row without one, rather than failing. */
    @Test
    public void anIssueWithNoSeriesStillMaps() {
        PublicationIssue orphan = issue(IssueStatus.OPEN);
        orphan.setSeries(null);

        MessageIssueRefVo vo = PublicationIssueRestService.refOf(
                new MessageIssueLookup.MessageIssue(orphan, MessageIssueLookup.Membership.LIVE));

        assertNull(vo.getSeriesId());
        assertTrue(vo.getSeriesNames().isEmpty());
        assertEquals("EfS uge 29, 2026", vo.getNames().get("da"));
    }
}
