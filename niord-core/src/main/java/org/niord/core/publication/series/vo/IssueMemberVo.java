package org.niord.core.publication.series.vo;

import java.util.Date;
import org.niord.model.IJsonSerializable;

/**
 * One frozen member, with the facts as they stood at freeze.
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

}
