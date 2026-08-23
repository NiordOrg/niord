package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.vo.PublicationStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5.3 and B5.4a, translated over the whole captured estate.
 *
 * No database and no Quarkus: the translation is a pure function of a legacy row,
 * which is deliberate -- it means the id-space and path assertions below run over
 * all 1,077 rows in milliseconds instead of over a fixture of three.
 */
public class LegacyTranslationTest {

    private static final Date FROZEN = new Date(1_755_000_000_000L);
    private static final String SOURCE = "test";

    private static List<PublicationSeries> series() {
        Set<String> authored = new LinkedHashSet<>();
        List<PublicationSeries> out = new ArrayList<>();
        for (Publication t : LegacyEstateFixture.templates()) {
            out.add(LegacySeriesTranslation.translate(
                    t, LegacySeriesTranslation.authorSeriesId(t, authored), SOURCE));
        }
        return out;
    }

    // ------------------------------------------------------------------ B5.3

    /** The twelve templates land as reviewable drafts, each naming its origin. */
    @Test
    public void everyTemplateLandsAsADraftCarryingItsLegacyId() {
        List<Publication> templates = LegacyEstateFixture.templates();
        assertEquals(12, templates.size(), "the captured estate holds twelve templates");

        List<PublicationSeries> translated = series();
        for (int i = 0; i < translated.size(); i++) {
            PublicationSeries s = translated.get(i);
            assertEquals(SeriesStatus.DRAFT, s.getStatus(),
                    "an imported series is a translation, not a fact; it is reviewed before it is active");
            assertEquals(templates.get(i).getPublicationId(), s.getLegacyTemplateId(),
                    "provenance travels in legacyTemplateId");
            assertNotNull(s.getImportSource());
        }
    }

    /** R3: the seriesId is authored, and is never the legacy UUID. */
    @Test
    public void theSeriesIdIsAuthoredAndNeverTheLegacyUuid() {
        for (PublicationSeries s : series()) {
            assertNotNull(s.getSeriesId());
            assertFalse(s.getSeriesId().equalsIgnoreCase(s.getLegacyTemplateId()),
                    "adopting the legacy UUID makes the new identity a copy of the old one");
            assertFalse(s.getSeriesId().matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-.*"),
                    "a UUID-shaped seriesId is not a human-readable identity: " + s.getSeriesId());
        }
    }

    /** Authoring is deterministic: two runs over one estate author one set of ids. */
    @Test
    public void authoringTheSameEstateTwiceProducesTheSameIds() {
        List<String> first = series().stream().map(PublicationSeries::getSeriesId).toList();
        List<String> second = series().stream().map(PublicationSeries::getSeriesId).toList();
        assertEquals(first, second);
        assertEquals(new HashSet<>(first).size(), first.size(), "authored ids must be unique");
    }

    /** A colliding title is refused rather than silently suffixed. */
    @Test
    public void acollidingSeriesIdIsRefused() {
        Publication a = LegacyEstateFixture.templates().get(0);
        Set<String> authored = new LinkedHashSet<>();
        LegacySeriesTranslation.authorSeriesId(a, authored);

        LegacySeriesTranslation.ImportRefusedException e =
                assertThrows(LegacySeriesTranslation.ImportRefusedException.class,
                        () -> LegacySeriesTranslation.authorSeriesId(a, authored));
        assertEquals("SERIES_ID_COLLISION", e.getCode());
    }

    /** A print setting outside the typed set aborts, naming the key. */
    @Test
    public void anUnknownPrintSettingIsRefusedByName() {
        Publication t = LegacyEstateFixture.templates().get(0);
        Map<String, Object> settings = new LinkedHashMap<>(t.getPrintSettings());
        settings.put("columnCount", 3);
        t.setPrintSettings(settings);

        LegacySeriesTranslation.ImportRefusedException e =
                assertThrows(LegacySeriesTranslation.ImportRefusedException.class,
                        () -> LegacySeriesTranslation.translate(t, "x", SOURCE));
        assertEquals("PRINT_SETTING_NOT_ALLOWED", e.getCode());
        assertTrue(e.getMessage().contains("columnCount"),
                "the refusal must name the key, or an admin cannot act on it");
    }

    /** The estate's own print settings are all inside the typed set. */
    @Test
    public void theEstateCarriesNoDisallowedPrintSetting() {
        for (Publication p : LegacyEstateFixture.templates()) {
            LegacySeriesTranslation.translate(p, "id-" + p.getPublicationId(), SOURCE);
        }
    }

    // ----------------------------------------------------------------- B5.4a

