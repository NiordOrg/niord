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
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.model.vo.DomainVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing domains.
 */
@Path("/domains")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class DomainRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;


    /** Returns all domains */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<DomainVo> getAllDomains(
            @QueryParam("lang") String lang,
            @QueryParam("keycloakState") @DefaultValue("true") boolean keycloakState) {

        // Load the domains including their keycloak state, if requested
        List<DomainVo> domains = domainService.getDomains(keycloakState ).stream()
                .map(Domain::toVo)
                .collect(Collectors.toList());

        // Sort areas and categories by language
        domains.forEach(d -> {
            if (d.getAreas() != null) {
                d.getAreas().forEach(a -> a.sortDescs(lang));
            }
            if (d.getCategories() != null) {
                d.getCategories().forEach(c -> c.sortDescs(lang));
            }
        });

        return domains;
    }


    /** Creates a new domain */
    @POST
    @Path("/domain/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public DomainVo createDomain(DomainVo domain) throws Exception {
        log.info("Creating domain " + domain);
        return domainService.createDomain(new Domain(domain), true).toVo();
    }

    /** Updates an existing domain */
    @PUT
    @Path("/domain/{clientId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public DomainVo updateDomain(@PathParam("clientId") String clientId, DomainVo domain) throws Exception {
        if (!Objects.equals(clientId, domain.getClientId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating domain " + domain);
        return domainService.updateDomain(new Domain(domain)).toVo();
    }

    /** Deletes an existing domain */
    @DELETE
    @Path("/domain/{clientId}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public void deleteDomain(@PathParam("clientId") String clientId) throws Exception {
        log.info("Deleting domain " + clientId);
        domainService.deleteDomain(clientId);
    }


    /** Creates the domain in Keycloak */
    @POST
    @Path("/keycloak")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({ "sysadmin" })
    @GZIP
    @NoCache
    public void createDomainInKeycloak(DomainVo domain) throws Exception {
        log.info("Creating Keycloak domain " + domain);
        domainService.createDomainInKeycloak(new Domain(domain));
    }


    /**
     * Imports an uploaded domains json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-domains")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("sysadmin")
    public String importDomains(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "domain-import");
    }

}
