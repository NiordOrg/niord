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

import org.niord.core.model.BaseEntity;
import org.niord.core.util.GzipUtils;
import org.niord.core.util.TimeUtils;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
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
 *    <li>The list of recipients.</li>
 *    <li>The mail subject.</li>
 *    <li>The HTML contents.</li>
 * </ul>
 *
 * Furthermore, the scheduled mail has a send date, a status which defines if the mail is pending or sent, and an
 * <i>attempts</i> field for the number of attempts to send the mail.
 */
@Entity
@NamedQueries({
        @NamedQuery(name = "ScheduledMail.findPendingMails",
                query = "SELECT m FROM ScheduledMail m where m.status = 'PENDING' and m.sendDate >= :date " +
                        " order by m.sendDate asc")
})
@SuppressWarnings("unused")
public class ScheduledMail extends BaseEntity<Integer> {

    /** Defines the max number of times to attempt to send the mail **/
    public static final int MAX_ATTEMPTS = 5;

    /** In case of an error, how many minutes should the system wait before attempting to send the mail again **/
    public static final int[] DELAYS = {1, 5, 15, 60};

    enum Status { PENDING, SENT, ERROR }

    @Temporal(TemporalType.TIMESTAMP)
    Date created;

    /** The sendDate defines the date, after which the mail will be send out. */
    @Temporal(TemporalType.TIMESTAMP)
    Date sendDate;

    @OneToMany(mappedBy = "mail")
    List<ScheduledMailRecipient> recipients = new ArrayList<>();

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
    }


    /**
     * When sending the mail has failed, call this method to register the error
     * and schedule when to make another attempt
     * @param error the error message
     */
    public void registerMailErrorAttempt(String error) {
        if (attempts < MAX_ATTEMPTS) {
            setSendDate(TimeUtils.add(new Date(), DELAYS[attempts], Calendar.MINUTE));
        } else {
            setStatus(Status.ERROR);
        }
        attempts++;
        lastError = error;
    }


    /**
     * Returns a new HTML mail from this queued mail
     *
     * @param baseUri the base URI of the document
     * @param styleHandling whether to inline CSS styles or not
     * @param includePlainText whether to include a plain text version or not
     * @return the HTML mail
     */
    public Mail toMail(String baseUri, HtmlMail.StyleHandling styleHandling, boolean includePlainText) throws IOException {

        List<Mail.MailRecipient> mailRecipients = recipients.stream()
                .map(ScheduledMailRecipient::toMailRecipient)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return HtmlMail.fromHtml(getHtmlContents(), baseUri, styleHandling, includePlainText)
                .subject(subject)
                .recipients(mailRecipients);
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
