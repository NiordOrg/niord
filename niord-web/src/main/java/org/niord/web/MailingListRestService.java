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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.mail.IMailable;
import org.niord.core.mail.vo.ScheduledMailVo;
import org.niord.core.mailinglist.*;
import org.niord.core.mailinglist.vo.MailingListReportVo;
import org.niord.core.mailinglist.vo.MailingListVo;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.user.*;
import org.niord.core.user.vo.ContactVo;
import org.niord.core.user.vo.UserVo;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST interface for accessing mailing lists.
 */
@Path("/mailing-lists")
@RequestScoped
@Transactional
@RolesAllowed(Roles.ADMIN)
public class MailingListRestService extends AbstractBatchableRestService  {

    @Inject
    Logger log;

    @Inject
    MailingListService mailingListService;

    @Inject
    MailingListExecutionService mailingListExecutionService;

    @Inject
    UserService userService;

    @Inject
    ContactService contactService;

    @Inject
    MessageService messageService;


    /***************************************/
    /** Mailing list retrieval            **/
    /***************************************/


    /** Returns all mailing lists that matches the search criteria */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MailingListVo> search(
            @QueryParam("name") String name,
            @QueryParam("username") String username,
            @QueryParam("contactEmail") String contactEmail,
            @QueryParam("lang") @DefaultValue("en") String lang) {

        MailingListSearchParams params = new MailingListSearchParams()
                .username(username)
                .contactEmail(contactEmail)
                .language(lang)
                .name(name);

        DataFilter filter = MailingList.LIST_DETAILS_FILTER.lang(lang);
        
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
    public MailingListVo getMailingListDetails(
            @PathParam("mailingListId") String mailingListId,
            @QueryParam("includeRecipients") @DefaultValue("false") boolean includeRecipients) {

        log.info("Returning details of mailing list " + mailingListId);
        MailingList mailingList = mailingListService.findByMailingListId(mailingListId);
        if (mailingList == null) {
            throw new WebApplicationException("Mailing list " + mailingListId + " not found", 404);
        }


        DataFilter filter = includeRecipients
                ? MailingList.LIST_DETAILS_AND_RECIPIENTS_FILTER
                : MailingList.LIST_DETAILS_FILTER;
        return mailingList.toVo(filter);
    }


    /***************************************/
    /** Mailing list life cycle           **/
    /***************************************/

    /**
     * Creates a new mailing list
     * @param mailingList the template mailing list to add
     */
    @POST
    @Path("/mailing-list/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @NoCache
    public MailingListVo addMailingList(MailingListVo mailingList) throws Exception {
        log.info("Creating mailing list " + mailingList.getMailingListId());
        return mailingListService.createMailingList(new MailingList(mailingList)).toVo(DataFilter.get());
    }


    /**
     * Updates the mailing list  with the given id.
     *
     * Important: the list of recipients (users and contacts) are not updated.
     * Use "/mailing-list/{mailingListId}/users" and "/mailing-list/{mailingListId}/contacts" for this.
     *
     * @param mailingListId the ID of the mailing list
     * @param mailingList the template mailing list to update
     * @noinspection all
     */
    @PUT
    @Path("/mailing-list/{mailingListId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
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
    @RolesAllowed(Roles.SYSADMIN)
    @NoCache
    public void deleteMailingList(@PathParam("mailingListId") String mailingListId) throws Exception {
        log.info("Deleting mailing list " + mailingListId);
        mailingListService.deleteMailingList(mailingListId);
    }


    /********************************************/
    /** Bulk-update Mailing list recipients    **/
    /********************************************/


    /** Returns recipient users and available users for the given mailing list */
    @GET
    @Path("/mailing-list/{mailingListId}/users")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MailingListRecipients getRecipientUsers(@PathParam("mailingListId") String mailingListId) {
        log.info("Returning recipient users of mailing list " + mailingListId);
        MailingList mailingList = mailingListService.findByMailingListId(mailingListId);
        if (mailingList == null) {
            throw new WebApplicationException("Mailing list " + mailingListId + " not found", 404);
        }

        MailingListRecipients<UserVo> recipientData = new MailingListRecipients<>();

        // Compute selected recipients
        recipientData.setSelectedRecipients(mailingList.getUsers().stream()
            .map(User::toVo)
            .collect(Collectors.toList()));

        // Compute available recipients
        Set<String> emailLookup = recipientData.getSelectedRecipients().stream()
                .map(UserVo::getEmail)
                .collect(Collectors.toSet());
        recipientData.setAvailableRecipients(userService.allUsers().stream()
            .filter(u -> StringUtils.isNotBlank(u.getEmail()) && !emailLookup.contains(u.getEmail()))
            .map(User::toVo)
            .collect(Collectors.toList()));

        return recipientData.sortRecipients();
    }


    /** Update the recipient users of the given mailing list */
    @PUT
    @Path("/mailing-list/{mailingListId}/users")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MailingListRecipients updateRecipientUsers(@PathParam("mailingListId") String mailingListId, List<UserVo> users) throws Exception {

        // Update the recipient users
        log.info("Updating " + users.size() + " recipient users of mailing list " + mailingListId);
        MailingListVo mailingList = getMailingListDetails(mailingListId, true);
        mailingList.setUsers(users);

        // Persist the changes
        mailingListService.updateMailingListRecipients(new MailingList(mailingList));

        // Returns the updated recipient users
        return getRecipientUsers(mailingListId);
    }


    /** Returns recipient contacts and available contacts for the given mailing list */
    @GET
    @Path("/mailing-list/{mailingListId}/contacts")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MailingListRecipients getRecipientContacts(@PathParam("mailingListId") String mailingListId) {
        log.info("Returning recipient contacts of mailing list " + mailingListId);
        MailingList mailingList = mailingListService.findByMailingListId(mailingListId);
        if (mailingList == null) {
            throw new WebApplicationException("Mailing list " + mailingListId + " not found", 404);
        }

        MailingListRecipients<ContactVo> recipientData = new MailingListRecipients<>();

        // Compute selected recipients
        recipientData.setSelectedRecipients(mailingList.getContacts().stream()
            .map(Contact::toVo)
            .collect(Collectors.toList()));

        // Compute available recipients
        Set<String> emailLookup = recipientData.getSelectedRecipients().stream()
                .map(ContactVo::getEmail)
                .collect(Collectors.toSet());
        recipientData.setAvailableRecipients(contactService.getAllContacts().stream()
            .filter(u -> StringUtils.isNotBlank(u.getEmail()) && !emailLookup.contains(u.getEmail()))
            .map(Contact::toVo)
            .collect(Collectors.toList()));

        return recipientData.sortRecipients();
    }


    /** Update the recipient contacts of the given mailing list */
    @PUT
    @Path("/mailing-list/{mailingListId}/contacts")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MailingListRecipients updateRecipientContacts(@PathParam("mailingListId") String mailingListId, List<ContactVo> contacts) throws Exception {

        // Update the recipient contacts
        log.info("Updating " + contacts.size() + " recipient contacts of mailing list " + mailingListId);
        MailingListVo mailingList = getMailingListDetails(mailingListId, true);
        mailingList.setContacts(contacts);

        // Persist the changes
        mailingListService.updateMailingListRecipients(new MailingList(mailingList));

        // Returns the updated recipient contacts
        return getRecipientContacts(mailingListId);
    }


    /***************************************************/
    /** Individual Mailing list recipient handling    **/
    /***************************************************/


    /** Update the recipient status for the given mailing list and user or contact */
    @PUT
    @Path("/mailing-list/{mailingListId}/update-status")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public boolean updateRecipientStatus(
            @PathParam("mailingListId") String mailingListId,
            MailingListRecipientStatus status) throws Exception {

       MailingListVo mailingList = getMailingListDetails(mailingListId, true);
       boolean updated = false;
       if (status.getUser() != null) {
           boolean isUser = mailingList.getUsers() != null && mailingList.getUsers().stream()
                   .anyMatch(u -> Objects.equals(u.getUsername(), status.getUser().getUsername()));
           if (status.isRecipient() && !isUser) {
               log.info("Add user " + status.getUser().getUsername() + " to mailing list " + mailingListId);
               mailingList.checkCreateUsers().add(status.getUser());
               updated = true;
           } else if (!status.isRecipient() && isUser) {
               log.info("Remove user " + status.getUser().getUsername() + " from mailing list " + mailingListId);
               updated = mailingList.getUsers().removeIf(u -> Objects.equals(u.getUsername(), status.getUser().getUsername()));
           }
       } else if (status.getContact() != null) {
           boolean isContact = mailingList.getContacts() != null && mailingList.getContacts().stream()
                   .anyMatch(c -> Objects.equals(c.getEmail(), status.getContact().getEmail()));
           if (status.isRecipient() && !isContact) {
               log.info("Add contact " + status.getContact().getEmail() + " to mailing list " + mailingListId);
               mailingList.checkCreateContacts().add(status.getContact());
               updated = true;
           } else if (!status.isRecipient() && isContact) {
               log.info("Remove contact " + status.getContact().getEmail() + " from mailing list " + mailingListId);
               updated = mailingList.getContacts().removeIf(c -> Objects.equals(c.getEmail(), status.getContact().getEmail()));
           }

       } else {
           throw new WebApplicationException("User or contact must be specified", 400);
       }

        // If recipients were updated, persist the changes
        if (updated) {
            mailingListService.updateMailingListRecipients(new MailingList(mailingList));
            return true;
        }

        // No updates
        return false;
    }


    /***************************************/
    /** Export / Import                   **/
    /***************************************/


    /**
     * Returns all publications for export purposes
     *
     * If not called via Ajax, pass a ticket request parameter along, which
     * can be requested via Ajax call to /rest/tickets/ticket?role=admin
     */
    @GET
    @Path("/export")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @PermitAll // Sysadmin role enforced programmatically
    @NoCache
    public List<MailingListVo> exportMailingLists() {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.SYSADMIN)) {
            throw new WebApplicationException(403);
        }

        MailingListSearchParams params = new MailingListSearchParams();

        DataFilter filter = MailingList.LIST_DETAILS_FILTER;

        return mailingListService.searchMailingLists(params).stream()
                .map(m -> m.toVo(filter))
                .collect(Collectors.toList());
    }


    /**
     * Imports an uploaded mailing-list json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-mailing-lists")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importPublications(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "mailing-list-import");
    }


    /***************************************/
    /** Mailing list Trigger Execution    **/
    /***************************************/

    /**
     * Tests executing a mailing list trigger and return the resulting emails, but do NOT send the mails
     * @param mailingListVo the mailing list
     * @param triggerIndex the trigger index
     * @param messageId the ID of the message to use with status-change triggers
     */
    @POST
    @Path("/test-execute-trigger")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @NoCache
    public List<ScheduledMailVo> testExecuteTrigger(
            @QueryParam("messageId") String messageId,
            @QueryParam("triggerIndex") Integer triggerIndex,
            MailingListVo mailingListVo) throws Exception {

        log.info(String.format("Test-executing mailing list trigger %d of mailingList %s",
                triggerIndex,
                mailingListVo.getMailingListId()));


        MailingList mailingList = new MailingList(mailingListVo);
        MailingListTrigger trigger = mailingList.getTriggers().get(triggerIndex);

        // Add recipients from the persisted mailing list to the template mailing lists
        MailingList orig = mailingListService.findByMailingListId(mailingList.getMailingListId());
        mailingList.getUsers().addAll(orig.getUsers());
        mailingList.getContacts().addAll(orig.getContacts());

        if (trigger.getType() == TriggerType.STATUS_CHANGE) {
            Message message = messageService.resolveMessage(messageId);

            return mailingListExecutionService.executeStatusChangeTrigger(trigger, message, false).stream()
                    .map(m -> m.toVo(DataFilter.get().fields(DataFilter.DETAILS)))
                    .collect(Collectors.toList());
        } else {
            return mailingListExecutionService.executeScheduledTrigger(trigger, false).stream()
                    .map(m -> m.toVo(DataFilter.get().fields(DataFilter.DETAILS)))
                    .collect(Collectors.toList());
        }
    }


    /***************************************/
    /** Mailing list Reports              **/
    /***************************************/


    /**
     * Returns the list of mailing list reports, i.e. the scheduled triggers that can
     * be executed by end-users as reports.
     *
     * @param lang the language
     * @return the list of mailing list reports
     */
    @GET
    @Path("/reports")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @RolesAllowed(Roles.USER)
    @NoCache
    public List<MailingListReportVo> getMailingListReports(@QueryParam("lang") String lang) {
        return mailingListExecutionService.getMailingListReports(lang);
    }


    /**
     * Executes the given mailing list report, i.e. the scheduled triggers that can
     * be executed by end-users as reports.
     *
     * @param triggerId the ID of the trigger (mailing list report) to execute
     * @return the resulting HTML
     */
    @GET
    @Path("/execute-report/{triggerId}.html")
    @Consumes("application/json;charset=UTF-8")
    @Produces("text/html;charset=UTF-8")
    @PermitAll // User role enforced programmatically
    @NoCache
    public String executeMailingListReport(
            @PathParam("triggerId") Integer triggerId,
            @QueryParam("lang") String lang
    ) throws Exception {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.USER)) {
            throw new WebApplicationException(403);
        }

