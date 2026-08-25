package org.niord.web.publication;

import jakarta.annotation.security.PermitAll;
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
import org.niord.core.publication.series.IssueLifecycleService;
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
 * So: /search returns the public shape and is @PermitAll; /search-details
 * returns the system shape and requires a role. Two endpoints, two types, no
 * runtime decision.
 */
@Path("/publication-series")
@RequestScoped
@Transactional
@SuppressWarnings("unused")
public class PublicationSeriesRestService {

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

    // ------------------------------------------------------------------ reads

    /** S1. The public list. */
    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<PublicationSeriesVo> search(@QueryParam("lang") String lang) {
        List<PublicationSeriesVo> out = new ArrayList<>();
        for (PublicationSeries s : seriesService.findAll()) {
            out.add(s.toVo(PublicationSeriesVo.class));
        }
        return out;
    }

    /** S2. The editor list, with everything the public shape omits. */
    @GET
    @Path("/search-details")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<SystemPublicationSeriesVo> searchDetails(@QueryParam("status") String status) {
        List<PublicationSeries> found = status == null
                ? seriesService.findAll()
                : seriesService.findByStatus(SeriesStatus.valueOf(status));

        List<SystemPublicationSeriesVo> out = new ArrayList<>();
        for (PublicationSeries s : found) {
            out.add(s.toVo(SystemPublicationSeriesVo.class));
        }
        return out;
    }

    /** S3. One series, public shape. */
    @GET
    @Path("/series/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public PublicationSeriesVo get(@PathParam("seriesId") String seriesId) {
        return required(seriesId).toVo(PublicationSeriesVo.class);
    }

    /** S4. One series, editor shape. */
    @GET
    @Path("/editable-series/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationSeriesVo getEditable(@PathParam("seriesId") String seriesId) {
        return required(seriesId).toVo(SystemPublicationSeriesVo.class);
    }

    /** S5. Several at once, for a picker. */
    @GET
    @Path("/series-by-ids/{seriesIds}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
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
    @RolesAllowed("admin")
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

        return seriesService.create(series).toVo(SystemPublicationSeriesVo.class);
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
        if (vo.getDomainId() != null && !vo.getDomainId().isBlank()) {
            Domain domain = domainService.findByDomainId(vo.getDomainId());
            if (domain == null) {
                throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                        "no domain '" + vo.getDomainId() + "'");
            }
            series.setDomain(domain);
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
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
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

        TimeRelation relation = request.timeRelation() == null
                ? TimeRelation.PUBLISHED_IN_INTERVAL
                : TimeRelation.valueOf(request.timeRelation());

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
        out.put("misses", resolution.misses());
        return out;
    }

    /** What a probe needs: an unsaved document and the interval to run it over. */
    public record ResolvePreviewRequest(IssueCriteriaVo criteria, Long intervalFrom, Long cutoff,
                                        String timeRelation, Boolean aliveAtCutoff) {
    }

    /** The contract's probe cap: a sample, not a page. */
    private static final int PROBE_SAMPLE = 50;

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
    @RolesAllowed("admin")
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

        // The status is NOT taken from the body. S10 owns the transition and
        // validates it; letting a save carry a status would route around that.
        SeriesStatus status = series.getStatus();
        series.updateFromVo(vo);
        series.setSeriesId(seriesId);
        series.setStatus(status);
        resolveReferences(series, vo);

