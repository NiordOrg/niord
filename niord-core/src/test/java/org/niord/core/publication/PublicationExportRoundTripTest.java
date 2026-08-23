package org.niord.core.publication;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.util.JsonUtils;
import org.niord.model.DataFilter;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.publication.PublicationType;
import org.niord.core.publication.vo.SystemPublicationVo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SystemPublicationVo[] export still round-trips.
 *
 * That array shape is a de-facto interchange format: it lives in deployment
 * seeds and in exported files in the wild, and the batch importer reads exactly
 * it. The redesign keeps the legacy tables and endpoints alive so it survives --
 * but nothing asserted that an export can still be re-imported and re-exported
 * to the same document, and "no legacy functionality is dropped" is only a claim
 * until something checks.
 *
 * Export -> import -> export, compared byte for byte. Not export against a
 * hand-written expectation: the property that matters is that the format is a
 * fixed point of its own round trip, so a field silently lost on import shows up
 * as a difference rather than as a fixture nobody updated.
 *
 * No database and no server: this exercises the entity/value-object pair and the
 * same JsonUtils the batch importer uses.
 */
public class PublicationExportRoundTripTest {

    /**
     * The estate survives a full round trip.
     *
     * Everything except the revision, which the entity deliberately bumps on
     * every export -- see the next test, which pins that as the ONLY difference
     * rather than letting it hide others.
     */
    @Test
    public void theExportRoundTripsToTheSameDocument() throws IOException {
        String exported = export(fixtureEstate());
        String reExported = export(toEntities(reimport(exported)));

        assertEquals(normaliseRevision(exported), normaliseRevision(reExported),
                "a SystemPublicationVo[] export no longer survives a re-import. Some field is "
                        + "written by the export and dropped by the Publication(PublicationVo) "
                        + "constructor, so every deployment seed and every file exported in the "
                        + "wild silently loses it on the way back in.");
    }

