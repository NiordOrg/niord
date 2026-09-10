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

import org.niord.core.publication.series.criteria.CriteriaValidator;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.PublicationOperandResolver;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Editing an open issue: its names, its interval, and everything the series
 * decides that this one edition occasionally has to decide for itself.
 *
 * FOUR GROUPS AND ONE RULE. The printed numbering, the names, the file names and
 * the report (with its parameters) each follow the series until somebody touches
 * them, and a field that has been touched stops following. Which is why every one
 * of them stores a flag or a value ALONGSIDE what it overrides rather than in
 * place of it: the derived week is still there under the printed one, the series'
 * pattern is still there under the typed file name, and "follow the series again"
 * is expressed by clearing the override rather than by typing the derived value
 * back in by hand.
 *
 * The one thing an admin could not do. An issue's name is minted at create from
 * the series' pattern over a PROVISIONAL interval start, and IssueLifecycleService
 * says so in as many words -- "a suggested name, not final; an admin may override
 * it before then". There was no way to. The same for the interval: a recovered
 * period is created from a bound somebody worked out, and getting it wrong meant
 * deleting the issue and creating it again.
 *
 * OPEN ONLY. A published issue's name is on a document people have downloaded and
 * its interval is what the frozen member list was resolved over; changing either
 * would make the record describe something that never happened. The correction
 * path for a published issue is amend, which regenerates the document.
 *
 * TWO KINDS OF NAME, and the distinction is the whole reason the interval edit is
 * safe. A name the series suggested tracks the interval: move the interval and
 * "EfS uge 29" becomes "EfS uge 30", because it was never a name so much as a
 * rendering of the period. A name somebody typed does not track anything -- it is
 * a decision, and re-deriving over it would silently discard it. `nameOverridden`
 * is what tells them apart, it is set by the act of typing one, and it comes off
 * by the act of emptying the field -- which is how a name goes back to being a
 * rendering. Without that second half the distinction is a one-way door: every
 * name written here would set the flag, and a flagged desc is skipped by the
 * shaping for ever.
 */
@ApplicationScoped
public class IssueEditService extends BaseService {

    @Inject
    IssueAuditService audit;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueShape shape;

    @Inject
    PublicationOperandResolver operands;

    /**
     * Only to ask whether a report id names anything.
     *
     * The rendering itself is nowhere near here; what this prevents is a typo
     * surviving a form and failing inside the publish transaction, where the
     * cut-off has already been stamped.
     */
    @Inject
    org.niord.core.report.FmReportService reports;

