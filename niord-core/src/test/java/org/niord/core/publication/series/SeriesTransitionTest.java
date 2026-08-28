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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.domain.Domain;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two series actions that are not saves: the status transition, and which
 * model serves the series to the public.
 *
 * Both are audited, both refuse in words, and neither is reachable through the
 * series editor's save -- that is the point of them being actions. What is
 * pinned here is the reason rule (leaving ACTIVE says why; entering it does
 * not), the one transition that does not exist (back to DRAFT), and the flip's
 * precondition together with the way round it that leaves a trace.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SeriesTransitionTest {

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    IssueAuditService auditService;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    /** A complete series -- one that S-17 lets become ACTIVE -- in the given status. */
    private PublicationSeries series(SeriesStatus status) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        // S-20: a series with a cadence belongs to a domain, which carries the
        // timezone its cut-offs are read in.
        Domain d = new Domain();
        d.setDomainId("dom-" + UUID.randomUUID().toString().substring(0, 8));
        d.setName("Test domain");
        d.setTimeZone("Europe/Copenhagen");
        em.persist(d);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(status);
        s.setDomain(d);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setNominalCutoffDay(CutoffDay.WEDNESDAY);
        s.setNominalCutoffTime("09:00");
        s.setNominalCutoffTimeZone("Europe/Copenhagen");
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setFirstIssueStartsAt(new java.util.Date());
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        // A query-backed series generates its document from a report and must name
        // one -- S-1 -- so a fixture that means to ACTIVATE has to carry the full
        // report configuration S-9 asks for alongside it.
        s.setReportId("some-report");
        s.setPageSize(PageSize.A4);
        s.setPageOrientation(PageOrientation.PORTRAIT);
        s.setMapThumbnails(Boolean.FALSE);
        s.setCategory(c);
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        PublicationSeriesDesc desc = s.createDesc("da");
        desc.setName("Test series");
        desc.setFileNamePattern("test-${week}-${year}.pdf");
        em.persist(s);
        em.flush();
        return s;
    }

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    private List<IssueAuditEntry> entries(PublicationSeries s, AuditAction action) {
        return auditService.forSeries(s).stream().filter(a -> action == a.getAction()).toList();
    }

    // ============================================================== transition

    /** The first activation is a confirm: nothing is taken away from anybody. */
    @Test
    @Transactional
    public void activatingNeedsNoReasonAndIsAudited() {
        PublicationSeries s = series(SeriesStatus.DRAFT);

        PublicationSeries saved = seriesService.transition(s, SeriesStatus.ACTIVE, null, user());
        em.flush();

        assertEquals(SeriesStatus.ACTIVE, saved.getStatus());
        List<IssueAuditEntry> activated = entries(saved, AuditAction.SERIES_ACTIVATED);
        assertEquals(1, activated.size(), "one activation, one entry");
        assertNull(activated.get(0).getReason(), "no reason was given and none is invented");
    }

    /** Retiring takes the series away from editors and readers, and must say why. */
    @Test
    @Transactional
    public void retiringWithoutAReasonIsRefused() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> seriesService.transition(s, SeriesStatus.RETIRED, "x", user()));
        assertEquals("REASON_REQUIRED", e.code());
        assertEquals(SeriesStatus.ACTIVE, s.getStatus(), "a refusal changes nothing");
    }

    @Test
    @Transactional
    public void retiringWithAReasonIsAuditedWithIt() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);

        PublicationSeries saved = seriesService.transition(s, SeriesStatus.RETIRED,
                "  replaced by the combined list  ", user());
        em.flush();

        assertEquals(SeriesStatus.RETIRED, saved.getStatus());
        List<IssueAuditEntry> retired = entries(saved, AuditAction.SERIES_RETIRED);
        assertEquals(1, retired.size());
        assertEquals("replaced by the combined list", retired.get(0).getReason(), "trimmed, as typed");
    }

    /** Reinstating restores a state the series was already in: confirm, no reason. */
    @Test
    @Transactional
    public void reinstatingNeedsNoReason() {
        PublicationSeries s = series(SeriesStatus.RETIRED);

        PublicationSeries saved = seriesService.transition(s, SeriesStatus.ACTIVE, null, user());

        assertEquals(SeriesStatus.ACTIVE, saved.getStatus());
        assertEquals(1, entries(saved, AuditAction.SERIES_ACTIVATED).size());
    }

    /** DRAFT means "not finished yet"; a series that has been active is past that. */
    @Test
    @Transactional
    public void aRetiredSeriesCannotGoBackToDraft() {
        PublicationSeries s = series(SeriesStatus.RETIRED);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> seriesService.transition(s, SeriesStatus.DRAFT, "a good reason", user()));
        assertEquals("INVALID_STATUS_TRANSITION", e.code());
    }

    /** ACTIVE is what puts a series in the picker, so it may not be incomplete. */
    @Test
    @Transactional
    public void activatingAnIncompleteSeriesIsRefusedWithTheFieldsNamed() {
        PublicationSeries s = series(SeriesStatus.DRAFT);
        s.setNominalCutoffDay(null);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> seriesService.transition(s, SeriesStatus.ACTIVE, null, user()));
        assertEquals("SERIES_INVALID", e.code());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> "S-17".equals(f.rule())),
                "the refusal names S-17, and " + e.fieldErrors());
        assertTrue(e.fieldErrors().stream().anyMatch(f -> f.field().startsWith("nominalCutoff")),
                "and the field that is missing: " + e.fieldErrors());
        assertEquals(SeriesStatus.DRAFT, em.find(PublicationSeries.class, s.getId()).getStatus());
    }

    /** The same status twice is not a transition, and writes nothing. */
    @Test
    @Transactional
    public void theSameStatusIsANoOp() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);

        seriesService.transition(s, SeriesStatus.ACTIVE, null, user());

        assertTrue(entries(s, AuditAction.SERIES_ACTIVATED).isEmpty(), "nothing happened, so nothing is recorded");
    }

    // ==================================================================== flip

    /** A series serves the public only once it is active. */
    @Test
    @Transactional
    public void aDraftCannotBecomeThePublicAuthority() {
        PublicationSeries s = series(SeriesStatus.DRAFT);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> seriesService.setPublicAuthority(s, PublicAuthority.NEW, false,
                                "cutover rehearsal", user()));
        assertEquals("SERIES_NOT_ACTIVE", e.code());
        assertEquals(PublicAuthority.LEGACY, s.getPublicAuthority());
    }

    /** Without the shadow diff's green light the flip is refused -- with the count in words. */
    @Test
    @Transactional
    public void anUnprovenSeriesIsRefusedUnlessForced() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> seriesService.setPublicAuthority(s, PublicAuthority.NEW, false,
                                "cutover rehearsal", user()));
        assertEquals("NOT_READY_FOR_CUTOVER", e.code());
        assertTrue(e.getMessage().contains("0 consecutive green"), e.getMessage());
        assertEquals(PublicAuthority.LEGACY, s.getPublicAuthority());
    }

    /** The way round the precondition exists, demands a reason, and says it was used. */
    @Test
    @Transactional
    public void aForcedFlipIsAuditedAsForced() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);

        PublicationSeries saved = seriesService.setPublicAuthority(s, PublicAuthority.NEW, true,
                "go-live window, diff exempt by ruling", user());
        em.flush();

        assertEquals(PublicAuthority.NEW, saved.getPublicAuthority());
        List<IssueAuditEntry> flipped = entries(saved, AuditAction.SERIES_AUTHORITY_CHANGED);
        assertEquals(1, flipped.size());
        assertEquals("go-live window, diff exempt by ruling", flipped.get(0).getReason());
        String detail = String.valueOf(flipped.get(0).getDetail());
        assertTrue(detail.contains("LEGACY") && detail.contains("NEW") && detail.contains("true"),
                "the entry says from, to and that it was forced: " + detail);
    }

    /** A forced flip with no reason is not a flip; the reason is the whole point of force. */
    @Test
    @Transactional
    public void aFlipWithoutAReasonIsRefusedEvenWhenForced() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> seriesService.setPublicAuthority(s, PublicAuthority.NEW, true, " ", user()));
        assertEquals("REASON_REQUIRED", e.code());
    }

    /** Flipping BACK has no precondition: a rollback that could be refused is not a rollback. */
    @Test
    @Transactional
    public void flippingBackToLegacyNeedsNoEvidence() {
        PublicationSeries s = series(SeriesStatus.ACTIVE);
        s.setPublicAuthority(PublicAuthority.NEW);
        em.flush();

        PublicationSeries saved = seriesService.setPublicAuthority(s, PublicAuthority.LEGACY, false,
                "rolling back after the public page showed a wrong week", user());
        em.flush();

        assertEquals(PublicAuthority.LEGACY, saved.getPublicAuthority());
        List<IssueAuditEntry> flipped = entries(saved, AuditAction.SERIES_AUTHORITY_CHANGED);
        assertEquals(1, flipped.size());
        assertNotNull(flipped.get(0).getDetail());
        assertTrue(String.valueOf(flipped.get(0).getDetail()).contains("false"),
                "and it was not forced: " + flipped.get(0).getDetail());
    }
}
