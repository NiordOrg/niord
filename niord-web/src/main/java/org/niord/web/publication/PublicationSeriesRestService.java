package org.niord.web.publication;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.user.Roles;
import org.niord.core.publication.series.IssueAuditService;
import org.niord.core.publication.series.IssueDraftService;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.vo.IssueDraftVo;
import org.niord.core.user.UserService;
import org.niord.core.publication.series.replay.ShadowDiffService;
import org.niord.core.publication.series.replay.DiagnosticReportService;
import org.niord.core.publication.series.replay.ShadowDiffRun;
import org.niord.core.publication.series.legacy.LegacyImportReportVo;
import org.niord.core.publication.series.legacy.CutoverPreflightService;
import org.niord.core.publication.series.legacy.LegacyImportService;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesService;
import org.niord.core.NiordApp;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.MemberResolutionService;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.PublicationSeriesDescVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.series.vo.PublicationSeriesVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The series resource.
 *
 * The tier split is enforced by which VO class each endpoint returns, not by a
 * parameter. An endpoint that returned "the public or the system shape depending
 * on a flag" is one wrong flag away from serving the criteria document and the
 * cutover switch to an anonymous caller -- and nothing about the response would
 * look wrong.
 *
 * So: /search returns the lean shape at the editor tier; /search-details returns
 * the system shape and requires admin. Two endpoints, two types, no runtime
 * decision.
 *
 * NOTHING HERE IS ANONYMOUS. The lean shape is the shape an EDITOR gets, not the
 * shape the world gets: listing a series discloses that it exists, what it is
 * called and which category it belongs to, and doing that for DRAFT and RETIRED
 * rows to an unauthenticated caller is an enumeration of the whole catalogue in
 * every state. The public site reads publications through the public adapter,
 * which serves released issues only.
 */
@Path("/publication-series")
@RequestScoped
@Transactional
@SuppressWarnings("unused")
public class PublicationSeriesRestService extends AbstractBatchableRestService {

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    NiordApp app;

    @Inject
    MemberResolutionService memberResolver;

    @Inject
    LegacyImportService importService;

    @Inject
    CutoverPreflightService preflight;

    @Inject
    ShadowDiffService shadowDiff;

    @Inject
    DiagnosticReportService diagnostics;

    @Inject
    PublicationCategoryService categoryService;

    @Inject
    DomainService domainService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueDraftService drafts;

    @Inject
    IssueAuditService audit;

    @Inject
    UserService userService;

    // ------------------------------------------------------------------ reads

