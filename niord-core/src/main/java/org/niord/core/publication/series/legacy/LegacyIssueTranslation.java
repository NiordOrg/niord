package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicWindowSource;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.PublicationDesc;
import org.niord.core.publication.vo.PublicationStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * B5.4a. A legacy publication becomes an issue, frozen.
 *
 * ID-SPACE CONTINUITY IS THE ONE-WAY PART. The legacy publicationId becomes the
 * issue's publicId verbatim, and legacyPublicationId records the same string.
 * Minting a fresh UUID would dangle every citation in the archive with no way to
 * find them again -- and there is no second chance, because the citations are
 * bytes inside stored message HTML, not references that can be re-pointed.
 *
 * NEVER KEY ON TITLE OR TAG NAME. Three publications carry a literal ${year} in
 * title, fileName AND tag name, and two of those were publicly released as
 * Skydeomraader-%24%7Byear%7D.pdf and EfS-A-v1-%24%7Byear%7D.pdf. Any keying that
 * goes through a name inherits that, and the corruption is already public.
 *
 * PATHS TRAVEL VERBATIM, REVISION SEGMENT AND ALL (R6). Legacy stores bytes at
 * <repoPath>/<revision>/<fileName> and that exact string is the citation href
 * inside stored message HTML. Re-deriving a flat path would 404 every imported
 * file and every citation pointing at it -- 1,077 public URLs -- and 42 of those
 * rows no longer have their bytes at all, so the path is the only record left.
 */
public final class LegacyIssueTranslation {

    private LegacyIssueTranslation() {
    }

    /**
     * The status mapping, and the reason it is not one-to-one.
     *
     * DRAFT and RECORDING both become OPEN because both mean "not released yet":
     * RECORDING is legacy's in-progress tag-accumulating state, which is what an
     * OPEN issue is. ACTIVE means released, INACTIVE means withdrawn.
     */
    public static IssueStatus statusOf(PublicationStatus legacy) {
        if (legacy == null) {
            return IssueStatus.OPEN;
        }
        return switch (legacy) {
            case DRAFT, RECORDING -> IssueStatus.OPEN;
            case ACTIVE -> IssueStatus.PUBLISHED;
            case INACTIVE -> IssueStatus.RETIRED;
        };
    }

    /**
     * Builds the issue. Never persists; the caller decides that.
     *
     * Writes status, cutoffStampedAt, created and the public window DIRECTLY,
     * bypassing the action machine. That is the one sanctioned write path that
     * does not go through the transactions: these rows are history, and running
     * them through publish() would stamp them with today and fabricate an audit
     * trail that never happened.
     *
     * previousInChain is the publication released immediately before this one in
     * the same chain, or null at the head. Required rather than optional: it is
     * the only thing that says when this issue's content period OPENED, and the
     * alternative -- the value legacy happens to have on this row -- is the public
     * window, which is a different period. See applyContentInterval.
     */
    public static PublicationIssue translate(Publication legacy, PublicationSeries series, Date frozenAt,
                                             Publication previousInChain) {
        PublicationIssue issue = new PublicationIssue();

        issue.setPublicId(legacy.getPublicationId());
        issue.setLegacyPublicationId(legacy.getPublicationId());
        issue.setSeries(series);

        issue.setStatus(statusOf(legacy.getStatus()));
        issue.setCreated(legacy.getCreated());
        issue.setUpdated(legacy.getUpdated());

        // The public window, verbatim. B5.4b recovers the cut-off stamp
        // afterwards; nothing here invents one.
        issue.setPublicFrom(legacy.getPublishDateFrom());
        issue.setPublicTo(legacy.getPublishDateTo());

        applyContentInterval(issue, legacy, series, previousInChain);

        // B5.4c / R8. DERIVED for anything with a cadence, MANUAL only for a
        // genuinely open-ended one-off.
        //
        // Read from the SERIES where there is one and from the publication's own
        // periodicalType where there is not. Keying on the series alone marked 9
        // template-less WEEKLY publications MANUAL -- they are the double-week
        // issues (NtM Week 15-16, EfS 51-52) that were created ad hoc rather than
        // from the template, and they are as cadenced as any other. Marking a
        // cadenced issue MANUAL is exactly what B2.3b step 13 skips by design,
        // and the first native publish would then leave two current EfS issues
        // on the public site at once.
        issue.setPublicWindowSource(isCadenced(legacy, series)
                ? PublicWindowSource.DERIVED
                : PublicWindowSource.MANUAL);

        issue.setRepoPath(legacy.getRepoPath());
        issue.setEdition(legacy.getEdition() == null ? null : String.valueOf(legacy.getEdition()));

        if (legacy.getPublishDateFrom() != null) {
            issue.setYear(yearOf(legacy.getPublishDateFrom()));
        }

        attachDescs(legacy, issue);

        // B5.4a2. Derived from THIS publication's own filter, never from the
        // series row -- see IssueSnapshotDeriver for why that distinction is
        // worth a task of its own.
        IssueSnapshotDeriver.derive(issue, legacy, series, frozenAt);

        return issue;
    }

