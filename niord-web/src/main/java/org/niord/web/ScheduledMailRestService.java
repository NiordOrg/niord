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

package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.mail.ScheduledMail;
import org.niord.core.mail.ScheduledMailSearchParams;
import org.niord.core.mail.ScheduledMailService;
import org.niord.core.mail.vo.ScheduledMailVo;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;

/**
 * REST interface for searching scheduled e-mails.
 */
@Path("/scheduled-mails")
@Stateless
@SecurityDomain("keycloak")
@SuppressWarnings("unused")
public class ScheduledMailRestService {

    @Inject
    Logger log;

    @Inject
    ScheduledMailService scheduledMailService;


    /**
     * Searches scheduled mails based on the given search parameters
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public PagedSearchResultVo<ScheduledMailVo> search(
            @QueryParam("recipient") String recipient,
            @QueryParam("status") ScheduledMail.Status status,
            @QueryParam("from") Long from,
            @QueryParam("to") Long to,
            @QueryParam("maxSize") @DefaultValue("100") int maxSize,
            @QueryParam("page") @DefaultValue("0") int page) {

        ScheduledMailSearchParams params = new ScheduledMailSearchParams()
                .recipient(recipient)
                .status(status)
                .from(from)
                .to(to);
        params.maxSize(maxSize).page(page);

        DataFilter dataFilter = DataFilter.get();
        return scheduledMailService.search(params)
                .map(m -> m.toVo(dataFilter));
    }


    /**
     * Returns the scheduled mail with the given ID
     */
    @GET
    @Path("/scheduled-mail/{scheduledMailId}")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public ScheduledMailVo getScheduledMail(@PathParam("scheduledMailId") Integer scheduledMailId) throws Exception {
        ScheduledMail mail = scheduledMailService.getScheduledMail(scheduledMailId);
        if (mail == null) {
            throw new WebApplicationException(404);
        }
        return mail.toVo(DataFilter.get().fields(DataFilter.DETAILS));
    }

}
