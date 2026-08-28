package org.niord.core.publication.series;

import org.niord.core.publication.series.criteria.CriteriaValidator;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Validates a series without looking any criteria operand up.
     *
     * The signature every caller that has no persistence context uses -- and this
     * class deliberately has none, which is what makes the whole rule set testable
     * without a database. A caller that CAN resolve an operand passes a resolver
     * to the overload below; the difference between the two is exactly rule C-4.
     */
    public static List<FieldError> validate(PublicationSeries s, Set<String> installationLanguages) {
        return validate(s, installationLanguages, CriteriaValidator.ACCEPT_ALL);
    }

    /**
     * Validates a series, resolving every criteria operand through the caller's
     * resolver.
     *
     * @param resolver answers whether an operand names something that exists. The
     *                 resolver is passed IN rather than looked up, so this class
     *                 stays pure and a test can hand it a set.
     */
    public static List<FieldError> validate(PublicationSeries s, Set<String> installationLanguages,
                                            CriteriaValidator.OperandResolver resolver) {
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
            // THE THIRD LEG, and it was the missing one. "Generated from a query"
            // names two things -- what to select and how to print it -- and a
            // series carrying the first without the second has no document to
            // produce at all. Nothing else supplies one either: publish writes the
            // file, so there are no bytes to fall back on, and the issue would
            // reach PUBLISHED with an empty link. That is the failure that looks
            // like success, and by the time anybody sees it the cut-off is stamped.
            if (s.getReportId() == null) {
                e.add(new FieldError("S-1", "reportId",
                        "a query-backed series generates its document from a report and must name one; "
                                + "without it there is nothing to render and its issues would publish "
                                + "with no file at all"));
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
        //
        // AND A ONE-OFF IS EXEMPT, because it has no chain to anchor. The field
        // answers "where does the FIRST of a sequence of periods open"; a
        // publication that comes out once has one issue, and that issue's own
        // interval is the whole answer. The one-off editor deliberately nulls the
        // field and does not render it, so demanding it here failed activation for
        // a control the form does not have -- a query-backed one-off could never
        // leave DRAFT at all.
        boolean interval = s.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;
        if (interval && !s.isOneOff() && s.getFirstIssueStartsAt() == null) {
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
        if (s.getCadence() != SeriesCadence.NONE) {
            if (s.getDomain() == null) {
                e.add(new FieldError("S-20", "domainId",
                        "a series with a cadence must belong to a domain; the domain carries the "
                                + "timezone its cut-offs are read in, and there is no other source "
                                + "for one"));
            } else if (s.getDomain().getDomainId() != null
                    && !isReadableZone(s.getDomain().getTimeZone())) {
                // The domain is the only source of the zone, so a domain that
                // carries none or carries a name nothing can parse leaves every
                // cut-off of this series being read in whatever the server happens
                // to be set to. The cut-off decides which ISO week the issue is
                // named for and when the period closes -- an hour either way at the
                // year boundary is a different year on the cover.
                //
                // Asked only of a domain that NAMES ITSELF, and that is the whole
                // guard. Validation runs without a persistence context, so the
                // dry-run report stands a bare placeholder in for a domain the body
                // referred to by id -- it has no name and no zone because nothing
                // looked it up. Reading the zone off that placeholder would fail
                // every series that has a perfectly good domain, and since
                // activation is gated on a clean report, nothing could be activated
                // at all. This is the third id-backed rule to meet that trap.
                e.add(new FieldError("S-20", "domainId",
                        "domain '" + s.getDomain().getDomainId() + "' carries no readable timezone ("
                                + s.getDomain().getTimeZone() + "), and it is the only source of the "
                                + "zone this series' cut-offs are reckoned in"));
            }
        }

        // S-22. Automatic release is modelled and not yet built. Saving a series
        // that asks for it would leave a publication nobody is watching and
        // nothing is releasing -- silently, until somebody noticed the week was
        // missing. Refusing is the honest answer until the scheduler and the way
        // an aborted release reaches a human both exist.
        if (s.getReleaseMode() == ReleaseMode.AUTO_RELEASE) {
            e.add(new FieldError("S-22", "releaseMode",
                    "automatic release is not available yet; every series releases through the "
                            + "manual gate"));
        }

        // S-23. week, year, weekTo and edition are INJECTED into every report
        // from the issue being rendered. Typing one as a report parameter puts a
        // second, fixed answer beside the derived one, and which of the two the
        // template reads is a question about parameter order rather than about
        // the publication. Refused on the way in, where it is still a typo.
        for (String key : reservedReportParams(s.getReportParams())) {
            e.add(new FieldError("S-23", "reportParams." + key,
                    "'" + key + "' is taken from the issue and cannot be typed here"));
        }

        // C-1 to C-10, on the criteria document itself.
        e.addAll(criteriaRules(s, resolver));

        return e;
    }

    /**
     * The criteria rules alone, as field errors against the criteria control.
     *
     * Separated so a save can ask the one question that must be answered while the
     * form is still open -- does every operand name something -- without also
     * demanding the completeness a draft is allowed to lack.
     */
    public static List<FieldError> criteriaRules(PublicationSeries s,
                                                 CriteriaValidator.OperandResolver resolver) {
        List<FieldError> e = new ArrayList<>();
        for (CriteriaValidator.Violation v : CriteriaValidator.validate(
                s == null ? null : s.getCriteria(),
                resolver == null ? CriteriaValidator.ACCEPT_ALL : resolver)) {
            e.add(new FieldError(v.rule(), "criteria" + v.pointer(), v.message()));
        }
        return e;
    }

    /**
     * C-4 alone: the operands that name nothing.
     *
     * REFUSED ON EVERY SAVE, draft included, and that is the point of separating
     * it from the rest. A draft is allowed to be incomplete -- a missing report, a
     * criteria document with no scope yet -- because those are gaps somebody will
     * fill in. An operand that names an area, a chart or a message series which
     * does not exist is not a gap: it is wrong now and it will still be wrong at
     * activation, and left until then it is discovered inside the publish
     * transaction with the cut-off already stamped.
     */
    public static List<FieldError> danglingOperands(PublicationSeries s,
                                                    CriteriaValidator.OperandResolver resolver) {
        return criteriaRules(s, resolver).stream().filter(e -> "C-4".equals(e.rule())).toList();
    }

    /**
     * The rules a DRAFT may not break either.
     *
     * A draft is allowed to be incomplete -- a missing report, an empty
     * criteria document -- because it is not in the picker yet. It is not
     * allowed to ask for something the system cannot do, or to type a value the
     * issue supplies: those are not gaps to fill in later but mistakes to correct
     * now, and a save that accepted them would store a series whose activation
     * is already known to fail.
     */
    public static final Set<String> HARD_RULES = Set.of("S-22", "S-23");

    /** The hard-rule failures alone, for a save in any status. */
    public static List<FieldError> hardRules(PublicationSeries s) {
        return validate(s, null).stream().filter(e -> HARD_RULES.contains(e.rule())).toList();
    }

    /**
     * S-17. ACTIVE requires every other rule green.
     *
     * A series is allowed to be an incomplete DRAFT; it is not allowed to be an
     * incomplete ACTIVE one, because ACTIVE is what puts it in the picker.
     */
    public static List<FieldError> validateForActivation(PublicationSeries s, Set<String> installationLanguages) {
        return validateForActivation(s, installationLanguages, CriteriaValidator.ACCEPT_ALL);
    }

    /** S-17, with every criteria operand resolved through the caller's resolver. */
    public static List<FieldError> validateForActivation(PublicationSeries s, Set<String> installationLanguages,
                                                         CriteriaValidator.OperandResolver resolver) {
        List<FieldError> e = new ArrayList<>(validate(s, installationLanguages, resolver));
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

    /**
     * The report parameters that are the issue's to supply, never the series'.
     *
     * Matched case-insensitively and after trimming, because the failure this
     * prevents is a typo rather than an attack, and "Week " reaching a template
     * beside the injected week is the same problem spelled differently.
     */
    public static final Set<String> RESERVED_REPORT_PARAMS =
            Set.of("week", "weekto", "year", "edition");

    /**
     * Whether a stored zone name is one java.time can actually read.
     *
     * TimeZone.getTimeZone silently answers GMT for anything it does not
     * recognise, so a misspelt zone does not fail -- it shifts every cut-off of
     * the series by the offset nobody configured, and says nothing.
     */
    private static boolean isReadableZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return false;
        }
        try {
            java.time.ZoneId.of(zone.trim());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Which reserved names a report-parameter map uses, in the order it uses them. */
    public static List<String> reservedReportParams(Map<String, Object> reportParams) {
        List<String> hits = new ArrayList<>();
        if (reportParams == null) {
            return hits;
        }
        for (String key : reportParams.keySet()) {
            if (key != null && RESERVED_REPORT_PARAMS.contains(key.trim().toLowerCase())) {
                hits.add(key);
            }
        }
        return hits;
    }
}
