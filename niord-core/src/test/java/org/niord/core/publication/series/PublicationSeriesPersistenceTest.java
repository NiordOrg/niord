package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips the series and issue model through a real MySQL.
 *
 * Kept small on purpose. Everything expressible as a pure predicate is tested
 * without a database and runs in a second; what is here is what genuinely needs
 * one -- the per-language desc layer, the shared sequence, and the blank-desc
 * filter.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class PublicationSeriesPersistenceTest {

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    PublicationIssueService issueService;

    @Inject
    EntityManager em;

    // -------------------------------------------------------------- fixtures

    private PublicationCategory aCategory() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);
        return c;
    }

    private PublicationSeries aSeries(String... languages) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("series-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.DRAFT);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setMessagePublication(MessagePublication.NONE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setCategory(aCategory());
        s.getLanguages().addAll(List.of(languages));
        for (String lang : languages) {
            PublicationSeriesDesc d = s.createDesc(lang);
            d.setName("Name in " + lang);
            d.setNameSuggestionPattern("Pattern ${week} " + lang);
        }
        return s;
    }

    // ----------------------------------------------- C5, the per-language layer

    /**
     * The one-way door. Collapsing a per-language pattern to a single value fails
     * SILENTLY: ILocalizable falls back to the first desc, so an English consumer
     * is served Danish rather than an error. Three languages, not two, because a
     * two-language test passes just as well against an implementation that only
     * ever keeps the first and the last.
     */
    @BindsRule({"D-1", "D-2"})
    @Test
    @Transactional
    public void aSeriesRoundTripsOneDescPerConfiguredLanguage() {
        PublicationSeries saved = seriesService.create(aSeries("da", "en", "de"));
        em.flush();
        em.clear();

        PublicationSeries read = seriesService.findBySeriesId(saved.getSeriesId());
        assertNotNull(read, "the series did not come back");

        assertEquals(3, read.getDescs().size(),
                "expected exactly one desc row per configured language, got " + read.getDescs().size());

        assertEquals(List.of("da", "en", "de"), read.getLanguages(),
                "the configured language list did not survive in order; @OrderColumn is what preserves it");

        for (String lang : List.of("da", "en", "de")) {
            PublicationSeriesDesc d = read.getDescs().stream()
                    .filter(x -> lang.equals(x.getLang()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(d, "no desc row for " + lang);
            assertEquals("Name in " + lang, d.getName(), "the " + lang + " name was not preserved");
            assertEquals("Pattern ${week} " + lang, d.getNameSuggestionPattern(),
                    "the " + lang + " pattern was not preserved -- a collapsed pattern layer serves one "
                            + "language's text to every other language, without erroring");
        }
    }

    /**
     * D-7. A desc row with a format string but no name must not persist.
     *
     * Under the inherited "any field is defined" rule such a row survives, then
     * round-trips to nothing, and the citation text it carried is lost without a
     * word. That is a legacy defect, not a new risk.
     */
    @Test
    @Transactional
    public void aFormatOnlyDescRowIsRejected() {
        PublicationSeries s = aSeries("da");
        PublicationSeriesDesc formatOnly = s.createDesc("en");
        formatOnly.setMessageReferenceFormat("EfS ${year}, punkt ${parameters}");
        // and deliberately no name

        assertFalse(formatOnly.descDefined(),
                "a row with a format string and no name reports itself defined; it would persist and then "
                        + "round-trip to nothing");

        seriesService.create(s);
        em.flush();
        em.clear();

        PublicationSeries read = seriesService.findBySeriesId(s.getSeriesId());
        assertEquals(1, read.getDescs().size(),
                "the format-only row was persisted; it should have been dropped before insert");
        assertEquals("da", read.getDescs().get(0).getLang());
    }

    // ------------------------------------------------------- the shared sequence

    /**
     * Every id in the system is drawn from one counter. Two entities of different
     * types must therefore never share an id -- which is what would happen the
     * moment one of them acquired its own generator.
     */
    @Test
    @Transactional
    public void idsComeFromOneSharedSequence() {
        PublicationSeries series = seriesService.create(aSeries("da"));
        em.flush();

        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series);
        issue.setPublicId(UUID.randomUUID().toString());
        issue.setStatus(IssueStatus.OPEN);
        // Required at create and derived from the minted publicId, exactly as the
        // repository path of every other file-bearing entity in this system is.
        issue.setRepoPath("publications/" + issue.getPublicId());
        PublicationIssueDesc desc = issue.createDesc("da");
        desc.setName("Udgave 1");
        issueService.create(issue);
        em.flush();

        assertNotNull(series.getId());
        assertNotNull(issue.getId());
        assertNotEquals(series.getId(), issue.getId(),
                "a series and an issue were given the same id, so they are not drawing from one sequence");

        PublicationSeries second = seriesService.create(aSeries("da"));
        em.flush();
        assertTrue(second.getId() > issue.getId(),
                "the counter went backwards across entity types: " + second.getId() + " after " + issue.getId());
    }

    // ------------------------------------------------------------- D-9 agreement

    /**
     * D-9. The entity's own notion of "defined" must match what a blank row means,
     * field for field. Where they disagree, one of the two silently keeps rows the
     * other discards.
     */
    @BindsRule({"D-9"})
    @Test
    public void descDefinedAgreesOnWhatAnEmptyRowIs() {
        PublicationSeriesDesc blank = new PublicationSeriesDesc();
        blank.setLang("da");
        assertFalse(blank.descDefined(), "an entirely blank desc reports itself defined");

        PublicationSeriesDesc named = new PublicationSeriesDesc();
        named.setLang("da");
        named.setName("Efterretninger for Søfarende");
        assertTrue(named.descDefined(), "a desc with a name reports itself undefined");

        PublicationIssueDesc blankIssue = new PublicationIssueDesc();
        blankIssue.setLang("da");
        assertFalse(blankIssue.descDefined());

        PublicationIssueDesc namedIssue = new PublicationIssueDesc();
        namedIssue.setLang("da");
        namedIssue.setName("Uge 28");
        assertTrue(namedIssue.descDefined());

        // The two levels must agree with each other: a name defines a row on both.
        assertEquals(named.descDefined(), namedIssue.descDefined(),
                "the series and issue desc layers disagree about what a defined row is");
        assertEquals(blank.descDefined(), blankIssue.descDefined(),
                "the series and issue desc layers disagree about what a blank row is");
    }

    // ---------------------------------------------------- the criteria column

    /** The criteria document survives the converter in both directions. */
    @Test
    @Transactional
    public void theCriteriaDocumentRoundTripsThroughTheColumn() {
        PublicationSeries s = aSeries("da");

        org.niord.core.publication.series.criteria.IssueCriteriaVo doc =
                new org.niord.core.publication.series.criteria.IssueCriteriaVo();
        org.niord.core.publication.series.criteria.MessageSeriesCriterionVo node =
                new org.niord.core.publication.series.criteria.MessageSeriesCriterionVo();
        node.setValues(new java.util.ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        seriesService.create(s);
        em.flush();
        em.clear();

        PublicationSeries read = seriesService.findBySeriesId(s.getSeriesId());
        assertNotNull(read.getCriteria(), "the criteria document came back null");
        assertEquals(1, read.getCriteria().getCriteria().size());
        assertEquals(List.of("dma-nm"), read.getCriteria().getCriteria().get(0).getValues());

        // A null column is the no-membership case and must stay null, not become
        // an empty document.
        PublicationSeries none = aSeries("da");
        none.setCriteria(null);
        seriesService.create(none);
        em.flush();
        em.clear();
        assertNotNull(seriesService.findBySeriesId(none.getSeriesId()));
        org.junit.jupiter.api.Assertions.assertNull(
                seriesService.findBySeriesId(none.getSeriesId()).getCriteria(),
                "a null criteria column came back as something other than null");
    }
}
