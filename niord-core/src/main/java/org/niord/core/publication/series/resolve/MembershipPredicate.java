package org.niord.core.publication.series.resolve;

import org.niord.model.message.Status;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The canonical membership rule, as a pure function.
 *
 * No database, no Quarkus, no entity. That is not a style preference: the rule
 * turns on three-valued logic in two places -- a nullable publishDateFrom and a
 * nullable publishDateTo -- and in SQL those are silent. Here each branch is
 * explicit and a test can pin it, rather than partly testing MySQL's NULL
 * semantics. It also gives the one thing that must not be wrong the fastest
 * feedback loop available: plain JUnit runs in about two seconds, against
 * roughly twenty-five for a Quarkus boot.
 */
public final class MembershipPredicate {

    private MembershipPredicate() {
    }

    /**
     * The public statuses, DERIVED rather than written down.
     *
     * Spelling the triple out by hand is how it drifts: Status.isPublic() is the
     * single definition, and if a status is ever added to it this set follows
     * without anyone having to remember. The emptiness check is not paranoia --
     * an empty set here silently empties every issue in the system.
     */
    static final Set<Status> PUBLIC_STATUSES = Arrays.stream(Status.values())
            .filter(Status::isPublic)
            .collect(Collectors.collectingAndThen(
                    Collectors.toCollection(LinkedHashSet::new),
                    s -> {
                        if (s.isEmpty()) {
                            throw new IllegalStateException(
                                    "no Status is public -- every issue would resolve empty. Check Status.isPublic().");
                        }
                        return Collections.unmodifiableSet(s);
                    }));

    /**
     * Decides one message against one issue's criteria and window.
     *
     * @param m the message's facts, frozen at the moment they were read
     * @param c the series' criteria, already resolved
     * @param i the issue's window; its lower bound is ignored for IN_FORCE_AT_CUTOFF
     */
    public static MemberDecision decide(MessageFacts m, ResolvedCriteria c, Interval i) {
        if (m == null || c == null || i == null) {
            throw new IllegalArgumentException("decide() takes no nulls");
        }

        // RI-1. Derived, never a hardcoded triple.
        if (m.status() == null || !PUBLIC_STATUSES.contains(m.status())) {
            return MemberDecision.excluded(m.uid(), MembershipReason.STATUS_NOT_PUBLIC);
        }

        if (!c.acceptsSeries(m.messageSeriesId()) || !c.acceptsType(m.type())) {
            return MemberDecision.excluded(m.uid(), MembershipReason.CRITERIA_MISMATCH);
        }

        // RI-3. A null publishDateFrom cannot be compared to any bound. Excluded,
        // and reported as such -- one such message would otherwise leak into every
        // issue of the series at once.
        if (m.publishDateFrom() == null) {
            return MemberDecision.excluded(m.uid(), MembershipReason.NO_PUBLISH_DATE);
        }

        final long from = m.publishDateFrom().getTime();
        final long cutoff = i.cutoff().getTime();

        // Closed at the upper bound, for both relations.
        if (from > cutoff) {
            return MemberDecision.excluded(m.uid(), MembershipReason.AFTER_CUTOFF);
        }

        if (c.timeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL) {
            // RI-2. STRICT at the lower bound. Not CriteriaHelper.between(), which is
            // >= and <= and would place a message stamped exactly on a shared cut-off
            // in both adjacent issues at once.
            if (i.previousCutoff() != null && from <= i.previousCutoff().getTime()) {
                return MemberDecision.excluded(m.uid(), MembershipReason.BEFORE_INTERVAL);
            }
        }
        // RI-7. IN_FORCE_AT_CUTOFF applies no lower bound and never consults the
        // previous issue. Issues of such a series overlap; they do not tile.

        // RI-4. NULL-safe: a null publishDateTo means "still open", which is alive.
        // Treating null as though it were a date is what collapses P&T uge 28/2026
        // from 165 members to 47.
        if (c.aliveAtCutoff() && m.publishDateTo() != null && m.publishDateTo().getTime() < cutoff) {
            return MemberDecision.excluded(m.uid(), MembershipReason.NOT_ALIVE_AT_CUTOFF);
        }

        return MemberDecision.included(m.uid(),
                c.timeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL
                        ? MembershipReason.IN_INTERVAL
                        : MembershipReason.IN_FORCE_AT_CUTOFF);
    }

    /** Decides a whole candidate set, preserving order, keyed on uid. */
    public static Map<String, MemberDecision> decideAll(Collection<MessageFacts> candidates,
                                                        ResolvedCriteria c, Interval i) {
        Map<String, MemberDecision> out = new LinkedHashMap<>();
        for (MessageFacts m : candidates) {
            out.put(m.uid(), decide(m, c, i));
        }
        return out;
    }

    /**
     * RI-10. Applies curation on top of the query result: (Q minus excludes) union includes.
     *
     * Keyed on uid throughout. An include overrides the query's verdict, and an
     * exclude overrides an include -- a curator removing something is a later and
     * more specific act than the rule that put it there.
     */
    public static Set<String> applyOverrides(Map<String, MemberDecision> decided,
                                             Set<String> manualIncludes,
                                             Set<String> manualExcludes) {
        Set<String> includes = manualIncludes == null ? Set.of() : manualIncludes;
        Set<String> excludes = manualExcludes == null ? Set.of() : manualExcludes;

        Set<String> members = new LinkedHashSet<>();
        for (Map.Entry<String, MemberDecision> e : decided.entrySet()) {
            if (e.getValue().member()) {
                members.add(e.getKey());
            }
        }
        members.addAll(includes);
        members.removeAll(excludes);
        return members;
    }

    /** The reason to record for a uid once curation has been applied. */
    public static MembershipReason reasonAfterOverrides(String uid, Map<String, MemberDecision> decided,
                                                        Set<String> manualIncludes, Set<String> manualExcludes) {
        if (manualExcludes != null && manualExcludes.contains(uid)) {
            return MembershipReason.MANUAL_EXCLUDE;
        }
        MemberDecision d = decided.get(uid);
        if (d != null && d.member()) {
            return d.reason();
        }
        if (manualIncludes != null && manualIncludes.contains(uid)) {
            return MembershipReason.MANUAL_INCLUDE;
        }
        return d == null ? MembershipReason.CRITERIA_MISMATCH : d.reason();
    }

    /** Convenience for callers that only need the members of a plain candidate list. */
    public static Set<String> members(Collection<MessageFacts> candidates, ResolvedCriteria c, Interval i) {
        return applyOverrides(decideAll(candidates, c, i), Set.of(), Set.of());
    }

    /** The uids excluded for a reason worth surfacing rather than assuming. */
    public static Set<String> reportableOmissions(Map<String, MemberDecision> decided) {
        return decided.values().stream()
                .filter(MemberDecision::isReportableOmission)
                .map(MemberDecision::uid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static long millis(Date d) {
        return d == null ? Long.MIN_VALUE : d.getTime();
    }
}
