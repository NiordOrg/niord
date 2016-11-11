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

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.fm.vo.FmReportVo;
import org.niord.core.util.JsonUtils;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads reports from a reports.json file. The batch job only imports new reports, it does not change existing once.
 * <p>
 * Please note, the actual report-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the FmReportVo class. Example:
 * <pre>
 * [
 *   {
 *       "reportId": "nm-report",
 *       "name": "NM report",
 *       "templatePath": "/templates/messages/nm-report-pdf.ftl",
 *       "domains": [
 *          { "domainId": "niord-client-nm" }
 *       ],
 *       "properties": {}
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Named
public class BatchReportImportReader extends AbstractItemHandler {

    List<FmReportVo> reports;
    int reportNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the reports from the file
        reports = JsonUtils.readJson(
                new TypeReference<List<FmReportVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            reportNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + reports.size() + " reports from index " + reportNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (reportNo < reports.size()) {
            getLog().info("Reading report no " + reportNo);
            return reports.get(reportNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return reportNo;
    }
}