        if (anyPublished && publicationBefore != series.getMessagePublication()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "S-18: changing the citation channel after an issue has published makes every "
                            + "existing citation unfindable -- it lives in the other field -- and "
                            + "re-applying appends a duplicate rather than replacing");
        }

        List<SeriesValidator.FieldError> errors =
                SeriesValidator.validateForActivation(series, null);
        if (status == SeriesStatus.ACTIVE && !errors.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    errors.size() + " rule(s) fail: " + errors);
        }

        return seriesService.update(series).toVo(SystemPublicationSeriesVo.class);
    }

    /** S10. Status transition, validated. */
    @PUT
    @Path("/series/{seriesId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationSeriesVo setStatus(@PathParam("seriesId") String seriesId, String status) {
        PublicationSeries series = required(seriesId);
        SeriesStatus target = SeriesStatus.valueOf(status.replace("\"", "").trim());

        series.setStatus(target);

        // S-17: ACTIVE is what puts a series in the picker, so it may not be
        // incomplete. A DRAFT is allowed to be.
        List<SeriesValidator.FieldError> errors =
                SeriesValidator.validateForActivation(series, null);
        if (target == SeriesStatus.ACTIVE && !errors.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    errors.size() + " rule(s) fail: " + errors);
        }
        return seriesService.update(series).toVo(SystemPublicationSeriesVo.class);
    }

    /** S11. Delete, guarded on having no issues. */
    @DELETE
    @Path("/series/{seriesId}")
    @RolesAllowed("admin")
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
     */
    @GET
    @Path("/name-tokens")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
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
     * S18 and S19. The legacy import.
     *
     * Refused rather than half-implemented. The importer reads legacy fields --
     * messageTagFilter, messageTag, template, periodicalType -- that are not
     * reachable from here, and an import that silently produced approximate
     * series would be far worse than one that declines to run.
     */
    /**
     * S18. The dry run.
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
    @POST
    @Path("/import-legacy/validate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
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
    @POST
    @Path("/import-legacy")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
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
    @DELETE
    @Path("/import-legacy")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
    public String diagnosticReport(@QueryParam("historical") boolean historical) {
        return diagnostics.render(historical);
    }

    /**
     * B6.2/B6.3. The shadow-diff results, per series and per release.
     *
     * The artifact the cutover decision is made from. B7.1 waits on TWO
     * CONSECUTIVE GREEN WEEKS PER SERIES, and this is what evidences them.
     *
     * Deltas are keyed on uid rather than short id: a short id is display text
     * and is reused across years, so a delta keyed on it would collide between
     * an NM from 2018 and one from 2024 and read as agreement.
     *
     * Read-only. A shadow-diff that could change what it measures would not be
     * a measurement, and neither would an endpoint that could.
     */
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
    @RolesAllowed("admin")
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
    @RolesAllowed("admin")
    public Map<String, Object> resetShadowDiff() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("discarded", shadowDiff.reset());
        return out;
    }

    @GET
    @Path("/shadow-diff")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> shadowDiff(@QueryParam("seriesId") String seriesId) {
        List<ShadowDiffRun> runs = seriesId == null || seriesId.isBlank()
                ? shadowDiff.all()
                : shadowDiff.forSeries(seriesId);

        Map<String, List<Map<String, Object>>> bySeries = new LinkedHashMap<>();
        for (ShadowDiffRun run : runs) {
            bySeries.computeIfAbsent(
                            run.getSeriesId() == null ? "(unmapped)" : run.getSeriesId(),
                            k -> new ArrayList<>())
                    .add(describe(run));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", runs.size());
        out.put("series", bySeries);
        out.put("readiness", readiness(bySeries));
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
     * Per series: is it two consecutive green releases clear of cutover?
     *
     * A SKIPPED run breaks the streak rather than extending it. A week nobody
     * could compare is not evidence that the week agreed, and letting it count
     * would let a series reach the precondition without a single comparison.
     */
    private static Map<String, Object> readiness(
            Map<String, List<Map<String, Object>>> bySeries) {

        Map<String, Object> out = new LinkedHashMap<>();
        bySeries.forEach((series, runs) -> {
            int streak = 0;
            for (Map<String, Object> run : runs) {   // newest first
                boolean counts = Boolean.TRUE.equals(run.get("green"))
                        && run.get("skipReason") == null;
                if (!counts) {
                    break;
                }
                streak++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("consecutiveGreen", streak);
            row.put("meetsCutoverPrecondition", streak >= 2);
            out.put(series, row);
        });
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
     */
    @GET
    @Path("/cutover-preflight")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> cutoverPreflight() {
        CutoverPreflightService.Preflight result = preflight.run();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clear", result.isClear());
        out.put("counts", result.counts());
        out.put("violations", result.violations());
        out.put("triggerAudit", result.triggerAudit());
        return out;
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
