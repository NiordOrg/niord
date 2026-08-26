package org.niord.web.publication;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationIssueService;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.PublicationSeriesService;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;
import org.niord.core.publication.vo.MessagePublication;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One-off publications: the surface for something published once.
 *
 * TWO DECISIONS THAT ARE NOT THE SAME DECISION, and conflating them is what this
 * endpoint exists to undo.
 *
 * The STORAGE decision is that a one-off is a series holding a single issue.
 * That stays: it keeps PublicationIssue.series NOT NULL and gives the
 * publication somewhere to keep its category, its report and its reference
 * format.
 *
 * The SCREEN decision is a different question, and it was never separately made.
 * A one-off has no cadence, no cut-off schedule, no numbering across issues and
 * no automatic successor -- so offering those asks an admin to answer questions
 * that have no answer. But it has EVERYTHING ELSE a series has, including a
 * query-backed content mode with its criteria and its report. An earlier version
 * of this endpoint offered only UPLOADED_FILE and EXTERNAL_LINK, which is
 * narrower than the data: three of the five one-offs in the estate are
 * contentMode NONE, and nothing prevents a one-off being generated from a query.
 *
 * So the wire shape carries the WHOLE series VO and this endpoint ENFORCES the
 * one-off constraints rather than re-declaring the fields a one-off may have.
 * A parallel value object would be a second place to add every future series
 * field, and the field that got forgotten would be missing only here.
 *
 * ONE CALL WHERE ONE CALL IS POSSIBLE. Through the general endpoints, creating a
 * one-off is create-series, activate, create-issue, publish -- four round trips
 * in an order that cannot be got wrong without the last one refusing for a
 * reason naming none of the earlier three. Link-backed, query-backed and empty
 * publications finish here in a single request; an uploaded one comes back with
 * publishable = false and the caller attaches the bytes.
 */
@Path("/one-off-publications")
@RequestScoped
@Transactional
@SuppressWarnings("unused")
public class OneOffRestService {

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    PublicationIssueService issueService;

    @Inject
    PublicationCategoryService categoryService;

    @Inject
    DomainService domainService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    // ------------------------------------------------------------- wire shapes

    /** One language's value: a link, or a file name. */
    public static class LangText {
        public String lang;
        public String value;

        public LangText() {
        }

        public LangText(String lang, String value) {
            this.lang = lang;
            this.value = value;
        }
    }

    /**
     * A one-off: the whole series, plus the parts of its single issue that
     * whoever maintains the publication actually deals with.
     *
     * `series` is the ordinary series value object, so a one-off has every option
     * a series has. The constraints that make it a one-off are applied on the way
     * in, not expressed by leaving fields out.
     *
     * `active` folds the three states that really decide visibility -- series
     * ACTIVE, issue PUBLISHED, and an open public window -- into the single dot
     * the list shows, because any one of them being off is invisible from the
     * other two.
     */
    public static class OneOffVo {
        public SystemPublicationSeriesVo series;
        public boolean active;
        public String issuePublicId;
        public String issueStatus;
        /** Per language, for an EXTERNAL_LINK publication. */
        public List<LangText> links = new ArrayList<>();
        /** Per language, read-only: what was uploaded, if anything. */
        public List<LangText> fileNames = new ArrayList<>();
        /** False while the publication still needs its bytes before it can go live. */
        public boolean publishable;
    }

    // -------------------------------------------------------------------- read

    /** The dashboard list: every publication whose kind is ONE_OFF. */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<OneOffVo> list() {
        return seriesService.findAll().stream()
                .filter(s -> s.getKind() == SeriesKind.ONE_OFF)
                .map(this::toVo)
                .toList();
    }

    // ------------------------------------------------------------------ writes

