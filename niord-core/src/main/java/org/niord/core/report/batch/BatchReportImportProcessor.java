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
import org.niord.core.report.vo.FmReportVo;

import javax.inject.Named;

/**
 * Converts the report value object into a report entity template
 */
@Named
public class BatchReportImportProcessor extends AbstractItemHandler {

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        FmReportVo reportVo = (FmReportVo) item;

        return new FmReport(reportVo);
    }
}
