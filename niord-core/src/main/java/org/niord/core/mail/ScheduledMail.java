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

package org.niord.core.mail;

import org.apache.commons.lang.StringUtils;
import org.niord.core.mail.vo.ScheduledMailVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.util.GzipUtils;
import org.niord.core.util.TimeUtils;
import org.niord.model.DataFilter;

import javax.mail.internet.InternetAddress;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines a scheduled mail placed in a mail queue. The mail is comprised of:
 * <ul>
 *    <li>The list of recipients and from address.</li>
 *    <li>The mail subject.</li>
 *    <li>The HTML contents.</li>
 * </ul>
 *
 * Furthermore, the scheduled mail has a send date, a status which defines if the mail is pending or sent, and an
 * <i>attempts</i> field for the number of attempts to send the mail.
 */
@Entity
@Table(indexes = {
        @Index(name = "scheduled_mail_send_date", columnList="sendDate"),
        @Index(name = "scheduled_mail_status", columnList="status")
})
@NamedQueries({
        @NamedQuery(name = "ScheduledMail.findPendingMails",
                query = "SELECT m FROM ScheduledMail m where m.status = 'PENDING' and m.sendDate <= :date " +
                        " order by m.sendDate asc"),
        @NamedQuery(name = "ScheduledMail.findExpiredMails",
                query = "SELECT m.id FROM ScheduledMail m where m.created <= :expiryDate ")
})
@SuppressWarnings("unused")
public class ScheduledMail extends BaseEntity<Integer> {

    /** Defines the max number of times to attempt to send the mail **/
    public static final int MAX_ATTEMPTS = 5;

    /** In case of an error, how many minutes should the system wait before attempting to send the mail again **/
    public static final int[] DELAYS = {1, 5, 15, 60};

    public enum Status { PENDING, SENT, ERROR }

    @Temporal(TemporalType.TIMESTAMP)
    Date created;

    /** The sendDate defines the date, after which the mail will be send out. */
    @Temporal(TemporalType.TIMESTAMP)
    Date sendDate;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "mail", orphanRemoval = true)
    List<ScheduledMailRecipient> recipients = new ArrayList<>();

    String sender;

    @NotNull
    String subject;

    /** The mail contents is stored in compressed form to preserve space **/
    @NotNull
    @Lob
    byte[] contents;

    @NotNull
    @Enumerated(EnumType.STRING)
    Status status = Status.PENDING;

    int attempts = 0;

    String lastError;


    /** Set the created date **/
    @PrePersist
    protected void onCreate() {
        if (created == null) {
            created = new Date();
        }
        if (sendDate == null) {
            sendDate = created;
        }
    }


    /** Converts this entity to a value object */
    public ScheduledMailVo toVo(DataFilter filter) {
        DataFilter compFilter = filter.forComponent(ScheduledMail.class);

        ScheduledMailVo mail = new ScheduledMailVo();
        mail.setId(id);
        mail.setCreated(created);
        mail.setSendDate(sendDate);
        mail.setStatus(status);
        mail.setSender(sender);
        mail.setSubject(subject);
        mail.setAttempts(attempts);
        mail.setLastError(lastError);
        if (!recipients.isEmpty()) {
            mail.setRecipients(recipients.stream()
                .map(ScheduledMailRecipient::toVo)
                .collect(Collectors.toList()));
        }
        if (compFilter.includeDetails()) {
            try {
                mail.setContents(getHtmlContents());
            } catch (IOException ignored) {
            }
        }
        return mail;
    }


    /**
     * Creates a template message for each recipient with same subject and contents as this mail
     * @return a template message for each recipient with same subject and contents as this mail
     */
    public List<ScheduledMail> splitByRecipient() {
        return recipients.stream()
                .map(r -> {
                    ScheduledMail m = new ScheduledMail();
                    m.addRecipient(new ScheduledMailRecipient(r.getRecipientType(), r.getAddress()));
                    m.setSendDate(sendDate);
                    m.setCreated(created);
                    m.setSender(sender);
                    m.setSubject(subject);
                    m.setContents(contents);
                    return m;
                })
                .collect(Collectors.toList());
    }


    /**
     * When sending the mail has failed, call this method to register the error
     * and schedule when to make another attempt
     * @param error the error message
     */
    public void registerMailErrorAttempt(String error) {
        if (attempts < MAX_ATTEMPTS) {
            sendDate = TimeUtils.add(new Date(), Calendar.MINUTE, DELAYS[attempts]);
        } else {
            status = Status.ERROR;
        }
        attempts++;
        lastError = error;
    }


    /**
     * When sending the mail has succeeded, call this method to register the success
     */
    public void registerMailSent() {
        status = Status.SENT;
        attempts++;
        lastError = null;
    }


    /**
     * Returns a new HTML mail from this queued mail
     *
     * @param baseUri the base URI of the document
     * @param styleHandling whether to inline CSS styles or not
     * @param includePlainText whether to include a plain text version or not
     * @return the HTML mail
     */
    public Mail toMail(String baseUri, HtmlMail.StyleHandling styleHandling, boolean includePlainText) throws Exception {

        List<Mail.MailRecipient> mailRecipients = recipients.stream()
                .map(ScheduledMailRecipient::toMailRecipient)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Mail mail = HtmlMail.fromHtml(getHtmlContents(), baseUri, styleHandling, includePlainText)
                .subject(subject)
                .recipients(mailRecipients);

        if (StringUtils.isNotBlank(sender)) {
            // TODO: Figure out how to handle sender/from addresses ... depends on SMTP service
            //mail.from(new InternetAddress(user.getEmail()))
            mail.replyTo(new InternetAddress(sender));
        }

        return mail;
    }


    /** Adds a new recipient to the list **/
    public ScheduledMailRecipient addRecipient(ScheduledMailRecipient recipient) {
        recipient.setMail(this);
        recipients.add(recipient);
        return recipient;
    }


    /** Returns the uncompressed HTML contents of the email **/
    @Transient
    public String getHtmlContents() throws IOException {
        return GzipUtils.decompressString(contents);
    }


    /** Sets the uncompressed HTML contents of the email **/
    public void setHtmlContents(String htmlContents) throws IOException {
        this.contents = GzipUtils.compressString(htmlContents);
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/


    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getSendDate() {
        return sendDate;
    }

    public void setSendDate(Date sendDate) {
        this.sendDate = sendDate;
    }

    public List<ScheduledMailRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<ScheduledMailRecipient> recipients) {
        this.recipients = recipients;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public byte[] getContents() {
        return contents;
    }

    public void setContents(byte[] contents) {
        this.contents = contents;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
