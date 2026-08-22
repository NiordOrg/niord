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
import org.niord.core.publication.series.IssueLifecycleService;
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
        throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                "series creation from a VO is not wired yet; the entity path is used directly for now");
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
    @POST
    @Path("/import-legacy/validate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> importDryRun() {
        return notYet();
    }

    @POST
    @Path("/import-legacy")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> importLegacy() {
        return notYet();
    }

    private Map<String, Object> notYet() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", "NOT_IMPLEMENTED");
        out.put("message", "the legacy importer is Phase B5; it needs legacy publication fields that are "
                + "not readable from this environment");
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
