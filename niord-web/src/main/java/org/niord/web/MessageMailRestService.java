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
import org.niord.core.NiordApp;
import org.niord.core.fm.FmService;
import org.niord.core.mail.HtmlMail;
import org.niord.core.mail.Mail;
import org.niord.core.mail.MailService;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.model.message.MessageVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;

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
    NiordApp app;

    @Inject
    MessageSearchRestService messageSearchRestService;

    @Inject
    FmService fmService;

    @Inject
    MailService mailService;

    @Inject
    UserService userService;


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
        String mailTo = request.getParameter("mailTo");
        String mailSubject = request.getParameter("mailSubject");
        String mailMessage = request.getParameter("mailMessage");

        if (StringUtils.isBlank(mailTo)) {
            throw new WebApplicationException(400);
        }
        mailSubject = StringUtils.defaultIfBlank(mailSubject, "No subject");
        mailMessage = StringEscapeUtils.escapeHtml(StringUtils.defaultIfBlank(mailMessage, "")).replace("\n", "<br>");

        // Perform a search for at most 1000 messages
        MessageSearchParams params = MessageSearchParams.instantiate(request);
        params.maxSize(1000).page(0);
        PagedSearchResultVo<MessageVo> result = messageSearchRestService.search(params);

        User user = userService.currentUser();

        try {
            String mailContents =
                    fmService.newTemplateBuilder()
                            .setTemplatePath("/templates/messages/message-mail.ftl")
                            .setData("messages", result.getData())
                            .setData("areaHeadings", params.sortByArea())
                            .setData("searchCriteria", result.getDescription())
                            .setData("mailTo", mailTo)
                            .setData("mailMessage", mailMessage)
                            .setData("mailSender", user.getName())
                            .setDictionaryNames("web", "message", "mail")
                            .setLanguage(params.getLanguage())
                            .process();

            Mail mail = HtmlMail.fromHtml(mailContents, app.getBaseUri(), HtmlMail.StyleHandling.INLINE_STYLES, true)
                    .sender(new InternetAddress("noreply@e-navigation.net"))
                    .from(new InternetAddress("noreply@e-navigation.net"))
                    .replyTo(new InternetAddress(user.getEmail()))
                    .recipient(javax.mail.Message.RecipientType.TO, new InternetAddress(mailTo))
                    .subject(mailSubject);
            mailService.sendMail(mail);

            String msg = "Mail sent to " + mailTo + " in " + (System.currentTimeMillis() - t0) + " ms";
            log.info(msg);
            return msg;

        } catch (Exception e) {
            log.error("Error generating PDF for messages", e);
            throw e;
        }
    }



}
