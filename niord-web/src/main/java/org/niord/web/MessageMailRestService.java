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

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.domain.DomainService;
import org.niord.core.mail.Mail.MailRecipient;
import org.niord.core.message.MessageMailService;
import org.niord.core.message.MessageMailService.MessageMailTemplate;
import org.niord.core.message.MessageSearchParams;
import org.niord.model.message.MessageVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import java.util.Collections;

/**
 * REST interface for generating message e-mails.
 */
@Path("/message-mail")
@Stateless
@SecurityDomain("keycloak")
@SuppressWarnings("unused")
public class MessageMailRestService {

    @Inject
    Logger log;

    @Inject
    MessageSearchRestService messageSearchRestService;

    @Inject
    DomainService domainService;

    @Inject
    MessageMailService messageMailService;


    /**
     * Generates and sends an e-mail for the message search result.
     */
    @GET
    @Path("/send")
    @Produces("text/plain")
    @GZIP
    @NoCache
    @RolesAllowed({"editor"})
    public String sendMessageMail(@Context HttpServletRequest request) throws Exception {

        long t0 = System.currentTimeMillis();

        String[] mailAddresses = request.getParameterValues("mailTo");
        String mailSubject = request.getParameter("mailSubject");
        String mailMessage = request.getParameter("mailMessage");
        if (mailAddresses == null || mailAddresses.length == 0) {
            throw new WebApplicationException(400);
        }
        mailSubject = StringUtils.defaultIfBlank(mailSubject, "No subject");
        mailMessage = StringEscapeUtils.escapeHtml(StringUtils.defaultIfBlank(mailMessage, "")).replace("\n", "<br>");

        try {
            // Perform a search for at most 1000 messages
            MessageSearchParams params = MessageSearchParams.instantiate(domainService.currentDomain(), request);
            params.maxSize(1000).page(0);
            PagedSearchResultVo<MessageVo> result = messageSearchRestService.searchMessages(params);


            // Send the e-mails
            StringBuilder str = new StringBuilder();
            for (String mailTo : mailAddresses) {

                MailRecipient recipient = new MailRecipient(RecipientType.TO, new InternetAddress(mailTo));

                MessageMailTemplate mailTemplate = new MessageMailTemplate()
                        .subject(mailSubject)
                        .mailMessage(mailMessage)
                        .messages(result.getData())
                        .recipients(Collections.singletonList(recipient))
                        .language(params.getLanguage())
                        .templatePath("/templates/messages/message-mail.ftl");

                messageMailService.sendMessageMailAsync(mailTemplate);

                String msg = "Mail sent to " + mailTo + " in " + (System.currentTimeMillis() - t0) + " ms";
                log.info(msg);
                str.append(msg).append("\n");

            }
            return str.toString();

        } catch (Exception e) {
            log.error("Error sending e-mail for messages", e);
            throw e;
        }
    }



}
