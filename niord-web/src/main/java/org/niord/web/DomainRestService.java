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
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.domain.vo.DomainVo;
import org.niord.core.user.Roles;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing domains.
 */
@Path("/domains")
@RequestScoped
@Transactional
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
            @QueryParam("inactive") @DefaultValue("false") boolean includeInactive,
            @QueryParam("keycloakState") @DefaultValue("true") boolean keycloakState) {

        // Load the domains including their keycloak state, if requested
        List<DomainVo> domains = domainService.getDomains(includeInactive, keycloakState ).stream()
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
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public DomainVo createDomain(DomainVo domain) throws Exception {
        log.info("Creating domain " + domain);
        return domainService.createDomain(new Domain(domain), true).toVo();
    }


    /** Updates an existing domain */
    @PUT
    @Path("/domain/{domainId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public DomainVo updateDomain(@PathParam("domainId") String domainId, DomainVo domain) throws Exception {
        if (!Objects.equals(domainId, domain.getDomainId())) {
            throw new WebApplicationException(400);
        }

        log.info("Updating domain " + domain);
        return domainService.updateDomain(new Domain(domain)).toVo();
    }

    /** Deletes an existing domain */
    @DELETE
    @Path("/domain/{domainId}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public void deleteDomain(@PathParam("domainId") String domainId) throws Exception {
        log.info("Deleting domain " + domainId);
        domainService.deleteDomain(domainId);
    }


    /** Creates the domain in Keycloak */
    @POST
    @Path("/keycloak")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public void createDomainInKeycloak(DomainVo domain) throws Exception {
        log.info("Creating Keycloak domain " + domain);
        domainService.createDomainInKeycloak(new Domain(domain));
    }


    /**
     * Imports an uploaded domains json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-domains")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importDomains(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "domain-import");
    }

}
