package org.niord.core.publication.series;

import org.niord.core.publication.series.criteria.CriteriaValidator;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The series rules, S-1 to S-18, plus the desc rules and the criteria rules.
 *
 * Enforcement lives in the service layer because that is where this repository
 * already puts it -- there is no @Check anywhere in niord, and Hibernate's update
 * mode would not retrofit a database CHECK onto an existing table anyway. A
 * database constraint here would be defence in depth at best and a false sense of
 * safety at worst.
 *
 * Every rule reports the field it failed on, so the result can be rendered
 * against the form that produced it rather than as one opaque message.
 */
public final class SeriesValidator {

    /** One failed rule, named, with the field it belongs to. */
    public record FieldError(String rule, String field, String message) {
        @Override
        public String toString() {
            return rule + " (" + field + "): " + message;
        }
    }

    private SeriesValidator() {
    }

    /** Validates a series. An empty list means it may be saved. */
    public static List<FieldError> validate(PublicationSeries s, Set<String> installationLanguages) {
        List<FieldError> e = new ArrayList<>();
        if (s == null) {
            return e;
        }

        boolean queryBacked = s.getContentMode() == ContentMode.GENERATED_FROM_QUERY;

        // S-1. The query-backed shape is all-or-nothing. A series that declares it
        // generates from a query but carries no query resolves everything.
        if (queryBacked) {
            if (s.getTimeRelation() == null) {
                e.add(new FieldError("S-1", "timeRelation",
                        "a query-backed series must declare which time predicate it uses"));
            }
            if (s.getCriteria() == null) {
                e.add(new FieldError("S-1", "criteria",
                        "a query-backed series must carry a criteria document; a null one means NO query, "
                                + "which is not the same as an empty one"));
            }
        } else {
            if (s.getTimeRelation() != null) {
                e.add(new FieldError("S-1", "timeRelation",
                        "only a query-backed series has a time relation"));
            }
            if (s.getCriteria() != null) {
                e.add(new FieldError("S-1", "criteria",
                        "only a query-backed series has criteria"));
            }
        }

        // S-2. aliveAtCutoff is meaningful only where there is a query to apply it to.
        if (queryBacked && s.getAliveAtCutoff() == null) {
            e.add(new FieldError("S-2", "aliveAtCutoff",
                    "a query-backed series must state whether it filters on liveness; leaving it null makes "
                            + "'does not filter' and 'filters and everything passed' indistinguishable"));
        }
        if (!queryBacked && s.getAliveAtCutoff() != null) {
            e.add(new FieldError("S-2", "aliveAtCutoff", "only a query-backed series has a liveness filter"));
        }

        // S-3. In-force membership IS a liveness question; false would empty it.
        if (s.getTimeRelation() == TimeRelation.IN_FORCE_AT_CUTOFF
                && !Boolean.TRUE.equals(s.getAliveAtCutoff())) {
            e.add(new FieldError("S-3", "aliveAtCutoff",
                    "IN_FORCE_AT_CUTOFF requires aliveAtCutoff = true; 'in force' is the liveness test"));
        }

        // S-4. Only an interval-based series has a first interval to start.
        boolean interval = s.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;
        if (interval && s.getFirstIssueStartsAt() == null) {
            e.add(new FieldError("S-4", "firstIssueStartsAt",
                    "an interval-based series needs a start for its first interval"));
        }
        if (!interval && s.getFirstIssueStartsAt() != null) {
            e.add(new FieldError("S-4", "firstIssueStartsAt",
                    "only PUBLISHED_IN_INTERVAL has an interval to start"));
        }

        // S-5 to S-7. The nominal schedule must match the cadence it describes.
        boolean weekly = s.getCadence() == SeriesCadence.WEEKLY;
        if (weekly != (s.getNominalCutoffDay() != null)) {
            e.add(new FieldError("S-5", "nominalCutoffDay",
                    "a weekly cadence needs a weekday, and nothing else may carry one"));
        }
        boolean monthlyOrYearly = s.getCadence() == SeriesCadence.MONTHLY || s.getCadence() == SeriesCadence.YEARLY;
        if (monthlyOrYearly != (s.getNominalCutoffDayOfMonth() != null)) {
            e.add(new FieldError("S-6", "nominalCutoffDayOfMonth",
                    "a monthly or yearly cadence needs a day of the month"));
        }
        if ((s.getCadence() == SeriesCadence.YEARLY) != (s.getNominalCutoffMonth() != null)) {
            e.add(new FieldError("S-6", "nominalCutoffMonth", "a yearly cadence needs a month"));
        }
        boolean hasCadence = s.getCadence() != null && s.getCadence() != SeriesCadence.NONE;
        if (hasCadence != (s.getNominalCutoffTime() != null)) {
            e.add(new FieldError("S-7", "nominalCutoffTime", "a cadence needs a nominal time of day"));
        }

        // S-21. The cut-off default describes a period, so a series with no period
        // can only be cut off at the release; and a calendar-driven default on a
        // weekly series would stamp every issue at a Wednesday noon nobody
        // pressed, which is the nominal-versus-stamped confusion the model exists
        // to keep apart.
        if (s.getCutoffDefault() == null) {
            e.add(new FieldError("S-21", "cutoffDefault", "a series says where its cut-off falls by default"));
        } else if (s.getCutoffDefault() != CutoffDefault.RELEASE_MOMENT
                && s.getCadence() != SeriesCadence.YEARLY) {
            e.add(new FieldError("S-21", "cutoffDefault",
                    "only a yearly series is cut off at a period boundary; everything else at the release"));
        }

        // S-8. A one-off has nothing to chain to and no sequence to number within.
        if (s.getCadence() == SeriesCadence.NONE) {
            if (s.getNextIssueCreation() != NextIssueCreation.MANUAL) {
                e.add(new FieldError("S-8", "nextIssueCreation",
                        "a one-off has no next issue to create automatically"));
            }
            if (s.getNumberingScheme() != NumberingScheme.NONE) {
                e.add(new FieldError("S-8", "numberingScheme", "a one-off has no sequence to number within"));
            }
        }

        // S-9. Report settings arrive together or not at all.
        boolean hasReport = s.getReportId() != null;
        if (hasReport != (s.getPageSize() != null)
                || hasReport != (s.getPageOrientation() != null)
                || hasReport != (s.getMapThumbnails() != null)) {
            e.add(new FieldError("S-9", "reportId",
                    "pageSize, pageOrientation and mapThumbnails are set exactly when a report is"));
        }

        // S-10. A sort field with no direction, or the reverse, is half a setting.
        if ((s.getMessageSortBy() == null) != (s.getMessageSortOrder() == null)) {
            e.add(new FieldError("S-10", "messageSortOrder",
                    "a sort field and its direction are set together or not at all"));
        }

        // S-11. The configured language list.
        List<String> languages = s.getLanguages();
        if (languages == null || languages.isEmpty()) {
            e.add(new FieldError("S-11", "languages", "a series must declare at least one language"));
        } else {
            Set<String> seen = new LinkedHashSet<>();
            for (String lang : languages) {
                if (lang == null || lang.isBlank()) {
                    e.add(new FieldError("S-11", "languages", "a blank language entry"));
                } else if (!seen.add(lang)) {
                    e.add(new FieldError("S-11", "languages", "duplicate language: " + lang));
                } else if (installationLanguages != null && !installationLanguages.isEmpty()
                        && !installationLanguages.contains(lang)) {
                    e.add(new FieldError("S-11", "languages",
                            lang + " is not one of the installation's languages"));
                }
            }
        }

        // S-12 and S-13. Every configured language carries its own text, and no
        // desc row exists outside the list. A missing name falls back to the first
        // desc, so one language is silently served another's text.
        Set<String> declared = languages == null ? Set.of() : new LinkedHashSet<>(languages);
        Set<String> withDesc = new LinkedHashSet<>();
        if (s.getDescs() != null) {
            for (PublicationSeriesDesc d : s.getDescs()) {
                withDesc.add(d.getLang());
                if (!declared.contains(d.getLang())) {
                    e.add(new FieldError("S-12", "descs",
                            "a desc row for " + d.getLang() + ", which is not a configured language"));
                }
                if (d.getName() == null || d.getName().isBlank()) {
                    e.add(new FieldError("S-12", "descs." + d.getLang() + ".name",
                            "every configured language needs a non-blank name"));
                }
                if (s.getMessagePublication() != null
                        && s.getMessagePublication() != org.niord.core.publication.vo.MessagePublication.NONE
                        && (d.getMessageReferenceFormat() == null || d.getMessageReferenceFormat().isBlank())) {
                    e.add(new FieldError("S-13", "descs." + d.getLang() + ".messageReferenceFormat",
                            "a citable series needs a reference format in every language"));
                }
                // D-7. A format with no name round-trips to nothing and loses the format.
                if (d.getMessageReferenceFormat() != null && !d.getMessageReferenceFormat().isBlank()
                        && (d.getName() == null || d.getName().isBlank())) {
                    e.add(new FieldError("D-7", "descs." + d.getLang() + ".name",
                            "a desc carrying a reference format must carry a name, or the row round-trips "
                                    + "to nothing and the format is lost"));
                }
            }
        }
        for (String lang : declared) {
            if (!withDesc.contains(lang)) {
                e.add(new FieldError("S-12", "descs", "no desc row for configured language " + lang));
            }
        }

        // S-14. No token may survive into a file name and then into a public URL.
        if (s.getDescs() != null) {
            for (PublicationSeriesDesc d : s.getDescs()) {
                for (String[] pattern : new String[][]{
                        {"nameSuggestionPattern", d.getNameSuggestionPattern()},
                        {"fileNamePattern", d.getFileNamePattern()},
                        {"linkPattern", d.getLinkPattern()}}) {
                    if (pattern[1] != null && !IssueNaming.isExpandable(pattern[1])) {
                        e.add(new FieldError("S-14", "descs." + d.getLang() + "." + pattern[0],
                                "contains a token outside the vocabulary " + IssueNaming.TOKENS));
                    }
                }

                // The citation format is validated with the DEFERRED token allowed.
                // It is the one pattern that is expanded twice -- the naming tokens
                // here, and ${parameters} at the moment of citing -- so validating
                // it against the strict vocabulary rejects every format the legacy
                // convention produces, and S-13 then makes the series unsaveable.
                if (d.getMessageReferenceFormat() != null
                        && !IssueNaming.isCitationExpandable(d.getMessageReferenceFormat())) {
                    e.add(new FieldError("S-14", "descs." + d.getLang() + ".messageReferenceFormat",
                            "contains a token outside the vocabulary " + IssueNaming.TOKENS
                                    + " plus ${" + IssueNaming.DEFERRED_TOKEN + "}"));
                }
            }
        }

        // S-15. One path per language, or the last language written wins and the
        // others' files are simply not there.
        if (queryBacked && declared.size() > 1 && s.getDescs() != null) {
            Set<String> fileNames = new LinkedHashSet<>();
            for (PublicationSeriesDesc d : s.getDescs()) {
                if (d.getFileNamePattern() != null && !fileNames.add(d.getFileNamePattern())) {
                    e.add(new FieldError("S-15", "descs." + d.getLang() + ".fileNamePattern",
                            "every language must generate to its own file name; otherwise one path is "
                                    + "written repeatedly and the last language wins"));
                }
            }
        }

        // S-19. A series belongs to a category, because the COLUMN says so.
        //
        // PublicationSeries.category is NOT NULL. Without a rule here the constraint
        // is discovered by Hibernate at flush time and surfaces as a 500 with a
        // message about a transient value -- which tells an admin nothing about the
        // empty dropdown that caused it.
        //
        // This is the SECOND time this column has been found unset. The importer got
        // planCategoryOf; the interactive create path was never given the equivalent,
        // so the same defect existed on a route nobody had walked. A rule covers
        // every route at once, which is why it belongs here rather than in create().
        if (s.getCategory() == null) {
            e.add(new FieldError("S-19", "categoryId",
                    "a series must belong to a publication category; the column is NOT NULL and a "
                            + "missing one fails at flush time as a 500 rather than as an answer"));
        }

        // S-20. A series belongs to a domain, because that is where its TIMEZONE
        // comes from -- and a cut-off schedule with no zone is a schedule in
        // whatever zone the reader happens to be in.
        //
        // Not a style rule. The domains differ in practice: Atlantic/Faeroe for the
        // Faroe domain, UTC for Greenland, Europe/Copenhagen for the rest. A series
        // resolving in the wrong one names its issues for the wrong ISO week at the
        // year boundary and closes them an hour early or late all year.
        // Required only where there ARE cut-offs to read. A cadence-less series
        // has none -- S-5, S-6 and S-7 refuse every nominalCutoff* field on one --
        // so there is no cut-off to read in any zone, and the timezone rationale
        // does not reach it.
        //
        // NULL MEANS GLOBAL, and that is load-bearing rather than a loophole: it
        // is what makes the citation-only publications reachable from every
        // domain. The publication picker matches "domain IS NULL OR domain = the
        // current one", so assigning a domain NARROWS where a publication can be
        // seen. Requiring one here hid four publications that every domain needs.
        if (s.getDomain() == null && s.getCadence() != SeriesCadence.NONE) {
            e.add(new FieldError("S-20", "domainId",
                    "a series with a cadence must belong to a domain; the domain carries the "
                            + "timezone its cut-offs are read in, and there is no other source "
                            + "for one"));
        }

        // C-1 to C-10, on the criteria document itself.
        for (CriteriaValidator.Violation v : CriteriaValidator.validate(s.getCriteria(), CriteriaValidator.ACCEPT_ALL)) {
            e.add(new FieldError(v.rule(), "criteria" + v.pointer(), v.message()));
        }

        return e;
    }

