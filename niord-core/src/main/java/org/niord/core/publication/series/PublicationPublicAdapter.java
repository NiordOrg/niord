package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.service.BaseService;
import org.niord.model.DataFilter;
import org.niord.model.publication.PublicationVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The public face of publications, during and after the transition.
 *
 * THE MAPPING IS THE POINT, and it is not the one the field names suggest.
 *
 *   publishDateFrom = issue.publicFrom  (this issue's stamped cut-off)
 *   publishDateTo   = issue.publicTo    (the next issue's stamp, minus 1 ms)
 *
 * NOT intervalFrom and intervalTo. The public window is offset from the content
 * interval by one whole period: an issue covering week 33 becomes publicly
 * current when week 33 CLOSES, and stays current until week 34 closes. Map the
 * interval onto the window instead -- the only mapping the names invite -- and
 * every issue carries the previous period's window, so under the default
 * from = to = now the newest issue is never current and the site shows last
 * week's EfS from the moment of cutover.
 *
 * The minus one millisecond is required rather than tidy: the legacy overlap
 * helper is closed at both ends, so an exactly-equal boundary makes two issues
 * publicly current for one instant.
 *
 * Three things deliberately NOT in the predicate:
 *
 *   the SERIES lifecycle status -- a dormant or retired series must keep serving
 *   its published issues, or every citation into that back catalogue goes dark;
 *
 *   the series DOMAIN -- this endpoint has never been domain-scoped, and the
 *   seventeen domain-free rows are global rather than orphaned;
 *
 *   PAGING -- the union has to merge before anything is truncated, or the two
 *   halves each truncate independently and the merge is of two wrong lists.
 */
@ApplicationScoped
public class PublicationPublicAdapter extends BaseService {

    private static final Logger log = LoggerFactory.getLogger(PublicationPublicAdapter.class);

    /** One row as the public API emits it. */
    public record PublicPublication(
            String publicationId,
            String seriesId,
            Date publishDateFrom,
            Date publishDateTo,
            Integer categoryPriority,
            String source) {
    }

    /**
     * The public list.
     *
     * @param from window start, or null for "now"
     * @param to   window end, or null for "now"
     */
    @Transactional
    public List<PublicPublication> list(Date from, Date to) {
        return ordered(newHalf(from, to), legacyHalf(from, to));
    }

    /**
     * The same list, as the value objects the endpoint emits.
     *
     * Built from the SAME two halves and the SAME order as list(), not from a
     * second pair of queries. Two implementations of one union is two chances
     * for the public site and everything that checks it to disagree.
     */
    @Transactional
    public List<PublicationVo> listVo(Date from, Date to, String lang) {
        List<PublicationIssue> issues = newHalf(from, to);
        List<Publication> legacy = legacyHalf(from, to);

        // putIfAbsent, not put. dedupeThenSort keeps the FIRST row for a duplicate
        // id; a map that kept the last would render row B under row A's window and
        // sort position, which is worse than either row on its own.
        Map<String, PublicationIssue> issuesById = new LinkedHashMap<>();
        issues.forEach(i -> issuesById.putIfAbsent(i.getPublicId(), i));
        Map<String, Publication> legacyById = new LinkedHashMap<>();
        legacy.forEach(l -> legacyById.putIfAbsent(l.getPublicationId(), l));

        DataFilter filter = DataFilter.get().lang(lang);
        List<PublicationVo> out = new ArrayList<>();

        for (PublicPublication row : ordered(issues, legacy)) {
            if ("NEW".equals(row.source())) {
                out.add(IssuePublicationMapping.toPublicationVo(issuesById.get(row.publicationId()), lang));
            } else {
                out.add(legacyById.get(row.publicationId()).toVo(PublicationVo.class, filter));
            }
        }
        return out;
    }

    /**
     * Issues of series that have cut over.
     *
     * The eligibility predicate: PUBLISHED only -- a RETIRED ISSUE disappears
     * from the listing, which is what retiring an issue means -- and the window
     * overlaps the requested one. The SERIES status is not consulted.
     */
    private List<PublicationIssue> newHalf(Date from, Date to) {
        // One predicate per NON-NULL bound. A null from or to means no bound on
        // that side; it does not mean now. See legacyHalf for why that matters.
        StringBuilder jpql = new StringBuilder(
                "SELECT i FROM PublicationIssue i JOIN i.series s JOIN s.category c "
                        + "WHERE s.publicAuthority = :authority "
                        // The category publish flag applies to BOTH halves.
                        // legacyHalf has always had it; without it here a cut-over
                        // series in a non-publishing category puts its issues on
                        // the public site the moment it is flipped, while the
                        // legacy rows it replaced were correctly hidden.
                        + "AND c.publish = TRUE "
                        + "AND i.status = :published "
                        + "AND i.publicFrom IS NOT NULL");
        if (to != null) {
            jpql.append(" AND i.publicFrom <= :to");
        }
        if (from != null) {
            jpql.append(" AND (i.publicTo IS NULL OR i.publicTo >= :from)");
        }

        TypedQuery<PublicationIssue> q = em.createQuery(jpql.toString(), PublicationIssue.class)
                .setParameter("authority", PublicAuthority.NEW)
                .setParameter("published", IssueStatus.PUBLISHED);
        if (to != null) {
            q.setParameter("to", to);
        }
        if (from != null) {
            q.setParameter("from", from);
        }
        return q.getResultList();
    }

    /**
     * Legacy rows, minus anything a cut-over series has already taken over.
     *
     * The exclusion is by legacyPublicationId rather than by series, because a
     * series cuts over as a whole but its legacy rows are individual.
     */
    private List<Publication> legacyHalf(Date from, Date to) {
        // status, mainType and the category publish flag are the filter the public
        // list has always applied. Omitting them would put DRAFT publications and
        // internal categories on the public site the moment the adapter took over
        // the endpoint -- a regression wearing the shape of a new feature.
        //
        // TWO null semantics, and only one of them is about the column:
        //
        //   a null COLUMN is an open-ended window and still matches. A publication
        //   with no publishDateFrom is on the public list today.
        //
        //   a null PARAMETER is no bound on that side at all. Substituting "now"
        //   for the missing side reads like a harmless default and is not one:
        //   ?from=<t> alone would acquire an upper bound of now and hide every
        //   publication whose window starts later.
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Publication p JOIN p.category c "
                        + "WHERE p.status = :active "
                        + "AND p.mainType = :mainType "
                        + "AND c.publish = TRUE");
        if (to != null) {
            jpql.append(" AND (p.publishDateFrom IS NULL OR p.publishDateFrom <= :to)");
        }
        if (from != null) {
            jpql.append(" AND (p.publishDateTo IS NULL OR p.publishDateTo >= :from)");
        }

        // The exclusion is gated on PUBLISHED because newHalf only emits PUBLISHED
        // issues. Ungated, an imported issue sitting at OPEN between import and
        // first publish removes its legacy row from the list without putting
        // anything in its place, and the publication vanishes from the public site.
        jpql.append(" AND p.publicationId NOT IN ("
                + "  SELECT i.legacyPublicationId FROM PublicationIssue i "
                + "  WHERE i.legacyPublicationId IS NOT NULL "
                // HAVING BEEN PUBLISHED, not being published now.
                //
                // Gating on the current status gets one case wrong in each
                // direction. An imported issue at OPEN has NOT taken over: exclude
                // on that and the legacy row vanishes with nothing in its place.
                // An issue published and then RETIRED HAS taken over, permanently:
                // gate on status = PUBLISHED and the exclusion lapses on retire,
                // and the ACTIVE legacy row silently returns to the public list
                // carrying its original uncapped window -- so retiring an issue
                // resurrects the document it replaced, with no duplicate id and no
                // ERROR to notice it. publishedAt settles both: set at publish,
                // never cleared, including by retire.
                + "  AND i.publishedAt IS NOT NULL "
                + "  AND i.series.publicAuthority = :authority)");

        TypedQuery<Publication> q = em.createQuery(jpql.toString(), Publication.class)
                .setParameter("active", PublicationStatus.ACTIVE)
                .setParameter("mainType", PublicationMainType.PUBLICATION)
                .setParameter("authority", PublicAuthority.NEW);
        if (to != null) {
            q.setParameter("to", to);
        }
        if (from != null) {
            q.setParameter("from", from);
        }
        return q.getResultList();
    }

    /** Both halves as rows, deduplicated and put in the emitted order. */
    private List<PublicPublication> ordered(List<PublicationIssue> issues, List<Publication> legacy) {
        List<PublicPublication> merged = new ArrayList<>();

        for (PublicationIssue i : issues) {
            // publicFrom and publicTo, never intervalFrom and intervalTo.
            merged.add(new PublicPublication(i.getPublicId(),
                    i.getSeries() == null ? null : i.getSeries().getSeriesId(),
                    i.getPublicFrom(), i.getPublicTo(),
                    priorityOf(i.getSeries() == null ? null : i.getSeries().getCategory()),
                    "NEW"));
        }
        for (Publication p : legacy) {
            merged.add(new PublicPublication(p.getPublicationId(), null,
                    p.getPublishDateFrom(), p.getPublishDateTo(),
                    priorityOf(p.getCategory()), "LEGACY"));
        }
        return dedupeThenSort(merged);
    }

    private static Integer priorityOf(PublicationCategory category) {
        return category == null ? Integer.MAX_VALUE : category.getPriority();
    }

    /**
     * Dedup, then sort -- in that order, and after both halves are in hand.
     *
     * An imported issue REUSES the legacy id as its publicId, so a naive union
     * emits the same id twice and the site shows two "EfS uge 33" rows. On a
     * duplicate this logs an ERROR naming both sources and drops the later one:
     * never serve duplicates, and never silently pick one without saying so.
     */
    private List<PublicPublication> dedupeThenSort(List<PublicPublication> merged) {
        Map<String, PublicPublication> byId = new LinkedHashMap<>();
        for (PublicPublication p : merged) {
            PublicPublication existing = byId.get(p.publicationId());
            if (existing != null) {
                log.error("duplicate publicationId {} from both {} and {}; dropping the {} row. "
                                + "An imported issue reuses the legacy id, so this means the exclusion "
                                + "subquery missed it.",
                        p.publicationId(), existing.source(), p.source(), p.source());
                continue;
            }
            byId.put(p.publicationId(), p);
        }

        List<PublicPublication> out = new ArrayList<>(byId.values());

        // CATEGORY PRIORITY FIRST, then the window, then the id.
        //
        // The priority is not decoration: the legacy public list has always
        // ordered by category priority ascending and publish date descending, and
        // that is the order the sections of the public page are built from.
        // Sorting by date alone -- which is what this did while the adapter was
        // not yet wired to the endpoint -- reshuffles every section on the page.
        //
        // The id tiebreak is the addition. Without it the EfS and P&T
        // twins, which share a window to the millisecond, come back in whatever
        // order the database chose and two calls disagree.
        out.sort(Comparator
                .comparing(PublicPublication::categoryPriority,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PublicPublication::publishDateFrom,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublicPublication::publicationId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    /**
     * Resolves a citation.
     *
     * Order matters: a new issue first, then a legacy row. An imported issue
     * carries the legacy id as its own publicId, so checking legacy first would
     * keep serving the old row forever after cutover.
     */
    @Transactional
    public PublicPublication resolve(String publicationId) {
        List<Object[]> asIssue = em.createQuery(
                        "SELECT i.publicId, s.seriesId, i.publicFrom, i.publicTo "
                                + "FROM PublicationIssue i JOIN i.series s WHERE i.publicId = :id",
                        Object[].class)
                .setParameter("id", publicationId).setMaxResults(1).getResultList();

        if (!asIssue.isEmpty()) {
            Object[] r = asIssue.get(0);
            return new PublicPublication((String) r[0], (String) r[1], (Date) r[2], (Date) r[3], null, "NEW");
        }

        List<Object[]> asLegacy = em.createQuery(
                        "SELECT p.publicationId, p.publishDateFrom, p.publishDateTo "
                                + "FROM Publication p WHERE p.publicationId = :id",
                        Object[].class)
                .setParameter("id", publicationId).setMaxResults(1).getResultList();

        if (!asLegacy.isEmpty()) {
            Object[] r = asLegacy.get(0);
            return new PublicPublication((String) r[0], null, (Date) r[1], (Date) r[2], null, "LEGACY");
        }
        return null;
    }
}