    /**
     * What an edit may change.
     *
     * Every field is nullable and null means "leave it alone", so a caller
     * changing one thing does not have to send the others back correctly. That
     * matters more than it sounds: a form that round-trips the interval in order
     * to rename an issue will eventually round-trip a stale one.
     *
     * `names` is lang to name. A language absent from the map is untouched; a
     * language present with a blank name goes back to FOLLOWING THE SERIES, by
     * the same convention every other override here uses. The row stays named --
     * the clear drops the flag and leaves the text alone, and the re-shape writes
     * the series' suggestion over it -- because the column is NOT NULL precisely
     * because a nameless issue is unfindable.
     *
     * The document fields are deliberately NOT here. A file and a link have their
     * own endpoints, which archive, guard file-name collisions and audit -- and
     * two write paths to one field is how they end up disagreeing.
     *
     * `criteriaOverride` IS here, and needs its own null convention because null
     * is a meaningful value for it: absent means "leave it alone" like every
     * other field, and `clearCriteriaOverride` is how a caller says "go back to
     * inheriting the series". Without the second flag there would be no way to
     * express the second thing at all.
     *
     * THE OVERRIDE FIELDS SAY "FOLLOW THE SERIES AGAIN" WITH A BLANK, and that is
     * the one convention worth reading twice. `weekLabel`, `weekToLabel`,
     * `yearLabel`, `reportId` and each entry of `fileNames` and of `names` are
     * strings, and a string has an empty form -- so absent still means "leave it
     * alone", exactly as every other field here does, and "" means "stop
     * overriding this". The criteria needed a separate flag only because a
     * document has no empty form.
     *
     * ONE CONVENTION, INCLUDING THE NAME, and the name is the one that used to be
     * the exception. A blank name was refused with a sentence telling the caller
     * to clear the override instead -- and nothing could: every name written here
     * set the flag, and a desc carrying the flag is skipped by the shaping for
     * ever. "Follow the series again" was reachable for the printed numbering,
     * the file name and the report, and unreachable for the field the drawer
     * offers first.
     *
     * Reading a JSON null as a clear was the alternative, and it cannot work:
     * absent and null are the same value by the time a body has been deserialised
     * into this record, so every partial edit -- the criteria panel sends
     * `{criteriaOverride}` and nothing else -- would silently wipe the labels of
     * the issue it was editing.
     */
    public record IssueEdit(Map<String, String> names,
                            Date intervalFrom,
                            Date intervalTo,
                            Map<String, Object> reportParams,
                            IssueCriteriaVo criteriaOverride,
                            boolean clearCriteriaOverride,
                            String edition,
                            String weekLabel,
                            String weekToLabel,
                            String yearLabel,
                            Map<String, String> fileNames,
                            String reportId) {

        /** The four-field form, for callers with no criteria to say anything about. */
        public IssueEdit(Map<String, String> names, Date intervalFrom, Date intervalTo,
                         Map<String, Object> reportParams) {
            this(names, intervalFrom, intervalTo, reportParams, null, false, null);
        }

        /** The form from before the edition was editable. */
        public IssueEdit(Map<String, String> names, Date intervalFrom, Date intervalTo,
                         Map<String, Object> reportParams, IssueCriteriaVo criteriaOverride,
                         boolean clearCriteriaOverride) {
            this(names, intervalFrom, intervalTo, reportParams, criteriaOverride,
                    clearCriteriaOverride, null);
        }

        /** The form from before the per-edition overrides existed. */
        public IssueEdit(Map<String, String> names, Date intervalFrom, Date intervalTo,
                         Map<String, Object> reportParams, IssueCriteriaVo criteriaOverride,
                         boolean clearCriteriaOverride, String edition) {
            this(names, intervalFrom, intervalTo, reportParams, criteriaOverride,
                    clearCriteriaOverride, edition, null, null, null, null, null);
        }
    }

    /** As long as the column, which is free text and deliberately so. */
    public static final int MAX_EDITION = 64;

    /**
     * As long as the column the name lands in, less the extension this adds.
     *
     * A cap rather than a shape. What a publication calls its file is the
     * publication's business -- the estate holds names with plus signs, ampersands
     * and Danish letters in them -- and the only things refused are a name that
     * would not fit the column, one that is a path rather than a name, and one
     * carrying a control character.
     */
    public static final int MAX_FILE_NAME = 128;

    /**
     * As long as the column the name lands in.
     *
     * A cap and nothing else, for the reason the file name has one: what a
     * publication calls its edition is the publication's business, and the estate
     * holds titles with parentheses, ampersands and Danish letters in them. What
     * a cap prevents is a value that reaches the driver instead of the caller --
     * an over-long name is a data-truncation error from inside the transaction,
     * which says nothing about which field was too long or by how much.
     */
    public static final int MAX_NAME = 255;

    /** A name that would not fit the column. */
    public static final String NAME_INVALID = "NAME_INVALID";

    /**
     * A blank name where the language has nothing to fall back on.
     *
     * NOT the ordinary blank, which is a clear. This is the one case a clear
     * cannot serve: the desc has no name of its own to keep, so dropping the
     * flag would leave the language nameless -- and an issue is unfindable in
     * every list that shows it under a language with no name.
     */
    public static final String NAME_BLANK = "NAME_BLANK";

    /** A file name that is not a usable file name. */
    public static final String FILE_NAME_INVALID = "FILE_NAME_INVALID";

    /** A report id that names no report. */
    public static final String REPORT_NOT_FOUND = "REPORT_NOT_FOUND";

    /** A report parameter that the numbering supplies. */
    public static final String REPORT_PARAM_RESERVED = "REPORT_PARAM_RESERVED";

    @Transactional
    public PublicationIssue update(PublicationIssue issue, IssueEdit edit, User actor) {
        if (issue.getStatus() != IssueStatus.OPEN) {
            throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_OPEN",
                    "a published issue's name is on a document people have downloaded and its "
                            + "interval is what its frozen members were resolved over; amend it instead");
        }
        if (edit == null) {
            return issue;
        }

