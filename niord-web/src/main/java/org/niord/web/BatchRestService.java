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
package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.BatchService;
import org.niord.core.batch.vo.BatchInstanceVo;
import org.niord.core.batch.vo.BatchStatusVo;
import org.niord.core.repo.FileTypes;
import org.niord.core.repo.IconSize;
import org.niord.core.user.TicketService;
import org.niord.model.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
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
    FileTypes fileTypes;

    @Inject
    TicketService ticketService;

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
     * @return the status of the batch job system
     */
    @GET
    @Path("/status")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed("admin")
    public BatchStatusVo getStatus() {
        return batchService.getStatus();
    }


    /**
     * Returns a ticket to be used in a subsequent call to download batch data
     *
     * @return a ticket
     */
    @GET
    @Path("/download-ticket")
    @Produces("text/plain")
    @RolesAllowed("admin")
    @NoCache
    public String getDownloadBatchDataFileTicket() {
        // Because of the @RolesAllowed annotation, we know that the
        // the callee has the "admin" role
        return ticketService.createTicketForRoles("admin");
    }


    /**
     * Downloads the batch data associated with the given instance.
     * First, the callee must call the "/batch/download-ticket" endpoint using Ajax
     * to retrieve a valid ticket, and then pass the ticket along in this function
     *
     * @param instanceId the instance ID
     */
    @GET
    @Path("/instance/{instanceId}/download/{fileName:.*}")
    @PermitAll
    public Response downloadBatchDataFile(
            @PathParam("instanceId") long instanceId,
            @PathParam("fileName") String fileName,
            @QueryParam("ticket") String ticket) {

        // Check the ticket programmatically
        if (!ticketService.validateTicketForRoles(ticket, "admin")) {
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


}
