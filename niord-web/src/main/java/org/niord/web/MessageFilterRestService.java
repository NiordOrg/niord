package org.niord.web;

import org.niord.model.vo.MessageFilterVo;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Allows an authenticated user to fetch and save message list filters.
 */
@Path("/filters")
@Singleton
@Startup
public class MessageFilterRestService {

    // TODO: Test - this is a global list - not tied to a user
    List<MessageFilterVo> filters = new ArrayList<>();

    /** Returns all message list filters */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageFilterVo> getFilters() {
        return filters;
    }

    /** Returns the filter with the given ID */
    @GET
    @Path("/{filterId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageFilterVo getFilter(@PathParam("filterId") Long filterId) {
        return filters.stream()
                .filter(f -> f.getId().equals(filterId))
                .findFirst()
                .orElse(null);
    }

    /** Creates a new filter from the given template */
    @POST
    @Path("/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageFilterVo createFilter(MessageFilterVo filter) {
        Objects.requireNonNull(filter);
        Objects.requireNonNull(filter.getName());
        Objects.requireNonNull(filter.getParameters());

        filter.setId(System.currentTimeMillis());
        filters.add(filter);
        return filter;
    }

    /** Deletes the filter with the given ID */
    @DELETE
    @Path("/{filterId}")
    @GZIP
    @NoCache
    public void deleteFilter(@PathParam("filterId") Long filterId) {
        MessageFilterVo filter = getFilter(filterId);
        if (filter != null) {
            filters.remove(filter);
        }
    }
}

