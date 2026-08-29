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
import jakarta.transaction.Transactional;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.service.BaseService;

import java.util.ArrayList;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The release rail: what the server says about whether this issue may publish.
 *
 * Server-authoritative on purpose. A rail computed in the browser is a rail that
 * disagrees with the thing that actually enforces it, and the disagreement only
 * shows up when somebody is trying to release.
 *
 * Fifteen codes, and all fifteen ship together. Shipping a subset means the UI
 * renders and translates rows the backend never emits, which reads as "this
 * check passed" rather than "this check does not exist".
 */
@ApplicationScoped
public class PublishChecklistService extends BaseService {

    public enum Severity {
        OK, WARN, BLOCK
    }

    /**
     * One rail row.
     *
     * `acknowledgeCode` is the resolution warning the publish gate compares
     * against, and it is on the row because otherwise every client has to carry
     * its own copy of the mapping. The rail names a CONDITION -- "cancelled
     * members alive at the cut-off" -- while the acknowledgement travels as the
     * warning code the resolver raised, and the two are deliberately not the same
     * string. A frontend translating one into the other by hand is a second
     * definition of the rule, and it goes wrong silently: the publish is refused
     * for a code nobody ticked.
     */
    public record CheckRow(String code, Severity severity, boolean passed,
                           boolean acknowledgeable, String acknowledgeCode, String detail) {
    }

    /**
     * The whole rail, whether it permits publishing, and the resolution it took.
     *
     * The resolution is carried out so that publish can freeze exactly what the
     * rail counted. Re-resolving would cost a second full narrowing on every
     * release AND leave open the outcome this class exists to close: the rail
     * saying 214 members and the frozen snapshot holding a different set.
     */
    public record Checklist(List<CheckRow> rows, boolean canPublish, List<String> blockingCodes,
                            MemberResolutionService.Resolution resolution) {
    }

    /** Every code the rail can emit, in the order it is rendered. */
    public static final List<String> CODES = List.of(
            "ISSUE_OPEN",
            "INTERVAL_PRESENT",
            "INTERVAL_CHAINED",
            "FILE_PRESENT_PER_LANGUAGE",
            "REPORT_CONFIGURED",
            "REFERENCE_FORMAT_COMPLETE",
            "CUTOFF_AFTER_PREVIOUS",
            "CUTOFF_BEFORE_SUCCESSOR",
            "CUTOFF_NOT_FUTURE",
            "MEMBERS_RESOLVED",
            "MEMBER_LIMIT",
            "PREVIEW_FRESH",
            "NO_INEFFECTIVE_OVERRIDES",
            "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF",
            "OVERLAPPING_ISSUE");

    @Inject
    MemberResolutionService resolver;

    @Inject
    IssueCurationService curation;

    @Inject
    org.niord.core.publication.series.criteria.DomainSeriesExpander domains;

    private static final DateTimeFormatter CHECKLIST_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * A cut-off rendered in the zone it is actually read in, and named.
     *
     * These strings are shown to an admin. Concatenating the Date put
     * java.util.Date.toString() on the screen -- "Wed Aug 26 15:44:25 UTC 2026",
     * in the SERVER JVM zone -- and java.sql.Timestamp.toString() for the stamped
     * ones, "2026-07-29 10:16:08.0". Both are unreadable, and the first states a
     * timezone that is not the one the cut-off means: a Copenhagen cut-off shown
     * as UTC is two hours wrong to the person deciding whether to publish.
     *
     * The zone comes from the series' DOMAIN and from nowhere else, and it is
     * named in the output so the reader is never left guessing which one it is.
     */
    private static String at(Date instant, PublicationSeries series) {
        if (instant == null) {
            return "not set";
        }
        ZoneId zone = series == null ? ZoneId.of("UTC") : series.cutoffZone();
        return ZonedDateTime.ofInstant(instant.toInstant(), zone).format(CHECKLIST_STAMP)
                + " (" + zone.getId() + ")";
    }

