/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.chart.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.model.message.ChartVo;

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
