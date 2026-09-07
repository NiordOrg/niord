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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.Attachment;
import org.niord.core.message.DateInterval;
import org.niord.core.message.Message;
import org.niord.core.message.MessageDesc;
import org.niord.core.message.MessagePart;
import org.niord.core.message.MessagePartDesc;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.message.Reference;
import org.niord.core.publication.TestIds;
import org.niord.model.DataFilter;
import org.niord.model.geojson.FeatureVo;
import org.niord.model.geojson.PointVo;
import org.niord.model.message.AttachmentVo.AttachmentDisplayType;
import org.niord.model.message.MainType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessageVo;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The member rows a document is rendered from must cost the same however many
 * members there are -- and must be the same rows.
 *
 * Producing a document reads every field of every member, and the fields hang
 * off the message in eight collections. Read one member at a time and each of
 * those is a separate question to a database that is a network hop away, so the
 * time to produce a document grew with the member count in ROUND TRIPS rather
 * than in work -- which is why a 170-member weekly issue took seconds to reach
 * the renderer while a 9-member one was instant.
 *
 * So the property under test is not a duration, which would be a different
 * number on every machine, but the SHAPE: a bounded number of statements,
 * independent of the member count, and value objects that are indistinguishable
 * from the ones the per-member reads produced. The second half is what makes the
 * first half safe -- a faster load that returned anything different would change
 * a published document.
 *
 * And the second half only means something over a message that HAS the fields.
 * The local estate is a message corpus with the details stripped: no parts, no
 * attachments, no references, no geometry, and titles for a handful of rows.
 * Comparing two loads over it would compare two empty lists eight times and pass
 * whatever the loader did with an ordered collection. So this suite builds one
 * member that carries every association the loader touches -- two of each, in an
 * order that is not the insertion order of their ids, with a parent rung above
 * the area and the category -- and compares the value objects over the real
 * member list WITH that message in it, in every configured language.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueRenderMemberLoadTest {

    /**
     * A real weekly member list: the uids of one released P&T issue.
     *
     * A synthetic set alone would not do either. What is being COUNTED is a real
     * issue's worth of members, and the bound has to hold at that size.
     */
    private static final String FIXTURE = "/fixtures/publications/nm-pt-w28-2026.json";

    /**
     * The ceiling on statements for one load of the whole member set.
     *
     * Thirteen queries describe the shape of a message; the rest of the headroom
     * is the to-one associations Hibernate resolves behind them (the message
     * series, the area lineage), which repeat across an issue and so do not grow
     * with it. Well under a hundred either way, against the thousand-plus a
     * per-member read costs.
     */
    private static final int STATEMENT_CEILING = 100;

    /** The languages a document is produced in, and so the ones a desc is read in. */
    private static final List<String> LANGUAGES = List.of("da", "en");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    IssueMessageLoader loader;

    @Inject
    MessageService messageService;

    @Inject
    EntityManager em;

    /** The fixture message this suite created, so it can take it away again. */
    private Integer richMessageId;

    private final List<Integer> chartIds = new ArrayList<>();
    private final List<Integer> categoryIds = new ArrayList<>();
    private final List<Integer> areaIds = new ArrayList<>();
    private Integer messageSeriesId;

    // ------------------------------------------------------------------ setup

    /** The member uids of the fixture issue, in fixture order. */
    private List<String> memberUids() throws Exception {
        try (InputStream in = IssueRenderMemberLoadTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "fixture " + FIXTURE + " is missing");
            JsonNode root = MAPPER.readTree(in);
            List<String> uids = new ArrayList<>();
            for (JsonNode member : root.get("members")) {
                uids.add(member.get("uid").asText());
            }
            return uids;
        }
    }

    /** The statements this run costs, and how long it took. */
    private record Cost(long statements, long collectionFetches, long millis) {
    }

    /*
     * A NOTE ON THE COUNTERS, because they read like a bug otherwise.
     *
     * Hibernate's statistics belong to the session factory, not to a session, so
     * they count every query this JVM makes -- including the ones the message
     * indexer makes on its own schedule, which the fixture below wakes up by
     * committing a new message. A stage measured over a few hundred milliseconds
     * picks up whatever indexing lands inside it, which is why a stage that reads
     * nothing new can still show a hundred statements it did not make.
     *
     * So the ABSOLUTE bound is asserted on the load alone, which is short and
     * reads only member rows, and the two conversion stages are compared to each
     * other as a ratio -- where the noise, being the same size in both, only makes
     * the assertion harder to pass.
     */

    private Statistics statistics() {
        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }

    /** Runs the body in its own transaction, so no run is warmed by the one before it. */
    private <T> T measured(String stage, java.util.function.Supplier<T> body, List<Cost> costs) {
        Statistics stats = statistics();
        long statements = stats.getPrepareStatementCount();
        long collections = stats.getCollectionFetchCount();
        long t0 = System.currentTimeMillis();
        T out = QuarkusTransaction.requiringNew().call(body::get);
        long millis = System.currentTimeMillis() - t0;
        Cost cost = new Cost(stats.getPrepareStatementCount() - statements,
                stats.getCollectionFetchCount() - collections, millis);
        costs.add(cost);
        System.out.println("[member-load] " + stage + ": " + cost.millis() + " ms, "
                + cost.statements() + " statements, " + cost.collectionFetches() + " collection fetches");
        return out;
    }

    // ------------------------------------------------------------------- tests

    /**
     * The two loads, side by side: same value objects, incomparable statement
     * counts.
     */
    @Test
    public void loadingPerAssociationMatchesLoadingPerMember() throws Exception {
        List<String> uids = new ArrayList<>(memberUids());
        assertTrue(uids.size() > 100, "the fixture should carry a full weekly member list");
        // In the MIDDLE of the list, not at the end: an ordered collection that
        // lost a row would still line up if the one message that has rows sat where
        // nothing follows it.
        String richUid = aMemberCarryingEveryAssociation();
        uids.add(uids.size() / 2, richUid);

        List<Cost> costs = new ArrayList<>();

        // The load ALONE, and this is the stage the absolute bound is asserted on:
        // it is the only one that reads nothing but the member rows. TWICE, and the
        // cheaper of the two counts, because the counters are shared with the
        // indexer -- see the note on them above. Two loads of the same rows cost
        // the same, so the one that came out dearer is the one something else was
        // running underneath.
        measured("load only (" + uids.size() + " members)", () -> loader.load(uids).size(), costs);
        measured("load only, again (" + uids.size() + " members)", () -> loader.load(uids).size(), costs);

        String perMember = measured("load per member (" + uids.size() + " members)", () -> {
            List<String> rendered = new ArrayList<>();
            for (String lang : LANGUAGES) {
                DataFilter filter = Message.MESSAGE_DETAILS_FILTER.lang(lang);
                for (String uid : uids) {
                    Message m = messageService.findByUid(uid);
                    if (m != null) {
                        rendered.add(asJson(m.toVo(MessageVo.class, filter)));
                    }
                }
            }
            return String.join("\n", rendered);
        }, costs);

        String perAssociation = measured("load per association (" + uids.size() + " members)", () -> {
            Map<String, Message> byUid = loader.load(uids);
            List<String> rendered = new ArrayList<>();
            for (String lang : LANGUAGES) {
                DataFilter filter = Message.MESSAGE_DETAILS_FILTER.lang(lang);
                for (String uid : uids) {
                    Message m = byUid.get(uid);
                    if (m != null) {
                        rendered.add(asJson(m.toVo(MessageVo.class, filter)));
                    }
                }
            }
            return String.join("\n", rendered);
        }, costs);

        long loadOnly = Math.min(costs.get(0).statements(), costs.get(1).statements());
        Cost perMemberCost = costs.get(2);
        Cost perAssociationCost = costs.get(3);

        assertEquals(perMember, perAssociation,
                "the per-association load must produce exactly the value objects the per-member load "
                        + "produced -- in every configured language, and for the member that actually "
                        + "carries descs, areas, categories, charts, references, attachments, parts, "
                        + "event dates and geometry");
        assertTrue(loadOnly <= STATEMENT_CEILING,
                "one load of the whole member set should cost a bounded number of statements, not "
                        + loadOnly);
        assertTrue(perAssociationCost.statements() * 5 < perMemberCost.statements(),
                "the per-association load costs " + perAssociationCost.statements()
                        + " statements against the per-member " + perMemberCost.statements()
                        + "; the point of it is that the difference is an order of magnitude");

        // And the comparison above is only worth anything if the member that has
        // the fields actually reached both renderings. Two identical lists of
        // messages carrying nothing would agree perfectly and prove nothing, which
        // is exactly the shape the estate corpus alone produces.
        for (String marker : List.of("title da", "title en", "area child da", "area parent en",
                "category child da", "reference 0 da", "reference 1 en", "caption 0 da",
                "subject 0 da", "details 1 en")) {
            assertTrue(perAssociation.contains(marker),
                    "the rendered members carry no '" + marker + "', so the comparison never saw a "
                            + "message with that association on it");
        }
        assertRichMemberIsWhole(richUid);
    }

    /**
     * The member built with two of everything, read back through the loader.
     *
     * A count per association, because the string markers above cannot speak for
     * the two collections that carry no text of their own -- the event dates and
     * the geometry features -- and because a fetch join that multiplied rows
     * would show up here as four of something rather than two.
     */
    private void assertRichMemberIsWhole(String richUid) {
        MessageVo rich = QuarkusTransaction.requiringNew().call(() -> {
            Message m = loader.load(List.of(richUid)).get(richUid);
            assertNotNull(m, "the member built for this test is not in the database");
            return m.toVo(MessageVo.class, Message.MESSAGE_DETAILS_FILTER.lang("da"));
        });

        // The desc lists are filtered to the language that was asked for, which is
        // why they are one rather than two; the comparison above runs the whole
        // rendering once per language, so both rows are covered there.
        assertEquals(1, rich.getDescs().size(), "message descs in one language");
        assertEquals(2, rich.getAreas().size(), "areas");
        // The rooted one was stored SECOND, so this pins the order column as well
        // as the lineage: swap the two and both of these go the wrong way.
        assertNull(rich.getAreas().get(0).getParent(), "the areas came back in the wrong order");
        assertNotNull(rich.getAreas().get(1).getParent(), "the area lineage did not come with it");
        assertEquals(2, rich.getCategories().size(), "categories");
        assertEquals(2, rich.getCharts().size(), "charts");
        assertEquals(2, rich.getReferences().size(), "references");
        assertEquals(1, rich.getReferences().get(0).getDescs().size(), "reference descs in one language");
        assertEquals(2, rich.getAttachments().size(), "attachments");
        assertEquals(1, rich.getAttachments().get(0).getDescs().size(), "attachment descs in one language");
        assertEquals(2, rich.getParts().size(), "parts");
        assertEquals(1, rich.getParts().get(0).getDescs().size(), "part descs in one language");
        assertEquals(2, rich.getParts().get(0).getEventDates().size(), "event dates");
        assertNotNull(rich.getParts().get(0).getGeometry(), "the part geometry did not come with it");
        assertEquals(2, rich.getParts().get(0).getGeometry().getFeatures().length, "geometry features");
    }

    /**
     * And the bound is a bound: half the members must not cost half the
     * statements, or the loader is still reading per member with extra steps.
     */
    @Test
    public void statementCountDoesNotFollowTheMemberCount() throws Exception {
        List<String> all = memberUids();
        List<String> half = all.subList(0, all.size() / 2);
        List<Cost> costs = new ArrayList<>();

        // Twice each, and the cheaper run counts, for the reason the note on the
        // counters gives: they are shared with everything else this JVM does.
        measured("load per association (" + half.size() + " members)", () -> loader.load(half).size(), costs);
        measured("load per association (" + all.size() + " members)", () -> loader.load(all).size(), costs);
        measured("load per association, again (" + half.size() + " members)",
                () -> loader.load(half).size(), costs);
        measured("load per association, again (" + all.size() + " members)",
                () -> loader.load(all).size(), costs);

        long halfCost = Math.min(costs.get(0).statements(), costs.get(2).statements());
        long fullCost = Math.min(costs.get(1).statements(), costs.get(3).statements());
        assertTrue(fullCost <= halfCost + 20,
                "doubling the members took the statement count from " + halfCost + " to " + fullCost
                        + "; one query per association should barely notice");
    }

    // -------------------------------------------------------- the rich fixture

    /**
     * One message carrying two of everything a document reads, committed.
     *
     * Committed, and in its own transaction, because both loads under test run in
     * transactions of their own and neither may see this one's persistence
     * context: an entity still in the session would be handed back without a
     * query, which is precisely the difference being measured.
     *
     * TWO of each, and lineages two deep, because the failures worth catching are
     * about ORDER and MULTIPLICITY -- a fetch join that duplicates rows, an
     * ordered list that acquires a hole, a lineage rung whose name was never
     * loaded. One of anything cannot show any of that.
     */
    private String aMemberCarryingEveryAssociation() {
        return QuarkusTransaction.requiringNew().call(() -> {
            MessageSeries series = new MessageSeries();
            series.setSeriesId(TestIds.id("ms-"));
            series.setMainType(MainType.NM);
            em.persist(series);
            em.flush();
            messageSeriesId = series.getId();

            Area parentArea = area("parent", null);
            Area childArea = area("child", parentArea);
            Area otherArea = area("other", null);

            Category parentCategory = category("parent", null);
            Category childCategory = category("child", parentCategory);

            Chart chart = chart();
            Chart otherChart = chart();

            Message m = new Message();
            m.setUid(UUID.randomUUID().toString());
            m.setMessageSeries(series);
            m.setMainType(MainType.NM);
            m.setType(Type.TEMPORARY_NOTICE);
            // DRAFT, and in a series nothing else names, so no resolution in this
            // module can pick this message up as a member of anything.
            m.setStatus(Status.DRAFT);
            m.setShortId("NM-RENDER-" + TestIds.suffix().substring(0, 8));
            m.setPublishDateFrom(new Date(1_699_000_000_000L));
            m.setHorizontalDatum("WGS84");
            m.setOriginalInformation(Boolean.TRUE);

            // The ORDERED lists get the later-minted row first, so an order column
            // that stopped being applied shows up as a difference rather than as a
            // coincidence. The categories are left in id order on purpose: that
            // list carries no order column, so the order it comes back in is the
            // database's to choose and asserting on it would be asserting on
            // nothing.
            m.getAreas().add(otherArea);
            m.getAreas().add(childArea);
            m.getCategories().add(parentCategory);
            m.getCategories().add(childCategory);
            m.getCharts().add(otherChart);
            m.getCharts().add(chart);

            for (String lang : LANGUAGES) {
                MessageDesc desc = m.createDesc(lang);
                desc.setTitle("title " + lang);
                desc.setVicinity("vicinity " + lang);
                desc.setPublication("publication " + lang);
                desc.setSource("source " + lang);
            }

            for (int i = 0; i < 2; i++) {
                Reference reference = new Reference();
                reference.setMessageId("NM-" + (800 + i) + "-26");
                reference.setType(i == 0 ? ReferenceType.REPETITION : ReferenceType.CANCELLATION);
                for (String lang : LANGUAGES) {
                    reference.createDesc(lang).setDescription("reference " + i + " " + lang);
                }
                m.addReference(reference);
            }

            for (int i = 0; i < 2; i++) {
                Attachment attachment = new Attachment();
                attachment.setType("application/pdf");
                attachment.setFileName("attachment-" + i + ".pdf");
                attachment.setPath("attachments/attachment-" + i + ".pdf");
                attachment.setFileSize(1024L + i);
                attachment.setDisplay(AttachmentDisplayType.BELOW);
                for (String lang : LANGUAGES) {
                    attachment.createDesc(lang).setCaption("caption " + i + " " + lang);
                }
                m.addAttachment(attachment);
            }

            for (int i = 0; i < 2; i++) {
                MessagePart part = new MessagePart(i == 0 ? MessagePartType.DETAILS : MessagePartType.TIME);
                for (String lang : LANGUAGES) {
                    MessagePartDesc desc = part.createDesc(lang);
                    desc.setSubject("subject " + i + " " + lang);
                    desc.setDetails("<p>details " + i + " " + lang + "</p>");
                }
                // Descending, so a list that came back in the database's own order
                // rather than in indexNo order reads differently.
                part.addEventDates(new DateInterval(Boolean.FALSE,
                        new Date(1_699_200_000_000L), new Date(1_699_300_000_000L)));
                part.addEventDates(new DateInterval(Boolean.TRUE,
                        new Date(1_699_000_000_000L), new Date(1_699_100_000_000L)));

                FeatureCollection geometry = new FeatureCollection();
                geometry.addFeature(feature(10.0 + i, 55.0 + i, "one"));
                geometry.addFeature(feature(11.0 + i, 56.0 + i, "two"));
                part.setGeometry(geometry);

                m.addPart(part);
            }

            em.persist(m);
            em.flush();
            richMessageId = m.getId();
            return m.getUid();
        });
    }

    private Feature feature(double lon, double lat, String name) {
        FeatureVo vo = new FeatureVo();
        vo.setGeometry(new PointVo(new double[]{lon, lat}));
        vo.getProperties().put("name:en", name);
        return Feature.fromGeoJson(vo);
    }

    private Area area(String name, Area parent) {
        Area a = new Area();
        a.setMrn("urn:mrn:test:render-load:area:" + TestIds.suffix());
        if (parent != null) {
            parent.addChild(a);
        }
        em.persist(a);
        em.flush();
        a.updateLineage();
        Area merged = em.merge(a);
        for (String lang : LANGUAGES) {
            merged.createDesc(lang).setName("area " + name + " " + lang);
        }
        em.flush();
        areaIds.add(merged.getId());
        return merged;
    }

    private Category category(String name, Category parent) {
        Category c = new Category();
        c.setMrn("urn:mrn:test:render-load:category:" + TestIds.suffix());
        if (parent != null) {
            parent.addChild(c);
        }
        em.persist(c);
        em.flush();
        c.updateLineage();
        Category merged = em.merge(c);
        for (String lang : LANGUAGES) {
            merged.createDesc(lang).setName("category " + name + " " + lang);
        }
        em.flush();
        categoryIds.add(merged.getId());
        return merged;
    }

    private Chart chart() {
        Chart c = new Chart();
        c.setChartNumber("RL-" + TestIds.suffix().substring(0, 10));
        c.setName("chart");
        em.persist(c);
        em.flush();
        chartIds.add(c.getId());
        return c;
    }

    /**
     * The fixture, taken away again.
     *
     * The estate database is shared, long-lived and never truncated, so a suite
     * that leaves a message behind changes what every later run of every other
     * suite resolves over.
     */
    @AfterEach
    public void removeTheFixture() {
        if (richMessageId == null && messageSeriesId == null) {
            return;
        }
        QuarkusTransaction.requiringNew().run(() -> {
            if (richMessageId != null) {
                Message m = em.find(Message.class, richMessageId);
                if (m != null) {
                    // The join rows go with the owning side; the entities they point
                    // at are removed below, children before parents.
                    m.getAreas().clear();
                    m.getCategories().clear();
                    m.getCharts().clear();
                    em.remove(m);
                }
                em.flush();
            }
            removeAll(Chart.class, chartIds);
            removeAll(Category.class, reversed(categoryIds));
            removeAll(Area.class, reversed(areaIds));
            if (messageSeriesId != null) {
                MessageSeries series = em.find(MessageSeries.class, messageSeriesId);
                if (series != null) {
                    em.remove(series);
                }
            }
            em.flush();
        });
        richMessageId = null;
        messageSeriesId = null;
        chartIds.clear();
        categoryIds.clear();
        areaIds.clear();
    }

    private <T> void removeAll(Class<T> type, List<Integer> ids) {
        for (Integer id : ids) {
            T entity = em.find(type, id);
            if (entity != null) {
                em.remove(entity);
            }
        }
        em.flush();
    }

    /** Children before parents, so a tree node is never removed under a live child. */
    private static List<Integer> reversed(List<Integer> ids) {
        List<Integer> out = new ArrayList<>(ids);
        java.util.Collections.reverse(out);
        return out;
    }

    /** The value object, as text, so two runs can be compared exactly. */
    private String asJson(MessageVo vo) {
        try {
            return MAPPER.writeValueAsString(vo);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the rendered member", e);
        }
    }
}
