package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.FlushModeType;
import jakarta.transaction.Transactional;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.service.BaseService;

import java.util.List;
import java.util.Objects;

/**
 * Persistence for publication series.
 *
 * Deliberately thin. Everything expressible as a pure function -- membership,
 * criteria validation, criteria resolution -- lives outside any service and is
 * tested without a database. What is left here is the part that genuinely needs
 * one.
 */
@ApplicationScoped
@Transactional
@SuppressWarnings("unused")
public class PublicationSeriesService extends BaseService {

    /** Looks a series up by its human-authored, stable id. */
    public PublicationSeries findBySeriesId(String seriesId) {
        return em.createQuery("SELECT s FROM PublicationSeries s WHERE s.seriesId = :seriesId",
                        PublicationSeries.class)
                .setParameter("seriesId", seriesId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public List<PublicationSeries> findAll() {
        return em.createQuery("SELECT s FROM PublicationSeries s ORDER BY s.seriesId", PublicationSeries.class)
                .getResultList();
    }

    public List<PublicationSeries> findByStatus(SeriesStatus status) {
        return em.createQuery("SELECT s FROM PublicationSeries s WHERE s.status = :status ORDER BY s.seriesId",
                        PublicationSeries.class)
                .setParameter("status", status)
                .getResultList();
    }

    /**
     * Persists a series, dropping desc rows that carry no defined content.
     *
     * The blank-desc filter is not tidiness. A desc row whose name is blank but
     * which carries a format string round-trips to nothing under the legacy
     * descDefined() rule, and the citation text on it is silently lost -- so such
     * a row must never reach the database in the first place.
     */
    public PublicationSeries create(PublicationSeries series) {
        removeBlankDescs(series);
        em.persist(series);
        return series;
    }

    /**
     * Whether any issue of this series has ever been published.
     *
     * S-18 turns on it: a citation lives in whichever message field the series was
     * configured to use, so once an issue is out, moving the channel makes every
     * existing citation unfindable. RETIRED counts -- it was published and the
     * citations it wrote are still in the messages.
     */
    public boolean hasPublishedIssue(PublicationSeries series) {
        if (series == null || series.getId() == null) {
            return false;
        }
        Long n = em.createQuery(
                        "SELECT COUNT(i) FROM PublicationIssue i WHERE i.series = :s "
                                + "AND i.status <> org.niord.core.publication.series.IssueStatus.OPEN",
                        Long.class)
                .setParameter("s", series)
                .getSingleResult();
        return n != null && n > 0;
    }

    public PublicationSeries update(PublicationSeries series) {
        removeBlankDescs(series);
        checkMessagePublicationImmutable(series);
        return em.merge(series);
    }

    /**
     * messagePublication may not change once a published issue exists.
     *
     * The message-to-publication relation lives ONLY as publication="<id>" inside
     * stored message HTML, and messagePublication decides which field that HTML
     * is written into: "publication" or "internalPublication". Flip it after
     * citations exist and every one of them becomes unfindable -- it is sitting
     * in the other field -- while re-applying the citation appends a second copy
     * rather than finding the first. There is no endpoint that removes a
     * citation, so nothing can undo it.
     *
     * Enforced here rather than in a resource because it is a property of the
     * series, and a rule that lives in one endpoint is a rule the next endpoint
     * will not have.
     */
    private void checkMessagePublicationImmutable(PublicationSeries series) {
        if (series.getId() == null) {
            return;
        }

        // COMMIT flush mode on purpose: the incoming series is usually the
        // MANAGED instance with the new value already on it, so an auto-flush
        // would write the change and then compare it with itself.
        List<MessagePublication> stored = em.createQuery(
                        "SELECT s.messagePublication FROM PublicationSeries s WHERE s.id = :id",
                        MessagePublication.class)
                .setParameter("id", series.getId())
                .setFlushMode(FlushModeType.COMMIT)
                .getResultList();

        if (stored.isEmpty() || Objects.equals(stored.get(0), series.getMessagePublication())) {
            return;
        }

        Long published = em.createQuery(
                        "SELECT COUNT(i) FROM PublicationIssue i "
                                + "WHERE i.series.id = :id AND i.status <> :open", Long.class)
                .setParameter("id", series.getId())
                .setParameter("open", IssueStatus.OPEN)
                .setFlushMode(FlushModeType.COMMIT)
                .getSingleResult();

        if (published > 0) {
            throw new IssueLifecycleService.TransitionRefusedException("MESSAGE_PUBLICATION_IMMUTABLE",
                    "messagePublication cannot change from " + stored.get(0) + " to "
                            + series.getMessagePublication() + ": " + published + " issue(s) of this "
                            + "series have been released, and any citation into them lives in the "
                            + "field the old value selected. Changing it makes those citations "
                            + "unfindable, and nothing can remove them.");
        }
    }

    private void removeBlankDescs(PublicationSeries series) {
        if (series.getDescs() != null) {
            series.getDescs().removeIf(d -> !d.descDefined());
        }
    }
}
