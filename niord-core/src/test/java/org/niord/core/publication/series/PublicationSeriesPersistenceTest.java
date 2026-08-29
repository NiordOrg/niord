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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.PublicationSeriesVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;
import org.niord.model.DataFilter;

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

    // ------------------------------------------------- updating an existing series

    /**
     * A saved series can be EDITED, which is not the same as being saved once.
     *
     * updateFromVo clears the desc list and rebuilds it, and the desc table is
     * unique on (lang, entity_id). Hibernate orders inserts before deletes inside a
     * flush, so re-adding "da" while the old "da" row is still there violates that
     * constraint -- and the flush happens wherever the next query does, which was
     * inside a category lookup whose catch-all reported the failure as "no such
     * publication category". The series had a perfectly good category.
     *
     * Every test here created a series and stopped. Nothing had edited one, so the
     * second save -- the ordinary one, the one an admin does most -- was never run.
     */
    @Test
    @Transactional
    public void aSavedSeriesCanBeEditedAndSavedAgain() {
        PublicationSeries saved = seriesService.create(aSeries("da", "en"));
        em.flush();

        SystemPublicationSeriesVo vo = saved.toVo(SystemPublicationSeriesVo.class);
        vo.getDescs().forEach(d -> d.setName("Renamed in " + d.getLang()));

        PublicationSeries reloaded = seriesService.findBySeriesId(saved.getSeriesId());
        reloaded.updateFromVo(vo);

        // The flush is what fails, so it has to be forced here rather than left to
        // whatever query happens to run next and swallow it.
        em.flush();

        assertEquals(2, reloaded.getDescs().size());
        assertTrue(reloaded.getDescs().stream().allMatch(d -> d.getName().startsWith("Renamed")),
                "the edit did not survive: " + reloaded.getDescs().stream().map(d -> d.getName()).toList());
    }

    /** A language dropped from the payload is a language deleted. */
    @Test
    @Transactional
    public void alanguageRemovedFromThePayloadIsRemovedFromTheSeries() {
        PublicationSeries saved = seriesService.create(aSeries("da", "en"));
        em.flush();

        SystemPublicationSeriesVo vo = saved.toVo(SystemPublicationSeriesVo.class);
        vo.getDescs().removeIf(d -> "en".equals(d.getLang()));
        vo.getLanguages().remove("en");

        PublicationSeries reloaded = seriesService.findBySeriesId(saved.getSeriesId());
        reloaded.updateFromVo(vo);
        em.flush();

        assertEquals(List.of("da"), reloaded.getDescs().stream().map(d -> d.getLang()).toList(),
                "a desc the client did not send is one the client deleted");
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

    // ------------------------------------------------------- the language filter

    /**
     * A read for one language gets that language, and nothing else.
     *
     * The rule used to live in a private helper on the resource, where this
     * module cannot reach it -- niord-web has no container tests, so the one
     * thing every consumer of the series list depends on had no assertion behind
     * it at all.
     */
    @Test
    @Transactional
    public void aLanguageFilterNarrowsTheDescRowsToThatLanguage() {
        PublicationSeries saved = seriesService.create(aSeries("da", "en"));
        em.flush();

        PublicationSeriesVo vo = saved.toVo(PublicationSeriesVo.class, DataFilter.get().lang("en"));
        assertEquals(1, vo.getDescs().size(), "both languages travelled where one was asked for");
        assertEquals("en", vo.getDescs().get(0).getLang());
    }

    /**
     * A language the series does not have falls back to one it does.
     *
     * Not a nicety: a one-language series is entirely legitimate, and answering a
     * request for English with an empty descs array leaves every consumer
     * rendering the raw seriesId in place of a name.
     */
    @Test
    @Transactional
    public void anAbsentLanguageFallsBackRatherThanEmptying() {
        PublicationSeries saved = seriesService.create(aSeries("da", "en"));
        em.flush();

        PublicationSeriesVo vo = saved.toVo(PublicationSeriesVo.class, DataFilter.get().lang("de"));
        assertEquals(1, vo.getDescs().size(),
                "a language the series does not carry emptied the desc list instead of falling back");
        assertNotNull(vo.getDescs().get(0).getName());
    }

    /** No language asked for is every language, and a single-language series is never narrowed. */
    @Test
    @Transactional
    public void noLanguageAskedForKeepsThemAll() {
        PublicationSeries two = seriesService.create(aSeries("da", "en"));
        PublicationSeries one = seriesService.create(aSeries("da"));
        em.flush();

        assertEquals(2, two.toVo(PublicationSeriesVo.class, null).getDescs().size());
        assertEquals(2, two.toVo(PublicationSeriesVo.class, DataFilter.get()).getDescs().size());
        assertEquals(1, one.toVo(PublicationSeriesVo.class, DataFilter.get().lang("en")).getDescs().size(),
                "a one-language series was narrowed away from the only language it has");
    }

    /**
     * The patterns are AUTHORING and travel only on the system shape.
     *
     * The lean shape is what every logged-in caller gets; handing it the file-name
     * and link patterns publishes the naming and repository layout of every future
     * issue to an audience with no use for either.
     */
    @Test
    @Transactional
    public void thePatternsTravelOnlyOnTheSystemShape() {
        PublicationSeries saved = seriesService.create(aSeries("da"));
        em.flush();

        assertNotNull(saved.toVo(SystemPublicationSeriesVo.class).getDescs().get(0)
                .getNameSuggestionPattern(), "the system shape lost the pattern");
        org.junit.jupiter.api.Assertions.assertNull(
                saved.toVo(PublicationSeriesVo.class).getDescs().get(0).getNameSuggestionPattern(),
                "the lean shape carried an authoring pattern");
    }
}
