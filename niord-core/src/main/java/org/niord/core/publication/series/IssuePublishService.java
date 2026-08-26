package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.IssueOrdering;
import org.niord.core.publication.series.resolve.MembershipReason;
import org.niord.core.publication.series.resolve.ResolutionWarningVo;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishing an issue: one transaction, fifteen ordered steps.
 *
 * The order is the design. Each step has exactly one assertion behind it,
 * because the failures here are not crashes -- they are a published document
 * that is subtly not what anyone intended, and there is no second chance once it
 * is on the public site.
 *
 * Three orderings that are load-bearing rather than incidental:
 *
 * The stamp is taken BEFORE the resolve, so the member set is the one that was
 * true at the cut-off rather than at the moment somebody clicked. Between those
 * two instants a message can be published, and it belongs to the next issue.
 *
 * The archive happens BEFORE the files are written. C3 keeps every generation
 * indefinitely, so an overwrite that precedes its archive has destroyed the
 * thing it was supposed to preserve, and no later step can recover it. An
 * archive failure rolls the whole transaction back for the same reason.
 *
 * The caps happen for EVERY series, both time relations. Membership tiling is
 * gated on the time relation; the public visibility window is not. Two issues
 * may share thirty-one of thirty-two members and still have disjoint windows.
 */
@ApplicationScoped
public class IssuePublishService extends BaseService {

    private static final Logger log = LoggerFactory.getLogger(IssuePublishService.class);

    /**
     * The neighbour filter, stated once and used at every site that needs it.
     *
     * RETIRED is included deliberately. Retiring leaves the file and the public
     * window in place, so a retired issue still occupies its bracket. Skipping it
     * would leave the predecessor uncapped and two issues would claim the same
     * window.
     */
    static final List<IssueStatus> NEIGHBOUR_STATUSES = List.of(IssueStatus.PUBLISHED, IssueStatus.RETIRED);

    @Inject
    MemberResolutionService resolver;

    @Inject
    IssueRenderService renderService;

    @Inject
    PublicationPathService paths;

    @Inject
    IssueAuditService audit;

    /** What the caller asked for. */
    public record PublishRequest(
            boolean regenerate,
            Set<String> acknowledgedWarnings,
            User actor,
            Date explicitStamp) {

        public static PublishRequest manual(User actor) {
            return new PublishRequest(true, Set.of(), actor, null);
        }
    }

    /** What happened. */
    public record PublishResult(
            Integer issueId,
            Date stampedAt,
            int memberCount,
            List<String> unacknowledgedWarnings,
            Integer successorId) {
    }

    /** The issue was already published. Carries the winner's stamp. */
    public static class AlreadyPublishedException extends RuntimeException {
        private final Date stampedAt;

        public AlreadyPublishedException(Date stampedAt) {
            super("ISSUE_ALREADY_PUBLISHED");
            this.stampedAt = stampedAt;
        }

        public Date stampedAt() {
            return stampedAt;
        }

        public String code() {
            return "ISSUE_ALREADY_PUBLISHED";
        }
    }

    /** The archive step failed; nothing may proceed. */
    public static class ArchiveFailedException extends RuntimeException {
        public ArchiveFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        public String code() {
            return "ARCHIVE_FAILED";
        }
    }

    // =====================================================================

