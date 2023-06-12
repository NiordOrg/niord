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

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.chart.vo.SystemChartVo;
import org.niord.core.util.JsonUtils;

import javax.enterprise.context.Dependent;
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
@Dependent
@Named("batchChartImportReader")
public class BatchChartImportReader extends AbstractItemHandler {

    List<SystemChartVo> charts;
    int chartNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the charts from the file
        charts = JsonUtils.readJson(
                new TypeReference<List<SystemChartVo>>(){},
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
