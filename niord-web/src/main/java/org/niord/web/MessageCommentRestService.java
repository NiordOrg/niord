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
import org.niord.core.message.Comment;
import org.niord.core.message.CommentService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.CommentVo;
import org.niord.core.user.User;
import org.niord.core.user.UserService;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for managing message comments.
 */
@Path("/messages")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
@SuppressWarnings("unused")
public class MessageCommentRestService {

    @Inject
    MessageService messageService;

    @Inject
    UserService userService;

    @Inject
    CommentService commentService;


    /**
     * Returns the comments for the given message ID
     * @param messageId the message ID or message series ID
     * @return the message comments
     */
    @GET
    @Path("/message/{messageId}/comments")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public List<CommentVo> getComments(@PathParam("messageId") String messageId) {

        // Get the message id
        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return Collections.emptyList();
        }

        User user = userService.currentUser();
        return commentService.getComments(message.getUid()).stream()
                .map(c -> c.toVo(user))
                .collect(Collectors.toList());
    }


    /**
     * Creates a comments for the given message ID
     * @param messageId the message ID or message series ID
     * @param comment the template comment to create
     * @return the persisted comment
     */
    @POST
    @Path("/message/{messageId}/comment")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    @RolesAllowed({"editor"})
    public CommentVo createComment(@PathParam("messageId") String messageId, CommentVo comment) {

        // Get the message by message id
        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            throw new IllegalArgumentException("Invalid message ID " + messageId);
        }

        return commentService
                .createComment(new Comment(comment, message, userService.currentUser()))
                .toVo();
    }


    /**
     * Updates a comments for the given message ID
     * @param messageId the message ID or message series ID
     * @param commentId the message ID or message series ID
     * @param comment the template comment to updated
     * @return the message comments
     */
    @PUT
    @Path("/message/{messageId}/comment/{commentId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    @RolesAllowed({"editor"})
    public CommentVo updateComment(@PathParam("messageId") String messageId, @PathParam("commentId") Integer commentId,
                                   CommentVo comment) {
        // Enforce REST PUT pattern
        if (!Objects.equals(commentId, comment.getId())) {
            throw new WebApplicationException(400);
        }
        // Get the message by message id
        Message message = messageService.resolveMessage(messageId);
        if (message == null || message.getComments().stream().noneMatch(c -> c.getId().equals(commentId))) {
            throw new IllegalArgumentException("Invalid message or comment ID " + messageId);
        }
        return commentService
                .updateComment(new Comment(comment, message, userService.currentUser()))
                .toVo();
    }


    /**
     * Acknowledges a comments for the given message ID
     * @param messageId the message ID or message series ID
     * @param commentId the message ID or message series ID
     * @return the message comment
     */
    @PUT
    @Path("/message/{messageId}/comment/{commentId}/ack")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    @RolesAllowed({"editor"})
    public CommentVo acknowledgeComment(@PathParam("messageId") String messageId, @PathParam("commentId") Integer commentId) {
        // Get the message by message id
        Message message = messageService.resolveMessage(messageId);
        if (message == null || message.getComments().stream().noneMatch(c -> c.getId().equals(commentId))) {
            throw new IllegalArgumentException("Invalid message or comment ID " + messageId);
        }
        return commentService
                .acknowledgeComment(userService.currentUser(), commentId)
                .toVo();
    }

}
