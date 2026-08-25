package org.niord.core.publication.series.batch;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesService;

import java.util.List;

/** Persists the imported series. New ones are created, existing ones updated in place. */
@Dependent
@Named("batchPublicationSeriesImportWriter")
public class BatchPublicationSeriesImportWriter extends AbstractItemHandler {

    @Inject
    PublicationSeriesService seriesService;

    @Transactional
    @Override
    public void writeItems(List<Object> items) {
        long t0 = System.currentTimeMillis();
        for (Object item : items) {
            PublicationSeries series = (PublicationSeries) item;
            if (series.isNew()) {
                seriesService.create(series);
            } else {
                seriesService.update(series);
            }
        }
        getLog().info(String.format("Persisted %d publication series in %d ms",
                items.size(), System.currentTimeMillis() - t0));
    }
}
