package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The audit trail. Append-only: never updated, never deleted while its owner
 * exists.
 *
 * The actions are a closed vocabulary, declared here once. An action spelled
 * differently at two call sites is two actions as far as any reader is
 * concerned, and the Historik panel would show one of them as an unknown event.
 *
 * actorKind exists because not every audited action has a user. An AUTO_RELEASE
 * publish has no human at all, and the importer writes entries for things that
 * were nobody's action. Recording a fabricated actor in either case is worse
 * than recording none: it makes an unattended event look signed off.
 */
@ApplicationScoped
@Transactional
public class IssueAuditService extends BaseService {

    /** Every action that may appear in the trail. */
    public static final Set<String> ACTIONS = new LinkedHashSet<>(List.of(
            "CREATED",
            "CREATED_FROM_PREVIOUS_PUBLISH",
            "CREATED_RETROACTIVELY",
            "CREATED_NEW_EDITION",
            "PUBLISHED",
            "AMENDED",
            "RETIRED",
            "REACTIVATED",
            "SUPERSEDED_BY",
            "DELETED",
            // Specific, never a generic UPDATE: a Historik panel cannot render
            // "something changed", and each of these carries the before and after
            // that makes the line answer the question it was opened for.
            "INTERVAL_CHANGED",
            "NAME_CHANGED",
            "CRITERIA_OVERRIDDEN",
            "OVERRIDE_INCLUDED",
            "OVERRIDE_EXCLUDED",
            "OVERRIDE_REMOVED",
            "FILE_UPLOADED",
            // A document that was already released, overwritten by hand. Distinct
            // from an upload because the trail has to say whether a file appeared
            // or a cited one was replaced -- the archive path on this entry is
            // the only route back to what the public was reading before.
            "FILE_REPLACED_MANUALLY",
            "FILE_CLEARED",
            // A link is the published artefact for an external publication, exactly
            // as a file is for a hosted one, so changing one is as much a change to
            // what the public sees as replacing the other.
            "LINK_SET",
            "LINK_CLEARED",
            "PREVIEW_GENERATED",
            "WINDOW_ADJUSTED",
            // The public window closed by a NEIGHBOUR's publish -- the predecessor
            // capped at this stamp, or this issue capped at a successor that had
            // already published. Written on the issue whose window moved, because
            // that is where somebody looks when a publication left the site.
            "VISIBILITY_CAPPED",
            "IMPORTED",
            "SERIES_ACTIVATED",
            "SERIES_RETIRED",
            // Which model answers the public for this series. Visible to every
            // anonymous reader the moment it changes, so it is its own action.
            "SERIES_AUTHORITY_CHANGED"));

    /** An action outside the vocabulary. */
    public static class UnknownAuditActionException extends RuntimeException {
        public UnknownAuditActionException(String action) {
            super("unknown audit action '" + action + "'; the vocabulary is " + ACTIONS);
        }
    }

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

        IssueAuditEntry entry = write(issue, "PUBLISHED",
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
        return write(successor, "CREATED_FROM_PREVIOUS_PUBLISH", ActorKind.SYSTEM, null, null, detail);
    }

    /** The new-edition action, which is what gives supersedes a write path at all. */
    public IssueAuditEntry supersededBy(PublicationIssue predecessor, PublicationIssue successor, User actor) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("supersededByIssueId", successor.getId());
        detail.put("supersededByPublicId", successor.getPublicId());
        return write(predecessor, "SUPERSEDED_BY", ActorKind.USER, actor, null, detail);
    }

    /** Steps 12 and 13: a window closed by a neighbour's publish, on the issue whose window moved. */
    public IssueAuditEntry visibilityCapped(PublicationIssue capped, User actor, Date cappedAt,
                                            PublicationIssue byIssue) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("publicTo", cappedAt == null ? null : cappedAt.getTime());
        detail.put("byIssueId", byIssue == null ? null : byIssue.getId());
        detail.put("byPublicId", byIssue == null ? null : byIssue.getPublicId());
        return write(capped, "VISIBILITY_CAPPED", actor == null ? ActorKind.SYSTEM : ActorKind.USER,
                actor, null, detail);
    }

    public IssueAuditEntry retired(PublicationIssue issue, User actor, String reason) {
        return write(issue, "RETIRED", ActorKind.USER, actor, reason, Map.of());
    }

    public IssueAuditEntry reactivated(PublicationIssue issue, User actor, String reason) {
        return write(issue, "REACTIVATED", ActorKind.USER, actor, reason, Map.of());
    }

    public IssueAuditEntry amended(PublicationIssue issue, User actor, String reason, List<String> archivePaths) {
        IssueAuditEntry entry = write(issue, "AMENDED", ActorKind.USER, actor, reason, Map.of());
        if (archivePaths != null && !archivePaths.isEmpty()) {
            entry.setArchivePath(String.join(",", archivePaths));
        }
        return entry;
    }

    public IssueAuditEntry created(PublicationIssue issue, User actor, String action) {
        return write(issue, action, actor == null ? ActorKind.SYSTEM : ActorKind.USER, actor, null, Map.of());
    }

    public IssueAuditEntry override(PublicationIssue issue, User actor, String action,
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
    public IssueAuditEntry edited(PublicationIssue issue, User actor, String action,
                                  Map<String, Object> detail) {
        return write(issue, action, actor == null ? ActorKind.SYSTEM : ActorKind.USER, actor, null,
                detail == null ? Map.of() : detail);
    }

    /** A series-level event. DM-Q2: the audit is generalised, so this has no issue. */
    /**
     * Which model serves this series to the public, changed.
     *
     * Its own entry rather than a SERIES_ACTIVATED with a note, because this is
     * the one change an anonymous reader can see happen: before it, the public
     * list comes from one place and after it from another. The detail carries
     * both ends and whether the readiness precondition was overridden, so the
     * question "who flipped this, and did they know it was not ready" has an
     * answer that does not depend on anyone remembering.
     */
    public IssueAuditEntry seriesAuthority(PublicationSeries series, User actor,
                                           Map<String, Object> detail, String reason) {
        assertKnown("SERIES_AUTHORITY_CHANGED");
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setSeries(series);
        entry.setAction("SERIES_AUTHORITY_CHANGED");
        entry.setActorKind(actor == null ? ActorKind.SYSTEM : ActorKind.USER);
        entry.setUser(actor);
        entry.setReason(reason);
        entry.setDetail(detail);
        em.persist(entry);
        return entry;
    }

    public IssueAuditEntry series(PublicationSeries series, User actor, String action, String reason) {
        assertKnown(action);
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setSeries(series);
        entry.setAction(action);
        entry.setActorKind(actor == null ? ActorKind.SYSTEM : ActorKind.USER);
        entry.setUser(actor);
        entry.setReason(reason);
        em.persist(entry);
        return entry;
    }

    /** The importer's entries: real events, but nobody's action. */
    public IssueAuditEntry imported(PublicationIssue issue, String note) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("note", note);
        return write(issue, "IMPORTED", ActorKind.IMPORT, null, note, detail);
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

    private IssueAuditEntry write(PublicationIssue issue, String action, ActorKind actorKind,
                                  User user, String reason, Map<String, Object> detail) {
        assertKnown(action);
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setIssue(issue);
        entry.setAction(action);
        entry.setActorKind(actorKind);
        entry.setUser(user);
        entry.setReason(reason);
        entry.setDetail(detail);
        em.persist(entry);
        return entry;
    }

    private static void assertKnown(String action) {
        if (!ACTIONS.contains(action)) {
            throw new UnknownAuditActionException(action);
        }
    }
}
