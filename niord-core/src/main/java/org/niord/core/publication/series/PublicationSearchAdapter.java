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
import jakarta.transaction.Transactional;

import org.niord.core.publication.PublicationSearchParams;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.service.BaseService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Issues, findable by the same search the publication picker already uses.
 *
 * An issue is RESOLVABLE by id today -- PublicationResolver checks the new model
 * before the legacy one, so a citation into an imported id keeps working after
 * cutover. What it is not is FINDABLE: /publications/search reads the legacy
 * table alone, so an issue with no legacy twin never appears in the picker.
 *
 * That asymmetry is the whole reason this exists. An imported issue borrows its
 * legacy row's id, so the picker offers the legacy row and the citation resolves
 * to the issue -- it works by coincidence. An issue created AFTER cutover has no
 * legacy twin, so the picker cannot offer it at all, and the current week's EfS
 * becomes the one publication an editor cannot cite. Verified against the
 * deployed API: a new issue answers on /publications/publication/{id} and returns
 * zero hits from /publications/search.
 *
 * The filters mirror the legacy ones rather than inventing better ones, because
 * both halves of a union have to agree on what a hit is. Status is the projected
 * publication status, not the issue status: PUBLISHED reads as ACTIVE and
 * everything else as DRAFT, which is what IssuePublicationMapping already emits
 * and therefore what a caller filtering on ACTIVE means.
 */
@ApplicationScoped
public class PublicationSearchAdapter extends BaseService {

    /**
     * Issues matching the search, newest interval first.
     *
     * Returns ENTITIES rather than value objects. The caller has to dedupe against
     * the legacy half by id before mapping, and mapping first would mean building
     * value objects that are then thrown away -- for a picker fired on every
     * keystroke that is the wrong order.
     */
    @Transactional
    public List<PublicationIssue> search(PublicationSearchParams params) {
        if (params == null || !wantsPublications(params.getMainType())) {
            // PublicationMainType.PUBLICATION is what an issue is. A search for
            // LINK or anything else is asking for something an issue is not, and
            // an empty list is the honest answer rather than everything.
            return List.of();
        }

        // LEFT JOIN on the owner, and it has to be explicit. A path expression
        // like s.domain.domainId generates an INNER join, which eliminates a
        // series whose owner is missing BEFORE the where-clause is evaluated -- so
        // a predicate written against the path reads correctly and still cannot
        // return that row under any value. The column is NOT NULL where the
        // migration could apply it, and this join is what keeps the query honest
        // where it could not.
        StringBuilder jpql = new StringBuilder(
                "SELECT i FROM PublicationIssue i JOIN i.series s LEFT JOIN s.domain d WHERE 1 = 1");

        // EVERY FILTER CONTRIBUTES ITS CLAUSE AND ITS BINDING IN ONE PLACE.
        //
        // The two used to be separate chains -- append here, bind thirty lines
        // down -- and they had drifted: four clauses against three bindings,
        // because the status filter inlined a fully-qualified enum constant into
        // the query text instead of binding it. A clause without its binding is a
        // request-time failure, not a compile-time one, and the pairing is what
        // makes it impossible rather than merely unlikely.
        Map<String, Object> bindings = new LinkedHashMap<>();

        if (params.getMessagePublication() != null) {
            jpql.append(" AND s.messagePublication = :messagePublication");
            bindings.put("messagePublication", params.getMessagePublication());
        }
        if (params.getType() != null) {
            // The INVERSE of the derivation the payload emits, and it has to be
            // exactly that. The type a row reports comes from its series' content
            // mode, so a type filter is a content-mode filter; narrowing on
            // anything else -- or, as this half did, on nothing at all -- returns
            // rows the caller then reads a different type off.
            //
            // The legacy half applies its stored type column, so leaving this out
            // made ?type= mean two different things in one union: the editor's
            // message-report picker was served every issue in the estate, link and
            // uploaded-file publications included.
            jpql.append(" AND s.contentMode = :contentMode");
            bindings.put("contentMode", ContentMode.ofPublicationType(params.getType()));
        }
        if (params.getDomain() != null && !params.getDomain().isBlank()) {
            // VISIBLE FROM the named domain, by the same fragment the citation
            // picker uses. The two halves of this search are read by the same
            // screens, and a rule stated twice is a rule that drifts: the reason
            // most of the catalogue once vanished from every domain-scoped search
            // was that this clause and the picker's had been written separately.
            jpql.append(" AND ").append(SeriesVisibility.clause("s", "d"));
            SeriesVisibility.bind(bindings, params.getDomain());
        }
        if (params.getCategory() != null && !params.getCategory().isBlank()) {
            jpql.append(" AND s.category.categoryId = :category");
            bindings.put("category", params.getCategory());
        }
        String statusClause = statusPredicate(params);
        if (statusClause != null) {
            jpql.append(statusClause);
            bindings.put("issueStatus", IssueStatus.PUBLISHED);
        }

        // Newest first, matching the order every issue list in the product uses.
        // An interval with no end sorts last rather than being dropped.
        jpql.append(" ORDER BY i.intervalFrom DESC");

        var query = em.createQuery(jpql.toString(), PublicationIssue.class);
        bindings.forEach(query::setParameter);

        List<PublicationIssue> hits = query.getResultList();

        // Title is matched in memory, not in SQL. It has to match against the
        // issue's per-language desc the same way the legacy half matches against
        // its own, and a JOIN over descs would return one row per language.
        String title = params.getTitle();
        if (title != null && !title.isBlank()) {
            String needle = title.trim().toLowerCase(Locale.ROOT);
            List<PublicationIssue> filtered = new ArrayList<>();
            for (PublicationIssue issue : hits) {
                if (matchesTitle(issue, needle)) {
                    filtered.add(issue);
                }
            }
            hits = filtered;
        }
        return hits;
    }

