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

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.util.JsonUtils;
import org.niord.model.message.ChartVo;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads charts from a charts.json file.
 * <p>
 * Please note, the actual chart-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the ChartVo class. Example:
 * <pre>
 * [
 *   {
 *     "chartNumber": "198",
 *     "name": "Østersøen, Fakse Bugt og Hjelm Bugt",
 *     "geometry": {
 *       "type": "Polygon",
 *       "coordinates": [
 *         [
 *           [ 12.0, 54.74166667 ],
 *           [ 12.76666667, 54.74166667 ],
 *           [ 12.76666667, 55.38333333 ],
 *           [ 12.0, 55.38333333 ],
 *           [ 12.0, 54.74166667 ]
 *         ]
 *       ]
 *     },
 *     "horizontalDatum": "WGS84",
 *     "scale": 75000
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Named
public class BatchChartImportReader extends AbstractItemHandler {

    List<ChartVo> charts;
    int chartNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the charts from the file
        charts = JsonUtils.readJson(
                new TypeReference<List<ChartVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            chartNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + charts.size() + " charts from index " + chartNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (chartNo < charts.size()) {
            getLog().info("Reading chart no " + chartNo);
            return charts.get(chartNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return chartNo;
    }
}
