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

import org.infinispan.Cache;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents a mail
 * Contains functionality for composing a java mail message from this mail entity
 * with proper handling of attachments and inline HTML parts.
 */
@SuppressWarnings("unused")
public class Mail implements Serializable {

    private static final String CONTENT_ENCODING 			= "UTF-8";
    private static final String CONTENT_TYPE_HTML 			= "text/html; charset=UTF-8";
    private static final String CONTENT_TYPE_PLAIN 			= "text/plain; charset=UTF-8; format=flowed";
    private static final String MIME_MULTIPART_MIXED		= "mixed";
    private static final String MIME_MULTIPART_RELATED		= "related";
    private static final String MIME_MULTIPART_ALTERNATIVE	= "alternative";

    private transient Cache<URL, CachedUrlData> cache;
    private Address sender;
    private List<Address> from = new LinkedList<>();
    private List<MailRecipient> recipients = new LinkedList<>();
    private List<Address> replyTo = new LinkedList<>();
    private String subject;
    private Map<String, String> customHeaders = new LinkedHashMap<>();
    private Date sentDate = new Date();
    private String plainText;
    private String htmlText;
    private List<AttachmentMailPart> attachments = new LinkedList<>();
    private List<InlineMailPart> inlineParts = new LinkedList<>();

    /**
     * Produces the MIME message.
     * @param session the mail session
     * @return the mail message
     */
    public MimeMessage compose(Session session, Cache<URL, CachedUrlData> cache) throws MessagingException {

        // Check that this is a valid mail
        if (from.size() == 0) {
            throw new MessagingException("No from address specified");
        } else if (recipients.size() == 0) {
            throw new MessagingException("No recipient address specified");
        } else if (subject == null) {
            throw new MessagingException("No subject specified");
        }
        this.cache = cache;

        // But we do allow an empty message body
        if (plainText == null && htmlText == null) {
            plainText = "";
        }

        // Create and fill out mime message
        MimeMessage message = new MimeMessage(session);
        message.setSentDate(sentDate);

        // Fill out addresses, recipients, sender, etc
        if (sender != null) {
            message.setSender(sender);
        }
        message.addFrom(toArray(from));
        message.setReplyTo(toArray(replyTo));

        // Add recipients
        for (MailRecipient recipient : recipients) {
          message.addRecipient(recipient.type, recipient.address);
        }

        // Fill out headers
        for (Map.Entry<String, String> header : customHeaders.entrySet()) {
            message.addHeader(header.getKey(), header.getValue());
        }

        // Fill out the subject and body
        message.setSubject(subject, CONTENT_ENCODING);
        composeMailBody(message);

        return message;
    }


    /**
     * Filters the list of mail recipients and only include valid ones
     * @param validMailRecipients the valid mail recipients
     * @return the update mail
     */
    public Mail filterRecipients(ValidMailRecipients validMailRecipients) {

        List<MailRecipient> validRecipients = new ArrayList<>();
        recipients.forEach(r -> {
            if (r.getAddress() != null && r.getAddress() instanceof InternetAddress) {
                try {
                    Address addr = validMailRecipients.filter((InternetAddress) r.getAddress());
                    validRecipients.add(new MailRecipient(r.getType(), addr));
                } catch (AddressException ignored) {
                    // Skip invalid address
                }
            }
        });
        recipients = validRecipients;

        return this;
    }


    /**
     * Composes the body of the message at the top level.
     * If the mail contains attachments, wrap the body as a mixed multipart message
     *
     * @param message the mail to compose the body for
     * @return the mail message
     */
    private MimeMessage composeMailBody(MimeMessage message) throws MessagingException {

        if (attachments.size() == 0) {
            // Compose body parts at top level
            composeBodyPart(message);

        } else {
            // With attachments, create a mixed multipart message
            MimeMultipart mixedPart = new MimeMultipart(MIME_MULTIPART_MIXED);

            MimeBodyPart bodyPart = new MimeBodyPart();
            composeBodyPart(bodyPart);
            mixedPart.addBodyPart(bodyPart);

            // Next, add the attachments
            for (AttachmentMailPart attachment : attachments) {
                MimeBodyPart attachmentBodyPart = new MimeBodyPart();
                attachmentBodyPart.setFileName(attachment.getName());
                attachmentBodyPart.setDataHandler(attachment.getDataHandler(cache));
                attachmentBodyPart.setDisposition(Part.ATTACHMENT);
                mixedPart.addBodyPart(attachmentBodyPart);
            }
            message.setContent(mixedPart);
        }
        return message;
    }