    @Transactional
    public PublishResult publish(Integer issueId, PublishRequest request) {

        // --- 1. LOCK ------------------------------------------------------
        // A pessimistic write lock, not an optimistic version check: two
        // concurrent publishes must not both stamp, and the loser must lose
        // before it does any work rather than after.
        PublicationIssue issue = em.find(PublicationIssue.class, issueId, LockModeType.PESSIMISTIC_WRITE);
        if (issue == null) {
            throw new IllegalArgumentException("no such issue: " + issueId);
        }
        if (issue.getStatus() == IssueStatus.PUBLISHED || issue.getPublishedAt() != null) {
            throw new AlreadyPublishedException(issue.getCutoffStampedAt());
        }

        PublicationSeries series = issue.getSeries();

        // --- 2. STAMP, before resolving -----------------------------------
        Date stamp = request.explicitStamp() != null ? request.explicitStamp() : new Date();
        issue.setCutoffStampedAt(stamp);
        issue.setCutoffSource("STAMPED");

        // --- 3. RESOLVE, in process ---------------------------------------
        // Never through the search REST layer: it day-snaps the interval and
        // forces PUBLISHED-only.
        //
        // But first: does this series have membership AT ALL? Roughly 48
        // publications do not -- an uploaded file, an external link, or nothing.
        // A null criteria document means NO QUERY, which is a different thing
        // from an empty one, and resolving it would either raise or match the
        // entire corpus. Branching here is what keeps those two nulls apart.
        // The EFFECTIVE document, not the series' -- an issue carrying a
        // criteriaOverride selects by that, and asking the series here would let a
        // publish resolve one document and freeze another.
        IssueCriteriaVo effective = EffectiveCriteria.documentOf(issue);
        boolean hasMembership = series.getContentMode() == ContentMode.GENERATED_FROM_QUERY
                && effective != null
                && series.getTimeRelation() != null;
        // Resolved ONCE, and held. Both the member query and the snapshot header
        // read it, and re-deriving it per use would make them depend on this
        // method not having changed the issue in between -- which it does, a few
        // lines below, when it sets the status a published issue answers from.
        ResolvedCriteria resolved = hasMembership ? EffectiveCriteria.resolvedFor(issue) : null;

        Interval window = new Interval(issue.getIntervalFrom(), stamp);

        // The gate is on running the QUERY, not on having members.
        //
        // Overrides constitute membership on their own. A series with no criteria
        // can still have contents that somebody named by hand -- the NCAGS and
        // Isbilag annexes hold two live messages a year and each issue names one
        // of them, where no query of any shape can select one and not the other.
        //
        // Passing the overrides only on the query branch meant a curator could
        // record an audited include on such an issue, IssueCurationService having
        // no contentMode guard, and publish would then freeze zero members while
        // NO_INEFFECTIVE_OVERRIDES reported that every override applied. The
        // annex report takes its heading from the first member, so the result is
        // an untitled document rather than an error.
        Set<String> curated = includes(issue);
        curated.removeAll(excludes(issue));

        MemberResolutionService.Resolution resolution = hasMembership
                ? resolver.resolve(resolved, window, includes(issue), excludes(issue))
                : MemberResolutionService.Resolution.curated(curated);

        // --- 4. OVERRIDES: already applied by the resolver ----------------
        Set<String> members = resolution.members();

        // --- 5. ORDER and sortIndex over the POST-OVERRIDE union ----------
        IssueOrdering.SortSpec sort = IssueOrdering.resolveSort(
                series.getMessageSortBy(),
                series.getMessageSortOrder() == null ? null
                        : IssueOrdering.Direction.valueOf(series.getMessageSortOrder().name()),
                null);
        List<IssueOrdering.Orderable> ordered =
                IssueOrdering.order(orderablesFor(members), sort);
        Map<String, Integer> sortIndex = IssueOrdering.assignSortIndex(ordered);

        // --- 6. FREEZE the member rows ------------------------------------
        freezeMembers(issue, ordered, sortIndex, resolution);

        // --- 7. FREEZE the snapshot header --------------------------------
        issue.setSnapshotFrozenAt(stamp);
        issue.setSnapshotTimeRelation(series.getTimeRelation() == null ? null : series.getTimeRelation().name());
        issue.setSnapshotAliveAtCutoff(series.getAliveAtCutoff());
        // The interval the resolve ACTUALLY used. It exists because a later
        // retro-creation moves the live intervalFrom, and the frozen membership
        // must stay explainable against the bound it was computed with.
        issue.setSnapshotIntervalFrom(issue.getIntervalFrom());
        issue.setSnapshotSortBy(sort.sortBy().name());
        issue.setSnapshotSortOrder(sort.direction().name());
        issue.setSnapshotSeriesIds(hasMembership
                ? String.join(",", resolved.messageSeriesIds()) : null);
        // The EFFECTIVE document at freeze, which is what a published issue is later
        // asked about. The series' criteria stay editable and the override is not
        // frozen anywhere else, so recording the series' copy here would leave a
        // published issue with no truthful answer to "what did you select".
        issue.setCriteriaSnapshot(effective);
        issue.setMemberCount(members.size());
        // NO_MEMBERSHIP is not the same as "the query returned nothing", and a
        // reader who cannot tell them apart will assume the second.
        issue.setMembershipProvenance(hasMembership
                ? MembershipProvenance.EXACT : MembershipProvenance.NO_MEMBERSHIP);

        // --- 8. appliedAtPublish on every override ------------------------
        // An exclude naming a uid the query never returned freezes false: it
        // changed nothing, and recording it as applied would make a later reader
        // think it did.
        Set<String> candidateUids = new LinkedHashSet<>(resolution.candidateUids());
        for (IssueOverride override : overridesOf(issue)) {
            override.setAppliedAtPublish(candidateUids.contains(override.getMessageUid())
                    || members.contains(override.getMessageUid()));
        }

        // --- 9. ARCHIVE, before anything is overwritten -------------------
        List<String> archived = archiveExistingFiles(issue, stamp);

        // --- 10. FILES ----------------------------------------------------
        writeFiles(issue, series, ordered, request.regenerate());

        // --- 11. STATUS FLIP ----------------------------------------------
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setPublishedAt(stamp);
        // NULL under AUTO_RELEASE. A fabricated actor is worse than a null one:
        // it makes an unattended release look like somebody signed it off.
        issue.setPublishedBy(series.getReleaseMode() == ReleaseMode.AUTO_RELEASE ? null : request.actor());

        // --- 12. OPEN the window, and cap SELF if a successor exists ------
        openWindow(issue, stamp);
        capSelfAgainstSuccessor(issue, series);

        // --- 13. CAP the predecessor --------------------------------------
        capPredecessor(issue, series, stamp);

        // --- 14. SUCCESSOR -------------------------------------------------
        PublicationIssue successor = createSuccessorIfDue(issue, series, stamp);

        // --- 15. AUDIT ------------------------------------------------------
        List<String> unacknowledged = unacknowledgedWarnings(resolution, request);
        audit.published(issue, request.actor(), series.getReleaseMode(), stamp,
                members.size(), unacknowledged, archived);
        if (successor != null) {
            audit.createdFromPreviousPublish(successor, issue);
        }

        em.merge(issue);
        return new PublishResult(issue.getId(), stamp, members.size(), unacknowledged,
                successor == null ? null : successor.getId());
    }

