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

package org.niord.core.publication;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a message is named on a publication screen, decided in ONE place.
 *
 * Every list in this API that refers to a message -- members, omissions, standing
 * curation decisions, the messages citing a publication somebody tried to delete
 * -- has to render that message as something a person recognises. The identity is
 * the uid and the uid is unreadable, so each of those lists needs the short id
 * and the title beside it, and each of them was free to decide for itself what
 * "the title" meant. Four independent language rules is four screens that name
 * the same message differently.
 *
 * So the rule is here, once:
 *
 * <ul>
 *   <li>{@code messageId} is the SHORT ID -- "NM-815-26" -- and it is display
 *       text, not a key. It is not declared unique, nothing stops it being
 *       reused, and a message that has not been numbered yet has none. It is
 *       NULL in that case rather than falling back to the uid: a uid dressed up
 *       as a short id is a string a reader will try to cite.</li>
 *   <li>{@code title} is the LIVE title in the requested language; failing that,
 *       the first description that carries one, so a Danish-only message still
 *       names itself on an English screen. Blank counts as absent throughout --
 *       a row showing an empty name reads as a message with no subject, and the
 *       other language is the better answer. Null when the message carries no
 *       titled description at all.</li>
 * </ul>
 *
 * ONE QUERY PER LIST, never one per row. These lists run to hundreds of rows and
 * some of them are fetched while somebody is still typing; a lookup per row is
 * hundreds of round-trips on that path. The chunk exists only because an IN-list
 * has a practical ceiling.
 *
 * The title is never frozen anywhere. It records what the message is called
 * today, even on a row describing a document published months ago: a snapshotted
 * title would go stale against every other screen in the system with nothing
 * saying it had.
 */
@ApplicationScoped
public class MessageNaming {

    /** How many uids go into one lookup, matching the resolver's own bound. */
    private static final int LOOKUP_CHUNK = 1000;

    @Inject
    EntityManager em;

    /**
     * The two display names of one message.
     *
     * Both may be null and they are null for different reasons -- a message
     * awaiting its number has no short id, a message with no descriptions has no
     * title -- which is why they travel as separate fields rather than as one
     * "label" the server picked. The client decides which to show; only the
     * client knows whether it has room for both.
     */
    public record Name(String messageId, String title) {

        /** The empty name, for a uid nothing was found for. */
        public static final Name NONE = new Name(null, null);
    }

    /**
     * Every named message in the list, in one query.
     *
     * A uid with no message behind it is absent from the map rather than mapped
     * to an empty name: "this message is gone" is a fact a caller may need to
     * state, and a blank name renders identically to a message that merely has no
     * title.
     *
     * @param lang the language to prefer; blank or unknown falls through to the
     *             first description carrying a title
     */
    public Map<String, Name> namesOf(Collection<String> uids, String lang) {
        Map<String, Name> out = new LinkedHashMap<>();
        if (uids == null || uids.isEmpty()) {
            return out;
        }

        boolean wanted = lang != null && !lang.isBlank();
        // Which uids already have their title in the requested language, so a
        // later description in another one cannot overwrite it.
        Set<String> exact = new LinkedHashSet<>();

        List<String> all = new ArrayList<>(new LinkedHashSet<>(uids));
        for (int from = 0; from < all.size(); from += LOOKUP_CHUNK) {
            List<String> chunk = all.subList(from, Math.min(from + LOOKUP_CHUNK, all.size()));
            // LEFT JOIN, so a message with no descriptions still answers with its
            // short id. An inner join drops it entirely, and the row it names then
            // shows neither a number nor a title -- which reads as a message that
            // does not exist.
            //
            // Ordered by description id so "the first available" is the same
            // description on every call rather than whatever the database
            // happened to return first.
            for (Object[] row : em.createQuery(
                            "SELECT m.uid, m.shortId, d.lang, d.title FROM Message m "
                                    + "LEFT JOIN m.descs d WHERE m.uid IN (:uids) ORDER BY d.id",
                            Object[].class)
                    .setParameter("uids", chunk)
                    .getResultList()) {
                String uid = (String) row[0];
                String shortId = blankToNull((String) row[1]);
                String descLang = (String) row[2];
                String title = blankToNull((String) row[3]);

                Name known = out.get(uid);
                if (known == null) {
                    out.put(uid, new Name(shortId, title));
                    if (title != null && wanted && lang.equalsIgnoreCase(descLang)) {
                        exact.add(uid);
                    }
                    continue;
                }
                if (title == null || exact.contains(uid)) {
                    continue;
                }
                if (wanted && lang.equalsIgnoreCase(descLang)) {
                    out.put(uid, new Name(known.messageId(), title));
                    exact.add(uid);
                } else if (known.title() == null) {
                    out.put(uid, new Name(known.messageId(), title));
                }
            }
        }
        return out;
    }

    /** The name of one uid, never null -- {@link Name#NONE} where nothing was found. */
    public Name nameOf(String uid, String lang) {
        return namesOf(uid == null ? List.of() : List.of(uid), lang)
                .getOrDefault(uid, Name.NONE);
    }

    /**
     * The same list, as rows a screen can render, in the order it was given.
     *
     * For a list that carries nothing about each message but its identity -- the
     * probe's sample of what a criteria document selects. The order is the
     * caller's, because a sample reordered by a display lookup is a different
     * sample from the one whose count sits beside it.
     *
     * A uid nothing was found for still produces a row, with both names null: it
     * was part of what the caller counted, and dropping it here would show a
     * sample shorter than the number printed above it.
     */
    public List<NamedMessageVo> namedList(Collection<String> uids, String lang) {
        if (uids == null || uids.isEmpty()) {
            return List.of();
        }
        Map<String, Name> names = namesOf(uids, lang);
        List<NamedMessageVo> out = new ArrayList<>(uids.size());
        for (String uid : uids) {
            Name name = names.getOrDefault(uid, Name.NONE);
            out.add(new NamedMessageVo(uid, name.messageId(), name.title()));
        }
        return out;
    }

    /**
     * Just the titles, for the lists that already hold their own short ids.
     *
     * A frozen member row prints the short id the message had AT FREEZE, which is
     * a different fact from the one it has today and deliberately not read from
     * here.
     */
    public Map<String, String> titlesOf(Collection<String> uids, String lang) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Name> e : namesOf(uids, lang).entrySet()) {
            if (e.getValue().title() != null) {
                out.put(e.getKey(), e.getValue().title());
            }
        }
        return out;
    }

    /** Blank is absent: an empty name reads as a message with no subject. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
