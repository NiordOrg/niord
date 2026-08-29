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

import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.model.DataFilter;
import org.niord.model.publication.PublicationDescVo;
import org.niord.model.publication.PublicationType;
import org.niord.model.publication.PublicationVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * An issue, in the shape the public API and the citation machinery already speak.
 *
 * The point is that nothing downstream has to learn about issues. The public
 * list, the citation resolver, {@code extract-message-publication} and
 * {@code update-message-publications} all consume {@code PublicationVo} and
 * {@code SystemPublicationVo}; give them an issue wearing that shape and every
 * one of them works unchanged, including the parts of the legacy editor that
 * nobody is going to rewrite.
 *
 * PublicationVo is a published XSD and no field may be added to it, so the
 * mapping is a projection rather than an extension: everything an issue knows
 * that a publication does not simply has nowhere to go, and that is correct.
 *
 * Pure and static. It reads an entity graph and returns value objects; there is
 * nothing here that needs a database, and keeping it that way is what lets the
 * citation-format rule be tested without one.
 */
public final class IssuePublicationMapping {

    private static final Logger log = LoggerFactory.getLogger(IssuePublicationMapping.class);

    private IssuePublicationMapping() {
    }

    /** The public shape: what an anonymous caller may see of an issue. */
    public static PublicationVo toPublicationVo(PublicationIssue issue, String lang) {
        return fill(issue, new PublicationVo(), lang);
    }

    /**
     * The system shape.
     *
     * Carries the three fields the citation machinery reads and nothing more of
     * consequence: {@code publicationId}, {@code messagePublication} -- which
     * decides whether the citation lands in the public or the internal field --
     * and per-language {@code link} plus {@code messagePublicationFormat}.
     */
    public static SystemPublicationVo toSystemPublicationVo(PublicationIssue issue, String lang) {
        SystemPublicationVo vo = fill(issue, new SystemPublicationVo(), lang);
        if (vo == null) {
            return null;
        }

        PublicationSeries series = issue.getSeries();
        vo.setMainType(PublicationMainType.PUBLICATION);
        vo.setStatus(issue.getStatus() == IssueStatus.PUBLISHED
                ? PublicationStatus.ACTIVE
                : PublicationStatus.DRAFT);
        vo.setRepoPath(issue.getRepoPath());
        vo.setLanguageSpecific(true);
        if (series != null) {
            vo.setMessagePublication(series.getMessagePublication());
            vo.setDomain(series.getDomain() == null ? null : series.getDomain().toVo());
        }
        return vo;
    }

