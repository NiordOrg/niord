package org.niord.web.publication;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.vo.PublicationSeriesDescVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S15 validates THE BODY, not the stored row.
 *
 * The endpoint used to look the series up by id and validate whatever was saved,
 * which answers a question the form never asks -- it already has the saved state
 * and wants to know about the edit in front of it. The failure mode that matters
 * is the create form: a series with no stored row returned an EMPTY list, so a
 * screen that could not produce a saveable series was told it had no problems.
 *
 * Nothing caught it because every test of these rules called SeriesValidator
 * directly. The validator was right the whole time; the wiring handed it the wrong
 * subject. So these tests go through the endpoint's own code path -- the extracted
 * static is that path, not a paraphrase of it.
 */
public class SeriesValidateEndpointTest {

    private static final Set<String> INSTALLATION_LANGUAGES = Set.of("da", "en");

    /** The regression: an unsaved series is validated on its merits, not waved through. */
    @Test
    public void aSeriesThatWasNeverSavedIsStillValidated() {
        SystemPublicationSeriesVo vo = new SystemPublicationSeriesVo();
        vo.setSeriesId("brand-new-series-that-does-not-exist");
        vo.setStatus(SeriesStatus.ACTIVE.name());
        vo.setContentMode(ContentMode.GENERATED_FROM_QUERY.name());
        vo.setCadence(SeriesCadence.WEEKLY.name());
        // No criteria, no timeRelation, no languages, no descs, no schedule: this
        // series breaks most of S-1 to S-12 and cannot be created.

        List<Map<String, String>> errors =
                PublicationSeriesRestService.validationReport(vo, INSTALLATION_LANGUAGES);

        assertFalse(errors.isEmpty(),
                "an unsaved series reported zero problems. A create form would show 'no problems' "
                        + "for a series the server will refuse, and the admin finds out on save");
        assertTrue(errors.stream().anyMatch(e -> "S-1".equals(e.get("rule"))),
                "expected S-1 (a query-backed series must carry criteria), got "
                        + errors.stream().map(e -> e.get("rule")).toList());
    }

    /**
     * The edit in the form is what gets judged, even when a valid row is stored.
     *
     * This is the other half: validating the stored series would report nothing
     * here, because what is broken exists only in the body.
     */
    @Test
    public void theBodyIsTheSubjectRatherThanWhateverIsStored() {
        SystemPublicationSeriesVo vo = completeSeries();
        vo.setCriteria(null); // the edit that breaks it

        List<Map<String, String>> errors =
                PublicationSeriesRestService.validationReport(vo, INSTALLATION_LANGUAGES);

        assertTrue(errors.stream().anyMatch(e -> "criteria".equals(e.get("field"))),
                "removing the criteria in the form reported nothing against the criteria field, so "
                        + "the stored series was validated instead of the edit. Got "
                        + errors.stream().map(e -> e.get("field")).toList());
    }

    /** Every row names the rule and the field, so the form can render it in place. */
    @Test
    public void everyErrorNamesItsRuleAndItsField() {
        SystemPublicationSeriesVo vo = new SystemPublicationSeriesVo();
        vo.setSeriesId("incomplete");
        vo.setContentMode(ContentMode.GENERATED_FROM_QUERY.name());

        List<Map<String, String>> errors =
                PublicationSeriesRestService.validationReport(vo, INSTALLATION_LANGUAGES);
        assertFalse(errors.isEmpty());

        for (Map<String, String> e : errors) {
            assertTrue(e.get("rule") != null && !e.get("rule").isBlank(), "a row with no rule: " + e);
            assertTrue(e.get("field") != null && !e.get("field").isBlank(),
                    "a row with no field cannot be shown against the input that caused it: " + e);
            assertTrue(e.get("message") != null && !e.get("message").isBlank(),
                    "a row with no message: " + e);
        }
    }

    /** A null body is a caller error, not a 500. */
    @Test
    public void aMissingBodyReportsNothingRatherThanThrowing() {
        assertTrue(PublicationSeriesRestService.validationReport(null, INSTALLATION_LANGUAGES).isEmpty());
    }

