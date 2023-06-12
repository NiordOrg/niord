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

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Transactional;
import java.util.List;

/**
 * Persists the charts to the database
 */
@Dependent
@Named("batchChartImportWriter")
public class BatchChartImportWriter extends AbstractItemHandler {

    @Inject
    ChartService chartService;

    /** {@inheritDoc} **/
    @Override
    @Transactional
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Chart chart = (Chart) i;
            chartService.saveEntity(chart);
        }
        getLog().info(String.format("Persisted %d charts in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
