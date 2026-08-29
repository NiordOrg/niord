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
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesAvailability;
import org.niord.core.publication.series.ContentMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rulings the legacy data does not contain, applied by the IMPORT.
 *
 * This is the test that answers "will a fresh prod-to-test restore reproduce the
 * corrections". Both rulings below were first applied to a deployed database by
 * hand, which fixes one dataset and nothing else -- the next import would have
 * reproduced the same two defects, silently, and somebody would have had to
 * notice them again.
 *
 * Driven over the CAPTURED ESTATE rather than a hand-built fixture, so what it
 * asserts is what the real templates produce.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class LegacyTemplateRulingsTest {

    /** SER-019's one-language template: "NCAGS 2021". */
    private static final String NCAGS_2021_TEMPLATE = "ebf7e99d-7914-48bf-8919-7525c2f2aee8";

    @Inject
    LegacyImportService importService;

    @Inject
    EntityManager em;

    private LegacyImportService.Plan plan() {
        return importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());
    }

    /**
     * THE ASSERTION THAT WAS MISSING. A ruling must not refuse the import.
     *
     * Every other test here inspects plan.series(), which is read at the END --
     * after planOrphanSeries has run. A redirect resolved DURING planSeries could
     * only see template-derived series, and nm-annex-ncags is authored by the
     * ORPHAN pass, so the ruling reported RULED_DESTINATION_MISSING. The import is
     * all-or-nothing, so that one problem refused all 1,077 issues -- and not one
     * assertion here could see it, because they all read the finished plan.
     *
     * Scoped to RULED_* rather than asserting the plan is wholly clean, because a
     * clean plan also depends on the DATABASE: this one has neither the FM reports
     * nor the annex domains the deployed installation has, so it reports fourteen
     * REPORT_NOT_FOUND / DOMAIN_NOT_FOUND that say nothing about the rulings. Those
     * are environmental and honestly reported. A ruling refusing the import is not.
     */
    @Test
    public void norulingRefusesTheImport() {
        LegacyImportService.Plan plan = plan();

        List<String> ruled = plan.report().getProblems().stream()
                .filter(p -> p.getCode() != null && p.getCode().startsWith("RULED_"))
                .map(p -> p.getCode() + " on " + p.getPublicationId())
                .toList();

        assertTrue(ruled.isEmpty(),
                "a ruling reported a problem, and the import is all-or-nothing -- so this refuses "
                        + "every issue in the estate: " + ruled);
    }

    // ------------------------------------------------------------- destinations

    /**
     * A template that is one EDITION does not become a series.
     *
     * Rasmus, 2026-08-26: "ncags-2021 is not a series itself and should not be
     * imported/migrated as one. It's a template in the legacy yes, but the series
     * is the NCAGS that hold all the ncags publications."
     *
     * It is the same judgement LegacyOrphanGrouping already records for the
     * template-LESS NCAGS annexes -- eleven of them are one series. The template
     * path simply never asked the question, so a twelfth NCAGS edition became its
     * own series beside them.
     */
    @Test
    public void aruledTemplateProducesNoSeriesOfItsOwn() {
        LegacyImportService.Plan plan = plan();

        assertTrue(plan.series().stream()
                        .noneMatch(s -> NCAGS_2021_TEMPLATE.equals(s.getLegacyTemplateId())),
                "the NCAGS 2021 template still became a series; its editions belong to the NCAGS "
                        + "series that already holds the rest");
        assertTrue(plan.series().stream().noneMatch(s -> "ncags-2021".equals(s.getSeriesId())),
                "and the authored id it used to take is gone with it");
    }

    /** The destination it redirects to is a series the same import creates. */
    @Test
    public void thedestinationExists() {
        LegacyImportService.Plan plan = plan();

        assertTrue(plan.series().stream().anyMatch(s -> "nm-annex-ncags".equals(s.getSeriesId())),
                "the ruling files editions under a series this import does not produce, which would "
                        + "lose them entirely");
    }

    /**
     * Every ruled destination resolves. A typo here loses a template's editions.
     *
     * Cheap to state and one-way to get wrong: the import reports
     * RULED_DESTINATION_MISSING rather than dropping them, but a ruling that never
     * applies is a ruling nobody notices is broken.
     */
    @Test
    public void everyRuledDestinationIsProducedByTheImport() {
        LegacyImportService.Plan plan = plan();

        for (String destination : LegacyTemplateRulings.destinations().values()) {
            assertTrue(plan.series().stream().anyMatch(s -> destination.equals(s.getSeriesId())),
                    "no series '" + destination + "' is produced, so the editions ruled into it "
                            + "would be reported missing on every import");
        }
    }

    /**
     * The six "DONT USE" templates produce no series, and their editions land in
     * the weekly series they were cloned out of.
     *
     * Legacy had no way to vary one issue, so a week that had to differ -- a
     * double week over a year turnover, a re-issue -- was made by cloning the
     * whole template, publishing once, and abandoning it. Imported as series they
     * put six single-edition headings beside the archive those editions belong to.
     */
    @Test
    public void thedontUseTemplatesProduceNoSeriesOfTheirOwn() {
        LegacyImportService.Plan plan = plan();

        List<String> survivors = plan.series().stream()
                .map(PublicationSeries::getSeriesId)
                .filter(id -> id != null && id.startsWith("dont-use"))
                .toList();

        assertTrue(survivors.isEmpty(),
                "a DONT USE template still became a series: " + survivors);
    }

    /** And their editions are not lost on the way -- they are filed, not dropped. */
    @Test
    public void thedontUseEditionsLandInTheWeeklySeries() {
        LegacyImportService.Plan plan = plan();

        for (Map.Entry<String, String> ruling : LegacyTemplateRulings.destinations().entrySet()) {
            long filed = plan.issues().values().stream()
                    .filter(i -> i.getSeries() != null
                            && ruling.getValue().equals(i.getSeries().getSeriesId()))
                    .count();
            assertTrue(filed > 0,
                    "nothing at all is filed under " + ruling.getValue() + ", so the editions "
                            + "ruled into it went nowhere");
        }
    }

    // ------------------------------------------------------------------ domains

    /**
     * A template that names no domain gets the ruled one.
     *
     * niord-nm is used rather than an annex domain because it is the one this
     * test database is guaranteed to have -- the annex domains exist on the
     * deployed installation and not necessarily here, which is itself the case
     * the missing-domain branch handles.
     */
    @Test
    public void aruledDomainIsApplied() {
        PublicationSeries accumulated = plan().series().stream()
                .filter(s -> "accumulated-yearly-ntm".equals(s.getSeriesId()))
                .findFirst().orElse(null);

        assertNotNull(accumulated, "the accumulated yearly series is not in the plan at all");
        assertNotNull(accumulated.getDomain(),
                "the ruling files it under NM and it landed with no domain; a series with no domain "
                        + "has no timezone, and cutoffZone() then falls back to hardcoded UTC");
        assertEquals("niord-nm", accumulated.getDomain().getDomainId());
    }

    /**
     * A template that names its OWN domain keeps it.
     *
     * The ruling fills a gap; it does not override data. A ruling that silently
     * won over a real value would be a second source of truth, and the legacy
     * templates are the first one.
     */
    @Test
    public void aruledDomainDoesNotOverrideTheTemplatesOwn() {
        PublicationSeries weekly = plan().series().stream()
                .filter(s -> "weekly-ntm".equals(s.getSeriesId()))
                .findFirst().orElse(null);

        assertNotNull(weekly);
        assertNotNull(weekly.getDomain());
        assertEquals("niord-nm", weekly.getDomain().getDomainId(),
                "weekly-ntm's own template names niord-nm, and that is what must survive");
        assertFalse(LegacyTemplateRulings.domains().containsKey("weekly-ntm"),
                "weekly-ntm should not need a ruling at all -- its template answers the question");
    }

    /**
     * A ruling naming an ORPHAN-GROUPED series is applied too.
     *
     * THE ONE THE RESEED REHEARSAL CAUGHT. The domain ruling used to run inside
     * planSeries, which only ever sees series a TEMPLATE produced. Both annex
     * rulings name series the ORPHAN pass produces, so neither could ever apply,
     * and both came out domainless on every import.
     *
     * It hid for two reasons at once. The deployed estate had those domains set
     * BY HAND on the same day the ruling was written down, so the estate looked
     * right; and the only test asserting a ruled domain used accumulated-yearly-
     * ntm, which is template-derived and therefore took the working path.
     *
     * The annex domain is created here rather than assumed: this test database
     * ships with niord-nm and little else, which is exactly why the original test
     * chose niord-nm and why the gap survived.
     */
    @Test
    @Transactional
    public void aruledDomainReachesAnOrphanGroupedSeriesToo() {
        if (em.createQuery("SELECT COUNT(d) FROM Domain d WHERE d.domainId = :id", Long.class)
                .setParameter("id", "niord-annex").getSingleResult() == 0) {
            Domain annex = new Domain();
            annex.setDomainId("niord-annex");
            annex.setName("NM Annex");
            annex.setTimeZone("Europe/Copenhagen");
            em.persist(annex);
            em.flush();
        }

        PublicationSeries ncags = plan().series().stream()
                .filter(s -> "nm-annex-ncags".equals(s.getSeriesId()))
                .findFirst().orElse(null);

        assertNotNull(ncags, "nm-annex-ncags is not in the plan at all");
        // NOT asserted on legacyTemplateId: an orphan-grouped series carries the
        // representative PUBLICATION's id there, so the field cannot tell the two
        // paths apart. What matters is only that the ruling reached this series.
        assertNotNull(ncags.getDomain(),
                "the ruling did not reach an orphan-grouped series, so it can never apply to "
                        + "either annex series and both import domainless");
        assertEquals("niord-annex", ncags.getDomain().getDomainId());
    }

    /**
     * The count of ownerless series is reported, and zero is what it must read.
     *
     * A series with no owner has no timezone, appears on no admin list and is
     * refused by S-20a -- and the column is NOT NULL, so it cannot even be
     * written. The number has to be VISIBLE, or an admin reads a clean import
     * report and does not learn that some publications could not be filed.
     */
    @Test
    public void theReportSaysHowManySeriesHaveNoDomain() {
        LegacyImportService.Plan plan = plan();

        long actual = plan.series().stream().filter(s -> s.getDomain() == null).count();
        assertEquals(actual, plan.report().getSeriesWithoutDomain(),
                "the reported count disagrees with the plan it describes");
    }

    // ---------------------------------------------------------- owner and sharing

    /**
     * EVERY imported series leaves the plan with an owner.
     *
     * The rule that used to say "fill the named gaps and leave the rest" now says
     * "fill every gap", because there is no longer a state in which an ownerless
     * publication is legal: no desk lists it, nobody administers it, it has no
     * timezone, and the column refuses it.
     *
     * The annex domain is created here rather than assumed -- this test database
     * ships with niord-nm and little else, which is the same reason the orphan
     * ruling above creates it.
     */
    @Test
    @Transactional
    public void everySeriesLeavesThePlanWithAnOwner() {
        ensureAnnexDomain();

        List<String> ownerless = plan().series().stream()
                .filter(s -> s.getDomain() == null)
                .map(PublicationSeries::getSeriesId)
                .toList();

        assertTrue(ownerless.isEmpty(),
                "these series imported with no owner, and the column is NOT NULL -- the write "
                        + "would fail mid-flush naming a column: " + ownerless);
    }

    /**
     * THE WHOLE OF THE §3 TABLE, row by row, over the captured estate.
     *
     * Written out rather than derived, because the table IS the decision. Every
     * attempt to compute it has been wrong: the availability was briefly taken
     * from the content mode, which reads as a principle -- generated is the
     * owner's, everything else is everybody's -- and gets three rows wrong,
     * because accumulated-yearly-ntm and the two NM annexes are uploaded
     * documents exactly like the six reference lists that really are shared.
     * Nothing in the data separates them. So each row is named, and this test is
     * what stops the next derivation from passing.
     *
     * A series the fixture does not contain is skipped rather than failed: the
     * captured estate is a slice, and which slice it is is not this test's
     * subject. The count assertion below is what stops that skip from emptying
     * the test.
     */
    private static Map<String, String[]> theSpecTable() {
        Map<String, String[]> t = new LinkedHashMap<>();
        // seriesId -> { owner, availability }
        t.put("weekly-ntm", new String[]{"niord-nm", "OWNER_ONLY"});
        t.put("weekly-ntm-p-t", new String[]{"niord-nm", "OWNER_ONLY"});
        t.put("accumulated-yearly-ntm", new String[]{"niord-nm", "OWNER_ONLY"});
        t.put("efs-a", new String[]{"niord-almanac", "OWNER_ONLY"});
        t.put("firing-practice-areas", new String[]{"niord-fa", "OWNER_ONLY"});
        t.put("nm-annex-ice-service", new String[]{"niord-annex", "OWNER_ONLY"});
        t.put("nm-annex-ncags", new String[]{"niord-annex", "OWNER_ONLY"});
        // The six that carried no domain in legacy: the annex desk's to
        // administer, and citable from every desk -- which is the reach they had
        // when they carried no domain at all, said in the field that means it.
        t.put("journal-number", new String[]{"niord-annex", "ALL_DOMAINS"});
        t.put("aids-to-navigation", new String[]{"niord-annex", "ALL_DOMAINS"});
        t.put("list-of-wrecks", new String[]{"niord-annex", "ALL_DOMAINS"});
        t.put("www-danskehavnelods-dk", new String[]{"niord-annex", "ALL_DOMAINS"});
        t.put("danish-list-of-lights", new String[]{"niord-annex", "ALL_DOMAINS"});
        t.put("navigation-through-danish-waters", new String[]{"niord-annex", "ALL_DOMAINS"});
        return t;
    }

    @Test
    @Transactional
    public void theImportedEstateMatchesTheRulingTableRowForRow() {
        ensureAnnexDomain();
        LegacyImportService.Plan plan = plan();

        Map<String, String[]> expected = theSpecTable();
        assertEquals(13, expected.size(), "the ruling table is thirteen rows");

        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, String[]> row : expected.entrySet()) {
            String seriesId = row.getKey();
            PublicationSeries s = plan.series().stream()
                    .filter(candidate -> seriesId.equals(candidate.getSeriesId()))
                    .findFirst().orElse(null);
            if (s == null) {
                continue;
            }
            checked++;
            String owner = s.getDomain() == null ? null : s.getDomain().getDomainId();
            if (!row.getValue()[0].equals(owner)) {
                wrong.add(seriesId + ": owner is " + owner + ", the ruling says " + row.getValue()[0]);
            }
            if (!row.getValue()[1].equals(String.valueOf(s.getAvailability()))) {
                wrong.add(seriesId + ": availability is " + s.getAvailability()
                        + ", the ruling says " + row.getValue()[1]);
            }
        }

        assertTrue(checked >= 10,
                "only " + checked + " of the thirteen ruled series are in the captured estate; the "
                        + "fixture or the authored ids have moved, and a table that matches nothing "
                        + "passes over everything");
        assertEquals(List.of(), wrong,
                "the import does not produce the ruled estate: " + wrong);
    }

    /**
     * The declared ruling names the nine the data cannot decide, and no others.
     *
     * The other four rows of the §3 table -- the two weeklies, efs-a and
     * firing-practice-areas -- are GENERATED, so what an admin would get for a
     * publication of that kind is already the right answer and a ruling would be a
     * second source that can disagree with it. Naming them anyway would be the
     * kind of "harmless" duplication that later gets edited on one side only.
     */
    @Test
    public void therulingNamesOnlyTheSeriesTheDataCannotDecide() {
        Map<String, SeriesAvailability> ruled = LegacyTemplateRulings.availabilities();

        assertEquals(9, ruled.size(),
                "the ruling should name three uploaded publications that belong to one desk each "
                        + "and the six that belong to all of them: " + ruled.keySet());
        for (Map.Entry<String, String[]> row : theSpecTable().entrySet()) {
            SeriesAvailability named = ruled.get(row.getKey());
            if (named != null) {
                assertEquals(row.getValue()[1], named.name(),
                        row.getKey() + " is ruled differently from the table in the spec");
            }
        }
        for (String seriesId : List.of("weekly-ntm", "weekly-ntm-p-t")) {
            assertFalse(ruled.containsKey(seriesId),
                    seriesId + " is generated, so the default already answers for it and a ruling "
                            + "would be a second source that can disagree");
        }
    }

    /**
     * The three UPLOADED_FILE rows the content mode would have got wrong.
     *
     * Named on their own because they are the regression. A derivation from the
     * content mode shares every non-generated publication with every domain, and
     * these three are documents -- so all three came out ALL_DOMAINS, which offers
     * one desk's annual roll-up and both NM annexes as if they were everybody's.
     */
    @Test
    @Transactional
    public void theuploadedSeriesThatBelongToOneDeskAreNotShared() {
        ensureAnnexDomain();
        LegacyImportService.Plan plan = plan();

        for (String seriesId
                : List.of("accumulated-yearly-ntm", "nm-annex-ice-service", "nm-annex-ncags")) {
            PublicationSeries s = plan.series().stream()
                    .filter(row -> seriesId.equals(row.getSeriesId()))
                    .findFirst().orElse(null);
            assertNotNull(s, seriesId + " is not in the plan at all");
            assertEquals(ContentMode.UPLOADED_FILE, s.getContentMode(),
                    seriesId + " is no longer an uploaded document, so it no longer demonstrates "
                            + "what this test is about -- check the ruling still holds");
            assertEquals(SeriesAvailability.OWNER_ONLY, s.getAvailability(),
                    seriesId + " is an uploaded document that belongs to ONE desk. Deriving "
                            + "availability from the content mode shares it with every domain, "
                            + "because it cannot tell it from the six reference lists.");
        }
    }

    /** And the six really are shared, so the ruling is not simply narrowing everything. */
    @Test
    @Transactional
    public void thesixSharedReferencesAreSharedEverywhere() {
        ensureAnnexDomain();
        LegacyImportService.Plan plan = plan();

        for (String seriesId : LegacyTemplateRulings.sharedEverywhere()) {
            PublicationSeries s = plan.series().stream()
                    .filter(row -> seriesId.equals(row.getSeriesId()))
                    .findFirst().orElse(null);
            if (s == null) {
                continue;
            }
            assertEquals(SeriesAvailability.ALL_DOMAINS, s.getAvailability(),
                    seriesId + " is cited from every domain, and giving it an owner must not take "
                            + "that away -- which is exactly what happened the first time these "
                            + "were assigned a domain");
        }
    }

    /**
     * And the weeklies are their own desk's alone.
     *
     * A generated series is assembled from one domain's messages over that
     * domain's cut-off calendar, so its editions mean that desk's week. Sharing
     * one would offer another authority's weekly edition as if it were everybody's.
     */
    @Test
    @Transactional
    public void ageneratedSeriesIsSharedWithNobody() {
        ensureAnnexDomain();
        LegacyImportService.Plan plan = plan();

        for (String seriesId : List.of("weekly-ntm", "weekly-ntm-p-t")) {
            PublicationSeries s = plan.series().stream()
                    .filter(row -> seriesId.equals(row.getSeriesId()))
                    .findFirst().orElse(null);
            assertNotNull(s, seriesId + " is not in the plan at all");
            assertEquals(SeriesAvailability.OWNER_ONLY, s.getAvailability(),
                    seriesId + " is generated from its own desk's messages; sharing it would offer "
                            + "one authority's weekly edition as if it were everybody's");
        }
    }

    // -------------------------------------------------- what the dry run reports

    /**
     * The owner note is written for the series whose template named no domain, and
     * for no others.
     *
     * A note rather than a problem, because supplying the owner is the import
     * doing its job. But it has to be VISIBLE: the estate is too large to read row
     * by row, the run happens once, and "which publications did the importer
     * decide the desk for" is the question somebody asks afterwards. Written for a
     * series whose template DID name a domain, it would be noise that hides the
     * real ones.
     */
    @Test
    @Transactional
    public void thedryRunNotesEveryOwnerItSuppliedAndNoOthers() {
        ensureAnnexDomain();
        LegacyImportService.Plan plan = plan();

        Set<String> noted = plan.report().getNotes().stream()
                .filter(n -> "OWNER_ASSIGNED".equals(n.getCode()))
                .map(LegacyImportReportVo.ProblemVo::getTitle)
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(noted.isEmpty(),
                "no owner was noted at all; the six that carried no domain each get one, so the "
                        + "note is not being written and the report cannot show what was decided");

        for (String seriesId : noted) {
            assertTrue(LegacyTemplateRulings.domainFor(seriesId) != null
                            || plan.series().stream().anyMatch(s -> seriesId.equals(s.getSeriesId())),
                    "a note names a series the plan does not contain: " + seriesId);
        }
        // weekly-ntm's own template names niord-nm, so nothing was supplied for it.
        assertFalse(noted.contains("weekly-ntm"),
                "an owner was noted for a series whose template names its own domain; the note "
                        + "would then be noise hiding the ones that were really decided");
    }

    /**
     * And nothing is reported as unresolvable while the ruled domains are present.
     *
     * The positive control for the problem below: with niord-annex in place the
     * whole estate finds an owner, so a report carrying this code would mean the
     * lookup itself is broken.
     */
    @Test
    @Transactional
    public void thedryRunReportsNoUnresolvableOwnerWhenTheDomainsExist() {
        ensureAnnexDomain();
        LegacyImportService.Plan plan = plan();

        List<String> unresolvable = plan.report().getProblems().stream()
                .filter(p -> "OWNER_DOMAIN_NOT_FOUND".equals(p.getCode()))
                .map(LegacyImportReportVo.ProblemVo::getTitle)
                .toList();

        assertEquals(List.of(), unresolvable,
                "a series could not be given an owner although every ruled domain exists here");
    }

    /**
     * The problem raised when the owner a series would take does NOT exist.
     *
     * Asserted on the builder rather than through a dry run, and the reason is
     * that the branch cannot be reproduced twice: reaching it means importing into
     * an installation that lacks niord-annex, and the moment any test creates that
     * domain -- as the ones above must -- no later run on the shared database can
     * get back to it. What matters is the shape a report carries when it happens:
     * the code an operator greps for, the series it names, and a message saying
     * the row cannot be imported at all.
     *
     * A PROBLEM, not a note: the import is all-or-nothing and this refuses the
     * estate, which is correct -- the column is NOT NULL, so such a series does
     * not land DRAFT and wait for somebody, it takes the write down mid-flush.
     */
    @Test
    public void anunresolvableOwnerIsReportedAsAProblemNamingTheDomain() {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("probe-series");
        s.setLegacyTemplateId("11111111-2222-3333-4444-555555555555");

        LegacyImportReportVo.ProblemVo problem =
                LegacyImportService.ownerUnresolvable(s, "niord-missing");

        assertEquals("OWNER_DOMAIN_NOT_FOUND", problem.getCode());
        assertEquals("probe-series", problem.getTitle());
        assertEquals("11111111-2222-3333-4444-555555555555", problem.getPublicationId());
        assertTrue(problem.getDetail().contains("niord-missing"),
                "the report must name the domain that is missing, or nobody knows what to create: "
                        + problem.getDetail());
    }

    /** And the note says whether a ruling named the owner or the default supplied it. */
    @Test
    public void theownerNoteSaysWhetherItWasRuledOrDefaulted() {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("probe-series");

        assertTrue(LegacyImportService.ownerAssigned(s, "niord-annex", true)
                        .getDetail().contains("by ruling"));
        assertTrue(LegacyImportService.ownerAssigned(s, "niord-annex", false)
                        .getDetail().contains("by default"));
    }

    private void ensureAnnexDomain() {
        if (em.createQuery("SELECT COUNT(d) FROM Domain d WHERE d.domainId = :id", Long.class)
                .setParameter("id", "niord-annex").getSingleResult() == 0) {
            Domain annex = new Domain();
            annex.setDomainId("niord-annex");
            annex.setName("NM Annex");
            annex.setTimeZone("Europe/Copenhagen");
            em.persist(annex);
            em.flush();
        }
    }
}
