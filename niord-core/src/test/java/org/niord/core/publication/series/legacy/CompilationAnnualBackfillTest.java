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

package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.domain.Domain;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.CutoffDefault;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.MemberSource;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.TestOwnerDomain;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The years the archive stops before, opened by the import.
 *
 * THE DEFECT THIS EXISTS FOR is invisible rather than loud. The accumulated
 * annual NtM is in legacy up to its last hand-assembled edition and then stops,
 * so the years since carry no issue at all -- and a series whose newest period
 * ended several cadence periods ago reads as DORMANT, which switches gap
 * detection off. Nothing then reports the missing years as missing, and the only
 * remedy was somebody opening eight issues by hand in the go-live window, again
 * after every rehearsal.
 *
 * THE SCOPE IS THE RULING AND NEVER THE CADENCE, which is what the plan-level
 * assertions here pin. A compilation's content for a year nobody assembled is
 * still derivable -- it is the union of the source series' published issues -- so
 * an issue opened for it is a period waiting to be compiled. Every other yearly
 * in the estate has nothing to backfill from, and must come out of the import
 * exactly as legacy has it.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class CompilationAnnualBackfillTest {

    /** The owner's zone, which is the only zone a publication's year is read in. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Copenhagen");

    /**
     * The fixture's clock, fixed rather than read off the wall.
     *
     * Every expectation below counts years up to it, and a fixture anchored on now
     * would assert a different number of issues each January.
     */
    private static final Date NOW = Date.from(
            ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, ZONE).toInstant());

    /** The estate's own pattern, so what comes out is the name the estate will show. */
    private static final String NAME_PATTERN = "Accumulated NtM - ${year}";

    @Inject
    LegacyImportService importService;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    private LegacyImportService.Plan plan() {
        return importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());
    }

    // ------------------------------------------------------- the shape in memory

    /**
     * Eight OPEN annuals, one per year the archive is short of, bounded by the year.
     *
     * The bounds are the pair the create dialog proposes for a YEARLY series whose
     * cut-off falls on the period's own end: 1 January 00:00:00.000 to 31 December
     * 23:59:59.999, read in the owner's zone rather than the server's. Both are
     * NOMINAL, because nothing has been released and a nominal close is exactly
     * what an issue waiting to be published carries.
     */
    @Test
    public void theMissingYearsAreOpenedOnePerYearAndBoundedByIt() {
        PublicationSeries series = compilationSeries();
        List<PublicationIssue> archive = annuals(series, 2002, 2018);

        List<Integer> years = CompilationBackfill.missingYears(series, archive, NOW);

        assertEquals(List.of(2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026), years,
                "the backfill runs from the year after the newest annual up to and including the "
                        + "year now falls in, and 2026 is as much a missing year as 2019 is");

        for (int year : years) {
            PublicationIssue issue = CompilationBackfill.annualFor(series, year);

            assertEquals(IssueStatus.OPEN, issue.getStatus(),
                    year + " was opened in some state other than OPEN; nothing has been released");
            assertEquals(jan1(year), issue.getIntervalFrom(),
                    year + " does not open at 1 January 00:00 in the owner's zone");
            assertEquals(dec31(year), issue.getIntervalTo(),
                    year + " does not close at the last millisecond of 31 December in the owner's "
                            + "zone -- the bound the draft would have proposed");
            assertEquals(IntervalBoundSource.NOMINAL, issue.getIntervalFromSource());
            assertEquals(IntervalBoundSource.NOMINAL, issue.getIntervalToSource());
            assertNull(issue.getCutoffStampedAt(),
                    year + " carries a cut-off stamp, which says it was released");
            assertNull(issue.getPublishedAt());
            assertNull(issue.getLegacyPublicationId(),
                    year + " claims to have been imported from a legacy row, and there is none");
            assertNotNull(issue.getPublicId(), "an issue with no public id is uncitable");
        }
    }

    /**
     * The year is the CALENDAR year, and the week is a single week.
     *
     * An annual closing at 31 December 23:59 falls inside ISO week 1 of the
     * following year, so the week-based answer would name the 2019 edition 2020 --
     * in its title, in its file name, and therefore in the public link it is cited
     * by. The cut-off default is what settles it, and it arrives with the
     * compilation ruling, which is why these issues are shaped after the
     * conversion rather than before it.
     *
     * And a single week rather than a range: a span is a WEEKLY publication's way
     * of saying its window swallowed a period no issue closed. A year-long window
     * offered as a span reads as fifty-two of them, and the annual was once stored
     * as "weeks 1 to 52".
     */
    @Test
    public void theYearIsTheCalendarYearAndTheWeekIsNotARange() {
        PublicationSeries series = compilationSeries();

        PublicationIssue issue = CompilationBackfill.annualFor(series, 2019);

        assertEquals(Integer.valueOf(2019), issue.getYear(),
                "the 2019 edition is numbered for the year it closes in; the ISO week-based answer "
                        + "would call it 2020 and the download link would say so");
        assertNull(issue.getWeekTo(),
                "an annual edition is one period, not a range of weeks");
        assertNotNull(issue.getWeek(), "the report header prints 'Uge , ' without one");
    }

    /** Every configured language gets a row, named from that language's pattern. */
    @Test
    public void everyLanguageGetsARowNamedFromItsOwnPattern() {
        PublicationSeries series = compilationSeries();

        PublicationIssue issue = CompilationBackfill.annualFor(series, 2019);

        assertEquals(Set.of("da", "en"),
                issue.getDescs().stream().map(PublicationIssueDesc::getLang)
                        .collect(Collectors.toSet()),
                "a language the series declares and the issue has no row for has nowhere to put "
                        + "its file name or its link");
        for (PublicationIssueDesc desc : issue.getDescs()) {
            assertEquals("Accumulated NtM - 2019", desc.getName(),
                    "the name must be what the series' own suggestion pattern expands to, so the "
                            + "eight rows the import leaves behind read like the ninth somebody "
                            + "creates next January");
            assertFalse(desc.isNameOverridden(),
                    "nobody typed this name; flagging it would freeze it against the restamp");
        }
    }

    /**
     * A series the archive holds nothing for is not backfilled at all.
     *
     * That is not a gap: a compilation with no annual ever is a publication whose
     * first issue is a decision nobody has taken, and opening one from the first
     * year its source series happens to cover would invent a publication history.
     */
    @Test
    public void aSeriesWithNoImportedIssueIsNotBackfilled() {
        PublicationSeries series = compilationSeries();

        assertTrue(CompilationBackfill.missingYears(series, List.of(), NOW).isEmpty(),
                "a compilation that never had an annual was given one");
    }

    /** An archive already up to date is left alone. */
    @Test
    public void anArchiveThatReachesThisYearIsLeftAlone() {
        PublicationSeries series = compilationSeries();

        assertTrue(CompilationBackfill.missingYears(series, annuals(series, 2020, 2026), NOW)
                        .isEmpty(),
                "the archive already covers every year up to now, and the import opened one anyway");
    }

    // --------------------------------------------------------- the plan's scope

    /**
     * THE RULING AND NOT THE CADENCE. Only the compiled series is backfilled.
     *
     * The estate carries other YEARLY publications -- the EfS annex and the firing
     * practice areas -- whose newest edition is also older than this year, so a
     * backfill keyed on the cadence would open issues on them too. It must not.
     * The years a compilation is missing are derivable from its source series'
     * published issues; a yearly whose content is not derivable has nothing to
     * backfill from, and an issue opened for it would be an empty row claiming a
     * period nobody published.
     */
    @Test
    public void onlyASeriesTheCompilationRulingShapedIsBackfilled() {
        LegacyImportService.Plan plan = plan();

        for (String seriesId : plan.compilationAnnuals().keySet()) {
            assertNotNull(LegacyTemplateRulings.compilationFor(seriesId),
                    "'" + seriesId + "' is backfilled and carries no compilation ruling; the rule "
                            + "is keyed on the ruling, never on the cadence");
        }

        List<PublicationSeries> plainYearlies = plan.series().stream()
                .filter(s -> s.getCadence() == SeriesCadence.YEARLY)
                .filter(s -> LegacyTemplateRulings.compilationFor(s.getSeriesId()) == null)
                .toList();

        assertFalse(plainYearlies.isEmpty(),
                "the estate no longer carries a yearly series outside the compilation ruling, so "
                        + "this test is asserting nothing rather than finding a bug");

        for (PublicationSeries yearly : plainYearlies) {
            assertFalse(plan.compilationAnnuals().containsKey(yearly.getSeriesId()),
                    "'" + yearly.getSeriesId() + "' is a plain yearly and the import opened issues "
                            + "on it; its content is not derivable from anything, so there is "
                            + "nothing to backfill from");
        }
    }

    /**
     * The plan WRITES no issue, and reports every one it would create.
     *
     * The dry run and the real run return the same report, so an admin reading a
     * dry run is reading what the real run will do -- and the number has to be
     * there before anything is written, or the preview is thinner than the thing
     * it previews.
     */
    @Test
    public void thedryRunCreatesNothingAndReportsWhatItWould() {
        LegacyImportService.Plan plan = plan();

        int planned = plan.compilationAnnuals().values().stream().mapToInt(List::size).sum();
        assertEquals(planned, plan.report().getCompilationIssuesCreated(),
                "the report's count disagrees with the years the plan settled on");
        assertTrue(planned > 0,
                "the captured estate's accumulated annual stops years ago, so this must plan at "
                        + "least one -- zero means the rule did not run");

        List<String> notes = plan.report().getNotes().stream()
                .filter(n -> LegacyImportService.COMPILATION_ISSUE_CREATED.equals(n.getCode()))
                .map(LegacyImportReportVo.ProblemVo::getDetail)
                .toList();
        assertEquals(planned, notes.size(),
                "one note per created issue, naming the series and the year: a single line saying "
                        + "'eight created' names none of them");

        for (PublicationIssue issue : plan.issues().values()) {
            assertNotNull(issue.getLegacyPublicationId(),
                    "planning built an issue that came from no legacy row; the annuals are shaped "
                            + "at apply time, after the compilation ruling has converted the series");
        }
    }

    // ------------------------------------------------------------- the chain

    /**
     * Publishing the first backfilled year opens no successor, and the next year is
     * already there.
     *
     * THE FAILURE THIS GUARDS AGAINST is eight issues plus a ninth nobody asked
     * for. A series set to open its next issue on publish would otherwise mint one
     * beside 2020, chained off the 2019 stamp -- a second OPEN issue in the same
     * series, and the editor's publication panel then reports every message
     * published since as a live member of a seven-year-old period.
     *
     * It also exercises the chain and the overlap refusal over the whole backfill:
     * 2019 releases covering the union of its source weeks, and 2020 -- which opens
     * one millisecond after 2019 closes -- is untouched and still open.
     */
    @Test
    @Transactional
    public void publishingTheFirstBackfilledYearOpensNoSuccessor() {
        PublicationCategory category = new PublicationCategory();
        category.setCategoryId(TestIds.category());
        category.setPriority(100);
        category.setPublish(true);
        em.persist(category);

        MessageSeries messageSeries = new MessageSeries();
        messageSeries.setSeriesId(TestIds.id("ms-"));
        messageSeries.setMainType(MainType.NM);
        em.persist(messageSeries);

        PublicationSeries weekly = persistedSeries(category, SeriesCadence.WEEKLY,
                TimeRelation.PUBLISHED_IN_INTERVAL, CutoffDefault.RELEASE_MOMENT);
        // Two published weeks inside 2019, each with a frozen row: what the annual
        // compiles is what those weeks printed, so the union has to have something
        // in it for the release to be about anything.
        publishedWeek(weekly, messageSeries, at(2019, 3, 6), at(2019, 3, 13), "NM-1");
        publishedWeek(weekly, messageSeries, at(2019, 3, 13), at(2019, 3, 20), "NM-2");

        PublicationSeries annual = persistedSeries(category, SeriesCadence.YEARLY,
                TimeRelation.COMPILED_FROM_SOURCE, CutoffDefault.PERIOD_END);
        annual.setSourceSeries(weekly);
        annual.setAliveAtCutoff(null);
        // AUTO_ON_PUBLISH, or "no successor was created" would be true of a series
        // that never creates one.
        annual.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);
        // The compilation ruling's own naming, applied here as the import applies
        // it: two languages, each rendering its own document under its own name.
        // Anything less and both languages are written to one path named by the
        // issue's public id, and the second render overwrites the first.
        LegacyTemplateRulings.CompilationShape shape =
                LegacyTemplateRulings.compilationFor("accumulated-yearly-ntm");
        annual.setLanguageSpecific(shape.languageSpecific());
        annual.getLanguages().add("en");
        annual.createDesc("en").setName("Test " + annual.getSeriesId());
        for (PublicationSeriesDesc desc : annual.getDescs()) {
            desc.setNameSuggestionPattern(NAME_PATTERN);
            desc.setFileNamePattern(shape.fileNamePatterns().get(desc.getLang()));
        }
        em.merge(annual);

        // The archive's last hand-assembled edition, standing in for the seventeen.
        PublicationIssue last = new PublicationIssue();
        last.setSeries(annual);
        last.setPublicId(UUID.randomUUID().toString());
        last.setRepoPath("publications/" + last.getPublicId());
        last.setStatus(IssueStatus.PUBLISHED);
        last.setIntervalFrom(jan1(2018));
        last.setIntervalTo(dec31(2018));
        last.setCutoffStampedAt(dec31(2018));
        last.setPublishedAt(dec31(2018));
        last.createDesc("da").setName("Accumulated NtM - 2018");
        em.persist(last);
        em.flush();

        List<Integer> years = CompilationBackfill.missingYears(annual, List.of(last), NOW);
        assertEquals(8, years.size(), "2019 through 2026 is eight years: " + years);

        List<PublicationIssue> opened = new ArrayList<>();
        for (int year : years) {
            PublicationIssue issue = CompilationBackfill.annualFor(annual, year);
            em.persist(issue);
            opened.add(issue);
        }
        em.flush();

        long before = issueCount(annual);
        assertEquals(9, before, "one archived edition and eight opened ones");

        IssuePublishService.PublishResult result = publishService.publish(opened.get(0).getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null, dec31(2019)));
        em.flush();

        assertNull(result.successorId(),
                "publishing 2019 minted a successor although 2020 was already open beside it; the "
                        + "series now holds two open issues for two different periods");
        assertEquals(before, issueCount(annual),
                "the series gained an issue it was not asked for");

        // And each language went out under its own name, expanded from the ruling's
        // pattern. The file name is the last segment of the link the edition is
        // cited by, so a shared name is not only a lost document -- it is a Danish
        // citation resolving to the English annual.
        assertEquals(2, opened.get(0).getDescs().size(),
                "a language the series declares and the published issue has no row for has no "
                        + "document at all");
        for (PublicationIssueDesc desc : opened.get(0).getDescs()) {
            String expected = "da".equals(desc.getLang())
                    ? "Akkumuleret-EfS-2019.pdf"
                    : "Accumulated-NtM-2019.pdf";
            assertEquals(expected, desc.getFileName(),
                    "the " + desc.getLang() + " annual was filed under the wrong name; with no "
                            + "pattern it falls back to the issue's public id, which both "
                            + "languages share");
        }

        PublicationIssue next = em.find(PublicationIssue.class, opened.get(1).getId());
        em.refresh(next);
        assertEquals(IssueStatus.OPEN, next.getStatus(), "2020 should be untouched and waiting");
        assertEquals(jan1(2020), next.getIntervalFrom(),
                "2020 opens on 1 January, one millisecond after 2019 closed, and the release must "
                        + "not have moved it");
    }

    // ------------------------------------------------------------------ fixtures

    /** A compilation series with everything the shaping reads, and no database. */
    private static PublicationSeries compilationSeries() {
        Domain domain = new Domain();
        domain.setDomainId("compilation-backfill-test");
        domain.setTimeZone(ZONE.getId());

        PublicationSeries series = new PublicationSeries();
        series.setSeriesId("accumulated-yearly-ntm-fixture");
        series.setCadence(SeriesCadence.YEARLY);
        series.setTimeRelation(TimeRelation.COMPILED_FROM_SOURCE);
        // The cut-off default the compilation ruling applies, and the reason the
        // annuals are shaped only after the conversion: it is what makes the year
        // the calendar year.
        series.setCutoffDefault(CutoffDefault.PERIOD_END);
        series.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        series.setDomain(domain);
        series.getLanguages().add("da");
        series.getLanguages().add("en");
        series.createDesc("da").setNameSuggestionPattern(NAME_PATTERN);
        series.createDesc("en").setNameSuggestionPattern(NAME_PATTERN);
        return series;
    }

    /** The archive's own annuals, one per year, closing when each year does. */
    private static List<PublicationIssue> annuals(PublicationSeries series, int from, int to) {
        List<PublicationIssue> out = new ArrayList<>();
        for (int year = from; year <= to; year++) {
            PublicationIssue issue = new PublicationIssue();
            issue.setSeries(series);
            issue.setPublicId("archived-" + year);
            issue.setStatus(IssueStatus.PUBLISHED);
            issue.setIntervalFrom(jan1(year));
            issue.setCutoffStampedAt(dec31(year));
            out.add(issue);
        }
        return out;
    }

    private PublicationSeries persistedSeries(PublicationCategory category, SeriesCadence cadence,
                                              TimeRelation relation, CutoffDefault cutoffDefault) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        s.setCutoffDefault(cutoffDefault);
        s.setAliveAtCutoff(Boolean.FALSE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(category);
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        PublicationSeriesDesc desc = s.createDesc("da");
        // The column is NOT NULL: a series has to be called something before its
        // issues can be called anything.
        desc.setName("Test " + s.getSeriesId());
        desc.setNameSuggestionPattern(NAME_PATTERN);
        em.persist(s);
        return s;
    }

    /** A source week that went out, with one frozen row for the annual to compile. */
    private void publishedWeek(PublicationSeries series, MessageSeries messageSeries,
                               Date from, Date stamp, String shortId) {
        Message message = new Message();
        message.setUid(UUID.randomUUID().toString());
        message.setMessageSeries(messageSeries);
        message.setShortId(shortId);
        message.setMainType(MainType.NM);
        message.setType(Type.TEMPORARY_NOTICE);
        message.setStatus(Status.PUBLISHED);
        message.setPublishDateFrom(from);
        em.persist(message);

        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series);
        issue.setPublicId(UUID.randomUUID().toString());
        issue.setRepoPath("publications/" + issue.getPublicId());
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setIntervalFrom(from);
        issue.setIntervalFromSource(IntervalBoundSource.STAMPED);
        issue.setCutoffStampedAt(stamp);
        issue.setPublishedAt(stamp);
        issue.createDesc("da").setName(shortId + " week");
        em.persist(issue);

        IssueMember member = new IssueMember();
        member.setIssue(issue);
        member.setMessageUid(message.getUid());
        member.setMessage(message);
        member.setSortIndex(0);
        member.setFrozenShortId(shortId);
        member.setFrozenMainType(message.getMainType().name());
        member.setFrozenType(message.getType().name());
        member.setFrozenStatus(message.getStatus().name());
        member.setFrozenPublishDateFrom(message.getPublishDateFrom());
        member.setSource(MemberSource.CRITERIA);
        em.persist(member);
    }

    private long issueCount(PublicationSeries series) {
        return em.createQuery(
                        "SELECT COUNT(i) FROM PublicationIssue i WHERE i.series = :s", Long.class)
                .setParameter("s", series)
                .getSingleResult();
    }

    private static Date jan1(int year) {
        return Date.from(ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZONE).toInstant());
    }

    private static Date dec31(int year) {
        return Date.from(
                ZonedDateTime.of(year, 12, 31, 23, 59, 59, 999_000_000, ZONE).toInstant());
    }

    private static Date at(int year, int month, int day) {
        return Date.from(ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZONE).toInstant());
    }
}
