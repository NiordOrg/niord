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

import java.util.Date;
import java.util.List;
import org.niord.model.IJsonSerializable;

/**
 * One frozen member, with the facts as they stood at freeze.
 *
 * And, where they have since moved, what they are now. Both halves are here
 * because the interesting question about a published issue is not what it
 * contains -- that is settled and printed -- but whether the world it described
 * still looks like that. A row whose message was cancelled the week after
 * release is the row an admin needs to find, and it is indistinguishable from
 * every other row unless the list says so.
 */
public class IssueMemberVo implements IJsonSerializable {

    private String messageUid;

    private Integer sortIndex;

    private String frozenShortId;

    private String frozenType;

    private String frozenStatus;

    private Date frozenPublishDateFrom;

    private Date frozenPublishDateTo;

    private String source;

    /** DERIVED from source and the snapshot relation, never stored. */
    private String reasonCode;

    /** Stored: an import note nothing can derive. */
    private String reasonNote;

    /**
     * The frozen fields whose value no longer matches the live message.
     *
     * Names of fields, not values -- `type`, `status`, `publishDateTo`, and
     * `exists` when the message is gone altogether. SURFACED, NEVER HEALED: the
     * member row itself does not change, because it records what was published
     * and the archived document is the proof of it. Absent on a LIVE list, where
     * "the frozen value" does not exist to disagree with anything.
     */
    private List<String> drift;

    /**
     * The live message, present only when something drifted.
     *
     * Its presence is the flag. A field that were always there would have to be
     * compared against the frozen half by every client that renders a row, and
     * the comparison is the server's to make once.
     */
    private LiveMessageStateVo current;

    /**
     * The curation decision behind this row, where a human took one.
     *
     * ADMIN TIER, and one hop in from the row itself, which is why the member
     * carries a foreign key to the override rather than copies of its columns.
     */
    private MemberCurationVo curation;

    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }

    public Integer getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(Integer sortIndex) {
        this.sortIndex = sortIndex;
    }

    public String getFrozenShortId() {
        return frozenShortId;
    }

    public void setFrozenShortId(String frozenShortId) {
        this.frozenShortId = frozenShortId;
    }

    public String getFrozenType() {
        return frozenType;
    }

    public void setFrozenType(String frozenType) {
        this.frozenType = frozenType;
    }

    public String getFrozenStatus() {
        return frozenStatus;
    }

    public void setFrozenStatus(String frozenStatus) {
        this.frozenStatus = frozenStatus;
    }

    public Date getFrozenPublishDateFrom() {
        return frozenPublishDateFrom;
    }

    public void setFrozenPublishDateFrom(Date frozenPublishDateFrom) {
        this.frozenPublishDateFrom = frozenPublishDateFrom;
    }

    public Date getFrozenPublishDateTo() {
        return frozenPublishDateTo;
    }

    public void setFrozenPublishDateTo(Date frozenPublishDateTo) {
        this.frozenPublishDateTo = frozenPublishDateTo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonNote() {
        return reasonNote;
    }

    public void setReasonNote(String reasonNote) {
        this.reasonNote = reasonNote;
    }

    public List<String> getDrift() {
        return drift;
    }

    public void setDrift(List<String> drift) {
        this.drift = drift;
    }

    public LiveMessageStateVo getCurrent() {
        return current;
    }

    public void setCurrent(LiveMessageStateVo current) {
        this.current = current;
    }

    public MemberCurationVo getCuration() {
        return curation;
    }

    public void setCuration(MemberCurationVo curation) {
        this.curation = curation;
    }

}
