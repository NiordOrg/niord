package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationDesc;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PageOrientation;
import org.niord.core.publication.series.PageSize;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesIdSlug;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;
import org.niord.model.publication.PublicationType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A legacy template becomes a series, and lands as a reviewable DRAFT.
 *
 * WHY DRAFT. A series is a configuration row and this import is a TRANSLATION,
 * not a fact. A DRAFT series is invisible to the new-issue picker and has to
 * pass S-1..S-16 before an admin activates it, which is exactly the review a
 * translation needs. Importing straight to ACTIVE would route around the only
 * check that exists on the translation being right.
 *
 * WHY THE seriesId IS AUTHORED (R3). Adopting the legacy UUID would make the
 * new identity a copy of the old one, and the whole point of seriesId is that it
 * is a stable, human-readable name an admin can recognise in a picker.
 * Provenance travels in legacyTemplateId instead, so nothing is lost.
 */
public final class LegacySeriesTranslation {

    /**
     * The seriesId column is varchar(64), so an authored id has to fit.
     *
     * Not cosmetic: the ice-service annexes are titled "Meddelelse fra
     * Marinestaben om istjeneste samt om ismeldinger m.m. for vinteren 2019
     * (Danish Only)", whose slug is 95 characters. MySQL in strict mode rejects
     * the insert rather than truncating, so the import would die on those eight
     * rows.
     *
     * The cap is applied to the BASE at each escalation step, so the suffix
     * always fits rather than being cut off -- a truncated disambiguator would
     * reintroduce exactly the collision it was added to break.
     */
    public static final int MAX_SERIES_ID = SeriesIdSlug.MAX_SERIES_ID;

    /** printSettings keys that map onto a typed column. Anything else is refused. */
    public static final Set<String> ALLOWED_PRINT_SETTINGS =
            Set.of("report", "pageSize", "pageOrientation", "mapThumbnails");

    /** A legacy row that cannot be translated. Aborts the whole import. */
    public static class ImportRefusedException extends RuntimeException {

        private final String code;
        private final String publicationId;

        public ImportRefusedException(String code, String publicationId, String message) {
            super(message);
            this.code = code;
            this.publicationId = publicationId;
        }

        public String getCode() {
            return code;
        }

        public String getPublicationId() {
            return publicationId;
        }
    }

    private LegacySeriesTranslation() {
    }

    /**
     * The authored seriesId for a template.
     *
     * Derived from the template's own title so that an admin opening the DRAFT
     * list recognises what each row is. Deterministic, so re-running the import
     * against the same estate authors the same ids rather than a second set.
     *
     * Titles are unique across the twelve templates in the captured estate, and
     * a collision is refused rather than silently suffixed: two series quietly
     * named x and x-2 is precisely the ambiguity an authored id exists to avoid.
     */
    public static String authorSeriesId(Publication template, Set<String> alreadyAuthored) {
        String title = titleOf(template);
        String slug = fit(slug(title), MAX_SERIES_ID);

        if (slug.isEmpty()) {
            throw new ImportRefusedException("SERIES_ID_UNAUTHORABLE", template.getPublicationId(),
                    "the template has no title in any language, so no seriesId can be authored from it. "
                            + "seriesId is the human-readable identity and is never adopted from the "
                            + "legacy UUID (R3), so this needs a human.");
        }
        if (!alreadyAuthored.add(slug)) {
            throw new ImportRefusedException("SERIES_ID_COLLISION", template.getPublicationId(),
                    "two templates author the same seriesId [" + slug + "]. Suffixing one of them would "
                            + "leave two series nobody can tell apart, which is what an authored id exists "
                            + "to prevent.");
        }
        return slug;
    }

    /**
     * Authors seriesIds for the template-less publications, which become one
     * one-off series each.
     *
     * Escalates only as far as it has to, so the common case stays readable:
     *
     *   1. the title slug                      -- "danish-list-of-lights-2022"
     *   2. plus the publication's start year    -- "ncags-2019"
     *   3. plus a short id, for the whole colliding group
     *
     * Step 3 applies to EVERY member of a colliding group rather than to the
     * later ones, so the result does not depend on iteration order: three NCAGS
     * rows share 2023, and naming one of them "ncags-2023" and the others
     * "ncags-2023-<id>" would make which-one-got-the-clean-name an artefact of
     * the query plan. Determinism matters because the import is disposable --
     * re-running it after a regrouping must author the same ids.
     */
    public static Map<String, String> authorOrphanSeriesIds(List<Publication> orphans) {
        return authorOrphanSeriesIds(orphans, Set.of());
    }

