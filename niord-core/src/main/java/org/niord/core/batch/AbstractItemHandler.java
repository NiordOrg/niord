/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.niord.core.batch;

import javax.annotation.PostConstruct;
import javax.batch.api.chunk.ItemProcessor;
import javax.batch.api.chunk.ItemReader;
import javax.batch.api.chunk.ItemWriter;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Can be used as a base class for ItemReader, ItemWriter and ItemProcessor.
 */
public abstract class AbstractItemHandler implements ItemReader, ItemProcessor, ItemWriter {

    @Inject
    protected JobContext jobContext;

    @Inject
    protected BatchService batchService;

    @Inject
    protected org.slf4j.Logger systemLog;

    protected BatchData job;

    // The log in the batch job folder must be accessed via getLog()
    private Logger log;

    /**
     * Initializes the item handler
     */
    @PostConstruct
    protected void init() {
        // Look up the associated batch data
        job = batchService.findByInstanceId(jobContext.getInstanceId());
    }

    /**
     * Returns a logger that will log to the batch job folder
     * @return a logger that will log to the batch job folder
     */
    protected synchronized Logger getLog() {
        if (log != null) {
            return log;
        }

        log = Logger.getLogger(this.getClass().getSimpleName());


        // Store the log in the transient user data, so that it can be properly closed after the batch job is complete.
        // This is handler by the BatchJobListener
        @SuppressWarnings("unchecked")
        Map<String, Logger> logs = (Map<String, java.util.logging.Logger>)jobContext.getTransientUserData();
        if (logs != null) {
            logs.put(getClass().getSimpleName(), log);
        }


        try {
            Path batchJobFolder = batchService.computeBatchJobPath(job.computeBatchJobFolderPath());
            String file = batchJobFolder.resolve(getClass().getSimpleName() + "Log.txt")
                    .toAbsolutePath().toString();
            log.setUseParentHandlers(false);
            FileHandler fh = new FileHandler(file, true);
            fh.setFormatter(new SimpleFormatter());
            fh.setLevel(Level.ALL);
            log.addHandler(fh);

            systemLog.debug("Logging batch job to " + file);
        } catch (IOException e) {
            systemLog.info("Error Logging batch job", e);
        }

        return log;
    }


    /**
     * Updates progress (0-100) for the current batch job.
     *
     * @param progress the progress
     */
    protected void updateProgress(Integer progress) {
        // Note to self: We don't bother updating the local "job" batch data
        batchService.updateBatchJobProgress(jobContext.getInstanceId(), progress);
    }

    /** {@inheritDoc} */
    @Override
    public Object processItem(Object item) throws Exception {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void open(Serializable checkpoint) throws Exception {

    }

    /** {@inheritDoc} */
    @Override
    public void close() throws Exception {
    }

    /** {@inheritDoc} */
    @Override
    public void writeItems(List<Object> items) throws Exception {

    }

    /** {@inheritDoc} */
    @Override
    public Object readItem() throws Exception {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Serializable checkpointInfo() throws Exception {
        return null;
    }
}
