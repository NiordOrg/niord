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

package org.niord.core.fm.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.domain.DomainService;
import org.niord.core.fm.FmReport;
import org.niord.core.fm.FmReportService;
import org.niord.core.fm.vo.FmReportVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters reports that need to be a added or updated
 */
@Named
public class BatchReportImportProcessor extends AbstractItemHandler {

    @Inject
    FmReportService reportService;

    @Inject
    DomainService domainService;


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        FmReportVo reportVo = (FmReportVo) item;

        FmReport report = new FmReport(reportVo);
        // Replace the domain with the persisted once
        report.setDomains(domainService.persistedDomains(report.getDomains()));

        // Look up any existing report
        FmReport orig = reportService.findByReportId(report.getReportId());

        if (orig == null) {
            // Persist new report
            getLog().info("Persisting new report " + report);
            return report;

        } else  {
            // The batch job only imports new reports, it does not change existing once.
            getLog().warning("Report " + orig.getReportId() + " already exists. Skipping import of report.");
            return null;
        }
    }
}
