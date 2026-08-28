package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.series.vo.LiveMessageStateVo;
import org.niord.core.publication.series.vo.IssueOverrideVo;
import org.niord.core.publication.series.vo.MemberCurationVo;
import org.niord.core.user.User;
import org.niord.model.message.Status;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The member list of one issue, with what has moved under it since.
 *
 * A published issue's member rows are frozen and stay frozen: they record what
 * was printed, and the archived document is the proof. But the messages
 * themselves go on living -- a notice is cancelled, a type is corrected, an
 * open-ended validity gets an end date -- and none of that is visible from the
 * snapshot, which is exactly why an admin looking at a three-week-old issue
 * cannot tell whether it still describes the world.
 *
 * So the divergence is COMPUTED and SURFACED, and the snapshot is not touched.
 * Healing it would be the one unrecoverable move here: the row would then agree
 * with today and disagree with the PDF that went out, and nothing would record
 * that they ever differed.
 *
 * It is a service in core rather than logic on the endpoint because the rules --
 * which fields count as drift, whether the list is live or frozen, what a
 * curated row reveals -- are the interesting part and the web layer has no
 * container tests to pin them with.
 */
@ApplicationScoped
public class IssueMemberListService {

    /**
     * How many uids go into one live-facts lookup.
     *
     * The whole point of reading the live side in one query is that a member list
     * runs to hundreds of rows and a per-row lookup would be hundreds of
     * round-trips. The chunk exists only because an IN-list has a practical
     * ceiling, and it is the same bound the resolver already uses.
     */
    private static final int LOOKUP_CHUNK = 1000;

    @Inject
    EntityManager em;

    @Inject
    IssueCurationService curation;

    /**
     * Every curation decision that STANDS on this issue, include and exclude alike.
     *
     * The exclusions are the reason this exists. An excluded message is not a
     * member -- that is what excluding it means -- so it has no row in the member
     * list for a "withdraw this decision" affordance to hang off, and the only
     * other record of it is the audit trail. The trail records what HAPPENED,
     * not what stands: an exclude followed by a clear leaves two entries and no
     * decision, and reading the trail as a state is how a withdrawn exclusion
     * comes back to life on a screen.
     *
     * CURATOR TIER, like the writes it describes. It carries the author and the
     * reason, which is the admin-only half of a why-line.
     */
    @Transactional
    public List<IssueOverrideVo> standingDecisions(PublicationIssue issue) {
        List<IssueOverrideVo> out = new ArrayList<>();
        for (IssueOverride override : curation.forIssue(issue)) {
            IssueOverrideVo vo = new IssueOverrideVo();
            fillCuration(vo, override);
            vo.setMessageUid(override.getMessageUid());
            out.add(vo);
        }
        return out;
    }

    /**
     * The rows, in print order.
     *
     * Live-versus-frozen is keyed off the issue's STATUS and nothing else. A
     * request parameter would let a caller ask a published issue what it would
     * contain today, and that answer looks authoritative while describing a
     * document nobody published.
     */
    @Transactional
    public List<IssueMemberVo> members(PublicationIssue issue) {
        List<IssueMember> frozen = em.createQuery(
                        "SELECT m FROM IssueMember m WHERE m.issue = :i ORDER BY m.sortIndex",
                        IssueMember.class)
                .setParameter("i", issue)
                .getResultList();

        // Every curation decision on this issue, in one query and indexed by uid.
        // The member's own foreign key answers for rows frozen after the override
        // was taken; an imported row carries the decision without the link, and a
        // live list has no member rows to hang a key on at all -- so the uid is
        // the fallback, and it is the key every one of them shares.
        Map<String, IssueOverride> overrides = new LinkedHashMap<>();
        for (IssueOverride o : em.createQuery(
                        "SELECT o FROM IssueOverride o LEFT JOIN FETCH o.author WHERE o.issue = :i",
                        IssueOverride.class)
                .setParameter("i", issue)
                .getResultList()) {
            overrides.put(o.getMessageUid(), o);
        }

        boolean frozenList = issue.getStatus() == IssueStatus.PUBLISHED
                || issue.getStatus() == IssueStatus.RETIRED;
        Map<String, LiveFacts> live = frozenList ? liveFacts(frozen) : Map.of();

        List<IssueMemberVo> out = new ArrayList<>();
        for (IssueMember m : frozen) {
            IssueMemberVo vo = new IssueMemberVo();
            vo.setMessageUid(m.getMessageUid());
            vo.setSortIndex(m.getSortIndex());
            vo.setFrozenShortId(m.getFrozenShortId());
            vo.setFrozenType(m.getFrozenType());
            vo.setFrozenStatus(m.getFrozenStatus());
            vo.setFrozenPublishDateFrom(m.getFrozenPublishDateFrom());
            vo.setFrozenPublishDateTo(m.getFrozenPublishDateTo());
            vo.setSource(m.getSource() == null ? null : m.getSource().name());
            // DERIVED from source and the snapshot relation, never a stored column:
            // a stored derivable value is a second source of truth that can
            // disagree with the first.
            vo.setReasonCode(deriveReason(m, issue));
            vo.setReasonNote(m.getReasonNote());

            IssueOverride override = m.getOverride() != null
                    ? m.getOverride() : overrides.get(m.getMessageUid());
            vo.setCuration(curationOf(override));

            if (frozenList) {
                applyDrift(vo, m, live.get(m.getMessageUid()));
            }
            out.add(vo);
        }
        return out;
    }

