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

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.NiordApp;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.geojson.FeatureService;
import org.niord.core.geojson.GeometryFormatService;
import org.niord.core.geojson.PlainTextConverter;
import org.niord.core.message.EditorFieldsService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageHistory;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.MessageHistoryVo;
import org.niord.core.message.vo.MessagePublicationVo;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.PromulgationManager;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationService;
import org.niord.core.publication.PublicationUtils;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.core.repo.FileTypes;
import org.niord.core.repo.RepositoryService;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.niord.core.util.WebUtils;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.GeoJsonVo;
import org.niord.model.message.AttachmentVo;
import org.niord.model.message.MainType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessagePartVo;
import org.niord.model.message.MessageVo;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.ReferenceVo;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.message.vo.MessageTagVo.MessageTagType.PUBLIC;
import static org.niord.model.message.Status.*;

/**
 * REST interface for managing messages.
 */
@Path("/messages")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
@SuppressWarnings("unused")
public class MessageRestService  {

    enum EditOp { VIEW, UPDATE, CHANGE_STATUS }

    @Inject
    Logger log;

    @Inject
    FileTypes fileTypes;

    @Inject
    NiordApp app;

    @Inject
    RepositoryService repositoryService;

    @Inject
    MessageService messageService;

    @Inject
    DomainService domainService;

    @Inject
    UserService userService;

    @Inject
    FeatureService featureService;

    @Inject
    GeometryFormatService geometryFormatService;

    @Inject
    EditorFieldsService editorFieldsService;

    @Inject
    PublicationService publicationService;

    @Inject
    PromulgationManager promulgationManager;

    /***************************
     * Message access functions
     ***************************/

    /**
     * Checks that the user has editing access to the given message by matching the current domain
     * with the message series of the message.<br>
     * Throws a 403 response code error if no access.
     * @param editOp the type of edit operation
     * @param message the message
     */
    private void checkMessageEditingAccess(Message message, EditOp editOp) {
        Domain domain = domainService.currentDomain();

        if (domain == null || message == null || !domain.containsMessageSeries(message.getMessageSeries())) {
            throw new WebApplicationException(403);
        }

        if (editOp == EditOp.VIEW && !userService.isCallerInRole(Roles.USER)) {
            throw new WebApplicationException("Only users, editors and admins can view an editable message", 403);
        }

        if (editOp == EditOp.CHANGE_STATUS && !userService.isCallerInRole(Roles.EDITOR)) {
            throw new WebApplicationException("Only editors and admins can change status of a message", 403);
        }

        if (editOp == EditOp.UPDATE && !userService.isCallerInRole(Roles.EDITOR)) {
            throw new WebApplicationException("Only editors and admins can update a message", 403);
        }

        // Extra check - only admins can edit a non-draft message
        boolean draft = message.getStatus().isDraft();
        if (editOp == EditOp.UPDATE && !draft && !userService.isCallerInRole(Roles.ADMIN)) {
            throw new WebApplicationException("Only admins can update a non-draft message", 403);
        }
    }


