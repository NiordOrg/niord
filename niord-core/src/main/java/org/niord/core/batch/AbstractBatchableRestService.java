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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.niord.core.repo.RepositoryService;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import java.util.List;
import java.util.Properties;

/**
 * May be used as a super class for REST services that handles the uploading of a batch-job file
 * and subsequent execution of the associated batch job
 */
public abstract class AbstractBatchableRestService {

    @Context
    ServletContext servletContext;

    @Inject
    Logger log;

    @Inject
    BatchService batchService;


    /**
     * Starts the execution of a batch job from an uploaded file
     *
     * @param request the servlet request
     * @param batchJobName the name of the batch job
     * @return a status
     */
    protected String executeBatchJobFromUploadedFile(HttpServletRequest request, String batchJobName) throws Exception {

        FileItemFactory factory = RepositoryService.newDiskFileItemFactory(servletContext);
        ServletFileUpload upload = new ServletFileUpload(factory);

        StringBuilder txt = new StringBuilder();

        List<FileItem> items = upload.parseRequest(request);

        // Collect properties from non-file form parameters
        Properties params = new Properties();
        items.stream()
            .filter(FileItem::isFormField)
            .forEach(item -> {
                try {
                    params.setProperty(item.getFieldName(), item.getString());
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
    protected void checkBatchJob(String batchJobName, FileItem fileItem, Properties params) throws Exception {
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
    private void startBatchJob(String batchJobName, FileItem fileItem, Properties params, StringBuilder txt) throws Exception {
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
