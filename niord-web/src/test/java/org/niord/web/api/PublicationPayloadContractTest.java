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

package org.niord.web.api;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationDesc;
import org.niord.model.DataFilter;
import org.niord.model.publication.PublicationCategoryDescVo;
import org.niord.model.publication.PublicationCategoryVo;
import org.niord.model.publication.PublicationDescVo;
import org.niord.model.publication.PublicationType;
import org.niord.model.publication.PublicationVo;

import jakarta.xml.bind.annotation.XmlType;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What /rest/public/v1/publications is allowed to emit.
 *
 * PublicationVo is a published XSD, consumed by clients this codebase does not
 * own and cannot redeploy. A field added, renamed or reordered here is not a
 * refactor -- it is a breaking change to an interchange format, and nothing else
 * in the build would notice.
 *
 * So this pins two things: the SHAPE (declared fields and their order) and the
 * BYTES (a golden payload, serialized through the endpoint's own mapper). The
 * shape assertions fail with a readable message; the byte assertion catches
 * whatever the shape assertions did not think of.
 *
 * No database and no server.
 */
public class PublicationPayloadContractTest {

    /**
     * The publication shape is frozen.
     *
     * Field ORDER matters as much as membership: propOrder drives XML element
     * order and Jackson emits declaration order, so a reorder changes both
     * documents while every getter still works.
     */
    @Test
    public void thePublicationShapeIsFrozen() {
        assertIterableEquals(
                List.of("publicationId", "created", "updated", "category", "type",
                        "publishDateFrom", "publishDateTo", "descs"),
                declaredFields(PublicationVo.class),
                "PublicationVo's fields changed. This is a published XSD: adding, removing, renaming "
                        + "or reordering one breaks every client that parses it positionally. If the "
                        + "redesign needs another field, it needs another DTO.");

        assertArrayEquals(
                new String[] { "category", "type", "publishDateFrom", "publishDateTo", "descs" },
                PublicationVo.class.getAnnotation(XmlType.class).propOrder(),
                "PublicationVo's propOrder changed, which reorders the XML elements");

        assertIterableEquals(
                List.of("lang", "title", "titleFormat", "link", "fileName", "messagePublicationFormat"),
                declaredFields(PublicationDescVo.class),
                "PublicationDescVo's fields changed");

        assertIterableEquals(
                List.of("categoryId", "priority", "publish", "descs"),
                declaredFields(PublicationCategoryVo.class),
                "PublicationCategoryVo's fields changed");

        assertIterableEquals(
                List.of("lang", "name", "description"),
                declaredFields(PublicationCategoryDescVo.class),
                "PublicationCategoryDescVo's fields changed");
    }

    /**
     * The public shape cannot carry the operational fields, structurally.
     *
     * fileSource, messageTagFilter, template and the rest live on
     * SystemPublicationVo. A field that is not declared here cannot leak here,
     * whatever a future endpoint does with its filter.
     */
    @Test
    public void theOperationalFieldsAreStructurallyAbsent() {
        List<String> fields = declaredFields(PublicationVo.class);
        for (String operational : List.of("fileSource", "messageTagFilter", "messageTag", "template",
                "domain", "status", "repoPath", "reportParams", "printSettings")) {
            assertFalse(fields.contains(operational),
                    "PublicationVo declares " + operational + "; the tier split is the only thing "
                            + "keeping it off an anonymous endpoint");
        }
    }

    /**
     * The golden payload -- byte for byte, in both date formats.
     *
     * Serialized through ApiRestService's own mapper factory rather than a
     * hand-built one, because the endpoint's date handling is part of what is
     * being pinned: dateFormat=UNIX_EPOCH writes an integer and ISO_8601 writes
     * a string, and a client parsing one cannot read the other.
     */
    @Test
    public void theEmittedJsonIsByteIdenticalToTheGolden() throws IOException {
        PublicationVo vo = fixturePublication().toVo(PublicationVo.class, DataFilter.get().lang("da"));

        assertGolden("publication-unix-epoch.json",
                ApiRestService.objectMapperForDateFormat(ApiRestService.JsonDateFormat.UNIX_EPOCH)
                        .writeValueAsString(vo));

        assertGolden("publication-iso-8601.json",
                ApiRestService.objectMapperForDateFormat(ApiRestService.JsonDateFormat.ISO_8601)
                        .writeValueAsString(vo));
    }

    /**
     * The language filter still reaches the descriptions.
     *
     * The refactor moved the DataFilter out of the REST layer and into
     * AbstractApiService. If the language were dropped on the way, every caller
     * would silently receive every language -- a bigger payload that still
     * parses, so nothing downstream would complain.
     */
    @Test
    public void theLanguageFilterStillSelectsOneDescription() {
        Publication pub = fixturePublication();

        PublicationVo da = pub.toVo(PublicationVo.class, DataFilter.get().lang("da"));
        assertEquals(1, da.getDescs().size());
        assertEquals("da", da.getDescs().get(0).getLang());

        PublicationVo all = pub.toVo(PublicationVo.class, DataFilter.get());
        assertEquals(2, all.getDescs().size(),
                "with no language the filter must not narrow; /publications is called without lang");
    }

    // ------------------------------------------------------------- externalize

    /** Relative links become absolute; already-absolute links are left alone. */
    @Test
    public void externalizeRewritesOnlyRelativeLinks() {
        PublicationVo vo = voWithLinks("rest/repo/file/x/y.pdf", "https://example.org/z.pdf");

        ApiRestService.externalize(vo, "https://niord.example/");

        assertEquals("https://niord.example/rest/repo/file/x/y.pdf", vo.getDescs().get(0).getLink());
        assertEquals("https://example.org/z.pdf", vo.getDescs().get(1).getLink(),
                "an absolute link was rewritten; prefixing an absolute URL produces a dead link, and "
                        + "the transition union merges rows that may already carry one");
    }

    /**
     * Running it twice changes nothing.
     *
     * The union merges two halves that need not have passed through this the same
     * number of times, so the operation has to be safe to repeat.
     */
    @Test
    public void externalizeIsIdempotent() {
        PublicationVo vo = voWithLinks("rest/repo/file/x/y.pdf");

        ApiRestService.externalize(vo, "https://niord.example/");
        String once = vo.getDescs().get(0).getLink();
        ApiRestService.externalize(vo, "https://niord.example/");

        assertEquals(once, vo.getDescs().get(0).getLink());
    }

    /** It is pure enough to be handed anything: null publication, null descs, blank link. */
    @Test
    public void externalizeToleratesTheEmptyCases() {
        assertNull(ApiRestService.externalize(null, "https://niord.example/"));

        PublicationVo noDescs = new PublicationVo();
        assertSame(noDescs, ApiRestService.externalize(noDescs, "https://niord.example/"));

        PublicationVo blank = voWithLinks("");
        ApiRestService.externalize(blank, "https://niord.example/");
        assertEquals("", blank.getDescs().get(0).getLink(),
                "a blank link must stay blank rather than become the bare base URI");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * One publication, with every field the public shape carries populated.
     *
     * Fixed instants rather than "now": a golden payload that moves is not a
     * golden payload.
     */
    private static Publication fixturePublication() {
        PublicationCategory category = new PublicationCategory();
        category.setCategoryId("nautical-charts");
        category.setPriority(10);
        category.setPublish(true);
        category.checkCreateDesc("da").setName("Søkort");
        category.checkCreateDesc("en").setName("Nautical charts");

        Publication pub = new Publication();
        pub.setPublicationId("5eab7f50-d890-42d9-8f0a-d30e078d3d5a");
        pub.setCreated(new Date(1_483_228_800_000L));  // 2017-01-01T00:00:00Z
        pub.setUpdated(new Date(1_500_000_000_000L));  // 2017-07-14T02:40:00Z
        pub.setType(PublicationType.LINK);
        pub.setCategory(category);
        pub.setPublishDateFrom(new Date(1_502_755_200_000L));
        pub.setPublishDateTo(new Date(1_503_359_999_999L));

        PublicationDesc da = pub.checkCreateDesc("da");
        da.setTitle("Efterretninger for Søfarende 33/2017");
        da.setLink("rest/repo/file/publications/5e/ab/efs-33-2017.pdf");
        da.setFileName("efs-33-2017.pdf");

        PublicationDesc en = pub.checkCreateDesc("en");
        en.setTitle("Notices to Mariners 33/2017");
        en.setLink("rest/repo/file/publications/5e/ab/nm-33-2017.pdf");
        en.setFileName("nm-33-2017.pdf");

        return pub;
    }

    private static PublicationVo voWithLinks(String... links) {
        PublicationVo vo = new PublicationVo();
        List<PublicationDescVo> descs = new ArrayList<>();
        String[] langs = { "da", "en", "de" };
        for (int i = 0; i < links.length; i++) {
            PublicationDescVo desc = new PublicationDescVo();
            desc.setLang(langs[i]);
            desc.setLink(links[i]);
            descs.add(desc);
        }
        vo.setDescs(descs);
        return vo;
    }

    private static List<String> declaredFields(Class<?> type) {
        List<String> out = new ArrayList<>();
        for (var f : type.getDeclaredFields()) {
            if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers())) {
                out.add(f.getName());
            }
        }
        return out;
    }

    /**
     * Compares against the golden resource.
     *
     * -Dgolden.write=true regenerates it -- and then the diff has to be read,
     * because regenerating is how a breaking change gets blessed by accident.
     */
    private static void assertGolden(String name, String actual) throws IOException {
        String path = "golden/" + name;

        if (Boolean.getBoolean("golden.write")) {
            java.nio.file.Path file = java.nio.file.Paths.get("src/test/resources", path);
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }

        String expected;
        try (InputStream in = PublicationPayloadContractTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(in, "missing golden resource " + path);
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals(expected, actual,
                "the emitted payload changed. This is the published API shape -- if the change is "
                        + "intended it is a versioning decision, not a test update.");
    }
}
