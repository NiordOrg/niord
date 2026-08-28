package org.niord.core.publication.series;

import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * table alone. EntityContractTest.noEntityBringsItsOwnIdGenerator() enforces it.
 */
@Entity
// Named because the same statement is issued from two places: the delete of an
// unpublished issue, and step 6 of publish clearing the previous freeze before
// writing the new one. Two copies of a bulk DELETE are two chances for one of
// them to widen.
@NamedQuery(name = "IssueMember.deleteByIssue",
        query = "DELETE FROM IssueMember m WHERE m.issue = :issue")
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "UK_issue_member_issue_uid",
                columnNames = { "issue_id", "messageUid" }),
        indexes = {
                @Index(name = "issue_member_issue_sort_k", columnList = "issue_id,sortIndex"),
                @Index(name = "issue_member_uid_k", columnList = "messageUid")
        })
public class IssueMember extends BaseEntity<Integer> {

    @ManyToOne(optional = false)
    @NotNull
    @JoinColumn(nullable = false)
    private PublicationIssue issue;

    @NotNull
    @Column(length = 36, nullable = false)
    private String messageUid;

    @ManyToOne
    private Message message;

    @NotNull
    @Column(nullable = false)
    private Integer sortIndex;

    @Column(length = 255)
    private String frozenShortId;

    @NotNull
    @Column(length = 255, nullable = false)
    private String frozenMainType;

    @NotNull
    @Column(length = 255, nullable = false)
    private String frozenType;

    @NotNull
    @Column(length = 255, nullable = false)
    private String frozenStatus;

    @Temporal(TemporalType.TIMESTAMP)
    private Date frozenPublishDateFrom;

    @Temporal(TemporalType.TIMESTAMP)
    private Date frozenPublishDateTo;

    @Enumerated(EnumType.STRING)
    @NotNull
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
