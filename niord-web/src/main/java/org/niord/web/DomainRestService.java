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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.user.UserService;
import org.niord.model.vo.DomainVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST interface for accessing domains.
 */
@Path("/domains")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class DomainRestService {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;

    @Inject
    UserService userService;

    @Context
    HttpRequest request;


    /** Returns all domains */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<DomainVo> getAllDomains() {

        return domainService.getDomains().stream()
                .map(Domain::toVo)
                .collect(Collectors.toList());
    }


    /** Returns all domains as a javascript */
    @GET
    @Path("/all.js")
    @Produces("application/javascript;charset=UTF-8")
    @GZIP
    public String getAllDomainsAsJavascript() {

        StringBuilder js = new StringBuilder()
                .append("niordDomains = ");
        try {
            ObjectMapper mapper = new ObjectMapper();
            js.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(getAllDomains()));
        } catch (JsonProcessingException e) {
            js.append("[]");
        }

        js.append(";\n");
        return js.toString();
    }


    /** Returns the domains for the current user */
    @GET
    @Path("/user-domains")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    public List<DomainVo> getUserDomains() {

        Set<String> clientIds = userService.getUserKeycloakClientIds(request);

        return domainService.getDomains().stream()
                .filter(d -> clientIds.contains(d.getClientId()))
                .map(Domain::toVo)
                .collect(Collectors.toList());
    }

}
