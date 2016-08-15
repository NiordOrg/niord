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

import javax.batch.api.listener.AbstractJobListener;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;

/**
 * A listener used for setting up and removing batch job log files.
 * The listener should be added to all Niord batch jobs.
 * <p>
 * Configure using:
 * <pre>
 *  &lt;listeners&gt;
 *      &lt;listener ref="batchJobListener"/&gt;
 *  &lt;/listeners&gt;
 * </pre>
 */
@Named
public class BatchJobListener extends AbstractJobListener {

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


    /** {@inheritDoc} */
    @Override
    public void beforeJob() throws Exception {
        BatchData job = getBatchData(jobContext.getExecutionId());

        // Create a map holding the logs
        Map<String, java.util.logging.Logger> logs = new HashMap<>();
        jobContext.setTransientUserData(logs);


        // When a job is started, the instanceId will be null and the job must be persisted
        // Restarts will have a well-defined instanceId and be persisted already
        if (job != null) {
            job.setInstanceId(jobContext.getInstanceId());
            job = batchService.saveBatchJob(job);
            log.info("Batch job started for " + job.getJobName() + " and instanceId " + job.getInstanceId());
        } else {
            job = batchService.findByInstanceId(jobContext.getInstanceId());
            log.info("Batch job re-started for " + job.getJobName() + " and instanceId " + job.getInstanceId());
        }
    }


    /** {@inheritDoc} */
    @Override
    public void afterJob() throws Exception {

        // Close all the batch logs - otherwise the will keep a lock on the log files.
        @SuppressWarnings("unchecked")
        Map<String, java.util.logging.Logger> logs = (Map<String, java.util.logging.Logger>)jobContext.getTransientUserData();
        if (logs != null && !logs.isEmpty()) {
            for (Map.Entry<String, java.util.logging.Logger> batchLog : logs.entrySet()) {
                try {
                    for (Handler handler : batchLog.getValue().getHandlers()) {
                        handler.close();
                    }

                } catch (Exception ignored) {
                }
                log.info("Closed log for batch item handler " + batchLog.getKey());
            }
        }
    }

}
