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
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the report is handed, and in what form.
 *
 * THE DEFECT THIS PINS. week, weekTo and year went into the FreeMarker model as
 * Integers, and FreeMarker formats a NUMBER with the grouping of the language it
 * renders in -- so a Danish edition printed the year 2026 as "2.026" on its
 * cover. Every edition, for as long as the injection has existed. The previous
 * system substituted the same tokens as text and did not have the defect.
 *
 * The fix is not a format setting: it is that these are not numbers to the
 * report at all. They are what the edition is CALLED, they may be free text --
 * "36+37" -- and text is interpolated identically in every language.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueReportParamInjectionTest {

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
    EntityManager em;

    // ------------------------------------------------------------------ fixture

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
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
        s.getReportParams().put("overskrift", "Aktive P&T");

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
        // The first edition of the period, which is what the create writes and
        // what the report's cover prints where a series names one.
        i.setEdition(IssueShape.FIRST_EDITION_TEXT);
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

    private Map<String, Object> paramsFromPublishing(PublicationIssue i) {
        publishService.publish(i.getId(), new IssuePublishService.PublishRequest(
                IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(CUTOFF)));
        em.flush();
        IssueRenderService.RenderRequest request = StubIssueRenderService.lastRequest();
        assertNotNull(request, "nothing was rendered, so there is no parameter map to read");
        return request.reportParams();
    }

    // ------------------------------------------------------------------- tests

    /**
     * The defect, stated as arithmetic: an Integer 2026 IS "2.026" in Danish.
     *
     * Not an assertion about our code -- it is the reason the injection had to
     * change form, written down where the next reader of the change will find it.
     * FreeMarker formats numbers through exactly this, per render language.
     */
    @Test
    public void anintegerYearGroupsUnderADanishLocale() {
        assertEquals("2.026", NumberFormat.getInstance(Locale.forLanguageTag("da")).format(2026),
                "if this ever stops holding, the reason the year is injected as text is gone");
    }

    /** So the year reaches the report as TEXT, and reads the same in every language. */
    @Test
    @Transactional
    public void theyearIsInjectedAsTextSoADanishRenderPrints2026() {
        PublicationIssue i = issue(series());
        em.flush();

        Map<String, Object> params = paramsFromPublishing(i);

        assertInstanceOf(String.class, params.get("year"),
                "the year went into the model as a number again; FreeMarker groups a number per "
                        + "render language, and a Danish edition then prints its year as 2.026");
        assertEquals("2026", params.get("year"),
                "the rendered year parameter is not the year this edition is for");
    }

    /** And so do the two week values, for the same reason and by the same route. */
    @Test
    @Transactional
    public void theweekIsInjectedAsText() {
        PublicationIssue i = issue(series());
        em.flush();

        Map<String, Object> params = paramsFromPublishing(i);

        assertInstanceOf(String.class, params.get("week"));
        assertEquals("36", params.get("week"));
        assertTrue(params.containsKey("weekTo"),
                "weekTo dropped out of the model; a template interpolating it fails the render");
    }

    /**
     * A printed label reaches the report heading -- the third of the three
     * renderings of one decision.
     */
    @Test
    @Transactional
    public void aprintedLabelIsWhatTheReportPrints() {
        PublicationIssue i = issue(series());
        em.flush();
        editService.update(i, new IssueEditService.IssueEdit(null, null, null, null, null, false,
                null, "36+37", null, null, null), user());
        em.flush();

        Map<String, Object> params = paramsFromPublishing(i);

        assertEquals("36+37", params.get("week"),
                "the report still prints the derived week while the title says two");
    }

    /** The series' own parameters still travel, and the injection sits on top of them. */
    @Test
    @Transactional
    public void theseriesParametersStillReachTheReport() {
        PublicationIssue i = issue(series());
        em.flush();

        Map<String, Object> params = paramsFromPublishing(i);

        assertEquals("Aktive P&T", params.get("overskrift"));
        assertEquals("1", params.get("edition"),
                "the edition is free text and is passed as it stands");
    }

    /**
     * S-23 holds on this side of the fence too.
     *
     * The drawer now offers the parameter table, so a reserved name can be typed
     * where previously only the series form could reach it. The injection would
     * win either way, which is the problem: the table would show a value under a
     * heading saying it applies while the document ignored it.
     */
    @Test
    @Transactional
    public void areservedReportParameterIsRefusedOnTheIssueToo() {
        PublicationIssue i = issue(series());
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, new IssueEditService.IssueEdit(
                                null, null, null, Map.of("Year", "1999")), user()));
        assertEquals(IssueEditService.REPORT_PARAM_RESERVED, e.code());
    }

    /**
     * But an issue that ALREADY stores one stays editable.
     *
     * The rule forbids DECIDING one of the four here. A key the issue already
     * holds at the value it already holds is not that decision -- it is the row
     * being sent back unchanged by a drawer that round-trips the whole parameter
     * table in order to edit a field somewhere else on the form.
     *
     * The previous system had no such rule, so an imported issue can carry one.
     * Judging the whole submitted map would make every one of those rows
     * uneditable -- renames, interval corrections, the report id, all refused
     * with a message about a parameter nobody opened -- on an endpoint that has
     * been answering in production.
     */
    @Test
    @Transactional
    public void astoredReservedParameterSurvivesAnUnrelatedEdit() {
        PublicationIssue i = issue(series());
        i.getReportParams().put("week", "36 og 37");
        em.flush();

        Map<String, Object> roundTripped = new LinkedHashMap<>();
        roundTripped.put("week", "36 og 37");
        roundTripped.put("overskrift", "Aktive P&T");

        editService.update(i, new IssueEditService.IssueEdit(
                Map.of("da", "Et nyt navn"), null, null, roundTripped), user());
        em.flush();

        assertEquals("Et nyt navn", i.getDescs().get(0).getName(),
                "the rename was refused over a parameter the request did not change");
        assertEquals("36 og 37", i.getReportParams().get("week"),
                "the stored parameter did not survive the edit that carried it back");
    }

    /** Changing one that is already stored IS still a decision, and is still refused. */
    @Test
    @Transactional
    public void changingAstoredReservedParameterIsStillRefused() {
        PublicationIssue i = issue(series());
        i.getReportParams().put("week", "36 og 37");
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, new IssueEditService.IssueEdit(
                                null, null, null, Map.of("week", "40")), user()));
        assertEquals(IssueEditService.REPORT_PARAM_RESERVED, e.code());
    }

    /** And an edit that says nothing about the parameters never asks the question. */
    @Test
    @Transactional
    public void anEditThatSendsNoParametersIsNotJudgedOnThem() {
        PublicationIssue i = issue(series());
        i.getReportParams().put("year", "2026");
        em.flush();

        editService.update(i, new IssueEditService.IssueEdit(
                Map.of("da", "Et nyt navn"), null, null, null), user());
        em.flush();

        assertEquals("2026", i.getReportParams().get("year"),
                "an edit that sent no parameter map wrote one anyway");
    }

    /** And the reserved set is exactly the four the numbering supplies. */
    @Test
    public void thereservedSetIsTheNumbering() {
        assertEquals(java.util.Set.of("week", "weekto", "year", "edition"),
                SeriesValidator.RESERVED_REPORT_PARAMS,
                "the reserved set moved; these four are injected from the edition's numbering and "
                        + "typing one puts a second, fixed answer beside the derived one");
    }
}
