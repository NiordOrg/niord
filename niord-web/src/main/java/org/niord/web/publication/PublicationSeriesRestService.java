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
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.resolve.IssueNaming;
import org.niord.core.publication.series.vo.PublicationSeriesVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    LegacyImportService importService;

    @Inject
    CutoverPreflightService preflight;

    @Inject
    ShadowDiffService shadowDiff;

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

    /** S15. Validate without saving, so the form can show its errors. */
    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<Map<String, String>> validate(SystemPublicationSeriesVo vo) {
        PublicationSeries series = seriesService.findBySeriesId(vo.getSeriesId());
        List<Map<String, String>> out = new ArrayList<>();
        if (series == null) {
            return out;
        }
        for (SeriesValidator.FieldError e : SeriesValidator.validate(series, null)) {
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