    /**
     * The citation format for one language, or null when there is none.
     *
     * The order is the whole rule:
     *
     *   1. the ISSUE desc's messageReferenceFormat -- an issue may have been
     *      given its own wording, and that wording wins;
     *   2. else the SERIES pattern, with this issue's naming tokens substituted
     *      -- which is what makes "EfS ${week}/${year}" become "EfS 33/2017"
     *      without anybody typing it per issue;
     *   3. else nothing.
     *
     * Null rather than an exception, because whether "no format" is fatal
     * depends on the caller: listing an issue must not fail, citing INTO that
     * language must. Legacy resolved this by writing the four characters
     * " null" into the field and rendering them to the public site.
     *
     * ${parameters} deliberately survives: it belongs to the moment of citing,
     * not to the issue.
     */
    public static String citationFormat(PublicationIssue issue, String lang) {
        if (issue == null || lang == null) {
            return null;
        }

        PublicationIssueDesc issueDesc = descOf(issue, lang);
        if (issueDesc != null && notBlank(issueDesc.getMessageReferenceFormat())) {
            return issueDesc.getMessageReferenceFormat();
        }

        PublicationSeries series = issue.getSeries();
        if (series == null) {
            return null;
        }
        PublicationSeriesDesc seriesDesc = series.getDescs() == null ? null
                : series.getDescs().stream()
                        .filter(d -> lang.equals(d.getLang()))
                        .findFirst().orElse(null);

        if (seriesDesc == null || !notBlank(seriesDesc.getMessageReferenceFormat())) {
            return null;
        }

        IssueNaming.Numbers numbers = numbersOf(issue);
        if (numbers == null) {
            log.warn("issue {} has no date to derive citation numbers from; no citation format in {}",
                    issue.getPublicId(), lang);
            return null;
        }

        try {
            return IssueNaming.expandCitation(seriesDesc.getMessageReferenceFormat(), numbers);
        } catch (RuntimeException e) {
            // A pattern that cannot expand is a series-validation problem, and it
            // must not become a half-expanded citation stored in a message. But it
            // is NOT the same thing as having no format, and the caller can only
            // report what it is told -- so the reason is logged here or it is lost,
            // and the editor is sent looking for a missing format that is present.
            log.warn("series {} has a citation format in {} that cannot expand for issue {}: {}",
                    series.getSeriesId(), lang, issue.getPublicId(), e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ internals

    private static <P extends PublicationVo> P fill(PublicationIssue issue, P vo, String lang) {
        if (issue == null) {
            return null;
        }

        PublicationSeries series = issue.getSeries();

        vo.setPublicationId(issue.getPublicId());
        vo.setCreated(issue.getCreated());
        vo.setUpdated(issue.getUpdated());
        vo.setCategory(series == null || series.getCategory() == null
                ? null
                : series.getCategory().toVo(DataFilter.get().lang(lang)));

        // The public window, never the content interval. An issue covering week
        // 33 becomes publicly current when week 33 CLOSES; mapping the interval
        // here is the one-period-offset failure the ledger flags twice.
        vo.setPublishDateFrom(issue.getPublicFrom());
        vo.setPublishDateTo(issue.getPublicTo());

        List<PublicationDescVo> descs = new ArrayList<>();
        // descOf() and citationFormat() both guard this, so a null collection is
        // reachable. Unguarded here it NPEs out of listVo and takes down the whole
        // public listing rather than omitting one row.
        for (PublicationIssueDesc desc : descsOf(issue)) {
            if (lang != null && !lang.equals(desc.getLang())) {
                continue;
            }
            descs.add(descVo(issue, desc));
        }
        vo.setDescs(descs);

        // LINK when there is somewhere to send a reader, REPOSITORY when the file
        // is ours, NONE when neither. Derived rather than stored: an issue's file
        // can arrive after it is created, and the type has to follow.
        vo.setType(typeOf(issue));

        return vo;
    }

    private static PublicationDescVo descVo(PublicationIssue issue, PublicationIssueDesc desc) {
        PublicationDescVo out = new PublicationDescVo();
        out.setLang(desc.getLang());
        out.setTitle(desc.getName());
        out.setFileName(desc.getFileName());
        out.setLink(linkOf(desc));
        out.setMessagePublicationFormat(citationFormat(issue, desc.getLang()));
        return out;
    }

    /** An explicit link wins; otherwise the repository path the file was written to. */
    /**
     * D-8. Relative for repository-hosted files, absolute only for an external link.
     *
     * filePath is a STORAGE path -- publications/a/8e/<id>/EfS.pdf -- and is not
     * fetchable. What a client needs is the repository URL that serves it, which
     * is what legacy stores for all 2,128 of its repository-hosted descs:
     * /rest/repo/file/<path>. Emitting the bare storage path gives every natively
     * published issue a link that 404s, while the imported rows beside it work,
     * because those carry legacy's link verbatim.
     *
     * An explicit link is returned untouched: that is the EXTERNAL_LINK case, and
     * it is absolute by definition.
     */
    static String linkOf(PublicationIssueDesc desc) {
        if (notBlank(desc.getLink())) {
            return desc.getLink();
        }
        return notBlank(desc.getFilePath()) ? REPO_FILE_PREFIX + desc.getFilePath() : null;
    }

    /** What the repository serves its files under. */
    static final String REPO_FILE_PREFIX = "/rest/repo/file/";

    private static PublicationType typeOf(PublicationIssue issue) {
        boolean anyLink = descsOf(issue).stream().anyMatch(d -> notBlank(d.getLink()));
        if (anyLink) {
            return PublicationType.LINK;
        }
        boolean anyFile = descsOf(issue).stream().anyMatch(d -> notBlank(d.getFilePath()));
        return anyFile ? PublicationType.REPOSITORY : PublicationType.NONE;
    }

    /** The descs, never null. One guard, so the callers cannot each forget it differently. */
    private static List<PublicationIssueDesc> descsOf(PublicationIssue issue) {
        return issue.getDescs() == null ? List.of() : issue.getDescs();
    }

    private static PublicationIssueDesc descOf(PublicationIssue issue, String lang) {
        return descsOf(issue).stream()
                .filter(d -> lang.equals(d.getLang()))
                .findFirst().orElse(null);
    }

    /**
     * The naming numbers for this issue.
     *
     * Taken from the STORED week and year where they exist, and derived from the
     * cut-off only as a fallback. A published issue's numbers were fixed when it
     * was published; re-deriving them would let a later change to the series
     * timezone silently renumber a citation that is already in print.
     */
    private static IssueNaming.Numbers numbersOf(PublicationIssue issue) {
        Integer edition = editionNumber(issue);
        ZoneId zone = zoneOf(issue.getSeries());

        // The best available basis for the calendar tokens, in order. Month and day
        // were previously hard-coded to 1 when there was no cut-off stamp, so a
        // DAILY series whose citation format uses the day and month tokens --
        // added for exactly that cadence -- printed 1-1 into the message and
        // stored it there.
        Date basis = issue.getCutoffStampedAt() != null ? issue.getCutoffStampedAt()
                : issue.getIntervalTo() != null ? issue.getIntervalTo()
                : issue.getIntervalFrom();

        if (basis == null) {
            return null;
        }

        if (issue.getWeek() != null && issue.getYear() != null) {
            // The STORED week and year win -- they were fixed when the issue was
            // published, and re-deriving them would let a later timezone change
            // renumber a citation already in print. Month and day come from the
            // basis, because they are not stored at all.
            ZonedDateTime at = Instant.ofEpochMilli(basis.getTime()).atZone(zone);
            return new IssueNaming.Numbers(issue.getWeek(), issue.getWeekTo(), issue.getYear(),
                    at.getMonthValue(), at.getDayOfMonth(), edition);
        }

        return IssueNaming.derive(basis, issue.getIntervalFrom(), zone, edition);
    }

    /** The edition, where it is a number. A non-numeric edition simply has no token value. */
    private static Integer editionNumber(PublicationIssue issue) {
        if (!notBlank(issue.getEdition())) {
            return null;
        }
        try {
            return Integer.valueOf(issue.getEdition().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ZoneId zoneOf(PublicationSeries series) {
        return series == null ? ZoneId.of("UTC") : series.cutoffZone();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