    /**
     * A language the installation does not run is reported.
     *
     * The old call passed null for the installation languages, which switched this
     * check off entirely -- so the one rule that needs context from outside the
     * series was the one rule that never ran.
     */
    @Test
    public void aLanguageTheInstallationDoesNotRunIsReported() {
        SystemPublicationSeriesVo vo = completeSeries();
        vo.getLanguages().add("de");
        PublicationSeriesDescVo german = new PublicationSeriesDescVo();
        german.setLang("de");
        german.setName("Deutsche Ausgabe");
        vo.getDescs().add(german);

        List<Map<String, String>> errors =
                PublicationSeriesRestService.validationReport(vo, INSTALLATION_LANGUAGES);

        assertTrue(errors.stream().anyMatch(e -> "S-11".equals(e.get("rule"))),
                "a series declaring a language the installation does not run passed S-11, which means "
                        + "the installation languages were not passed through. Got "
                        + errors.stream().map(e -> e.get("rule")).toList());
    }

    /**
     * S-19: a series with no category is reported, not left to the flush.
     *
     * PublicationSeries.category is NOT NULL. Before the rule existed, creating a
     * series without one died inside Hibernate with "not-null property references a
     * null or transient value" -- a 500 naming a Java field, against an admin who
     * had simply left a dropdown alone.
     *
     * The SECOND time this column has been found unset. The importer was given
     * planCategoryOf after the first; the interactive create path never got the
     * equivalent, so the same defect sat on a route nobody had walked until a
     * settings screen walked it.
     */
    @Test
    public void aSeriesWithNoCategoryIsReported() {
        List<Map<String, String>> errors =
                PublicationSeriesRestService.validationReport(completeSeries(), INSTALLATION_LANGUAGES);

        assertTrue(errors.stream().anyMatch(e -> "S-19".equals(e.get("rule"))),
                "a series with no category passed validation, so the NOT NULL column is still "
                        + "discovered at flush time as a 500. Got "
                        + errors.stream().map(e -> e.get("rule")).toList());
        assertTrue(errors.stream().filter(e -> "S-19".equals(e.get("rule")))
                        .allMatch(e -> "categoryId".equals(e.get("field"))),
                "S-19 must name categoryId, so the form can render it against the dropdown");
    }

    /**
     * NAMING an id-backed reference is enough to satisfy the rule that requires it.
     *
     * Category and domain are entities on the series and ids on the wire, and
     * updateFromVo bridges neither -- resolveReferences does, and validation runs
     * without a persistence context. So a rule reading the unresolved entity fires
     * on every series that has a perfectly good one, and since activation is gated
     * on a clean report, nothing can ever be activated.
     *
     * ONE TEST OVER BOTH, on purpose. This happened twice: fixed for the category
     * when S-19 landed, then reproduced exactly on the domain when S-20 arrived,
     * because the first fix and its test both named the instance instead of the
     * shape. A third id-backed rule should fail here rather than in production.
     */
    @Test
    public void namingAnIdBackedReferenceSatisfiesTheRuleThatRequiresIt() {
        SystemPublicationSeriesVo vo = completeSeries();
        vo.setCategoryId("dk-dma-internal-publications");
        vo.setDomainId("niord-nm");

        List<Map<String, String>> errors =
                PublicationSeriesRestService.validationReport(vo, INSTALLATION_LANGUAGES);

        List<String> onNamedReferences = errors.stream()
                .filter(e -> "categoryId".equals(e.get("field")) || "domainId".equals(e.get("field")))
                .map(e -> e.get("rule") + " (" + e.get("field") + ")")
                .toList();

        assertTrue(onNamedReferences.isEmpty(),
                "a series naming both references still failed " + onNamedReferences
                        + ", so every report carries a false positive and no series can be "
                        + "activated at all");
    }

