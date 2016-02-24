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
