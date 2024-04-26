/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.report.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.report.FmReport;
import org.niord.core.report.FmReportService;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

/**
 * Persists the reports to the database
 */
@Dependent
@Named("batchReportImportWriter")
public class BatchReportImportWriter extends AbstractItemHandler {

    @Inject
    FmReportService reportService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            FmReport report = (FmReport) i;

            FmReport orig = reportService.findByReportId(report.getReportId());

            if (orig == null) {
                getLog().info("Persisting new report " + report.getReportId());
                reportService.createReport(report);
            } else {
                getLog().info("Updating existing report " + report.getReportId());
                reportService.updateReport(report);
            }
        }
        getLog().info(String.format("Persisted %d reports in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
