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

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.FeatureService;
import org.niord.core.keycloak.KeycloakIntegrationService;
import org.niord.model.geojson.FeatureCollectionVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test.
 */
@Path("/test")
@Singleton
@Startup
@SecurityDomain("keycloak")
@PermitAll
public class TestRestService {

    @Inject
    Logger log;

    @Inject
    FeatureService featureService;

    @Inject
    KeycloakIntegrationService keycloakIntegrationService;

    @GET
    @Path("/shapes")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<FeatureCollectionVo> getAllFeatureCollections() throws Exception {
        return featureService.loadAllFeatureCollections().stream()
                .map(FeatureCollection::toGeoJson)
                .collect(Collectors.toList());
    }

    @POST
    @Path("/shapes")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FeatureCollectionVo createFeatureCollection(FeatureCollectionVo fc) throws Exception {
        log.info("Creating feature collection " + fc);
        return featureService.createFeatureCollection(FeatureCollection.fromGeoJson(fc))
                .toGeoJson();
    }

    @PUT
    @Path("/shapes")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FeatureCollectionVo updateFeatureCollection(FeatureCollectionVo fc) throws Exception {
        log.info("Updating feature collection " + fc);
        return featureService.updateFeatureCollection(FeatureCollection.fromGeoJson(fc))
                .toGeoJson();
    }

    @GET
    @Path("/xxx")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public Map<String, String> test() throws Exception {

        return keycloakIntegrationService.createKeycloakDeploymentForWebApp();
    }

}
