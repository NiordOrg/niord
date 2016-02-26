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
package org.niord.core.batch;

import org.slf4j.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Called when a batch job is started.
 * Updates and persists the BatchEntity
 *
 * Configure using:
 * <pre>
 *   &lt;batchlet ref="jobStartBatchlet" /&gt;
 * </pre>
 */
@Named
public class JobStartBatchlet extends AbstractBatchlet implements IBatchable {

    @Inject
    Logger log;

    @Inject
    JobContext jobContext;

    @Inject
    BatchService batchService;

    /** {@inheritDoc} **/
    @Override
    public String process() throws Exception {
        log.info("JobStartBatchlet started ");

        BatchEntity job = getBatchEntity(jobContext.getExecutionId());
        job.setExecutionId(jobContext.getExecutionId());
        job.setStatus(jobContext.getBatchStatus());
        job = batchService.saveBatchJob(job);

        // Update the properties
        getSharedProperties(jobContext.getExecutionId()).put(BATCH_JOB_ENTITY, job);
        return "COMPLETED";
    }
}
