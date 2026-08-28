/*
 * Copyright 2026 Danish Maritime Authority.
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
import org.niord.core.publication.series.vo.IssueAuditEntryVo;
import org.niord.core.user.User;

/**
 * The Historik panel. Append-only: never updated, never deleted while its owner exists.
 *
 * Identity comes from BaseEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityContractTest.noEntityBringsItsOwnIdGenerator() enforces it.
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
     * The audit is generalised rather than given three fixed columns on
     * PublicationSeries. A lifecycle event that overwrites its own predecessor is
     * not an audit trail -- three columns cannot record a series that was
     * activated, flagged dormant and then reactivated. Exactly one of issue and
     * series is set on any row.
     */
    @ManyToOne
    private PublicationSeries series;

    /**
     * What happened.
     *
     * Stored by name in a varchar column rather than as a native database enum,
     * so a new action needs no schema change -- see {@link AuditAction}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 255, nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @NotNull
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

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
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

    /**
     * One line of the history panel.
     *
     * The actor rule lives here rather than in the resource that renders it,
     * because which of two fields answers "who did this" is a rule about the
     * data and not about the transport: the entry carries a user for anything a
     * person did, and a free-text label for the events -- an import, an unattended
     * release -- that had no person behind them.
     *
     * The label is the person's NAME, not their login. User.getName() is first
     * plus last name and already falls back to the username when both are blank,
     * so it can never render emptier than the login would -- and the panel is
     * read by people asking who did something, for whom a login id is not an
     * answer.
     */
    public IssueAuditEntryVo toVo() {
        IssueAuditEntryVo vo = new IssueAuditEntryVo();
        vo.setId(getId());
        vo.setAction(action == null ? null : action.name());
        vo.setActorKind(actorKind == null ? null : actorKind.name());
        vo.setActorLabel(user == null ? actorLabel : user.getName());
        vo.setCreated(created);
        vo.setReason(reason);
        vo.setArchivePath(archivePath);
        vo.setDetail(detail);
        return vo;
    }

}
