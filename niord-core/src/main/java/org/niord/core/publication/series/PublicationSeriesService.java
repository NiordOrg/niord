package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.niord.core.service.BaseService;

import java.util.List;

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

    public PublicationSeries update(PublicationSeries series) {
        removeBlankDescs(series);
        return em.merge(series);
    }

    private void removeBlankDescs(PublicationSeries series) {
        if (series.getDescs() != null) {
            series.getDescs().removeIf(d -> !d.descDefined());
        }
    }
}