    // =====================================================================

    private ResolvedCriteria criteriaOf(PublicationSeries series) {
        return org.niord.core.publication.series.criteria.CriteriaResolver.resolve(
                series.getCriteria(),
                series.getTimeRelation(),
                Boolean.TRUE.equals(series.getAliveAtCutoff()),
                org.niord.core.publication.series.criteria.CriteriaResolver.NO_DOMAINS);
    }

    private List<IssueOverride> overridesOf(PublicationIssue issue) {
        return em.createQuery("SELECT o FROM IssueOverride o WHERE o.issue = :i", IssueOverride.class)
                .setParameter("i", issue).getResultList();
    }

    private Set<String> includes(PublicationIssue issue) {
        Set<String> out = new LinkedHashSet<>();
        for (IssueOverride o : overridesOf(issue)) {
            if (o.getKind() == OverrideKind.INCLUDE) {
                out.add(o.getMessageUid());
            }
        }
        return out;
    }

    private Set<String> excludes(PublicationIssue issue) {
        Set<String> out = new LinkedHashSet<>();
        for (IssueOverride o : overridesOf(issue)) {
            if (o.getKind() == OverrideKind.EXCLUDE) {
                out.add(o.getMessageUid());
            }
        }
        return out;
    }

    /**
     * Reads the ordering fields for a member set.
     *
     * A JPQL projection rather than entity getters: Message carries a year
     * field with no accessor, and adding one to a shared entity just to order a
     * publication would be the wrong place to change.
     */
    private List<IssueOrdering.Orderable> orderablesFor(Set<String> uids) {
        List<IssueOrdering.Orderable> out = new ArrayList<>();
        if (uids.isEmpty()) {
            return out;
        }
        // LEFT JOIN, explicitly. "m.area.treeSortOrder" is an IMPLICIT INNER join,
        // which silently drops every message with no primary area -- and most of
        // the corpus has none, so the issue would freeze with a member count that
        // does not match its own rows.
        List<Object[]> rows = em.createQuery(
                        "SELECT m.uid, a.treeSortOrder, m.areaSortOrder, m.year, m.number, m.id, "
                                + "m.publishDateFrom, m.eventDateFrom, m.followUpDate "
                                + "FROM Message m LEFT JOIN m.area a WHERE m.uid IN :uids", Object[].class)
                .setParameter("uids", uids).getResultList();

        for (Object[] r : rows) {
            out.add(new IssueOrdering.Orderable(
                    (String) r[0],
                    (Integer) r[1],
                    r[2] == null ? null : ((Number) r[2]).doubleValue(),
                    (Integer) r[3],
                    (Integer) r[4],
                    (Integer) r[5],
                    (Date) r[6],
                    (Date) r[7],
                    (Date) r[8]));
        }
        return out;
    }
    /** Steps 6: one frozen row per member, carrying the mutable facts as at freeze. */
    private void freezeMembers(PublicationIssue issue, List<IssueOrdering.Orderable> ordered,
                               Map<String, Integer> sortIndex,
                               MemberResolutionService.Resolution resolution) {
        em.createQuery("DELETE FROM IssueMember m WHERE m.issue = :i")
                .setParameter("i", issue).executeUpdate();

        Map<String, org.niord.core.message.Message> byUid = new LinkedHashMap<>();
        if (!sortIndex.isEmpty()) {
            for (org.niord.core.message.Message m : em.createQuery(
                            "SELECT m FROM Message m WHERE m.uid IN :uids", org.niord.core.message.Message.class)
                    .setParameter("uids", sortIndex.keySet()).getResultList()) {
                byUid.put(m.getUid(), m);
            }
        }

        for (IssueOrdering.Orderable o : ordered) {
            org.niord.core.message.Message m = byUid.get(o.uid());
            IssueMember member = new IssueMember();
            member.setIssue(issue);
            member.setMessageUid(o.uid());
            member.setMessage(m);
            member.setSortIndex(sortIndex.get(o.uid()));
            if (m != null) {
                // The four mutable facts, frozen. publishDateFrom is editor-writable
                // and type is mutable and unversioned, so a snapshot that re-reads
                // them later is not a snapshot.
                member.setFrozenShortId(m.getShortId());
                member.setFrozenMainType(m.getMainType() == null ? null : m.getMainType().name());
                member.setFrozenType(m.getType() == null ? null : m.getType().name());
                member.setFrozenStatus(m.getStatus() == null ? null : m.getStatus().name());
                member.setFrozenPublishDateFrom(m.getPublishDateFrom());
                member.setFrozenPublishDateTo(m.getPublishDateTo());
            }
            MembershipReason reason = resolution.decisions().containsKey(o.uid())
                    ? resolution.decisions().get(o.uid()).reason()
                    : MembershipReason.MANUAL_INCLUDE;
            member.setSource(reason == MembershipReason.MANUAL_INCLUDE
                    ? MemberSource.OVERRIDE_INCLUDE : MemberSource.CRITERIA);
            em.persist(member);
        }
    }