    /**
     * The revision is the only field that moves, and it moves by exactly one.
     *
     * toVo() writes revision + 1 on purpose: an import is a new revision. That is
     * a decision rather than a bug, but it has to be an isolated one -- if a
     * second field started drifting, the normalised comparison above would go on
     * passing.
     */
    @Test
    public void theRevisionIsTheOnlyFieldThatMoves() throws IOException {
        List<SystemPublicationVo> first = reimport(export(fixtureEstate()));
        List<SystemPublicationVo> second = reimport(export(toEntities(first)));

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getRevision() + 1, second.get(i).getRevision(),
                    "revision moved by something other than one on " + first.get(i).getPublicationId());
        }

        assertEquals(normaliseRevision(JsonUtils.toJson(first)),
                normaliseRevision(JsonUtils.toJson(second)));
    }

    /**
     * repoPath is exported but not read back by the constructor -- and that is
     * survivable only because it is derivable.
     *
     * The importer does not carry it; the save path regenerates it from the
     * publicationId with a pure function. So the round trip holds, but it holds
     * for a reason worth pinning: change the hashing and every exported file in
     * the wild starts importing to a different repository path, with no error.
     */
    @Test
    public void repoPathIsNotCarriedByTheImportButIsDerivable() throws IOException {
        Publication original = fixtureEstate().get(0);
        original.checkPublicationId();
        String repoPath = original.getRepoPath();
        assertTrue(repoPath != null && !repoPath.isBlank());

        SystemPublicationVo vo = JsonUtils.fromJson(export(List.of(original)),
                new TypeReference<List<SystemPublicationVo>>() { }).get(0);
        assertEquals(repoPath, vo.getRepoPath(), "the export must carry repoPath");

        Publication imported = new Publication(vo);
        assertEquals(null, imported.getRepoPath(),
                "the constructor now carries repoPath; if that changed deliberately this test "
                        + "should say so, because the save path also derives it and the two could "
                        + "disagree");

        imported.checkPublicationId();
        assertEquals(repoPath, imported.getRepoPath(),
                "the derived repoPath no longer matches the exported one, so every file exported "
                        + "before the change imports to a different repository folder and its "
                        + "attachments go missing -- silently, because nothing reads the old path");
    }

    /** The descriptions survive with their language, title, link and file name. */
    @Test
    public void theDescriptionsSurviveInEveryLanguage() throws IOException {
        SystemPublicationVo vo = reimport(export(fixtureEstate())).get(0);

        assertNotNull(vo.getDescs());
        assertEquals(2, vo.getDescs().size());
        assertEquals("da", vo.getDescs().get(0).getLang());
        assertEquals("Efterretninger for Søfarende 33/2017", vo.getDescs().get(0).getTitle());
        assertEquals("efs-33-2017.pdf", vo.getDescs().get(0).getFileName());
        assertEquals("en", vo.getDescs().get(1).getLang());
    }

    // ------------------------------------------------------------------ helpers

    /** Exports as the admin endpoint and the deployment seeds do. */
    private static String export(List<Publication> publications) throws IOException {
        DataFilter filter = DataFilter.get();
        List<SystemPublicationVo> vos = new ArrayList<>();
        for (Publication p : publications) {
            vos.add(p.toVo(SystemPublicationVo.class, filter));
        }
        return JsonUtils.toJson(vos);
    }

    /** Re-imports as the batch reader and processor do, then exports again. */
    private static List<SystemPublicationVo> reimport(String json) throws IOException {
        return JsonUtils.fromJson(json, new TypeReference<List<SystemPublicationVo>>() { });
    }

    private static List<Publication> toEntities(List<SystemPublicationVo> vos) {
        List<Publication> out = new ArrayList<>();
        for (SystemPublicationVo vo : vos) {
            out.add(new Publication(vo));
        }
        return out;
    }

    /**
     * Blanks the revision so the comparison is about the other fields.
     *
     * Deliberately blunt: it rewrites every occurrence rather than parsing, so a
     * revision appearing somewhere unexpected is normalised too instead of
     * failing the regex and being silently skipped.
     */
    private static String normaliseRevision(String json) {
        return json.replaceAll("\"revision\":\\d+", "\"revision\":0");
    }

    /**
     * Two publications and the template one of them is based on.
     *
     * Every SystemPublicationVo field that the export carries is populated: a
     * round-trip test whose fixture leaves a field null proves nothing about that
     * field.
     */
    private static List<Publication> fixtureEstate() {
        PublicationCategory category = new PublicationCategory();
        category.setCategoryId("nautical-charts");
        category.setPriority(10);
        category.setPublish(true);
        category.checkCreateDesc("da").setName("Søkort");
        category.checkCreateDesc("en").setName("Nautical charts");

        Publication template = new Publication();
        template.setPublicationId("11111111-1111-1111-1111-111111111111");
        template.setCreated(new Date(1_483_228_800_000L));
        template.setUpdated(new Date(1_483_228_800_000L));
        template.setMainType(PublicationMainType.TEMPLATE);
        template.setStatus(PublicationStatus.ACTIVE);
        template.setType(PublicationType.MESSAGE_REPORT);
        template.setCategory(category);
        template.setEdition(1);
        template.setMessageTagFormat("efs-${week}-${year}");
        template.setMessageTagFilter("msg.publishDateFrom != null");
        template.setMessagePublication(MessagePublication.EXTERNAL);
        template.setLanguageSpecific(true);
        template.getPrintSettings().put("pageSize", "A4");
        template.getReportParams().put("mapThumbnails", Boolean.TRUE);
        template.checkCreateDesc("da").setTitle("EfS skabelon");
        template.checkCreateDesc("en").setTitle("NM template");

        Publication issue = new Publication();
        issue.setPublicationId("5eab7f50-d890-42d9-8f0a-d30e078d3d5a");
        issue.setCreated(new Date(1_483_228_800_000L));
        issue.setUpdated(new Date(1_500_000_000_000L));
        issue.setMainType(PublicationMainType.PUBLICATION);
        issue.setStatus(PublicationStatus.ACTIVE);
        issue.setType(PublicationType.LINK);
        issue.setCategory(category);
        issue.setTemplate(template);
        issue.setEdition(1);
        issue.setPublishDateFrom(new Date(1_502_755_200_000L));
        issue.setPublishDateTo(new Date(1_503_359_999_999L));
        issue.setMessagePublication(MessagePublication.EXTERNAL);
        issue.setLanguageSpecific(true);
        issue.getPrintSettings().put("pageSize", "A4");

        var da = issue.checkCreateDesc("da");
        da.setTitle("Efterretninger for Søfarende 33/2017");
        da.setLink("rest/repo/file/publications/5e/ab/efs-33-2017.pdf");
        da.setFileName("efs-33-2017.pdf");
        da.setMessagePublicationFormat("EfS ${week}/${year}");

        var en = issue.checkCreateDesc("en");
        en.setTitle("Notices to Mariners 33/2017");
        en.setLink("rest/repo/file/publications/5e/ab/nm-33-2017.pdf");
        en.setFileName("nm-33-2017.pdf");
        en.setMessagePublicationFormat("NM ${week}/${year}");

        return List.of(issue, template);
    }
}
