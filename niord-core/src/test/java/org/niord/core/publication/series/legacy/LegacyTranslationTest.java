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

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.BindsRule;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.criteria.CriteriaSerialization;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesDesc;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Templates into series and publications into issues, translated over the whole captured estate.
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

    /**
     * EVERY series the import creates, in the shape it creates them.
     *
     * series() above is the twelve templates alone; the estate also produces
     * eight from the template-less publications -- three shared annex series and
     * five standalone one-offs -- and those are exactly the shapes a rule about
     * uploaded documents and absent criteria has to be checked against.
     *
     * Two pieces are attached here because the import attaches them from
     * elsewhere and a series is not complete without them. The CATEGORY is
     * resolved against the category table, so a stand-in stands for it: the
     * question here is whether the translated fields are valid, not whether the
     * category lookup works, and leaving it null would fail every series on S-19
     * and say nothing. The CRITERIA is read off the harvest, because the import
     * derives it from the message series the tagged messages actually belong to
     * and no fixture carries those.
     */
    private static List<PublicationSeries> importedSeries() {
        Set<String> authored = new LinkedHashSet<>();
        List<PublicationSeries> out = new ArrayList<>();

        for (Publication t : LegacyEstateFixture.templates()) {
            out.add(LegacySeriesTranslation.translate(
                    t, LegacySeriesTranslation.authorSeriesId(t, authored), SOURCE));
        }

        Map<String, List<Publication>> shared = new LinkedHashMap<>();
        List<Publication> standalone = new ArrayList<>();
        for (Publication p : LegacyEstateFixture.publications()) {
            if (p.getTemplate() != null) {
                continue;
            }
            LegacyOrphanGrouping.Placement place = LegacyOrphanGrouping.placeOf(p);
            switch (place.kind()) {
                case SHARED_SERIES ->
                        shared.computeIfAbsent(place.seriesId(), k -> new ArrayList<>()).add(p);
                case OWN_SERIES -> standalone.add(p);
                default -> {
                    // Files onto a series a template already produced.
                }
            }
        }
        for (Map.Entry<String, List<Publication>> e : shared.entrySet()) {
            authored.add(e.getKey());
            out.add(LegacySeriesTranslation.translate(
                    LegacyOrphanGrouping.configurationSource(e.getValue()), e.getKey(), SOURCE));
        }
        Map<String, String> ownIds =
                LegacySeriesTranslation.authorOrphanSeriesIds(standalone, authored);
        for (Publication p : standalone) {
            out.add(LegacySeriesTranslation.translate(p, ownIds.get(p.getPublicationId()), SOURCE));
        }

        Map<String, String> criteria = EstateSlice.criteriaByLegacyTemplateId();
        for (PublicationSeries s : out) {
            PublicationCategory category = new PublicationCategory();
            category.setCategoryId("category-of-" + s.getSeriesId());
            s.setCategory(category);

            String doc = criteria.get(s.getLegacyTemplateId());
            if (doc != null && s.getContentMode() == ContentMode.GENERATED_FROM_QUERY) {
                try {
                    s.setCriteria(CriteriaSerialization.mapper()
                            .readValue(doc, IssueCriteriaVo.class));
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "cannot read the harvested criteria of " + s.getSeriesId(), e);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------ templates to series

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

    /**
     * Only a query-backed series carries a time relation or a liveness flag.
     *
     * The legacy filter was translated unconditionally, so publications with no
     * membership at all -- contentMode NONE, a link, an uploaded file -- were
     * imported claiming to resolve messages published in an interval. S-1 and S-2
     * then refused all eight of them, and nothing could be done about it from the
     * settings screen: it offers those fields to a query-backed series only, so the
     * values blocking the save were the ones nobody could see.
     *
     * Asserted over the WHOLE captured estate rather than one fixture, because the
     * question is whether any real template produces the illegal pairing.
     */
    @Test
    public void onlyaQueryBackedSeriesCarriesATimeRelation() {
        for (PublicationSeries s : series()) {
            boolean queryBacked = s.getContentMode() == ContentMode.GENERATED_FROM_QUERY;
            if (queryBacked) {
                assertNotNull(s.getTimeRelation(),
                        s.getSeriesId() + " is query-backed and must declare its time predicate (S-1)");
                assertNotNull(s.getAliveAtCutoff(),
                        s.getSeriesId() + " is query-backed and must say whether it filters on "
                                + "liveness; null makes \"does not filter\" and \"filters and "
                                + "everything passed\" indistinguishable (S-2)");
            } else {
                assertNull(s.getTimeRelation(),
                        s.getSeriesId() + " has contentMode " + s.getContentMode()
                                + " and still carries a time relation, which S-1 refuses");
                assertNull(s.getAliveAtCutoff(),
                        s.getSeriesId() + " has contentMode " + s.getContentMode()
                                + " and still carries a liveness flag, which S-2 refuses");
            }
        }
    }

    /**
     * The parameters the ISSUE supplies are not copied into the series that
     * would then be refused for carrying them.
     *
     * Both weekly templates store reportParams {"year": "${year}", "week":
     * "${week}"} -- the substitutions the old report engine filled in. The new
     * model injects year, week, weekTo and edition from the issue being
     * rendered, and S-23 refuses them as typed parameters; carried over verbatim
     * they made an imported weekly series impossible to activate at all, because
     * S-17 needs every rule green and S-23 was never going to be. Measured on
     * the test estate: PUT .../status ACTIVE answered 400 SERIES_INVALID for both.
     */
    @Test
    public void noImportedSeriesCarriesAReportParameterTheIssueSupplies() {
        for (PublicationSeries s : importedSeries()) {
            assertEquals(List.of(), SeriesValidator.reservedReportParams(s.getReportParams()),
                    s.getSeriesId() + " was imported with a report parameter that S-23 refuses, so "
                            + "it cannot be activated until somebody hand-edits it");
        }
    }

    /** The two weekly series specifically, named because they are the ones that failed. */
    @Test
    public void theWeeklySeriesImportWithNeitherAYearNorAWeekParameter() {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        importedSeries().forEach(s -> byId.put(s.getSeriesId(), s.getReportParams()));

        for (String seriesId : List.of("weekly-ntm", "weekly-ntm-p-t")) {
            Map<String, Object> params = byId.get(seriesId);
            assertNotNull(params, seriesId + " is not among the imported series: " + byId.keySet());
            assertFalse(params.containsKey("year"), seriesId + " still carries a year parameter");
            assertFalse(params.containsKey("week"), seriesId + " still carries a week parameter");
        }
    }

    /**
     * Dropping is narrow: a reserved name, or a value still holding a ${...}.
     *
     * Everything else is a parameter somebody configured deliberately and the
     * new model has no opinion about, so it travels. Asserted directly because
     * the captured estate happens to carry nothing but the reserved pair -- the
     * "keeps the rest" half of the rule has no witness in the fixture, and an
     * unwitnessed half is the one that quietly turns into "drops everything".
     */
    @Test
    public void onlyTheDerivedNamesAndTheUnresolvedTokensAreDropped() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("year", "${year}");
        params.put("week", "${week}");
        params.put("Edition", 4);
        params.put("chartNumber", "${chart}");
        params.put("area", "Kattegat");
        params.put("copies", 250);

        Map<String, Object> kept = LegacySeriesTranslation.importableReportParams(params);

        assertEquals(Set.of("area", "copies"), kept.keySet(),
                "the issue supplies year, week and edition -- and a ${...} kept as a literal reaches "
                        + "the report as the characters themselves");
        assertEquals("Kattegat", kept.get("area"));
        assertEquals(250, kept.get("copies"));
        assertTrue(LegacySeriesTranslation.importableReportParams(null).isEmpty(),
                "a template with no parameters imports as no parameters, never as null");
    }

    /**
     * No imported series breaks a rule that is refused on EVERY save.
     *
     * S-22 and S-23 are the two a draft may not break either, because they are
     * not gaps somebody fills in later: they are values the model supplies
     * itself, and a series carrying one is already known to fail activation the
     * day it is created. That is precisely what the copied report parameters did
     * -- and asserting it as the hard-rule set rather than as S-23 by name keeps
     * the claim tied to whatever the save actually enforces.
     */
    @Test
    public void noImportedSeriesBreaksARuleThatEverySaveEnforces() {
        List<String> refusals = new ArrayList<>();
        for (PublicationSeries s : importedSeries()) {
            SeriesValidator.hardRules(s).forEach(e ->
                    refusals.add(s.getSeriesId() + ": " + e.rule() + " on " + e.field()
                            + " -- " + e.message()));
        }
        assertEquals(List.of(), refusals,
                "a series that breaks a hard rule cannot be saved, let alone activated");
    }

    /**
     * What stands between an imported series and ACTIVE, and nothing else.
     *
     * S-17 needs every rule green, so the useful question is not "does the
     * translation alone validate" -- it cannot, because the import derives the
     * nominal schedule and the first interval from the ISSUES it goes on to
     * write, which this reconstruction does not have. The question is whether
     * anything ELSE is in the way, and the answer has to stay no: a single
     * remaining rule is a series an operator has to hand-edit before the archive
     * can publish, which is the state the copied report parameters left both
     * weekly series in.
     *
     * S-20 is admitted for the two publications whose legacy row names no domain
     * at all -- the accumulated yearly EfS and NCAGS 2021, both cadenced, both
     * domainless in the estate. That is missing DATA rather than a translation
     * defect: no rule can invent the timezone their cut-offs are read in, and
     * which domain they belong to is an editorial answer.
     */
    @Test
    public void nothingButTheIssueDerivedFieldsStandsBetweenAnImportedSeriesAndActivation() {
        // Derived by the import from the issues it writes, so absent here by
        // construction and not a finding.
        Set<String> fromTheIssues = Set.of("S-4", "S-5", "S-6", "S-7");

        List<String> refusals = new ArrayList<>();
        for (PublicationSeries s : importedSeries()) {
            for (SeriesValidator.FieldError e : SeriesValidator.validateForActivation(s, null)) {
                if (fromTheIssues.contains(e.rule())) {
                    continue;
                }
                if ("S-20".equals(e.rule()) && s.getDomain() == null) {
                    continue;
                }
                refusals.add(s.getSeriesId() + ": " + e.rule() + " on " + e.field()
                        + " -- " + e.message());
            }
        }
        assertEquals(List.of(), refusals,
                "an imported series that needs hand-editing before it can be activated is an "
                        + "archive nobody can publish from");
    }

    /** The two the estate leaves domainless are named, so the number cannot drift unnoticed. */
    @Test
    public void exactlyTwoImportedSeriesCarryACadenceAndNoDomain() {
        List<String> domainless = importedSeries().stream()
                .filter(s -> s.getDomain() == null)
                .filter(s -> s.getCadence() != null
                        && s.getCadence() != org.niord.core.publication.series.SeriesCadence.NONE)
                .map(PublicationSeries::getSeriesId)
                .sorted()
                .toList();

        assertEquals(List.of("accumulated-yearly-ntm", "ncags-2021"), domainless,
                "S-20 refuses a cadenced series with no domain, and no translation can invent one; "
                        + "which domain these belong to is an editorial answer, not an importable one");
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

    // --------------------------------------------------- publications to issues

    /** Id-space continuity, over every row: publicId IS the legacy id. */
    @BindsRule({"X-2"})
    @Test
    public void everyIssueKeepsItsLegacyIdVerbatim() {
        List<String> broken = new ArrayList<>();

        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN, (java.util.Date) null);
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
    @BindsRule({"X-1"})
    @Test
    public void theImportedIdSpaceDoesNotCollide() {
        Set<String> seen = new HashSet<>();
        for (Publication p : LegacyEstateFixture.publications()) {
            assertTrue(seen.add(LegacyIssueTranslation.translate(p, null, FROZEN, (java.util.Date) null).getPublicId()),
                    "duplicate publicId: " + p.getPublicationId());
        }
        assertEquals(1077, seen.size());
    }

    /**
     * Paths are carried verbatim, revision segment included, and every filePath
     * is contained by its issue's repoPath.
     *
     * The containment rule is D-4's imported layout, which is the fixture the invariant-binding pass
     * waits on.
     */
    @BindsRule({"D-4"})
    @Test
    public void pathsAreVerbatimAndContainedByTheirRepoPath() {
        int checked = 0;
        List<String> broken = new ArrayList<>();

        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN, (java.util.Date) null);
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
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN, (java.util.Date) null);
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
                    LegacyIssueTranslation.translate(p, null, FROZEN, (java.util.Date) null).getStatus(), 1, Integer::sum);
        }

        // The captured estate: 1042 ACTIVE, 31 INACTIVE, 3 RECORDING, 1 DRAFT.
        assertEquals(1042, distribution.get(IssueStatus.PUBLISHED));
        assertEquals(31, distribution.get(IssueStatus.RETIRED));
        assertEquals(4, distribution.get(IssueStatus.OPEN), "3 RECORDING + 1 DRAFT");
    }

    /** Every issue arrives with its own snapshot header already written. */
    @Test
    public void theTranslationWritesTheSnapshotHeader() {
        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN, (java.util.Date) null);
            assertNotNull(issue.getSnapshotTimeRelation(), p.getPublicationId());
            assertNotNull(issue.getSnapshotAliveAtCutoff(), p.getPublicationId());
            assertEquals(FROZEN, issue.getSnapshotFrozenAt());
        }
    }
    // ------------------------------------------- descs are ATTACHED, not merely held

    /**
     * Every translated desc points back at its series.
     *
     * descs is mappedBy="entity": the desc row owns the foreign key, so a desc
     * that is merely present in the list is still cascaded on save and still
     * written -- with a null entity_id. Nothing fails. The series simply reads
     * back afterwards with no name, which is how the first import produced
     * twenty archive rows displaying their own id where a title belongs.
     *
     * Asserting the back-reference rather than the list is the point: the list
     * was always right.
     */
    @Test
    public void everySeriesDescIsAttachedToItsSeries() {
        int checked = 0;
        for (PublicationSeries s : series()) {
            assertFalse(s.getDescs().isEmpty(),
                    s.getSeriesId() + " translated with no descs at all");
            for (PublicationSeriesDesc d : s.getDescs()) {
                assertSame(s, d.getEntity(), s.getSeriesId() + "/" + d.getLang()
                        + ": desc is unattached, so it persists with a null entity_id");
                assertNotNull(d.getName(), s.getSeriesId() + "/" + d.getLang());
                checked++;
            }
        }
        assertTrue(checked > 0, "the estate fixture carried no series descs to check");
    }

    /** The same over every issue -- where the name and the file path both live. */
    @Test
    public void everyIssueDescIsAttachedToItsIssue() {
        int checked = 0;
        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationIssue issue = LegacyIssueTranslation.translate(p, null, FROZEN, (java.util.Date) null);
            for (PublicationIssueDesc d : issue.getDescs()) {
                assertSame(issue, d.getEntity(), p.getPublicationId() + "/" + d.getLang()
                        + ": desc is unattached, so it persists with a null entity_id");
                checked++;
            }
        }
        assertTrue(checked > 0, "the estate fixture carried no issue descs to check");
    }

    /**
     * The import and the one-off editor mint into ONE seriesId namespace.
     *
     * They used to fold Danish letters at different points -- one before the
     * accent strip, one after -- and NFD turns the ring above a into a combining
     * mark, so a fold applied afterwards has nothing left to see. The importer
     * answered "arsberetning" where the editor answered "aarsberetning". A series
     * id is immutable after create, so that disagreement never surfaces as a
     * conflict: it produces two series meant to be one.
     */
    @Test
    public void theSeriesIdFoldIsTheSharedOne() {
        for (String title : new String[]{"Årsberetning", "Søkort", "Ædelmetal", "Skydeområder"}) {
            assertEquals(org.niord.core.publication.series.SeriesIdSlug.fold(title),
                    LegacySeriesTranslation.slug(title),
                    "the importer folds '" + title + "' differently from the interactive editor");
        }
        assertEquals("aarsberetning", LegacySeriesTranslation.slug("Årsberetning"),
                "aa is the Danish transliteration; dropping the ring loses a letter rather than "
                        + "an accent");
    }
}
