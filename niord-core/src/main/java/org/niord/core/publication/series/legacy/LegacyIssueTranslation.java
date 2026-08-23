package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicWindowSource;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
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
     */
    public static PublicationIssue translate(Publication legacy, PublicationSeries series, Date frozenAt) {
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
        issue.setIntervalFrom(legacy.getPublishDateFrom());
        issue.setIntervalTo(legacy.getPublishDateTo());

        // R8's belt and braces: a cadenced series derives its window, and only a
        // genuinely open-ended one-off is MANUAL. Marking a cadenced imported
        // issue MANUAL is what leaves two current EfS issues on the public site
        // at the first native publish. Refined in B5.4c.
        issue.setPublicWindowSource(
                series != null && series.getCadence() != null && series.getCadence() != SeriesCadence.NONE
                        ? PublicWindowSource.DERIVED
                        : PublicWindowSource.MANUAL);

        issue.setRepoPath(legacy.getRepoPath());
        issue.setEdition(legacy.getEdition() == null ? null : String.valueOf(legacy.getEdition()));

        if (legacy.getPublishDateFrom() != null) {
            issue.setYear(yearOf(legacy.getPublishDateFrom()));
        }

        issue.setDescs(descs(legacy));

        // B5.4a2. Derived from THIS publication's own filter, never from the
        // series row -- see IssueSnapshotDeriver for why that distinction is
        // worth a task of its own.
        IssueSnapshotDeriver.derive(issue, legacy, series, frozenAt);

        return issue;
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

    private static List<PublicationIssueDesc> descs(Publication legacy) {
        List<PublicationIssueDesc> out = new ArrayList<>();
        if (legacy.getDescs() == null) {
            return out;
        }
        for (PublicationDesc d : legacy.getDescs()) {
            if (d.getLang() == null || d.getLang().isBlank()) {
                continue;
            }
            PublicationIssueDesc desc = new PublicationIssueDesc();
            desc.setLang(d.getLang());
            desc.setName(d.getTitle());
            desc.setFileName(d.getFileName());
            desc.setLink(d.getLink());
            desc.setFilePath(filePath(legacy, d.getFileName()));
            out.add(desc);
        }
        return out;
    }
}
