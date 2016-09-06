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
package org.niord.core.message;

import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.message.Status;

import java.util.List;

/**
 * Encapsulates a - possibly partial - message ID match.
 * <p>
 * The match may be for either the message database ID, the shortId or MRN of a message.
 */
@SuppressWarnings("unused")
public class MessageIdMatch implements IJsonSerializable {

    public enum MatchType { UID, SHORT_ID, MRN, TEXT }

    String messageId;
    MatchType type;
    String title;
    Status status;

    /** Constructor **/
    public MessageIdMatch() {
    }


    /** Constructor **/
    public MessageIdMatch(String messageId, MatchType type, String title) {
        this.messageId = messageId;
        this.type = type;
        this.title = title;
    }

    /** Constructor **/
    public MessageIdMatch(String messageId, MatchType type, Message message, String lang) {
        this.messageId = messageId;
        this.type = type;

        // Resolve the title
        this.title = "";
        List<MessageDesc> descs = message.getDescs(DataFilter.get().lang(lang));
        if (!descs.isEmpty()) {
            title = descs.get(0).getTitle();
        }
        this.status = message.getStatus();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public MatchType getType() {
        return type;
    }

    public void setType(MatchType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