    /**
     * The same, against ids that are already spoken for.
     *
     * The estate has ONE seriesId namespace, and it used to be authored by three
     * routines that could not see each other: the templates, the ruled shared
     * series, and these one-offs. A template titled "Firing Practice Areas" and a
     * standalone publication of the same name therefore both authored
     * "firing-practice-areas", each passing its own local uniqueness check, and
     * the clash surfaced as a MySQL duplicate-key error PART WAY THROUGH the
     * write -- the one place it costs the most to find out.
     *
     * The one-offs are the side that yields. A template becomes a live series
     * that keeps producing issues, so the readable name belongs to it; a one-off
     * is a single publication that will never have a successor. Yielding is also
     * the reversible choice: a suffixed one-off can be renamed later, whereas a
     * refusal here would block the whole estate on a name clash.
     */
    public static Map<String, String> authorOrphanSeriesIds(List<Publication> orphans,
                                                            Set<String> taken) {
        Map<String, String> base = new LinkedHashMap<>();
        for (Publication p : orphans) {
            base.put(p.getPublicationId(), fit(slug(titleOf(p)), MAX_SERIES_ID));
        }
        escalate(orphans, base, taken, p -> {
            Integer year = yearOf(p);
            if (year == null) {
                return fit(slug(titleOf(p)), MAX_SERIES_ID);
            }
            String suffix = "-" + year;
            return fit(slug(titleOf(p)), MAX_SERIES_ID - suffix.length()) + suffix;
        });
        escalate(orphans, base, taken, p -> {
            String suffix = "-" + p.getPublicationId().substring(0, 8);
            return fit(base.get(p.getPublicationId()), MAX_SERIES_ID - suffix.length()) + suffix;
        });
        return base;
    }

