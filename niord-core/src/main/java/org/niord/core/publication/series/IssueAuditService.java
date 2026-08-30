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

package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Step 15 of publish. Exactly one entry, carrying the warnings nobody
     * acknowledged and the release checklist it was published against.
     *
     * The checklist is recorded because it is the only evidence of it that
     * outlives the transaction. It is computed fresh on every read of the rail,
     * from a corpus that keeps moving -- ask the same issue tomorrow and rows that
     * warned may pass and rows that passed may warn -- so a week after a release
     * nobody can reconstruct what the person pressing the button was looking at.
     * "This went out with two warnings, and somebody ticked both" is exactly the
     * question a history panel is opened to answer.
     *
     * @param checklistRows   the rail as it stood in this transaction; the rows
     *                        that do not APPLY to this issue are dropped, because a
     *                        row about a question this issue never raises is not an
     *                        answer about this issue and counting it as one
     *                        overstates what was checked
     * @param acknowledgedSet what the caller ticked, by warning code. Empty under
     *                        AUTO_RELEASE, where nobody ticked anything because
     *                        nobody was there
     */
    public IssueAuditEntry published(PublicationIssue issue, User actor, ReleaseMode releaseMode,
                                     Date stampedAt, int memberCount,
                                     List<String> unacknowledgedWarnings, List<String> archivePaths,
                                     List<PublishChecklistService.CheckRow> checklistRows,
                                     Set<String> acknowledgedSet) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stampedAt", stampedAt == null ? null : stampedAt.getTime());
        detail.put("memberCount", memberCount);
        // Materially different from a human having confirmed them: under
        // AUTO_RELEASE nobody saw these at all.
        detail.put("unacknowledgedWarnings", unacknowledgedWarnings);
        detail.put("releaseMode", releaseMode == null ? null : releaseMode.name());
        detail.put("checklist", checklistOf(checklistRows, acknowledgedSet));

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

    /**
     * The rail, flattened to what a reader of the trail can act on.
     *
     * Four fields per row and no more. The rail's `detail` string is a sentence
     * written for the dialog that was on screen at the time -- "must be after
     * 2026-07-29 10:16 (Europe/Copenhagen)" -- and it names instants and counts
     * that have since moved; kept here it would read as a statement about today.
     * The code, the severity, whether it passed and whether somebody ticked it are
     * facts about the release, and they stay true.
     *
     * `acknowledged` is read against the code the GATE compares, and ONLY against
     * that. A rail row names a condition -- "cancelled members alive at the
     * cut-off" -- while the acknowledgement travels as the resolution warning the
     * resolver raised, and the two are deliberately different strings. A row that
     * carries no acknowledgement code cannot be ticked at all and answers false,
     * whatever the caller sent: OVERLAPPING_ISSUE is the name of a rail row AND of
     * a warning nobody can sign, so falling back to the row's own name would badge
     * it as confirmed by a caller that never had a control to confirm it with.
     */
    private static List<Map<String, Object>> checklistOf(
            List<PublishChecklistService.CheckRow> rows, Set<String> acknowledgedSet) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        Set<String> acknowledged = acknowledgedSet == null ? Set.of() : acknowledgedSet;
        for (PublishChecklistService.CheckRow row : rows) {
            if (!row.applicable()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", row.code());
            entry.put("severity", row.severity() == null ? null : row.severity().name());
            entry.put("passed", row.passed());
            entry.put("acknowledged",
                    row.acknowledgeCode() != null && acknowledged.contains(row.acknowledgeCode()));
            out.add(entry);
        }
        return out;
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

    /**
     * The series changed hands.
     *
     * A series-level event like the authority flip, and recorded the same way: the
     * detail carries both ends so the entry answers "where did this come from"
     * without anybody having to remember, and the reason is the sentence the
     * receiving desk reads months later when they wonder why they own it.
     */
    public IssueAuditEntry ownerTransferred(PublicationSeries series, User actor,
                                            SeriesOwnerTransfer.Moved moved, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        // `from` is null for a CLAIM: a publication that belonged to no desk at
        // all. The entry has to be able to say that, because "who did this come
        // from" and "nobody owned this until somebody took it" are different
        // histories and only one of them needs explaining further.
        detail.put("from", moved == null ? null : moved.fromDomainId());
        detail.put("to", moved == null ? null : moved.toDomainId());
        // The sharing setting travels too, because a transfer can change it:
        // moving a publication to the one domain it was shared with leaves the
        // list empty and collapses SELECTED_DOMAINS to OWNER_ONLY. Without this,
        // a reader would see a publication stop being shared on the day it moved
        // with nothing connecting the two.
        if (moved != null && moved.availabilityChanged()) {
            detail.put("availabilityBefore",
                    moved.availabilityBefore() == null ? null : moved.availabilityBefore().name());
            detail.put("availabilityAfter",
                    moved.availabilityAfter() == null ? null : moved.availabilityAfter().name());
        }
        return writeSeries(series, AuditAction.OWNER_TRANSFERRED, actor, reason, detail);
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