        // S-23, judged before anything is written and on this side of the fence
        // too. week, weekTo, year and edition are what the edition IS, and the
        // render injects them after the parameter map is laid down -- so one
        // typed here is not merely redundant: the parameter table would show a
        // value under a heading saying it applies while the document ignored it.
        List<String> reserved = reservedParamsIntroducedBy(issue, edit.reportParams());
        if (!reserved.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException(REPORT_PARAM_RESERVED,
                    reserved + " " + (reserved.size() == 1 ? "is" : "are")
                            + " taken from this edition's numbering and injected into every report; "
                            + "typing a second answer here changes nothing and contradicts the document");
        }

        // Edition first, then the interval, then the printed numbering, then the
        // names. Everything above a name re-derives it: the interval moves the
        // numbers, and the edition and the labels change what those numbers PRINT
        // as -- so any of them arriving after a rename would overwrite the rename
        // with the derivation it was meant to replace. The file names come after
        // the labels for the same reason, since the series' pattern renders them.
        applyEdition(issue, edit, actor);
        applyInterval(issue, edit, actor);
        applyNumberingLabels(issue, edit, actor);
        applyNames(issue, edit.names(), actor);
        applyFileNames(issue, edit.fileNames(), actor);
        applyReport(issue, edit, actor);
        applyCriteriaOverride(issue, edit, actor);

