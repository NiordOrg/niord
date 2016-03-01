package org.niord.core.chart.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.model.vo.ChartVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters charts that need to be a added or updated
 */
@Named
public class BatchChartImportProcessor extends AbstractItemHandler {

    @Inject
    ChartService chartService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        ChartVo chartVo = (ChartVo) item;
        Chart chart = new Chart(chartVo);

        // Look
        Chart orig = chartService.findByChartNumber(chart.getChartNumber());

        if (orig == null) {
            // Persist new chart
            getLog().info("Persisting new chart " + chart);
            return chart;

        } else if (orig.hasChanged(chart)) {
            // Update original
            getLog().info("Updating chart " + orig.getId());
            orig.updateChart(chartVo);
            return orig;
        }

        // No change, ignore...
        getLog().info("Ignoring unchanged chart " + orig.getId());
        return null;
    }
}
