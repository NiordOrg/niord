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

    /** One rail row. */
    public record CheckRow(String code, Severity severity, boolean passed,
                           boolean acknowledgeable, String detail) {
    }

    /** The whole rail, and whether it permits publishing. */
    public record Checklist(List<CheckRow> rows, boolean canPublish, List<String> blockingCodes) {
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
        PublicationIssue predecessor = neighbour(issue, series, true);
        boolean chained = predecessor == null
                || (issue.getIntervalFrom() != null && predecessor.getCutoffStampedAt() != null
                && issue.getIntervalFrom().equals(predecessor.getCutoffStampedAt()));
        rows.add(row("INTERVAL_CHAINED", Severity.WARN, chained,
                predecessor == null ? "no predecessor" : "predecessor stamped " + predecessor.getCutoffStampedAt()));

        // 4. ONLY where bytes must already exist.
        //
        // This is the check that would otherwise deadlock the whole feature:
        // gating publish on a file that publish itself writes means no
        // query-backed issue could ever be published. It applies to uploaded and
        // link-backed content, where the bytes are a precondition rather than an
        // output.
        boolean filesRequired = series.getContentMode() == ContentMode.UPLOADED_FILE;
        boolean filesPresent = !filesRequired || issue.getDescs().stream()
                .allMatch(d -> d.getFilePath() != null && !d.getFilePath().isBlank());
        rows.add(row("FILE_PRESENT_PER_LANGUAGE", Severity.BLOCK, filesPresent,
                filesRequired ? "uploaded content must already have bytes"
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
        PublicationIssue successor = neighbour(issue, series, false);
        rows.add(row("CUTOFF_AFTER_PREVIOUS", Severity.BLOCK,
                predecessor == null || predecessor.getCutoffStampedAt() == null
                        || proposedCutoff.after(predecessor.getCutoffStampedAt()),
                predecessor == null ? "no predecessor" : "must be after " + predecessor.getCutoffStampedAt()));

        rows.add(row("CUTOFF_BEFORE_SUCCESSOR", Severity.BLOCK,
                successor == null || successor.getCutoffStampedAt() == null
                        || proposedCutoff.before(successor.getCutoffStampedAt()),
                successor == null ? "no successor" : "must be before " + successor.getCutoffStampedAt()));

        // 9
        rows.add(row("CUTOFF_NOT_FUTURE", Severity.BLOCK,
                allowFuture || !proposedCutoff.after(new Date()),
                allowFuture ? "future cut-offs explicitly allowed" : "cut-off is " + proposedCutoff));

        // 10 to 15 need the resolver.
        // The EFFECTIVE document -- criteriaOverride where the issue carries one.
        // The rail's whole claim is "this is what would go out if you pressed
        // publish", so resolving the series' document while publish resolves the
        // override would make the rail describe a different issue than the one it
        // is offering to release.
        MemberResolutionService.Resolution resolution = null;
        if (queryBacked && series.getTimeRelation() != null) {
            try {
                ResolvedCriteria criteria = EffectiveCriteria.resolvedFor(issue);
                if (criteria != null) {
                    resolution = resolver.resolve(criteria,
                            new Interval(issue.getIntervalFrom(), proposedCutoff));
                }
            } catch (RuntimeException e) {
                resolution = null;
            }
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
                aliveButWithdrawn.map(w -> w.count() + " member(s) cancelled or expired but still open at the cut-off")
                        .orElse("none")));

        // Purely informational for an in-force series: overlap is what they do.
        boolean overlaps = resolution != null
                && resolution.warning(ResolutionWarningCode.OVERLAPPING_ISSUE).isPresent();
        rows.add(row("OVERLAPPING_ISSUE", Severity.WARN, !overlaps,
                interval ? "issues of this series tile" : "in-force issues overlap by design"));

        List<String> blocking = new ArrayList<>();
        for (CheckRow r : rows) {
            if (r.severity() == Severity.BLOCK && !r.passed()) {
                blocking.add(r.code());
            }
        }
        return new Checklist(rows, blocking.isEmpty(), blocking);
    }

    private CheckRow row(String code, Severity severity, boolean passed, String detail) {
        return new CheckRow(code, severity, passed, false, detail);
    }

    /** The nearest neighbour in the bracket, using the same status filter as publish. */
    private PublicationIssue neighbour(PublicationIssue issue, PublicationSeries series, boolean before) {
        Date pivot = issue.getIntervalFrom() == null ? new Date() : issue.getIntervalFrom();
        String order = before ? "DESC" : "ASC";
        String comparison = before ? "<" : ">";
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