    /**
     * S-17. ACTIVE requires every other rule green.
     *
     * A series is allowed to be an incomplete DRAFT; it is not allowed to be an
     * incomplete ACTIVE one, because ACTIVE is what puts it in the picker.
     */
    public static List<FieldError> validateForActivation(PublicationSeries s, Set<String> installationLanguages) {
        List<FieldError> e = new ArrayList<>(validate(s, installationLanguages));
        if (s != null && s.getStatus() == SeriesStatus.ACTIVE && !e.isEmpty()) {
            e.add(0, new FieldError("S-17", "status",
                    "a series cannot be ACTIVE while " + e.size() + " rule(s) fail; ACTIVE is what puts it "
                            + "in the picker"));
        }
        return e;
    }

    /** S-16 and S-18: the two fields that cannot change once something depends on them. */
    public static List<FieldError> validateImmutables(PublicationSeries existing, PublicationSeries updated,
                                                      boolean anyIssuePublished) {
        List<FieldError> e = new ArrayList<>();
        if (existing == null || updated == null) {
            return e;
        }
        if (existing.getSeriesId() != null && !existing.getSeriesId().equals(updated.getSeriesId())) {
            e.add(new FieldError("S-16", "seriesId",
                    "seriesId is the import/export key and is immutable after create"));
        }
        if (anyIssuePublished && existing.getMessagePublication() != updated.getMessagePublication()) {
            e.add(new FieldError("S-18", "messagePublication",
                    "changing the citation channel after an issue has published makes every existing "
                            + "citation unfindable -- it lives in the other field -- and re-applying appends "
                            + "a duplicate rather than replacing"));
        }
        return e;
    }
}
