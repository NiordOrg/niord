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
package org.niord.web;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.batch.BatchService;
import org.niord.core.batch.BatchSetService;
import org.niord.core.batch.vo.BatchInstanceVo;
import org.niord.core.batch.vo.BatchStatusVo;
import org.niord.core.repo.FileTypes;
import org.niord.core.repo.IconSize;
import org.niord.core.repo.RepositoryService;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.niord.core.util.WebUtils;
import org.niord.model.IJsonSerializable;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API for accessing the batch functionality
 */
@Path("/batch")
@RequestScoped
@Transactional
public class BatchRestService {

    @Inject
    Logger log;

    @Inject
    BatchService batchService;

    @Inject
    BatchSetService batchSetService;

    @Inject
    FileTypes fileTypes;

    @Inject
    UserService userService;

    @Inject
    RepositoryService repositoryService;


    /**
     * Returns the job names
     * @return the job names
     */
    @GET
    @Path("/job-names")
    @Produces("application/json;charset=UTF-8")
    @GZIP
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
    @RolesAllowed(Roles.ADMIN)
    public void stopExecution(@PathParam("executionId") long executionId) {
        batchService.stopExecution(executionId);
    }


    /**
     * Restarts the given batch job execution
     *
     * @param executionId the execution ID
     */
    @PUT
    @Path("/execution/{executionId}/restart")
    @NoCache
    @RolesAllowed(Roles.ADMIN)
    public long restartExecution(@PathParam("executionId") long executionId) {
        return batchService.restartExecution(executionId);
    }


    /**
     * Abandons the given batch job execution
     *
     * @param executionId the execution ID
     */
    @PUT
    @Path("/execution/{executionId}/abandon")
    @NoCache
    @RolesAllowed(Roles.ADMIN)
    public void abandonExecution(@PathParam("executionId") long executionId) {
        batchService.abandonExecution(executionId);
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
    @GZIP
    @NoCache
    @RolesAllowed(Roles.ADMIN)
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
            @PathParam("jobName") String jobName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize) throws Exception {

        return batchService.getJobInstances(jobName, page * pageSize, pageSize);
    }


    /**
     * Returns the status of the batch job system
     *
     * NB: Since this gets called every 10 seconds, adding a @RolesAllowed(Roles.ADMIN) has
     * the potential of filling up the server log.
     * Instead, we allow all to call the endpoint, but return an empty result for non-admin users.
     *
     * @return the status of the batch job system
     */
    @GET
    @Path("/status")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @PermitAll
    public BatchStatusVo getStatus() {
        if (!userService.isCallerInRole(Roles.ADMIN)) {
            return new BatchStatusVo();
        }
        return batchService.getStatus();
    }


    /**
     * Downloads the batch data associated with the given instance.
     * First, the callee must call the "/rest/tickets/ticket?role=admin" endpoint using Ajax
     * to retrieve a valid ticket, and then pass the ticket along in this function
     *
     * @param instanceId the instance ID
     */
    @GET
    @Path("/instance/{instanceId}/download/{fileName:.*}")
    @PermitAll
    public Response downloadBatchDataFile(
            @PathParam("instanceId") long instanceId,
            @PathParam("fileName") String fileName) {

        // Check the ticket programmatically
        if (!userService.isCallerInRole(Roles.ADMIN)) {
            throw new WebApplicationException(403);
        }

        java.nio.file.Path f = batchService.getBatchJobDataFile(instanceId);
        if (f == null) {
            log.warn("Failed streaming batch file: " + fileName);
            return Response
                    .status(HttpServletResponse.SC_NOT_FOUND)
                    .entity("Failed streaming batch file: " + fileName)
                    .build();
        }

        // Set expiry to 12 hours
        Date expirationDate = new Date(System.currentTimeMillis() + 1000L * 60L * 60L * 12L);

        String mt = fileTypes.getContentType(f);

        log.trace("Streaming file: " + f);
        return Response
                .ok(f.toFile(), mt)
                .expires(expirationDate)
                .build();
    }

