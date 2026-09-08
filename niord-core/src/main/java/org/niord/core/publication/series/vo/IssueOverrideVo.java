/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.core.publication.series.vo;

/**
 * One standing curation decision on an issue, whichever way it went.
 *
 * A curation decision plus the message it is about, which is why it is the
 * member row's curation block with a uid on it rather than a second shape: the
 * why-line an INCLUDE renders and the reason an EXCLUDE was taken are the same
 * facts and must not be able to disagree.
 *
 * It exists as a list of its own because an EXCLUDE never appears in the member
 * list -- that is what excluding means -- so there is no row for a "withdraw
 * this exclusion" affordance to hang off. Without this the exclusions have to be
 * reconstructed from the audit trail, which records what HAPPENED rather than
 * what STANDS: an exclude followed by a clear leaves two entries and no
 * decision, and reading it as one is how a withdrawn exclusion comes back to
 * life on a screen.
 */
public class IssueOverrideVo extends MemberCurationVo {

    private String messageUid;

    /**
     * The short id an editor reads and cites -- "NM-815-26" -- or null.
     *
     * DISPLAY TEXT, never a key: it is not declared unique, nothing stops it
     * being reused, and a message that has not been numbered yet has none. Null
     * in that case rather than falling back to the uid, because a uid dressed up
     * as a short id is a string somebody will try to cite.
     */
    private String messageId;

    /**
     * The message's live title, in the language the list was asked for.
     *
     * The half that makes an EXCLUSION legible at all. An excluded message has no
     * row in the member list -- that is what excluding it means -- so this list is
     * the only place it is named, and "uid 7f3a… is excluded" is not a decision
     * anybody can check. Never frozen: it says what the message is called today,
     * so it cannot drift away from every other screen that names it.
     *
     * Null when the message carries no titled description, and null when it is
     * gone altogether.
     */
    private String title;

    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
