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

package org.niord.web.publication;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.CutoffDay;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What a one-off may and may not carry.
 *
 * This is the whole contract of the one-off endpoint, and it is two claims that
 * pull in opposite directions: a one-off has EVERY option a series has, except
 * the ones that are questions about the next issue. Getting either half wrong is
 * a real defect and neither is visible from the other.
 *
 * The first version of this endpoint got the first half wrong -- it accepted only
 * UPLOADED_FILE and EXTERNAL_LINK, which is narrower than the data it edits:
 * three of the five one-offs in the estate carry contentMode NONE.
 *
 * No database and no server: this is a shape, and shape tests that need MySQL
 * are shape tests that stop running.
 */
public class OneOffShapeTest {

    private static PublicationSeries seriesWithEverything() {
        PublicationSeries s = new PublicationSeries();
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);

        // The parts that are questions about a SEQUENCE. All of these must go.
        s.setCadence(SeriesCadence.WEEKLY);
        s.setNominalCutoffDay(CutoffDay.WEDNESDAY);
        s.setNominalCutoffTime("09:00");
        s.setNominalCutoffDayOfMonth(15);
        s.setNominalCutoffMonth(6);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);
        s.setFirstIssueStartsAt(new Date(1_700_000_000_000L));

        // The parts a one-off keeps.
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setReportId("fm-report");
        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        PublicationSeriesDesc desc = s.createDesc("da");
        desc.setName("Test publication");
        desc.setNameSuggestionPattern("EfS uge ${week}");
        desc.setMessageReferenceFormat("EfS ${week}/${year}");
        return s;
    }

    // ------------------------------------------------- what a one-off drops

    /**
     * Everything about the NEXT issue is stripped, because there is no next issue.
     *
     * S-5, S-6 and S-7 refuse the nominal cut-off fields on a cadence-less series
     * anyway, so leaving them set would produce a series that cannot be activated
     * and would report its reason against fields the form never showed.
     */
    @Test
    public void theSequenceFieldsAreStripped() {
        PublicationSeries s = seriesWithEverything();

        OneOffRestService.forceOneOffShape(s);

        assertEquals(SeriesKind.ONE_OFF, s.getKind());
        assertEquals(SeriesCadence.NONE, s.getCadence());
        assertNull(s.getNominalCutoffDay());
        assertNull(s.getNominalCutoffTime());
        assertNull(s.getNominalCutoffDayOfMonth());
        assertNull(s.getNominalCutoffMonth());
        assertEquals(NumberingScheme.NONE, s.getNumberingScheme());
        assertEquals(NextIssueCreation.MANUAL, s.getNextIssueCreation());
        assertNull(s.getFirstIssueStartsAt());
    }

    /** A name suggested from a pattern needs a sequence to suggest across. */
    @Test
    public void thenameSuggestionPatternIsStripped() {
        PublicationSeries s = seriesWithEverything();

        OneOffRestService.forceOneOffShape(s);

        assertNull(s.getDescs().get(0).getNameSuggestionPattern(),
                "a one-off has one issue, so there is nothing to suggest a name for and nothing "
                        + "to suggest it from");
    }

    // -------------------------------------------------- what a one-off keeps

    /**
     * A QUERY-BACKED one-off keeps its query. This is the defect being fixed.
     *
     * Nothing about publishing once prevents the content being generated from a
     * message query, and S-1 requires the time relation and the criteria document
     * on that content mode. Stripping them left a series that could never resolve
     * anything and could never activate.
     */
    @Test
    public void aqueryBackedOneOffKeepsItsCriteriaAndTimeRelation() {
        PublicationSeries s = seriesWithEverything();

        OneOffRestService.forceOneOffShape(s);

        assertEquals(ContentMode.GENERATED_FROM_QUERY, s.getContentMode(),
                "the content mode is the admin's choice, not something this endpoint decides");
        assertEquals(TimeRelation.PUBLISHED_IN_INTERVAL, s.getTimeRelation(),
                "S-1 requires a time relation on a query-backed series");
        assertNotNull(s.getCriteria(), "S-1 requires a criteria document on a query-backed series");
        assertEquals("fm-report", s.getReportId(), "a query-backed publication needs its report");
    }

    /** The reference format survives: a one-off is citable like anything else. */
    @Test
    public void thecitationFormatIsKept() {
        PublicationSeries s = seriesWithEverything();

        OneOffRestService.forceOneOffShape(s);

        assertEquals("EfS ${week}/${year}", s.getDescs().get(0).getMessageReferenceFormat());
    }

    // ------------------------------------------------------------ S-1, both ways

    /**
     * On any OTHER content mode, S-1 forbids exactly what it required above.
     *
     * A criteria document on an uploaded PDF is a query nothing will ever run,
     * and it would refuse activation for a reason the form cannot explain.
     */
    @Test
    public void anonQueryBackedOneOffCarriesNoCriteria() {
        PublicationSeries s = seriesWithEverything();
        s.setContentMode(ContentMode.UPLOADED_FILE);

        OneOffRestService.forceOneOffShape(s);

        assertNull(s.getTimeRelation(), "S-1: only a query-backed series has a time relation");
        assertNull(s.getCriteria(), "S-1: only a query-backed series carries criteria");
    }

    /** Including NONE, which three of the five one-offs in the estate use. */
    @Test
    public void contentModeNoneIsAcceptedAndKept() {
        PublicationSeries s = seriesWithEverything();
        s.setContentMode(ContentMode.NONE);

        OneOffRestService.forceOneOffShape(s);

        assertEquals(ContentMode.NONE, s.getContentMode(),
                "NONE is a live legacy content type: journal-number, list-of-wrecks and "
                        + "aids-to-navigation all carry it, and coercing it would rewrite them");
        assertNull(s.getCriteria());
    }

    // ------------------------------------------------------- S-2, both ways

    /**
     * aliveAtCutoff must be ABSENT on a publication with no query, not false.
     *
     * It is a filter applied to a query. "false" on a publication that runs no
     * query claims a filter that ran and passed everything, which is exactly the
     * distinction S-2 exists to keep -- and the save was refused for it, on a
     * field the one-off form never showed.
     */
    @Test
    public void anonQueryBackedOneOffCarriesNoLivenessFilter() {
        PublicationSeries s = seriesWithEverything();
        s.setContentMode(ContentMode.EXTERNAL_LINK);
        s.setAliveAtCutoff(Boolean.FALSE);

        OneOffRestService.forceOneOffShape(s);

        assertNull(s.getAliveAtCutoff(),
                "S-2 refuses a liveness filter on a series with no query, so a one-off that "
                        + "carries one cannot be activated at all");
    }

    /** And a query-backed one MUST state it, or it fails activation for a field nobody was asked about. */
    @Test
    public void aqueryBackedOneOffAlwaysStatesItsLivenessFilter() {
        PublicationSeries s = seriesWithEverything();
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setAliveAtCutoff(null);

        OneOffRestService.forceOneOffShape(s);

        assertEquals(Boolean.FALSE, s.getAliveAtCutoff(),
                "S-2 requires a query-backed series to say whether it filters on liveness");
    }

    // --------------------------------------------------------- the desc reuse

    /**
     * A language that already has a desc gets THAT desc, not a second one.
     *
     * The issue lifecycle writes one desc per language at create. Attaching a link
     * by calling createDesc again inserts a duplicate, violates
     * UNIQUE (entity_id, lang), and fails the whole save with a database error
     * naming a column -- which says nothing about the link somebody just typed.
     * Measured against the deployed API before the fix: HTTP 500, "Duplicate entry
     * 'da-3411907'".
     */
    @Test
    public void alanguageThatAlreadyHasADescIsReused() {
        PublicationIssue issue = new PublicationIssue();
        PublicationIssueDesc existing = issue.createDesc("da");
        existing.setName("Already here");

        PublicationIssueDesc found = OneOffRestService.descFor(issue, "da");

        assertSame(existing, found, "a second desc for the same language cannot be stored");
        assertEquals(1, issue.getDescs().size(), "the issue grew a duplicate desc row");
    }

    /** A language with no desc yet gets one. */
    @Test
    public void alanguageWithNoDescGetsOne() {
        PublicationIssue issue = new PublicationIssue();

        PublicationIssueDesc created = OneOffRestService.descFor(issue, "en");

        assertEquals("en", created.getLang());
        assertEquals(1, issue.getDescs().size());
    }
}
