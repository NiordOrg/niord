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
import org.niord.core.mail.MailingList;
import org.niord.core.mail.MailingListSearchParams;
import org.niord.core.mail.MailingListService;
import org.niord.core.mail.vo.MailingListVo;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
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
 * REST interface for accessing mailing lists.
 */
@Path("/mailing-lists")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.ADMIN)
public class MailingListRestService {

    @Inject
    Logger log;

    @Inject
    MailingListService mailingListService;


    /** Returns all mailing lists that matches the search criteria */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MailingListVo> search(
            @QueryParam("name") String name,
            @QueryParam("lang") @DefaultValue("en") String lang) {

        MailingListSearchParams params = new MailingListSearchParams()
                .language(lang)
                .name(name);

        DataFilter filter = MailingList.MESSAGE_DETAILS_FILTER.lang(lang);
        
        return mailingListService.searchMailingLists(params).stream()
                .map(m -> m.toVo(filter))
                .collect(Collectors.toList());
    }


    /**
     * Returns the details of the mailing list with the given id
     * @param mailingListId the ID of the mailing list
     */
    @GET
    @Path("/mailing-list/{mailingListId}")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public MailingListVo getMailingListDetails(@PathParam("mailingListId") String mailingListId) throws Exception {

        log.info("Returning details of mailing list " + mailingListId);
        MailingList mailingList = mailingListService.findByMailingListId(mailingListId);
        if (mailingList == null) {
            throw new WebApplicationException("Mailing list " + mailingListId + " not found", 404);
        }
        return mailingList.toVo(MailingList.MESSAGE_DETAILS_AND_RECIPIENTS_FILTER);
    }


    /**
     * Creates a new mailing list
     * @param mailingList the template mailing list to add
     */
    @POST
    @Path("/mailing-list/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public MailingListVo addMailingList(MailingListVo mailingList) throws Exception {
        log.info("Creating mailing list " + mailingList.getMailingListId());
        return mailingListService.createMailingList(new MailingList(mailingList)).toVo(DataFilter.get());
    }


    /**
     * Updates the mailing list  with the given id
     * @param mailingListId the ID of the mailing list
     * @param mailingList the template mailing list to update
     */
    @PUT
    @Path("/mailing-list/{mailingListId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public MailingListVo updateMailingList(
            @PathParam("mailingListId") String mailingListId,
            MailingListVo mailingList) throws Exception {

        if (!Objects.equals(mailingListId, mailingList.getMailingListId())) {
            throw new WebApplicationException(400);
        }
        log.info("Updating mailing list " + mailingList.getMailingListId());
        return mailingListService.updateMailingList(new MailingList(mailingList)).toVo(DataFilter.get());
    }


    /**
     * Deletes the mailing list with the given id
     * @param mailingListId the mailingList to delete
     */
    @DELETE
    @Path("/mailing-list/{mailingListId}")
    @NoCache
    public void deleteMailingList(@PathParam("mailingListId") String mailingListId) throws Exception {
        log.info("Deleting mailing list " + mailingListId);
        mailingListService.deleteMailingList(mailingListId);
    }

}
