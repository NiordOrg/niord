package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.niord.core.publication.PublicationSearchParams;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.service.BaseService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        StringBuilder jpql = new StringBuilder(
                "SELECT i FROM PublicationIssue i JOIN i.series s WHERE 1 = 1");

        if (params.getMessagePublication() != null) {
            jpql.append(" AND s.messagePublication = :messagePublication");
        }
        if (params.getDomain() != null && !params.getDomain().isBlank()) {
            jpql.append(" AND s.domain.domainId = :domain");
        }
        if (params.getCategory() != null && !params.getCategory().isBlank()) {
            jpql.append(" AND s.category.categoryId = :category");
        }
        if (statusPredicate(params) != null) {
            jpql.append(statusPredicate(params));
        }

        // Newest first, matching the order every issue list in the product uses.
        // An interval with no end sorts last rather than being dropped.
        jpql.append(" ORDER BY i.intervalFrom DESC");

        var query = em.createQuery(jpql.toString(), PublicationIssue.class);
        if (params.getMessagePublication() != null) {
            query.setParameter("messagePublication", params.getMessagePublication());
        }
        if (params.getDomain() != null && !params.getDomain().isBlank()) {
            query.setParameter("domain", params.getDomain());
        }
        if (params.getCategory() != null && !params.getCategory().isBlank()) {
            query.setParameter("category", params.getCategory());
        }

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
                ? " AND i.status = org.niord.core.publication.series.IssueStatus.PUBLISHED"
                : " AND i.status <> org.niord.core.publication.series.IssueStatus.PUBLISHED";
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
