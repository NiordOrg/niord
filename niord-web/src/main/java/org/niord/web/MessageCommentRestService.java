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
import org.niord.core.NiordApp;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.mail.Mail.MailRecipient;
import org.niord.core.message.Comment;
import org.niord.core.message.CommentService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageMailService;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.CommentVo;
import org.niord.core.user.Roles;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.model.message.MessageVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
    Logger log;

    @Inject
    NiordApp app;

    @Inject
    MessageService messageService;

    @Inject
    UserService userService;

    @Inject
    CommentService commentService;

    @Inject
    MessageMailService messageMailService;

    @Inject
    DictionaryService dictionaryService;


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
    @RolesAllowed(Roles.USER)
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
    @RolesAllowed(Roles.USER)
    public CommentVo createComment(
            @PathParam("messageId") String messageId,
            @QueryParam("lang") String lang,
            CommentVo comment) {

        // Get the message by message id
        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            throw new IllegalArgumentException("Invalid message ID " + messageId);
        }

        comment =  commentService
                .createComment(new Comment(comment, message, userService.currentUser()))
                .toVo();

        // Check if we need to send the comment as e-mail
        checkSendCommentEmails(comment, message, lang);

        return comment;
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
    @RolesAllowed(Roles.USER)
    public CommentVo updateComment(
            @PathParam("messageId") String messageId,
            @PathParam("commentId") Integer commentId,
            @QueryParam("lang") String lang,
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
        comment = commentService
                .updateComment(new Comment(comment, message, userService.currentUser()))
                .toVo();

        // Check if we need to send the comment as e-mail
        checkSendCommentEmails(comment, message, lang);

        return comment;
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
    @RolesAllowed(Roles.USER)
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


    /**
     * Check if the comment needs to be sent to the associated e-mail addresses
     * @param comment the comment
     * @param message the message associated with the comment
     */
    private void checkSendCommentEmails(CommentVo comment, Message message, String language) {
        if (comment.getEmailAddresses() != null && !comment.getEmailAddresses().isEmpty()) {

            try {
                String lang = app.getLanguage(language);

                List<MailRecipient> recipients = comment.getEmailAddresses().stream()
                        .map(email -> {
                            try {
                                return new MailRecipient(RecipientType.TO, new InternetAddress(email));
                            } catch (AddressException e) {
                                log.warn("Skipping comment e-mail for address " + email + ": " + e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                MessageVo messageVo = message.toVo(MessageVo.class, Message.MESSAGE_DETAILS_FILTER.lang(lang));

                String subject = dictionaryService.value("mail", lang, "mail.comment.subject")
                        + " " + comment.getUser();

                // Send the e-mail
                MessageMailService.MessageMailTemplate mailTemplate = new MessageMailService.MessageMailTemplate()
                        .subject(subject)
                        .mailMessage(comment.getComment())
                        .messages(Collections.singletonList(messageVo))
                        .recipients(recipients)
                        .language(lang)
                        .templatePath("/templates/messages/message-mail.ftl");
                messageMailService.sendMessageMailAsync(mailTemplate);

            } catch (Exception e) {
                log.error("Failed sending comment e-mail to users " + comment.getEmailAddresses()
                        + ": " + e.getMessage());
            }


        }
    }
}
