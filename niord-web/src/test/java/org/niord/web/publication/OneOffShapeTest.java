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
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.CutoffDay;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationIssueService;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.PublicationSeriesService;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * The last section is the other end of the same endpoint: which ids the read of
 * one publication admits, and with which refusal.
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

    // ------------------------------------------------------- the address it lists

    // A loop rather than a stream: the value under test is legitimately null for a
    // publication with no document, and findFirst() cannot carry one.
    private static String linkFor(OneOffRestService.OneOffVo vo, String lang) {
        for (OneOffRestService.LangText row : vo.links) {
            if (lang.equals(row.lang)) {
                return row.value;
            }
        }
        return null;
    }

    /**
     * A document uploaded here lists the URL that serves it.
     *
     * The desc has a storage path and no link column, so reading the column
     * straight left the row with no address at all and the list with nothing to
     * point at -- while the imported publications beside it linked fine, because
     * those carry their address verbatim.
     */
    @Test
    public void anuploadedOneOffListsItsRepositoryUrl() {
        PublicationIssue issue = new PublicationIssue();
        PublicationIssueDesc desc = issue.createDesc("da");
        desc.setFilePath("publications/a/8e/3411907/EfS.pdf");
        desc.setFileName("EfS.pdf");

        OneOffRestService.OneOffVo vo = new OneOffRestService.OneOffVo();
        OneOffRestService.fillIssueDescs(issue, vo);

        assertEquals("/rest/repo/file/publications/a/8e/3411907/EfS.pdf", linkFor(vo, "da"),
                "an uploaded one-off must list the URL its file is served from");
        assertEquals("EfS.pdf", vo.fileNames.get(0).value);
    }

    /** An external link is listed exactly as it was typed. */
    @Test
    public void anexternalLinkOneOffListsItsLinkUnchanged() {
        PublicationIssue issue = new PublicationIssue();
        PublicationIssueDesc desc = issue.createDesc("en");
        desc.setLink("https://example.invalid/handbook.pdf");

        OneOffRestService.OneOffVo vo = new OneOffRestService.OneOffVo();
        OneOffRestService.fillIssueDescs(issue, vo);

        assertEquals("https://example.invalid/handbook.pdf", linkFor(vo, "en"),
                "an external link is absolute by definition and is not rewritten");
    }

    /**
     * A reference-only one-off lists no address, and still lists its language.
     *
     * Three of the five one-offs in the estate carry no document at all: they
     * exist to be cited. The row stays -- it has a title and a language to show --
     * and its address is simply absent.
     */
    @Test
    public void areferenceOnlyOneOffListsNoAddress() {
        PublicationIssue issue = new PublicationIssue();
        issue.createDesc("da").setName("Reference only");

        OneOffRestService.OneOffVo vo = new OneOffRestService.OneOffVo();
        OneOffRestService.fillIssueDescs(issue, vo);

        assertEquals(1, vo.links.size(), "the language row is kept");
        assertNull(linkFor(vo, "da"), "a publication with no file and no link has no address");
        assertNull(vo.fileNames.get(0).value);
    }

    // --------------------------------------------------- the address coming back

    /**
     * The listed address handed straight back does not become a stored link.
     *
     * The detail page sends the body back unchanged when it activates or
     * deactivates the publication -- that is a transition, not an edit. Storing
     * the derived address would freeze it: replace the document and the storage
     * path moves while the stored link keeps naming the file that is gone.
     */
    @Test
    public void thederivedAddressSentBackIsNotStoredAsALink() {
        PublicationIssue issue = new PublicationIssue();
        PublicationIssueDesc desc = issue.createDesc("da");
        desc.setFilePath("publications/a/8e/3411907/EfS.pdf");
        desc.setName("Uploaded");

        OneOffRestService.OneOffVo out = new OneOffRestService.OneOffVo();
        OneOffRestService.fillIssueDescs(issue, out);
        OneOffRestService.applyLinks(issue, out);

        assertNull(desc.getLink(), "the derived address is not a link somebody typed");
    }

    /** A link somebody really did type is stored, file or no file. */
    @Test
    public void alinkTypedOnAFileBackedOneOffIsStored() {
        PublicationIssue issue = new PublicationIssue();
        PublicationIssueDesc desc = issue.createDesc("da");
        desc.setFilePath("publications/a/8e/3411907/EfS.pdf");
        desc.setName("Uploaded");

        OneOffRestService.OneOffVo request = new OneOffRestService.OneOffVo();
        request.links.add(new OneOffRestService.LangText("da", "https://example.invalid/elsewhere.pdf"));
        OneOffRestService.applyLinks(issue, request);

        assertEquals("https://example.invalid/elsewhere.pdf", desc.getLink());
    }

    /** Clearing a stored link still clears it. */
    @Test
    public void ablankLinkClearsTheStoredOne() {
        PublicationIssue issue = new PublicationIssue();
        PublicationIssueDesc desc = issue.createDesc("da");
        desc.setLink("https://example.invalid/handbook.pdf");
        desc.setName("Linked");

        OneOffRestService.OneOffVo request = new OneOffRestService.OneOffVo();
        request.links.add(new OneOffRestService.LangText("da", "  "));
        OneOffRestService.applyLinks(issue, request);

        assertNull(desc.getLink());
    }

    // ------------------------------------------------- what the single GET admits

    /**
     * A lookup that answers whatever the test hands it.
     *
     * The read of one publication is a lookup, a gate and a fold, and only the
     * lookup needs storage. Answering it from a stub leaves the two parts worth
     * pinning -- which ids this collection admits and with which code, and what
     * `active` comes out as -- testable with no database at all. The endpoint's
     * service fields are package-private, which is what makes that possible here.
     */
    private static final class SeriesLookup extends PublicationSeriesService {

        private final PublicationSeries answer;

        private SeriesLookup(PublicationSeries answer) {
            this.answer = answer;
        }

        @Override
        public PublicationSeries findBySeriesId(String seriesId) {
            return answer;
        }
    }

    /** The same, for the one issue a one-off has. */
    private static final class IssueLookup extends PublicationIssueService {

        private final PublicationIssue answer;

        private IssueLookup(PublicationIssue answer) {
            this.answer = answer;
        }

        @Override
        public List<PublicationIssue> findBySeries(PublicationSeries series) {
            return answer == null ? List.of() : List.of(answer);
        }
    }

    private static OneOffRestService endpointFinding(PublicationSeries answer) {
        return endpointFinding(answer, null);
    }

    private static OneOffRestService endpointFinding(PublicationSeries answer, PublicationIssue issue) {
        OneOffRestService endpoint = new OneOffRestService();
        endpoint.seriesService = new SeriesLookup(answer);
        endpoint.issueService = new IssueLookup(issue);
        return endpoint;
    }

    /** An id that names nothing is a 404, like any other missing thing. */
    @Test
    public void anidThatNamesNothingIsNotFound() {
        OneOffRestService endpoint = endpointFinding(null);

        IssueLifecycleService.TransitionRefusedException refusal = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> endpoint.get("no-such-publication"));

        assertEquals("SERIES_NOT_FOUND", refusal.code());
        assertEquals(404, PublicationErrorCatalogue.statusOf(refusal.code()),
                "a deep link to an id that was never here has to answer 404, or the detail "
                        + "page cannot tell 'gone' from 'broken'");
    }

    /**
     * A scheduled series is ALSO not found here, and deliberately not the save's 400.
     *
     * This collection IS the one-off publications -- the list filters the estate by
     * kind, so a weekly series' id names nothing in it and 404 is the same answer
     * the list already gives by leaving the row out. SERIES_NOT_ONE_OFF is a 400
     * and belongs to the save, where the caller holds a form that would force
     * cadence NONE onto a scheduled series and has to be told what it is about to
     * do. Answering the read that way would tell a reader who asked for a document
     * that their REQUEST was malformed.
     */
    @Test
    public void ascheduledSeriesIsNotFoundEither() {
        PublicationSeries scheduled = seriesWithEverything();
        scheduled.setKind(SeriesKind.SCHEDULED);

        OneOffRestService endpoint = endpointFinding(scheduled);
        IssueLifecycleService.TransitionRefusedException refusal = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> endpoint.get("weekly-ntm"));

        assertEquals("SERIES_NOT_FOUND", refusal.code(),
                "the read has one refusal; SERIES_NOT_ONE_OFF is the save's, and it is a 400");
        assertEquals(404, PublicationErrorCatalogue.statusOf(refusal.code()));
        assertEquals(400, PublicationErrorCatalogue.statusOf("SERIES_NOT_ONE_OFF"),
                "the save keeps its 400: this test is the distinction, so it asserts both sides");
    }

    /**
     * A one-off is admitted, and answered with the dot the list would have shown.
     *
     * This is why the read has a route of its own rather than a copy on the client:
     * `active` is not a field on the series. It is series ACTIVE, issue PUBLISHED
     * and the public window, folded here -- any of the three being off is invisible
     * from the other two, so a page that re-derived it would be a second definition
     * of visibility, free to disagree with the list the publication was reached
     * from.
     */
    @Test
    public void aoneOffIsAnsweredWithTheVisibilityTheListWouldShow() {
        PublicationSeries oneOff = new PublicationSeries();
        oneOff.setKind(SeriesKind.ONE_OFF);
        oneOff.setStatus(SeriesStatus.ACTIVE);

        PublicationIssue issue = new PublicationIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setPublicFrom(new Date(1_700_000_000_000L));

        OneOffRestService.OneOffVo vo =
                endpointFinding(oneOff, issue).get("navigation-through-danish-waters");

        assertNotNull(vo, "a one-off's own id is the one id this route exists to answer");
        assertEquals(IssueStatus.PUBLISHED.name(), vo.issueStatus);
        assertTrue(vo.active, "ACTIVE series, PUBLISHED issue, window open: this one is public");
    }

    /** And one of the three being off is enough, which is the part a client cannot see. */
    @Test
    public void aoneOffWhoseWindowHasNotOpenedIsNotActive() {
        PublicationSeries oneOff = new PublicationSeries();
        oneOff.setKind(SeriesKind.ONE_OFF);
        oneOff.setStatus(SeriesStatus.ACTIVE);

        PublicationIssue issue = new PublicationIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setPublicFrom(new Date(System.currentTimeMillis() + 86_400_000L));

        OneOffRestService.OneOffVo vo =
                endpointFinding(oneOff, issue).get("navigation-through-danish-waters");

        assertFalse(vo.active,
                "published, but not yet: the series and the issue both say live and the "
                        + "publication still is not");
    }

    // --------------------------------------------------- what the dot cannot say

    /**
     * The window's END travels with the publication, because it is the state the
     * dot folds away and nothing else on the wire carries.
     *
     * A one-off whose series is ACTIVE and whose issue is PUBLISHED, with a window
     * that closed in 2017, answers `active: false` -- and until this field existed
     * the screen had the two states that were ON and nothing at all about the one
     * that was off. Four of the five archived one-offs have no end at all, which
     * is the shape that says "current until somebody decides otherwise", so null
     * is a value here rather than a gap.
     */
    @Test
    public void theclosedPublicWindowIsOnTheWire() {
        PublicationSeries oneOff = new PublicationSeries();
        oneOff.setKind(SeriesKind.ONE_OFF);
        oneOff.setStatus(SeriesStatus.ACTIVE);

        PublicationIssue issue = new PublicationIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setPublicFrom(new Date(1_487_718_000_000L));
        issue.setPublicTo(new Date(1_514_761_140_000L));

        OneOffRestService.OneOffVo vo =
                endpointFinding(oneOff, issue).get("navigation-through-danish-waters");

        assertFalse(vo.active, "the window closed in 2017, so the publication is not public");
        assertEquals(Long.valueOf(1_514_761_140_000L), vo.publicTo,
                "the one state the dot folds away has to be readable, or the screen can only say "
                        + "'not active' about a series and an issue that both say live");
    }

    /**
     * Whether the CATEGORY publishes travels with the publication too.
     *
     * The public listing is category.publish AND the issue published AND the
     * window open. The first of those is not on the series value object -- only
     * the categoryId is -- so a screen that re-derived it from the category list
     * would hold a second definition of visibility beside `active`, free to
     * disagree with it, and would have to tell "the flag is false" apart from "the
     * list has not arrived" while the two look identical.
     *
     * Two of the five categories in the archive do not publish, and four of the
     * five one-offs are filed under them.
     */
    @Test
    public void thecategoryPublishFlagIsOnTheWire() {
        PublicationCategory internal = new PublicationCategory();
        internal.setCategoryId("dk-dma-internal-publications");
        internal.setPublish(false);

        PublicationSeries oneOff = new PublicationSeries();
        oneOff.setKind(SeriesKind.ONE_OFF);
        oneOff.setStatus(SeriesStatus.ACTIVE);
        oneOff.setCategory(internal);

        PublicationIssue issue = new PublicationIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setPublicFrom(new Date(1_451_606_400_000L));

        OneOffRestService.OneOffVo vo = endpointFinding(oneOff, issue).get("aids-to-navigation");

        assertTrue(vo.active,
                "series ACTIVE, issue PUBLISHED, window open -- `active` is about those three and "
                        + "stays about those three");
        assertFalse(vo.categoryPublish,
                "and the publication is still on no public site, which is what the screen could "
                        + "not say");

        PublicationCategory published = new PublicationCategory();
        published.setCategoryId("dk-dma-publications");
        published.setPublish(true);
        oneOff.setCategory(published);
        assertTrue(endpointFinding(oneOff, issue).get("aids-to-navigation").categoryPublish);
    }

    /**
     * The public-window refusal is catalogued, carries the date, and is a 409.
     *
     * The case it exists for: series ACTIVE, issue PUBLISHED, window expired.
     * Neither branch of the active toggle matches -- there is no retired issue to
     * reactivate and no inactive series to activate -- so the save used to answer
     * 200 having changed nothing, and the screen redrew the same "not active" dot.
     * A save that reports success and changes nothing is the worst of the three
     * available answers.
     *
     * 409 rather than 400 because the same request succeeds once the window is
     * re-opened, which is what the refusal tells the caller to do.
     */
    @Test
    public void theclosedWindowRefusalIsCataloguedAndCarriesTheDate() {
        Date closed = new Date(1_514_761_140_000L);
        OneOffRestService.PublicWindowClosedException refusal =
                new OneOffRestService.PublicWindowClosedException(closed);

        assertEquals("PUBLIC_WINDOW_CLOSED", refusal.code());
        assertEquals(409, PublicationErrorCatalogue.statusOf(refusal.code()),
                "the same request succeeds once the window is re-opened, which is what 409 means");
        assertEquals(closed, refusal.publicTo(),
                "the whole point of the refusal is that it names the state the toggle could not "
                        + "see; a client parsing it out of the sentence breaks on the next rewording");
    }

    // ------------------------------------------- what TURNING the dot means

    private static PublicationSeries live() {
        PublicationSeries oneOff = new PublicationSeries();
        oneOff.setKind(SeriesKind.ONE_OFF);
        oneOff.setStatus(SeriesStatus.ACTIVE);
        return oneOff;
    }

    private static PublicationIssue publishedIssue(Date publicTo) {
        PublicationIssue issue = new PublicationIssue();
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setPublicFrom(new Date(1_487_718_000_000L));
        issue.setPublicTo(publicTo);
        return issue;
    }

    private static final Date NOW = new Date(1_757_000_000_000L);
    private static final Date CLOSED_IN_2017 = new Date(1_514_761_140_000L);

    /**
     * A RENAME OF AN EXPIRED PUBLICATION IS A RENAME, and nothing else.
     *
     * The editor sends the whole publication back on every save, and `active` comes
     * with it -- as FALSE for a publication whose window ran out years ago, because
     * that is what the read reported. Acting on the requested value alone made an
     * ordinary save either a refusal for a reason the admin was never asked for, or,
     * with one filled in, a RETIRE nobody asked for: an audit entry saying a
     * document was withdrawn, written because somebody fixed a typo in its title.
     */
    @Test
    public void asaveOnAnExpiredPublicationIsNoTransitionAtAll() {
        assertEquals(OneOffRestService.ActiveAction.NOTHING,
                OneOffRestService.activeAction(live(), publishedIssue(CLOSED_IN_2017), false, NOW),
                "the window ran out, so the dot ALREADY reads off; a save that carries that value "
                        + "back is a rename, and it must not retire the document it renames");
    }

    /** And the dot really does turn off, on a publication that is really on. */
    @Test
    public void turningOffApublicationThatIsLiveRetiresItsIssue() {
        assertEquals(OneOffRestService.ActiveAction.RETIRE_ISSUE,
                OneOffRestService.activeAction(live(), publishedIssue(null), false, NOW),
                "series ACTIVE, issue PUBLISHED, window open: this one IS on the public site, so "
                        + "off is the issue's own retire with its own reason and its own audit entry");
    }

    /**
     * Turning it ON when only the window is closed is still the refusal.
     *
     * The path the refusal was added for stays exactly where it was: re-opening a
     * public window puts a document back in front of the public, which is the
     * decision publicTo exists to protect, so it is its own action rather than a
     * side effect of a dot.
     */
    @Test
    public void turningOnAnExpiredPublicationIsStillRefused() {
        assertEquals(OneOffRestService.ActiveAction.REFUSE_WINDOW_CLOSED,
                OneOffRestService.activeAction(live(), publishedIssue(CLOSED_IN_2017), true, NOW),
                "neither a retired issue to reactivate nor an inactive series to activate: what is "
                        + "off is the window, and the toggle does not own it");
    }

    /**
     * The two transitions that were always there, pinned beside the two that were not.
     *
     * A retired issue comes back; a series that was never activated is activated.
     * And a plain save on a publication that is already live is a plain save --
     * not a re-publish.
     */
    @Test
    public void theotherWaysOfTurningItOnAreUnchanged() {
        PublicationIssue retired = publishedIssue(null);
        retired.setStatus(IssueStatus.RETIRED);
        assertEquals(OneOffRestService.ActiveAction.REACTIVATE_ISSUE,
                OneOffRestService.activeAction(live(), retired, true, NOW));

        PublicationSeries draft = live();
        draft.setStatus(SeriesStatus.DRAFT);
        PublicationIssue open = publishedIssue(null);
        open.setStatus(IssueStatus.OPEN);
        assertEquals(OneOffRestService.ActiveAction.ACTIVATE_SERIES,
                OneOffRestService.activeAction(draft, open, true, NOW));

        assertEquals(OneOffRestService.ActiveAction.NOTHING,
                OneOffRestService.activeAction(live(), publishedIssue(null), true, NOW),
                "already live and asked to be live: a save is a save");
    }

    /** And the window endpoint's own refusal for a series that is not a one-off. */
    @Test
    public void thewindowEndpointRefusesAScheduledSeries() {
        PublicationSeries scheduled = seriesWithEverything();
        scheduled.setKind(SeriesKind.SCHEDULED);

        OneOffRestService endpoint = endpointFinding(scheduled);
        IssueLifecycleService.TransitionRefusedException refusal = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> endpoint.setPublicWindow("weekly-ntm",
                        new OneOffRestService.PublicWindowRequest(null, null)));

        assertEquals("NOT_ONE_OFF", refusal.code(),
                "a scheduled issue's window is closed by the issue that succeeds it; clearing an "
                        + "end there hands the public site two current editions");
        assertEquals(409, PublicationErrorCatalogue.statusOf(refusal.code()));
    }

    /** An id that names nothing is the same 404 the read gives. */
    @Test
    public void thewindowEndpointIsNotFoundForAnUnknownId() {
        OneOffRestService endpoint = endpointFinding(null);

        IssueLifecycleService.TransitionRefusedException refusal = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> endpoint.setPublicWindow("no-such-publication",
                        new OneOffRestService.PublicWindowRequest(null, null)));

        assertEquals("SERIES_NOT_FOUND", refusal.code());
        assertEquals(404, PublicationErrorCatalogue.statusOf(refusal.code()));
    }
}