    /**
     * Composes the body of the message
     * @param part the mail body part
     */
    private void composeBodyPart(Part part) throws MessagingException {

        if (htmlText == null) {
            // Only plain text
            part.setContent(plainText, CONTENT_TYPE_PLAIN);

        } else if (plainText == null) {
            // Only HTML
            composeHtmlBodyPart(part);

        } else {
            // Both plain text and html.
            // Wrap in an "alternative" multipart message
            MimeMultipart alternatives = new MimeMultipart(MIME_MULTIPART_ALTERNATIVE);

            // Add plain text
            MimeBodyPart plainTextPart = new MimeBodyPart();
            plainTextPart.setContent(plainText, CONTENT_TYPE_PLAIN);
            alternatives.addBodyPart(plainTextPart);

            // Add html
            MimeBodyPart htmlPart = new MimeBodyPart();
            composeHtmlBodyPart(htmlPart);
            alternatives.addBodyPart(htmlPart);

            part.setContent(alternatives);
        }
    }

    /**
     * Composes the HTML body of the message
     * @param part the mail body part
     */
    private void composeHtmlBodyPart(Part part) throws MessagingException {

        // Check if there are any inline body parts
        if (inlineParts.size() == 0) {
            // Only HTML
            part.setContent(htmlText, CONTENT_TYPE_HTML);

        } else {

            // Wrap the HTML and inline parts in a "related" part
            MimeMultipart related = new MimeMultipart(MIME_MULTIPART_RELATED);
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlText, CONTENT_TYPE_HTML);
            related.addBodyPart(htmlPart);
            // Add the related inline parts
            for (InlineMailPart inlinePart : inlineParts) {
                MimeBodyPart inlineBodyPart = new MimeBodyPart();
                inlineBodyPart.setDataHandler(inlinePart.getDataHandler(cache));
                inlineBodyPart.setHeader("Content-ID", "<" + inlinePart.getContentId() + ">");
                related.addBodyPart(inlineBodyPart);
            }

            part.setContent(related);
        }
    }

    private Address[] toArray(List<Address> a) {
        return a.toArray(new Address[a.size()]);
    }

    /********* Getters and setters  *********/

    public Address getSender() { return sender; }
    public void setSender(Address sender) { this.sender = sender; }

    public List<Address> getFrom() { return from; }
    public void setFrom(List<Address> from) { this.from = from; }

    public List<MailRecipient> getRecipients() { return recipients; }
    public void setRecipients(List<MailRecipient> recipients) { this.recipients = recipients; }

    public List<Address> getReplyTo() { return replyTo; }
    public void setReplyTo(List<Address> replyTo) { this.replyTo = replyTo; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public Map<String, String> getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(Map<String, String> customHeaders) { this.customHeaders = customHeaders; }

    public Date getSentDate() { return sentDate; }
    public void setSentDate(Date sentDate) { this.sentDate = sentDate; }

    public String getPlainText() { return plainText; }
    public void setPlainText(String plainText) { this.plainText = plainText; }

    public String getHtmlText() { return htmlText; }
    public void setHtmlText(String htmlText) { this.htmlText = htmlText; }

    public List<AttachmentMailPart> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentMailPart> attachments) { this.attachments = attachments; }

    public List<InlineMailPart> getInlineParts() { return inlineParts; }
    public void setInlineParts(List<InlineMailPart> inlineParts) { this.inlineParts = inlineParts; }

    /********* Method chaining *********/

    public Mail sender(Address sender) {
        this.sender = sender;
        return this;
    }

    public Mail from(Address addr) {
        from.add(addr);
        return this;
    }

    public Mail recipient(Message.RecipientType type, Address address) {
        recipients.add(new MailRecipient(type, address));
        return this;
    }

    public Mail recipients(List<MailRecipient> recipients) {
        this.recipients = recipients;
        return this;
    }

    public Mail replyTo(Address addr) {
        replyTo.add(addr);
        return this;
    }

    public Mail subject(String subject) {
        this.subject = subject;
        return this;
    }

    public Mail customHeader(String name, String value) {
        customHeaders.put(name, value);
        return this;
    }

    public Mail sentDate(Date sentDate) {
        this.sentDate = sentDate;
        return this;
    }

    public Mail plainText(String plainText) {
        this.plainText = plainText;
        return this;
    }

    public Mail htmlText(String htmlText) {
        this.htmlText = htmlText;
        return this;
    }

    public Mail attachment(AttachmentMailPart attachment) {
        attachments.add(attachment);
        return this;
    }

    public Mail inlineParts(InlineMailPart inlinePart) {
        inlineParts.add(inlinePart);
        return this;
    }

    /**
     * Encapsulates a mail recipient
     */
    public static class MailRecipient implements Serializable {
        Message.RecipientType type;
        Address address;

        /**
         * Constructor
         */
        public MailRecipient(Message.RecipientType type, Address address) {
            this.type = type;
            this.address = address;
        }

        public Message.RecipientType getType() { return type; }
        public Address getAddress() { return address; }
    }
}


