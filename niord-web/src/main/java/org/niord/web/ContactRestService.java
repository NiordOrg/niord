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

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.ejb3.annotation.SecurityDomain;
import org.niord.core.user.Contact;
import org.niord.core.user.ContactSearchParams;
import org.niord.core.user.ContactService;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.niord.core.user.vo.ContactVo;
import org.niord.model.IJsonSerializable;
import org.niord.model.search.PagedSearchResultVo;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.niord.core.util.TextUtils.encodeCVSLine;

/**
 * REST interface for accessing contacts.
 */
@Path("/contacts")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed(Roles.ADMIN)
public class ContactRestService {

    @Inject
    Logger log;

    @Inject
    ContactService contactService;

    @Inject
    UserService userService;


    /** Returns all contacts that matches the search criteria */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PagedSearchResultVo<ContactVo> search(
            @QueryParam("name") String name,
            @QueryParam("maxSize") @DefaultValue("100") int maxSize,
            @QueryParam("page") @DefaultValue("0") int page) {

        ContactSearchParams params = new ContactSearchParams()
                .name(name);
        params.maxSize(maxSize).page(page);

        return contactService.searchContacts(params)
                .map(Contact::toVo);
    }


    /**
     * Creates a new contact
     * @param contact the template contact to add
     */
    @POST
    @Path("/contact/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public ContactVo addContact(ContactVo contact) throws Exception {
        log.info("Creating contact " + contact.getEmail());
        return contactService.createContact(new Contact(contact)).toVo();
    }


    /**
     * Updates the contact  with the given id
     * @param contact the template contact to update
     */
    @PUT
    @Path("/contact/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public ContactVo updateContact(@PathParam("id") Integer id, ContactVo contact) throws Exception {
        if (!Objects.equals(id, contact.getId())) {
            throw new WebApplicationException(400);
        }
        log.info("Updating contact " + contact.getId());
        return contactService.updateContact(new Contact(contact)).toVo();
    }


    /**
     * Deletes the contact with the given id
     * @param id the contact to delete
     */
    @DELETE
    @Path("/contact/{id}")
    @NoCache
    public void deleteContact(@PathParam("id") Integer id) throws Exception {
        log.info("Deleting contact " + id);
        contactService.deleteContact(id);
    }



    /** Exports all contacts in CSV format */
    @GET
    @Path("/export")
    @Produces("text/plain;charset=UTF-8")
    @PermitAll // Checked programmatically
    @GZIP
    @NoCache
    public String exportCSV(@QueryParam("separator") @DefaultValue(";") String separator) {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.ADMIN)) {
            throw new WebApplicationException(403);
        }

        return contactService.getAllContacts().stream()
                .map(c -> encodeCVSLine(separator, c.getEmail(), c.getName(), c.getLanguage()))
                .collect(Collectors.joining(System.lineSeparator()));
    }



    /**
     * Imports a list of emails addresses as contacts.
     * The emails are only imported if they do not already exist as contacts or users
     * @param param the emails to import
     */
    @POST
    @Path("/import-emails")
    @Consumes("application/json;charset=UTF-8")
    @Produces("text/plain;charset=UTF-8")
    @NoCache
    public String importEmails(EmailContactImportVo param) throws Exception {

        long t0 = System.currentTimeMillis();

        String[] emailList = StringUtils.defaultString(param.getEmails()).split("[;,\n]");

        // emails are only imported if they are valid and do not already exist as contacts or users
        List<String> validEmails = Arrays.stream(emailList)
                .map(String::trim)
                .filter(e -> StringUtils.isNotBlank(e) && UserService.EMAIL_PATTERN.matcher(e).matches())
                .filter(e -> contactService.findByEmail(e) == null)
                .filter(e -> userService.findByEmail(e) == null)
                .collect(Collectors.toList());

        log.info("Importing " + validEmails.size() + " of " + emailList.length + " e-mails");
        for (String email : validEmails) {
            Contact contact = new Contact();
            contact.setEmail(email);
            if (email.endsWith(".dk")) {
                contact.setLanguage("da");
            }
            try {
                contactService.importContact(contact);
            } catch (Exception e) {
                log.error("Failed importing contact email " + email);
            }
        }

        String result = String.format("Imported %d new contacts in %d ms",
                validEmails.size(),
                System.currentTimeMillis() - t0);
        log.info(result);
        return result;
    }


    /************************/
    /** Helper classes     **/
    /************************/


    @SuppressWarnings("unused")
    public static class EmailContactImportVo implements IJsonSerializable {
        String emails;

        public String getEmails() {
            return emails;
        }

        public void setEmails(String emails) {
            this.emails = emails;
        }
    }
}
