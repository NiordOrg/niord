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

import org.niord.core.batch.vo.BatchExecutionVo;
import org.niord.core.batch.vo.BatchInstanceVo;
import org.niord.core.batch.vo.BatchStatusVo;
import org.niord.core.batch.vo.BatchTypeVo;
import org.niord.core.repo.FileTypes;
import org.niord.core.repo.RepositoryService;
import org.niord.core.service.BaseService;
import org.niord.core.service.UserService;
import org.niord.model.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides an interface for managing batch jobs.
 * <p>
 * Note to self: Currently, there is a bug in Wildfly, so that the batch job xml files cannot be loaded
 * from an included jar.
 * Hence, move the xml files to META-INF/batch-jobs of the web application you are working on.
 *
 * @see <a hreg="https://issues.jboss.org/browse/WFLY-4988">Error report</a>
 * @see <a hreg="https://github.com/NiordOrg/niord-dk/tree/master/niord-dk-web">Example solution</a>
 */
@Stateless
@SuppressWarnings("unused")
public class BatchService extends BaseService {

    public static final String BATCH_REPO_FOLDER = "batch";

    @Inject
    private Logger log;

    @Inject
    RepositoryService repositoryService;

    @Inject
    UserService userService;

    @Inject
    JobOperator jobOperator;

    @Inject
    FileTypes fileTypes;

    /**
     * Starts a new batch job
     *
     * @param job the batch job name
     */
    public long startBatchJob(BatchData job) {

        // Launch the batch job
        Properties props = new Properties();
        props.put(IBatchable.BATCH_JOB_ENTITY, job);
        long executionId = jobOperator.start(job.getJobName(), props);

        log.info("Started batch job: " + job);
        return executionId;
    }


    /**
     * Starts a new raw data-based batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJob(String jobName, byte[] data, boolean deflated, String fileName, String fileType) throws Exception {

        // Construct a new raw data-based batch data entity
        BatchRawData job = new BatchRawData();
        job.setUser(userService.currentUser());
        job.setJobName(jobName);
        job.setFileName(fileName);
        job.setFileType(fileType);
        job.setData(data);
        job.setDeflated(deflated);

        return startBatchJob(job);
    }

    /**
     * Starts a new raw data-based batch job.
     * NB: The data will be deflated
     *
     * @param jobName the batch job name
     */
    public long startBatchJobDeflateData(String jobName, Object data,  String fileName, String fileType) throws Exception {

        return startBatchJob(jobName, BatchRawData.writeDeflatedData(data), true, fileName, fileType);
    }

    /**
     * Starts a new file-based batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJob(String jobName, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Invalid file " + file);
        }

        try (InputStream in = new FileInputStream(file.toFile())) {
            return startBatchJob(
                    jobName,
                    in,
                    file.getFileName().toString(),
                    fileTypes.getContentType(file));
        }
    }

    /**
     * Starts a new file-based batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJob(String jobName, InputStream in, String fileName, String fileType) throws IOException {

        // Copy the file to the repository
        String repoUri = copyToBatchRepo(jobName, in, fileName);

        // Construct a new file-based batch data entity
        BatchFileData job = new BatchFileData();
        job.setUser(userService.currentUser());
        job.setJobName(jobName);
        job.setFileName(fileName);
        job.setBatchFilePath(repoUri);
        job.setFileType(fileType);

        return startBatchJob(job);
    }

    /**
     * Saves the file to a batch repository folder
     * @param jobName the batch job name
     * @param in the input stream
     * @param fileName the file name
     * @return the resulting path
     */
    private String copyToBatchRepo(String jobName, InputStream in, String fileName) throws IOException {
        Path repoFolder = repositoryService.getRepoRoot()
                .resolve(BATCH_REPO_FOLDER)
                .resolve(jobName);

        // Create the folder if it does not exist
        if (!Files.exists(repoFolder)) {
            Files.createDirectories(repoFolder);
        }

        // Copy the file to the repo
        DateFormat df = new SimpleDateFormat("yyymmdd-HHmmss-");
        Path repoFile = repoFolder.resolve(df.format(new Date()) + fileName);
        Files.copy(in, repoFile);
        return repositoryService.getRepoUri(repoFile);
    }


    /**
     * Returns the batch data entity with the given instance id.
     * Returns null if no batch data entity is not found.
     *
     * @param instanceId the instance id
     * @return the batch data entity with the given instance id
     */
    public BatchData findByInstanceId(Long instanceId) {
        try {
            return em.createNamedQuery("BatchData.findByInstanceId", BatchData.class)
                    .setParameter("instanceId", instanceId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the batch data entities with the given instance ids.
     *
     * @param instanceIds the instance ids
     * @return the batch data entities with the given instance ids
     */
    public List<BatchData> findByInstanceIds(Set<Long> instanceIds) {
        return em.createNamedQuery("BatchData.findByInstanceIds", BatchData.class)
                .setParameter("instanceIds", instanceIds)
                .getResultList();
    }


    /**
     * Creates or updates the batch job.
     *
     * @param job the batch job entity
     */
    public BatchData saveBatchJob(BatchData job) {
        Objects.requireNonNull(job, "Invalid job parameter");
        Objects.requireNonNull(job.getInstanceId(), "Invalid job instance ID");

        job = saveEntity(job);
        em.flush();
        return job;
    }

    /**
     * Returns the batch job names
     * @return the batch job names
     */
    @SuppressWarnings("unchecked")
    public List<String> getJobNames() {
        // Sadly, this gets reset upon every JVM restart
        /*
        return jobOperator.getJobNames()
                .stream()
                .sorted()
                .collect(Collectors.toList());
        */

        // Look up the names from the database
        return (List<String>)em
                .createNativeQuery("select distinct JOBNAME from JOB_INSTANCE order by lower(JOBNAME)")
                .getResultList();
    }


    /**
     * Stops the given batch job execution
     *
     * @param executionId the execution ID
     */
    public void stopExecution(long executionId) {
        jobOperator.stop(executionId);
    }


    /**
     * Restarts the given batch job execution
     *
     * @param executionId the execution ID
     */
    public long restartExecution(long executionId) {
        return jobOperator.restart(executionId, new Properties());
    }

    /**
     * Abandons the given batch job execution
     *
     * @param executionId the execution ID
     */
    public void abandonExecution(long executionId) {
        jobOperator.abandon(executionId);
    }

    /**
     * Returns the paged search result for the given batch type
     * @param jobName the job name
     * @param start the start index of the paged search result
     * @param count the max number of instances per start
     * @return the paged search result for the given batch type
     */
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
           String jobName, int start, int count) {

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
            instance.updateExecutions();
        });

        // Next, copy the associated batch data to the instance VO
        Set<Long> instanceIds = result.getData().stream()
                .map(BatchInstanceVo::getInstanceId).collect(Collectors.toSet());
        Map<Long, BatchData> batchDataLookup = findByInstanceIds(instanceIds)
                .stream()
                .collect(Collectors.toMap(BatchData::getInstanceId, Function.identity()));
        result.getData().forEach(i -> {
            BatchData data = batchDataLookup.get(i.getInstanceId());
            if (data != null) {
                i.setFileName(data.getFileName());
                i.setFileType(data.getFileType());
                i.setUser(data.getUser() != null ? data.getUser().getName() : null);
                i.setJobName(data.getJobName());
            }
        });

        result.updateSize();
        return result;
    }


    /**
     * Returns the status of the batch job system
     * @return the status of the batch job system
     */
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

}