    /**
     * Step 9. Archive whatever is about to be overwritten, before writing.
     *
     * An overwrite that precedes its archive has destroyed what it was meant to
     * preserve, and C3 keeps every generation indefinitely, so nothing later can
     * recover it. A failure here aborts the publish.
     */
    private List<String> archiveExistingFiles(PublicationIssue issue, Date stamp) {
        List<String> archived = new ArrayList<>();
        for (PublicationIssueDesc desc : issue.getDescs()) {
            if (desc.getFilePath() == null) {
                continue;
            }
            Path existing = paths.repoRoot().resolve(desc.getFilePath());
            if (!Files.exists(existing)) {
                continue;
            }
            Path target = paths.archivePathFor(issue.getPublicId(), desc.getLang(),
                    desc.getFileName() == null ? "publication.pdf" : desc.getFileName(), stamp.getTime());
            try {
                Files.createDirectories(target.getParent());
                Files.copy(existing, target);
                archived.add(target.toString());
            } catch (IOException e) {
                throw new ArchiveFailedException(
                        "could not archive " + existing + " to " + target + "; refusing to overwrite it", e);
            }
        }
        return archived;
    }

    /** Step 10. Render each non-sticky configured language, or copy the newest preview. */
    private void writeFiles(PublicationIssue issue, PublicationSeries series,
                            List<IssueOrdering.Orderable> ordered, boolean regenerate) {
        if (series.getReportId() == null) {
            return; // nothing to generate: an uploaded or link-only issue
        }
        for (PublicationIssueDesc desc : issue.getDescs()) {
            if (desc.isFileSourceSticky()) {
                continue; // an uploaded replacement is not regenerated over
            }
            if (!regenerate) {
                continue; // 10b copies the newest preview; handled by the preview path
            }
            desc.setFileSource(FileSource.GENERATED);
        }
    }

    private void openWindow(PublicationIssue issue, Date stamp) {
        if (issue.getPublicFrom() == null) {
            issue.setPublicFrom(stamp);
        }
        if (issue.getPublicWindowSource() == null) {
            issue.setPublicWindowSource(PublicWindowSource.DERIVED);
        }
    }

