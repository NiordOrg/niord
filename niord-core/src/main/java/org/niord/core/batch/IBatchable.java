package org.niord.core.batch;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import java.util.Properties;

/**
 * Common behaviour of batch job entities
 */
public interface IBatchable {

    String BATCH_JOB_ENTITY = "batchJobEntity";

    /**
     * Returns the shared properties for the given execution
     * @param executionId the execution ID
     * @return the shared properties
     */
    default Properties getSharedProperties(long executionId) {
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        return jobOperator.getParameters(executionId);
    }

    /**
     * Returns the shared properties for the given execution
     * @param executionId the execution ID
     * @return the shared properties
     */
    default BatchEntity getBatchEntity(long executionId) {
        Properties jobProperties = getSharedProperties(executionId);
        if (jobProperties != null) {
            return (BatchEntity)jobProperties.get(BATCH_JOB_ENTITY);
        }
        return null;
    }

}
