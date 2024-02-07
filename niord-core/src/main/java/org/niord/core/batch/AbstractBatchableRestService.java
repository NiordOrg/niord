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

import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.repo.RepositoryService;
import org.niord.core.util.WebUtils;
import org.slf4j.Logger;

import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.Map;

/**
 * May be used as a super class for REST services that handles the uploading of a batch-job file
 * and subsequent execution of the associated batch job
 */
public abstract class AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    BatchService batchService;

    @Inject
    RepositoryService repositoryService;

    /**
     * Starts the execution of a batch job from an uploaded file
     *
     * @param input the multi-part form data input request
     * @param batchJobName the name of the batch job
     * @return a status
     */
    protected String executeBatchJobFromUploadedFile(MultipartFormDataInput input, String batchJobName) throws Exception {

        // Initialise the form parsing parameters
        final Map<String, Object> formParams = WebUtils.getMultipartInputFormParams(input);
        final Map<String, InputStream> formFiles = WebUtils.getMultipartInputFiles(input);
        final StringBuilder txt = new StringBuilder();

        // Start the batch job for each file item
        formFiles.entrySet()
                .stream()
                .forEach(fileEntry -> {
                    try {
                        checkBatchJob(batchJobName, fileEntry.getKey(), fileEntry.getValue(), formParams);
                        startBatchJob(batchJobName, fileEntry.getKey(), fileEntry.getValue(), formParams, txt);
                    } catch (Exception e) {
                        String errorMsg = "Error executing batch job " + batchJobName + " with file " + fileEntry.getKey() + ": " + e.getMessage();
                        log.error(errorMsg, e);
                        txt.append(errorMsg);
                    }
                });

        return txt.toString();
    }

    /**
     * Allows sub-classes to validate the uploaded file and parameters.
     * The method should throw an exception to prevent the batch job from running
     *
     * @param batchJobName the batch job name
     * @param fileName the file name
     * @param inputStream the file contents
     * @param params the non-file form parameters
     */
    @SuppressWarnings("unused")
    protected void checkBatchJob(String batchJobName, String fileName, InputStream inputStream, Map<String, Object> params) throws Exception {
        // By default, we accept the batch job
    }

    /**
     * Starts the batch job on the given file
     *
     * @param batchJobName the batch job name
     * @param fileName the file name
     * @param inputStream the file contents
     * @param params the non-file form parameters
     * @param txt a log of the import
     */
    private void startBatchJob(String batchJobName, String fileName, InputStream inputStream, Map<String, Object> params, StringBuilder txt) throws Exception {
        batchService.startBatchJobWithDataFile(
                batchJobName,
                inputStream,
                fileName,
                params);

        String message = "Started batch job '" + batchJobName + "' with file " + fileName;
        log.info(message);
        txt.append(message);
    }


}
