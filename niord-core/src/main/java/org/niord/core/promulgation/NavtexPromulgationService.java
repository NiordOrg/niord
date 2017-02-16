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

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.area.Area;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.NavtexPromulgationVo;
import org.niord.core.promulgation.vo.NavtexTransmitterVo;
import org.niord.core.promulgation.vo.PromulgationServiceDataVo;
import org.niord.core.user.Roles;
import org.niord.core.util.TextUtils;
import org.niord.model.DataFilter;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
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
@PermitAll
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
    public void onLoadSystemMessage(SystemMessageVo message) throws PromulgationException {
        NavtexPromulgationVo navtex = message.promulgation(NavtexPromulgationVo.class, getType());
        if (navtex == null) {
            navtex = new NavtexPromulgationVo();
            message.getPromulgations().add(navtex);
        }

        // Add all active transmitters not already added
        for (NavtexTransmitter transmitter : findTransmittersByAreas(null, true)) {
            if (!navtex.getTransmitters().containsKey(transmitter.getName())) {
                navtex.getTransmitters().put(transmitter.getName(), Boolean.FALSE);
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public BasePromulgation<?> generateMessagePromulgation(Message message) throws PromulgationException {
        NavtexPromulgation navtex = new NavtexPromulgation();

        navtex.setPromulgate(true);

        // Add all active transmitters - by default, not selected
        findTransmittersByAreas(null, true)
                .forEach(t -> navtex.getTransmitters().put(t.getName(), Boolean.FALSE));

        // Select transmitters associated with the current message areas
        if (!message.getAreas().isEmpty()) {
            findTransmittersByAreas(message.getAreas(), true)
                    .forEach(t -> navtex.getTransmitters().put(t.getName(), Boolean.TRUE));
        }

        StringBuilder text = new StringBuilder("NAVTEX:\n");
        message.getParts().stream()
            .flatMap(p -> p.getDescs().stream())
            .filter(d -> d.getLang().equals("en"))
            .filter(d -> StringUtils.isNotBlank(d.getDetails()))
            .map(d -> TextUtils.html2txt(d.getDetails()))
            .forEach(d -> text.append(d.toUpperCase()).append("\n"));
        navtex.setText(text.toString());

        return navtex;
    }


    /** {@inheritDoc} */
    @Override
    public PromulgationServiceDataVo updatePromulgationService(PromulgationServiceDataVo serviceData) {
        // NB: Override because the function is protected
        return super.updatePromulgationService(serviceData);
    }

    /***************************************/
    /** Transmitter Handling              **/
    /***************************************/


    /** Returns the transmitter with the given name, or null if not found **/
    public NavtexTransmitter findTransmitterByName(String name) {
        try {
            return em.createNamedQuery("NavtexTransmitter.findByName", NavtexTransmitter.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Find all NAVTEX transmitters matching the given areas.
     * @param areas the areas to match
     * @return all NAVTEX transmitters matching the given areas.
     */
    @SuppressWarnings("all")
    public List<NavtexTransmitter> findTransmittersByAreas(List<Area> areas, boolean onlyActive) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<NavtexTransmitter> query = cb.createQuery(NavtexTransmitter.class);
        Root<NavtexTransmitter> transmitterRoot = query.from(NavtexTransmitter.class);

        CriteriaHelper<NavtexTransmitter> criteriaHelper = new CriteriaHelper<>(cb, query);

        if (onlyActive) {
            criteriaHelper.equals(transmitterRoot.get("active"), Boolean.TRUE);
        }


        if (areas != null && !areas.isEmpty()) {

            // Make sure we have managed entities, not template antities
            areas = persistedList(Area.class, areas);

            Join<NavtexTransmitter, Area> areaJoin = transmitterRoot.join("areas", JoinType.LEFT);
            Predicate[] areaMatch = areas.stream()
                    .map(a -> cb.like(areaJoin.get("lineage"), a.getLineage() + "%"))
                    .toArray(Predicate[]::new);
            criteriaHelper.add(cb.or(areaMatch));
        }

        query.select(transmitterRoot)
                .where(criteriaHelper.where())
                .orderBy(cb.asc(cb.lower(transmitterRoot.get("name"))));

        return em.createQuery(query).getResultList();
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
        NavtexTransmitter original = findTransmitterByName(name);
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
        NavtexTransmitter original = findTransmitterByName(name);
        remove(original);
    }

}