    /**
     * Re-authors every member of any group whose name is not free.
     *
     * "Not free" is two things: shared with a sibling in this group, or already
     * claimed outside it. A group of one whose only name is taken has to escalate
     * just as a group of three does, which is why the size test alone was not
     * enough -- the Firing Practice Areas clash was a group of exactly one.
     */
    private static void escalate(List<Publication> orphans, Map<String, String> names,
                                 Set<String> taken,
                                 java.util.function.Function<Publication, String> next) {
        Map<String, List<Publication>> byName = new LinkedHashMap<>();
        for (Publication p : orphans) {
            byName.computeIfAbsent(names.get(p.getPublicationId()), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<String, List<Publication>> e : byName.entrySet()) {
            List<Publication> group = e.getValue();
            if (group.size() > 1 || taken.contains(e.getKey())) {
                group.forEach(p -> names.put(p.getPublicationId(), next.apply(p)));
            }
        }
    }

    /** Trims to a length without leaving a trailing hyphen. */
    static String fit(String slug, int max) {
        if (slug == null || slug.length() <= max) {
            return slug;
        }
        String cut = slug.substring(0, max);
        while (cut.endsWith("-")) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    private static Integer yearOf(Publication p) {
        if (p.getPublishDateFrom() == null) {
            return null;
        }
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.setTime(p.getPublishDateFrom());
        return c.get(java.util.Calendar.YEAR);
    }

    /** Builds the series. Never persists; the caller decides that. */
    public static PublicationSeries translate(Publication template, String seriesId, String importSource) {
        PublicationSeries series = new PublicationSeries();

        series.setSeriesId(seriesId);
        series.setLegacyTemplateId(template.getPublicationId());
        series.setImportSource(importSource);
        series.setStatus(SeriesStatus.DRAFT);

        series.setCadence(cadence(template));
        series.setContentMode(contentMode(template));

        // The domain, which is the ONLY source of a timezone. Ten of the twelve
        // templates carry one and it was being dropped, so every imported series had
        // no domain and therefore no zone -- and a cut-off schedule with no zone is
        // a schedule in whatever zone the reader happens to be in. The domains
        // genuinely differ: Atlantic/Faeroe, UTC for Greenland, Europe/Copenhagen
        // for the rest.
        series.setDomain(template.getDomain());

        // S-1 and S-2: a time relation and a liveness flag belong to the
        // query-backed shape and to NOTHING else. The legacy filter was translated
        // unconditionally, so eight publications that carry no membership at all --
        // contentMode NONE, a link or a file -- were imported claiming to resolve
        // messages published in an interval. Both rules then refused them, and no
        // amount of editing could help: the screen offers those fields only to a
        // query-backed series, so the values nobody could see were the ones
        // blocking the save.
        // ALWAYS translated, even when the result is thrown away. Translating is
        // also what detects a filter nobody has taught the importer about, and that
        // refusal has to hold for every publication -- an unknown filter on a link
        // publication is still an estate the importer does not understand.
        LegacyFilterTranslator.Translation t =
                LegacyFilterTranslator.translate(template.getMessageTagFilter());
        if (series.getContentMode() == ContentMode.GENERATED_FROM_QUERY) {
            series.setTimeRelation(t.timeRelation());
            series.setAliveAtCutoff(t.aliveAtCutoff());
        }

        // Where the cut-off falls, decided from the shape: yearly lists are
        // calendar-driven (in force where the year opens, accumulated where it
        // closes), everything else is stamped at the release.
        series.setCutoffDefault(org.niord.core.publication.series.CutoffDefault.forShape(
                series.getCadence(), series.getTimeRelation()));

        series.setNumberingScheme(numbering(template));
        series.setMessagePublication(template.getMessagePublication());
        series.setLanguageSpecific(template.isLanguageSpecific());

        // Imported series stay on the legacy public path until the cutover flips them
        // deliberately. Importing as NEW would move the whole archive onto the
        // new adapter in the same change that created it.
        series.setPublicAuthority(PublicAuthority.LEGACY);

        // A translated series is reviewed before it releases anything, so the
        // conservative pair: nothing happens without a human.
        series.setReleaseMode(ReleaseMode.MANUAL_GATE);
        series.setNextIssueCreation(NextIssueCreation.MANUAL);

        applyPrintSettings(template, series);
        series.setReportParams(template.getReportParams());
        series.setLanguages(languages(template));
        attachDescs(template, series);

        return series;
    }

    /**
     * printSettings map onto typed columns, and an unknown key aborts.
     *
     * These keys silently change what is in the PDF and in what order, so
     * importing an unrecognised one "best effort" produces a publication that
     * looks right and prints wrong -- worse than refusing.
     */
    private static void applyPrintSettings(Publication template, PublicationSeries series) {
        Map<String, Object> settings = template.getPrintSettings();
        if (settings == null || settings.isEmpty()) {
            return;
        }

        for (String key : settings.keySet()) {
            if (!ALLOWED_PRINT_SETTINGS.contains(key)) {
                throw new ImportRefusedException("PRINT_SETTING_NOT_ALLOWED", template.getPublicationId(),
                        "printSettings carries [" + key + "], which is not one of "
                                + ALLOWED_PRINT_SETTINGS + ". Print settings decide what is in the PDF "
                                + "and in what order; importing an unknown one best-effort is worse than "
                                + "failing.");
            }
        }

        series.setReportId(str(settings.get("report")));
        series.setPageSize(enumOf(PageSize.class, settings.get("pageSize"), "pageSize",
                template.getPublicationId()));
        series.setPageOrientation(enumOf(PageOrientation.class, settings.get("pageOrientation"),
                "pageOrientation", template.getPublicationId()));
        series.setMapThumbnails(settings.get("mapThumbnails") instanceof Boolean b ? b : null);
    }

    /**
     * S-9 requires the four report fields to arrive together or not at all.
     *
     * The legacy estate satisfies this already, but a template carrying a report
     * and no page size would otherwise import as a DRAFT that can never be
     * activated, and the admin would have no idea why.
     */
    public static void assertReportSettingsAreComplete(PublicationSeries series, String publicationId) {
        boolean hasReport = series.getReportId() != null;
        if (hasReport != (series.getPageSize() != null)
                || hasReport != (series.getPageOrientation() != null)
                || hasReport != (series.getMapThumbnails() != null)) {
            throw new ImportRefusedException("PRINT_SETTINGS_INCOMPLETE", publicationId,
                    "report, pageSize, pageOrientation and mapThumbnails must be set exactly together "
                            + "(S-9). Importing a partial set produces a DRAFT that can never be activated.");
        }
    }

    private static SeriesCadence cadence(Publication template) {
        if (template.getPeriodicalType() == null) {
            return SeriesCadence.NONE;
        }
        return switch (template.getPeriodicalType()) {
            case DAILY -> SeriesCadence.DAILY;
            case WEEKLY -> SeriesCadence.WEEKLY;
            case MONTHLY -> SeriesCadence.MONTHLY;
            case YEARLY -> SeriesCadence.YEARLY;
        };
    }

    /** The numbering that matches the cadence; a one-off numbers nothing. */
    private static NumberingScheme numbering(Publication template) {
        return switch (cadence(template)) {
            case WEEKLY -> NumberingScheme.ISO_WEEK_YEAR;
            case MONTHLY -> NumberingScheme.MONTH_YEAR;
            case YEARLY -> NumberingScheme.YEAR_EDITION;
            case DAILY -> NumberingScheme.EDITION_SEQUENCE;
            case NONE -> NumberingScheme.NONE;
        };
    }

    /**
     * A LINK row carrying no link in any language normalises to NONE.
     *
     * The alternative is a series declaring it publishes a link and holding
     * none, which fails validation on activation for a reason the admin cannot
     * act on -- the legacy row simply never had one.
     */
    private static ContentMode contentMode(Publication template) {
        PublicationType type = template.getType();
        if (type == null) {
            return ContentMode.NONE;
        }
        return switch (type) {
            case MESSAGE_REPORT -> ContentMode.GENERATED_FROM_QUERY;
            case REPOSITORY -> ContentMode.UPLOADED_FILE;
            case LINK -> hasAnyLink(template) ? ContentMode.EXTERNAL_LINK : ContentMode.NONE;
            case NONE -> ContentMode.NONE;
        };
    }

    private static boolean hasAnyLink(Publication template) {
        return template.getDescs() != null && template.getDescs().stream()
                .anyMatch(d -> d.getLink() != null && !d.getLink().isBlank());
    }

    private static List<String> languages(Publication template) {
        Set<String> langs = new LinkedHashSet<>();
        if (template.getDescs() != null) {
            template.getDescs().stream()
                    .map(PublicationDesc::getLang)
                    .filter(l -> l != null && !l.isBlank())
                    .forEach(langs::add);
        }
        return new ArrayList<>(langs);
    }

    /**
     * Copies the legacy descs onto the series THROUGH createDesc.
     *
     * createDesc is what sets the back-reference, and the back-reference is what
     * the row is stored by: descs is mappedBy="entity", so a desc built with new
     * and handed to setDescs is still cascaded on save -- with a null entity_id.
     * It survives the import, passes any assertion made against the in-memory
     * object, and then reads back as a series with no name at all.
     */
    private static void attachDescs(Publication template, PublicationSeries series) {
        if (template.getDescs() == null) {
            return;
        }
        for (PublicationDesc d : template.getDescs()) {
            if (d.getLang() == null || d.getLang().isBlank()) {
                continue;
            }
            PublicationSeriesDesc desc = series.createDesc(d.getLang());
            desc.setName(d.getTitle());
            desc.setNameSuggestionPattern(d.getTitleFormat());
            desc.setFileNamePattern(d.getFileName());
            desc.setLinkPattern(d.getLink());
            desc.setMessageReferenceFormat(d.getMessagePublicationFormat());
        }
    }

    private static String titleOf(Publication template) {
        if (template.getDescs() == null) {
            return "";
        }
        // English first, then anything: the id is an operator-facing key, not a
        // user-facing label, so a stable language preference beats the session's.
        return template.getDescs().stream()
                .filter(d -> "en".equalsIgnoreCase(d.getLang()))
                .map(PublicationDesc::getTitle)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElseGet(() -> template.getDescs().stream()
                        .map(PublicationDesc::getTitle)
                        .filter(s -> s != null && !s.isBlank())
                        .findFirst().orElse(""));
    }

    /**
     * Lower-case ASCII with single hyphens. Danish letters fold rather than vanish.
     *
     * Delegated, because the interactive editor mints into the SAME namespace: two
     * implementations of this produced two answers for the same title, and a
     * series id is immutable once authored, so the disagreement would have been
     * permanent rather than a conflict somebody notices.
     */
    static String slug(String s) {
        return SeriesIdSlug.fold(s);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * Legacy stores these lower-case ("portrait") against upper-case constants,
     * so the match folds case -- and only case. A value outside the enum aborts
     * rather than defaulting: silently printing A4 because the stored size was
     * unreadable is the same class of harm as an unknown print-setting key.
     */
    private static <E extends Enum<E>> E enumOf(Class<E> type, Object raw, String field,
                                                String publicationId) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        String name = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new ImportRefusedException("PRINT_SETTING_NOT_ALLOWED", publicationId,
                    "printSettings." + field + " = '" + raw + "' is not one of "
                            + java.util.Arrays.toString(type.getEnumConstants())
                            + ". Defaulting it would change what the PDF looks like without saying so.");
        }
    }
}