    /**
     * Create, and take it as far towards live as its content allows.
     *
     * Activation is validated exactly as the status transition would validate it
     * rather than skipped: a one-off that reaches the list unactivatable is a
     * publication an admin cannot publish and cannot see the reason for.
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public OneOffVo create(OneOffVo request) {
        SystemPublicationSeriesVo vo = seriesOf(request);

        PublicationSeries series = new PublicationSeries();
        series.updateFromVo(vo);
        series.setSeriesId(uniqueSeriesId(vo));
        series.setStatus(SeriesStatus.DRAFT);
        if (series.getPublicAuthority() == null) {
            // LEGACY until cutover flips it, matching every other series. Claiming
            // NEW would assert that the public site already serves this.
            series.setPublicAuthority(PublicAuthority.LEGACY);
        }
        if (series.getMessagePublication() == null) {
            series.setMessagePublication(MessagePublication.NONE);
        }
        resolveReferences(series, vo);
        forceOneOffShape(series);
        requireCategory(series);

        PublicationSeries saved = seriesService.create(series);
        PublicationIssue issue = lifecycle.create(saved, new Date(), IntervalBoundSource.MANUAL, null);
        applyLinks(issue, request);

        if (request.active) {
            activate(saved);
            publishIfComplete(saved, issue);
        }
        return toVo(saved);
    }

    /**
     * Update, including clearing the domain.
     *
     * A blank domainId CLEARS it, because null is a value here rather than an
     * omission: it means "visible from every domain", which is what four of these
     * publications are in the legacy data and what the message editor's
     * publication picker relies on.
     */
    @PUT
    @Path("/{seriesId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public OneOffVo update(@PathParam("seriesId") String seriesId, OneOffVo request) {
        PublicationSeries series = required(seriesId);
        SystemPublicationSeriesVo vo = seriesOf(request);

        // S-16, REFUSED rather than silently corrected, matching the series
        // endpoint. The id is the import/export key and the citation handle, so a
        // body naming a different one is a client that thinks it is renaming
        // something -- and quietly saving under the old id would leave it believing
        // the rename worked.
        if (vo.getSeriesId() != null && !vo.getSeriesId().isBlank()
                && !seriesId.equals(vo.getSeriesId())) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_ID_IMMUTABLE",
                    "seriesId cannot be changed after create. The path says " + seriesId
                            + " and the body says " + vo.getSeriesId());
        }

        // The status is NOT taken from the body: activation is a transition that
        // validates, and letting a save carry a status would route around it.
        SeriesStatus status = series.getStatus();
        series.updateFromVo(vo);
        // S-16: the id is the import/export key and the citation handle.
        series.setSeriesId(seriesId);
        series.setStatus(status);
        resolveReferences(series, vo);
        forceOneOffShape(series);
        requireCategory(series);

        PublicationSeries saved = seriesService.update(series);

        PublicationIssue issue = onlyIssue(saved);
        if (issue != null) {
            applyLinks(issue, request);
            issueService.update(issue);
        }

        if (request.active && saved.getStatus() != SeriesStatus.ACTIVE) {
            activate(saved);
            publishIfComplete(saved, issue);
        }
        return toVo(saved);
    }

    // ------------------------------------------------------------------ pieces

    private static SystemPublicationSeriesVo seriesOf(OneOffVo request) {
        if (request == null || request.series == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "a one-off carries a series body; it is a series holding a single issue");
        }
        return request.series;
    }

    /**
     * The constraints that make a one-off a one-off, applied rather than declared.
     *
     * Everything nulled here is a question about a SEQUENCE of issues -- when the
     * next one cuts off, what it will be numbered, whether it appears by itself.
     * A publication that comes out once has no answer to any of them, and S-5,
     * S-6 and S-7 refuse the cut-off fields on a cadence-less series anyway.
     *
     * What is deliberately NOT touched: the content mode and everything that
     * hangs off it. A one-off may be generated from a query, and then it needs
     * its criteria, its time relation and its report exactly as a series does.
     */
    // Package-private so the shape it enforces can be asserted directly: what a
    // one-off may and may not carry is the whole contract of this endpoint.
    static void forceOneOffShape(PublicationSeries series) {
        series.setKind(SeriesKind.ONE_OFF);
        series.setCadence(SeriesCadence.NONE);
        series.setNominalCutoffDay(null);
        series.setNominalCutoffDayOfMonth(null);
        series.setNominalCutoffMonth(null);
        series.setNominalCutoffTime(null);
        series.setNumberingScheme(NumberingScheme.NONE);
        series.setNextIssueCreation(NextIssueCreation.MANUAL);
        series.setFirstIssueStartsAt(null);
        if (series.getReleaseMode() == null) {
            series.setReleaseMode(ReleaseMode.MANUAL_GATE);
        }

        // S-1 and S-2. Only a query-backed series carries a time relation, a
        // criteria document and a liveness filter; on any other content mode all
        // three must be ABSENT rather than merely false. aliveAtCutoff in
        // particular is a filter applied to a query, so "false" on a publication
        // with no query claims a filter that ran and passed everything -- which is
        // exactly the distinction S-2 exists to keep.
        if (series.getContentMode() != ContentMode.GENERATED_FROM_QUERY) {
            series.setTimeRelation(null);
            series.setCriteria(null);
            series.setAliveAtCutoff(null);
        } else if (series.getAliveAtCutoff() == null) {
            // S-2 the other way: a query-backed series must SAY. The form sends a
            // boolean, but a body that omits it would otherwise fail activation
            // for a field the admin was never asked about.
            series.setAliveAtCutoff(Boolean.FALSE);
        }

        // Names are suggested per issue from a pattern, across a series. One issue
        // has nothing to derive and nothing to derive it from.
        for (PublicationSeriesDesc desc : series.getDescs()) {
            desc.setNameSuggestionPattern(null);
        }
    }

    private PublicationSeries required(String seriesId) {
        PublicationSeries series = seriesService.findBySeriesId(seriesId);
        if (series == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_FOUND",
                    "no publication series with id " + seriesId);
        }
        if (series.getKind() != SeriesKind.ONE_OFF) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_ONE_OFF",
                    seriesId + " is a " + series.getKind() + " series, not a one-off. Saving it "
                            + "through this form would force cadence NONE and drop its numbering "
                            + "and its schedule.");
        }
        return series;
    }

    private static void requireCategory(PublicationSeries series) {
        // S-19, checked here rather than left to the flush: the column is NOT NULL,
        // so without this it dies inside Hibernate naming a Java field, which tells
        // an admin nothing about the empty dropdown that caused it.
        if (series.getCategory() == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "a category is required: it decides where the publication appears publicly");
        }
    }

    private void resolveReferences(PublicationSeries series, SystemPublicationSeriesVo vo) {
        if (vo.getCategoryId() != null && !vo.getCategoryId().isBlank()) {
            PublicationCategory category = categoryService.findByCategoryId(vo.getCategoryId());
            if (category == null) {
                throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_NOT_FOUND",
                        "no publication category " + vo.getCategoryId());
            }
            series.setCategory(category);
        }

        // Absent CLEARS it. Null is a value here: it means visible from every
        // domain, and without a way to say it there is no way back to global.
        if (vo.getDomainId() != null && !vo.getDomainId().isBlank()) {
            Domain domain = domainService.findByDomainId(vo.getDomainId());
            if (domain == null) {
                throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                        "no domain " + vo.getDomainId());
            }
            series.setDomain(domain);
        } else {
            series.setDomain(null);
        }
    }

    private void applyLinks(PublicationIssue issue, OneOffVo request) {
        if (issue == null || request.links == null) {
            return;
        }
        for (LangText link : request.links) {
            if (link == null || link.lang == null) {
                continue;
            }
            // FOUND, not created. createDesc always makes a new row, and the issue
            // lifecycle has already written one per language -- so creating a
            // second violates UNIQUE (entity_id, lang) and fails the whole save
            // with a database error naming a column, which tells an admin nothing
            // about the link they just typed.
            PublicationIssueDesc desc = descFor(issue, link.lang);
            if (desc.getName() == null || desc.getName().isBlank()) {
                desc.setName(seriesNameFor(request, link.lang));
            }
            desc.setLink(link.value == null || link.value.isBlank() ? null : link.value.trim());
        }
    }

    /**
     * The issue's desc for a language, created only if it genuinely has none.
     *
     * Package-private so the reuse can be asserted: creating a second desc for a
     * language violates UNIQUE (entity_id, lang), and the failure surfaces as a
     * 500 naming a database column rather than anything about the save.
     */
    static PublicationIssueDesc descFor(PublicationIssue issue, String lang) {
        for (PublicationIssueDesc existing : issue.getDescs()) {
            if (lang.equals(existing.getLang())) {
                return existing;
            }
        }
        return issue.createDesc(lang);
    }

    private void activate(PublicationSeries series) {
        List<SeriesValidator.FieldError> errors = SeriesValidator.validateForActivation(series, null);
        if (!errors.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    errors.size() + " rule(s) fail: " + errors);
        }
        series.setStatus(SeriesStatus.ACTIVE);
        seriesService.update(series);
    }

    /**
     * Publish when there is something to publish.
     *
     * regenerate is true only for a query-backed publication, which is the one
     * kind with a report to run. An uploaded publication with no bytes yet is
     * left OPEN: the checklist would refuse it, and refusing here would turn
     * "you still need to attach the file" into an error on save.
     */
    private void publishIfComplete(PublicationSeries series, PublicationIssue issue) {
        if (issue == null || !isPublishable(series, issue)) {
            return;
        }
        boolean queryBacked = series.getContentMode() == ContentMode.GENERATED_FROM_QUERY;
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(queryBacked, Set.of(), null, null));
    }

    private boolean isPublishable(PublicationSeries series, PublicationIssue issue) {
        if (issue == null || issue.getStatus() != IssueStatus.OPEN) {
            return false;
        }
        if (series.getContentMode() == ContentMode.UPLOADED_FILE) {
            return !issue.getDescs().isEmpty() && issue.getDescs().stream()
                    .allMatch(d -> d.getFilePath() != null && !d.getFilePath().isBlank());
        }
        if (series.getContentMode() == ContentMode.EXTERNAL_LINK) {
            return !issue.getDescs().isEmpty() && issue.getDescs().stream()
                    .allMatch(d -> d.getLink() != null && !d.getLink().isBlank());
        }
        // GENERATED_FROM_QUERY publishes by running its report; NONE has nothing
        // to attach at all.
        return true;
    }

    private PublicationIssue onlyIssue(PublicationSeries series) {
        List<PublicationIssue> issues = issueService.findBySeries(series);
        return issues.isEmpty() ? null : issues.get(0);
    }

    private static String seriesNameFor(OneOffVo request, String lang) {
        if (request.series == null || request.series.getDescs() == null) {
            return null;
        }
        return request.series.getDescs().stream()
                .filter(d -> d != null && lang.equals(d.getLang())
                        && d.getName() != null && !d.getName().isBlank())
                .map(d -> d.getName().trim())
                .findFirst()
                .orElse(null);
    }

    /**
     * A seriesId derived from the name, because nobody should have to invent one.
     *
     * S-16 makes it immutable after create and it is the import/export key, so it
     * matters -- but it is a slug, not a decision, and asking an admin publishing
     * one PDF to mint a permanent identifier is asking the wrong person the wrong
     * question. A supplied id wins, and a supplied id that collides is refused
     * rather than quietly renumbered.
     */
    private String uniqueSeriesId(SystemPublicationSeriesVo vo) {
        boolean supplied = vo.getSeriesId() != null && !vo.getSeriesId().isBlank();
        String firstName = vo.getDescs() == null ? null : vo.getDescs().stream()
                .filter(d -> d != null && d.getName() != null && !d.getName().isBlank())
                .map(d -> d.getName())
                .findFirst().orElse(null);
        if (!supplied && firstName == null) {
            throw new IssueLifecycleService.TransitionRefusedException("NAME_BLANK",
                    "a one-off publication needs a name in at least one language");
        }

        String proposed = supplied ? vo.getSeriesId().trim() : slug(firstName);
        if (seriesService.findBySeriesId(proposed) == null) {
            return proposed;
        }
        if (supplied) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_ID_TAKEN",
                    "a series with id " + proposed + " already exists");
        }
        for (int n = 2; n < 100; n++) {
            String candidate = truncate(proposed, 60) + "-" + n;
            if (seriesService.findBySeriesId(candidate) == null) {
                return candidate;
            }
        }
        throw new IssueLifecycleService.TransitionRefusedException("SERIES_ID_TAKEN",
                "could not derive a free series id from " + proposed);
    }

    /**
     * Danish letters fold to ASCII rather than vanishing.
     *
     * NFD strips the diacritic off the ring above a, but slashed o has none to
     * strip and ae is a ligature, so a bare non-ASCII filter would drop both and
     * leave a hole in the middle of the identifier.
     */
    private static String slug(String text) {
        String folded = text
                .replace("æ", "ae").replace("Æ", "AE")
                .replace("ø", "oe").replace("Ø", "OE")
                .replace("å", "aa").replace("Å", "AA");
        String ascii = Normalizer.normalize(folded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String cleaned = ascii.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "").replaceAll("-+$", "");
        return truncate(cleaned.isEmpty() ? "publication" : cleaned, 64);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).replaceAll("-+$", "");
    }

    private OneOffVo toVo(PublicationSeries series) {
        OneOffVo vo = new OneOffVo();
        vo.series = series.toVo(SystemPublicationSeriesVo.class);

        PublicationIssue issue = onlyIssue(series);
        if (issue != null) {
            vo.issuePublicId = issue.getPublicId();
            vo.issueStatus = issue.getStatus() == null ? null : issue.getStatus().name();
            for (PublicationIssueDesc d : issue.getDescs()) {
                vo.links.add(new LangText(d.getLang(), d.getLink()));
                vo.fileNames.add(new LangText(d.getLang(), d.getFileName()));
            }
            vo.publishable = isPublishable(series, issue);
        }

        Date now = new Date();
        vo.active = series.getStatus() == SeriesStatus.ACTIVE
                && issue != null
                && issue.getStatus() == IssueStatus.PUBLISHED
                && (issue.getPublicFrom() == null || !issue.getPublicFrom().after(now))
                && (issue.getPublicTo() == null || issue.getPublicTo().after(now));
        return vo;
    }
}
