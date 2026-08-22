package org.niord.core.publication.series;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;
import org.niord.core.message.Message;
import org.niord.core.model.BaseEntity;

/**
 * The frozen member snapshot: one row per member, keyed on message uid, carrying the mutable
 * facts as they stood at freeze plus the recorded print order. Written only at freeze.
 *
 * Identity comes from BaseEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityIdentityTest enforces it.
 */
@Entity
public class IssueMember extends BaseEntity<Integer> {

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private PublicationIssue issue;

    @Column(length = 36, nullable = false)
    private String messageUid;

    @ManyToOne
    private Message message;

    @Column(nullable = false)
    private Integer sortIndex;

    @Column(length = 255)
    private String frozenShortId;

    @Column(length = 255, nullable = false)
    private String frozenMainType;

    @Column(length = 255, nullable = false)
    private String frozenType;

    @Column(length = 255, nullable = false)
    private String frozenStatus;

    @Temporal(TemporalType.TIMESTAMP)
    private Date frozenPublishDateFrom;

    @Temporal(TemporalType.TIMESTAMP)
    private Date frozenPublishDateTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberSource source;

    @Column(length = 512)
    private String reasonNote;

    @ManyToOne
    private IssueOverride override;

    public PublicationIssue getIssue() {
        return issue;
    }

    public void setIssue(PublicationIssue issue) {
        this.issue = issue;
    }

    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
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

    public String getFrozenMainType() {
        return frozenMainType;
    }

    public void setFrozenMainType(String frozenMainType) {
        this.frozenMainType = frozenMainType;
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

    public MemberSource getSource() {
        return source;
    }

    public void setSource(MemberSource source) {
        this.source = source;
    }

    public String getReasonNote() {
        return reasonNote;
    }

    public void setReasonNote(String reasonNote) {
        this.reasonNote = reasonNote;
    }

    public IssueOverride getOverride() {
        return override;
    }

    public void setOverride(IssueOverride override) {
        this.override = override;
    }

}
