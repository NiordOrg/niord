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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;
import org.niord.core.publication.series.resolve.IssueOrdering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The messages a document is rendered from, loaded per ASSOCIATION rather than
 * per message.
 *
 * A document renders every field of every member: the descs, the areas and their
 * lineage, the categories, the charts, the references and their descs, the
 * attachments and their descs, and the parts with their descs and their
 * geometry. Fetching a member one at a time leaves every one of those
 * collections to be initialised on first touch, so a 170-member issue asks the
 * database somewhere north of two thousand questions -- and the database is a
 * network hop away, so the cost is round trips rather than work. Measured
 * against the two largest weekly issues, that per-member loading was the single
 * biggest term in the time to produce a preview.
 *
 * The queries below ask the same questions in the other order: one per
 * association, over the whole member set at once. The number of round trips is
 * then a property of the SHAPE of a message rather than of how many are in the
 * issue, which is what makes a 170-member issue cost about what a 9-member one
 * costs to load.
 *
 * Nothing is remembered between calls. The returned map is the caller's, it dies
 * with the render, and the only reason it exists is so that a two-language
 * document does not ask for the same rows twice.
 *
 * ONE association per query, deliberately. Fetching two collections in one query
 * multiplies the rows and, for the ordered lists here, is how an ordered
 * collection quietly acquires holes. Each query below fetches exactly one
 * collection, plus to-one associations, which cannot multiply anything.
 */
@ApplicationScoped
public class IssueMessageLoader {

    /**
     * How many ids go into one IN-list.
     *
     * An issue is normally a few hundred members, but a one-off can be far
     * larger, and an unbounded IN-list is how a query stops being planned and
     * starts being parsed.
     */
    private static final int CHUNK = 500;

    @Inject
    EntityManager em;

    /**
     * The named messages, keyed by uid, with everything a document reads already
     * in memory.
     *
     * A uid with no message is simply absent: whether a missing frozen member is
     * a failure is the caller's question, not this one's, and the two callers
     * answer it differently.
     */
    public Map<String, Message> load(List<String> uids) {
        Map<String, Message> byUid = new LinkedHashMap<>();
        if (uids == null || uids.isEmpty()) {
            return byUid;
        }

        Set<String> distinct = new LinkedHashSet<>(uids);
        List<List<String>> chunks = chunk(new ArrayList<>(distinct));

        for (List<String> ids : chunks) {
            // The roots. Everything after this initialises collections on rows
            // that are already in the persistence context.
            List<Message> messages = em.createNamedQuery("Message.findByUids", Message.class)
                    .setParameter("uids", ids)
                    .getResultList();
            for (Message m : messages) {
                byUid.put(m.getUid(), m);
            }
            if (messages.isEmpty()) {
                continue;
            }

            fetchCollection("select m from Message m left join fetch m.descs where m.uid in (:uids)", ids);
            fetchCollection("select m from Message m left join fetch m.areas where m.uid in (:uids)", ids);
            fetchCollection("select m from Message m left join fetch m.categories where m.uid in (:uids)", ids);
            fetchCollection("select m from Message m left join fetch m.charts where m.uid in (:uids)", ids);
            fetchCollection("select m from Message m left join fetch m.references where m.uid in (:uids)", ids);
            fetchCollection("select m from Message m left join fetch m.attachments where m.uid in (:uids)", ids);
            // The geometry comes along here because it is a to-one and is loaded
            // eagerly anyway: left to itself it would be one select per part.
            fetchCollection("select m from Message m left join fetch m.parts p left join fetch p.geometry "
                    + "where m.uid in (:uids)", ids);

            // The second level. Each of these is a collection hanging off one of
            // the collections above, so it needs its own root to be fetched in
            // one go.
            em.createQuery("select r from Reference r left join fetch r.descs where r.message.uid in (:uids)")
                    .setParameter("uids", ids)
                    .getResultList();
            em.createQuery("select a from Attachment a left join fetch a.descs where a.message.uid in (:uids)")
                    .setParameter("uids", ids)
                    .getResultList();
            em.createQuery("select p from MessagePart p left join fetch p.descs where p.message.uid in (:uids)")
                    .setParameter("uids", ids)
                    .getResultList();
            em.createQuery("select p from MessagePart p left join fetch p.eventDates "
                            + "where p.message.uid in (:uids)")
                    .setParameter("uids", ids)
                    .getResultList();

            fetchGeometries(messages);
            fetchDescs(messages);
        }

        return byUid;
    }

    /** Initialises one collection over the whole chunk. */
    private void fetchCollection(String jpql, List<String> ids) {
        em.createQuery(jpql, Message.class)
                .setParameter("uids", ids)
                .getResultList();
    }

    /** The features of every part's geometry, in one query rather than one per part. */
    private void fetchGeometries(List<Message> messages) {
        Set<Integer> collectionIds = new LinkedHashSet<>();
        for (Message m : messages) {
            for (MessagePart part : m.getParts()) {
                FeatureCollection fc = part.getGeometry();
                if (fc != null && fc.getId() != null) {
                    collectionIds.add(fc.getId());
                }
            }
        }
        for (List<Integer> ids : chunk(new ArrayList<>(collectionIds))) {
            em.createQuery("select fc from FeatureCollection fc left join fetch fc.features "
                            + "where fc.id in (:ids)", FeatureCollection.class)
                    .setParameter("ids", ids)
                    .getResultList();
        }
    }

    /**
     * The names of every area and category a member sits in, including the ones
     * only their lineage mentions.
     *
     * Both repeat across an issue, so this is a handful of rows however many
     * members there are -- but without it the first message in each area pays for
     * a query, and a heading walks the parent chain paying for one more per rung.
     */
    private void fetchDescs(List<Message> messages) {
        Set<Integer> areaIds = new LinkedHashSet<>();
        Set<Integer> categoryIds = new LinkedHashSet<>();
        for (Message m : messages) {
            for (Area area : m.getAreas()) {
                for (Area rung : area.lineageAsList()) {
                    if (rung.getId() != null) {
                        areaIds.add(rung.getId());
                    }
                }
            }
            for (Category category : m.getCategories()) {
                for (Category rung : category.lineageAsList()) {
                    if (rung.getId() != null) {
                        categoryIds.add(rung.getId());
                    }
                }
            }
        }
        for (List<Integer> ids : chunk(new ArrayList<>(areaIds))) {
            em.createQuery("select a from Area a left join fetch a.descs where a.id in (:ids)", Area.class)
                    .setParameter("ids", ids)
                    .getResultList();
        }
        for (List<Integer> ids : chunk(new ArrayList<>(categoryIds))) {
            em.createQuery("select c from Category c left join fetch c.descs where c.id in (:ids)", Category.class)
                    .setParameter("ids", ids)
                    .getResultList();
        }
    }

    /** Splits an id list into IN-list sized pieces. */
    private <T> List<List<T>> chunk(List<T> ids) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK) {
            out.add(ids.subList(i, Math.min(ids.size(), i + CHUNK)));
        }
        return out;
    }

    /** The uids of an ordered member list, in order. */
    public static List<String> uidsOf(Collection<IssueOrdering.Orderable> ordered) {
        List<String> uids = new ArrayList<>(ordered.size());
        for (IssueOrdering.Orderable o : ordered) {
            uids.add(o.uid());
        }
        return uids;
    }
}
