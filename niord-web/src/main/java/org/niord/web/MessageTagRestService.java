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

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagSearchParams;
import org.niord.core.message.MessageTagService;
import org.niord.core.message.vo.MessageTagVo;
import org.niord.model.search.PagedSearchParamsVo.SortOrder;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API for accessing message tags, used for grouping a fixed set of messages.
 */
@Path("/tags")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class MessageTagRestService {

    @Inject
    Logger log;

    @Inject
    MessageTagService messageTagService;


    /** Returns the tags with the given IDs */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageTagVo> searchTags(
            @QueryParam("name")  @DefaultValue("") String name,
            @QueryParam("type") Set<MessageTagVo.MessageTagType> types,
            @QueryParam("sortBy") @DefaultValue("name") String sortBy,
            @QueryParam("sortOrder") @DefaultValue("ASC") SortOrder sortOrder,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        MessageTagSearchParams params = new MessageTagSearchParams();
        params.name(name)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .maxSize(limit);
        if (types != null) {
            params.types(types);
        }

        return messageTagService.searchMessageTags(params).stream()
                .map(MessageTag::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the tags which contain the message with the given ID */
    @GET
    @Path("/message/{messageUid}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageTagVo> findTagsByMessageId(@PathParam("messageUid") String messageUid) {
        return messageTagService.findTagsByMessageId(messageUid).stream()
                .map(MessageTag::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the tags with the given IDs */
    @GET
    @Path("/tag/{tagIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageTagVo> getTags(@PathParam("tagIds") String tagIds) {
        return messageTagService.findTags(tagIds.split(",")).stream()
                .map(MessageTag::toVo)
                .collect(Collectors.toList());
    }


    /** Creates a new tag from the given template */
    @POST
    @Path("/tag/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageTagVo createTag(MessageTagVo tag) {
        return messageTagService.createMessageTag(new MessageTag(tag)).toVo();
    }


    /** Updates a tag from the given template */
    @PUT
    @Path("/tag/{tagId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageTagVo updateTag(@PathParam("tagId") String tagId, MessageTagVo tag) {
        if (!Objects.equals(tagId, tag.getTagId())) {
            throw new WebApplicationException(400);
        }
        return messageTagService.updateMessageTag(new MessageTag(tag)).toVo();
    }


    /** Deletes the tag with the given ID */
    @DELETE
    @Path("/tag/{tagId}")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public boolean deleteTag(@PathParam("tagId") String tagId) {
        log.info("Deleting tag " + tagId);
        return messageTagService.deleteMessageTag(tagId);
    }


    /** Unlocks the tag with the given ID */
    @PUT
    @Path("/unlock-tag/{tagId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageTagVo unlockTag(@PathParam("tagId") String tagId) {
        MessageTag tag = messageTagService.findTag(tagId);
        if (tag.isLocked()) {
            tag.setLocked(false);
            messageTagService.updateMessageTag(tag);
            log.info("Unlocked tag " + tagId);
        }
        return tag.toVo();
    }


    /** Creates a new tag from the given template */
    @POST
    @Path("/temp-tag/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageTagVo createTempTag(List<String> messageUids) {
        // TODO: Validate access to the messages for the current user
        return messageTagService.createTempMessageTag(messageUids).toVo();
    }


    /** Clears messages from the given tag */
    @DELETE
    @Path("/tag/{tagId}/messages")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public boolean clearTag(@PathParam("tagId") String tagId) {
        log.info("Clearing tag " + tagId);
        return messageTagService.clearMessageTag(tagId);
    }


    /** Adds messages to the given tag */
    @PUT
    @Path("/tag/{tagId}/add-messages")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageTagVo addMessageToTag(@PathParam("tagId") String tagId, List<String> messageUids) {
        log.info("Adding messages " + messageUids + " to tag " + tagId);

        // TODO: Validate access to the messages for the current user
        return messageTagService.addMessageToTag(tagId, messageUids).toVo();
    }


    /** Removes messages from the given tag */
    @PUT
    @Path("/tag/{tagId}/remove-messages")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public MessageTagVo removeMessageFromTag(@PathParam("tagId") String tagId, List<String> messageUids) {
        log.info("Removing messages " + messageUids + " from tag " + tagId);

        // TODO: Validate access to the messages for the current user
        return messageTagService.removeMessageFromTag(tagId, messageUids).toVo();
    }

}