    @Transactional
    public Checklist compute(PublicationIssue issue, Date proposedCutoff, boolean allowFuture,
                             boolean previewStale) {
        PublicationSeries series = issue.getSeries();
        List<CheckRow> rows = new ArrayList<>();

        boolean queryBacked = series.getContentMode() == ContentMode.GENERATED_FROM_QUERY;
        boolean interval = series.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;

        // 1
        rows.add(row("ISSUE_OPEN", Severity.BLOCK,
                issue.getStatus() == IssueStatus.OPEN
                        && (series.getStatus() == SeriesStatus.ACTIVE || series.getStatus() == SeriesStatus.RETIRED),
                "status is " + issue.getStatus() + ", series is " + series.getStatus()));

        // 2
        boolean intervalOk = !queryBacked
                || ((interval) == (issue.getIntervalFrom() != null));
        rows.add(row("INTERVAL_PRESENT", Severity.BLOCK, intervalOk,
                "intervalFrom " + (issue.getIntervalFrom() == null ? "absent" : "present")
                        + " under " + series.getTimeRelation()));

        // 3. A warning, not a block: a deliberate gap is legitimate.
        PublicationIssue predecessor = neighbour(issue, series, proposedCutoff, true);
        boolean chained = predecessor == null
                || (issue.getIntervalFrom() != null && predecessor.getCutoffStampedAt() != null
                && issue.getIntervalFrom().equals(predecessor.getCutoffStampedAt()));
        rows.add(row("INTERVAL_CHAINED", Severity.WARN, chained,
                predecessor == null ? "no predecessor"
                        : "predecessor stamped " + at(predecessor.getCutoffStampedAt(), series)));

        // 4. ONLY where bytes must already exist.
        //
        // This is the check that would otherwise deadlock the whole feature:
        // gating publish on a file that publish itself writes means no
        // query-backed issue could ever be published. It applies to uploaded and
        // link-backed content, where the bytes are a precondition rather than an
        // output.
        // BOTH precondition modes, which is what the paragraph above always said
        // and the code did not do. An EXTERNAL_LINK issue carries a link instead
        // of bytes, and it was checked for neither -- so it could be published
        // with nothing at all behind it, putting a live publication on the public
        // site that points nowhere.
        boolean filesRequired = series.getContentMode() == ContentMode.UPLOADED_FILE;
        boolean linkRequired = series.getContentMode() == ContentMode.EXTERNAL_LINK;
        boolean contentPresent;
        if (filesRequired) {
            contentPresent = issue.getDescs().stream()
                    .allMatch(d -> d.getFilePath() != null && !d.getFilePath().isBlank());
        } else if (linkRequired) {
            contentPresent = issue.getDescs().stream()
                    .allMatch(d -> d.getLink() != null && !d.getLink().isBlank());
        } else {
            contentPresent = true;
        }
        rows.add(row("FILE_PRESENT_PER_LANGUAGE", Severity.BLOCK, contentPresent,
                filesRequired ? "uploaded content must already have bytes"
                        : linkRequired ? "link-backed content must already have a link"
                                : "not applicable: publish generates the file"));

        // 5
        rows.add(row("REPORT_CONFIGURED", Severity.BLOCK,
                !queryBacked || series.getReportId() != null,
                "reportId " + series.getReportId()));

        // 6
        boolean citable = series.getMessagePublication() != null
                && series.getMessagePublication() != org.niord.core.publication.vo.MessagePublication.NONE;
        boolean formatsComplete = !citable || issue.getSeries().getDescs().stream()
                .allMatch(d -> d.getMessageReferenceFormat() != null && !d.getMessageReferenceFormat().isBlank());
        rows.add(row("REFERENCE_FORMAT_COMPLETE", Severity.BLOCK, formatsComplete,
                citable ? "series is citable" : "not applicable: series is not citable"));

        // 7 and 8. The neighbour bracket.
        PublicationIssue successor = neighbour(issue, series, proposedCutoff, false);
        rows.add(row("CUTOFF_AFTER_PREVIOUS", Severity.BLOCK,
                predecessor == null || predecessor.getCutoffStampedAt() == null
                        || proposedCutoff.after(predecessor.getCutoffStampedAt()),
                predecessor == null ? "no predecessor"
                        : "must be after " + at(predecessor.getCutoffStampedAt(), series)));

        rows.add(row("CUTOFF_BEFORE_SUCCESSOR", Severity.BLOCK,
                successor == null || successor.getCutoffStampedAt() == null
                        || proposedCutoff.before(successor.getCutoffStampedAt()),
                successor == null ? "no successor"
                        : "must be before " + at(successor.getCutoffStampedAt(), series)));

        // 9
        rows.add(row("CUTOFF_NOT_FUTURE", Severity.BLOCK,
                allowFuture || !proposedCutoff.after(new Date()),
                allowFuture ? "future cut-offs explicitly allowed"
                        : "cut-off is " + at(proposedCutoff, series)));

        // 10 to 15 need the resolver.
        // The EFFECTIVE document -- criteriaOverride where the issue carries one.
        // The rail's whole claim is "this is what would go out if you pressed
        // publish", so resolving the series' document while publish resolves the
        // override would make the rail describe a different issue than the one it
        // is offering to release.
        //
        // AND WITH THE CURATION, which is the half that made two of these rows
        // unfailable. Publish resolves through the includes and excludes; a rail
        // that resolved without them reported the PRE-override count as the member
        // count, and STALE_OVERRIDE -- the warning that only exists when there ARE
        // excludes -- could never be raised, so NO_INEFFECTIVE_OVERRIDES answered
        // "every override applies" on an issue whose overrides applied to nothing.
        Set<String> includes = new LinkedHashSet<>();
        Set<String> excludes = new LinkedHashSet<>();
        for (IssueOverride override : curation.forIssue(issue)) {
            (override.getKind() == OverrideKind.INCLUDE ? includes : excludes)
                    .add(override.getMessageUid());
        }

        MemberResolutionService.Resolution resolution = null;
        if (queryBacked && series.getTimeRelation() != null) {
            try {
                ResolvedCriteria criteria = EffectiveCriteria.resolvedFor(issue, domains);
                if (criteria != null) {
                    resolution = resolver.resolve(criteria,
                            new Interval(issue.getIntervalFrom(), proposedCutoff), includes, excludes);
                }
            } catch (RuntimeException e) {
                resolution = null;
            }
        } else if (!includes.isEmpty()) {
            // Overrides constitute membership on their own. The annexes are a
            // series holding two live messages a year with each issue naming one of
            // them, where no query can select one and not the other -- and gating
            // the count on queryBacked reported "0 members" for exactly those.
            Set<String> curated = new LinkedHashSet<>(includes);
            curated.removeAll(excludes);
            resolution = MemberResolutionService.Resolution.curated(curated);
        }

        int memberCount = resolution == null ? 0 : resolution.members().size();
        rows.add(row("MEMBERS_RESOLVED", resolution == null ? Severity.WARN : Severity.OK,
                resolution != null, memberCount + " members"));

        rows.add(row("MEMBER_LIMIT", Severity.BLOCK,
                memberCount <= MemberResolutionService.MEMBER_LIMIT,
                memberCount + " of " + MemberResolutionService.MEMBER_LIMIT));

        rows.add(row("PREVIEW_FRESH", Severity.WARN, !previewStale,
                previewStale ? "the preview predates the current member set" : "preview is current"));

        boolean noStale = resolution == null
                || resolution.warning(ResolutionWarningCode.STALE_OVERRIDE).isEmpty();
        rows.add(row("NO_INEFFECTIVE_OVERRIDES", Severity.WARN, noStale,
                noStale ? "every override applies" : "an override no longer refers to a candidate"));

        // The one acknowledgeable row. An exclusions panel cannot show this class
        // at all -- those messages ARE members.
        var aliveButWithdrawn = resolution == null
                ? java.util.Optional.<org.niord.core.publication.series.resolve.ResolutionWarningVo>empty()
                : resolution.warning(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE);
        rows.add(new CheckRow("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF", Severity.WARN,
                aliveButWithdrawn.isEmpty(), true,
                ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE.name(),
                aliveButWithdrawn.map(w -> w.count() + " member(s) cancelled or expired but still open at the cut-off")
                        .orElse("none")));

        // Purely informational for an in-force series: overlap is what they do.
        //
        // The producer is wired here, on the predecessor the bracket already found.
        // Left unwired, the row rendered "no other issue covers this period" as
        // SATISFIED on every issue in the system -- a check that cannot fail is
        // worse than an absent one, because the screen states an answer nobody
        // computed.
        var overlap = overlapWith(predecessor, resolution);
        rows.add(row("OVERLAPPING_ISSUE", Severity.WARN, overlap.isEmpty(),
                overlap.map(w -> w.count() + " member(s) also belong to '"
                                + predecessor.getPublicId() + "'")
                        .orElse(interval ? "issues of this series tile"
                                : "in-force issues overlap by design")));

        List<String> blocking = new ArrayList<>();
        for (CheckRow r : rows) {
            if (r.severity() == Severity.BLOCK && !r.passed()) {
                blocking.add(r.code());
            }
        }
        return new Checklist(rows, blocking.isEmpty(), blocking, resolution);
    }

