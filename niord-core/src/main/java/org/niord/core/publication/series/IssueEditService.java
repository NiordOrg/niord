package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.niord.core.publication.series.criteria.CriteriaValidator;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Editing an open issue: its names, its interval, its report parameters.
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
 * is what tells them apart, and it is set by the act of typing one.
 */
@ApplicationScoped
public class IssueEditService extends BaseService {

    @Inject
    IssueAuditService audit;

    /**
     * What an edit may change.
     *
     * Every field is nullable and null means "leave it alone", so a caller
     * changing one thing does not have to send the others back correctly. That
     * matters more than it sounds: a form that round-trips the interval in order
     * to rename an issue will eventually round-trip a stale one.
     *
     * `names` is lang to name. A language absent from the map is untouched; a
     * language present with a blank name is refused rather than blanked, because
     * the column is NOT NULL precisely because a nameless issue is unfindable.
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
     */
    public record IssueEdit(Map<String, String> names,
                            Date intervalFrom,
                            Date intervalTo,
                            Map<String, Object> reportParams,
                            IssueCriteriaVo criteriaOverride,
                            boolean clearCriteriaOverride) {

        /** The four-field form, for callers with no criteria to say anything about. */
        public IssueEdit(Map<String, String> names, Date intervalFrom, Date intervalTo,
                         Map<String, Object> reportParams) {
            this(names, intervalFrom, intervalTo, reportParams, null, false);
        }
    }

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

        // Interval first. It re-derives the suggested names, so a rename in the
        // same call has to land after it -- otherwise the rename is overwritten
        // by the re-derivation it was meant to replace.
        applyInterval(issue, edit, actor);
        applyNames(issue, edit, actor);
        applyCriteriaOverride(issue, edit, actor);

        if (edit.reportParams() != null) {
            issue.setReportParams(new LinkedHashMap<>(edit.reportParams()));
        }
        return em.merge(issue);
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
            // The same validator the series form runs, and ACCEPT_ALL for the same
            // reason SeriesValidator uses it: operand EXISTENCE is C-4's job and
            // needs a persistence context this pure check does not have. A document
            // accepted here and refused on the series would be a second definition
            // of a valid document.
            List<CriteriaValidator.Violation> violations =
                    CriteriaValidator.validate(wanted, CriteriaValidator.ACCEPT_ALL);
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
        audit.edited(issue, actor, "CRITERIA_OVERRIDDEN", detail);
    }

    // ------------------------------------------------------------------ interval

    private void applyInterval(PublicationIssue issue, IssueEdit edit, User actor) {
        Date from = edit.intervalFrom() == null ? issue.getIntervalFrom() : edit.intervalFrom();
        Date to = edit.intervalTo() == null ? issue.getIntervalTo() : edit.intervalTo();

        if (equal(from, issue.getIntervalFrom()) && equal(to, issue.getIntervalTo())) {
            return;
        }
        if (from != null && to != null && !from.before(to)) {
            throw new IssueLifecycleService.TransitionRefusedException("INTERVAL_INVERTED",
                    "an interval that ends before it starts selects nothing, and the issue would "
                            + "publish empty rather than fail");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", bounds(issue.getIntervalFrom(), issue.getIntervalTo()));
        detail.put("to", bounds(from, to));

        issue.setIntervalFrom(from);
        issue.setIntervalTo(to);
        // MANUAL, not STAMPED or RECOVERED. Nobody stamped this bound at release
        // and nobody reconstructed it from the chain -- somebody typed it, and a
        // later reader deciding whether to trust the period needs to know which.
        issue.setIntervalFromSource(IntervalBoundSource.MANUAL);

        renumber(issue);
        resuggestNames(issue);

        audit.edited(issue, actor, "INTERVAL_CHANGED", detail);
    }

    /**
     * Re-derives week, weekTo and year from the moved interval.
     *
     * These are what every issue list and name pattern reads, so leaving them
     * behind after an interval edit produces an issue labelled "uge 29" sitting in
     * week 30's period. Derived in the SERIES' zone, never the JVM's: the domains
     * differ, and a cut-off near midnight lands in a different ISO week depending
     * on which one is used.
     *
     * Silent when there is no cut-off to derive from. An issue whose period has
     * neither a stamped cut-off nor an end bound has no week yet, and inventing one
     * from the interval start would name it after the wrong end of its own window.
     */
    private void renumber(PublicationIssue issue) {
        Date cutoff = issue.effectiveCutoff();
        if (cutoff == null) {
            return;
        }
        IssueNaming.Numbers numbers = IssueNaming.derive(
                cutoff, issue.getIntervalFrom(), zoneOf(issue), null);
        issue.setWeek(numbers.week());
        issue.setWeekTo(numbers.weekTo());
        issue.setYear(numbers.year());
    }

    /**
     * Re-suggests the names the series would have produced for the new interval.
     *
     * Only where nobody has overridden them. A suggested name is a rendering of
     * the period rather than a decision, so it follows the period; a typed one is
     * a decision, and re-deriving over it would discard it with no trace.
     */
    private void resuggestNames(PublicationIssue issue) {
        PublicationSeries series = issue.getSeries();
        if (series == null) {
            return;
        }
        for (PublicationIssueDesc desc : issue.getDescs()) {
            if (desc.isNameOverridden()) {
                continue;
            }
            String suggested = IssueLifecycleService.suggestName(
                    series, desc.getLang(), issue.getIntervalFrom());
            if (suggested != null && !suggested.isBlank()) {
                desc.setName(suggested);
            }
        }
    }

    // --------------------------------------------------------------------- names

    private void applyNames(PublicationIssue issue, IssueEdit edit, User actor) {
        if (edit.names() == null || edit.names().isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : edit.names().entrySet()) {
            String lang = entry.getKey();
            String name = entry.getValue();

            if (name == null || name.isBlank()) {
                throw new IssueLifecycleService.TransitionRefusedException("NAME_BLANK",
                        "a nameless issue is unfindable in every list that shows it; clear the "
                                + "override instead if the series should name it again");
            }
            PublicationIssueDesc desc = descFor(issue, lang);
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

            audit.edited(issue, actor, "NAME_CHANGED", detail);
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

    private static java.time.ZoneId zoneOf(PublicationIssue issue) {
        return issue.getSeries() == null
                ? java.time.ZoneId.of("UTC")
                : issue.getSeries().cutoffZone();
    }

    private static PublicationIssueDesc descFor(PublicationIssue issue, String lang) {
        return issue.getDescs().stream()
                .filter(d -> d.getLang() != null && d.getLang().equals(lang))
                .findFirst()
                .orElseThrow(() -> new IssueLifecycleService.TransitionRefusedException("NO_SUCH_LANGUAGE",
                        "the issue has no " + lang + " desc row; the series may not be configured for it"));
    }
}
