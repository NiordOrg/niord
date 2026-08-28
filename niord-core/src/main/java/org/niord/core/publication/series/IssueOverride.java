package org.niord.core.publication.series;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.niord.core.message.Message;
import org.niord.core.model.VersionedEntity;
import org.niord.core.user.User;

/**
 * Live curation state: one manual include or exclude, with author, reason and timestamp.
 *
 * Identity comes from VersionedEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityContractTest.noEntityBringsItsOwnIdGenerator() enforces it.
 */
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "UK_issue_override_issue_uid",
                columnNames = { "issue_id", "messageUid" }),
        indexes = @Index(name = "issue_override_uid_k", columnList = "messageUid"))
public class IssueOverride extends VersionedEntity<Integer> {

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private PublicationIssue issue;

    @Column(length = 36, nullable = false)
    private String messageUid;

    @ManyToOne
    private Message message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OverrideKind kind;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private User author;

    @Column(length = 512, nullable = false)
    private String reason;

    private Boolean appliedAtPublish;

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

    public OverrideKind getKind() {
        return kind;
    }

    public void setKind(OverrideKind kind) {
        this.kind = kind;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getAppliedAtPublish() {
        return appliedAtPublish;
    }

    public void setAppliedAtPublish(Boolean appliedAtPublish) {
        this.appliedAtPublish = appliedAtPublish;
    }

}
