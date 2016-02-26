package org.niord.web;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.BatchService;
import org.niord.model.IJsonSerializable;
import org.niord.model.PagedSearchResultVo;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.batch.runtime.BatchStatus;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.*;

/**
 * API for accessing the batch functionality
 */
@Path("/batch")
@Stateless
@SecurityDomain("keycloak")
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
    @PermitAll
    public List<String> getJobNames() {
        return batchService.getJobNames();
    }


    /**
     * Stops the given batch job execution
     *
     * @param executionId the execution ID
     */
    @PUT
    @Path("/execution/{executionId}/stop")
    @NoCache
    @RolesAllowed("admin")
    public void stopExecution(@PathParam("executionId") long executionId) {
        jobOperator.stop(executionId);
    }


    /**
     * Restarts the given batch job execution
     *
     * @param executionId the execution ID
     */
    @PUT
    @Path("/execution/{executionId}/restart")
    @NoCache
    @RolesAllowed("admin")
    public long restartExecution(@PathParam("executionId") long executionId) {
        Properties properties = jobOperator.getParameters(executionId);
        return jobOperator.restart(executionId, properties);
    }

    /**
     * Abandons the given batch job execution
     *
     * @param executionId the execution ID
     */
    @PUT
    @Path("/execution/{executionId}/abandon")
    @NoCache
    @RolesAllowed("admin")
    public void abandonExecution(@PathParam("executionId") long executionId) {
        jobOperator.abandon(executionId);
    }

    /**
     * Returns the paged search result for the given batch type
     * @param jobName the job name
     * @param page the page index of the paged search result
     * @param pageSize the max number of instances per page
     * @return the paged search result for the given batch type
     */
    @GET
    @Path("/{jobName}/instances")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    @RolesAllowed("admin")
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
            @PathParam("jobName") String jobName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize) {

        Objects.requireNonNull(jobName);
        PagedSearchResultVo<BatchInstanceVo> result = new PagedSearchResultVo<>();

        result.setTotal(jobOperator.getJobInstanceCount(jobName));

        jobOperator.getJobInstances(jobName, page * pageSize, pageSize).forEach(i -> {
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
            instance.updateExecutions();
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
    @RolesAllowed("admin")
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public static class BatchInstanceVo implements IJsonSerializable {
        String name;
        long instanceId;
        List<BatchExecutionVo> executions = new ArrayList<>();

        /** Sorts the executions with the most recent execution first and update execution flags **/
        public void updateExecutions() {
            // Sort executions
            Comparator<BatchExecutionVo> mostRecentFirst = (e1, e2) -> {
                if (e1.getStartTime() == null && e2.getStartTime() == null) {
                    return 0;
                } else if (e1.getStartTime() == null) {
                    return 1;
                } else if (e2.getStartTime() == null) {
                    return -1;
                }
                return -e1.getStartTime().compareTo(e2.getStartTime());
            };
            executions.sort(mostRecentFirst);

            // Update execution flags
            executions.forEach(BatchExecutionVo::updateFlags);

            // Only latest execution of an instance can be restarted
            if (!executions.isEmpty()) {
                executions.stream()
                        .skip(1)
                        .forEach(e -> e.setRestartable(false));
            }

            // Execution can only be abandoned if there are no running executions for the instance
            if (executions.stream().anyMatch(BatchExecutionVo::isStoppable)) {
                executions.forEach(e -> e.setAbandonable(false));
            }
        }

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
    @SuppressWarnings("unused")
    public static class BatchExecutionVo implements IJsonSerializable {
        long executionId;
        BatchStatus batchStatus;
        Date startTime;
        Date endTime;
        boolean restartable;
        boolean stoppable;
        boolean abandonable;

        /** Update flags **/
        public void updateFlags() {
            stoppable = batchStatus == BatchStatus.STARTED || batchStatus == BatchStatus.STARTING;
            restartable = batchStatus == BatchStatus.FAILED || batchStatus == BatchStatus.STOPPED;
            abandonable = restartable;
        }

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

        public boolean isRestartable() {
            return restartable;
        }

        public void setRestartable(boolean restartable) {
            this.restartable = restartable;
        }

        public boolean isStoppable() {
            return stoppable;
        }

        public void setStoppable(boolean stoppable) {
            this.stoppable = stoppable;
        }

        public boolean isAbandonable() {
            return abandonable;
        }

        public void setAbandonable(boolean abandonable) {
            this.abandonable = abandonable;
        }
    }
}

