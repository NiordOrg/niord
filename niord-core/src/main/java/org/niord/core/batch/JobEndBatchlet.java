package org.niord.core.batch;

import org.slf4j.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Called when a batch job is started.
 *
 * Configure using:
 * <pre>
 *   &lt;batchlet ref="jobEndBatchlet" /&gt;
 * </pre>
 */
@Named
public class JobEndBatchlet extends AbstractBatchlet implements IBatchable {

    @Inject
    private Logger log;

    @Inject
    private JobContext jobContext;

    @Inject
    BatchService batchService;

    /** {@inheritDoc} **/
    @Override
    public String process() throws Exception {
        log.info("JobEndBatchlet started ");

        // Update the job entity with the execution id and batch status
        BatchEntity job = getBatchEntity(jobContext.getExecutionId());
        job.setStatus(BatchStatus.COMPLETED);
        batchService.saveBatchJob(job);

        return "COMPLETED";
    }
}