    /**
     * Whether this issue and the one before it would print the same messages.
     *
     * Compared against the FROZEN member rows of the neighbour rather than
     * re-resolving it: those rows are what that issue actually published, and a
     * re-resolution would answer for a document that does not exist.
     */
    private java.util.Optional<org.niord.core.publication.series.resolve.ResolutionWarningVo> overlapWith(
            PublicationIssue predecessor, MemberResolutionService.Resolution resolution) {
        if (resolution == null || predecessor == null || predecessor.getId() == null) {
            return java.util.Optional.empty();
        }
        Set<String> theirs = new LinkedHashSet<>(em.createQuery(
                        "SELECT m.messageUid FROM IssueMember m WHERE m.issue = :i", String.class)
                .setParameter("i", predecessor)
                .getResultList());
        if (theirs.isEmpty()) {
            return java.util.Optional.empty();
        }
        return MemberResolutionService.overlappingIssue(resolution.members(), theirs);
    }

    private CheckRow row(String code, Severity severity, boolean passed, String detail) {
        return new CheckRow(code, severity, passed, false, null, detail);
    }

    /**
     * The bracket this issue sits in: the released issues either side of it.
     *
     * PIVOTED ON THE ISSUE'S PLACE IN THE CHAIN, and the place is where its period
     * opens -- not the instant somebody is proposing to stamp. That distinction is
     * the whole point of the bracket: the two cut-off rows ask whether the
     * PROPOSED instant still falls between the neighbours, and a lookup that
     * pivoted on the proposal itself can never find the neighbour the proposal has
     * already stepped past. A cut-off a second below its predecessor would then
     * find no predecessor at all, pass the check, and fail deeper in with an
     * uncoded 500 from the empty interval it built.
     *
     * The predecessor comparison is INCLUSIVE, and that is the routine chain
     * rather than an off-by-one: a successor opens exactly where its predecessor's
     * stamped cut-off closed, so `intervalFrom == predecessor.cutoffStampedAt` for
     * every ordinary issue. A strict comparison excluded the real predecessor and
     * named the one before it, so every ordinary issue showed a broken
     * INTERVAL_CHAINED against the wrong date -- and an admin "correcting" the
     * interval on that advice really did break the chain.
     *
     * An issue with no lower bound has no place but the one being proposed, so it
     * pivots there. Its issues do not tile, so there is no chain to misread.
     */
    private PublicationIssue neighbour(PublicationIssue issue, PublicationSeries series,
                                       Date proposedCutoff, boolean before) {
        Date pivot = issue.getIntervalFrom() != null ? issue.getIntervalFrom()
                : proposedCutoff == null ? new Date() : proposedCutoff;
        String order = before ? "DESC" : "ASC";
        String comparison = before ? "<=" : ">";
        List<PublicationIssue> found = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s AND i.status IN :st "
                                + "AND i.id <> :self AND i.cutoffStampedAt " + comparison + " :pivot "
                                + "ORDER BY i.cutoffStampedAt " + order, PublicationIssue.class)
                .setParameter("s", series)
                .setParameter("st", IssuePublishService.NEIGHBOUR_STATUSES)
                .setParameter("self", issue.getId() == null ? -1 : issue.getId())
                .setParameter("pivot", pivot)
                .setMaxResults(1)
                .getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    /** Every code the rail declares. Used by the coverage test. */
    public static Set<String> declaredCodes() {
        return new LinkedHashSet<>(CODES);
    }
}