        if (edit.reportParams() != null) {
            issue.setReportParams(new LinkedHashMap<>(edit.reportParams()));
        }
        return em.merge(issue);
    }

    // ----------------------------------------------------------- report parameters

    /**
     * Which reserved names this request ADDS or CHANGES -- never the ones it
     * merely carries.
     *
     * The rule is S-23's, and the scope is the difference. What the rule forbids
     * is DECIDING one of the four here, because the render injects them from the
     * edition's own numbering after the parameter map is laid down. A key the
     * issue already stores at the value it already stores is not that decision:
     * it is the row being sent back unchanged by a form that round-trips the
     * whole map in order to edit one field.
     *
     * And an imported issue can carry one. The previous system had no such rule,
     * so an estate row whose parameters happen to name a week would otherwise be
     * refused on EVERY edit -- including the edits that never opened the parameter
     * table -- on an endpoint that has been answering in production. A guard that
     * makes existing rows uneditable does not protect them.
     *
     * Judged by key AND value, so changing a stored reserved parameter is still
     * a decision and is still refused; only leaving it exactly as it stands
     * passes.
     */
    private static List<String> reservedParamsIntroducedBy(PublicationIssue issue,
                                                           Map<String, Object> submitted) {
        if (submitted == null || submitted.isEmpty()) {
            return List.of();
        }
        Map<String, Object> stored = issue.getReportParams();
        List<String> introduced = new ArrayList<>();
        // One definition of WHICH names are reserved, in the validator the series
        // form runs. A second list here is how the two screens come to disagree
        // about what may be typed.
        for (String key : SeriesValidator.reservedReportParams(submitted)) {
            boolean unchanged = stored != null && stored.containsKey(key)
                    && sameValue(stored.get(key), submitted.get(key));
            if (!unchanged) {
                introduced.add(key);
            }
        }
        return introduced;
    }

    /**
     * Whether two parameter values are the same value, compared as TEXT.
     *
     * The column round-trips through a properties converter, so everything comes
     * back from the database as a string whatever it went in as. A client
     * re-sending 36 as a JSON number would otherwise be told it was changing a
     * parameter it had just been handed, and the refusal would name the one field
     * it had not touched.
     */
    private static boolean sameValue(Object stored, Object submitted) {
        if (stored == null || submitted == null) {
            return stored == submitted;
        }
        return Objects.equals(String.valueOf(stored), String.valueOf(submitted));
    }

    // ------------------------------------------------------- printed numbering

    /**
     * What this edition is CALLED where its derived numbers are printed.
     *
     * The numbers themselves are untouched -- they are derived from the cut-off,
     * and the ordering, the timeline strip, the gap arithmetic and the archive
     * all read them. What changes here is the text on the cover, and with it the
     * file name and the report's heading, because all three expand the same
     * tokens through the same resolution.
     *
     * A blank label is a CLEAR, and is the whole way back to the derived number.
     * Absent leaves the label alone, like every other field.
     *
     * The names follow, for the reason the edition and the interval do: a
     * suggested name RENDERS the numbering, and an issue left titled "uge 36"
     * while its file is written as "36+37" is exactly the drift the rendering
     * exists to avoid.
     */
    private void applyNumberingLabels(PublicationIssue issue, IssueEdit edit, User actor) {
        if (edit.weekLabel() == null && edit.weekToLabel() == null && edit.yearLabel() == null) {
            return;
        }

        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        boolean changed = false;

        if (edit.weekLabel() != null) {
            String wanted = PrintedNumbering.validated(edit.weekLabel());
            if (!Objects.equals(wanted, issue.getWeekLabel())) {
                from.put("weekLabel", issue.getWeekLabel());
                to.put("weekLabel", wanted);
                issue.setWeekLabel(wanted);
                changed = true;
            }
        }
        if (edit.weekToLabel() != null) {
            String wanted = PrintedNumbering.validated(edit.weekToLabel());
            if (!Objects.equals(wanted, issue.getWeekToLabel())) {
                from.put("weekToLabel", issue.getWeekToLabel());
                to.put("weekToLabel", wanted);
                issue.setWeekToLabel(wanted);
                changed = true;
            }
        }
        if (edit.yearLabel() != null) {
            String wanted = PrintedNumbering.validated(edit.yearLabel());
            if (!Objects.equals(wanted, issue.getYearLabel())) {
                from.put("yearLabel", issue.getYearLabel());
                to.put("yearLabel", wanted);
                issue.setYearLabel(wanted);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        shape.renumber(issue, issue.getSeries());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", from);
        detail.put("to", to);
        audit.edited(issue, actor, AuditAction.NUMBERING_CHANGED, detail);
    }

    // ---------------------------------------------------------------- file names

    /**
     * The file name each language's document will be written under.
     *
     * NEW, and it was the one document field a person could not decide. The
     * series names the file by a pattern, publish expands it, and until now the
     * pattern always won: an edition that had to be filed under something else
     * could only be uploaded by hand, which sets the sticky flag and stops the
     * release regenerating it at all.
     *
     * OPEN ONLY, through the gate at the top of {@link #update}. A published
     * issue's document is at an address that message HTML points at, and the
     * upload path refuses to move it for the same reason -- FILE_NAME_IMMUTABLE,
     * unchanged. Renaming a released document is a different decision.
     *
     * A blank name is a CLEAR: the language goes back to the series' pattern.
     * What it does NOT do is erase the column, because that column also holds the
     * name of the file that currently exists -- an uploaded correction, or what
     * the last release wrote -- and clearing an OVERRIDE is not a statement about
     * the bytes on disk.
     *
     * PUBLIC for the reason applyNames is: this is the one place a file name is
     * decided, and a second implementation would set the name without the flag,
     * leaving the next publish to quietly rename the document back.
     */
    public void applyFileNames(PublicationIssue issue, Map<String, String> fileNames, User actor) {
        if (fileNames == null || fileNames.isEmpty()) {
            return;
        }

        // Resolved first, applied second. D-3 is a property of the WHOLE issue --
        // every language writes into one folder, so two languages sharing a name
        // is one document overwriting the other -- and it can only be judged once
        // every requested name is known. Refusing halfway through would leave the
        // languages that came first renamed and the rest not.
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fileNames.entrySet()) {
            String lang = entry.getKey();
            // Throws NO_SUCH_LANGUAGE where the issue has no row to write.
            descFor(issue, lang);
            resolved.put(lang, validatedFileName(entry.getValue()));
        }

        Map<String, String> byName = new LinkedHashMap<>();
        for (PublicationIssueDesc desc : issue.getDescs()) {
            String lang = desc.getLang();
            String name = resolved.containsKey(lang) ? resolved.get(lang) : desc.getFileName();
            if (name == null) {
                continue;
            }
            String previous = byName.put(name.toLowerCase(), lang);
            if (previous != null && !previous.equals(lang)) {
                throw new IssueLifecycleService.TransitionRefusedException("FILE_NAME_NOT_DISTINCT",
                        "'" + name + "' would be the file name for both '" + previous + "' and '"
                                + lang + "'. Both languages write into the same folder, so sharing a "
                                + "name means one silently overwrites the other.");
            }
        }

        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String lang = entry.getKey();
            String wanted = entry.getValue();
            PublicationIssueDesc desc = descFor(issue, lang);

            boolean clearing = wanted == null;
            if (clearing && !desc.isFileNameOverridden()) {
                continue;
            }
            if (!clearing && desc.isFileNameOverridden() && wanted.equals(desc.getFileName())) {
                continue;
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("lang", lang);
            detail.put("from", desc.isFileNameOverridden() ? desc.getFileName() : null);
            detail.put("to", wanted);

            if (clearing) {
                // The flag only. The column still names whatever file exists, and
                // the resolution simply stops preferring it over the pattern.
                desc.setFileNameOverridden(false);
            } else {
                desc.setFileName(wanted);
                desc.setFileNameOverridden(true);
            }
            audit.edited(issue, actor, AuditAction.FILE_NAME_CHANGED, detail);
        }
    }

    /** The name as it will be stored -- sanitised, capped, always a PDF -- or null for a clear. */
    private static String validatedFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        // The same stripping the upload applies, from the same place: both end up
        // resolved against the issue's repository folder, so a name carrying `..`
        // or an absolute path would write outside it.
        String bare = IssueFileService.bareFileName(fileName);
        if (bare == null) {
            throw new IssueLifecycleService.TransitionRefusedException(FILE_NAME_INVALID,
                    "'" + fileName + "' does not name a file; a path is not a file name, and the "
                            + "document is written inside this issue's own folder");
        }
        for (int i = 0; i < bare.length(); i++) {
            if (Character.isISOControl(bare.charAt(i))) {
                throw new IssueLifecycleService.TransitionRefusedException(FILE_NAME_INVALID,
                        "a file name is one line of text; this one carries a control character, and "
                                + "the name becomes part of a public download URL");
            }
        }
        if (bare.length() > MAX_FILE_NAME) {
            throw new IssueLifecycleService.TransitionRefusedException(FILE_NAME_INVALID,
                    "a file name is at most " + MAX_FILE_NAME + " characters");
        }
        // Always a PDF, because that is what the release writes -- and the upload
        // path already refuses to move a published document to a different name,
        // so a suffix added later would be a rename nobody asked for.
        return IssueFileNaming.withPdf(bare);
    }

    // -------------------------------------------------------------------- report

    /**
     * The report THIS edition renders with.
     *
     * Blank hands it back to the series, which is what all but a handful of
     * editions do. A value equal to the series' own is stored as no override at
     * all, for the reason the criteria document is: it is not a deviation, and
     * recording it as one would badge the edition "tilpasset" while it renders
     * exactly what its series renders.
     *
     * THE ID IS LOOKED UP, not merely stored. A report that does not exist fails
     * at RENDER -- which is step 10 of the publish transaction, after the cut-off
     * has been stamped and the members frozen -- so the whole release rolls back
     * on a typo that could have been refused here, in a form, with somebody
     * watching.
     */
    private void applyReport(PublicationIssue issue, IssueEdit edit, User actor) {
        if (edit.reportId() == null) {
            return;
        }
        String wanted = edit.reportId().isBlank() ? null : edit.reportId().trim();
        PublicationSeries series = issue.getSeries();
        if (wanted != null && series != null && wanted.equals(series.getReportId())) {
            wanted = null;
        }
        if (Objects.equals(wanted, issue.getReportId())) {
            return;
        }
        if (wanted != null && reports.findByReportId(wanted) == null) {
            throw new IssueLifecycleService.TransitionRefusedException(REPORT_NOT_FOUND,
                    "'" + wanted + "' is not a report. The render would fail at step 10 of the "
                            + "release, after the cut-off had been stamped and the members frozen.");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", issue.getReportId());
        detail.put("to", wanted);

        issue.setReportId(wanted);
        audit.edited(issue, actor, AuditAction.REPORT_CHANGED, detail);
    }

    /**
     * Tailors what this one issue selects, or gives it back to the series.
     *
     * The escape hatch legacy had no concept of: an edition that must differ --
     * a two-week issue over the week 52/1 turnover, a supplement that carries
     * one extra message series -- used to require cloning the whole template
     * into a throwaway `dont-use-` series and publishing one edition from it.
     * Six of those are in the imported estate, and they fragment the archive they
     * were cloned from.
     *
     * VALIDATED, not merely stored. An unresolvable document would resolve to
     * nothing at publish and the issue would go out EMPTY -- the one failure mode
     * that looks like success -- so it is refused here, where somebody is
     * watching, rather than at 02:00 under AUTO_RELEASE.
     *
     * A document equal to the series' is stored as no override at all. It is not
     * a deviation, and recording it as one would label the issue "tilpasset for
     * denne udgave" while it selects exactly what the series does -- and would
     * make the shadow diff skip a week that had nothing wrong with it.
     */
    private void applyCriteriaOverride(PublicationIssue issue, IssueEdit edit, User actor) {
        if (!edit.clearCriteriaOverride() && edit.criteriaOverride() == null) {
            return;
        }

        PublicationSeries series = issue.getSeries();
        IssueCriteriaVo wanted = edit.clearCriteriaOverride() ? null : edit.criteriaOverride();

        if (wanted != null && series != null && wanted.equals(series.getCriteria())) {
            wanted = null;
        }
        if (Objects.equals(wanted, issue.getCriteriaOverride())) {
            return;
        }

        if (wanted != null) {
            if (series == null || series.getContentMode() != ContentMode.GENERATED_FROM_QUERY) {
                throw new IssueLifecycleService.TransitionRefusedException("CRITERIA_NOT_APPLICABLE",
                        "only a query-backed series selects by criteria, so an override on this "
                                + "issue would decide nothing");
            }
            // The same validator the series form runs, with the same resolver
            // behind it. Every operand is looked up: an area, a chart or a message
            // series that names no row narrows the query silently, and a domain
            // node is worse still -- it is a MACRO for the message series that
            // domain publishes, so one that expands to nothing narrows the query to
            // NOTHING and the issue publishes EMPTY rather than failing.
            //
            // One resolver in both places on purpose. A document accepted on the
            // series form and refused here, or the reverse, would be two
            // definitions of a valid document differing only by which screen it was
            // typed on.
            List<CriteriaValidator.Violation> violations =
                    CriteriaValidator.validate(wanted, operands);
            if (!violations.isEmpty()) {
                throw new IssueLifecycleService.TransitionRefusedException("CRITERIA_INVALID",
                        "the override would not resolve, and an issue that cannot resolve publishes "
                                + "EMPTY rather than failing: " + violations);
            }
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", issue.getCriteriaOverride());
        detail.put("to", wanted);

        issue.setCriteriaOverride(wanted);
        audit.edited(issue, actor, AuditAction.CRITERIA_OVERRIDDEN, detail);
    }

    // ------------------------------------------------------------------- edition

    /**
     * The edition string, where the caller sent one.
     *
     * FREE TEXT, up to the width of the column. The archive holds "1", "2" and
     * two rows where somebody typed a YEAR into the previous system's edition box
     * -- and one of those years is in a published file name to this day. Storing
     * it as a number would refuse to round-trip the archive it has to carry, so
     * what is checked is that the value is a value: absent leaves it alone, and a
     * blank one is REFUSED rather than treated as a clear. An issue that reached
     * "no edition" through the form would be indistinguishable from one created
     * before the field existed, and the create writes the first edition
     * precisely so that nothing has to be.
     *
     * OPEN ONLY, through the gate this whole method sits behind. Once the issue is
     * published, the edition is on the cover of the document and in the file name
     * people have downloaded, and it is frozen by the same rule that freezes the
     * interval and the names.
     *
     * The names follow it, for the same reason an interval change re-derives them:
     * a suggestion pattern may render the edition, and an issue left describing
     * the previous one is exactly the drift the rendering exists to avoid.
     */
    private void applyEdition(PublicationIssue issue, IssueEdit edit, User actor) {
        String edition = edit.edition();
        if (edition == null) {
            return;
        }
        String trimmed = edition.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_EDITION) {
            throw new IssueLifecycleService.TransitionRefusedException("EDITION_INVALID",
                    "an edition is between 1 and " + MAX_EDITION + " characters. It is what tells "
                            + "two publications of the same period apart, so an empty one is not a "
                            + "way of saying there is only one -- the first edition says that.");
        }
        if (trimmed.equals(issue.getEdition())) {
            return;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", issue.getEdition());
        detail.put("to", trimmed);

        issue.setEdition(trimmed);
        shape.renumber(issue, issue.getSeries());

        audit.edited(issue, actor, AuditAction.EDITION_CHANGED, detail);
    }

    // ------------------------------------------------------------------ interval

    private void applyInterval(PublicationIssue issue, IssueEdit edit, User actor) {
        Date from = edit.intervalFrom() == null ? issue.getIntervalFrom() : edit.intervalFrom();
        Date to = edit.intervalTo() == null ? issue.getIntervalTo() : edit.intervalTo();

        boolean fromChanged = !equal(from, issue.getIntervalFrom());
        boolean toChanged = !equal(to, issue.getIntervalTo());
        if (!fromChanged && !toChanged) {
            return;
        }
        if (from != null && to != null && !from.before(to)) {
            throw new IssueLifecycleService.TransitionRefusedException("INTERVAL_INVERTED",
                    "an interval that ends before it starts selects nothing, and the issue would "
                            + "publish empty rather than fail");
        }

        // The same refusal the create makes, and it belongs here for the same
        // reason: an edited interval that reaches back inside a released issue's
        // period would republish that week's messages under a second name. The
        // issue being edited is excluded from the test -- it is allowed to overlap
        // where it already was.
        if (fromChanged) {
            lifecycle.assertNoOverlap(issue.getSeries(), from, issue);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", bounds(issue.getIntervalFrom(), issue.getIntervalTo()));
        detail.put("to", bounds(from, to));

        issue.setIntervalFrom(from);
        issue.setIntervalTo(to);
        // EACH BOUND SAYS WHERE IT CAME FROM, and only the bound that actually
        // moved is re-attributed. MANUAL means somebody typed THIS bound: writing
        // it on both because one of them changed would claim an admin authored a
        // period start that was in fact stamped by the previous release, and the
        // "(stemplet)/(nominel)" marker the issue list shows reads exactly these
        // two columns. A bound that has been cleared has no source at all.
        if (fromChanged) {
            issue.setIntervalFromSource(from == null ? null : IntervalBoundSource.MANUAL);
        }
        if (toChanged) {
            issue.setIntervalToSource(to == null ? null : IntervalBoundSource.MANUAL);
        }

        // The numbers and the suggested names follow the period they render: an
        // issue left labelled "uge 29" in week 30's window is what an interval edit
        // produces without this. A name somebody typed is a decision rather than a
        // rendering, and keeps its own value.
        shape.renumber(issue, issue.getSeries());

        audit.edited(issue, actor, AuditAction.INTERVAL_CHANGED, detail);
    }

    // --------------------------------------------------------------------- names

    /**
     * The names, applied -- the one place a name is written.
     *
     * PUBLIC because two other actions offer the same field and must not grow
     * their own copy of it. The create dialog prefills the series' suggestions and
     * lets an admin correct them before the issue exists in a list under a name
     * nobody chose, and the release dialog is the LAST moment a name can still be
     * changed, because publishing puts it on the document and into every citation.
     * A second implementation of this would set the name without the flag, and the
     * next interval change would quietly rename the issue back.
     *
     * A null or empty map changes nothing, so a caller with nothing to say sends
     * nothing rather than reconstructing the current names.
     *
     * A BLANK NAME IS A CLEAR, per language, exactly as it is for the printed
     * numbering and the file names: the flag comes off, the text stays, and the
     * re-shape below writes the series' suggestion over it. Two properties make
     * that the right shape rather than a convenience. The row is never nameless
     * for an instant, which matters because the column is NOT NULL; and the
     * suggestion is written back HERE rather than at the next interval edit, so
     * the drawer that sent the clear gets the name the series proposes in the
     * response to the request that asked for it, instead of showing the old
     * typed name until something unrelated moves.
     *
     * The other languages are untouched. The clear is per-desc, and the re-shape
     * re-derives only descs that carry no flag of their own -- which is what
     * every other edit on this service already does to them.
     */
    public void applyNames(PublicationIssue issue, Map<String, String> names, User actor) {
        if (names == null || names.isEmpty()) {
            return;
        }
        validateNames(issue, names);
        boolean cleared = false;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String lang = entry.getKey();
            String name = entry.getValue();
            PublicationIssueDesc desc = descFor(issue, lang);

            if (name == null || name.isBlank()) {
                // Nothing to stop following. Silent rather than audited, for the
                // reason the file-name clear is: a Historik panel listing edits
                // that changed nothing buries the ones that did.
                if (!desc.isNameOverridden()) {
                    continue;
                }

                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("lang", lang);
                detail.put("from", desc.getName());
                // Null is the whole statement, and the same one the file-name
                // clear writes: there is no override any more. What the name
                // becomes is the series' business from here on, and reading it
                // out of this line would freeze one rendering of it into history.
                detail.put("to", null);

                // The flag only. The row keeps the name it is listed under until
                // the re-shape below replaces it, so there is no instant at which
                // the issue has none.
                desc.setNameOverridden(false);
                cleared = true;

                audit.edited(issue, actor, AuditAction.NAME_CHANGED, detail);
                continue;
            }

            String trimmed = name.trim();
            if (trimmed.equals(desc.getName())) {
                continue;
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("lang", lang);
            detail.put("from", desc.getName());
            detail.put("to", trimmed);

            desc.setName(trimmed);
            // Typed, so it stops tracking the interval. Without this the next
            // interval edit would quietly put the suggested name back.
            desc.setNameOverridden(true);

            audit.edited(issue, actor, AuditAction.NAME_CHANGED, detail);
        }

        // Only where something actually stopped following. The re-shape is what
        // turns a cleared flag into a visible name, and running it when no flag
        // came off would re-derive descs the caller said nothing about -- which
        // on the release path means overwriting names typed before the flag
        // existed, since the trail heuristic that protects them is only consulted
        // by the restamp.
        if (cleared) {
            shape.renumber(issue, issue.getSeries());
        }
    }

    /**
     * The refusals {@link #applyNames} makes, WITHOUT touching the issue.
     *
     * A caller that renames as one step of a longer action needs to know the names
     * are acceptable before it starts changing anything else: the release path
     * stamps a cut-off and restamps the numbers before it can apply a name, and a
     * refusal at that point would have written those columns first. Nothing here
     * mutates, so it is safe to ask early and then ask again by applying.
     */
    public void validateNames(PublicationIssue issue, Map<String, String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : names.entrySet()) {
            // Throws NO_SUCH_LANGUAGE where the issue has no row to write.
            PublicationIssueDesc desc = descFor(issue, entry.getKey());
            String name = entry.getValue();

            if (name == null || name.isBlank()) {
                // A clear, and the only thing that can go wrong with one: the
                // clear writes no name of its own, so a language whose desc is
                // already nameless would be left that way -- the re-shape rewrites
                // a name only where there is a series to derive one from.
                if (desc.getName() == null || desc.getName().isBlank()) {
                    throw new IssueLifecycleService.TransitionRefusedException(NAME_BLANK,
                            "'" + entry.getKey() + "' has no name to fall back on, so following the "
                                    + "series again would leave it with none -- and a nameless issue is "
                                    + "unfindable in every list that shows it");
                }
                continue;
            }
            if (name.trim().length() > MAX_NAME) {
                throw new IssueLifecycleService.TransitionRefusedException(NAME_INVALID,
                        "a name is at most " + MAX_NAME + " characters; a longer one reaches the "
                                + "driver as a truncation rather than the caller as a refusal, and by "
                                + "then it is inside the transaction that was renaming the issue");
            }
        }
    }

    // ----------------------------------------------------------------- internals

    private static Map<String, Object> bounds(Date from, Date to) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intervalFrom", from == null ? null : from.getTime());
        out.put("intervalTo", to == null ? null : to.getTime());
        return out;
    }

    private static boolean equal(Date a, Date b) {
        return a == null ? b == null : a.equals(b);
    }

    private static PublicationIssueDesc descFor(PublicationIssue issue, String lang) {
        return issue.getDescs().stream()
                .filter(d -> d.getLang() != null && d.getLang().equals(lang))
                .findFirst()
                .orElseThrow(() -> new IssueLifecycleService.TransitionRefusedException("NO_SUCH_LANGUAGE",
                        "the issue has no " + lang + " desc row; the series may not be configured for it"));
    }
}
