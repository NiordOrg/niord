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

package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.script.FmTemplateService;
import org.niord.core.mail.Mail.MailRecipient;
import org.niord.core.mail.ScheduledMail;
import org.niord.core.mail.ScheduledMailRecipient;
import org.niord.core.mail.ScheduledMailRecipient.RecipientType;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.model.message.MessageVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import java.util.LinkedList;
import java.util.List;

/**
 * Can be used for sending message-related emails
 */
@Stateless
public class MessageMailService extends BaseService {

    @Inject
    Logger log;

    @Inject
    FmTemplateService templateService;

    @Inject
    UserService userService;


    /**
     * Generates an email from the given mail template
     * @param mailTemplate the message mail template
     */
    public void sendMessageMail(MessageMailTemplate mailTemplate) throws Exception {

        if (mailTemplate.getRecipients().isEmpty()) {
            throw new Exception("No mail recipient specified");
        }

        long t0 = System.currentTimeMillis();
        String mailSubject = StringUtils.defaultIfBlank(mailTemplate.getSubject(), "No subject");
        String mailMessage = StringUtils.defaultIfBlank(mailTemplate.getMailMessage(), "");

        User user = userService.currentUser();

        for (MailRecipient recipient : mailTemplate.getRecipients()) {
            try {

                String mailTo = recipient.getAddress().toString();
                if (recipient.getAddress() instanceof InternetAddress) {
                    mailTo = StringUtils.defaultIfBlank(((InternetAddress)recipient.getAddress()).getPersonal(), mailTo);
                }

                String mailContents =
                        templateService.newFmTemplateBuilder()
                                .templatePath(mailTemplate.getTemplatePath())
                                .data("messages", mailTemplate.getMessages())
                                .data("areaHeadings", false)
                                .data("mailTo", mailTo)
                                .data("mailMessage", mailMessage)
                                .data("mailSender", user.getName())
                                .data("mapThumbnails", false)
                                .data("frontPage", false)
                                .data("link", true)
                                .dictionaryNames("message", "mail")
                                .language(mailTemplate.getLanguage())
                                .process();

                ScheduledMail scheduledMail = new ScheduledMail();
                scheduledMail.setSubject(mailSubject);
                scheduledMail.setHtmlContents(mailContents);
                scheduledMail.addRecipient(new ScheduledMailRecipient(RecipientType.TO, recipient.getAddress().toString()));
                scheduledMail.setSender(user.getInternetAddress());
                saveEntity(scheduledMail);

                String msg = "Mail scheduled to " + mailTo + " in " + (System.currentTimeMillis() - t0) + " ms";
                log.info(msg);
            } catch (Exception e) {
                log.error("Error generating PDF for messages", e);
                throw e;
            }
        }
    }


    /**
     * Helper class that encapsulates a message list-based mail
     */
    public static class MessageMailTemplate {

        String language;
        List<MailRecipient> recipients = new LinkedList<>();
        String subject;
        String mailMessage;
        List<MessageVo> messages;
        String templatePath;

        public String getLanguage() {
            return language;
        }

        public MessageMailTemplate language(String language) {
            this.language = language;
            return this;
        }

        public List<MailRecipient> getRecipients() {
            return recipients;
        }

        public MessageMailTemplate recipients(List<MailRecipient> recipients) {
            this.recipients = recipients;
            return this;
        }

        public String getSubject() {
            return subject;
        }

        public MessageMailTemplate subject(String subject) {
            this.subject = subject;
            return this;
        }

        public String getMailMessage() {
            return mailMessage;
        }

        public MessageMailTemplate mailMessage(String mailMessage) {
            this.mailMessage = mailMessage;
            return this;
        }

        public List<MessageVo> getMessages() {
            return messages;
        }

        public MessageMailTemplate messages(List<MessageVo> messages) {
            this.messages = messages;
            return this;
        }

        public String getTemplatePath() {
            return templatePath;
        }

        public MessageMailTemplate templatePath(String templatePath) {
            this.templatePath = templatePath;
            return this;
        }
    }

}