    /**
     * The CONTENT interval -- which is NOT the public window.
     *
     * Legacy stored ONE window: publishDateFrom to publishDateTo, the period the
     * edition was on the site. The content it carries is the period that CLOSED
     * when that window opened. An EfS published on a Wednesday carries the week up
     * to that Wednesday, not the week after it.
     *
     * Copying the public window into both pairs -- which is what this did -- made
     * every tiling issue claim the period AFTER the one it actually contains.
     * Measured against the frozen members, which are the only witness independent
     * of either window because they were frozen from the legacy message tag: over
     * 40 weekly-ntm issues spanning 2017-2026, 93% of members fall in the
     * PRECEDING period and 6% in the stated one, consistent in every year.
     *
     * Every bound here is RECOVERED. None of it was recorded; all of it is read
     * back out of the release chain, and a reader deciding whether to trust an
     * imported interval needs to see that.
     */
    private static void applyContentInterval(PublicationIssue issue, Publication legacy,
                                             PublicationSeries series, Publication previousInChain) {
        Date released = legacy.getPublishDateFrom();

        // IN_FORCE_AT_CUTOFF has NO lower bound, and that is a fact rather than a
        // gap: the issue carries whatever was still in force at its cut-off,
        // however old, so 2027's firing areas legitimately contain a 2016 notice.
        // intervalTo is that cut-off -- an upper bound, not the end of a period.
        if (series != null && series.getTimeRelation() == TimeRelation.IN_FORCE_AT_CUTOFF) {
            issue.setIntervalFrom(null);
            issue.setIntervalFromSource(null);
            issue.setIntervalTo(released);
            issue.setIntervalToSource(released == null ? null : IntervalBoundSource.RECOVERED.name());
            return;
        }

        // A one-off has no cadence, so it has no preceding period to have closed.
        // Its window is the only period anybody ever described it by, and stepping
        // it back by "one period" would be stepping back by nothing defined.
        if (!isCadenced(legacy, series)) {
            issue.setIntervalFrom(released);
            issue.setIntervalTo(legacy.getPublishDateTo());
            issue.setIntervalFromSource(released == null ? null : IntervalBoundSource.RECOVERED);
            issue.setIntervalToSource(legacy.getPublishDateTo() == null
                    ? null : IntervalBoundSource.RECOVERED.name());
            return;
        }

        // The tiling case. The period runs from the PREVIOUS release to this one,
        // which is what "issues tile" means -- one closes where the next opens.
        // Taken from the chain rather than by subtracting a nominal period, so a
        // week somebody skipped produces one long interval instead of a wrong
        // short one plus a phantom gap.
        Date opened = previousInChain == null ? null : previousInChain.getPublishDateFrom();
        issue.setIntervalFrom(opened);
        issue.setIntervalFromSource(opened == null ? null : IntervalBoundSource.RECOVERED);
        issue.setIntervalTo(released);
        issue.setIntervalToSource(released == null ? null : IntervalBoundSource.RECOVERED.name());
        // The head of a chain keeps a null lower bound. Nothing records when the
        // oldest imported issue began collecting, and inventing a bound there is
        // the same move that produced this defect.
    }

    /**
     * Whether this issue belongs to something that repeats.
     *
     * The series is authoritative when it exists, because that is the row an
     * admin edits. The publication's own periodicalType is the fallback for a
     * template-less row, which has no series to ask.
     */
    public static boolean isCadenced(Publication legacy, PublicationSeries series) {
        if (series != null) {
            return series.getCadence() != null && series.getCadence() != SeriesCadence.NONE;
        }
        return legacy.getPeriodicalType() != null;
    }

    /**
     * The stored file path, verbatim, revision segment included.
     *
     * Kept as a function rather than inlined because the containment rule that
     * B1.7b asserts -- filePath startsWith issue.repoPath -- is only meaningful
     * if both sides are built the same way.
     */
    public static String filePath(Publication legacy, String fileName) {
        if (legacy.getRepoPath() == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        return legacy.getRepoPath() + "/" + legacy.getRevision() + "/" + fileName;
    }

    private static Integer yearOf(Date d) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(d);
        return c.get(java.util.Calendar.YEAR);
    }

    /**
     * Copies the legacy descs onto the issue THROUGH createDesc.
     *
     * createDesc is what sets the back-reference, and the back-reference is what
     * the row is stored by: descs is mappedBy="entity", so a desc built with new
     * and handed to setDescs is still cascaded on save -- with a null entity_id.
     * The issue then reads back with no name and no file path, which is the whole
     * of what an archived issue is.
     */
    private static void attachDescs(Publication legacy, PublicationIssue issue) {
        if (legacy.getDescs() == null) {
            return;
        }
        for (PublicationDesc d : legacy.getDescs()) {
            if (d.getLang() == null || d.getLang().isBlank()) {
                continue;
            }
            PublicationIssueDesc desc = issue.createDesc(d.getLang());
            desc.setName(d.getTitle());
            desc.setFileName(d.getFileName());
            desc.setLink(d.getLink());
            desc.setFilePath(filePath(legacy, d.getFileName()));
        }
    }
}
