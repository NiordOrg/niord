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

import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.BatchData;
import org.niord.core.batch.BatchFileData;
import org.niord.core.batch.BatchRawData;
import org.niord.core.batch.BatchService;
import org.niord.core.batch.vo.BatchInstanceVo;
import org.niord.core.batch.vo.BatchStatusVo;
import org.niord.model.PagedSearchResultVo;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.List;

/**
 * API for accessing the batch functionality
 */
@Path("/batch")
@Stateless
@SecurityDomain("keycloak")
public class BatchRestService {

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
    @NoCache
    @RolesAllowed("admin")
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
            @PathParam("jobName") String jobName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize) {

        return batchService.getJobInstances(jobName, page * pageSize, pageSize);
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
        return batchService.getStatus();
    }

    /**
     * Downloads the batch data associated with the given instance
     *
     * @param instanceId the instance ID
     */
    @PUT
    @Path("/instance/{instanceId}/download/{fileName:.*}")
    @NoCache
    @RolesAllowed("admin")
    public long downloadBatchData(@PathParam("instanceId") long instanceId, String fileName) {

        BatchData job = batchService.findByInstanceId(instanceId);
        if (job == null) {
            throw new WebApplicationException(404);

        } else if (job instanceof BatchFileData) {
            BatchFileData fileData = (BatchFileData) job;
            if (fileData.getBatchFilePath() == null) {
                throw new WebApplicationException(404);
            }



        } else if (job instanceof BatchRawData) {
            BatchRawData rawData = (BatchRawData) job;
            if (rawData.getData() == null) {
                throw new WebApplicationException(404);
            }


        }
        throw new WebApplicationException(404);
    }

}
