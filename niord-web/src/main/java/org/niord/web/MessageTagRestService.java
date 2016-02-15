package org.niord.web;

import org.niord.core.util.TimeUtils;
import org.niord.model.vo.MessageTagVo;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.annotation.PostConstruct;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ws.rs.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API for accessing message tags, used for grouping a fixed set of messages.
 */
@Path("/tags")
@Singleton
@Startup
public class MessageTagRestService {

    // TODO: Test - this is a global list - not tied to a user
    List<MessageTagVo> tags = new ArrayList<>();

    /** Add dummy data */
    @PostConstruct
    void init() {
        Calendar cal = Calendar.getInstance();
        int thisYear = cal.get(Calendar.YEAR);
        for (int year = thisYear - 10; year <= thisYear; year++) {
            int weeksInYear = TimeUtils.getNumberOfWeeksInYear(year);
            for (int week = 1; week < weeksInYear; week++) {
                MessageTagVo tag = new MessageTagVo();
                tag.setCreatorId("NA");
                tag.setShared(true);
                tag.setUid(String.format("w%02d-%02d", week, year - 2000));
                tags.add(tag);
            }
        }
    }


    /** Periodically purge expired tags */
    @Schedule(persistent = false, second = "10", minute = "14", hour = "*", dayOfWeek = "*", year = "*")
    void purgeExpiredTags() {
        Date now = new Date();
        tags.removeIf(t -> t.getExpiryDate() != null && t.getExpiryDate().after(now));
    }


    /** Returns all message tags */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageTagVo> getTags() {
        return tags;
    }


    /** Returns the tags with the given IDs */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageTagVo> searchTags( @QueryParam("name")  @DefaultValue("")     String name,
                                          @QueryParam("limit") @DefaultValue("1000") int limit) {
        return tags.stream()
                .filter(t -> name.isEmpty() ||
                        t.getUid().toLowerCase().contains(name.toLowerCase()))
                .limit(limit)
                .collect(Collectors.toList());
    }


    /** Returns the tags with the given IDs */
    @GET
    @Path("/{tagUids}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageTagVo> getTags(@PathParam("tagUids") String tagUids) {
        Set<String> ids = new HashSet<>(Arrays.asList(tagUids.split(",")));
        return tags.stream()
                .filter(t -> ids.contains(t.getUid()))
                .collect(Collectors.toList());
    }


    /** Creates a new tag from the given template */
    @POST
    @Path("/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageTagVo createTag(MessageTagVo tag) {
        Objects.requireNonNull(tag);
        Objects.requireNonNull(tag.getUid());

        tag.setCreatorId("NA");
        tags.add(tag);
        return tag;
    }


    /** Deletes the tag with the given UID */
    @DELETE
    @Path("/{tagUid}")
    @GZIP
    @NoCache
    public void deleteTag(@PathParam("tagUid") String tagUid) {
        List<MessageTagVo> t = getTags(tagUid);
        if (!t.isEmpty()) {
            tags.remove(t.get(0));
        }
    }
}