    /**
     * The reason code for one member.
     *
     * Public and static so it can be checked without a database. It is the one
     * value on the row that is derived rather than read, and a derivation nobody
     * can test directly is a derivation that drifts.
     */
    public static String deriveReason(IssueMember m, PublicationIssue issue) {
        if (m.getSource() == MemberSource.OVERRIDE_INCLUDE) {
            return "MANUAL_INCLUDE";
        }
        if (m.getSource() == MemberSource.IMPORTED) {
            return "IMPORTED";
        }
        return "IN_FORCE_AT_CUTOFF".equals(issue.getSnapshotTimeRelation())
                ? "IN_FORCE_AT_CUTOFF" : "IN_INTERVAL";
    }

    // ------------------------------------------------------------------ drift

    /** The live values membership and the printed row depend on. */
    private record LiveFacts(String type, String status, Date publishDateTo) {
    }

    /**
     * Every member's live row, in ONE query.
     *
     * Reading the message entity per member would be a query per row on a list
     * that regularly runs past two hundred, on a screen that is opened to answer
     * a single question. The projection is deliberate too: three scalars is all
     * the comparison needs, and selecting whole Message entities would drag their
     * parts, areas and geometry along for nothing.
     */
    private Map<String, LiveFacts> liveFacts(List<IssueMember> members) {
        List<String> uids = new ArrayList<>();
        for (IssueMember m : members) {
            if (m.getMessageUid() != null) {
                uids.add(m.getMessageUid());
            }
        }
        Map<String, LiveFacts> out = new LinkedHashMap<>();
        for (int from = 0; from < uids.size(); from += LOOKUP_CHUNK) {
            List<String> chunk = uids.subList(from, Math.min(from + LOOKUP_CHUNK, uids.size()));
            for (Object[] row : em.createQuery(
                            "SELECT m.uid, m.type, m.status, m.publishDateTo FROM Message m"
                                    + " WHERE m.uid IN (:uids)", Object[].class)
                    .setParameter("uids", chunk)
                    .getResultList()) {
                out.put((String) row[0],
                        new LiveFacts(row[1] == null ? null : row[1].toString(),
                                row[2] == null ? null : row[2].toString(),
                                (Date) row[3]));
            }
        }
        return out;
    }

    /**
     * What has moved, and what it moved to.
     *
     * The three compared fields are the ones a frozen row stores BECAUSE they are
     * mutable in production: type is editor-writable and unversioned, status
     * changes on every withdrawal, and publishDateTo is null while a notice is
     * still open and gets a value the moment it closes -- which is the single
     * most common drift on the estate and the one a null-unsafe comparison would
     * miss entirely.
     *
     * A message that is GONE drifts too, and says so under its own name rather
     * than reporting three nulls that read like empty values.
     */
    private static void applyDrift(IssueMemberVo vo, IssueMember member, LiveFacts live) {
        List<String> drift = new ArrayList<>();
        LiveMessageStateVo current = new LiveMessageStateVo();

        if (live == null) {
            drift.add("exists");
            current.setExists(false);
            current.setPubliclyVisible(false);
        } else {
            current.setExists(true);
            current.setType(live.type());
            current.setStatus(live.status());
            current.setPubliclyVisible(isPublic(live.status()));

            if (!Objects.equals(member.getFrozenType(), live.type())) {
                drift.add("type");
            }
            if (!Objects.equals(member.getFrozenStatus(), live.status())) {
                drift.add("status");
            }
            // Compared as instants rather than as objects: two Date instances of
            // the same millisecond are not equal under Timestamp's own equals,
            // and the values come back from JDBC as Timestamps.
            if (!sameInstant(member.getFrozenPublishDateTo(), live.publishDateTo())) {
                drift.add("publishDateTo");
            }
        }

        if (drift.isEmpty()) {
            return;
        }
        vo.setDrift(drift);
        vo.setCurrent(current);
    }

    private static boolean sameInstant(Date a, Date b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.getTime() == b.getTime();
    }

    /**
     * Whether the live status is one the public may read.
     *
     * Derived from the enum rather than listed. A literal list drifts the first
     * time a status is added, and the answer here decides whether a member of a
     * published issue is still readable at all.
     */
    private static boolean isPublic(String status) {
        if (status == null) {
            return false;
        }
        try {
            return Status.valueOf(status).isPublic();
        } catch (IllegalArgumentException e) {
            // A status this build does not know is not one it can call public.
            return false;
        }
    }

    // ------------------------------------------------------------------ curation

    /**
     * The curation facts a why-line renders, or null where nobody curated.
     *
     * Null rather than an empty object: "no human touched this row" and "a human
     * touched it and left no reason" are different facts, and the reason column
     * is NOT NULL precisely so the second cannot happen.
     */
    static MemberCurationVo curationOf(IssueOverride override) {
        if (override == null) {
            return null;
        }
        MemberCurationVo vo = new MemberCurationVo();
        fillCuration(vo, override);
        return vo;
    }

    /**
     * The curation facts onto whichever shape is carrying them.
     *
     * One filler for the member row's block and the standing-decisions list, so
     * an EXCLUDE's reason and an INCLUDE's why-line cannot come to disagree about
     * what a decision says.
     */
    static void fillCuration(MemberCurationVo vo, IssueOverride override) {
        vo.setKind(override.getKind() == null ? null : override.getKind().name());
        vo.setAuthor(authorOf(override.getAuthor()));
        vo.setAt(override.getCreated());
        vo.setReason(override.getReason());
    }

    /** A curator by the name a colleague would recognise, falling back to the login. */
    private static String authorOf(User author) {
        if (author == null) {
            return null;
        }
        String name = author.getName();
        return name == null || name.isBlank() ? author.getUsername() : name;
    }
}
