/*
 * Copyright 2026 Danish Maritime Authority.
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.niord.core.domain.Domain;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationDesc;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.vo.PeriodicalType;
import org.niord.model.publication.PublicationType;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the captured production estate into Publication entities.
 *
 * The tests translate the REAL estate rather than hand-built rows, because every
 * defect this phase has produced so far came from a hand-built row agreeing with
 * the code about something the data disagreed with: the abbreviated filter
 * strings, the two-day CutoffDay, the initialised-on-one-side map. Fixtures that
 * are typed cannot contradict the typist.
 *
 * Nothing here touches a database. The entities are detached and never persisted.
 */
public final class LegacyEstateFixture {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LegacyEstateFixture() {
    }

    public static List<Publication> publications() {
        return load("/fixtures/legacy-estate/publications.json");
    }

    public static List<Publication> templates() {
        return load("/fixtures/legacy-estate/templates.json");
    }

    private static List<Publication> load(String resource) {
        try (InputStream in = LegacyEstateFixture.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing");
            }
            JsonNode root = MAPPER.readTree(in);
            List<Publication> out = new ArrayList<>();
            for (JsonNode n : root) {
                out.add(publication(n));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + resource, e);
        }
    }

    /** The raw stored filter, NULL-safe -- absent and null are the same column value. */
    public static String storedFilter(JsonNode n) {
        JsonNode f = n.get("messageTagFilter");
        return f == null || f.isNull() ? null : f.asText();
    }

    private static Publication publication(JsonNode n) {
        Publication p = new Publication();

        p.setPublicationId(text(n, "publicationId"));
        p.setRevision(n.has("revision") ? n.get("revision").asInt() : 0);
        p.setMainType(enumOf(PublicationMainType.class, text(n, "mainType")));
        p.setType(enumOf(PublicationType.class, text(n, "type")));
        p.setStatus(enumOf(PublicationStatus.class, text(n, "status")));
        p.setPeriodicalType(enumOf(PeriodicalType.class, text(n, "periodicalType")));
        p.setMessageTagFilter(storedFilter(n));
        p.setMessageTagFormat(text(n, "messageTagFormat"));
        p.setRepoPath(text(n, "repoPath"));
        p.setLanguageSpecific(n.has("languageSpecific") && n.get("languageSpecific").asBoolean());
        p.setPublishDateFrom(date(n, "publishDateFrom"));
        p.setPublishDateTo(date(n, "publishDateTo"));
        p.setCreated(date(n, "created"));
        p.setUpdated(date(n, "updated"));

        if (n.hasNonNull("edition")) {
            p.setEdition(n.get("edition").asInt());
        }
        if (n.hasNonNull("messagePublication")) {
            p.setMessagePublication(enumOf(org.niord.core.publication.vo.MessagePublication.class,
                    text(n, "messagePublication")));
        }
        if (n.hasNonNull("category")) {
            p.setCategory(category(n.get("category")));
        }
        if (n.hasNonNull("domain")) {
            p.setDomain(domain(n.get("domain")));
        }
        if (n.hasNonNull("messageTag")) {
            // The tag's created instant is what stage 2 of the cut-off cascade
            // witnesses, so omitting it here silently disables that stage and
            // makes the whole cascade look like it only ever reaches stage 1.
            JsonNode tag = n.get("messageTag");
            org.niord.core.message.MessageTag mt = new org.niord.core.message.MessageTag();
            mt.setTagId(text(tag, "tagId"));
            mt.setName(text(tag, "name"));
            mt.setCreated(date(tag, "created"));
            // Whether the tag is LOCKED decides whether its contents are frozen
            // evidence. Omitting it makes every tag look unlocked, which turns the
            // whole estate into EXPLAINED_DIFF and leaves nothing as an oracle.
            mt.setLocked(tag.has("locked") && tag.get("locked").asBoolean());
            p.setMessageTag(mt);
        }
        if (n.hasNonNull("template")) {
            Publication t = new Publication();
            t.setPublicationId(text(n.get("template"), "publicationId"));
            p.setTemplate(t);
        }
        p.setPrintSettings(map(n.get("printSettings")));
        p.setReportParams(map(n.get("reportParams")));
        p.setDescs(descs(n.get("descs")));
        return p;
    }

    private static PublicationCategory category(JsonNode n) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(text(n, "categoryId"));
        c.setPriority(n.has("priority") ? n.get("priority").asInt() : 0);
        c.setPublish(n.has("publish") && n.get("publish").asBoolean());
        return c;
    }

    private static Domain domain(JsonNode n) {
        Domain d = new Domain();
        d.setDomainId(text(n, "domainId"));
        d.setName(text(n, "name"));
        List<MessageSeries> series = new ArrayList<>();
        if (n.hasNonNull("messageSeries")) {
            for (JsonNode s : n.get("messageSeries")) {
                MessageSeries ms = new MessageSeries();
                ms.setSeriesId(text(s, "seriesId"));
                series.add(ms);
            }
        }
        d.setMessageSeries(series);
        return d;
    }

    private static List<PublicationDesc> descs(JsonNode arr) {
        List<PublicationDesc> out = new ArrayList<>();
        if (arr == null || arr.isNull()) {
            return out;
        }
        for (JsonNode n : arr) {
            PublicationDesc d = new PublicationDesc();
            d.setLang(text(n, "lang"));
            d.setTitle(text(n, "title"));
            d.setTitleFormat(text(n, "titleFormat"));
            d.setFileName(text(n, "fileName"));
            d.setLink(text(n, "link"));
            d.setMessagePublicationFormat(text(n, "messagePublicationFormat"));
            out.add(d);
        }
        return out;
    }

    private static Map<String, Object> map(JsonNode n) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (n == null || n.isNull()) {
            return out;
        }
        n.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            out.put(e.getKey(), v.isBoolean() ? v.asBoolean() : v.isNumber() ? v.numberValue() : v.asText());
        });
        return out;
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static Date date(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? new Date(n.get(field).asLong()) : null;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name) {
        return name == null || name.isBlank() ? null : Enum.valueOf(type, name);
    }
}
