/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
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

            Mail mail = HtmlMail.fromHtml(content, baseUri, true)
                    .doSetSender(new InternetAddress(mailSender))
                    .addFrom(new InternetAddress(mailSender))
                    .doSetSubject(title);

            ValidMailRecipients mailRecipientFilter = new ValidMailRecipients(validRecipients);
            for (String recipient : recipients) {
                mail.addRecipient(Message.RecipientType.TO,	mailRecipientFilter.filter(recipient));
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
