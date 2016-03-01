package org.niord.core.chart.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the AtoNs to the database
 */
@Named
public class BatchChartImportWriter extends AbstractItemHandler {

    @Inject
    ChartService chartService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Chart chart = (Chart) i;
            chartService.saveEntity(chart);
        }
        getLog().info(String.format("Persisted %d charts in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
