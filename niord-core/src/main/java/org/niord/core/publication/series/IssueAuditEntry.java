package org.niord.core.publication.series;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import org.niord.core.db.JpaJsonAttributeConverter;
import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;

/**
 * The Historik panel. Append-only: never updated, never deleted while its owner exists.
 *
 * Identity comes from BaseEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityIdentityTest enforces it.
 */
@Entity
public class IssueAuditEntry extends BaseEntity<Integer> {

    /**
     * When this entry was written.
     *
     * Its OWN column with its own @PrePersist, because BaseEntity does not carry
     * one -- only VersionedEntity does, and an append-only audit row must not be
     * versioned. Without this the Historik panel has nothing to order by except
     * the surrogate id, which is an implementation detail rather than a time.
     */
    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date created;

    @PrePersist
    protected void stampCreated() {
        if (created == null) {
            created = new Date();
        }
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @ManyToOne
    private PublicationIssue issue;

    /**
     * The series this entry belongs to, when it is a series-level event.
     *
     * DM-Q2: the audit is generalised rather than given three fixed columns on
     * PublicationSeries. A lifecycle event that overwrites its own predecessor is
     * not an audit trail -- three columns cannot record a series that was
     * activated, flagged dormant and then reactivated. Exactly one of issue and
     * series is set on any row.
     */
    @ManyToOne
    private PublicationSeries series;

    @Column(length = 255, nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActorKind actorKind;

    @ManyToOne
    private User user;

    @Column(length = 255)
    private String actorLabel;

    @Column(length = 512)
    private String reason;

    @Column(length = 512)
    private String archivePath;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaJsonAttributeConverter.class)
    private Object detail;

    public PublicationIssue getIssue() {
        return issue;
    }

    public PublicationSeries getSeries() {
        return series;
    }

    public void setSeries(PublicationSeries series) {
        this.series = series;
    }

    public void setIssue(PublicationIssue issue) {
        this.issue = issue;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public ActorKind getActorKind() {
        return actorKind;
    }

    public void setActorKind(ActorKind actorKind) {
        this.actorKind = actorKind;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public void setActorLabel(String actorLabel) {
        this.actorLabel = actorLabel;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    public Object getDetail() {
        return detail;
    }

    public void setDetail(Object detail) {
        this.detail = detail;
    }

}
