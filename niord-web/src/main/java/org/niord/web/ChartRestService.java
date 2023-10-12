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
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.locationtech.jts.geom.Geometry;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.core.chart.vo.SystemChartVo;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.user.Roles;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.GeometryVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing charts.
 */
@Path("/charts")
@RequestScoped
@Transactional
@PermitAll
public class ChartRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    ChartService chartService;


    /** Returns the charts with the given comma-separated chart numbers */
    @GET
    @Path("/search/{chartNumbers}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemChartVo> getCharts(@PathParam("chartNumbers") String chartIds,
                                   @QueryParam("limit") @DefaultValue("1000") int limit) {
        return chartService.findByChartNumbers(chartIds.split(",")).stream()
                .limit(limit)
                .map(c -> c.toVo(SystemChartVo.class))
                .collect(Collectors.toList());
    }


    /** Searches charts based on the given search parameters */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemChartVo> searchCharts(@QueryParam("name") @DefaultValue("") String name,
                                      @QueryParam("inactive") @DefaultValue("false") boolean inactive,
                                      @QueryParam("limit") @DefaultValue("1000") int limit) {
        return chartService.searchCharts(name, inactive, limit).stream()
                .map(c -> c.toVo(SystemChartVo.class))
                .collect(Collectors.toList());
    }


    /** Returns all charts up to the given limit */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemChartVo> getAllCharts(@QueryParam("limit") @DefaultValue("1000") int limit) {
        return chartService.getCharts().stream()
                .limit(limit)
                .map(c -> c.toVo(SystemChartVo.class))
                .collect(Collectors.toList());
    }

    /** Creates a new chart */
    @POST
    @Path("/chart/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public SystemChartVo createChart(SystemChartVo chartVo) throws Exception {
        log.info("Creating chart " + chartVo);
        return chartService.createChart(new Chart(chartVo)).toVo(SystemChartVo.class);
    }

    /** Updates an existing chart */
    @PUT
    @Path("/chart/{chartNumber}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public SystemChartVo updateChart(@PathParam("chartNumber") String chartNumber, SystemChartVo chartVo) throws Exception {
        if (!Objects.equals(chartNumber, chartVo.getChartNumber())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating chart " + chartVo);
        return chartService.updateChart(new Chart(chartVo)).toVo(SystemChartVo.class);
    }

    /** Deletes an existing chart */
    @DELETE
    @Path("/chart/{chartNumber}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public void deleteChart(@PathParam("chartNumber") String chartNumber) throws Exception {
        log.info("Deleting chart " + chartNumber);
        chartService.deleteChart(chartNumber);
    }


    /**
     * Imports an uploaded Charts json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-charts")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.ADMIN)
    public String importCharts(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "chart-import");
    }


    /** Computes the charts intersecting with the current message geometry **/
    @POST
    @Path("/intersecting-charts")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.EDITOR)
    @GZIP
    @NoCache
    public List<SystemChartVo> computeIntersectingCharts(FeatureCollectionVo featureCollection) {
        GeometryVo geometryVo = featureCollection.toGeometry();
        if (geometryVo == null) {
            return Collections.emptyList();
        }

        Geometry geometry = JtsConverter.toJts(geometryVo);
        return chartService.getIntersectingCharts(geometry).stream()
                .map(c -> c.toVo(SystemChartVo.class))
                .collect(Collectors.toList());

    }
}