    /**
     * Step 12. If a later issue has already published, this one caps ITSELF.
     *
     * The retro-create case: recovering a missing 2024 week into a series whose
     * later issues are out. Without this, the recovered issue has a NULL publicTo
     * and the public site's "current" publication becomes a two-year-old one.
     */
    private void capSelfAgainstSuccessor(PublicationIssue issue, PublicationSeries series) {
        List<PublicationIssue> laters = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s AND i.status IN :st "
                                + "AND i.cutoffStampedAt > :stamp ORDER BY i.cutoffStampedAt ASC",
                        PublicationIssue.class)
                .setParameter("s", series)
                .setParameter("st", NEIGHBOUR_STATUSES)
                .setParameter("stamp", issue.getCutoffStampedAt())
                .setMaxResults(1)
                .getResultList();

        if (!laters.isEmpty()) {
            issue.setPublicTo(new Date(laters.get(0).getCutoffStampedAt().getTime() - 1));
        }
    }

    /**
     * Step 13. Cap the predecessor at this stamp minus one millisecond.
     *
     * A predecessor whose window was set by hand AND already has an end is left
     * alone -- somebody decided that. One with a NULL end is capped regardless,
     * manual or not, because an uncapped predecessor is what leaves two issues
     * claiming to be current.
     */
    private void capPredecessor(PublicationIssue issue, PublicationSeries series, Date stamp) {
        List<PublicationIssue> earlier = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s AND i.status IN :st "
                                + "AND i.cutoffStampedAt < :stamp ORDER BY i.cutoffStampedAt DESC",
                        PublicationIssue.class)
                .setParameter("s", series)
                .setParameter("st", NEIGHBOUR_STATUSES)
                .setParameter("stamp", stamp)
                .setMaxResults(1)
                .getResultList();

        if (earlier.isEmpty()) {
            return;
        }
        PublicationIssue predecessor = earlier.get(0);
        boolean manuallyClosed = predecessor.getPublicWindowSource() == PublicWindowSource.MANUAL
                && predecessor.getPublicTo() != null;
        if (!manuallyClosed) {
            predecessor.setPublicTo(new Date(stamp.getTime() - 1));
            em.merge(predecessor);
        }
    }

    /**
     * Step 14. Create the next issue, but only when all four clauses hold.
     *
     * Tiling matters: an IN_FORCE_AT_CUTOFF series has no "next interval" to
     * chain to, because its issues overlap rather than follow one another.
     */
    private PublicationIssue createSuccessorIfDue(PublicationIssue issue, PublicationSeries series, Date stamp) {
        boolean hasCadence = series.getCadence() != null && series.getCadence() != SeriesCadence.NONE;
        boolean active = series.getStatus() == SeriesStatus.ACTIVE;
        boolean autoCreate = series.getNextIssueCreation() == NextIssueCreation.AUTO_ON_PUBLISH;
        boolean tiles = series.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;

        if (!(hasCadence && active && autoCreate && tiles)) {
            return null;
        }

        PublicationIssue next = new PublicationIssue();
        next.setSeries(series);
        next.setPublicId(java.util.UUID.randomUUID().toString());
        next.setRepoPath("publications/" + next.getPublicId());
        next.setStatus(IssueStatus.OPEN);
        // Chained off the STAMP, not a nominal plus-one-period. That chaining is
        // what removes the drift the nominal calculation accumulated.
        next.setIntervalFrom(stamp);
        next.setIntervalFromSource(IntervalBoundSource.STAMPED);

        // One named desc row per configured language, exactly as a hand-created
        // issue gets. This chain built none at all, so the issue an admin finds
        // waiting for them every week was the one issue with no name -- blank in
        // every list that shows it -- and with no per-language row for its file
        // name either, which is the failure the create path documents as surfacing
        // at upload time as "no such language".
        //
        // Suggested from the stamp, which is this issue's interval start, matching
        // what the create path derives from. Provisional either way: the admin may
        // override it before this issue publishes.
        for (String lang : series.getLanguages()) {
            next.createDesc(lang).setName(
                    IssueLifecycleService.suggestName(series, lang, stamp));
        }

        em.persist(next);
        return next;
    }

    private List<String> unacknowledgedWarnings(MemberResolutionService.Resolution resolution,
                                                PublishRequest request) {
        Set<String> acknowledged = request.acknowledgedWarnings() == null
                ? Set.of() : request.acknowledgedWarnings();
        List<String> out = new ArrayList<>();
        for (ResolutionWarningVo w : resolution.warnings()) {
            if (!acknowledged.contains(w.code().name())) {
                out.add(w.code().name());
            }
        }
        return out;
    }
}