    /** Short-cut function that returns if the user has message editing access **/
    private boolean messageEditingAccess(Message message, EditOp editOp) {
        try {
            checkMessageEditingAccess(message, editOp);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }


    /**
     * Checks that the user has viewing access to the given message. The message must adhere to one of the following
     * criteria:
     * <ul>
     *     <li>The message is published, cancelled or expired.</li>
     *     <li>The current domain of the user matches the message series of the message.</li>
     *     <li>The message is associated with a public tag.</li>
     * </ul>
     *
     * Throws a 403 response code error if no access.
     * @param message the message
     */
    private void checkMessageViewingAccess(Message message) {
        // 1) Always grant access if the message is published, cancelled or expired
        if (message.getStatus().isPublic()) {
            return;
        }

        // 2) Grant access if the current domain of the user matches the message series of the message
        Domain domain = domainService.currentDomain();
        if (domain != null && domain.containsMessageSeries(message.getMessageSeries())
                && userService.isCallerInRole(Roles.USER)) {
            return;
        }

        // 3) Grant access if the message is associated with a public tag.
        if (message.getTags().stream()
                .anyMatch(t -> t.getType() == PUBLIC)) {
            return;
        }

        // No access
        throw new WebApplicationException(403);
    }


    /** Short-cut function that returns if the user has message viewing access **/
    private boolean messageViewingAccess(Message message) {
        try {
            checkMessageViewingAccess(message);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }


    /**
     * Computes the editor fields to use for the given message
     * @param message the message to compute the editor fields for
     */
    private void computeEditorFields(SystemMessageVo message) {
        editorFieldsService.computeEditorFields(message, userService.getUserAttributes());
    }


    /**
     * Returns the message with the given message id, which may be either a UID,
     * or a short ID of a message.
     *
     * If no message exists with the given ID, null is returned.
     *
     * @param messageId the message ID
     * @param language the language of the returned data
     * @return the message or null
     */
    @GET
    @Path("/message/{messageId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageVo getMessage(
            @PathParam("messageId") String messageId,
            @QueryParam("lang") String language
            ) throws Exception {

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return null;
        }

        // Validate access to the message
        checkMessageViewingAccess(message);

        DataFilter filter = Message.MESSAGE_DETAILS_FILTER
                .lang(language)
                .user(userService.userResolver());
        return message.toVo(MessageVo.class, filter);
    }


    /**
     * Returns a system-model version of the message, which has been associated with a temporary
     * repository folder for new changes.
     *
     * @param message the message get an system-model version of
     * @param copyToTemp whether to copy all message resources to the associated temporary directory or not
     * @return the system-model version of the message
     */
    private SystemMessageVo toSystemMessage(Message message, boolean copyToTemp) throws Exception {

        DataFilter filter = Message.MESSAGE_DETAILS_AND_PROMULGATIONS_FILTER
                .user(userService.userResolver());

        SystemMessageVo messageVo = message.toVo(SystemMessageVo.class, filter);

        // Let promulgation services adjust the promulgations of the message
        promulgationManager.onLoadSystemMessage(messageVo, false);

        // Compute the default set of editor fields to display for the message
        computeEditorFields(messageVo);

        // Create a temporary repository folder for the message
        messageService.createTempMessageRepoFolder(messageVo, copyToTemp);

        return messageVo;
    }


    /**
     * Returns the system-model message with the given message id, which may be either a UID,
     * or a short ID of a message.
     *
     * If no message exists with the given ID, null is returned.
     *
     * @param messageId the message ID
     * @return the message or null
     */
    @GET
    @Path("/editable-message/{messageId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public SystemMessageVo getSystemMessage(
            @PathParam("messageId") String messageId,
            @QueryParam("lang") String language) throws Exception {

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return null;
        }

        // Validate access to the message
        checkMessageEditingAccess(message, EditOp.VIEW);

        // Returns a system model version of the message
        SystemMessageVo msg = toSystemMessage(message, false);
        msg.sort(language);

        return msg;
    }


