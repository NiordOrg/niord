package org.niord.web;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.batch.BatchService;
import org.niord.model.IJsonSerializable;
import org.niord.model.PagedSearchResultVo;

import javax.batch.operations.JobOperator;
import javax.batch.operations.JobSecurityException;
import javax.batch.operations.NoSuchJobException;
import javax.batch.runtime.BatchStatus;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * API for accessing the batch functionality
 */
@Path("/batch")
@Stateless
public class BatchRestService {

    @Inject
    JobOperator jobOperator;

    @Inject
    BatchService batchService;

    /**
     * Returns the job names
     * @return the job names
     */
    @GET
    @Path("/job-names")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public List<String> getJobNames() {
        return batchService.getJobNames();
    }

    /**
     * Returns the job names
     * @return the job names
     */
    @GET
    @Path("/job-instance/{jobName}/count")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public int getJobInstanceCount(@PathParam("jobName") String jobName) {
        return jobOperator.getJobInstanceCount(jobName);
    }


    /**
     * Returns the paged search result for the given batch type
     * @param jobName the job name
     * @param start the start index of the paged search result
     * @param count the max number of instances per start
     * @return the paged search result for the given batch type
     */
    @GET
    @Path("/{jobName}/instances")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
            @PathParam("jobName") String jobName,
            @QueryParam("start") @DefaultValue("0") int start,
            @QueryParam("count") @DefaultValue("10") int count) {

        Objects.requireNonNull(jobName);
        PagedSearchResultVo<BatchInstanceVo> result = new PagedSearchResultVo<>();

        result.setTotal(jobOperator.getJobInstanceCount(jobName));

        jobOperator.getJobInstances(jobName, start, count).forEach(i -> {
            BatchInstanceVo instance = new BatchInstanceVo();
            instance.setName(i.getJobName());
            instance.setInstanceId(i.getInstanceId());
            result.getData().add(instance);
            jobOperator.getJobExecutions(i).forEach(e -> {
                BatchExecutionVo execution = new BatchExecutionVo();
                execution.setExecutionId(e.getExecutionId());
                execution.setBatchStatus(e.getBatchStatus());
                execution.setStartTime(e.getStartTime());
                execution.setEndTime(e.getEndTime());
                instance.getExecutions().add(execution);
            });
        });

        result.updateSize();
        return result;
    }


    /**
     * Returns the status of the batch job system
     * @return the status of the batch job system
     */
    @GET
    @Path("/status")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public BatchStatusVo getStatus() {
        BatchStatusVo status = new BatchStatusVo();
        getJobNames().forEach(name -> {
            // Create a status for the batch type
            BatchTypeVo batchType = new BatchTypeVo();
            batchType.setName(name);
            try {
                batchType.setRunningExecutions(jobOperator.getRunningExecutions(name).size());
            } catch (NoSuchJobException ignored) {
                // When the JVM has restarted the call will fail until the job has executed the first time.
                // A truly annoying behaviour, given that we use persisted batch jobs.
            }
            batchType.setInstanceCount(jobOperator.getJobInstanceCount(name));

            // Update the global batch status with the batch type
            status.getTypes().add(batchType);
            status.setRunningExecutions(status.getRunningExecutions() + batchType.getRunningExecutions());
        });
        return status;
    }

    /*************************/
    /** Helper classes      **/
    /*************************/

    /** Encapsulates the status of all batch job **/
    public static class BatchStatusVo implements IJsonSerializable {
        int runningExecutions;

        List<BatchTypeVo> types = new ArrayList<>();

        public int getRunningExecutions() {
            return runningExecutions;
        }

        public void setRunningExecutions(int runningExecutions) {
            this.runningExecutions = runningExecutions;
        }

        public List<BatchTypeVo> getTypes() {
            return types;
        }

        public void setTypes(List<BatchTypeVo> types) {
            this.types = types;
        }
    }


    /** Encapsulates the status of a named batch job **/
    public static class BatchTypeVo implements IJsonSerializable {
        String name;
        int instanceCount;
        int runningExecutions;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getInstanceCount() {
            return instanceCount;
        }

        public void setInstanceCount(int instanceCount) {
            this.instanceCount = instanceCount;
        }

        public int getRunningExecutions() {
            return runningExecutions;
        }

        public void setRunningExecutions(int runningExecutions) {
            this.runningExecutions = runningExecutions;
        }
    }


    /** Encapsulates a batch job instance **/
    public static class BatchInstanceVo implements IJsonSerializable {
        String name;
        long instanceId;
        List<BatchExecutionVo> executions = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(long instanceId) {
            this.instanceId = instanceId;
        }

        public List<BatchExecutionVo> getExecutions() {
            return executions;
        }

        public void setExecutions(List<BatchExecutionVo> executions) {
            this.executions = executions;
        }
    }


    /** Encapsulates a batch job execution **/
    public static class BatchExecutionVo implements IJsonSerializable {
        long executionId;
        BatchStatus batchStatus;
        Date startTime;
        Date endTime;

        public long getExecutionId() {
            return executionId;
        }

        public void setExecutionId(long executionId) {
            this.executionId = executionId;
        }

        public BatchStatus getBatchStatus() {
            return batchStatus;
        }

        public void setBatchStatus(BatchStatus batchStatus) {
            this.batchStatus = batchStatus;
        }

        public Date getStartTime() {
            return startTime;
        }

        public void setStartTime(Date startTime) {
            this.startTime = startTime;
        }

        public Date getEndTime() {
            return endTime;
        }

        public void setEndTime(Date endTime) {
            this.endTime = endTime;
        }
    }
}

