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
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Properties;

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
public class JobStartBatchlet extends AbstractBatchlet {

    @Inject
    Logger log;

    @Inject
    JobContext jobContext;

    @Inject
    BatchService batchService;

    /**
     * Fetch the BatchData from the job operator properties, if they exist
     * @param executionId the execution id
     * @return the BatchData from the job operator properties
     */
    private BatchData getBatchData(long executionId) {
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties properties = jobOperator.getParameters(executionId);
        return  properties != null
                ? (BatchData) properties.get(BatchService.BATCH_JOB_ENTITY)
                : null;
    }


    /** {@inheritDoc} **/
    @Override
    public String process() throws Exception {

        BatchData job = getBatchData(jobContext.getExecutionId());

        // When a job is started, the instanceId will be null and the job must be persisted
        // Restarts will have a well-defined instanceId and be persisted already
        if (job != null) {
            job.setInstanceId(jobContext.getInstanceId());
            job = batchService.saveBatchJob(job);
            log.info("JobStartBatchlet started for " + job.getJobName() + " and instanceId " + job.getInstanceId());
        } else {
            job = batchService.findByInstanceId(jobContext.getInstanceId());
            log.info("JobStartBatchlet re-started for " + job.getJobName() + " and instanceId " + job.getInstanceId());
        }

        // Update the properties
        return "COMPLETED";
    }
}
