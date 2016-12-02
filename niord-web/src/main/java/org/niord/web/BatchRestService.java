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

import org.apache.commons.fileupload.FileItem;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.BatchService;
import org.niord.core.batch.BatchSetService;
import org.niord.core.batch.vo.BatchInstanceVo;
import org.niord.core.batch.vo.BatchStatusVo;
import org.niord.core.repo.FileTypes;
import org.niord.core.repo.IconSize;
import org.niord.core.repo.RepositoryService;
import org.niord.core.user.UserService;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;

/**
 * API for accessing the batch functionality
 */
@Path("/batch")
@Stateless
@SecurityDomain("keycloak")
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
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
            @PathParam("jobName") String jobName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize) throws Exception {

        return batchService.getJobInstances(jobName, page * pageSize, pageSize);
    }


    /**
     * Returns the status of the batch job system
     *
     * NB: Since this gets called every 10 seconds, adding a @RolesAllowed("admin") has
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
        if (!userService.isCallerInRole("admin")) {
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
        if (!userService.isCallerInRole("admin")) {
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
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
    public String getBatchJobLogFileContent(
            @PathParam("instanceId") long instanceId,
            @PathParam("logFileName") String logFileName,
            @QueryParam("fromLineNo") Integer fromLineNo) throws IOException {

        return batchService.getBatchJobLogFile(instanceId, logFileName, fromLineNo);
    }


    /**
     * Imports an uploaded messages zip archive
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/execute-batch-set")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("sysadmin")
    public String executeBatchSet(@Context HttpServletRequest request) throws Exception {

        StringBuilder txt = new StringBuilder();

        List<FileItem> items = repositoryService.parseFileUploadRequest(request);

        // Start the batch job for each file item
        items.stream()
                .filter(item -> !item.isFormField())
                .forEach(item -> {
                    try {
                        txt.append("Processing batch-set zip-file: ")
                                .append(item.getName())
                                .append("\n");
                        batchSetService.extractAndExecuteBatchSetArchive(item.getInputStream(), txt);
                    } catch (Exception e) {
                        String errorMsg = "Error processing batch-set zip-file "
                                + item.getName() + ": " + e.getMessage();
                        log.error(errorMsg, e);
                        txt.append(errorMsg);
                    }
                });

        return txt.toString();


    }
}
