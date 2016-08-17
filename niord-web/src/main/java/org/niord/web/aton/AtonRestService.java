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
package org.niord.web.aton;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.aton.AtonDefaultsService;
import org.niord.core.aton.AtonNode;
import org.niord.core.aton.AtonSearchParams;
import org.niord.core.aton.AtonService;
import org.niord.model.IJsonSerializable;
import org.niord.model.search.PagedSearchResultVo;
import org.niord.model.aton.AtonNodeVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST interface for accessing AtoNs.
 */
@Path("/atons")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class AtonRestService {

    @Inject
    Logger log;

    @Inject
    AtonService atonService;

    @Inject
    AtonDefaultsService atonDefaultsService;


    /** Returns the AtoN with the given comma-separated IDs */
    @GET
    @Path("/{atonUids}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonNodeVo> getAtons(@PathParam("atonUids") String atonUids) {
        return atonService.findByAtonUids(atonUids.split(",")).stream()
                .map(AtonNode::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the AtoNs within the given bounds */
    @GET
    @Path("/search-name")
    @Consumes("application/json")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<AtonNodeVo> searchAtons(
            @QueryParam("name") @DefaultValue("") String name,
            @QueryParam("maxAtonNo") @DefaultValue("1000") int maxAtonNo
    ) {
        AtonSearchParams param = new AtonSearchParams();
        param.name(name);
        param.maxSize(maxAtonNo);

        return atonService.search(param)
                .map(AtonNode::toVo)
                .getData();
    }


    /** Returns the AtoNs within the given bounds */
    @GET
    @Path("/search")
    @Consumes("application/json")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PagedSearchResultVo<AtonNodeVo> search(
            @QueryParam("name") String name,
            @QueryParam("chart") Set<String> charts,
            @QueryParam("area") Set<Integer> areas,
            @QueryParam("minLat") Double minLat,
            @QueryParam("minLon") Double minLon,
            @QueryParam("maxLat") Double maxLat,
            @QueryParam("maxLon") Double maxLon,
            @QueryParam("maxAtonNo") Integer maxAtonNo,
            @QueryParam("emptyOnOverflow") @DefaultValue("false") boolean emptyOnOverflow
    ) {

        AtonSearchParams param = new AtonSearchParams()
            .name(name)
            .extent(minLat, minLon, maxLat, maxLon)
            .chartNumbers(charts)
            .areaIds(areas)
            .emptyOnOverflow(emptyOnOverflow);
        param.maxSize(maxAtonNo);

        PagedSearchResultVo<AtonNode> atons = atonService.search(param);

        return atons.map(AtonNode::toVo);
    }

    /**
     * Returns the name of all node types where the name matches the parameter
     *
     * @param name the substring match
     * @return the name of all node types where the name matches the parameter
     */
    @GET
    @Path("/defaults/node-types")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    public List<String> getNodeTypeNames(@QueryParam("name") String name) {
        return atonDefaultsService.getNodeTypeNames(name);
    }

    /**
     * Merges the given AtoN with the tags of the node types with the given names
     *
     * @param atonNodeTypeParam the AtoN and node type names
     * @return the updated AtoN
     */
    @POST
    @Path("/defaults/merge-with-node-types")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public AtonNodeVo mergeAtonWithNodeTypes(AtonNodeTypeParam atonNodeTypeParam) throws Exception {
        AtonNode aton = new AtonNode(atonNodeTypeParam.getAton());
        atonNodeTypeParam.getNodeTypeNames()
                .forEach(nt -> atonDefaultsService.mergeAtonWithNodeTypes(aton, nt));
        return aton.toVo();
    }

    /**
     * Creates an auto-complete list for OSM tag keys, based on the current AtoN and key
     *
     * @param key the currently typed key
     * @param aton the current AtoN
     * @return the auto-complete list
     */
    @POST
    @Path("/defaults/auto-complete-key")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<String> autoCompleteKey(
            @QueryParam("key") String key,
            AtonNodeVo aton) throws Exception {

        return atonDefaultsService.computeKeysForAton(new AtonNode(aton), key, 20);
    }

    /**
     * Creates an auto-complete list for OSM tag values, based on the current AtoN, key and value
     *
     * @param key the current key
     * @param value the currently typed value
     * @param aton the current AtoN
     * @return the auto-complete list
     */
    @POST
    @Path("/defaults/auto-complete-value")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<String> autoCompleteValue(
            @QueryParam("key") String key,
            @QueryParam("value") String value,
            AtonNodeVo aton) throws Exception {

        return atonDefaultsService.getValuesForAtonAndKey(new AtonNode(aton), key, value, 20);
    }


    /*************************/
    /** Helper classes      **/
    /*************************/


    /** Encapsulates the parameters uses for merging an AtoN with the node type tags */
    @SuppressWarnings("unused")
    public static class AtonNodeTypeParam implements IJsonSerializable {
        AtonNodeVo aton;
        List<String> nodeTypeNames = new ArrayList<>();

        public AtonNodeVo getAton() {
            return aton;
        }

        public void setAton(AtonNodeVo aton) {
            this.aton = aton;
        }

        public List<String> getNodeTypeNames() {
            return nodeTypeNames;
        }

        public void setNodeTypeNames(List<String> nodeTypeNames) {
            this.nodeTypeNames = nodeTypeNames;
        }
    }
}