    /**
     * Creates a new message template with a temporary repository path
     *
     * @return the new message template
     */
    @GET
    @Path("/new-message-template")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public SystemMessageVo newTemplateMessage(
            @QueryParam("mainType") MainType mainType,
            @QueryParam("populate") @DefaultValue("false") boolean populate) throws Exception {

        log.info("Creating new message template");

        // Validate the mainType is valid in the current domain
        Domain domain = domainService.currentDomain();
        List<MessageSeries> messageSeries = domain.getMessageSeries().stream()
                .filter(ms -> ms.getMainType().equals(mainType))
                .collect(Collectors.toList());

        if (messageSeries.isEmpty()) {
            throw new WebApplicationException(403);
        }

        // Create a new template message
        Message message = messageService.newTemplateMessage(mainType);

        // If only one matching message series is defined, set it for the message.
        if (messageSeries.size() == 1) {
            message.setMessageSeries(messageSeries.get(0));
        }

        // Check if the type can be deduced from the message series
        if (message.getType() == null && message.getMessageSeries() != null
                && message.getMessageSeries().validTypes().size() == 1) {
            message.setType(message.getMessageSeries().validTypes().iterator().next());
        }

        SystemMessageVo messageVo = toSystemMessage(message, false);

        // Set whether or not to promulgate by default
        promulgationManager.setPromulgateByDefault(messageVo);

        // Check if we need to populate with empty description records
        if (populate) {
            messageVo.checkCreateDescs(app.getLanguages());
            MessagePartVo part = messageVo.checkCreatePart(MessagePartType.DETAILS);
            Arrays.stream(app.getLanguages()).forEach(part::checkCreateDesc);
        }
        return messageVo;
    }


    /**
     * Creates a new message copy template with a temporary repository path
     *
     * @return the new message copy template
     */
    @GET
    @Path("/copy-message-template/{messageId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public SystemMessageVo copyMessageTemplate(
            @PathParam("messageId") String messageId,
            @QueryParam("referenceType") ReferenceType referenceType) throws Exception {

        log.info("Creating copy of message " + messageId);
        Domain domain = domainService.currentDomain();

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return null;
        }

        // Validate viewing access to the message
        checkMessageViewingAccess(message);

        // Create a system model version of the message - copy all repository resource to new temp folder
        SystemMessageVo editMessage = toSystemMessage(message, true);

        // Optionally, add a reference to the original message (before resetting IDs, etc)
        if (referenceType != null) {
            if (editMessage.getReferences() == null) {
                editMessage.setReferences(new ArrayList<>());
            }
            ReferenceVo ref = new ReferenceVo();
            ref.setMessageId(StringUtils.isNotBlank(editMessage.getShortId()) ? editMessage.getShortId() : editMessage.getId());
            ref.setType(referenceType);
            editMessage.getReferences().add(ref);
        }

        // Assign a new ID and repoPath
        editMessage.assignNewId();

        // Reset mainType, if not part of the current domain
        if (editMessage.getMainType() != null && !domain.supportsMainType(editMessage.getMainType())) {
            editMessage.setMainType(null);
            editMessage.setType(null);
            if (!domain.getMessageSeries().isEmpty()) {
                editMessage.setMainType(domain.getMessageSeries().get(0).getMainType());
            }
        }

        // Reset message series, if not part of the current domain
        if (editMessage.getMessageSeries() != null
                && !domain.containsMessageSeries(editMessage.getMessageSeries().getSeriesId())) {
            editMessage.setMessageSeries(null);
        }

        // Compute the default set of editor fields to display for the message
        computeEditorFields(editMessage);

        // Reset various fields
        editMessage.setShortId(null);
        editMessage.setStatus(DRAFT);
        editMessage.setCreated(null);
        editMessage.setUpdated(null);
        editMessage.setNumber(null);
        editMessage.setPublishDateFrom(null);
        editMessage.setPublishDateTo(null);
        editMessage.setUnackComments(0);
        if (editMessage.getParts() != null) {
            editMessage.getParts()
                    .forEach(p -> {
                        p.setEventDates(null);
                        p.setGeometry(featureService.copyFeatureCollection(p.getGeometry()));
                    });
        }

        // Reset copied promulgations
        promulgationManager.onCopyMessage(editMessage);

