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

import org.niord.core.mail.Mail.MailRecipient;
import org.niord.core.mail.vo.ScheduledMailRecipientVo;
import org.niord.core.model.BaseEntity;

import jakarta.mail.Message;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * Defines a recipient of a scheduled mail
 */
@Entity
@SuppressWarnings("unused")
public class ScheduledMailRecipient extends BaseEntity<Integer> {

    public enum RecipientType {TO, CC, BCC,}

    @NotNull
    @ManyToOne
    ScheduledMail mail;

    @NotNull
    @Enumerated(EnumType.STRING)
    RecipientType recipientType;

    @NotNull
    String address;


    /** Constructor **/
    public ScheduledMailRecipient() {
    }


    /** Constructor **/
    public ScheduledMailRecipient(RecipientType recipientType, String address) {
        this.recipientType = recipientType;
        this.address = address;
    }


    /** Converts this entity to a value object */
    public ScheduledMailRecipientVo toVo() {
        ScheduledMailRecipientVo recipient = new ScheduledMailRecipientVo();
        recipient.setAddress(address);
        recipient.setRecipientType(recipientType);
        return recipient;
    }


    /** Returns this entity as a MailRecipient. Returns null if the address cannot be parsed **/
    public MailRecipient toMailRecipient() {
        Message.RecipientType type;
        switch (recipientType) {
            case CC:
                type = Message.RecipientType.CC;
                break;
            case BCC:
                type = Message.RecipientType.BCC;
                break;
            default:
                type = Message.RecipientType.TO;
        }
        try {
            return new MailRecipient(type, new InternetAddress(address));
        } catch (AddressException e) {
            e.printStackTrace();
            return null;
        }
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    public ScheduledMail getMail() {
        return mail;
    }

    public void setMail(ScheduledMail mail) {
        this.mail = mail;
    }

    public RecipientType getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(RecipientType recipientType) {
        this.recipientType = recipientType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
