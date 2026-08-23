package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * One case per series rule.
 *
 * The rules are recorded as they fire, and any rule with no case fails the run.
 * A validator with eighteen documented rules and twelve implemented ones looks
 * exactly like a working validator right up until the thirteenth is violated.
 */
public class SeriesValidatorTest {

    private static final Set<String> LANGS = new LinkedHashSet<>(List.of("da", "en", "de"));
    private final Set<String> rulesFired = new LinkedHashSet<>();

    // ------------------------------------------------------------------ builder

    /** A series that passes everything, so each case can break exactly one thing. */
    private PublicationSeries valid() {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("efs");
        s.setStatus(SeriesStatus.DRAFT);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setNominalCutoffDay(CutoffDay.MONDAY);
        s.setNominalCutoffTime("09:00");
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setFirstIssueStartsAt(new Date());
        s.setMessagePublication(MessagePublication.NONE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);
        s.setPublicAuthority(PublicAuthority.LEGACY);

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.getLanguages().addAll(List.of("da", "en"));
        for (String lang : List.of("da", "en")) {
            PublicationSeriesDesc d = s.createDesc(lang);
            d.setName("EfS " + lang);
            d.setFileNamePattern("EfS-" + lang + "-${week}-${year}.pdf");
        }
        return s;
    }

    private void assertClean(PublicationSeries s) {
        List<SeriesValidator.FieldError> errors = SeriesValidator.validate(s, LANGS);
        assertTrue(errors.isEmpty(), "the baseline series should be valid, but: " + errors);
    }

    private void assertFires(String rule, PublicationSeries s) {
        List<SeriesValidator.FieldError> errors = SeriesValidator.validate(s, LANGS);
        List<SeriesValidator.FieldError> hits = errors.stream().filter(e -> e.rule().equals(rule)).toList();
        assertFalse(hits.isEmpty(), rule + " did not fire; the errors were " + errors);
        assertTrue(hits.stream().allMatch(h -> h.field() != null && !h.field().isBlank()),
                rule + " fired without naming a field, so it cannot be rendered against the form");
        rulesFired.add(rule);
    }

    // -------------------------------------------------------------- the baseline

    @Test
    public void theBaselineSeriesIsValid() {
        assertClean(valid());
    }

    // -------------------------------------------------- the citable series

    /**
     * A series that can actually be cited passes validation.
     *
     * This is the end-to-end shape, and it is the one that was missing: the token
     * vocabulary and the citation expander were each tested alone and both were
     * right alone. S-14 validated messageReferenceFormat against the STRICT
     * vocabulary, which does not admit the deferred token, while S-13 required a
     * reference format for any series with messagePublication != NONE -- so a
     * citable series could not be saved at all, and neither unit test could see it.
     */
    @Test
    public void aCitableSeriesWithTheCanonicalFormatIsValid() {
        PublicationSeries s = valid();
        s.setMessagePublication(MessagePublication.EXTERNAL);
        s.getDescs().forEach(d -> d.setMessageReferenceFormat("EfS ${week}/${year} ${parameters}"));

        List<SeriesValidator.FieldError> errors = SeriesValidator.validate(s, LANGS);
        assertTrue(errors.isEmpty(),
                "a series with the canonical citation format could not be saved: " + errors);
    }

    /** But the deferred token is still refused where it would reach a public URL. */
    @Test
    public void theDeferredTokenIsStillRefusedInAFileName() {
        PublicationSeries s = valid();
        s.getDescs().forEach(d -> d.setFileNamePattern("EfS-${parameters}.pdf"));

        assertFires("S-14", s);
    }

    // ---------------------------------------------------------- one case per rule

    @BindsRule({"S-1", "S-2", "S-3", "S-4", "S-5", "S-6", "S-7", "S-8", "S-9", "S-10", "S-11", "S-12", "S-13", "S-14", "S-15", "S-16", "S-17", "S-18", "D-7"})

