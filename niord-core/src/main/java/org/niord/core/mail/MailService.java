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

import jakarta.annotation.Resource;
import org.niord.core.NiordApp;
import org.niord.core.service.BaseService;
import org.niord.core.settings.annotation.Setting;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.transaction.Transactional;
import java.util.stream.Collectors;

/**
 * Interface for sending emails
 */
@RequestScoped
@SuppressWarnings("unused")
public class MailService extends BaseService {

    @Resource(name = "java:jboss/mail/Niord")
    Session mailSession;

    @Inject
    @Setting(value = "mailSender", defaultValue = "niord@e-navigation.net",
            description = "The sender e-mail address")
    String mailSender;

    @Inject
    @Setting(value = "mailValidRecipients", defaultValue = "LOG",
            description = "Comma-separated list of valid mail recipients, or 'ALL' for all recipients, or 'LOG' for simulation")
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

            for (String recipient : recipients) {
                mail.recipient(Message.RecipientType.TO, new InternetAddress(recipient));
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
            long t0 = System.currentTimeMillis();

            if (mail.getSender() == null) {
                mail.setSender(new InternetAddress(mailSender));
            }
            if (mail.getFrom() == null || mail.getFrom().isEmpty()) {
                mail.from(new InternetAddress(mailSender));
            }

            // Validate the mail recipients
            ValidMailRecipients recipientHandling = new ValidMailRecipients(validRecipients);
            mail.filterRecipients(recipientHandling);

            // Check if any recipients are defined
            if (mail.getRecipients().isEmpty()) {
                throw new MessagingException("No valid recipient");
            }

            // Check if we only simulate sending emails
            if (recipientHandling.simulate()) {

                // Make the simulation realistic - sleep 0.5 - 1 seconds
                try {
                    Thread.sleep(500L + (long)(500.0 * Math.random()));
                } catch (InterruptedException ignored) {
                }

            } else {
                // Send the message
                log.debug("Composing mail");
                Message message = mail.compose(mailSession, mailAttachmentCache.getCache());
                log.debug("Sending...");
                Transport.send(message);

            }

            String recipients = mail.getRecipients().stream()
                    .filter(r -> r.getAddress() != null)
                    .map(r -> r.getAddress().toString())
                    .collect(Collectors.joining(", "));
            log.info("Sent email to " + recipients + " in " + (System.currentTimeMillis() - t0) + " ms");

        } catch (MessagingException e) {
            log.error("Failed sending mail for " + mail.getFrom(), e);
            throw e;
        }
    }


    /**
     * Sends the scheduled mail with the given ID and updates the status of the scheduled mail entity
     * @param scheduledMailId the ID of the scheduled mail to send
     * @return the updated mail entity
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ScheduledMail sendScheduledMail(Integer scheduledMailId) {
        try {
            ScheduledMail scheduledMail = em.find(ScheduledMail.class, scheduledMailId);

            // Double-check that the scheduled mail is still pending
            if (scheduledMail != null && scheduledMail.getStatus() == ScheduledMail.Status.PENDING) {

                try {
                    Mail mail = scheduledMail.toMail(app.getBaseUri(), HtmlMail.StyleHandling.INLINE_STYLES, false);

                    // If undefined, set reply-to to the first to-recipient
                    if (mail.getReplyTo().isEmpty()) {
                        mail.getRecipients().stream()
                                .filter(r -> r.getType() == Message.RecipientType.TO)
                                .limit(1)
                                .forEach(r -> mail.replyTo(r.getAddress()));
                    }

                    // Send the mail
                    sendMail(mail);

                    // Register that the mail has successfully been sent
                    scheduledMail.registerMailSent();

                } catch (Exception e) {

                    // Register that the mail failed being sent
                    scheduledMail.registerMailErrorAttempt(e.getMessage());
                    log.error("Error sending mail " + scheduledMailId + ", attempt " + scheduledMail.getAttempts(), e);
                }
                saveEntity(scheduledMail);
            }

            return scheduledMail;

        } catch (Exception e) {
            log.error("Error finding scheduled mail " + scheduledMailId, e);
            return null;
        }
    }

}