    /**
     * A series that NAMES a category does not trip S-19.
     *
     * The category is an entity on the series and an id on the wire, and
     * updateFromVo does not bridge them -- resolveReferences does, and validation
     * runs without a persistence context. So the rule read the unresolved entity,
     * fired on every series that had a perfectly good category, and made "Check
     * rules" impossible to satisfy: activation is gated on a clean report, so it
     * could never be offered at all.
     *
     * Caught by rehearsing a full week through the UI rather than by any test here,
     * which is the argument for rehearsing.
     */
    @Test
    public void aSeriesThatNamesACategoryPassesSNineteen() {
        SystemPublicationSeriesVo vo = completeSeries();
        vo.setCategoryId("dk-dma-internal-publications");

        List<Map<String, String>> errors =
                PublicationSeriesRestService.validationReport(vo, INSTALLATION_LANGUAGES);

        assertTrue(errors.stream().noneMatch(e -> "S-19".equals(e.get("rule"))),
                "a series naming a category still failed S-19, so every report carries a false "
                        + "positive and no series can ever be activated. Got "
                        + errors.stream().map(e -> e.get("rule")).toList());
    }

    /**
     * The create template contradicts no rule it chose both halves of.
     *
     * A DRAFT is allowed to be incomplete, so the template legitimately fails the
     * rules whose fields the ADMIN supplies: criteria, a first interval, names, a
     * category. What it must never do is fail a rule where IT picked both sides.
     *
     * It did, twice. It emitted a desc row per configured language and left
     * `languages` empty, so S-12 reported "a desc row for da, which is not a
     * configured language" -- about a language the same screen had just offered.
     * And it paired cadence NONE with AUTO_ON_PUBLISH, which S-8 refuses. The form
     * corrected both silently on save, which is the worst place for a template to
     * be wrong: it works until somebody uses the endpoint without that form.
     *
     * Asserted as a SET DIFFERENCE rather than a list of expected errors, so a new
     * self-contradiction fails here even though nobody thought to look for it.
     */
    @Test
    public void theCreateTemplateOnlyFailsRulesTheAdminMustFillIn() {
        SystemPublicationSeriesVo template =
                PublicationSeriesRestService.newSeriesTemplate(new String[]{"da", "en"});

        // Fields a create form exists to collect. Everything else is the template's.
        // domainId joins these: the template cannot know which domain a new series
        // belongs to, and the domain is what carries its timezone (S-20).
        // reportId joins them for the same reason: a query-backed series prints
        // with a report (S-1), and which report is a choice about the publication
        // that no template can make on the admin's behalf.
        Set<String> adminSupplies =
                Set.of("criteria", "firstIssueStartsAt", "categoryId", "domainId", "reportId");

        List<String> selfContradictions =
                PublicationSeriesRestService.validationReport(template, INSTALLATION_LANGUAGES)
                        .stream()
                        .filter(e -> !adminSupplies.contains(e.get("field")))
                        .filter(e -> !e.get("field").startsWith("descs."))
                        .map(e -> e.get("rule") + " (" + e.get("field") + "): " + e.get("message"))
                        .toList();

        assertTrue(selfContradictions.isEmpty(),
                "the template disagrees with itself on " + selfContradictions.size()
                        + " rule(s), none of which an admin can fix by filling the form in:"
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), selfContradictions));
    }

    /** A minimally complete query-backed weekly series, used as the baseline to break. */
    private static SystemPublicationSeriesVo completeSeries() {
        SystemPublicationSeriesVo vo = new SystemPublicationSeriesVo();
        vo.setSeriesId("weekly-thing");
        vo.setStatus(SeriesStatus.DRAFT.name());
        vo.setContentMode(ContentMode.GENERATED_FROM_QUERY.name());
        vo.setCadence(SeriesCadence.WEEKLY.name());
        vo.getLanguages().add("da");
        PublicationSeriesDescVo desc = new PublicationSeriesDescVo();
        desc.setLang("da");
        desc.setName("Ugentlig");
        vo.getDescs().add(desc);
        return vo;
    }
}
