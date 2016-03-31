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

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.niord.core.batch.vo.BatchExecutionVo;
import org.niord.core.batch.vo.BatchInstanceVo;
import org.niord.core.batch.vo.BatchStatusVo;
import org.niord.core.batch.vo.BatchTypeVo;
import org.niord.core.sequence.DefaultSequence;
import org.niord.core.sequence.Sequence;
import org.niord.core.sequence.SequenceService;
import org.niord.core.service.BaseService;
import org.niord.core.settings.Setting.Type;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.user.UserService;
import org.niord.core.util.JsonUtils;
import org.niord.model.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Provides an interface for managing batch jobs.
 * <p>
 * Batch jobs have up to three associated batch job folders under the "batchJobRoot" path:
 * <ul>
 *  <li>
 *      <b>[jobName]/in</b>:
 *      The <b>in</b> folders will be monitored periodically, and any file placed in this folder will
 *      result in the <b>jobName</b> batch job being executed.
 *  </li>
 *  <li>
 *      <b>[jobName]/execution/[year]/[month]/[jobNo]</b>:
 *      Stores any input file associated with a batch job along with log files for the executions steps.
 *      These directories will be cleaned up after a configurable amount of time.
 *  </li>
 *  <li>
 *      <b>[jobName]/out</b>:
 *      Any file-based result from the execution of a batch job can be placed here.
 *      Not implemented yet.
 *  </li>
 * </ul>
 * <p>
 *
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
    public static final String BATCH_JOB_ENTITY = "batchJobEntity";

    @Inject
    private Logger log;

    @Inject
    UserService userService;

    @Inject
    JobOperator jobOperator;

    @Inject
    SequenceService sequenceService;

    @Inject
    @Setting(value="batchJobRootPath", defaultValue="${niord.home}/batch-jobs", description="The root directory of the Niord batch jobs")
    private Path batchJobRoot;

    // Delete execution files after 10 days
    @Inject
    @Setting(value="batchFileExpiryDays", defaultValue="10", description="Number of days after which batch job files are deleted", type = Type.Integer)
    private int batchFileExpiryDays;


    /****************************/
    /** Starting batch jobs    **/
    /****************************/


    /**
     * Starts a new batch job
     *
     * @param job the batch job name
     */
    public long startBatchJob(BatchData job) {

        // Note to self:
        // There are some transaction issues with storing the BatchData in the current transaction,
        // so, we leave it to the JobStartBatchlet batch step.

        // Launch the batch job
        Properties props = new Properties();
        props.put(BATCH_JOB_ENTITY, job);
        long executionId = jobOperator.start(job.getJobName(), props);

        log.info("Started batch job: " + job);
        return executionId;
    }


    /**
     * Creates and initializes a new batch job data entity
     */
    private BatchData initBatchData(String jobName, Properties properties) throws IOException {
        // Construct a new batch data entity
        BatchData job = new BatchData();
        job.setUser(userService.currentUser());
        job.setJobName(jobName);
        job.setJobNo(getNextJobNo(jobName));
        job.writeProperties(properties);
        return job;
    }


    /**
     * Starts a new batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithDeflatedData(String jobName, Object data, String dataFileName, Properties properties) throws Exception {

        BatchData job = initBatchData(jobName, properties);

        if (data != null) {
            dataFileName = StringUtils.isNotBlank(dataFileName) ? dataFileName : "batch-data.zip";
            job.setDataFileName(dataFileName);
            Path path = computeBatchJobPath(job.computeDataFilePath());
            createDirectories(path.getParent());
            try (FileOutputStream file = new FileOutputStream(path.toFile());
                 GZIPOutputStream gzipOut = new GZIPOutputStream(file);
                 ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut)) {
                objectOut.writeObject(data);
            }
        }

        return startBatchJob(job);
    }


    /**
     * Starts a new batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithJsonData(String jobName, Object data, String dataFileName, Properties properties) throws Exception {

        BatchData job = initBatchData(jobName, properties);

        if (data != null) {
            dataFileName = StringUtils.isNotBlank(dataFileName) ? dataFileName : "batch-data.json";
            job.setDataFileName(dataFileName);
            Path path = computeBatchJobPath(job.computeDataFilePath());
            createDirectories(path.getParent());
            JsonUtils.writeJson(data, path);
        }

        return startBatchJob(job);
    }


    /**
     * Starts a new file-based batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithDataFile(String jobName, InputStream in, String dataFileName, Properties properties) throws IOException {

        BatchData job = initBatchData(jobName, properties);

        if (in != null) {
            job.setDataFileName(dataFileName);
            Path path = computeBatchJobPath(job.computeDataFilePath());
            createDirectories(path.getParent());
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        }

        return startBatchJob(job);
    }


    /**
     * Starts a new file-based batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithDataFile(String jobName, Path file, Properties properties) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Invalid file " + file);
        }

        try (InputStream in = new FileInputStream(file.toFile())) {
            return startBatchJobWithDataFile(
                    jobName,
                    in,
                    file.getFileName().toString(),
                    properties);
        }
    }


    /**
     * Returns the next sequence number for the batch job
     *
     * @param jobName the name of the batch job
     * @return the next sequence number for the batch job
     */
    private Long getNextJobNo(String jobName) {
        Sequence jobSequence = new DefaultSequence("BATCH_JOB_" + jobName, 1);
        return sequenceService.getNextValue(jobSequence);
    }


    /****************************/
    /** Batch job repository   **/
    /****************************/


    /**
     * Creates the given directories if they do not exist
     */
    private Path createDirectories(Path path) throws IOException {
        if (path != null && !Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }


    /**
     * Computes the absolute path to the given local path within the repository
     *
     * @param localPath the local path withing the batch job repository
     * @return the absolute path to the given local path within the repository
     */
    public Path computeBatchJobPath(Path localPath) {

        Path path = batchJobRoot
                .normalize()
                .resolve(localPath);

        // Check that it is a valid path within the batch job repository root
        path = path.normalize();
        if (!path.startsWith(batchJobRoot)) {
            throw new RuntimeException("Invalid path " + localPath);
        }

        return path;
    }

    /**
     * Returns the data file associated with the given batch job instance.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data file associated with the given batch job instance.
     */
    public Path getBatchJobDataFile(Long instanceId) {
        BatchData job = findByInstanceId(instanceId);

        if (job == null || job.getDataFileName() == null) {
            return null;
        }

        Path path = computeBatchJobPath(job.computeDataFilePath());
        return Files.isRegularFile(path) ? path : null;
    }


    /**
     * Loads the batch job JSON data file as the given class.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data
     */
    public <T> T readBatchJobJsonDataFile(Long instanceId, Class<T> dataClass) throws IOException {
        Path path = getBatchJobDataFile(instanceId);

        return path != null ? JsonUtils.readJson(dataClass, path) : null;
    }


    /**
     * Loads the batch job JSON data file as the given class.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data
     */
    public <T> T readBatchJobJsonDataFile(Long instanceId, TypeReference typeRef) throws IOException {
        Path path = getBatchJobDataFile(instanceId);

        return path != null ? JsonUtils.readJson(typeRef, path) : null;
    }


    /**
     * Loads the batch job deflated data file as the given class.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data
     */
    @SuppressWarnings("unchecked")
    public <T> T readBatchJobDeflatedDataFile(Long instanceId) throws IOException, ClassNotFoundException {
        Path path = getBatchJobDataFile(instanceId);

        if (path != null) {
            try (FileInputStream file = new FileInputStream(path.toFile());
                 GZIPInputStream gzipIn = new GZIPInputStream(file);
                 ObjectInputStream objectIn = new ObjectInputStream(gzipIn)) {
                return (T) objectIn.readObject();
            }
        }

        return null;
    }


    /**
     * Returns the list of log names in the batch job directory
     * @param instanceId the instance id
     * @return the list of log names in the batch job directory
     */
    public List<String> getBatchJobLogFiles(Long instanceId) throws IOException {

        BatchData job = findByInstanceId(instanceId);
        if (job == null) {
            throw new IllegalArgumentException("Invalid batch instance ID " + instanceId);
        }

        Path path = computeBatchJobPath(job.computeBatchJobFolderPath());

        File[] files = path.toFile().listFiles();
        return files == null
                ? Collections.emptyList()
                : Arrays.stream(files)
                    .filter(f -> Files.isRegularFile(f.toPath()) && f.getName().matches(".+Log\\.txt(\\.\\d+)?"))
                    .map(File::getName)
                    .sorted()
                    .collect(Collectors.toList());
    }


    /**
     * Returns the contents of the log file with the given file name.
     * If fromLineNo is specified, only the subsequent lines are returned.
     *
     * @param instanceId the instance id
     * @param logFileName the log file name
     * @param fromLineNo if specified, only the subsequent lines are returned
     * @return the contents of the log file
     */
    public String getBatchJobLogFile(Long instanceId, String logFileName, Integer fromLineNo) throws IOException {

        BatchData job = findByInstanceId(instanceId);
        if (job == null) {
            throw new IllegalArgumentException("Invalid batch instance ID " + instanceId);
        }

        Path path = computeBatchJobPath(job.computeBatchJobFolderPath().resolve(logFileName));
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Invalid batch log file " + logFileName);
        }

        int skipLineNo = fromLineNo == null ? 0 : fromLineNo;
        return Files.lines(path)
                .skip(skipLineNo)
                .collect(Collectors.joining("\n"));
    }

    /****************************/
    /** Utility methods        **/
    /****************************/


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
     * Updates progress (0-100) for the given batch job.
     *
     * @param instanceIds the batch job id
     * @param progress the progress
     */
    public void updateBatchJobProgress(Long instanceIds, Integer progress) {

        BatchData job = findByInstanceId(instanceIds);
        if (job != null) {
            job.setProgress(progress);
            saveEntity(job);
        }
    }


    /****************************/
    /** Managing batch jobs    **/
    /****************************/


    /**
     * Returns the batch job names
     *
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
        //noinspection SqlDialectInspection
        return (List<String>) em
                .createNativeQuery("SELECT DISTINCT JOBNAME FROM JOB_INSTANCE ORDER BY lower(JOBNAME)")
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
     *
     * @param jobName the job name
     * @param start   the start index of the paged search result
     * @param count   the max number of instances per start
     * @return the paged search result for the given batch type
     */
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
            String jobName, int start, int count) throws Exception {

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
        for (BatchInstanceVo i : result.getData()) {
            BatchData data = batchDataLookup.get(i.getInstanceId());
            if (data != null) {
                i.setFileName(data.getDataFileName());
                i.setJobNo(data.getJobNo());
                i.setUser(data.getUser() != null ? data.getUser().getName() : null);
                i.setJobName(data.getJobName());
                i.setProperties(data.readProperties());
                i.setProgress(data.getProgress());
            }
        }

        result.updateSize();
        return result;
    }


    /**
     * Returns the status of the batch job system
     *
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


    /***************************************/
    /** Batch "in" folder monitoring      **/
    /***************************************/

    /**
     * Called every minute to monitor the batch job "[jobName]/in" folders. If a file has been
     * placed in one of these folders, the cause the "jobName" batch job to be started.
     */
    @Schedule(persistent=false, second="48", minute="*/1", hour="*/1")
    protected void monitorBatchJobInFolderInitiation() {

        // Resolve the list of batch job "in" folders
        List<Path> executionFolders = getBatchJobSubFolders("in");

        // Check for new batch-initiating files in each folder
        for (Path dir : executionFolders) {
            for (Path file : getDirectoryFiles(dir)) {
                String jobName = file.getParent().getParent().getFileName().toString();
                log.info("Found file " + file.getFileName() + " for batch job " + jobName);

                try {
                    startBatchJobWithDataFile(jobName, file, new Properties());
                } catch (IOException e) {
                    log.error("Failed starting batch job " + jobName + " with file " + file.getFileName());
                } finally {
                    // Delete the file
                    // Note to self: Move to error folder?
                    try { Files.delete(file); } catch (IOException ignored) {}
                }
            }
        }
    }


    /***************************************/
    /** Batch "execution" folder clean-up **/
    /***************************************/

    /**
     * Called every hour to clean up the batch job "[jobName]/execution" folders for expired files
     */
    @Schedule(persistent=false, second="30", minute="42", hour="*/1")
    protected void cleanUpExpiredBatchJobFiles() {

        long t0 = System.currentTimeMillis();

        // Resolve the list of batch job "execution" folders
        List<Path> executionFolders = getBatchJobSubFolders("execution");

        // Compute the expiry time
        Calendar expiryDate = Calendar.getInstance();
        expiryDate.add(Calendar.DATE, -batchFileExpiryDays);

        // Clean up the files
        executionFolders.forEach(folder -> {
            try {
                Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (isDirEmpty(dir)) {
                            log.info("Deleting batch job directory :" + dir);
                            Files.delete(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (FileUtils.isFileOlder(file.toFile(), expiryDate.getTime())) {
                            log.info("Deleting batch job file      :" + file);
                            Files.delete(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.error("Failed cleaning up " + folder + " batch job directory: " + e.getMessage(), e);
            }
        });

        log.info(String.format("Cleaned up expired batch job files in %d ms",
                System.currentTimeMillis() - t0));
    }


    /** Returns the named sub-folders **/
    private List<Path> getBatchJobSubFolders(String subFolderName) {
        List<Path> subFolders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(batchJobRoot)) {
            stream.forEach(p -> {
                Path executionFolder = p.resolve(subFolderName);
                if (Files.isDirectory(executionFolder)) {
                    subFolders.add(executionFolder);
                }
            });
        } catch (IOException e) {
            log.error("Failed finding '" + subFolderName + "' batch job folders" + e);
        }
        return subFolders;
    }


    /** Returns the list of regular files in the given directory **/
    private List<Path> getDirectoryFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isReadable(p) && Files.isRegularFile(p) && !Files.isHidden(p)) {
                    files.add(p);
                }
            }
        } catch (IOException ignored) {
        }
        return files;
    }


    /** Returns if the given directory is empty **/
    private boolean isDirEmpty(final Path directory) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            return !dirStream.iterator().hasNext();
        }
    }

}