    /** Id-space continuity, over every row: publicId IS the legacy id. */
    @Test
    public void everyIssueKeepsItsLegacyIdVerbatim() {
        List<String> broken = new ArrayList<>();

        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN);
            if (!p.getPublicationId().equals(issue.getPublicId())
                    || !p.getPublicationId().equals(issue.getLegacyPublicationId())) {
                broken.add(p.getPublicationId());
            }
        }

        assertTrue(broken.isEmpty(),
                "a re-keyed import dangles every citation in the archive, and the citations are bytes "
                        + "inside stored message HTML rather than references that can be re-pointed: "
                        + broken);
    }

    /** The whole estate, and no two issues collide on publicId. */
    @Test
    public void theImportedIdSpaceDoesNotCollide() {
        Set<String> seen = new HashSet<>();
        for (Publication p : LegacyEstateFixture.publications()) {
            assertTrue(seen.add(LegacyIssueTranslation.translate(p, null, FROZEN).getPublicId()),
                    "duplicate publicId: " + p.getPublicationId());
        }
        assertEquals(1077, seen.size());
    }

    /**
     * Paths are carried verbatim, revision segment included, and every filePath
     * is contained by its issue's repoPath.
     *
     * The containment rule is D-4's imported layout, which is the fixture B1.7b
     * waits on.
     */
    @Test
    public void pathsAreVerbatimAndContainedByTheirRepoPath() {
        int checked = 0;
        List<String> broken = new ArrayList<>();

        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN);
            assertEquals(p.getRepoPath(), issue.getRepoPath(), "repoPath must not be re-derived");

            for (PublicationIssueDesc d : issue.getDescs()) {
                if (d.getFilePath() == null) {
                    continue;
                }
                checked++;
                String expected = p.getRepoPath() + "/" + p.getRevision() + "/" + d.getFileName();
                if (!expected.equals(d.getFilePath()) || !d.getFilePath().startsWith(issue.getRepoPath())) {
                    broken.add(p.getPublicationId() + " -> " + d.getFilePath());
                }
            }
        }

        assertTrue(checked > 0, "no file paths were checked, so this asserted nothing");
        assertTrue(broken.isEmpty(),
                "re-deriving a flat path 404s every imported file AND every citation pointing at it; "
                        + "42 of these rows no longer have their bytes, so the path is all that is left: "
                        + broken);
    }

    /**
     * Nothing is keyed on a title or a tag name.
     *
     * Proven on the publications that carry a literal ${year} in title, fileName
     * and tag name -- they were publicly released as Skydeomraader-%24%7Byear%7D.pdf
     * and EfS-A-v1-%24%7Byear%7D.pdf, so any name-based keying inherits corruption
     * that is already public.
     */
    @Test
    public void theDollarYearRowsAreKeyedOnTheirIdAndNotTheirName() {
        List<Publication> cursed = LegacyEstateFixture.publications().stream()
                .filter(p -> p.getDescs().stream().anyMatch(d ->
                        (d.getTitle() != null && d.getTitle().contains("${year}"))
                                || (d.getFileName() != null && d.getFileName().contains("${year}")))
                        || (p.getMessageTagFormat() != null && p.getMessageTagFormat().contains("${year}")
                            && p.getMessageTagFormat().equals(p.getMessageTagFormat())))
                .toList();

        assertFalse(cursed.isEmpty(), "the ${year} rows are the evidence for this rule; none were found");

        for (Publication p : cursed) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN);
            assertEquals(p.getPublicationId(), issue.getPublicId());
            assertFalse(issue.getPublicId().contains("${"),
                    "a name-derived key would carry the literal placeholder into the new id space");
        }
    }

    /** The status mapping, over one row of each of the four legacy statuses. */
    @Test
    public void theFourLegacyStatusesMap() {
        assertSame(IssueStatus.OPEN, LegacyIssueTranslation.statusOf(PublicationStatus.DRAFT));
        assertSame(IssueStatus.OPEN, LegacyIssueTranslation.statusOf(PublicationStatus.RECORDING));
        assertSame(IssueStatus.PUBLISHED, LegacyIssueTranslation.statusOf(PublicationStatus.ACTIVE));
        assertSame(IssueStatus.RETIRED, LegacyIssueTranslation.statusOf(PublicationStatus.INACTIVE));

        Map<IssueStatus, Integer> distribution = new LinkedHashMap<>();
        for (Publication p : LegacyEstateFixture.publications()) {
            distribution.merge(
                    LegacyIssueTranslation.translate(p, null, FROZEN).getStatus(), 1, Integer::sum);
        }

        // The captured estate: 1042 ACTIVE, 31 INACTIVE, 3 RECORDING, 1 DRAFT.
        assertEquals(1042, distribution.get(IssueStatus.PUBLISHED));
        assertEquals(31, distribution.get(IssueStatus.RETIRED));
        assertEquals(4, distribution.get(IssueStatus.OPEN), "3 RECORDING + 1 DRAFT");
    }

    /** Every issue arrives with its snapshot header already written (B5.4a2). */
    @Test
    public void theTranslationWritesTheSnapshotHeader() {
        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN);
            assertNotNull(issue.getSnapshotTimeRelation(), p.getPublicationId());
            assertNotNull(issue.getSnapshotAliveAtCutoff(), p.getPublicationId());
            assertEquals(FROZEN, issue.getSnapshotFrozenAt());
        }
    }
}