        return editMessage;
    }


    /**
     * Returns the list of referenced messages, optionally with the given reference type and status
     *
     * @return the list of referenced messages
     */
    @GET
    @Path("/referenced-messages/{messageId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public List<MessageVo> getReferencedMessages(
            @PathParam("messageId") String messageId,
            @QueryParam("status") Status status,
            @QueryParam("referenceType") Set<ReferenceType> referenceTypes,
            @QueryParam("lang") String language
    ) throws Exception {

        log.info("Returning referenced messages for " + messageId + ", types="
                    + referenceTypes + ", status=" + status);

        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return Collections.emptyList();
        }

        // Validate viewing access to the message
        checkMessageViewingAccess(message);

        // Resolve the referenced messages
        List<Message> referencedMessages = messageService.getReferencedMessages(message, referenceTypes, status);

        DataFilter filter = Message.MESSAGE_DETAILS_FILTER
                .lang(language)
                .user(userService.userResolver());

        // Return the result
        // Filter the messages by access (exclude - do not throw exception)
        return referencedMessages.stream()
                .filter(msg -> messageViewingAccess(message))
                .map(msg -> msg.toVo(MessageVo.class, filter))
                .collect(Collectors.toList());
    }


    /**
     * Creates a new draft message
     *
     * @param message the message to create
     * @return the persisted message
     */
    @POST
    @Path("/message")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public MessageVo createMessage(SystemMessageVo message) throws Exception {
        log.info("Creating message " + message);

        // Point embedded links and images to the message repository folder
        message.toMessageRepo();

        Message msg = new Message(message);

        // Validate access to the message
        checkMessageEditingAccess(msg, EditOp.UPDATE);

        msg = messageService.createMessage(msg);

        // Copy resources from the temporary editing message folder to the message repository folder
        message.setId(msg.getUid());
        messageService.updateMessageFromTempRepoFolder(message);

        return getMessage(msg.getUid(), null);
    }


    /**
     * Updates a message
     *
     * @param message the message to update
     * @return the updated message
     */
    @PUT
    @Path("/message/{messageId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public MessageVo updateMessage(@PathParam("messageId") String messageId, SystemMessageVo message) throws Exception {
        if (!Objects.equals(messageId, message.getId())) {
            throw new WebApplicationException(400);
        }
        log.info("Updating message " + message);

        // Point embedded links and images to the message repository folder
        message.toMessageRepo();

        Message msg = new Message(message);

        // Validate access to the message
        checkMessageEditingAccess(msg, EditOp.UPDATE);
        checkMessageEditingAccess(messageService.findByUid(messageId), EditOp.UPDATE);

        msg = messageService.updateMessage(msg);

        // Copy resources from the temporary editing message folder to the message repository folder
        messageService.updateMessageFromTempRepoFolder(message);

        return getMessage(msg.getUid(), null);
    }


    /**
     * Updates the status of a message
     *
     * @param messageId the ID of the message
     * @param status the status update
     * @return the updated message
     */
    @PUT
    @Path("/message/{messageId}/status")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public MessageVo updateMessageStatus(
            @PathParam("messageId") String messageId,
            @QueryParam("cancelReferencedMessages") Boolean cancelReferencedMessages,
            String status) throws Exception {
        log.info("Updating status of message " + messageId + " to " + status);

        // Validate access to the message
        checkMessageEditingAccess(messageService.findByUid(messageId), EditOp.CHANGE_STATUS);

        Message msg = messageService.updateStatus(messageId, Status.valueOf(status));

        // When publishing a message, check if referenced messages should be cancelled
        if (Status.valueOf(status) == PUBLISHED && cancelReferencedMessages != null && cancelReferencedMessages) {

            // Get the cancellation-referenced published messages that the user has edit-access to
            List<Message> referencedMessages = messageService.getReferencedMessages(
                        msg,
                        ReferenceType.cancelsReferencedMessage(),
                        PUBLISHED
                    ).stream()
                    .filter(m -> messageEditingAccess(m, EditOp.CHANGE_STATUS))
                    .collect(Collectors.toList());

            if (!referencedMessages.isEmpty()) {
                for (Message refMsg : referencedMessages) {
                    try {
                        messageService.updateStatus(refMsg.getUid(), CANCELLED);
                        log.info("Cancelling referenced messages to " + messageId + ": " + refMsg.getUid());
                    } catch (Exception ex) {
                        log.error("Error cancelling referenced messages to " + messageId + ": " + refMsg.getUid(), ex);
                    }
                }
            }

        }

        return getMessage(msg.getUid(), null);
    }


    /**
     * Updates the statuses of a list of messages
     *
     * @param updates the status updates
     * @return the updated messages
     */
    @PUT
    @Path("/update-statuses")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public List<MessageVo> updateMessageStatuses(List<UpdateStatusParam> updates) throws Exception {
        log.info("Updating statuses " + updates);

        // Validate access to the messages
        for (UpdateStatusParam update : updates) {
            checkMessageEditingAccess(messageService.findByUid(update.getMessageId()), EditOp.CHANGE_STATUS);
        }

        // Perform the updates
        List<MessageVo> result = new ArrayList<>();
        for (UpdateStatusParam update : updates) {
            Message message = messageService.updateStatus(update.getMessageId(), update.getStatus());
            result.add(getMessage(message.getUid(), null));
        }
        return result;
    }


    /***************************
     * Attachment handling
     ***************************/


    /**
     * Called to upload message attachments to a temporary message folder
     *
     * @param request the servlet request
     * @return a the updated list of attachments
     */
    @POST
    @Path("/attachments/{folder:.+}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public List<AttachmentVo> uploadMessageAttachments(
            @PathParam("folder") String path,
            @Context HttpServletRequest request) throws Exception {

        List<String> uploadedFiles = repositoryService.uploadTempFile(path, request);

        return uploadedFiles.stream()
                .map(f -> {
                    try {
                        File file = repositoryService.getRepoRoot().resolve(f).toFile();
                        AttachmentVo att = new AttachmentVo();
                        att.setPath(path + "/" + WebUtils.encodeURIComponent(file.getName()));
                        att.setFileName(file.getName());
                        att.setFileSize(file.length());
                        att.setType(fileTypes.getContentType(file));
                        return att;
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /***************************************/
    /** Message History methods           **/
    /***************************************/

    /**
     * Returns the message history for the given message ID
     * @param messageId the message ID or message series ID
     * @return the message history
     */
    @GET
    @Path("/message/{messageId}/history")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public List<MessageHistoryVo> getMessageHistory(@PathParam("messageId") String messageId) {

        // Get the message id
        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            return Collections.emptyList();
        }

        return messageService.getMessageHistory(message.getId()).stream()
                .map(MessageHistory::toVo)
                .collect(Collectors.toList());
    }


    /**
     * Returns the recently edited messages for the current user
     * @return the recently edited messages for the current user
     */
    @GET
    @Path("/recently-edited-drafts")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.USER)
    public List<MessageVo> getRecentlyEditedMessages(
            @QueryParam("lang") @DefaultValue("en") String lang,
            @QueryParam("maxMessageNo") @DefaultValue("20") int maxMessageNo
    ) {
        DataFilter filter = DataFilter
                .get().lang(lang)
                .fields("MessageDesc.title");
        return messageService.getMostRecentlyEditedDrafts(maxMessageNo).stream()
                .map(m -> m.toVo(MessageVo.class, filter))
                .collect(Collectors.toList());
    }


    /***************************
     * Sorting functionality
     ***************************/


    /** Re-computes the area sort order of published messages in the current domain */
    @PUT
    @Path("/recompute-area-sort-order")
    @RolesAllowed(Roles.ADMIN)
    public void reindexPublishedMessageAreaSorting() {

        // Search for published message in the curretn domain
        Domain domain = domainService.currentDomain();
        Set<String> messageSeries = domain.getMessageSeries().stream()
                .map(MessageSeries::getSeriesId)
                .collect(Collectors.toSet());

        MessageSearchParams params = new MessageSearchParams();
        params.statuses(Collections.singleton(Status.PUBLISHED))
                .seriesIds(messageSeries);

        // Perform the search and update the area sort order of the messages
        long t0 = System.currentTimeMillis();
        PagedSearchResultVo<Message> searchResult = messageService.search(params);
        searchResult.getData().forEach(m -> messageService.computeMessageAreaSortingOrder(m));

        log.info(String.format("Updates area sort order for  %d messages in %d ms",
                searchResult.getData().size(), System.currentTimeMillis() - t0));
    }


    /** Swaps the area sort order of the two messages */
    @PUT
    @Path("/change-area-sort-order")
    @RolesAllowed(Roles.EDITOR)
    public void changeAreaSortOrder(AreaSortOrderUpdateParam params) {
        if (params.getId() == null || (params.getBeforeId() == null && params.getAfterId() == null)) {
            throw new WebApplicationException(400);
        }
        messageService.changeAreaSortOrder(params.getId(), params.getAfterId(), params.getBeforeId());
    }


    /***************************
     * Utility functions
     ***************************/


    /**
     * Adjust two aspects of the edited message:
     * <ul>
     *     <li>Computes the editor fields to display for the message</li>
     *     <li>If "autoTitle" is set, computes the title lines for the given message template</li>
     * </ul>
     *
     * @param message the message template to adjust
     * @return the updated message template
     */
    @POST
    @Path("/adjust-editable-message")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed(Roles.EDITOR)
    public SystemMessageVo adjustEditableMessage(SystemMessageVo message) throws Exception {

        // Compute the editor fields to use for the message
        computeEditorFields(message);

        Message msg = null;
        boolean autoTitle = message.isAutoTitle() != null && message.isAutoTitle();

        // If auto-title, auto-publication or auto-source is on, compute the resulting fields
        if (autoTitle) {
            msg = new Message(message);

            // Ensure that description records are generated for all model languages
            Arrays.stream(app.getLanguages()).forEach(msg::checkCreateDesc);

            messageService.updateAutoMessageFields(msg);

            // Replace the value object with title line descriptors.
            message.setDescs(null);
            DataFilter filter = DataFilter.get().fields(DataFilter.DETAILS);
            msg.getDescs().forEach(desc -> message.checkCreateDescs().add(desc.toVo(filter)));

        } else {
            message.setDescs(null);
        }


        // Check if we need to update the short ID from the message number
        if (message.getNumber() != null && message.getMessageSeries() != null) {
            if (msg == null) {
                msg = new Message(message);
            }
            messageService.checkUpdateShortId(msg);
            message.setShortId(msg.getShortId());
        }

        // Let promulgation services adjust the promulgations of the message
        promulgationManager.onLoadSystemMessage(message, true);

        // Prune irrelevant fields
        message.setNumber(null);
        message.setPublishDateFrom(null);
        message.setMainType(null);
        message.setAutoTitle(null);
        message.setAreas(null);
        message.setCategories(null);
        message.setMessageSeries(null);

        return message;
    }


    /** Extracts the message publication from the message */
    @POST
    @Path("/extract-message-publication")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.EDITOR)
    @GZIP
    @NoCache
    public MessagePublicationVo extractMessagePublication(
            @QueryParam("lang") @DefaultValue("en") String lang,
            @QueryParam("publicationId") String publicationId,
            MessageVo message) throws Exception {
        Publication publication = publicationService.findByPublicationId(publicationId);
        if (publication != null) {
            return PublicationUtils.extractMessagePublication(
                    message,
                    publication.toVo(SystemPublicationVo.class, DataFilter.get()),
                    lang);
        }
        return null;
    }


    /** Updates the message publications with the given parameters and link */
    @POST
    @Path("/update-message-publications")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.EDITOR)
    @GZIP
    @NoCache
    public MessageVo updateMessagePublications(
            @QueryParam("publicationId") String publicationId,
            @QueryParam("parameters") String parameters,
            @QueryParam("link") String link,
            @QueryParam("lang") String lang,
            MessageVo message) throws Exception {
        Publication publication = publicationService.findByPublicationId(publicationId);
        if (publication != null) {
            return PublicationUtils.updateMessagePublications(
                    message,
                    publication.toVo(SystemPublicationVo.class, DataFilter.get()),
                    parameters,
                    link,
                    lang);
        }
        return message;
    }


    /**
     * Formats the feature collection according to the given template
     *
     * @param geometry the feature collection to format
     * @param language the language of the returned data
     * @param template the template to use for formatting the geometry
     * @param format the position format, either "dec", "sec" or "navtex"
     * @return the formatted geometry
     */
    @POST
    @Path("/format-message-geometry")
    @Consumes("application/json;charset=UTF-8")
    @Produces("text/html;charset=UTF-8")
    @GZIP
    @NoCache
    public String formatGeometry(
            @QueryParam("lang") @DefaultValue("en") String language,
            @QueryParam("template") @DefaultValue("list") String template,
            @QueryParam("format") @DefaultValue("dec") String format,
            FeatureCollectionVo geometry) throws Exception {

        try {
            return geometryFormatService.formatGeometryAsHtml(language, template, format, geometry);
        } catch (Exception e) {
            log.error("Error formatting geometry: " + e.getMessage(), e);
            throw new WebApplicationException("Error formatting geometry", e);
        }
    }


    /**
     * Converts a plain-text geometry representation into a GeoJSON representation.
     * Returns null if the parsing fails.
     */
    @POST
    @Path("/parse-geometry")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public FeatureCollectionVo parsePlainTextGeometry(PlainTextGeometryParam param) throws Exception {
        try {
            PlainTextConverter converter = PlainTextConverter.newInstance(app.getLanguages());
            return converter.fromPlainText(param.getGeometryText());
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Converts a GeoJSON geometry to a plain-text representation
     * Returns null if the formatting fails.
     */
    @POST
    @Path("/format-geometry")
    @Consumes("application/json;charset=UTF-8")
    @Produces("plain/text;charset=UTF-8")
    @GZIP
    @NoCache
    public String formatGeometryAsPlainText(
            @QueryParam("lang") @DefaultValue("en") String language,
            GeoJsonVo geoJson) throws Exception {
        try {
            // Sort languages so that the language parameter comes first (decides decimal separator being used)
            List<String> languages = new ArrayList<>();
            languages.add(language);
            Arrays.stream(app.getLanguages())
                    .filter(lang -> !lang.equalsIgnoreCase(language))
                    .forEach(languages::add);

            PlainTextConverter converter = PlainTextConverter.newInstance(languages.toArray(new String[languages.size()]));
            return converter.toPlainText(geoJson);
        } catch (Exception e) {
            return null;
        }
    }

    /***************************
     * Helper classes
     ***************************/

    /** Encapsulates a status change for a message */
    public static class UpdateStatusParam implements IJsonSerializable {
        String messageId;
        Status status;

        @Override
        public String toString() {
            return "{messageId='" + messageId + "', status=" + status + "}";
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }
    }


    /** Parameter that encapsulates a message being dragged to a new area sort order position */
    public static class AreaSortOrderUpdateParam implements IJsonSerializable {
        String id;
        String beforeId;
        String afterId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getBeforeId() {
            return beforeId;
        }

        public void setBeforeId(String beforeId) {
            this.beforeId = beforeId;
        }

        public String getAfterId() {
            return afterId;
        }

        public void setAfterId(String afterId) {
            this.afterId = afterId;
        }
    }


    /** Parameter that encapsulates a plain-text geometry specification */
    public static class PlainTextGeometryParam implements IJsonSerializable {
        String geometryText;

        public String getGeometryText() {
            return geometryText;
        }

        public void setGeometryText(String geometryText) {
            this.geometryText = geometryText;
        }
    }
}