    /**
     * Returns the thumbnail to use for the given batch job instance
     * @param size the icon size, either 32, 64 or 128
     * @return the thumbnail to use for the given batch job instance
     */
    @GET
    @Path("/instance/{instanceId}/thumbnail.png")
    @PermitAll
    public Response getBatchDataFileThumbnail(
            @PathParam("instanceId") long instanceId,
            @QueryParam("size") @DefaultValue("32") int size) throws IOException, URISyntaxException {

        IconSize iconSize = IconSize.getIconSize(size);
        java.nio.file.Path f = batchService.getBatchJobDataFile(instanceId);

        // Set expiry to 12 hours
        Date expirationDate = new Date(System.currentTimeMillis() + 1000L * 60L * 60L * 12L);

        // NB: Response.temporaryRedirect() uses "/rest" as the root.
        String thumbUri = ".." + fileTypes.getIcon(f, iconSize);

        log.trace("Redirecting to thumbnail: " + thumbUri);
        return Response
                .temporaryRedirect(new URI(thumbUri))
                .expires(expirationDate)
                .build();
    }

    /**
     * Returns the names of log files for the batch job with the given instance.
     * @param instanceId the instance ID
     */
    @GET
    @Path("/instance/{instanceId}/logs")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.ADMIN)
    public List<String> getBatchJobLogFiles(@PathParam("instanceId") long instanceId) throws IOException {

        return batchService.getBatchJobLogFiles(instanceId);
    }

    /**
     * Returns the contents of the batch job log file with the given name.
     *
     * @param instanceId the instance id
     * @param logFileName the log file name
     * @param fromLineNo if specified, only the subsequent lines are returned
     * @return the contents of the log file
     */
    @GET
    @Path("/instance/{instanceId}/logs/{logFileName}")
    @Produces("text/plain")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.ADMIN)
    public String getBatchJobLogFileContent(
            @PathParam("instanceId") long instanceId,
            @PathParam("logFileName") String logFileName,
            @QueryParam("fromLineNo") Integer fromLineNo) throws IOException {

        return batchService.getBatchJobLogFile(instanceId, logFileName, fromLineNo);
    }


    /**
     * Imports an uploaded messages zip archive
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/execute-batch-set")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String executeBatchSet(MultipartFormDataInput input) throws Exception {

        // Initialise the form parsing parameters
        final Map<String, InputStream> formFiles = WebUtils.getMultipartInputFiles(input);
        final StringBuilder txt = new StringBuilder();

        // Start the batch job for each file item
        formFiles.entrySet()
                .stream()
                .forEach(item -> {
                    try {
                        txt.append("Processing batch-set zip-file: ")
                                .append(item.getKey())
                                .append("\n");
                        batchSetService.extractAndExecuteBatchSetArchive(item.getValue(), txt);
                    } catch (Exception e) {
                        String errorMsg = "Error processing batch-set zip-file "
                                + item.getKey()
                                + ": "
                                + e.getMessage();
                        log.error(errorMsg, e);
                        txt.append(errorMsg);
                    }
                });

        return txt.toString();
    }


    /**
     * Executes the given javascript via the "script-executor" batch job
     *
     * NB: Important to only let sysadmin access this operation.
     *
     * @param params the javascript parameter
     */
    @POST
    @Path("/execute-javascript")
    @Consumes("application/json;charset=UTF-8")
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String executeJavaScript(ExecuteJavaScriptParams params) throws Exception {

        if (StringUtils.isNotBlank(params.getJavaScript())) {
            log.warn("User " + userService.currentUser().getName()
                    + " scheduled execution of JavaScript:\n" + params.getJavaScript());

            batchService.startBatchJobWithDataFile(
                    "script-executor",
                    IOUtils.toInputStream(params.getJavaScript(), "UTF-8"),
                    StringUtils.defaultIfBlank(params.getScriptName(), "javascript.js"),
                    new HashMap<>());
        }

        return "OK";
    }


    /**
     * Used when executing a back-end JavaScript
     */
    @SuppressWarnings("unused")
    public static class ExecuteJavaScriptParams implements IJsonSerializable {
        String scriptName;
        String javaScript;

        public String getScriptName() {
            return scriptName;
        }

        public void setScriptName(String scriptName) {
            this.scriptName = scriptName;
        }

        public String getJavaScript() {
            return javaScript;
        }

        public void setJavaScript(String javaScript) {
            this.javaScript = javaScript;
        }
    }

}
