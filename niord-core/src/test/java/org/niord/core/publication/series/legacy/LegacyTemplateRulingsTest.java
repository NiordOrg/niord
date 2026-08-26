package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.PublicationSeries;

import java.util.List;

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
     * The count of unactivatable series is reported, and zero would be a finding.
     *
     * A series with no domain stays DRAFT because S-20 refuses it. That is
     * proportionate -- refusing 1,077 issues over one absent domain is not -- but
     * it has to be VISIBLE, or an admin reads a clean import report and does not
     * learn that some publications cannot be activated.
     */
    @Test
    public void theReportSaysHowManySeriesHaveNoDomain() {
        LegacyImportService.Plan plan = plan();

        long actual = plan.series().stream().filter(s -> s.getDomain() == null).count();
        assertEquals(actual, plan.report().getSeriesWithoutDomain(),
                "the reported count disagrees with the plan it describes");
    }
}
