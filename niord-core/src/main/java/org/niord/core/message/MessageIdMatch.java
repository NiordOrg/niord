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
package org.niord.core.message;

import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;

import java.util.List;

/**
 * Encapsulates a - possibly partial - message ID match.
 * <p>
 * The match may be for either the message database ID, the shortId or MRN of a message.
 */
@SuppressWarnings("unused")
public class MessageIdMatch implements IJsonSerializable {

    public enum MatchType { ID, SHORT_ID, MRN, TEXT }

    String messageId;
    MatchType type;
    String title;

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
}
