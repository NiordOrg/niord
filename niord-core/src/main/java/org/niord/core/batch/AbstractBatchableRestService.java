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

import org.apache.commons.fileupload.FileItem;
import org.niord.core.repo.RepositoryService;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
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
     * @param request the servlet request
     * @param batchJobName the name of the batch job
     * @return a status
     */
    protected String executeBatchJobFromUploadedFile(HttpServletRequest request, String batchJobName) throws Exception {

        StringBuilder txt = new StringBuilder();

        List<FileItem> items = repositoryService.parseFileUploadRequest(request);

        // Collect properties from non-file form parameters
        Map<String, Object> params = new HashMap<>();
        items.stream()
            .filter(FileItem::isFormField)
            .forEach(item -> {
                try {
                    params.put(item.getFieldName(), item.getString("UTF-8"));
                } catch (Exception ignored) {
                }
            });


        // Start the batch job for each file item
        items.stream()
                .filter(item -> !item.isFormField())
                .forEach(item -> {
                    try {
                        checkBatchJob(batchJobName, item, params);
                        startBatchJob(batchJobName, item, params, txt);
                    } catch (Exception e) {
                        String errorMsg = "Error executing batch job " + batchJobName
                                    + " with file " + item.getName() + ": " + e.getMessage();
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
     * @param fileItem the file item
     * @param params the non-file form parameters
     */
    @SuppressWarnings("unused")
    protected void checkBatchJob(String batchJobName, FileItem fileItem, Map<String, Object> params) throws Exception {
        // By default, we accept the batch job
    }

    /**
     * Starts the batch job on the given file
     *
     * @param batchJobName the batch job name
     * @param fileItem the file item
     * @param params the non-file form parameters
     * @param txt a log of the import
     */
    private void startBatchJob(String batchJobName, FileItem fileItem, Map<String, Object> params, StringBuilder txt) throws Exception {
        batchService.startBatchJobWithDataFile(
                batchJobName,
                fileItem.getInputStream(),
                fileItem.getName(),
                params);

        String message = "Started batch job '" + batchJobName + "' with file " + fileItem.getName();
        log.info(message);
        txt.append(message);
    }


}
