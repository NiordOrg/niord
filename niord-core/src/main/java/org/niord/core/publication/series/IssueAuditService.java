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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.slf4j.Logger;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The audit trail. Append-only: never updated, never deleted while its owner
 * exists.
 *
 * The actions are a closed vocabulary, enumerated once in {@link AuditAction}.
 * An action spelled differently at two call sites is two actions as far as any
 * reader is concerned, and the history panel would show one of them as an
 * unknown event; typing the column and the signatures makes that a compile
 * error instead of something the trail has to be policed for at runtime.
 *
 * actorKind exists because not every audited action has a user. An AUTO_RELEASE
 * publish has no human at all, and the importer writes entries for things that
 * were nobody's action. Recording a fabricated actor in either case is worse
 * than recording none: it makes an unattended event look signed off.
 */
@ApplicationScoped
public class IssueAuditService extends BaseService {

    @Inject
    Logger log;

    // ------------------------------------------------------------------ writes

    /** Step 15 of publish. Exactly one entry, carrying the warnings nobody acknowledged. */
    public IssueAuditEntry published(PublicationIssue issue, User actor, ReleaseMode releaseMode,
                                     Date stampedAt, int memberCount,
                                     List<String> unacknowledgedWarnings, List<String> archivePaths) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stampedAt", stampedAt == null ? null : stampedAt.getTime());
        detail.put("memberCount", memberCount);
        // Materially different from a human having confirmed them: under
        // AUTO_RELEASE nobody saw these at all.
        detail.put("unacknowledgedWarnings", unacknowledgedWarnings);
        detail.put("releaseMode", releaseMode == null ? null : releaseMode.name());

        IssueAuditEntry entry = write(issue, AuditAction.PUBLISHED,
                releaseMode == ReleaseMode.AUTO_RELEASE ? ActorKind.SYSTEM : ActorKind.USER,
                releaseMode == ReleaseMode.AUTO_RELEASE ? null : actor,
                null, detail);