    /**
     * The legacy publication ids the named domain may NOT see, through their twins.
     *
     * THE LEGACY HALF OF THE UNION HAS NO AVAILABILITY OF ITS OWN. Its rows carry
     * the old nullable domain column and nothing else, so on its own it answers
     * "domain is null or domain = X" -- which for a row that has since been
     * imported is the OLD sharing rule, still running beside the new one. An
     * imported issue borrows its legacy row's id, so the two collide by design and
     * the merge drops the legacy row whenever the issue half returned it; when the
     * issue half withheld the twin, the legacy row survived and showed the very
     * publication the new rule had just hidden.
     *
     * So a twinned legacy row FOLLOWS ITS TWIN. This names the ids whose twin is
     * not visible from the caller's domain, and the merge drops them. The opposite
     * direction needs nothing: a legacy row whose twin IS visible is already in the
     * union as the issue, under the same id.
     *
     * A row with NO twin keeps the old rule, applied by the legacy half itself.
     * There is nothing else it could follow -- it was never imported, so no series
     * states who may cite it.
     */
    @Transactional
    public Set<String> legacyIdsHiddenFrom(String domainId) {
        if (domainId == null || domainId.isBlank()) {
            return Set.of();
        }
        var query = em.createQuery(
                "SELECT i.legacyPublicationId FROM PublicationIssue i JOIN i.series s"
                        + " LEFT JOIN s.domain d WHERE i.legacyPublicationId IS NOT NULL"
                        + " AND NOT " + SeriesVisibility.clause("s", "d"), String.class);
        SeriesVisibility.bind(query, domainId);
        return new LinkedHashSet<>(query.getResultList());
    }

    /** An issue is a PUBLICATION; a search for anything else is not asking for one. */
    private static boolean wantsPublications(PublicationMainType mainType) {
        return mainType == null || mainType == PublicationMainType.PUBLICATION;
    }

    /**
     * The status clause, or null when every status is wanted.
     *
     * Translated from the PROJECTED publication status rather than applied to the
     * issue status directly: a caller asking for ACTIVE means what the mapping
     * emits as ACTIVE, which is a PUBLISHED issue.
     *
     * The constant is a bound parameter named issueStatus, not a literal spelled
     * into the query text. A caller adding a clause here has to add its binding
     * at the call site, which is the whole point of the pairing there.
     */
    private static String statusPredicate(PublicationSearchParams params) {
        if (params.getStatuses() == null || params.getStatuses().isEmpty()) {
            return null;
        }
        boolean wantsActive = params.getStatuses().contains(PublicationStatus.ACTIVE);
        boolean wantsOther = params.getStatuses().stream()
                .anyMatch(s -> s != PublicationStatus.ACTIVE);

        if (wantsActive && wantsOther) {
            return null;
        }
        return wantsActive
                ? " AND i.status = :issueStatus"
                : " AND i.status <> :issueStatus";
    }

    /** Case-insensitive substring over every language, as the legacy title filter is. */
    private static boolean matchesTitle(PublicationIssue issue, String needle) {
        if (issue.getDescs() == null) {
            return false;
        }
        for (PublicationIssueDesc desc : issue.getDescs()) {
            String name = desc.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
