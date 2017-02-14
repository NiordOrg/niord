/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.promulgation;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.area.Area;
import org.niord.core.message.Message;
import org.niord.core.promulgation.vo.NavtexTransmitterVo;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;

import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manages NAVTEX-via-mailing-lists promulgations
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SecurityDomain("keycloak")
@Path("/promulgation/navtex")
@SuppressWarnings("unused")
public class NavtexPromulgationService extends BasePromulgationService {

    public static final int PRIORITY = 1;

    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/

    /**
     * Registers the promulgation service with the promulgation manager
     */
    @PostConstruct
    public void init() {
        registerPromulgationService();
    }


    /** {@inheritDoc} */
    @Override
    public String getType() {
        return NavtexPromulgation.TYPE;
    }


    /** {@inheritDoc} */
    @Override
    public int getDefaultPriority() {
        return PRIORITY;
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public void onNewTemplateMessage(Message message) {
        NavtexPromulgation navtex = new NavtexPromulgation();
        navtex.getTransmitters().put("Baltico", Boolean.FALSE);
        navtex.getTransmitters().put("Rogaland", Boolean.FALSE);
        message.getPromulgations().add(navtex);
    }


    /** {@inheritDoc} */
    @Override
    public void onCreateMessage(Message message) {
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessage(Message message) {
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessageStatus(Message message) {
    }


    /***************************************/
    /** Transmitter Handling              **/
    /***************************************/


    /** Returns the transmitter with the given name, or null if not found **/
    public NavtexTransmitter findByName(String name) {
        try {
            return em.createNamedQuery("NavtexTransmitter.findByName", NavtexTransmitter.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /** Returns all transmitters */
    @GET
    @Path("/transmitters/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @RolesAllowed(Roles.SYSADMIN)
    @NoCache
    public List<NavtexTransmitterVo> getTransmitters(@QueryParam("lang") @DefaultValue("en") String lang) {
        DataFilter filter = DataFilter.get().lang(lang);
        return getAll(NavtexTransmitter.class).stream()
                .map(t -> t.toVo(filter))
                .collect(Collectors.toList());
    }

    /** Creates a new transmitter */
    @POST
    @Path("/transmitters/transmitter/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public NavtexTransmitterVo createTransmitter(NavtexTransmitterVo transmitter) throws Exception {
        log.info("Creating transmitter " + transmitter);
        NavtexTransmitter t = new NavtexTransmitter(transmitter);
        t.setAreas(persistedList(Area.class, t.getAreas()));
        return saveEntity(t).toVo(DataFilter.get());
    }


    /** Updates an existing transmitter */
    @PUT
    @Path("/transmitters/transmitter/{name}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public NavtexTransmitterVo updateTransmitter(@PathParam("name") String name, NavtexTransmitterVo transmitter) throws Exception {
        if (!Objects.equals(name, transmitter.getName())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating transmitter " + transmitter);
        NavtexTransmitter t = new NavtexTransmitter(transmitter);
        NavtexTransmitter original = findByName(name);
        original.setActive(transmitter.isActive());
        original.setAreas(persistedList(Area.class, t.getAreas()));
        return saveEntity(original).toVo(DataFilter.get());
    }


    /** Deletes an existing transmitter */
    @DELETE
    @Path("/transmitters/transmitter/{name}")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public void deleteTransmitter(@PathParam("name") String name) throws Exception {
        log.info("Deleting transmitter " + name);
        NavtexTransmitter original = findByName(name);
        remove(original);
    }

}