        if (archivePaths != null && !archivePaths.isEmpty()) {
            // The archive lands on THIS transaction's own entry, not only on an
            // AMENDED one: a first publish over existing bytes archives too.
            entry.setArchivePath(String.join(",", archivePaths));
        }
        return entry;
    }

    /** Step 14's successor. There is no separate NEXT_ISSUE_CREATED action. */
    public IssueAuditEntry createdFromPreviousPublish(PublicationIssue successor, PublicationIssue from) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromIssueId", from.getId());
        detail.put("fromPublicId", from.getPublicId());
        return write(successor, AuditAction.CREATED_FROM_PREVIOUS_PUBLISH, ActorKind.SYSTEM, null, null, detail);
    }

    /** The new-edition action, which is what gives supersedes a write path at all. */
    public IssueAuditEntry supersededBy(PublicationIssue predecessor, PublicationIssue successor, User actor) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("supersededByIssueId", successor.getId());
        detail.put("supersededByPublicId", successor.getPublicId());
        return write(predecessor, AuditAction.SUPERSEDED_BY, ActorKind.USER, actor, null, detail);
    }

    /** Steps 12 and 13: a window closed by a neighbour's publish, on the issue whose window moved. */
    public IssueAuditEntry visibilityCapped(PublicationIssue capped, User actor, Date cappedAt,
                                            PublicationIssue byIssue) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("publicTo", cappedAt == null ? null : cappedAt.getTime());
        detail.put("byIssueId", byIssue == null ? null : byIssue.getId());
        detail.put("byPublicId", byIssue == null ? null : byIssue.getPublicId());
        return write(capped, AuditAction.VISIBILITY_CAPPED, actor == null ? ActorKind.SYSTEM : ActorKind.USER,
                actor, null, detail);
    }

    public IssueAuditEntry retired(PublicationIssue issue, User actor, String reason) {
        return write(issue, AuditAction.RETIRED, ActorKind.USER, actor, reason, Map.of());
    }

    public IssueAuditEntry reactivated(PublicationIssue issue, User actor, String reason) {
        return write(issue, AuditAction.REACTIVATED, ActorKind.USER, actor, reason, Map.of());
    }

    public IssueAuditEntry amended(PublicationIssue issue, User actor, String reason, List<String> archivePaths) {
        IssueAuditEntry entry = write(issue, AuditAction.AMENDED, ActorKind.USER, actor, reason, Map.of());
        if (archivePaths != null && !archivePaths.isEmpty()) {
            entry.setArchivePath(String.join(",", archivePaths));
        }
        return entry;
    }

    public IssueAuditEntry created(PublicationIssue issue, User actor, AuditAction action) {
        return write(issue, action, actor == null ? ActorKind.SYSTEM : ActorKind.USER, actor, null, Map.of());
    }

    public IssueAuditEntry override(PublicationIssue issue, User actor, AuditAction action,
                                    String messageUid, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("messageUid", messageUid);
        return write(issue, action, ActorKind.USER, actor, reason, detail);
    }

    /**
     * An edit, with the before and after it changed.
     *
     * The detail is the entry. A Historik line reading "the interval changed"
     * answers nothing an admin came to the panel for -- they are there because
     * the members are not what they expected, and the question is what the
     * interval changed FROM. That is also why there is no generic UPDATE action:
     * a panel cannot render "something changed".
     */
    public IssueAuditEntry edited(PublicationIssue issue, User actor, AuditAction action,
                                  Map<String, Object> detail) {
        return write(issue, action, actor == null ? ActorKind.SYSTEM : ActorKind.USER, actor, null,
                detail == null ? Map.of() : detail);
    }

    /**
     * Which model serves this series to the public, changed.
     *
     * A series-level event: the audit is generalised, so this row belongs to a
     * series and carries no issue. Its own action rather than a SERIES_ACTIVATED
     * with a note, because this is the one change an anonymous reader can see
     * happen: before it, the public list comes from one place and after it from
     * another. The detail carries both ends and whether the readiness
     * precondition was overridden, so the question "who flipped this, and did
     * they know it was not ready" has an answer that does not depend on anyone
     * remembering.
     */
    public IssueAuditEntry seriesAuthority(PublicationSeries series, User actor,
                                           Map<String, Object> detail, String reason) {
        return writeSeries(series, AuditAction.SERIES_AUTHORITY_CHANGED, actor, reason, detail);
    }

    /** Any other series-level event: the row belongs to a series and carries no issue. */
    public IssueAuditEntry series(PublicationSeries series, User actor, AuditAction action, String reason) {
        return writeSeries(series, action, actor, reason, null);
    }

    /** The importer's entries: real events, but nobody's action. */
    public IssueAuditEntry imported(PublicationIssue issue, String note) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("note", note);
        return write(issue, AuditAction.IMPORTED, ActorKind.IMPORT, null, note, detail);
    }

    // ------------------------------------------------------------------ reads

    /**
     * The Historik panel, oldest first.
     *
     * BY THE TIME, with the surrogate id only as a tiebreak. The created column
     * exists precisely so the panel has something to order by that is a time
     * rather than an insertion order -- ordering on the id alone reads correctly
     * only while nothing is ever backfilled, and an import that writes its
     * entries in one pass writes them in whatever order it iterated. The id keeps
     * the order total for entries written in the same millisecond, which the
     * publish transaction does.
     */
    public List<IssueAuditEntry> forIssue(PublicationIssue issue) {
        return em.createQuery(
                        "SELECT a FROM IssueAuditEntry a WHERE a.issue = :i "
                                + "ORDER BY a.created ASC, a.id ASC",
                        IssueAuditEntry.class)
                .setParameter("i", issue).getResultList();
    }

    public List<IssueAuditEntry> forSeries(PublicationSeries series) {
        return em.createQuery(
                        "SELECT a FROM IssueAuditEntry a WHERE a.series = :s "
                                + "ORDER BY a.created ASC, a.id ASC",
                        IssueAuditEntry.class)
                .setParameter("s", series).getResultList();
    }

    // ------------------------------------------------------------------ internals

    private IssueAuditEntry write(PublicationIssue issue, AuditAction action, ActorKind actorKind,
                                  User user, String reason, Map<String, Object> detail) {
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setIssue(issue);
        entry.setAction(action);
        entry.setActorKind(actorKind);
        entry.setUser(user);
        entry.setReason(reason);
        entry.setDetail(detail);
        return persist(entry, action, "issue " + (issue == null ? null : issue.getPublicId()));
    }

    private IssueAuditEntry writeSeries(PublicationSeries series, AuditAction action, User actor,
                                        String reason, Map<String, Object> detail) {
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setSeries(series);
        entry.setAction(action);
        entry.setActorKind(actor == null ? ActorKind.SYSTEM : ActorKind.USER);
        entry.setUser(actor);
        entry.setReason(reason);
        entry.setDetail(detail);
        return persist(entry, action, "series " + (series == null ? null : series.getSeriesId()));
    }

    /**
     * The one place an audit row reaches the database, and the one place a
     * failure to write one is reported.
     *
     * The exception is rethrown, never swallowed: an action that happened with no
     * trail is worse than an action that was refused, so the business transaction
     * has to roll back with it. But it is logged first. Without that, the only
     * evidence of a lost trail is the caller's own failure -- a publish that
     * aborts for no visible reason, with nothing written anywhere that says the
     * audit was what broke.
     */
    private IssueAuditEntry persist(IssueAuditEntry entry, AuditAction action, String owner) {
        try {
            em.persist(entry);
        } catch (RuntimeException e) {
            log.error("Failed to write audit entry {} for {}", action, owner, e);
            throw e;
        }
        log.debug("Audit {} for {}", action, owner);
        return entry;
    }
}
