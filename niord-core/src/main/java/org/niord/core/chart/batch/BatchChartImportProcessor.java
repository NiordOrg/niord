/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.core.chart.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.core.chart.vo.SystemChartVo;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Filters charts that need to be a added or updated
 */
@Dependent
@Named("batchChartImportProcessor")
public class BatchChartImportProcessor extends AbstractItemHandler {

    @Inject
    ChartService chartService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        SystemChartVo chartVo = (SystemChartVo) item;
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
