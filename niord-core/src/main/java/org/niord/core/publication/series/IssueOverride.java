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

import jakarta.validation.constraints.NotNull;
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
    @NotNull
    @JoinColumn(nullable = false)
    private PublicationIssue issue;

    @NotNull
    @Column(length = 36, nullable = false)
    private String messageUid;

    @ManyToOne
    private Message message;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private OverrideKind kind;

    @ManyToOne(optional = false)
    @NotNull
    @JoinColumn(nullable = false)
    private User author;

    @NotNull
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
