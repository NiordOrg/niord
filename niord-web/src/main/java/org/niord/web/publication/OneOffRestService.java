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
 * SEPARATE FROM THE SERIES SCREEN ON PURPOSE. A one-off is stored as a series
 * holding a single issue -- that is what keeps PublicationIssue.series NOT NULL
 * and gives the publication somewhere to keep its category and reference format
 * -- but almost nothing on the series form applies to it. Cadence, cut-offs,
 * criteria, numbering, automation and the report are all inert for a document
 * somebody uploads once, and offering them asks an admin to answer thirty
 * questions in order to publish one PDF.
 *
 * The storage decision and the screen decision are deliberately different
 * answers, and this endpoint is where they are kept apart. What it exposes is
 * the four things such a publication actually has: a name, a type, whether it
 * is live, and the file or link behind it.
 *
 * ONE CALL WHERE ONE CALL IS POSSIBLE. Building a one-off out of the general
 * endpoints is create-series, activate, create-issue, publish -- four round
 * trips in an order that cannot be got wrong without the last one refusing for
 * a reason that names none of the earlier three. Link-backed and empty
 * publications finish here in a single request. An uploaded one still needs its
 * bytes, so it comes back with publishable = false and the caller uploads and
 * publishes.
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

    /** One language's value: a name, a link, a file name. */
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
     * A one-off, flattened.
     *
     * The series and its single issue are one thing to whoever maintains it, so
     * they are one row here. `active` folds the three states that actually
     * decide visibility -- series ACTIVE, issue PUBLISHED, and an open public
     * window -- into the single dot the list shows, because any one of them
     * being off is invisible from the other two.
     */
    public static class OneOffVo {
        public String seriesId;
        public String contentMode;
        public String categoryId;
        /** Null means GLOBAL: visible from every domain, as in the legacy data. */
        public String domainId;
        public boolean active;
        public String issuePublicId;
        public String issueStatus;
        public List<LangText> names = new ArrayList<>();
        public List<LangText> links = new ArrayList<>();
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
     * Activation is validated exactly as the status transition would validate
     * it, rather than skipped: a one-off that reaches the list unactivatable is
     * a publication an admin cannot publish and cannot see the reason for.
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public OneOffVo create(OneOffVo request) {
        if (request == null || request.names == null || request.names.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("NAME_BLANK",
                    "a one-off publication needs a name in at least one language");
        }

        PublicationSeries series = new PublicationSeries();
        series.setSeriesId(uniqueSeriesId(request));
        series.setKind(SeriesKind.ONE_OFF);
        series.setCadence(SeriesCadence.NONE);
        series.setContentMode(contentModeOf(request));
        series.setStatus(SeriesStatus.DRAFT);

        // S-1: a series that is not query-backed carries no time relation and no
        // criteria document. A one-off is never query-backed, so both stay null.
        series.setTimeRelation(null);
        series.setCriteria(null);

        series.setNumberingScheme(NumberingScheme.NONE);
        series.setNextIssueCreation(NextIssueCreation.MANUAL);
        series.setReleaseMode(ReleaseMode.MANUAL_GATE);
        series.setMessagePublication(MessagePublication.NONE);
        series.setAliveAtCutoff(false);
        // LEGACY until cutover flips it, matching every other series. Claiming NEW
        // would assert that the public site already serves this publication.
        series.setPublicAuthority(PublicAuthority.LEGACY);

        applyNamesAndReferences(series, request);
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
     * omission: it means "visible from every domain", which is what six of these
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
        if (request == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "a body is required");
        }

        series.setContentMode(contentModeOf(request));
        applyNamesAndReferences(series, request);
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

    private PublicationSeries required(String seriesId) {
        PublicationSeries series = seriesService.findBySeriesId(seriesId);
        if (series == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_FOUND",
                    "no publication series with id " + seriesId);
        }
        if (series.getKind() != SeriesKind.ONE_OFF) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_ONE_OFF",
                    seriesId + " is a " + series.getKind() + " series, not a one-off. Editing it "
                            + "through this form would silently drop the cadence, criteria and "
                            + "numbering the form has no fields for.");
        }
        return series;
    }

    private ContentMode contentModeOf(OneOffVo request) {
        String mode = request.contentMode == null ? "" : request.contentMode.trim();
        if (ContentMode.EXTERNAL_LINK.name().equals(mode)) {
            return ContentMode.EXTERNAL_LINK;
        }
        if (ContentMode.NONE.name().equals(mode)) {
            return ContentMode.NONE;
        }
        if (ContentMode.UPLOADED_FILE.name().equals(mode) || mode.isEmpty()) {
            return ContentMode.UPLOADED_FILE;
        }
        // GENERATED_FROM_QUERY is deliberately unreachable here: it is the one
        // mode that needs the criteria, time relation and report this form has no
        // fields for, so accepting it would build a series that resolves nothing.
        throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                "a one-off is UPLOADED_FILE, EXTERNAL_LINK or NONE, and " + mode + " is none of them");
    }

    private void applyNamesAndReferences(PublicationSeries series, OneOffVo request) {
        if (request.categoryId != null && !request.categoryId.isBlank()) {
            PublicationCategory category = categoryService.findByCategoryId(request.categoryId);
            if (category == null) {
                throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_NOT_FOUND",
                        "no publication category " + request.categoryId);
            }
            series.setCategory(category);
        }
        if (series.getCategory() == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "a category is required: it decides where the publication appears publicly");
        }

        if (request.domainId != null && !request.domainId.isBlank()) {
            Domain domain = domainService.findByDomainId(request.domainId);
            if (domain == null) {
                throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                        "no domain " + request.domainId);
            }
            series.setDomain(domain);
        } else {
            series.setDomain(null);
        }

        for (LangText name : request.names) {
            if (name == null || name.lang == null || name.value == null || name.value.isBlank()) {
                continue;
            }
            series.getLanguages().add(name.lang);
            series.createDesc(name.lang).setName(name.value.trim());
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
            PublicationIssueDesc desc = issue.createDesc(link.lang);
            if (desc.getName() == null || desc.getName().isBlank()) {
                desc.setName(nameFor(request, link.lang));
            }
            desc.setLink(link.value == null || link.value.isBlank() ? null : link.value.trim());
        }
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
     * regenerate = false, because a one-off is never query-backed: there is no
     * report to run and nothing to regenerate. An uploaded publication with no
     * bytes yet is simply left OPEN -- the checklist would refuse it, and
     * refusing here would turn "you still need to attach the file" into an error
     * on save.
     */
    private void publishIfComplete(PublicationSeries series, PublicationIssue issue) {
        if (issue == null || !isPublishable(series, issue)) {
            return;
        }
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, null));
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
        return true;
    }

    private PublicationIssue onlyIssue(PublicationSeries series) {
        List<PublicationIssue> issues = issueService.findBySeries(series);
        return issues.isEmpty() ? null : issues.get(0);
    }

    private static String nameFor(OneOffVo request, String lang) {
        if (request.names == null) {
            return null;
        }
        return request.names.stream()
                .filter(n -> n != null && lang.equals(n.lang) && n.value != null && !n.value.isBlank())
                .map(n -> n.value.trim())
                .findFirst()
                .orElse(null);
    }

    /**
     * A seriesId derived from the name, because nobody should have to invent one.
     *
     * S-16 makes it immutable after create and it is the import/export key, so it
     * matters -- but it is a slug, not a decision, and asking an admin uploading
     * one PDF to mint a permanent identifier is asking the wrong person the wrong
     * question. A supplied id still wins, and a supplied id that collides is
     * refused rather than quietly renumbered.
     */
    private String uniqueSeriesId(OneOffVo request) {
        boolean supplied = request.seriesId != null && !request.seriesId.isBlank();
        String proposed = supplied
                ? request.seriesId.trim()
                : slug(request.names.stream()
                        .filter(n -> n != null && n.value != null && !n.value.isBlank())
                        .map(n -> n.value)
                        .findFirst().orElse("publication"));

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
     * NFD strips the diacritic off aa and oe, but slashed o has none to strip and
     * ae is a ligature, so both would be dropped by a bare non-ASCII filter --
     * turning "Sofartsstyrelsen" into something with a hole in it.
     */
    private static String slug(String text) {
        String folded = text
                .replace("æ", "ae").replace("Æ", "AE")
                .replace("ø", "oe").replace("Ø", "OE")
                .replace("å", "aa").replace("Å", "AA");
        String ascii = Normalizer.normalize(folded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String cleaned = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
        return truncate(cleaned.isEmpty() ? "publication" : cleaned, 64);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).replaceAll("-+$", "");
    }

    private OneOffVo toVo(PublicationSeries series) {
        OneOffVo vo = new OneOffVo();
        vo.seriesId = series.getSeriesId();
        vo.contentMode = series.getContentMode() == null ? null : series.getContentMode().name();
        vo.categoryId = series.getCategory() == null ? null : series.getCategory().getCategoryId();
        vo.domainId = series.getDomain() == null ? null : series.getDomain().getDomainId();

        for (PublicationSeriesDesc d : series.getDescs()) {
            vo.names.add(new LangText(d.getLang(), d.getName()));
        }

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
