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
package org.niord.core.mail;

import org.niord.core.NiordApp;
import org.niord.core.settings.annotation.Setting;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;

/**
 * Interface for sending emails
 */
@Stateless
@SuppressWarnings("unused")
public class MailService {

    @Resource(name = "java:jboss/mail/Niord")
    Session mailSession;

    @Inject
    @Setting(value = "mailSender", defaultValue = "niord@e-navigation.net",
            description = "The sender e-mail address")
    String mailSender;

    @Inject
    @Setting(value = "mailValidRecipients", defaultValue = "niord@e-navigation.net",
            description = "Comma-separated list of valid mail recipients, or 'ALL' for all recipients")
    String validRecipients;

    @Inject
    Logger log;

    @Inject
    MailAttachmentCache mailAttachmentCache;

    @Inject
    NiordApp app;


    /**
     * Sends an email
     * @param content the HTML content
     * @param title the title of the email
     * @param recipients the list of recipients
     */
    public void sendMail(String content, String title, String... recipients) throws Exception {
        try {
            String baseUri = app.getBaseUri();

            Mail mail = HtmlMail.fromHtml(content, baseUri, HtmlMail.StyleHandling.INLINE_STYLES, true)
                    .sender(new InternetAddress(mailSender))
                    .from(new InternetAddress(mailSender))
                    .subject(title);

            ValidMailRecipients mailRecipientFilter = new ValidMailRecipients(validRecipients);
            for (String recipient : recipients) {
                mail.recipient(Message.RecipientType.TO,	mailRecipientFilter.filter(recipient));
            }

            sendMail(mail);


        } catch (Exception e) {
            log.error("error sending email: " + title, e);
            throw e;
        }
    }


    /**
     * Sends the given mail synchronously
     * @param mail the mail to send
     */
    public void sendMail(Mail mail) throws MessagingException {
        try {
            if (mail.getSender() == null) {
                mail.setSender(new InternetAddress(mailSender));
            }
            if (mail.getFrom() == null || mail.getFrom().isEmpty()) {
                mail.from(new InternetAddress(mailSender));
            }

            log.info("Composing mail for " + mail.getRecipients());
            Message message = mail.compose(mailSession, mailAttachmentCache.getCache());
            log.info("Sending...");
            Transport.send(message);
            log.info("Done");

        } catch (MessagingException e) {
            log.error("Failed sending mail for " + mail.getFrom(), e);
            throw e;
        }
    }

}
