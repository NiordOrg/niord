package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.niord.core.service.BaseService;
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
        Date windowFrom = from == null ? new Date() : from;
        Date windowTo = to == null ? new Date() : to;

        List<PublicPublication> merged = new ArrayList<>();
        merged.addAll(newHalf(windowFrom, windowTo));
        merged.addAll(legacyHalf(windowFrom, windowTo));

        return dedupeThenSort(merged);
    }

    /**
     * Issues of series that have cut over.
     *
     * The eligibility predicate: PUBLISHED only -- a RETIRED ISSUE disappears
     * from the listing, which is what retiring an issue means -- and the window
     * overlaps the requested one. The SERIES status is not consulted.
     */
    private List<PublicPublication> newHalf(Date from, Date to) {
        List<Object[]> rows = em.createQuery(
                        "SELECT i.publicId, s.seriesId, i.publicFrom, i.publicTo "
                                + "FROM PublicationIssue i JOIN i.series s "
                                + "WHERE s.publicAuthority = :authority "
                                + "AND i.status = :published "
                                + "AND i.publicFrom IS NOT NULL "
                                + "AND i.publicFrom <= :to "
                                + "AND (i.publicTo IS NULL OR i.publicTo >= :from)",
                        Object[].class)
                .setParameter("authority", PublicAuthority.NEW)
                .setParameter("published", IssueStatus.PUBLISHED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        List<PublicPublication> out = new ArrayList<>();
        for (Object[] r : rows) {
            // publicFrom and publicTo, never intervalFrom and intervalTo.
            out.add(new PublicPublication((String) r[0], (String) r[1], (Date) r[2], (Date) r[3], "NEW"));
        }
        return out;
    }

    /**
     * Legacy rows, minus anything a cut-over series has already taken over.
     *
     * The exclusion is by legacyPublicationId rather than by series, because a
     * series cuts over as a whole but its legacy rows are individual.
     */
    private List<PublicPublication> legacyHalf(Date from, Date to) {
        List<Object[]> rows = em.createQuery(
                        "SELECT p.publicationId, p.publishDateFrom, p.publishDateTo "
                                + "FROM Publication p "
                                + "WHERE p.publishDateFrom IS NOT NULL "
                                + "AND p.publishDateFrom <= :to "
                                + "AND (p.publishDateTo IS NULL OR p.publishDateTo >= :from) "
                                + "AND p.publicationId NOT IN ("
                                + "  SELECT i.legacyPublicationId FROM PublicationIssue i "
                                + "  WHERE i.legacyPublicationId IS NOT NULL "
                                + "  AND i.series.publicAuthority = :authority)",
                        Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("authority", PublicAuthority.NEW)
                .getResultList();

        List<PublicPublication> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new PublicPublication((String) r[0], null, (Date) r[1], (Date) r[2], "LEGACY"));
        }
        return out;
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

        // A TOTAL order. Without the id tiebreak the EfS and P&T twins -- which
        // share a window to the millisecond -- come back in whatever order the
        // database chose, and two calls disagree.
        out.sort(Comparator
                .comparing(PublicPublication::publishDateFrom,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublicPublication::publicationId));
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
            return new PublicPublication((String) r[0], (String) r[1], (Date) r[2], (Date) r[3], "NEW");
        }

        List<Object[]> asLegacy = em.createQuery(
                        "SELECT p.publicationId, p.publishDateFrom, p.publishDateTo "
                                + "FROM Publication p WHERE p.publicationId = :id",
                        Object[].class)
                .setParameter("id", publicationId).setMaxResults(1).getResultList();

        if (!asLegacy.isEmpty()) {
            Object[] r = asLegacy.get(0);
            return new PublicPublication((String) r[0], null, (Date) r[1], (Date) r[2], "LEGACY");
        }
        return null;
    }
}
