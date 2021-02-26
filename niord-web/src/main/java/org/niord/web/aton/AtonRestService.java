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

import org.jboss.ejb3.annotation.SecurityDomain;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.aton.AtonDefaultsService;
import org.niord.core.aton.AtonNode;
import org.niord.core.aton.AtonSearchParams;
import org.niord.core.aton.AtonService;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.user.Roles;
import org.niord.model.IJsonSerializable;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.*;

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
     * Returns the AtoN with the given AtoN UID, which is the seamark reference
     *
     * If no AtoN node exists with the given UID, null is returned.
     *
     * @param atonUid the AtoN UID
     * @return the AtoN or null
     */
    @GET
    @Path("/aton/{atonUid}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public AtonNodeVo getAton(@PathParam("atonUid") String atonUid) throws Exception {

        AtonNode atonNode = atonService.findByAtonUid(atonUid);

        // Check for nulls
        if (atonNode == null) {
            return null;
        }

        // Return the VO object
        return atonNode.toVo();
    }

    /**
     * Creates a new AtoN node.
     *
     * @param aton the AtoN to create
     * @return the persisted AtoN
     */
    @POST
    @Path("/aton")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public AtonNodeVo createAton(AtonNodeVo aton) {
        // Reconstruct the internal AtoN node object
        AtonNode atonNode = new AtonNode(aton);

        log.debug("Creating aton " + aton);

        try {
            atonNode = atonService.createAton(atonNode);
        } catch (Exception ex) {
            throw new WebApplicationException(ex.getMessage(), 400);
        }

        return atonNode.toVo();
    }


    /**
     * Updates an AtoN node.
     *
     * @param atonUid the UID of the AtoN to be updated
     * @param aton the AtoN to update
     * @return the updated AtoN
     */
    @PUT
    @Path("/aton/{atonUid}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public AtonNodeVo updateAton(@PathParam("atonUid") String atonUid, AtonNodeVo aton) {
        // Reconstruct the internal AtoN node object
        AtonNode atonNode = new AtonNode(aton);

        log.debug("Updating aton " + aton);

        try {
            atonNode = atonService.updateAton(atonNode);
        } catch (Exception ex) {
            throw new WebApplicationException(ex.getMessage(), 400);
        }

        return atonNode.toVo();
    }

    /**
     * Delete an AtoN node.
     *
     * @param atonUid the UID of the AtoN to be updated
     * @return the outcome of the operation
     */
    @DELETE
    @Path("/aton/{atonUid}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public boolean deleteAton(@PathParam("atonUid") String atonUid) {
        log.debug("Deleting aton with UID" + atonUid);

        try {
            return atonService.deleteAton(atonUid);
        } catch (Exception ex) {
            throw new WebApplicationException(ex.getMessage(), 400);
        }
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
