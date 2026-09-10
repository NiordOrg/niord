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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.report.FmReport;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which report renders THIS edition.
 *
 * The escape hatch six imported `dont-use-` series exist because there was none.
 * An edition that has to be set out differently -- a supplement, a year-end
 * double issue with its own front matter -- required cloning the whole series
 * and publishing one edition from the clone, which fragments the archive it was
 * cloned from.
 *
 * The three assertions that matter are all the SAME one seen from three
 * surfaces: the release rail, the preview and the publish must all be answering
 * about the same report. A rail that reported the series' while the render used
 * the edition's is a rail that passes an issue which then fails, or refuses one
 * that would have worked.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueReportOverrideTest {

    /** 2026-09-02 12:00 UTC -- a Wednesday in ISO week 36 of 2026. */
    private static final long CUTOFF = 1_788_350_400_000L;

    private static final long WEEK = 7L * 24 * 3600 * 1000;

    @AfterEach
    public void restoreTheRenderer() {
        StubIssueRenderService.reset();
    }

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueEditService editService;

    @Inject
    PublishChecklistService checklist;

    @Inject
    IssueAuditService audit;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixture

    /**
     * A real report row, because the edit LOOKS THE ID UP.
     *
     * A report that does not exist fails at step 10 of the publish -- after the
     * cut-off has been stamped and the members frozen -- so a typo is refused in
     * the form instead. Which means a fixture that wants an override has to give
     * it something to find.
     */
    private FmReport report(String reportId) {
        FmReport r = new FmReport();
        r.setReportId(reportId);
        r.setName(reportId);
        r.setTemplatePath("/templates/" + reportId + ".ftl");
        em.persist(r);
        return r;
    }

    private PublicationSeries series(String reportId) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId(reportId);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        PublicationSeriesDesc d = s.createDesc("da");
        d.setName("Test series");
        em.persist(s);
        return s;
    }

    private PublicationIssue issue(PublicationSeries s) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(new Date(CUTOFF - WEEK));
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        PublicationIssueDesc d = i.createDesc("da");
        d.setName("Test issue");
        em.persist(i);
        return i;
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    private static IssueEditService.IssueEdit reportId(String reportId) {
        return new IssueEditService.IssueEdit(null, null, null, null, null, false, null,
                null, null, null, null, reportId);
    }

    private boolean railPasses(PublicationIssue i, String code) {
        return checklist.compute(i, new Date(CUTOFF), false).rows().stream()
                .filter(r -> code.equals(r.code()))
                .findFirst().orElseThrow().passed();
    }

    // -------------------------------------------------------------------- W3

    /** The edition's own report is what the release renders with. */
    @Test
    @Transactional
    public void theeditionsOwnReportIsWhatIsRendered() {
        String own = "own-report-" + TestIds.suffix();
        report(own);
        PublicationIssue i = issue(series("series-report"));
        em.flush();

        editService.update(i, reportId(own), user());
        em.flush();

        publishService.publish(i.getId(), new IssuePublishService.PublishRequest(
                IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(CUTOFF)));
        em.flush();

        assertEquals(own, StubIssueRenderService.lastRequest().reportId(),
                "the release rendered the series' report while the edition named its own");
    }

    /** And the rail asks the same question, so it cannot pass what the publish refuses. */
    @Test
    @Transactional
    public void theRailReadsTheEffectiveReport() {
        String own = "own-report-" + TestIds.suffix();
        report(own);
        // A series with NO report at all: the rail blocks it today, and the whole
        // point of the override is that this edition can still be released.
        PublicationSeries s = series(null);
        PublicationIssue i = issue(s);
        em.flush();

        assertFalse(railPasses(i, "REPORT_CONFIGURED"),
                "the rail passed an issue whose series names no report and which names none itself");

        editService.update(i, reportId(own), user());
        em.flush();

        assertTrue(railPasses(i, "REPORT_CONFIGURED"),
                "the rail still refuses an edition that names its own report, so the one case the "
                        + "override exists for cannot be released at all");
    }

    /** The preview renders from the same resolution rather than from the series. */
    @Test
    @Transactional
    public void thePreviewReadsTheEffectiveReport() {
        String own = "own-report-" + TestIds.suffix();
        report(own);
        PublicationIssue i = issue(series(null));
        em.flush();

        editService.update(i, reportId(own), user());
        em.flush();

        List<IssuePreviewService.Preview> previews = publishService.preview(i.getId());

        assertEquals(1, previews.size());
        assertEquals(own, StubIssueRenderService.lastRequest().reportId());
    }

    /** An id that names no report is refused where somebody is watching. */
    @Test
    @Transactional
    public void anunknownReportIsRefused() {
        PublicationIssue i = issue(series("series-report"));
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, reportId("no-such-report-anywhere"), user()));
        assertEquals(IssueEditService.REPORT_NOT_FOUND, e.code());
    }

    /**
     * The series' own value is not a deviation, and is not stored as one.
     *
     * Recording it would badge the edition "tilpasset" while it renders exactly
     * what its series renders -- the same rule the criteria override already
     * carries.
     */
    @Test
    @Transactional
    public void theseriesOwnValueIsNoOverride() {
        String shared = "shared-report-" + TestIds.suffix();
        report(shared);
        PublicationIssue i = issue(series(shared));
        em.flush();

        editService.update(i, reportId(shared), user());
        em.flush();

        assertNull(i.getReportId(), "an override equal to the series' value was stored as a deviation");
        assertFalse(EffectiveReport.isOverridden(i));
        assertEquals(shared, EffectiveReport.idOf(i, i.getSeries()));
    }

    /** A blank hands the edition back to its series. */
    @Test
    @Transactional
    public void ablankHandsItBackToTheSeries() {
        String own = "own-report-" + TestIds.suffix();
        report(own);
        PublicationIssue i = issue(series("series-report"));
        em.flush();
        editService.update(i, reportId(own), user());
        em.flush();

        editService.update(i, reportId(""), user());
        em.flush();

        assertNull(i.getReportId());
        assertEquals("series-report", EffectiveReport.idOf(i, i.getSeries()));
    }

    /** Absent leaves it alone, so a partial edit cannot silently hand it back. */
    @Test
    @Transactional
    public void anabsentReportIsNotAClear() {
        String own = "own-report-" + TestIds.suffix();
        report(own);
        PublicationIssue i = issue(series("series-report"));
        em.flush();
        editService.update(i, reportId(own), user());
        em.flush();

        editService.update(i, new IssueEditService.IssueEdit(Map.of("da", "Andet navn"),
                null, null, null), user());
        em.flush();

        assertEquals(own, i.getReportId());
    }

    /** Its own action: an edition that came out looking unlike its siblings is a question. */
    @Test
    @Transactional
    public void thetrailRecordsTheReportChange() {
        String own = "own-report-" + TestIds.suffix();
        report(own);
        PublicationIssue i = issue(series("series-report"));
        em.flush();

        editService.update(i, reportId(own), user());
        em.flush();

        IssueAuditEntry entry = audit.forIssue(i).stream()
                .filter(a -> a.getAction() == AuditAction.REPORT_CHANGED)
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) entry.getDetail();
        assertEquals(own, detail.get("to"));
        assertNull(detail.get("from"));
    }

    // ------------------------------------------------------------------- wire

    /** Both ends and the comparison, so the drawer never makes it itself. */
    @Test
    @Transactional
    public void thewireCarriesBothReportsAndTheComparison() {
        String own = "own-report-" + TestIds.suffix();
        report(own);
        PublicationIssue i = issue(series("series-report"));
        em.flush();
        editService.update(i, reportId(own), user());
        em.flush();

        SystemPublicationIssueVo vo = i.toVo(SystemPublicationIssueVo.class);

        assertEquals(own, vo.getReportId());
        assertEquals("series-report", vo.getSeriesReportId());
        assertTrue(vo.isReportOverridden());
    }

    /** The edition's report parameters reach the wire too, beside the series' own. */
    @Test
    @Transactional
    public void thewireCarriesBothParameterMaps() {
        PublicationSeries s = series("series-report");
        s.getReportParams().put("overskrift", "Aktive P&T");
        PublicationIssue i = issue(s);
        em.flush();

        editService.update(i, new IssueEditService.IssueEdit(null, null, null,
                Map.of("overskrift", "Aktive P&T uge 36+37")), user());
        em.flush();

        SystemPublicationIssueVo vo = i.toVo(SystemPublicationIssueVo.class);

        assertEquals("Aktive P&T uge 36+37", vo.getReportParams().get("overskrift"),
                "a screen offering to change a parameter cannot show what it currently is");
        assertEquals("Aktive P&T", vo.getSeriesReportParams().get("overskrift"));
    }
}