    @Test
    public void everySeriesRuleHasACaseThatTripsIt() {
        // S-1: query-backed but no criteria. This one resolves EVERYTHING.
        PublicationSeries s1 = valid();
        s1.setCriteria(null);
        assertFires("S-1", s1);

        // S-2: query-backed with no liveness answer.
        PublicationSeries s2 = valid();
        s2.setAliveAtCutoff(null);
        assertFires("S-2", s2);

        // S-3: in-force without liveness would empty the issue.
        PublicationSeries s3 = valid();
        s3.setTimeRelation(TimeRelation.IN_FORCE_AT_CUTOFF);
        s3.setAliveAtCutoff(false);
        s3.setFirstIssueStartsAt(null);
        assertFires("S-3", s3);

        // S-4: an interval series with no start.
        PublicationSeries s4 = valid();
        s4.setFirstIssueStartsAt(null);
        assertFires("S-4", s4);

        // S-5: weekly with no weekday.
        PublicationSeries s5 = valid();
        s5.setNominalCutoffDay(null);
        assertFires("S-5", s5);

        // S-6: yearly with no month.
        PublicationSeries s6 = valid();
        s6.setCadence(SeriesCadence.YEARLY);
        s6.setNominalCutoffDay(null);
        s6.setNominalCutoffDayOfMonth(1);
        assertFires("S-6", s6);

        // S-7: a cadence with no time of day.
        PublicationSeries s7 = valid();
        s7.setNominalCutoffTime(null);
        assertFires("S-7", s7);

        // S-8: a one-off that auto-creates a next issue it will never have.
        PublicationSeries s8 = valid();
        s8.setCadence(SeriesCadence.NONE);
        s8.setNominalCutoffDay(null);
        s8.setNominalCutoffTime(null);
        assertFires("S-8", s8);

        // S-9: half the report settings.
        PublicationSeries s9 = valid();
        s9.setReportId("fm-report");
        assertFires("S-9", s9);

        // S-10: a sort field with no direction.
        PublicationSeries s10 = valid();
        s10.setMessageSortBy("AREA");
        assertFires("S-10", s10);

        // S-11: a language the installation does not have.
        PublicationSeries s11 = valid();
        s11.getLanguages().add("fr");
        s11.createDesc("fr").setName("EfS fr");
        assertFires("S-11", s11);

        // S-12: a configured language with no desc row. Falls back to the first
        // desc, so one language is served another's text without erroring.
        PublicationSeries s12 = valid();
        s12.getLanguages().add("de");
        assertFires("S-12", s12);

        // S-13: citable, but with no reference format to cite with.
        PublicationSeries s13 = valid();
        s13.setMessagePublication(MessagePublication.INTERNAL);
        assertFires("S-13", s13);

        // S-14: a token that would survive into a file name and then a URL.
        PublicationSeries s14 = valid();
        s14.getDescs().get(0).setFileNamePattern("EfS-${yeer}.pdf");
        assertFires("S-14", s14);

        // S-15: two languages generating to one path; the last one written wins.
        PublicationSeries s15 = valid();
        s15.getDescs().forEach(d -> d.setFileNamePattern("EfS-${week}.pdf"));
        assertFires("S-15", s15);

        // D-7: a reference format with no name.
        PublicationSeries d7 = valid();
        d7.getDescs().get(0).setName(null);
        d7.getDescs().get(0).setMessageReferenceFormat("EfS ${year}, punkt ${parameters}");
        assertFires("D-7", d7);

        // S-16 and S-18 need a before-and-after, so they have their own entry point.
        PublicationSeries before = valid();
        PublicationSeries renamed = valid();
        renamed.setSeriesId("efs-renamed");
        List<SeriesValidator.FieldError> immutables =
                SeriesValidator.validateImmutables(before, renamed, false);
        assertTrue(immutables.stream().anyMatch(e -> e.rule().equals("S-16")), "S-16 did not fire");
        rulesFired.add("S-16");

        PublicationSeries rechannelled = valid();
        rechannelled.setMessagePublication(MessagePublication.EXTERNAL);
        List<SeriesValidator.FieldError> channel =
                SeriesValidator.validateImmutables(before, rechannelled, true);
        assertTrue(channel.stream().anyMatch(e -> e.rule().equals("S-18")),
                "S-18 did not fire; changing the channel after publishing makes every citation unfindable");
        rulesFired.add("S-18");

        // Changing it BEFORE anything published is fine.
        assertTrue(SeriesValidator.validateImmutables(before, rechannelled, false).stream()
                .noneMatch(e -> e.rule().equals("S-18")), "S-18 fired before any issue had published");

        // S-17: ACTIVE requires everything else green.
        PublicationSeries s17 = valid();
        s17.setStatus(SeriesStatus.ACTIVE);
        s17.setCriteria(null);
        List<SeriesValidator.FieldError> activation = SeriesValidator.validateForActivation(s17, LANGS);
        assertTrue(activation.stream().anyMatch(e -> e.rule().equals("S-17")),
                "S-17 did not fire; an incomplete ACTIVE series is what reaches the picker");
        rulesFired.add("S-17");

        // A DRAFT is allowed to be incomplete.
        PublicationSeries draft = valid();
        draft.setCriteria(null);
        assertTrue(SeriesValidator.validateForActivation(draft, LANGS).stream()
                .noneMatch(e -> e.rule().equals("S-17")), "S-17 fired on a DRAFT, which may be incomplete");

        // --- coverage -----------------------------------------------------
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 18; i++) {
            expected.add("S-" + i);
        }
        List<String> missing = expected.stream().filter(r -> !rulesFired.contains(r)).toList();
        if (!missing.isEmpty()) {
            fail("these series rules have no case, so nothing proves they are implemented: " + missing);
        }
    }

    /** The criteria rules are composed in rather than reimplemented. */
    @Test
    public void theCriteriaRulesAreEnforcedThroughTheSeries() {
        PublicationSeries s = valid();
        // An unscoped query: no messageSeries and no domain node.
        IssueCriteriaVo doc = new IssueCriteriaVo();
        org.niord.core.publication.series.criteria.MessageTypeCriterionVo types =
                new org.niord.core.publication.series.criteria.MessageTypeCriterionVo();
        types.setValues(new ArrayList<>(List.of("TEMPORARY_NOTICE")));
        doc.getCriteria().add(types);
        s.setCriteria(doc);

        List<SeriesValidator.FieldError> errors = SeriesValidator.validate(s, LANGS);
        assertTrue(errors.stream().anyMatch(e -> e.rule().equals("C-6")),
                "an unscoped query passed; it resolves across every message series in the installation");
        assertTrue(errors.stream().filter(e -> e.rule().equals("C-6"))
                        .allMatch(e -> e.field().startsWith("criteria")),
                "a criteria failure must point into the criteria document");
    }
}
