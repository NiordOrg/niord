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
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.ResolutionWarningVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.service.BaseService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The release rail: what the server says about whether this issue may publish.
 *
 * Server-authoritative on purpose. A rail computed in the browser is a rail that
 * disagrees with the thing that actually enforces it, and the disagreement only
 * shows up when somebody is trying to release.
 *
 * Fourteen codes, and all fourteen ship together. Shipping a subset means the UI
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
     *
     * `applicable` says whether this issue can be in the condition the row
     * describes at all. All fourteen rows are emitted for every issue -- a client
     * that renders only the rows it received cannot tell "this check passed" from
     * "this check does not exist" -- but a row about a question this issue does
     * not raise is not an answer about this issue, and counting it as one is how
     * an uploaded issue ends up showing a warning that says "0 members" and
     * cannot be cleared, because there is no query to run and no curation to fix.
     *
     * No inapplicable row is a BLOCK row that fails, and that is the invariant
     * the publish gate rests on: the gate refuses on BLOCK rows that did not
     * pass, so nothing it reads changes. The one inapplicable row that can still
     * report a failure -- MEMBERS_RESOLVED, saying the resolver did not run -- is
     * a WARN, described rather than enforced.
     *
     * `detail` is the English sentence and `detailCode` + `detailParams` are the
     * same statement as a key and its values. All three ship, and the reason is
     * that they have different readers. A screen has to render the sentence in
     * the reader's language, so it needs a key it can translate and the numbers
     * to interpolate; an API caller and a log have nobody to translate for them,
     * and the publish gate composes its refusal sentences out of `detail`
     * directly. A row is one statement, and the code is per SENTENCE VARIANT
     * rather than per row -- "the MEMBER_LIMIT row" says several different things
     * depending on what it found, and none of them is translatable as a row name.
     */
    public record CheckRow(String code, Severity severity, boolean passed, boolean applicable,
                           boolean acknowledgeable, String acknowledgeCode, String detail,
                           String detailCode, Map<String, Object> detailParams) {

        /** A row that applies: the default, and the only shape most callers want. */
        public CheckRow(String code, Severity severity, boolean passed,
                        boolean acknowledgeable, String acknowledgeCode, Detail detail) {
            this(code, severity, passed, true, acknowledgeable, acknowledgeCode, detail);
        }

        /** The same, saying for itself whether it applies. */
        public CheckRow(String code, Severity severity, boolean passed, boolean applicable,
                        boolean acknowledgeable, String acknowledgeCode, Detail detail) {
            this(code, severity, passed, applicable, acknowledgeable, acknowledgeCode,
                    detail.text(), detail.code(), detail.params());
        }
    }

    /**
     * What a row says about itself, in the two forms it has to say it in.
     *
     * ONE EXPRESSION HOLDS ALL THREE PARTS -- the code, the values, and the English
     * -- because they are one statement and any arrangement that separates them
     * lets them drift. A sentence reworded without its params is a translation
     * that renders the wrong number; a code added without its sentence is a row
     * that reads as blank to every API caller.
     *
     * {@code code} is the stable key a client translates: one per SENTENCE
     * VARIANT rather than one per row, because a row says several different
     * things depending on what it found and "the MEMBER_LIMIT row" is not a
     * sentence anybody can translate. {@code params} carries the values the
     * sentence interpolates, typed and unformatted -- an instant as epoch
     * milliseconds and its zone beside it, never a rendered date, so the client
     * formats it the way it formats every other instant on the screen.
     * {@code text} is the English, kept for the API reader, for the log, and for
     * the refusal sentences the publish gate composes out of it.
     */
    public record Detail(String code, Map<String, Object> params, String text) {
    }

    /**
     * Why a row does not apply, and how that reads.
     *
     * An enum rather than free text so the reason is a key: an inapplicable row
     * is still rendered, and "not applicable" alone tells the person deciding
     * whether to release nothing at all. The phrase is here beside the constant
     * for the same reason {@link Detail} keeps its three parts together.
     */
    public enum Inapplicable {

        /** The series selects no content by query, so it has no period to require. */
        NOT_QUERY_BACKED("the series does not select its content by query"),

        /** First issue of the series: there is no chain to be in and no floor to clear. */
        NO_PREDECESSOR("no predecessor"),

        /** Last issue of the series: no ceiling to stay under. */
        NO_SUCCESSOR("no successor"),

        /** The bytes this row would demand are what publishing itself writes. */
        PUBLISH_GENERATES_FILE("publish generates the file"),

        /** Neither bytes nor a link: the series is not a document at all. */
        NO_DOCUMENT("the series carries no document"),

        /** Nothing is rendered for this series, so no report has to be configured. */
        NOTHING_RENDERED("nothing is rendered for this series"),

        /** Nothing may cite this series, so it needs no reference format. */
        NOT_CITABLE("series is not citable"),

        /** The caller waived the future check, which means it was not made. */
        FUTURE_ALLOWED("future cut-offs explicitly allowed"),

        /** No query, no curation: there is no member list to be asked about. */
        NO_MEMBERSHIP("the series resolves no member list"),

        /**
         * The series' issues do not tile, so a row that presumes they do is
         * asking a question this series cannot be in the wrong about.
         */
        IN_FORCE_SERIES("in-force series");

        private final String phrase;

        Inapplicable(String phrase) {
            this.phrase = phrase;
        }

        /** How the reason reads to the admin deciding whether to release. */
        public String phrase() {
            return phrase;
        }
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
            "NO_INEFFECTIVE_OVERRIDES",
            "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF",
            "OVERLAPPING_ISSUE");

    @Inject
    IssueResolutionService resolutions;

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
        ZoneId zone = zoneOf(series);
        return ZonedDateTime.ofInstant(instant.toInstant(), zone).format(CHECKLIST_STAMP)
                + " (" + zone.getId() + ")";
    }

    /** The zone a cut-off of this series means, which is its domain's and nothing else. */
    private static ZoneId zoneOf(PublicationSeries series) {
        return series == null ? ZoneId.of("UTC") : series.cutoffZone();
    }

    /**
     * The same instant for a client that renders it itself.
     *
     * Epoch milliseconds and the zone id, never the formatted string: the client
     * shows every other instant on the screen in the session's own format, and a
     * date pre-rendered by the server is the one date on the page that looks
     * foreign. Null stays null -- "not set" is a sentence, not a date.
     */
    private static Long epoch(Date instant) {
        return instant == null ? null : instant.getTime();
    }

    /**
     * The rail for a proposed cut-off, taking its own resolve.
     *
     * The publish path and the dialog both call it: they name an instant and
     * nothing else about the issue is already in hand.
     */
    @Transactional
    public Checklist compute(PublicationIssue issue, Date proposedCutoff, boolean allowFuture) {
        return compute(issue, allowFuture, resolutions.forIssue(issue, proposedCutoff));
    }

    /**
     * The same rail, off a resolution the caller already took.
     *
     * THE CUT-OFF COMES FROM THE RESOLUTION, which is what makes the two forms one
     * answer rather than two: a rail computed against a member set taken at a
     * different instant is a rail describing a release nobody is about to make.
     * The issue screen resolves once and hands that resolution to the member list
     * and to this, so the count on the rail and the length of the list beside it
     * cannot differ.
     *
     * @param pre the resolution this rail is answered from; its instant IS the
     *            proposed cut-off
     */
    @Transactional
    public Checklist compute(PublicationIssue issue, boolean allowFuture,
                             IssueResolutionService.IssueResolution pre) {
        Date proposedCutoff = pre.at();
        PublicationSeries series = issue.getSeries();
        List<CheckRow> rows = new ArrayList<>();

        boolean queryBacked = series.getContentMode() == ContentMode.GENERATED_FROM_QUERY;
        boolean interval = series.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;

        // An IN-FORCE series: one whose issues answer "what is in force at this
        // instant" instead of each covering a period of its own. Consecutive
        // editions therefore share every message that stayed in force across both
        // of them, and such an issue has no lower bound to chain off -- creating one
        // with an intervalFrom is refused outright.
        //
        // Two rows presume the opposite, that the issues TILE -- each opening where
        // the one before it closed, covering a span nothing else covers -- and on an
        // in-force series both were a permanent, uncleanable warning: INTERVAL_CHAINED
        // failing against an intervalFrom an in-force issue does not have, and
        // OVERLAPPING_ISSUE failing against an overlap that is the entire point of
        // the series.
        //
        // Asked as IN_FORCE_AT_CUTOFF rather than as "not PUBLISHED_IN_INTERVAL",
        // and the difference is the whole estate of uploaded, link-backed and one-off
        // series: those carry NO time relation at all -- S-1 requires it null,
        // because only a query-backed series has one -- so a negated test would
        // sweep every one of them in and tell the admin their PDF series is
        // "in-force". It is not; it is neither.
        //
        // Their issues do not tile either, though: the shaping gives a lower bound
        // to a PUBLISHED_IN_INTERVAL issue only, so INTERVAL_CHAINED has nothing to
        // compare on them and would warn on every issue after the first, for ever.
        // It is therefore not applicable on every series that does not tile -- with
        // the reason that fits the kind. OVERLAPPING_ISSUE is different: it hangs
        // off the member list, and a series without one already says so.
        boolean inForce = series.getTimeRelation() == TimeRelation.IN_FORCE_AT_CUTOFF;

        // 1
        rows.add(row("ISSUE_OPEN", Severity.BLOCK,
                issue.getStatus() == IssueStatus.OPEN
                        && (series.getStatus() == SeriesStatus.ACTIVE || series.getStatus() == SeriesStatus.RETIRED),
                detail("ISSUE_OPEN.status",
                        "status is " + issue.getStatus() + ", series is " + series.getStatus(),
                        "status", issue.getStatus() == null ? null : issue.getStatus().name(),
                        "seriesStatus", series.getStatus() == null ? null : series.getStatus().name())));

        // 2. The interval is a property of membership: a series that selects
        // nothing by query has no period for the rail to require.
        rows.add(queryBacked
                ? row("INTERVAL_PRESENT", Severity.BLOCK,
                        (interval) == (issue.getIntervalFrom() != null),
                        detail("INTERVAL_PRESENT.under",
                                "intervalFrom " + (issue.getIntervalFrom() == null ? "absent" : "present")
                                        + " under " + series.getTimeRelation(),
                                "present", issue.getIntervalFrom() != null,
                                "timeRelation", series.getTimeRelation() == null
                                        ? null : series.getTimeRelation().name()))
                : notApplicable("INTERVAL_PRESENT", Severity.BLOCK, Inapplicable.NOT_QUERY_BACKED));

        // 3. A warning, not a block: a deliberate gap is legitimate. And there is
        // no chain to be in when this is the first issue of the series -- nor when
        // the series' issues do not tile, whether because they are in force or
        // because the series selects nothing by query.
        PublicationIssue predecessor = neighbour(issue, series, proposedCutoff, true);
        rows.add(!interval
                ? notApplicable("INTERVAL_CHAINED", Severity.WARN,
                        inForce ? Inapplicable.IN_FORCE_SERIES : Inapplicable.NOT_QUERY_BACKED)
                : predecessor == null
                        ? notApplicable("INTERVAL_CHAINED", Severity.WARN, Inapplicable.NO_PREDECESSOR)
                        : row("INTERVAL_CHAINED", Severity.WARN,
                                issue.getIntervalFrom() != null && predecessor.getCutoffStampedAt() != null
                                        && issue.getIntervalFrom().equals(predecessor.getCutoffStampedAt()),
                                detail("INTERVAL_CHAINED.gap",
                                        "predecessor stamped "
                                                + at(predecessor.getCutoffStampedAt(), series),
                                        "at", epoch(predecessor.getCutoffStampedAt()),
                                        "zone", zoneOf(series).getId())));

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
        if (filesRequired) {
            rows.add(row("FILE_PRESENT_PER_LANGUAGE", Severity.BLOCK,
                    issue.getDescs().stream()
                            .allMatch(d -> d.getFilePath() != null && !d.getFilePath().isBlank()),
                    detail("FILE_PRESENT_PER_LANGUAGE.bytes",
                            "uploaded content must already have bytes")));
        } else if (linkRequired) {
            rows.add(row("FILE_PRESENT_PER_LANGUAGE", Severity.BLOCK,
                    issue.getDescs().stream()
                            .allMatch(d -> d.getLink() != null && !d.getLink().isBlank()),
                    detail("FILE_PRESENT_PER_LANGUAGE.link",
                            "link-backed content must already have a link")));
        } else {
            rows.add(notApplicable("FILE_PRESENT_PER_LANGUAGE", Severity.BLOCK,
                    queryBacked ? Inapplicable.PUBLISH_GENERATES_FILE : Inapplicable.NO_DOCUMENT));
        }

        // 5
        rows.add(queryBacked
                ? row("REPORT_CONFIGURED", Severity.BLOCK, series.getReportId() != null,
                        detail("REPORT_CONFIGURED.reportId", "reportId " + series.getReportId(),
                                "reportId", series.getReportId()))
                : notApplicable("REPORT_CONFIGURED", Severity.BLOCK, Inapplicable.NOTHING_RENDERED));

        // 6
        boolean citable = series.getMessagePublication() != null
                && series.getMessagePublication() != org.niord.core.publication.vo.MessagePublication.NONE;
        rows.add(citable
                ? row("REFERENCE_FORMAT_COMPLETE", Severity.BLOCK,
                        issue.getSeries().getDescs().stream()
                                .allMatch(d -> d.getMessageReferenceFormat() != null
                                        && !d.getMessageReferenceFormat().isBlank()),
                        detail("REFERENCE_FORMAT_COMPLETE.citable", "series is citable"))
                : notApplicable("REFERENCE_FORMAT_COMPLETE", Severity.BLOCK,
                        Inapplicable.NOT_CITABLE));

        // 7 and 8. The neighbour bracket -- and an end of the chain is an absent
        // bound rather than a satisfied one.
        //
        // These two hold for an in-force series as well, and deliberately: they ask
        // whether the editions are in ORDER, not whether they tile. A new edition of
        // an in-force publication stamped before the one it replaces is wrong in
        // exactly the way it is wrong for a tiling series.
        PublicationIssue successor = neighbour(issue, series, proposedCutoff, false);
        rows.add(predecessor == null || predecessor.getCutoffStampedAt() == null
                ? notApplicable("CUTOFF_AFTER_PREVIOUS", Severity.BLOCK, Inapplicable.NO_PREDECESSOR)
                : row("CUTOFF_AFTER_PREVIOUS", Severity.BLOCK,
                        proposedCutoff.after(predecessor.getCutoffStampedAt()),
                        detail("CUTOFF_AFTER_PREVIOUS.after",
                                "must be after " + at(predecessor.getCutoffStampedAt(), series),
                                "at", epoch(predecessor.getCutoffStampedAt()),
                                "zone", zoneOf(series).getId())));

        rows.add(successor == null || successor.getCutoffStampedAt() == null
                ? notApplicable("CUTOFF_BEFORE_SUCCESSOR", Severity.BLOCK, Inapplicable.NO_SUCCESSOR)
                : row("CUTOFF_BEFORE_SUCCESSOR", Severity.BLOCK,
                        proposedCutoff.before(successor.getCutoffStampedAt()),
                        detail("CUTOFF_BEFORE_SUCCESSOR.before",
                                "must be before " + at(successor.getCutoffStampedAt(), series),
                                "at", epoch(successor.getCutoffStampedAt()),
                                "zone", zoneOf(series).getId())));

        // 9. Waived means the check was not made, not that it held.
        rows.add(allowFuture
                ? notApplicable("CUTOFF_NOT_FUTURE", Severity.BLOCK, Inapplicable.FUTURE_ALLOWED)
                : row("CUTOFF_NOT_FUTURE", Severity.BLOCK, !proposedCutoff.after(new Date()),
                        detail("CUTOFF_NOT_FUTURE.at", "cut-off is " + at(proposedCutoff, series),
                                "at", epoch(proposedCutoff),
                                "zone", zoneOf(series).getId())));

        // 10 to 14 need the resolver.
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
        //
        // Both of those now arrive on the resolution rather than being read here:
        // one resolve per screen, taken at one instant, shared with the member
        // list it has to agree with.
        MemberResolutionService.Resolution resolution = pre.resolution();

        // Whether this issue HAS a member list to be asked about, which is exactly
        // the condition under which a resolve was attempted.
        //
        // Without it the five membership rows answered for every issue in the
        // system, including the ones whose content is a file somebody uploaded.
        // "0 members" as an outstanding warning on every uploaded and link-backed
        // issue is a warning nobody can clear and nobody should try to: there is
        // no query to run and no curation to fix. A resolve that was ATTEMPTED
        // and failed is a different thing entirely and still warns, which is why
        // this asks what the series is rather than whether `resolution` is null.
        boolean membership = pre.membership();

        int memberCount = resolution == null ? 0 : resolution.members().size();
        rows.add(new CheckRow("MEMBERS_RESOLVED", resolution == null ? Severity.WARN : Severity.OK,
                resolution != null, membership, false, null,
                membership
                        ? detail("MEMBERS_RESOLVED.count", memberCount + " members",
                                "count", memberCount)
                        : detailFor(Inapplicable.NO_MEMBERSHIP)));

        rows.add(membership
                ? row("MEMBER_LIMIT", Severity.BLOCK,
                        memberCount <= MemberResolutionService.MEMBER_LIMIT,
                        detail("MEMBER_LIMIT.of",
                                memberCount + " of " + MemberResolutionService.MEMBER_LIMIT,
                                "count", memberCount,
                                "limit", MemberResolutionService.MEMBER_LIMIT))
                : notApplicable("MEMBER_LIMIT", Severity.BLOCK, Inapplicable.NO_MEMBERSHIP));

        boolean noStale = resolution == null
                || resolution.warning(ResolutionWarningCode.STALE_OVERRIDE).isEmpty();
        rows.add(membership
                ? row("NO_INEFFECTIVE_OVERRIDES", Severity.WARN, noStale,
                        noStale
                                ? detail("NO_INEFFECTIVE_OVERRIDES.none", "every override applies")
                                : detail("NO_INEFFECTIVE_OVERRIDES.stale",
                                        "an override no longer refers to a candidate"))
                : notApplicable("NO_INEFFECTIVE_OVERRIDES", Severity.WARN,
                        Inapplicable.NO_MEMBERSHIP));

        // The one acknowledgeable row. An exclusions panel cannot show this class
        // at all -- those messages ARE members.
        //
        // It carries its acknowledgement code whether or not it applies. The gate
        // compares that code against what the admin ticked, and a row that dropped
        // it on the way out would be a refusal the dialog has no control for.
        var aliveButWithdrawn = resolution == null
                ? Optional.<ResolutionWarningVo>empty()
                : resolution.warning(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE);
        rows.add(new CheckRow("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF", Severity.WARN,
                aliveButWithdrawn.isEmpty(), membership, true,
                ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE.name(),
                membership
                        ? aliveButWithdrawn
                                .map(w -> detail("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF.count",
                                        w.count() + " member(s) cancelled or expired after the "
                                                + "cut-off, still included",
                                        "count", w.count()))
                                .orElseGet(() -> detail("CANCELLED_MEMBERS_ALIVE_AT_CUTOFF.none",
                                        "none"))
                        : detailFor(Inapplicable.NO_MEMBERSHIP)));

        // Two issues of one series sharing members -- which only means anything
        // where the issues are supposed to tile. An in-force series answers "what
        // is in force now", so consecutive editions share every message that stayed
        // in force across both, and the row was reporting that as a warning on
        // every single in-force issue with no action that could ever clear it.
        //
        // The producer is wired here, on the predecessor the bracket already found.
        // Left unwired, the row rendered "no other issue covers this period" as
        // SATISFIED on every issue in the system -- a check that cannot fail is
        // worse than an absent one, because the screen states an answer nobody
        // computed.
        // Not taken at all where the row does not apply: it reads every frozen
        // member row of the neighbouring issue, and an in-force series is the one
        // whose neighbours are largest.
        var overlap = !inForce && membership
                ? overlapWith(predecessor, resolution)
                : Optional.<ResolutionWarningVo>empty();
        rows.add(inForce
                ? notApplicable("OVERLAPPING_ISSUE", Severity.WARN, Inapplicable.IN_FORCE_SERIES)
                : membership
                        ? row("OVERLAPPING_ISSUE", Severity.WARN, overlap.isEmpty(),
                                overlap.map(w -> detail("OVERLAPPING_ISSUE.shared",
                                                w.count() + " member(s) also belong to '"
                                                        + predecessor.getPublicId() + "'",
                                                "count", w.count(),
                                                "issue", predecessor.getPublicId()))
                                        .orElseGet(() -> detail("OVERLAPPING_ISSUE.tile",
                                                "issues of this series tile")))
                        : notApplicable("OVERLAPPING_ISSUE", Severity.WARN,
                                Inapplicable.NO_MEMBERSHIP));

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
    private Optional<ResolutionWarningVo> overlapWith(
            PublicationIssue predecessor, MemberResolutionService.Resolution resolution) {
        if (resolution == null || predecessor == null || predecessor.getId() == null) {
            return Optional.empty();
        }
        Set<String> theirs = new LinkedHashSet<>(em.createQuery(
                        "SELECT m.messageUid FROM IssueMember m WHERE m.issue = :i", String.class)
                .setParameter("i", predecessor)
                .getResultList());
        if (theirs.isEmpty()) {
            return Optional.empty();
        }
        return MemberResolutionService.overlappingIssue(resolution.members(), theirs);
    }

    private CheckRow row(String code, Severity severity, boolean passed, Detail detail) {
        return new CheckRow(code, severity, passed, true, false, null, detail);
    }

    /**
     * One statement of a row: its key, its English, and the values both use.
     *
     * The params arrive as alternating name and value so the call site reads as
     * one expression -- the code, the sentence and the numbers it interpolates
     * sitting together, where a reader can see that they agree. Split across a
     * builder or a separate map they drift silently: a reworded sentence keeps
     * the old params, and the translated row then states a number the English one
     * does not.
     *
     * Values go in UNFORMATTED -- a count as an int, an instant as epoch
     * milliseconds with its zone beside it -- because the client renders them in
     * the session's own locale and zone. A pre-rendered date here would be the
     * one date on the screen that does not match the rest of it.
     */
    private static Detail detail(String code, String text, Object... params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "detail params for " + code + " are name/value pairs; got " + params.length);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            map.put(String.valueOf(params[i]), params[i + 1]);
        }
        return new Detail(code, Collections.unmodifiableMap(map), text);
    }

    /**
     * A row whose condition this issue cannot be in, and which passes vacuously.
     *
     * Still emitted, still rendered, and still passing -- what changes is that it
     * says so, so a reader counting what the rail actually decided does not count
     * a check that never ran.
     *
     * Not every inapplicable row can be built here: MEMBERS_RESOLVED reports a
     * resolver that did not run and CANCELLED_MEMBERS_ALIVE_AT_CUTOFF carries an
     * acknowledgement code it must keep, so both name their own applicability.
     * What holds for all of them is the one thing the publish gate depends on: no
     * inapplicable row is a BLOCK row that fails.
     */
    private CheckRow notApplicable(String code, Severity severity, Inapplicable reason) {
        return new CheckRow(code, severity, true, false, false, null, detailFor(reason));
    }

    /**
     * The detail an inapplicable row carries in place of an answer nobody computed.
     *
     * The reason is written out rather than left to the code alone, because the
     * person reading the row is the one deciding whether to release -- and it
     * carries its own key, {@code NOT_APPLICABLE.<REASON>}, so that reason is
     * translated rather than shown in English on a Danish screen.
     */
    private static Detail detailFor(Inapplicable reason) {
        return new Detail("NOT_APPLICABLE." + reason.name(), Map.of(),
                "not applicable: " + reason.phrase());
    }

    /**
     * The bracket this issue sits in: the issues still covering the periods either
     * side of it.
     *
     * A RETIRED neighbour is not in the bracket. It no longer covers its period --
     * that is what withdrawing it said -- so it neither raises the floor a cut-off
     * has to clear nor lowers the ceiling it has to stay under. Counting it did
     * both: an issue created to replace a withdrawn one was refused
     * CUTOFF_AFTER_SUCCESSOR against the very issue it was replacing, at any
     * instant inside the period the two share, which is every instant such an
     * issue can sensibly be stamped at.
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
                .setParameter("st", IssuePublishService.COVERING_STATUSES)
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
