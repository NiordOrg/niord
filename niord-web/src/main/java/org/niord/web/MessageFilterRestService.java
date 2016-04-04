package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.message.MessageFilter;
import org.niord.core.message.MessageFilterService;
import org.niord.model.vo.MessageFilterVo;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows an authenticated user to fetch and save message list filters.
 */
@Path("/filters")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed({ "editor" })
public class MessageFilterRestService {

    @Inject
    Logger log;

    @Inject
    MessageFilterService messageFilterService;


    /** Returns all message list filters */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageFilterVo> getMessageFilters() {
        return messageFilterService.getMessageFiltersForUser().stream()
                .map(MessageFilter::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the filter with the given ID */
    @GET
    @Path("/{filterId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageFilterVo getMessageFilter(@PathParam("filterId") Integer filterId) {
        MessageFilter filter = messageFilterService.findById(filterId);
        return filter == null ? null : filter.toVo();
    }


    /** Creates a new filter from the given template */
    @POST
    @Path("/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageFilterVo createMessageFilter(MessageFilterVo filter) {
        log.info("Creating new message filter " + filter);
        return messageFilterService.createOrUpdateMessageFilter(new MessageFilter(filter)).toVo();
    }


    /** Deletes the filter with the given ID */
    @DELETE
    @Path("/{filterId}")
    @GZIP
    @NoCache
    public void deleteMessageFilter(@PathParam("filterId") Integer filterId) {
        log.info("Deleting message filter " + filterId);
        messageFilterService.deleteMessageFilter(filterId);
    }
}