        return mailingListExecutionService.executeMailingListReport(triggerId, lang);
    }



    /***************************************/
    /** Helper Classes                    **/
    /***/


    @SuppressWarnings("unused")
    public static class MailingListRecipients<T extends IMailable> implements IJsonSerializable {
        List<T> selectedRecipients = new ArrayList<>();
        List<T> availableRecipients = new ArrayList<>();

        public MailingListRecipients<T> sortRecipients() {
            IMailable.sortByEmail(selectedRecipients);
            IMailable.sortByEmail(availableRecipients);
            return this;
        }

        public List<T> getSelectedRecipients() {
            return selectedRecipients;
        }

        public void setSelectedRecipients(List<T> selectedRecipients) {
            this.selectedRecipients = selectedRecipients;
        }

        public List<T> getAvailableRecipients() {
            return availableRecipients;
        }

        public void setAvailableRecipients(List<T> availableRecipients) {
            this.availableRecipients = availableRecipients;
        }
    }

    @SuppressWarnings("unused")
    public static class MailingListRecipientStatus implements IJsonSerializable {
        UserVo user;
        ContactVo contact;
        boolean recipient;

        public UserVo getUser() {
            return user;
        }

        public void setUser(UserVo user) {
            this.user = user;
        }

        public ContactVo getContact() {
            return contact;
        }

        public void setContact(ContactVo contact) {
            this.contact = contact;
        }

        public boolean isRecipient() {
            return recipient;
        }

        public void setRecipient(boolean recipient) {
            this.recipient = recipient;
        }
    }

}
