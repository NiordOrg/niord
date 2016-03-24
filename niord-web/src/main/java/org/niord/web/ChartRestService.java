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

import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.BatchService;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.core.repo.RepositoryService;
import org.niord.model.vo.ChartVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * REST interface for accessing charts.
 */
@Path("/charts")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class ChartRestService {

    @Context
    ServletContext servletContext;

    @Inject
    Logger log;

    @Inject
    ChartService chartService;

    @Inject
    BatchService batchService;


    /** Returns the charts with the given comma-separated chart numbers */
    @GET
    @Path("/search/{chartNumbers}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<ChartVo> getCharts(@PathParam("chartNumbers") String chartIds,
                                   @QueryParam("limit") @DefaultValue("1000") int limit) {
        return chartService.findByChartNumbers(chartIds.split(",")).stream()
                .limit(limit)
                .map(Chart::toVo)
                .collect(Collectors.toList());
    }


    /** Searches charts based on the given search parameters */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<ChartVo> searchCharts(@QueryParam("name") @DefaultValue("") String name,
                                      @QueryParam("limit") @DefaultValue("1000") int limit) {
        return chartService.searchCharts(name, limit).stream()
                .map(Chart::toVo)
                .collect(Collectors.toList());
    }


    /** Returns all charts up to the given limit */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<ChartVo> getAllCharts(@QueryParam("limit") @DefaultValue("1000") int limit) {
        return chartService.getCharts().stream()
                .limit(limit)
                .map(Chart::toVo)
                .collect(Collectors.toList());
    }

    /** Creates a new chart */
    @POST
    @Path("/chart/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public ChartVo createChart(ChartVo chartVo) throws Exception {
        log.info("Creating chart " + chartVo);
        return chartService.createChart(new Chart(chartVo)).toVo();
    }

    /** Updates an existing chart */
    @PUT
    @Path("/chart/{chartNumber}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public ChartVo updateChart(@PathParam("chartNumber") String chartNumber, ChartVo chartVo) throws Exception {
        if (!Objects.equals(chartNumber, chartVo.getChartNumber())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating chart " + chartVo);
        return chartService.updateChartData(new Chart(chartVo)).toVo();
    }

    /** Deletes an existing chart */
    @DELETE
    @Path("/chart/{chartNumber}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({ "admin" })
    @GZIP
    @NoCache
    public void deleteChart(@PathParam("chartNumber") String chartNumber) throws Exception {
        log.info("Deleting chart " + chartNumber);
        chartService.deleteChart(chartNumber);
    }


    /**
     * Imports an uploaded Charts json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-charts")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importCharts(@Context HttpServletRequest request) throws Exception {

        FileItemFactory factory = RepositoryService.newDiskFileItemFactory(servletContext);
        ServletFileUpload upload = new ServletFileUpload(factory);

        StringBuilder txt = new StringBuilder();
        upload.parseRequest(request).stream()
                .filter(item -> !item.isFormField())
                .forEach(item -> {
                    try {
                        startChartsImportBatchJob(item.getInputStream(), item.getName(), txt);
                    } catch (Exception e) {
                        String errorMsg = "Error importing charts from " + item.getName() + ": " + e;
                        log.error(errorMsg, e);
                        txt.append(errorMsg);
                    }
                });

        return txt.toString();
    }


    /**
     * Starts a chart import batch job
     * @param inputStream the chart JSON input stream
     * @param fileName the name of the file
     * @param txt a log of the import
     */
    private void startChartsImportBatchJob(InputStream inputStream, String fileName, StringBuilder txt) throws Exception {
        batchService.startBatchJobWithDataFile(
                "chart-import",
                inputStream,
                fileName,
                new Properties());

        log.info("Started 'chart-import' batch job with file " + fileName);
        txt.append("Started 'chart-import' batch job with file ").append(fileName);
    }
}