    /**
     * S1. The editor list, lean shape.
     *
     * ACTIVE only, and that is the contract rather than a convenience. This is
     * what a picker and a filter panel read, and offering a DRAFT series there
     * lets an editor bind a message to a publication nobody has finished
     * configuring; offering a RETIRED one invites a citation into something the
     * public list no longer carries. An admin who needs every state has
     * /search-details, which takes a status and says so.
     *
     * The language narrows the desc rows, with a fallback to whatever the series
     * does have. A one-language series is legitimate, and answering a request for
     * English with an empty descs array leaves every consumer rendering the raw
     * seriesId.
     */
    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.USER)
    public List<PublicationSeriesVo> search(@QueryParam("lang") String lang) {
        List<PublicationSeriesVo> out = new ArrayList<>();
        for (PublicationSeries s : seriesService.findByStatus(SeriesStatus.ACTIVE)) {
            out.add(narrowToLang(s.toVo(PublicationSeriesVo.class), lang));
        }
        return out;
    }

    /**
     * Keeps the desc row for one language, or the first one when it has none.
     *
     * The fallback is the behaviour every other localized read in the product
     * has: a caller that asked for a language it cannot have is better served the
     * name that exists than an empty list it has to invent a label for.
     */
    private static <V extends PublicationSeriesVo> V narrowToLang(V vo, String lang) {
        if (lang == null || lang.isBlank() || vo.getDescs().size() < 2) {
            return vo;
        }
        List<PublicationSeriesDescVo> wanted = vo.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .toList();
        List<PublicationSeriesDescVo> kept = wanted.isEmpty()
                ? List.of(vo.getDescs().get(0))
                : wanted;
        vo.getDescs().retainAll(kept);
        return vo;
    }

    /**
     * S2. The editor list, with everything the public shape omits.
     *
     * The released-issue count travels with every row, from ONE grouped query
     * rather than one per series. It is what S-18 turns on: the citation channel
     * is fixed for the life of a series once an issue is out, so the editor has to
     * DISABLE that control rather than let an admin type a change the save will
     * refuse -- and a list screen cannot ask per row without a query per row.
     */
    @GET
    @Path("/search-details")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public List<SystemPublicationSeriesVo> searchDetails(@QueryParam("status") String status) {
        List<PublicationSeries> found = status == null
                ? seriesService.findAll()
                : seriesService.findByStatus(seriesStatusOf(status));

        Map<String, Integer> released = seriesService.publishedIssueCounts();
        List<SystemPublicationSeriesVo> out = new ArrayList<>();
        for (PublicationSeries s : found) {
            SystemPublicationSeriesVo vo = s.toVo(SystemPublicationSeriesVo.class);
            vo.setPublishedIssueCount(
                    PublicationSeriesService.publishedIssueCountOf(released, s.getSeriesId()));
            out.add(vo);
        }
        return out;
    }

    /** S3. One series, lean shape. */
    @GET
    @Path("/series/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.USER)
    public PublicationSeriesVo get(@PathParam("seriesId") String seriesId) {
        return required(seriesId).toVo(PublicationSeriesVo.class);
    }

    /** S4. One series, editor shape. */
    @GET
    @Path("/editable-series/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public SystemPublicationSeriesVo getEditable(@PathParam("seriesId") String seriesId) {
        PublicationSeries series = required(seriesId);
        SystemPublicationSeriesVo vo = series.toVo(SystemPublicationSeriesVo.class);
        // The settings form locks the citation channel off this (S-18), so the
        // single read carries it as well as the list does. A form that had to
        // consult the list to know whether to disable a control would show the
        // control enabled on a deep link.
        vo.setPublishedIssueCount(seriesService.publishedIssueCount(series));
        return vo;
    }

    /** S5. Several at once, for a picker. */
    @GET
    @Path("/series-by-ids/{seriesIds}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.USER)
    public List<PublicationSeriesVo> byIds(@PathParam("seriesIds") String seriesIds) {
        List<PublicationSeriesVo> out = new ArrayList<>();
        for (String id : seriesIds.split(",")) {
            PublicationSeries s = seriesService.findBySeriesId(id.trim());
            if (s != null) {
                out.add(s.toVo(PublicationSeriesVo.class));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ writes

    /** S8. Create. */
    @POST
    @Path("/series/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public SystemPublicationSeriesVo create(SystemPublicationSeriesVo vo) {
        if (vo == null || vo.getSeriesId() == null || vo.getSeriesId().isBlank()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "seriesId is required; it is the human-authored, stable identity of the series");
        }
        if (seriesService.findBySeriesId(vo.getSeriesId()) != null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_ID_TAKEN",
                    "a series with id '" + vo.getSeriesId() + "' already exists");
        }

        PublicationSeries series = new PublicationSeries();
        series.updateFromVo(vo);
        resolveReferences(series, vo);

        // S-19, checked here rather than left to the flush.
        //
        // The category column is NOT NULL, so a series without one dies inside
        // Hibernate with "not-null property references a null or transient value" --
        // a 500 that names a Java field and tells an admin nothing about the empty
        // dropdown that caused it. A DRAFT is allowed to be incomplete in every
        // other respect; this one is a database constraint rather than a rule about
        // completeness, so it cannot wait for activation.
        if (series.getCategory() == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "a category is required: every series belongs to one, and it decides where the "
                            + "series appears on the public page");
        }

        // Created as DRAFT whatever the client asked for. ACTIVE is what puts a
        // series in the picker, and S-17 requires a complete series for that --
        // so activation is the status transition, which validates. Letting create
        // set ACTIVE would route around that check.
        series.setStatus(SeriesStatus.DRAFT);
        refuseHardRules(series);

        return seriesService.create(series).toVo(SystemPublicationSeriesVo.class);
    }

    /**
     * A draft may be incomplete; it may not be wrong. Asking for a release mode
     * the system cannot honour, or typing a report parameter the issue supplies,
     * is refused on the save that carries it -- as field errors, so the form can
     * put the message beside the control -- rather than stored and discovered at
     * activation.
     */
    private static void refuseHardRules(PublicationSeries series) {
        List<SeriesValidator.FieldError> hard = SeriesValidator.hardRules(series);
        if (!hard.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    hard.size() + " rule(s) fail: " + hard, hard);
        }
    }

    /**
     * Resolves the two references the value object carries only by id.
     *
     * They are separate from updateFromVo because that method is on the entity
     * and has no persistence context. A category that does not exist is refused
     * rather than created: the categories are a small curated set with a publish
     * flag and a priority that decides where the series appears on the public
     * page, and inventing one silently would put a series in a category nobody
     * configured.
     */
    private void resolveReferences(PublicationSeries series, SystemPublicationSeriesVo vo) {
        if (vo.getCategoryId() != null && !vo.getCategoryId().isBlank()) {
            PublicationCategory category = categoryService.findByCategoryId(vo.getCategoryId());
            if (category == null) {
                throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_NOT_FOUND",
                        "no publication category '" + vo.getCategoryId() + "'");
            }
            series.setCategory(category);
        }
        // FULL REPRESENTATION on the domain: an absent domainId CLEARS it rather
        // than meaning "leave whatever is there".
        //
        // Null is a meaningful VALUE here, not the absence of one -- it means
        // "global, visible from every domain", which is what the publication
        // picker implements as "domain IS NULL OR domain = the current one". So
        // there has to be a way to say it, and a PUT carrying the whole series is
        // where it gets said. Treating null as "unchanged" left no way at all to
        // put a publication back to global once it had been given a domain.
        //
        // The category is deliberately NOT symmetric: that column is NOT NULL and
        // has no "none" to express.
        if (vo.getDomainId() != null && !vo.getDomainId().isBlank()) {
            Domain domain = domainService.findByDomainId(vo.getDomainId());
            if (domain == null) {
                throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                        "no domain '" + vo.getDomainId() + "'");
            }
            series.setDomain(domain);
        } else {
            series.setDomain(null);
        }
    }

    /**
     * S6. An unsaved series, filled in as far as the server can.
     *
     * Not a convenience. A create form built client-side has to guess the
     * defaults, and a guess that drifts from the server's produces a series that
     * validates in the browser and is refused on save -- or worse, one that saves
     * with a shape nobody chose. The defaults live here because the rules that
     * check them live here.
     *
     * DRAFT, always. ACTIVE is what puts a series in the picker and S-17 requires a
     * complete one, so activation is a transition that validates rather than a
     * field a create form can set.
     */
    @GET
    @Path("/new-series-template")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public SystemPublicationSeriesVo newSeriesTemplate() {
        return newSeriesTemplate(app.getLanguages());
    }

    /**
     * The template, without the container.
     *
     * Package-private and static for the same reason validationReport is: what the
     * template CONTAINS is worth asserting, and it had two fields disagreeing with
     * each other that no CDI-free test could reach.
     */
    static SystemPublicationSeriesVo newSeriesTemplate(String[] languages) {
        SystemPublicationSeriesVo vo = new SystemPublicationSeriesVo();
        vo.setStatus(SeriesStatus.DRAFT.name());
        vo.setContentMode(ContentMode.GENERATED_FROM_QUERY.name());
        vo.setCadence(SeriesCadence.NONE.name());
        vo.setNumberingScheme(NumberingScheme.NONE.name());
        vo.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL.name());
        vo.setAliveAtCutoff(Boolean.FALSE);
        vo.setReleaseMode(ReleaseMode.MANUAL_GATE.name());
        // MANUAL, to agree with cadence NONE. S-8 says a one-off has no next issue
        // to create automatically, so the pair the template shipped broke a rule
        // against itself -- the create form corrected it silently on save, which is
        // the worst place for a template to be wrong.
        vo.setNextIssueCreation(NextIssueCreation.MANUAL.name());
        vo.setMessagePublication(MessagePublication.NONE.name());

        // LEGACY until cutover flips it, matching every imported series. A new
        // series claiming NEW would assert that the public site already serves it.
        vo.setPublicAuthority(PublicAuthority.LEGACY.name());

        // Zero, and stated. The form disables the citation channel once a series
        // has released an issue (S-18), and a missing count on a series that has
        // never had one would disable it on the create screen.
        vo.setPublishedIssueCount(0);

        // One desc row per configured language, so the form has a row to type into
        // for each -- C5: a payload narrowed to one language is uneditable.
        //
        // DECLARED as well as written. S-12 refuses a desc row for a language the
        // series does not list, so a template that emitted the rows and left
        // languages empty failed its own validation twice over and told the admin
        // that "da" was not a configured language on a screen that had just offered
        // it to them.
        for (String lang : languages == null ? new String[0] : languages) {
            PublicationSeriesDescVo desc = new PublicationSeriesDescVo();
            desc.setLang(lang);
            vo.getDescs().add(desc);
            vo.getLanguages().add(lang);
        }
        return vo;
    }

    /**
     * S7. An existing series as the starting point for a new one, unsaved.
     *
     * The reason this is a server endpoint and not a client-side object copy is
     * WHAT IT CLEARS. A copy carries the settings that took an admin an afternoon
     * -- the criteria document, the per-language patterns, the report and its
     * parameters -- and must not carry the four fields that are claims about the
     * ORIGINAL row. Each of them causes a distinct, quiet failure if it travels:
     *
     *  - `seriesId` is unique and is the citation handle. Carried, the save is
     *    refused as taken; worse, an admin who edits it to something plausible has
     *    renamed nothing and created a second series under a name that reads like
     *    the first.
     *  - `legacyTemplateId` is unique too, and it is what says "this row IS that
     *    legacy publication". A second row claiming it makes the import's own
     *    already-imported check ambiguous.
     *  - `importSource` says where the row came from. A hand-made series carrying
     *    it would be undone by an import undo.
     *  - `publicAuthority` decides who serves the public. A copy of a flipped
     *    series would arrive already answering for a publication that has never
     *    published anything.
     *
     * `firstIssueStartsAt` is cleared for the same reason, and it is the one that
     * is easy to argue about: it is not a setting but a fact about when the
     * ORIGINAL series began, and a 2017 date on a series created today would put
     * every gap since then on its first screen. S-4 makes the admin state it.
     *
     * DRAFT, always -- ACTIVE is what puts a series in the picker, and activation
     * validates. Nothing is persisted; the copy exists only in the response.
     */
    @GET
    @Path("/copy-series-template/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public SystemPublicationSeriesVo copySeriesTemplate(@PathParam("seriesId") String seriesId) {
        return copyOf(required(seriesId).toVo(SystemPublicationSeriesVo.class));
    }

    /**
     * The copy, without the container.
     *
     * Package-private and static for the same reason newSeriesTemplate is: what
     * the template CONTAINS is the whole behaviour, and asserting it must not
     * require a server.
     */
    static SystemPublicationSeriesVo copyOf(SystemPublicationSeriesVo source) {
        source.setSeriesId(null);
        source.setStatus(SeriesStatus.DRAFT.name());
        source.setLegacyTemplateId(null);
        source.setImportSource(null);
        source.setPublicAuthority(PublicAuthority.LEGACY.name());
        source.setFirstIssueStartsAt(null);

        // Timestamps of the row this was copied FROM. Left in place they would
        // date a series that does not exist yet.
        source.setCreated(null);
        source.setUpdated(null);

        // Zero rather than absent: the copy has released nothing, and the settings
        // form reads this to decide whether the citation channel is still editable
        // (S-18). Absent would leave that control disabled on a brand-new series.
        source.setPublishedIssueCount(0);
        return source;
    }

    /**
     * S13. What a criteria document WOULD select, without saving it.
     *
     * The editing surface for criteria is unusable without it. A criteria document
     * decides what goes into a publication, and the only way to know whether the
     * one being typed is right is to run it -- against real messages, over a real
     * interval. The alternative is saving a guess onto a series and finding out
     * when an issue publishes.
     *
     * PERSISTS NOTHING. It resolves and returns; no issue, no member rows, no
     * override. That is what makes it safe to call on every keystroke-ish edit.
     *
     * Capped at 50 by the contract. The number is not a page -- it is a sample. A
     * probe that returned nine hundred rows would be answering a question nobody
     * asked, and `total` carries the real answer.
     */
    @POST
    @Path("/resolve-preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> resolvePreview(ResolvePreviewRequest request) {
        if (request == null || request.criteria() == null) {
            throw new IssueLifecycleService.TransitionRefusedException("CRITERIA_INVALID",
                    "a criteria document is required to preview what it would select");
        }
        if (request.cutoff() == null) {
            throw new IssueLifecycleService.TransitionRefusedException("CRITERIA_INVALID",
                    "a cut-off is required: a criteria document selects over an interval, and "
                            + "\"everything ever\" is not the question the editor is asking");
        }

        // Parsed with a coded refusal rather than valueOf: an unknown token is a
        // client error, and an IllegalArgumentException out of a probe fired on
        // every edit answers 500 to the editor with nothing to act on.
        TimeRelation relation = request.timeRelation() == null
                ? TimeRelation.PUBLISHED_IN_INTERVAL
                : parseEnum(TimeRelation.class, request.timeRelation(), "timeRelation");

        // An in-force probe has no lower bound, exactly as a real in-force issue has
        // none -- passing one would preview a narrower set than the series produces.
        Date from = relation == TimeRelation.IN_FORCE_AT_CUTOFF || request.intervalFrom() == null
                ? null : new Date(request.intervalFrom());
        Date cutoff = new Date(request.cutoff());

        MemberResolutionService.Resolution resolution;
        try {
            ResolvedCriteria resolved = CriteriaResolver.resolve(request.criteria(), relation,
                    Boolean.TRUE.equals(request.aliveAtCutoff()), CriteriaResolver.NO_DOMAINS);
            resolution = memberResolver.resolve(resolved, new Interval(from, cutoff));
        } catch (IllegalArgumentException e) {
            // A malformed document or an impossible interval is the caller's, and
            // saying which beats a 500 the editor cannot act on.
            throw new IssueLifecycleService.TransitionRefusedException("CRITERIA_INVALID",
                    e.getMessage());
        }

        List<String> sample = resolution.members().stream().limit(PROBE_SAMPLE).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "PROBE");
        out.put("total", resolution.members().size());
        out.put("candidates", resolution.candidateCount());
        out.put("sample", sample);
        out.put("truncated", resolution.members().size() > sample.size());
        out.put("warnings", resolution.warnings());
        // The misses are a SAMPLE too, and the count travels beside them.
        //
        // Every candidate the predicate rejected produces one of these, so a wide
        // criteria document over a live corpus yields thousands of nested objects
        // -- serialised on a path the editor fires while somebody is still typing,
        // to answer a question nobody asked. Fifty is enough to see the shape of
        // what is being dropped; missCount is what says how much there is.
        out.put("missCount", resolution.misses().size());
        out.put("misses", resolution.misses().stream().limit(PROBE_SAMPLE).toList());
        return out;
    }

    /** What a probe needs: an unsaved document and the interval to run it over. */
    public record ResolvePreviewRequest(IssueCriteriaVo criteria, Long intervalFrom, Long cutoff,
                                        String timeRelation, Boolean aliveAtCutoff) {
    }

    /** The contract's probe cap: a sample, not a page. */
    private static final int PROBE_SAMPLE = 50;

    /**
     * An enum from a client token, or a catalogued 400.
     *
     * Enum.valueOf on request data is the pattern the error catalogue exists to
     * stop: it raises an IllegalArgumentException, nothing maps it, and a
     * mistyped filter comes back as "the server broke" with a stack trace in the
     * log for something the caller did.
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String token, String field) {
        try {
            return Enum.valueOf(type, token.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_FILTER_VALUE",
                    "'" + token + "' is not a value of " + field);
        }
    }

    /**
     * S9. Update a series.
     *
     * The settings screen has nowhere to save to without this, so every
     * configuration an imported series needs before it can be reviewed and
     * activated -- criteria included -- was unreachable.
     *
     * THREE GUARDS, and they are not the same guard.
     *
     * S-16: seriesId is the import/export key and the citation handle. Changing it
     * renames the thing every stored reference points at, so the path id wins and a
     * body disagreeing with it is refused rather than silently ignored -- a caller
     * that thinks it renamed a series and did not is worse off than one that got an
     * error.
     *
     * S-18: messagePublication cannot move once an issue has published, because a
     * citation lives in whichever field was configured at the time. Changing the
     * channel makes every existing citation unfindable and re-applying appends a
     * duplicate rather than replacing.
     *
     * S-17: an ACTIVE series may not be edited INTO incompleteness. Activation
     * already validates; without the same check here an admin could activate a
     * valid series and then remove the criteria it was activated for, which is the
     * same end state by a longer route.
     */
    @PUT
    @Path("/series/{seriesId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public SystemPublicationSeriesVo update(@PathParam("seriesId") String seriesId,
                                           SystemPublicationSeriesVo vo) {
        if (vo == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "a series body is required");
        }
        if (vo.getSeriesId() != null && !vo.getSeriesId().isBlank()
                && !seriesId.equals(vo.getSeriesId())) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_ID_IMMUTABLE",
                    "seriesId is the import/export key and the citation handle; it cannot be changed "
                            + "after create. The path says '" + seriesId + "' and the body says '"
                            + vo.getSeriesId() + "'");
        }

        PublicationSeries series = required(seriesId);

        // Captured BEFORE the update, because updateFromVo overwrites the entity in
        // place and there is nothing left to compare against afterwards.
        MessagePublication publicationBefore = series.getMessagePublication();
        boolean anyPublished = seriesService.hasPublishedIssue(series);

        // Neither the status nor the public authority is taken from the body.
        // Each has its own endpoint that validates the transition and audits it,
        // and a save carrying either would route around both -- an admin renaming
        // a series would be able to flip what the public reads, and nothing in
        // the trail would say a cutover had happened.
        SeriesStatus status = series.getStatus();
        PublicAuthority authority = series.getPublicAuthority();
        series.updateFromVo(vo);
        series.setSeriesId(seriesId);
        series.setStatus(status);
        series.setPublicAuthority(authority);
        resolveReferences(series, vo);

        if (anyPublished && publicationBefore != series.getMessagePublication()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "S-18: changing the citation channel after an issue has published makes every "
                            + "existing citation unfindable -- it lives in the other field -- and "
                            + "re-applying appends a duplicate rather than replacing");
        }

        refuseHardRules(series);
        List<SeriesValidator.FieldError> errors =
                SeriesValidator.validateForActivation(series, null);
        if (status == SeriesStatus.ACTIVE && !errors.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    errors.size() + " rule(s) fail: " + errors, errors);
        }

        return seriesService.update(series).toVo(SystemPublicationSeriesVo.class);
    }

    /**
     * S10. Status transition, validated and audited.
     *
     * DRAFT is where a series is assembled, and it is reachable only before the
     * series has ever been active. Going back to it afterwards would put a
     * publication that has issues, citations and readers into the state whose
     * whole meaning is "not finished yet", and every rule that guards an ACTIVE
     * series -- the domain its cut-offs are read in, the report it renders --
     * would stop applying to something the public is still reading.
     *
     * Leaving ACTIVE asks for a reason: it changes what editors can cite and
     * what the site lists, and "who turned this off, and why" is the question
     * asked months later when somebody notices. Entering it -- the first
     * activation, or a reinstatement -- is confirmed and audited, but restores a
     * state the series was already in, and asks for none.
     */
    @PUT
    @Path("/series/{seriesId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public SystemPublicationSeriesVo setStatus(@PathParam("seriesId") String seriesId,
                                               @QueryParam("reason") String reason,
                                               String status) {
        PublicationSeries series = required(seriesId);
        // Null-safe: an empty body reaches here as null, and seriesStatusOf
        // answers that with a coded 400 rather than a NullPointerException the
        // caller reads as a server failure.
        SeriesStatus target = seriesStatusOf(status == null ? null : status.replace("\"", "").trim());
        return seriesService.transition(series, target, reason, userService.currentUser())
                .toVo(SystemPublicationSeriesVo.class);
    }

    static final int MIN_REASON = IssueLifecycleService.MIN_REASON;
    static final int MAX_REASON = IssueLifecycleService.MAX_REASON;

    /**
     * A series status by either vocabulary.
     *
     * The two frontends still in service speak different words for the same
     * three states, and an unknown one is a client error rather than a server
     * failure -- SeriesStatus.valueOf raised IllegalArgumentException, which
     * reaches the caller as a 500 that says nothing.
     */
    static SeriesStatus seriesStatusOf(String token) {
        if (token == null || token.isBlank()) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_STATUS",
                    "a status is required");
        }
        return switch (token.trim().toUpperCase()) {
            case "DRAFT", "RECORDING" -> SeriesStatus.DRAFT;
            case "ACTIVE" -> SeriesStatus.ACTIVE;
            case "RETIRED", "INACTIVE" -> SeriesStatus.RETIRED;
            default -> throw new IssueLifecycleService.TransitionRefusedException("INVALID_STATUS",
                    "'" + token + "' is not a publication series status; the states are DRAFT, ACTIVE "
                            + "and RETIRED");
        };
    }

    /**
     * Which model serves this series to the public.
     *
     * The single irreversible-feeling step of the cutover, and the reason it is
     * its own endpoint rather than a field on a save: flipping authority changes
     * what every anonymous reader sees, and it must not be reachable by an admin
     * editing a name. Both directions are audited, and flipping BACK is a
     * first-class action -- a rollback nobody has rehearsed is not a rollback.
     *
     * The precondition is the shadow diff's own answer: two consecutive green
     * comparisons by release order, or a series that cannot be compared at all
     * and is exempt by rule. `force` exists because a precondition that cannot be
     * overridden gets worked around in the database instead, where nothing is
     * recorded -- so it is allowed, it demands a reason, and the audit entry says
     * it was forced.
     */
    @PUT
    @Path("/series/{seriesId}/public-authority")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public SystemPublicationSeriesVo setPublicAuthority(@PathParam("seriesId") String seriesId,
                                                        Map<String, Object> body) {
        return flip(required(seriesId), authorityOf(body), body)
                .toVo(SystemPublicationSeriesVo.class);
    }

    /**
     * The whole estate at once, all or nothing.
     *
     * The window flips every series together, so the request that does it is one
     * transaction: a partial flip leaves editors working in two systems for the
     * series that did not make it, and discovering which those are means reading
     * the database. One refusal refuses the lot.
     *
     * THE TWO DIRECTIONS DO NOT HAVE THE SAME UNNAMED TARGET SET, and that
     * asymmetry is the point. Going to NEW is an editorial step taken on series
     * the estate is actually running, so it takes the ACTIVE ones. Coming back to
     * LEGACY is a rollback, and a rollback has to reach everything the flip
     * reached: the public adapter chooses its half by this column alone and never
     * looks at the series status, so a series retired after a cutover is still
     * being served from the new model. Leaving those behind would strand exactly
     * the rows nobody is watching.
     */
    @PUT
    @Path("/public-authority")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public List<SystemPublicationSeriesVo> setPublicAuthorityForAll(Map<String, Object> body) {
        PublicAuthority target = authorityOf(body);
        List<PublicationSeries> targets = new ArrayList<>();
        Object named = body == null ? null : body.get("seriesIds");
        if (named instanceof List<?> ids && !ids.isEmpty()) {
            for (Object id : ids) {
                targets.add(required(String.valueOf(id)));
            }
        } else if (target == PublicAuthority.LEGACY) {
            targets.addAll(seriesService.findByPublicAuthority(PublicAuthority.NEW));
        } else {
            targets.addAll(seriesService.findByStatus(SeriesStatus.ACTIVE));
        }

        List<SystemPublicationSeriesVo> out = new ArrayList<>();
        for (PublicationSeries series : targets) {
            out.add(flip(series, target, body).toVo(SystemPublicationSeriesVo.class));
        }
        return out;
    }

    /**
     * The requested authority, or a coded refusal.
     *
     * valueOf on client input is the pattern the error catalogue exists to stop:
     * an unknown token is a client error, and letting it out as an
     * IllegalArgumentException made it a 500 that says nothing.
     */
    private static PublicAuthority authorityOf(Map<String, Object> body) {
        String token = String.valueOf(body == null ? "" : body.getOrDefault("authority", ""))
                .trim().toUpperCase();
        try {
            return PublicAuthority.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_AUTHORITY",
                    "'" + token + "' is not a public authority; it is NEW or LEGACY");
        }
    }

    private PublicationSeries flip(PublicationSeries series, PublicAuthority target,
                                   Map<String, Object> body) {
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
        return seriesService.setPublicAuthority(series, target, force, reason, userService.currentUser());
    }

    /**
     * S12. The issue that does not exist yet.
     *
     * ONE endpoint for three screens -- "＋ Ny udgave", the retro-create prefill,
     * and the live preview on a gap row -- because all three ask the same
     * question. Built separately, the interval one screen prefills and the
     * interval another one displays are two derivations of one bound, and the day
     * they disagree an admin creates a week that overlaps the one before it and
     * finds out at publish.
     *
     * Three ways to say which interval, and they are not alternatives so much as
     * three amounts of knowledge the caller has:
     *
     *  - `afterPublicId` -- chain off that issue's close. This is "the next one
     *    after this", and it is the only form that needs no dates at all.
     *  - `intervalFrom` / `intervalTo` -- the caller already has the period: the
     *    gap row hands its own bounds back to ask what would land in them.
     *  - neither -- the newest issue plus one cadence period, and at the head of
     *    the chain the series' own declared first start.
     *
     * WRITES NOTHING, which is what makes it safe to call while somebody drags a
     * date around. A GET for the same reason: it is a question, it is idempotent,
     * and it is bookmarkable from the row that asked it.
     */
    @GET
    @Path("/series/{seriesId}/issue-draft")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public IssueDraftVo issueDraft(@PathParam("seriesId") String seriesId,
                                   @QueryParam("afterPublicId") String afterPublicId,
                                   @QueryParam("intervalFrom") Long intervalFrom,
                                   @QueryParam("intervalTo") Long intervalTo) {
        return drafts.draft(required(seriesId), afterPublicId,
                intervalFrom == null ? null : new Date(intervalFrom),
                intervalTo == null ? null : new Date(intervalTo),
                new Date());
    }

    /** S11. Delete, guarded on having no issues. */
    @DELETE
    @Path("/series/{seriesId}")
    @RolesAllowed(Roles.ADMIN)
    public void delete(@PathParam("seriesId") String seriesId) {
        lifecycle.deleteSeries(required(seriesId));
    }

    // ------------------------------------------------------------------ tools

    /**
     * S14. The token vocabulary.
     *
     * Served from the constant the expander itself uses. A menu built from a
     * second list is a second source of truth, and the drift shows up as a token
     * the UI offers and the backend rejects.
     *
     * Editor tier: it is a fixed vocabulary of placeholder names and discloses
     * nothing about any series, while the surfaces that render a pattern hint sit
     * below the admin screens.
     */
    @GET
    @Path("/name-tokens")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.USER)
    public List<String> nameTokens() {
        return new ArrayList<>(IssueNaming.TOKENS);
    }

    /**
     * S15. Validate without saving, so the form can show its errors.
     *
     * THE BODY IS THE SUBJECT. This used to load the STORED series by id and
     * validate that, which answers a question nobody asked: the form already has
     * the saved state and is asking about the edit in front of it. Worse, a series
     * that did not exist yet returned an EMPTY list -- "no problems" for a create
     * form whose series could not be created at all.
     *
     * Validated on a transient entity, never persisted and never attached. The
     * validator reads no entity references (category and domain are resolved from
     * ids elsewhere and it looks at neither), so nothing here needs a database.
     */
    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public List<Map<String, String>> validate(SystemPublicationSeriesVo vo) {
        String[] languages = app.getLanguages();
        return validationReport(vo,
                languages == null ? Set.of() : new LinkedHashSet<>(List.of(languages)));
    }

    /**
     * The endpoint's body, without the container.
     *
     * Package-private and static so the wiring itself is testable -- the bug this
     * replaced was not in the validator but in what got handed to it, and a test of
     * SeriesValidator would have stayed green throughout.
     */
    static List<Map<String, String>> validationReport(SystemPublicationSeriesVo vo,
                                                      Set<String> installationLanguages) {
        List<Map<String, String>> out = new ArrayList<>();
        if (vo == null) {
            return out;
        }
        PublicationSeries candidate = new PublicationSeries();
        candidate.updateFromVo(vo);

        // Category and domain are ENTITIES on the series and IDS on the wire, and
        // updateFromVo does not bridge them -- resolveReferences does, and it needs a
        // persistence context this has no business holding.
        //
        // So a named reference stands in as a placeholder. S-19 and S-20 ask whether
        // one was NAMED; whether it EXISTS is create and update's question, and they
        // answer it by refusing an id that resolves to nothing. Without this the rules
        // fire on every series that has a perfectly good category or domain, "Check
        // rules" can never come back clean, and activation can never be offered.
        //
        // BOTH, deliberately. This was fixed for the category alone when S-19 landed,
        // and S-20 then arrived and reproduced the identical bug on the domain --
        // because the first fix patched the instance instead of the shape. Any future
        // rule reading an id-backed reference belongs in this block.
        if (vo.getCategoryId() != null && !vo.getCategoryId().isBlank()) {
            candidate.setCategory(new PublicationCategory());
        }
        if (vo.getDomainId() != null && !vo.getDomainId().isBlank()) {
            candidate.setDomain(new Domain());
        }
        for (SeriesValidator.FieldError e
                : SeriesValidator.validateForActivation(candidate, installationLanguages)) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("rule", e.rule());
            row.put("field", e.field());
            row.put("message", e.message());
            out.add(row);
        }
        return out;
    }

    /**
     * S18. The dry run of the legacy import.
     *
     * Reads and translates the whole legacy estate, reports everything wrong with
     * it, and writes NOTHING -- not written-and-rolled-back, but never opened for
     * write at all. That is what lets it promise the database is byte-identical
     * afterwards.
     *
     * Returns 200 with the report whether or not the import would succeed, because
     * the report IS the answer: an admin running a dry run is asking what would
     * happen, and a 422 with a body they have to dig out of an error handler is a
     * worse way to tell them.
     */
    // NOT_SUPPORTED: this operation opens its OWN transaction with a budget sized
    // for the whole estate. The class-level @Transactional otherwise wrapped it in
    // a second, ambient transaction on the DEFAULT timeout -- which the reaper
    // aborted long before the work finished, so a completed import returned 500
    // with "the transaction is not active" while its rows sat committed in the
    // database. An operator reading that would re-run a cutover that had worked.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    @POST
    @Path("/import-legacy/validate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public LegacyImportReportVo importDryRun() {
        return importService.dryRun();
    }

    /**
     * S19. The run.
     *
     * Refuses with 422 and the FULL report if anything is wrong -- every offender,
     * not the first. An admin fixing them one build at a time is exactly the
     * failure mode a full report exists to prevent, and a half-imported archive is
     * worse than none: the rows that landed look correct and nothing marks them as
     * partial.
     */
    // NOT_SUPPORTED: this operation opens its OWN transaction with a budget sized
    // for the whole estate. The class-level @Transactional otherwise wrapped it in
    // a second, ambient transaction on the DEFAULT timeout -- which the reaper
    // aborted long before the work finished, so a completed import returned 500
    // with "the transaction is not active" while its rows sat committed in the
    // database. An operator reading that would re-run a cutover that had worked.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    @POST
    @Path("/import-legacy")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Response importLegacy() {
        LegacyImportReportVo report = importService.run();
        return report.isWouldSucceed()
                ? Response.ok(report).build()
                : Response.status(422).entity(report).build();
    }

    /**
     * S20. Undo the import.
     *
     * The importer refuses rather than merging, so a second attempt is blocked
     * until the first one is cleared. Exposing this is not a convenience: the
     * alternative is hand-written DELETE statements against a live archive during
     * a cutover window, which is the worst possible time to be composing SQL.
     *
     * DELETE rather than POST because that is what it is, and the method itself
     * should warn the reader.
     *
     * Returns 409 with every reason when it refuses -- an imported series that is
     * no longer DRAFT, or one whose publicAuthority has been flipped. After B7.1
     * those rows ARE the public list and undoing would withdraw published
     * editions from under their readers.
     */
    // NOT_SUPPORTED: this operation opens its OWN transaction with a budget sized
    // for the whole estate. The class-level @Transactional otherwise wrapped it in
    // a second, ambient transaction on the DEFAULT timeout -- which the reaper
    // aborted long before the work finished, so a completed import returned 500
    // with "the transaction is not active" while its rows sat committed in the
    // database. An operator reading that would re-run a cutover that had worked.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    @DELETE
    @Path("/import-legacy")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Response undoImport() {
        LegacyImportService.UndoReport report = importService.undo();
        return report.deleted()
                ? Response.ok(report).build()
                : Response.status(409).entity(report).build();
    }

    /**
     * B6.3. The diagnostic report, as markdown.
     *
     * Markdown rather than JSON because the decision it supports is made by
     * people reading it and arguing about it. The same numbers are available
     * structurally from /shadow-diff; this is the version somebody pastes into
     * a meeting, and it says in words what a table of counts does not: that a
     * skipped week is not a green one, and what was never examined.
     *
     * historical=true also runs B6.1's full replay. Off by default because it
     * re-resolves every imported issue, which is minutes of work and not
     * something to trigger on a page refresh.
     */
    @GET
    @Path("/diagnostic-report")
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed(Roles.ADMIN)
    public String diagnosticReport(@QueryParam("historical") boolean historical) {
        return diagnostics.render(historical);
    }

    /**
     * Runs the shadow diff now, rather than waiting for the hourly tick.
     *
     * The scheduler is the normal path. This exists because the evidence is read
     * during a cutover window, where waiting up to an hour to find out whether the
     * last import produced comparable rows is the wrong shape of feedback -- and
     * because a re-import invalidates every skip, so somebody will want the answer
     * immediately after one.
     *
     * Idempotent: it compares only what has no comparison at its current stamp.
     * Running it twice writes nothing the second time.
     *
     * BOUNDED per call, and it reports what is left. A full sweep of the estate
     * is ~1,000 real member resolutions and does not fit in one request, so the
     * honest interface is a batch plus a remaining count the caller loops on --
     * rather than one request that appears to work and times out at 240 seconds
     * with everything rolled back.
     */
    @POST
    @Path("/shadow-diff/run")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> runShadowDiff(@QueryParam("max") Integer max) {
        int written = shadowDiff.runOnce(
                max == null ? ShadowDiffService.DEFAULT_BATCH : max);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("written", written);
        out.put("remaining", shadowDiff.remaining());
        return out;
    }

    /**
     * Discards every stored comparison so the sweep recomputes them.
     *
     * For when the diff LOGIC changed: a run is keyed on the legacy inputs, so a
     * stale verdict is never reselected on its own. Separate from the sweep
     * because it is a different act -- this one throws away the green-week
     * evidence the cutover decision rests on, and that should never be a side
     * effect of asking for a sweep.
     */
    @POST
    @Path("/shadow-diff/reset")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> resetShadowDiff() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("discarded", shadowDiff.reset());
        return out;
    }

    /**
     * The shadow-diff results, per series and per release.
     *
     * The artefact the cutover decision is made from: the flip waits on two
     * consecutive green comparisons per series, and this is what evidences them.
     *
     * Deltas are keyed on uid rather than short id: a short id is display text
     * and is reused across years, so a delta keyed on it would collide between
     * an NM from 2018 and one from 2024 and read as agreement.
     *
     * Read-only. A shadow diff that could change what it measures would not be a
     * measurement, and neither would an endpoint that could.
     */
    @GET
    @Path("/shadow-diff")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> shadowDiff(@QueryParam("seriesId") String seriesId) {
        List<ShadowDiffRun> runs = seriesId == null || seriesId.isBlank()
                ? shadowDiff.all()
                : shadowDiff.forSeries(seriesId);

        Map<String, List<Map<String, Object>>> bySeries = new LinkedHashMap<>();
        Map<String, List<ShadowDiffRun>> runsBySeries = new LinkedHashMap<>();
        for (ShadowDiffRun run : runs) {   // newest release first, by the named query
            String key = run.getSeriesId() == null ? "(unmapped)" : run.getSeriesId();
            bySeries.computeIfAbsent(key, k -> new ArrayList<>()).add(describe(run));
            runsBySeries.computeIfAbsent(key, k -> new ArrayList<>()).add(run);
        }

        // ONE readiness rule, shared with the diagnostic report.
        Map<String, Object> readiness = new LinkedHashMap<>();
        runsBySeries.forEach((series, seriesRuns) -> {
            org.niord.core.publication.series.replay.ShadowDiffService.Readiness r =
                    org.niord.core.publication.series.replay.ShadowDiffService.readinessOf(seriesRuns);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("consecutiveGreen", r.consecutiveGreen());
            row.put("runs", r.runs());
            row.put("skipped", r.skipped());
            row.put("exempt", r.exempt());
            row.put("meetsCutoverPrecondition", r.ready());
            readiness.put(series, row);
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", runs.size());
        out.put("series", bySeries);
        out.put("readiness", readiness);
        return out;
    }

    private static Map<String, Object> describe(ShadowDiffRun run) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("legacyPublicationId", run.getLegacyPublicationId());
        out.put("comparedAt", run.getComparedAt());
        out.put("intervalFrom", run.getIntervalFrom());
        out.put("cutoffAt", run.getCutoffAt());
        out.put("green", run.isGreen());
        out.put("skipReason", run.getSkipReason());
        out.put("missing", run.missing());
        out.put("extra", run.extra());
        return out;
    }

    /**
     * B5.7. The cutover pre-flight, and the mailing-list trigger audit.
     *
     * Read-only, and safe to run as often as you like. Exposed because the pass
     * was previously reachable only from a test -- which meant the one person who
     * has to act on the trigger audit before B7.1 had no way to see it.
     *
     * Returns 200 with the report either way: an admin running a pre-flight is
     * asking what the state IS, and a non-2xx would bury the answer in an error
     * handler. Read "clear": false means do not flip publicAuthority yet.
     *
     * `series` is the per-series sheet -- the shadow diff's verdict, how many
     * periods the archive leaves uncovered, and what kind of publication it is.
     * NONE of it moves `clear`: a gap is a fact about an archive that predates
     * this system, and readiness is the flip's own precondition, refused at the
     * flip. Folding either in would stop the pre-flight ever passing on an estate
     * that is in exactly the state everybody expects.
     */
    @GET
    @Path("/cutover-preflight")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> cutoverPreflight() {
        CutoverPreflightService.Preflight result = preflight.run();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clear", result.isClear());
        out.put("counts", result.counts());
        out.put("violations", result.violations());
        out.put("triggerAudit", result.triggerAudit());
        out.put("series", result.series());
        return out;
    }

    /**
     * Exports every series as the JSON the importer reads back.
     *
     * The same shape as /search-details, and that is the point: one format, so a
     * round trip cannot lose a field only one side knows about.
     *
     * @RolesAllowed, NOT the @PermitAll-plus-programmatic-check the older admin
     * exports use. That pattern exists because a ticketed download opens in a new
     * tab carrying no bearer token, and a ticket does not produce a security
     * identity -- UserService.isCallerInRole reads it from a thread-local -- so a
     * declarative annotation refuses the request before the ticket is ever
     * consulted.
     *
     * This export does not need it. It carries the criteria documents and the
     * cutover switch, so the client fetches it with its own credentials and saves
     * the response, and no ticket is involved. Going the other way would put a
     * @PermitAll endpoint returning a SYSTEM shape into the API -- exactly what
     * PublicationApiContractTest exists to prevent, and the in-code guard is
     * invisible to it, so deleting that one line later would leave nothing at all
     * flagging an anonymous system-shape endpoint.
     */
    @GET
    @Path("/export")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @RolesAllowed(Roles.ADMIN)
    @NoCache
    public List<SystemPublicationSeriesVo> exportSeries() {
        // The same grouped count the admin list carries, so a round trip through
        // the file does not lose a field one side knows about. It is read-only on
        // the way back in -- the importer writes series, not issues -- and is here
        // because an export that omitted it would not be the same shape as
        // /search-details, which is the one property this format has.
        Map<String, Integer> released = seriesService.publishedIssueCounts();
        List<SystemPublicationSeriesVo> out = new ArrayList<>();
        for (PublicationSeries s : seriesService.findAll()) {
            SystemPublicationSeriesVo vo = s.toVo(SystemPublicationSeriesVo.class);
            vo.setPublishedIssueCount(
                    PublicationSeriesService.publishedIssueCountOf(released, s.getSeriesId()));
            out.add(vo);
        }
        return out;
    }

    /**
     * Imports series from an uploaded JSON file, as a background batch job.
     *
     * The file format is whatever /search-details emits, unchanged. There is no
     * export endpoint for the same reason: the admin list IS the export, so there
     * are not two formats that can drift apart, and a round trip cannot lose a
     * field only one side knows about.
     *
     * Upsert by seriesId, and never ACTIVE -- an imported series arrives DRAFT
     * and an existing one keeps the status it had. Activation validates against
     * S-1..S-20, and a file that could set it would route around every one of
     * them, which is precisely what a file written against another installation's
     * categories, domains and reports would do.
     *
     * The same batch-job shape as every other admin import in the system, so it
     * appears in the Batch Jobs screen and its log says which rows were dropped.
     *
     * ADMIN, matching the export beside it and the category import. An admin who
     * can produce the file and can create every series in it by hand gains
     * nothing from being refused the upload, and the admin page that offers the
     * control is reachable by admin -- so a sysadmin gate showed a button that
     * answered 403.
     */
    @POST
    @Path("/upload-series")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.ADMIN)
    public String importSeries(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "publication-series-import");
    }

    private PublicationSeries required(String seriesId) {
        PublicationSeries series = seriesService.findBySeriesId(seriesId);
        if (series == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_FOUND",
                    "no series with id " + seriesId);
        }
        return series;
    }
}
