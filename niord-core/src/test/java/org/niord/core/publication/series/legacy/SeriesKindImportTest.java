package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The import decides what KIND each publication is, once.
 *
 * Driven over the CAPTURED ESTATE rather than a hand-built fixture, so what it
 * asserts is what the real archive produces. The classification it pins is the
 * one the old reading got wrong: cadence = NONE was taken to mean "one-off",
 * which put an eleven-issue series in the one-off list.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SeriesKindImportTest {

    /**
     * Cadence-less and unmistakably a series: these keep publishing, they just
     * keep no calendar while doing it.
     */
    private static final List<String> UNSCHEDULED = List.of(
            "nm-annex-ncags", "nm-annex-ice-service", "danish-list-of-lights");

    /** Cadence-less because they were published once and stopped. */
    private static final List<String> ONE_OFF = List.of(
            "aids-to-navigation", "journal-number", "list-of-wrecks",
            "navigation-through-danish-waters", "www-danskehavnelods-dk");

    @Inject
    LegacyImportService importService;

    private LegacyImportService.Plan plan() {
        return importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());
    }

    private static PublicationSeries find(LegacyImportService.Plan plan, String seriesId) {
        return plan.series().stream()
                .filter(s -> seriesId.equals(s.getSeriesId()))
                .findFirst().orElse(null);
    }

    /** Every series gets one. The column is NOT NULL, so a gap here fails the write. */
    @Test
    public void everySeriesIsClassified() {
        List<String> unclassified = plan().series().stream()
                .filter(s -> s.getKind() == null)
                .map(PublicationSeries::getSeriesId)
                .toList();

        assertTrue(unclassified.isEmpty(),
                "these series have no kind, and the column is NOT NULL: " + unclassified);
    }

    /**
     * A cadence-less series with more than one issue is UNSCHEDULED, not a one-off.
     *
     * THE ASSERTION THAT MATTERS. Eleven NCAGS editions, eight ice-service
     * notices and four editions of Dansk Fyrliste all have cadence = NONE, and
     * reading that as "one-off" is what filed an eleven-issue series under
     * one-offs.
     */
    @Test
    public void acadencelessSeriesWithManyIssuesIsUnscheduled() {
        LegacyImportService.Plan plan = plan();
        Map<PublicationSeries, Long> counts = plan.issues().values().stream()
                .filter(i -> i.getSeries() != null)
                .collect(Collectors.groupingBy(PublicationIssue::getSeries, Collectors.counting()));

        for (String seriesId : UNSCHEDULED) {
            PublicationSeries s = find(plan, seriesId);
            assertNotNull(s, seriesId + " is not in the plan at all");
            assertEquals(SeriesCadence.NONE, s.getCadence(),
                    seriesId + " is expected to be cadence-less; if that changed, this test is "
                            + "asserting the wrong thing rather than finding a bug");
            assertTrue(counts.getOrDefault(s, 0L) > 1,
                    seriesId + " no longer has more than one issue, so it is no longer the case "
                            + "this test was written to pin");
            assertEquals(SeriesKind.UNSCHEDULED, s.getKind(),
                    seriesId + " holds " + counts.get(s) + " issues and was classified "
                            + s.getKind() + "; a series with editions is not a one-off");
        }
    }

    /** A cadence-less series with one issue is the genuine one-off. */
    @Test
    public void acadencelessSeriesWithOneIssueIsAOneOff() {
        LegacyImportService.Plan plan = plan();

        for (String seriesId : ONE_OFF) {
            PublicationSeries s = find(plan, seriesId);
            assertNotNull(s, seriesId + " is not in the plan at all");
            assertEquals(SeriesKind.ONE_OFF, s.getKind(),
                    seriesId + " was published once and was classified " + s.getKind());
        }
    }

    /** Anything with a cadence is SCHEDULED, whatever its issue count says. */
    @Test
    public void acadencedSeriesIsScheduled() {
        List<String> wrong = plan().series().stream()
                .filter(s -> s.getCadence() != null && s.getCadence() != SeriesCadence.NONE)
                .filter(s -> s.getKind() != SeriesKind.SCHEDULED)
                .map(s -> s.getSeriesId() + " (" + s.getCadence() + " -> " + s.getKind() + ")")
                .toList();

        assertTrue(wrong.isEmpty(),
                "a series with a cadence is scheduled by definition, whatever it has published "
                        + "so far -- a brand new weekly series with one issue is not a one-off: " + wrong);
    }

    /**
     * The count is spent HERE and the answer stored.
     *
     * Stated as a test so the intent survives: only the importer may look at how
     * many issues a series has in order to decide what it is. Everything after
     * this reads the stored kind, which is what makes a second issue on a
     * one-off a refusal rather than a silent reclassification.
     */
    @Test
    public void theOneOffsAreExactlyTheSingleIssueCadencelessOnes() {
        LegacyImportService.Plan plan = plan();
        Map<PublicationSeries, Long> counts = plan.issues().values().stream()
                .filter(i -> i.getSeries() != null)
                .collect(Collectors.groupingBy(PublicationIssue::getSeries, Collectors.counting()));

        List<String> disagree = plan.series().stream()
                .filter(s -> {
                    boolean cadenceless = s.getCadence() == null || s.getCadence() == SeriesCadence.NONE;
                    boolean single = counts.getOrDefault(s, 0L) <= 1;
                    return (cadenceless && single) != (s.getKind() == SeriesKind.ONE_OFF);
                })
                .map(s -> s.getSeriesId() + " (" + s.getCadence() + ", "
                        + counts.getOrDefault(s, 0L) + " issues -> " + s.getKind() + ")")
                .toList();

        assertTrue(disagree.isEmpty(),
                "the stored kind disagrees with the rule that produced it: " + disagree);
    }
}
