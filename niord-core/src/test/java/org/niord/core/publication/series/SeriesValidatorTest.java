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

import org.junit.jupiter.api.Test;
import org.niord.core.domain.Domain;
import org.niord.core.publication.PublicationCategory;
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
        s.setNominalCutoffDay(CutoffDay.WEDNESDAY);
        s.setNominalCutoffTime("09:00");
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        // S-1's third leg: a query-backed series names the report it prints with,
        // and S-9 wants the three print settings alongside it. The baseline
        // carries all four so a case can break exactly one.
        s.setReportId("fm-report");
        s.setPageSize(PageSize.A4);
        s.setPageOrientation(PageOrientation.PORTRAIT);
        s.setMapThumbnails(Boolean.FALSE);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setFirstIssueStartsAt(new Date());
        s.setMessagePublication(MessagePublication.NONE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        // S-19: the category column is NOT NULL, so a baseline without one is not a
        // valid series -- it is one that fails at flush rather than at validation.
        s.setCategory(new PublicationCategory());
        // S-20: the domain carries the timezone the cut-offs are read in, so a
        // baseline without one is a series whose schedule has no zone -- and so is
        // one whose domain carries no readable zone name, which is why the
        // baseline sets a real one rather than a bare Domain.
        s.setDomain(domainIn("Europe/Copenhagen"));

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

    /**
     * Every weekday is expressible, and Wednesday in particular.
     *
     * The weekly EfS is released every Wednesday and S-5 makes this field
     * required for a WEEKLY series, so an enum missing Wednesday means the
     * primary production series cannot record its own release day. It held
     * MONDAY and SUNDAY alone: the specification writes the type as
     * "MONDAY...SUNDAY" and its DDL column transcribed that ellipsis as a
     * two-element list, the schema followed the DDL and the enum followed the
     * schema -- so all three agreed with each other and none with the domain.
     *
     * Asserted as a COUNT as well as by name, because the failure was a set that
     * looked plausible rather than a value that looked wrong.
     */
    @Test
    public void everyWeekdayIsExpressibleAsACutOffDay() {
        assertEquals(7, CutoffDay.values().length,
                "CutoffDay holds " + java.util.Arrays.toString(CutoffDay.values())
                        + "; a weekly series must be able to name any release day");

        for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) {
            CutoffDay.valueOf(d.name());
        }

        PublicationSeries s = valid();
        s.setNominalCutoffDay(CutoffDay.WEDNESDAY);
        assertClean(s);
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

        // S-1 again: query-backed but naming no report. It has something to select
        // and nothing to print with, so its issues would publish with no file.
        PublicationSeries s1b = valid();
        s1b.setReportId(null);
        s1b.setPageSize(null);
        s1b.setPageOrientation(null);
        s1b.setMapThumbnails(null);
        assertFires("S-1", s1b);

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

        // S-4, the other way: a ONE-OFF is exempt. The field answers "where does
        // the first of a sequence of periods open", and a publication that comes
        // out once has one issue whose own interval is the whole answer. The
        // one-off form nulls the field and never renders it, so demanding it here
        // stranded a query-backed one-off in DRAFT forever.
        PublicationSeries oneOff = valid();
        oneOff.setKind(SeriesKind.ONE_OFF);
        oneOff.setCadence(SeriesCadence.NONE);
        oneOff.setNominalCutoffDay(null);
        oneOff.setNominalCutoffTime(null);
        oneOff.setNumberingScheme(NumberingScheme.NONE);
        oneOff.setNextIssueCreation(NextIssueCreation.MANUAL);
        oneOff.setFirstIssueStartsAt(null);
        assertDoesNotFire("S-4", oneOff);

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
        s9.setPageSize(null);
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
    // ------------------------------------------------------------------- S-20

    /** A cadence-less series, which has no nominal cut-off fields at all. */
    private PublicationSeries cadenceless() {
        PublicationSeries s = valid();
        s.setCadence(SeriesCadence.NONE);
        s.setKind(SeriesKind.ONE_OFF);
        s.setContentMode(ContentMode.UPLOADED_FILE);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setNominalCutoffDay(null);
        s.setNominalCutoffTime(null);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setCriteria(null);
        return s;
    }

    private void assertDoesNotFire(String rule, PublicationSeries s) {
        List<SeriesValidator.FieldError> hits = SeriesValidator.validate(s, LANGS).stream()
                .filter(e -> e.rule().equals(rule)).toList();
        assertTrue(hits.isEmpty(), rule + " fired when it should not have: " + hits);
    }

    /**
     * A series with a cadence still needs a domain: its cut-offs have to be read
     * in some zone, and the domain is the only place one comes from.
     */
    @Test
    public void acadencedSeriesStillNeedsADomain() {
        PublicationSeries s = valid();
        s.setDomain(null);

        assertFires("S-20", s);
    }

    /**
     * A cadence-less one does NOT, and this is the case the old rule got wrong.
     *
     * S-5, S-6 and S-7 refuse every nominalCutoff* field on a cadence-less
     * series, so it has no cut-off to read in any zone and the timezone argument
     * does not reach it. What requiring a domain DID do was narrow visibility:
     * the publication picker matches "domain IS NULL OR domain = the current",
     * so four publications that every domain cites had to be filed under one.
     */
    @Test
    public void acadencelessSeriesMayHaveNoDomain() {
        PublicationSeries s = cadenceless();
        s.setDomain(null);

        assertDoesNotFire("S-20", s);
    }

    /** And it may still HAVE one -- null is permitted, not mandated. */
    @Test
    public void acadencelessSeriesMayAlsoCarryADomain() {
        PublicationSeries s = cadenceless();
        s.setDomain(domainIn("Europe/Copenhagen"));

        assertDoesNotFire("S-20", s);
    }

    /**
     * S-20's other half: a domain that names no zone, or names one nothing can
     * read, is not a source of a timezone either.
     *
     * TimeZone.getTimeZone answers GMT for anything it does not recognise, so a
     * misspelt zone does not fail anywhere -- it shifts every cut-off of the
     * series by the offset nobody configured, and at the year boundary that is a
     * different year printed on the cover.
     */
    @Test
    public void aDomainWithNoReadableTimezoneIsNotASourceOfOne() {
        PublicationSeries blank = valid();
        blank.setDomain(domainIn(null));
        assertFires("S-20", blank);

        PublicationSeries misspelt = valid();
        misspelt.setDomain(domainIn("Europe/Kopenhagen"));
        assertFires("S-20", misspelt);
    }

    private static Domain domainIn(String zone) {
        Domain d = new Domain();
        d.setDomainId("dma-test");
        d.setTimeZone(zone);
        return d;
    }

    // ------------------------------------------------------------------- S-21

    /** The weekly shape is cut off at the release; a calendar default there is refused. */
    @Test
    public void aweeklySeriesIsCutOffAtTheRelease() {
        PublicationSeries s = valid();
        assertDoesNotFire("S-21", s);

        s.setCutoffDefault(CutoffDefault.PERIOD_START);
        assertFires("S-21", s);
    }

    /** A yearly series may be cut off where its period opens or closes. */
    @Test
    public void ayearlySeriesMayBeCutOffAtAPeriodBoundary() {
        PublicationSeries s = valid();
        s.setCadence(SeriesCadence.YEARLY);
        s.setNominalCutoffDay(null);
        s.setNominalCutoffDayOfMonth(1);
        s.setNominalCutoffMonth(1);
        s.setNumberingScheme(NumberingScheme.YEAR_EDITION);

        s.setCutoffDefault(CutoffDefault.PERIOD_START);
        assertDoesNotFire("S-21", s);
        s.setCutoffDefault(CutoffDefault.PERIOD_END);
        assertDoesNotFire("S-21", s);
        s.setCutoffDefault(CutoffDefault.RELEASE_MOMENT);
        assertDoesNotFire("S-21", s);
    }

    /** A series always says where its cut-off falls. */
    @Test
    public void thecutoffDefaultIsRequired() {
        PublicationSeries s = valid();
        s.setCutoffDefault(null);

        assertFires("S-21", s);
    }

    /** The shape rule the importer and the create form share. */
    @Test
    public void theDefaultFollowsTheShape() {
        assertEquals(CutoffDefault.RELEASE_MOMENT,
                CutoffDefault.forShape(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL));
        assertEquals(CutoffDefault.RELEASE_MOMENT,
                CutoffDefault.forShape(SeriesCadence.WEEKLY, TimeRelation.IN_FORCE_AT_CUTOFF));
        assertEquals(CutoffDefault.PERIOD_START,
                CutoffDefault.forShape(SeriesCadence.YEARLY, TimeRelation.IN_FORCE_AT_CUTOFF));
        assertEquals(CutoffDefault.PERIOD_END,
                CutoffDefault.forShape(SeriesCadence.YEARLY, TimeRelation.PUBLISHED_IN_INTERVAL));
        assertEquals(CutoffDefault.RELEASE_MOMENT,
                CutoffDefault.forShape(SeriesCadence.NONE, null));
    }

    // ------------------------------------------------------- the hard rules

    /** Automatic release is modelled and not built; a series asking for it is refused. */
    @Test
    public void automaticReleaseIsRefusedUntilItExists() {
        PublicationSeries s = valid();
        assertDoesNotFire("S-22", s);

        s.setReleaseMode(ReleaseMode.AUTO_RELEASE);
        assertFires("S-22", s);
    }

    /** The four report parameters the issue supplies cannot be typed, in any spelling. */
    @Test
    public void reservedReportParametersAreRefusedInAnySpelling() {
        PublicationSeries s = valid();
        s.getReportParams().put("landscape", "true");
        assertDoesNotFire("S-23", s);

        for (String key : List.of("week", "weekTo", "WEEKTO", " year ", "Edition")) {
            PublicationSeries t = valid();
            t.getReportParams().put(key, "1");
            assertFires("S-23", t);
            assertTrue(SeriesValidator.validate(t, LANGS).stream()
                            .anyMatch(e -> "S-23".equals(e.rule()) && e.field().startsWith("reportParams.")),
                    "named against the row, so the form can show it there: " + key);
        }
    }

    /**
     * The hard rules are the ones a DRAFT may not break either: a draft may be
     * incomplete, but not wrong. Everything else waits for activation.
     */
    @Test
    public void theHardRulesAreExactlyTheTwoADraftMayNotBreak() {
        assertEquals(Set.of("S-22", "S-23"), SeriesValidator.HARD_RULES);

        PublicationSeries incomplete = valid();
        incomplete.setNominalCutoffDay(null);
        incomplete.setReportId(null);
        assertTrue(SeriesValidator.hardRules(incomplete).isEmpty(),
                "an incomplete draft breaks no hard rule");

        PublicationSeries wrong = valid();
        wrong.setReleaseMode(ReleaseMode.AUTO_RELEASE);
        wrong.getReportParams().put("week", "12");
        assertEquals(List.of("S-22", "S-23"),
                SeriesValidator.hardRules(wrong).stream().map(SeriesValidator.FieldError::rule).toList());
    }

}
