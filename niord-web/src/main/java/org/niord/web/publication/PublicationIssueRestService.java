package org.niord.web.publication;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.InputStream;
import org.niord.core.publication.series.IssueAuditEntry;
import org.niord.core.publication.series.IssueAuditService;
import org.niord.core.publication.series.IssueCurationService;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssueListService;
import org.niord.core.publication.series.IssueMemberListService;
import org.niord.core.publication.series.IssuePickerService;
import org.niord.core.publication.series.IssueStatusTokens;
import org.niord.core.publication.series.vo.IssueOverrideVo;
import org.niord.core.publication.series.vo.IssueTimelineVo;
import org.niord.core.publication.series.vo.PublicationIssuePickerVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.publication.PublicationType;
import org.niord.model.search.PagedSearchResultVo;
import org.niord.core.user.Roles;
import org.niord.core.util.WebUtils;
import org.niord.core.user.UserService;
import org.niord.core.publication.series.MessageIssueLookup;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.vo.MessageIssueRefVo;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.PublicationSeriesService;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueEditService;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.IssueFileService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.OverrideKind;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueService;
import org.niord.core.publication.series.PublishChecklistService;
import org.niord.core.publication.series.vo.IssueAuditEntryVo;
import org.niord.core.publication.series.vo.IssueListResultVo;
import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.series.vo.PublicationIssueVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The issues resource.
 *
 * Same tier discipline as the series: the public shape and the editor shape are
 * different types on different endpoints, never one endpoint choosing.
 *
 * The member list is the one place where the response depends on state rather
 * than on the caller: an OPEN issue returns the LIVE resolution, a PUBLISHED one
 * returns the FROZEN rows. That is keyed off status with no request parameter,
 * deliberately -- a parameter would let a caller ask a published issue what it
 * "would" contain now, and that answer looks authoritative while describing a
 * document nobody ever published.
 */
@Path("/publication-issues")
@RequestScoped
@Transactional
@SuppressWarnings("unused")
public class PublicationIssueRestService {

    @Inject
    PublicationIssueService issueService;

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    UserService userService;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    MessageIssueLookup messageIssues;

    @Inject
    IssueFileService fileService;

    @Inject
    IssueEditService editService;

    @Inject
    IssueCurationService curation;

    @Inject
    IssueListService issueList;

    @Inject
    IssueMemberListService memberList;

    @Inject
    IssuePickerService picker;

    @Inject
    IssueAuditService audit;

    @Inject
    PublishChecklistService checklist;

    @Inject
    EntityManager em;

    // ----------------------------------------------------------------- writes

    /**
     * I1. Create an issue on a series.
     *
     * The only route into the issue lifecycle. Everything else here -- publish,
     * retire, reactivate, curate -- operates on an issue that already exists, so
     * without this the whole surface was unreachable: the series and issue
     * endpoints had never been exercised against a running system, and the first
     * thing to touch them would have been the historical importer.
     *
     * Delegates to IssueLifecycleService.create, which mints the publicId and
     * writes the audit entry. This adds no rules of its own -- an endpoint that
     * re-implements the lifecycle is a second lifecycle.
     */
    @POST
    @Path("/issue")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo create(CreateIssueRequest request) {
        if (request == null || request.seriesId() == null || request.seriesId().isBlank()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "seriesId is required to create an issue");
        }

        PublicationSeries series = seriesService.findBySeriesId(request.seriesId());
        if (series == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_FOUND",
                    "no series '" + request.seriesId() + "'");
        }

        // A PUBLISHED_IN_INTERVAL series chains: intervalFrom is the previous
        // issue's stamped cut-off, and the caller supplying it is how a recovered
        // issue is created. IN_FORCE_AT_CUTOFF has no interval at all, so null is
        // the right value there rather than a missing one.
        Date intervalFrom = request.intervalFrom() == null ? null : new Date(request.intervalFrom());
        IntervalBoundSource source = intervalFrom == null ? null
                : (request.recovered() ? IntervalBoundSource.RECOVERED : IntervalBoundSource.STAMPED);
        // The close, where the caller reviewed one. Absent lets the cadence derive
        // the nominal bound, which is what an ordinary "next issue" wants.
        Date intervalTo = request.intervalTo() == null ? null : new Date(request.intervalTo());

        PublicationIssue issue = lifecycle.create(series, intervalFrom, source, intervalTo,
                userService.currentUser());
        em.flush();
        return issue.toVo(SystemPublicationIssueVo.class);
    }

    /**
     * What a create needs, and nothing more.
     *
     * `intervalTo` is optional and is the bound the draft put on the screen. It is
     * here so a reviewed period is created in ONE call: sending only the start and
     * correcting the close afterwards is two writes with a window in between where
     * the issue covers a period nobody chose.
     */
    public record CreateIssueRequest(String seriesId, Long intervalFrom, Long intervalTo,
                                     boolean recovered) {
    }

    // ------------------------------------------------------------------ reads

    /** I3. Public shape. */
    @GET
    @Path("/issue/{publicId}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public PublicationIssueVo get(@PathParam("publicId") String publicId) {
        return required(publicId).toVo(PublicationIssueVo.class);
    }

    /**
     * I5. Every issue of one series, newest first.
     *
     * B1.1 named this and it was never built, which nothing noticed until the
     * series-detail page needed it: an issue is reachable only by its own
     * publicId, so the only way to see a series' archive was to already know
     * every id in it.

     * Newest first because that is the order the question is asked in -- what
     * went out most recently, and is the current one still open. The full
     * history is the tail of that answer, not the head of it.
     *
     * Returns the EDITOR shape. The one caller is the admin section, and the
     * public list is served by the public adapter rather than from here.
     *
     * Wrapped in an envelope rather than returned as a bare array, because the
     * rows alone cannot be read safely: a series with no MISSING rows and a
     * series nobody examined for gaps produce the same array, and every
     * imported series is DRAFT, so today that is all twenty of them.
     *
     * Paging is `page` + `maxSize` and is OPT-IN: unbounded until a caller asks
     * for a page. An archive is a set an admin scans, and one silently truncated
     * at a default is the failure this redesign exists to remove -- a reader
     * sees a complete-looking list and has no way to know it stops at twenty.
     * `limit` is not accepted under any spelling; the parameter is `maxSize`.
     */
    @GET
    @Path("/series/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public IssueListResultVo bySeries(@PathParam("seriesId") String seriesId,
                                      @QueryParam("page") @DefaultValue("0") int page,
                                      @QueryParam("maxSize") Integer maxSize) {
        PublicationSeries series = seriesService.findBySeriesId(seriesId);
        if (series == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_FOUND",
                    "no series '" + seriesId + "'");
        }
        return maxSize == null
                ? issueList.forSeries(series, new Date())
                : issueList.forSeries(series, new Date(), page, maxSize);
    }

    /**
     * I27. The publication picker.
     *
     * What an editor citing a publication is offered, and deliberately the
     * thinnest payload on this resource: no criteria, no report configuration, no
     * repository path, no member count. It duplicates the list endpoint in shape
     * and not in content, which is what lets the admin list grow without changing
     * anything the citation dialog reads.
     *
     * NOT anonymous. Its default status filter reaches OPEN issues, and an
     * unauthenticated caller walking that would have the names and links of every
     * unreleased publication in the estate -- precisely the enumeration the
     * redesign removes. Hydration of an id somebody already holds is a different
     * question with a different tier, and it lives at /by-ids.
     *
     * NOT domain-scoped either, matching the shipped pickers. Most of the
     * catalogue has no domain, and scoping this would empty the citation dialog
     * of exactly those rows. A caller that names a domain still sees them: a
     * series with no domain belongs to every domain.
     */
    @GET
    @Path("/picker")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.USER)
    public PagedSearchResultVo<PublicationIssuePickerVo> pickerSearch(
            @QueryParam("lang") String lang,
            @QueryParam("title") String title,
            @QueryParam("publicationSeriesId") String publicationSeriesId,
            @QueryParam("status") List<String> status,
            @QueryParam("messagePublication") String messagePublication,
            @QueryParam("type") String type,
            @QueryParam("domain") String domain,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("maxSize") @DefaultValue("100") int maxSize) {

        return picker.search(new IssuePickerService.PickerQuery(
                lang,
                title,
                publicationSeriesId,
                // Published and open, not "all three". A picker exists to offer
                // something citable, and a withdrawn publication in the same list
                // as the current week's invites a citation into something nobody
                // may read any more. An explicit status narrows within that.
                IssueStatusTokens.parseAll(status, IssuePickerService.DEFAULT_STATUSES),
                enumOf(MessagePublication.class, messagePublication, "messagePublication"),
                enumOf(PublicationType.class, type, "type"),
                domain,
                page,
                maxSize));
    }

    /**
     * I28. Hydration of ids the caller already holds.
     *
     * Anonymous, and that is not enumeration: nothing here can be discovered,
     * only resolved. A citation chip on the public site has to render the title
     * of what it points at, and it points at ids that are already bytes inside
     * published message HTML.
     *
     * NO status narrowing, for the same reason. A message citing an issue that
     * was later retired must still show what it cited -- narrowing to published
     * would blank precisely the chips that need explaining. Unknown ids are
     * omitted silently and an empty list is 200: one dead citation must not fail
     * the lookup for the four beside it.
     *
     * The ids travel as one comma-separated parameter rather than a path segment.
     * `/issue/{publicIds}` would collide with the single-issue read at
     * `/issue/{publicId}` and which one RESTEasy matched would be undefined.
     */
    @GET
    @Path("/by-ids")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<PublicationIssuePickerVo> byIds(@QueryParam("ids") String ids,
                                                @QueryParam("lang") String lang) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return picker.byIds(List.of(ids.split(",")), lang);
    }

    /**
     * I29. The dashboard's per-series timeline strip, in one request.
     *
     * Built from the issue list this is a request per series -- roughly sixty on
     * the production estate -- and it still could not render a missing period,
     * because gap synthesis needs exactly one series named and a mixed-series page
     * cannot name one. The cells come from the same synthesizer the issue list
     * uses, so a week the list calls missing is a cell the strip shows as missing.
     *
     * Bounded by its arguments rather than paged: at most fifty series by at most
     * fifty-two periods. The series ids are REQUIRED -- this endpoint never scans
     * the estate, because a dashboard that renders what it was told to render
     * cannot accidentally become an enumeration of everything.
     */
    @GET
    @Path("/recent")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.USER)
    public List<IssueTimelineVo> recent(@QueryParam("publicationSeriesId") String publicationSeriesId,
                                        @QueryParam("periods") @DefaultValue("8") int periods,
                                        @QueryParam("lang") String lang) {
        List<String> seriesIds = new ArrayList<>();
        for (String id : publicationSeriesId == null ? new String[0] : publicationSeriesId.split(",")) {
            if (!id.isBlank()) {
                seriesIds.add(id.trim());
            }
        }
        if (seriesIds.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("NO_SERIES_IDS",
                    "at least one publicationSeriesId is required; this endpoint never scans the estate");
        }
        if (seriesIds.size() > MAX_TIMELINE_SERIES) {
            throw new IssueLifecycleService.TransitionRefusedException("TOO_MANY_SERIES_IDS",
                    "at most " + MAX_TIMELINE_SERIES + " series can be asked for in one strip; "
                            + seriesIds.size() + " were named");
        }

        // Clamped rather than refused. periods is a viewport width, not an
        // assertion about the data -- a client asking for more cells than fit
        // wants as many as it can have, and failing the whole dashboard over it
        // would be a worse answer than a shorter strip.
        int wanted = Math.min(Math.max(periods, 1), MAX_TIMELINE_PERIODS);

        Date now = new Date();
        List<IssueTimelineVo> out = new ArrayList<>();
        for (String seriesId : seriesIds) {
            PublicationSeries series = seriesService.findBySeriesId(seriesId);
            if (series == null) {
                // A series that has been deleted since the dashboard was
                // configured leaves an EMPTY group rather than failing the strip:
                // one stale id must not blank the other fifty-nine.
                IssueTimelineVo empty = new IssueTimelineVo();
                empty.setPublicationSeriesId(seriesId);
                out.add(empty);
                continue;
            }
            out.add(issueList.recent(series, wanted, now, lang));
        }
        return out;
    }

    /** One group per series, and the dashboard asks for the ones it renders. */
    static final int MAX_TIMELINE_SERIES = 50;

    /** A year of weekly cells. Beyond that the strip is an archive, and that is the issue list. */
    static final int MAX_TIMELINE_PERIODS = 52;

    /**
     * An enum-valued query parameter, or a refusal naming the parameter.
     *
     * `valueOf` on client input raises IllegalArgumentException, which reaches a
     * caller as a 500 saying nothing -- the exact pattern the error catalogue
     * exists to stop. Blank means "do not narrow" rather than an error, because a
     * form that submits an empty select is asking for everything.
     */
    private static <E extends Enum<E>> E enumOf(Class<E> type, String token, String param) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, token.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_FILTER_VALUE",
                    "'" + token + "' is not a value of " + param);
        }
    }

    /** I4. Editor shape. */
    @GET
    @Path("/editable-issue/{publicId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo getEditable(@PathParam("publicId") String publicId) {
        return required(publicId).toVo(SystemPublicationIssueVo.class);
    }

    /**
     * Every issue one message is in, for the message editor.
     *
     * The inverse of the member list, and the direction an editor actually asks
     * in. "Which issues contain this message" had no answer short of opening
     * every issue in the admin area and reading its members -- a question asked
     * constantly, answerable only by somebody with admin rights, about a message
     * sitting open on the screen.
     *
     * Roles.USER rather than admin. This is a read about a message, shown next to
     * the message, and everyone who may view a message may see where it was
     * published. The VO is scoped to match: no naming patterns, no reference
     * formats, nothing from the series editing surface.
     *
     * Never throws for an unknown uid. A message that is in no issue and a
     * message that does not exist both mean "nothing to show here", and a 404
     * would turn a quiet panel into an error banner on a screen where the
     * publication state is a footnote.
     */
    @GET
    @Path("/by-message/{messageUid}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.USER)
    public List<MessageIssueRefVo> forMessage(@PathParam("messageUid") String messageUid) {
        return group(messageIssues.forMessage(messageUid, new Date()));
    }

    /**
     * The lookup's per-issue rows, collapsed to one row per series per basis.
     *
     * The lookup answers per issue because that is the honest primitive -- a
     * membership is in an issue, not in a series. The screen needs the other
     * shape: an IN_FORCE_AT_CUTOFF series re-lists every message still in force
     * in every edition, so a notice in force for two years is a member of a
     * hundred-odd weekly issues of one series. All hundred say the same thing,
     * and rendered as a hundred rows they bury the single EfS row an editor came
     * to find.
     *
     * The MOST RECENT issue survives as the group's representative, because "has
     * this gone out" is a question about the latest edition. Order is preserved
     * from the lookup, which already puts the frozen facts before the live
     * predictions.
     */
    static List<MessageIssueRefVo> group(List<MessageIssueLookup.MessageIssue> rows) {
        Map<String, MessageIssueRefVo> byGroup = new LinkedHashMap<>();

        for (MessageIssueLookup.MessageIssue row : rows) {
            MessageIssueRefVo vo = refOf(row);
            // An issue whose series did not load groups under its own id rather
            // than merging with every other seriesless issue into one wrong row.
            String key = (vo.getSeriesId() == null ? "issue:" + vo.getIssuePublicId() : vo.getSeriesId())
                    + "/" + vo.getMembership();

            MessageIssueRefVo kept = byGroup.get(key);
            if (kept == null) {
                byGroup.put(key, vo);
            } else if (isNewer(vo, kept)) {
                vo.setIssueCount(kept.getIssueCount() + 1);
                byGroup.put(key, vo);
            } else {
                kept.setIssueCount(kept.getIssueCount() + 1);
            }
        }
        return new ArrayList<>(byGroup.values());
    }

    /**
     * Which of two issues in a group is the later one.
     *
     * By interval start, because that is the field every issue has -- an open one
     * has never been published and a recovered one may carry no publish stamp, so
     * ordering on publishedAt would rank exactly the rows that matter as oldest.
     * A missing interval loses to any real date rather than winning by accident.
     */
    private static boolean isNewer(MessageIssueRefVo candidate, MessageIssueRefVo incumbent) {
        Date a = candidate.getIntervalFrom();
        Date b = incumbent.getIntervalFrom();
        if (a == null) {
            return false;
        }
        return b == null || a.after(b);
    }

    /**
     * One lookup row as the wire shape.
     *
     * Static and entity-in, VO-out so it can be pinned by a plain unit test: the
     * FROZEN/LIVE distinction is the whole point of the payload, and it is one
     * assignment away from being dropped.
     */
    static MessageIssueRefVo refOf(MessageIssueLookup.MessageIssue row) {
        PublicationIssue issue = row.issue();
        MessageIssueRefVo vo = new MessageIssueRefVo();
        // One issue is one issue: a row that never reaches group() still reports a
        // truthful count rather than zero.
        vo.setIssueCount(1);
        vo.setIssuePublicId(issue.getPublicId());
        vo.setStatus(issue.getStatus());
        vo.setMembership(row.membership() == MessageIssueLookup.Membership.FROZEN
                ? MessageIssueRefVo.Membership.FROZEN
                : MessageIssueRefVo.Membership.LIVE);
        vo.setIntervalFrom(issue.getIntervalFrom());
        vo.setIntervalTo(issue.getIntervalTo());
        vo.setPublishedAt(issue.getPublishedAt());

        for (PublicationIssueDesc d : issue.getDescs()) {
            if (d.getLang() == null) {
                continue;
            }
            if (d.getName() != null) {
                vo.getNames().put(d.getLang(), d.getName());
            }
            if (d.getLink() != null) {
                vo.getLinks().put(d.getLang(), d.getLink());
            }
        }

        PublicationSeries series = issue.getSeries();
        if (series != null) {
            vo.setSeriesId(series.getSeriesId());
            for (PublicationSeriesDesc d : series.getDescs()) {
                if (d.getLang() != null && d.getName() != null) {
                    vo.getSeriesNames().put(d.getLang(), d.getName());
                }
            }
        }
        return vo;
    }

    /**
     * I10. The member list.
     *
     * Live while OPEN, frozen once PUBLISHED, and the caller does not get to
     * choose. Asking a published issue what it would contain today produces an
     * authoritative-looking answer about a document that does not exist.
     *
     * On a frozen list each row also carries what has MOVED under it since --
     * which frozen fields no longer match the live message, and what they are now.
     * Surfaced, never healed: the snapshot is the record of what was printed, and
     * a row quietly updated to agree with today would disagree with the PDF that
     * went out with nothing left to say they ever differed.
     *
     * The rules live in the core service. The endpoint is the address, not the
     * behaviour -- the web layer has no container tests, so anything decided here
     * is decided where nothing can pin it.
     */
    @GET
    @Path("/issue/{publicId}/members")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<IssueMemberVo> members(@PathParam("publicId") String publicId) {
        return memberList.members(required(publicId));
    }

    /** I11. The Historik panel. */
    @GET
    @Path("/issue/{publicId}/audit")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<IssueAuditEntryVo> auditTrail(@PathParam("publicId") String publicId) {
        List<IssueAuditEntryVo> out = new ArrayList<>();
        for (IssueAuditEntry e : audit.forIssue(required(publicId))) {
            IssueAuditEntryVo vo = new IssueAuditEntryVo();
            vo.setId(e.getId());
            vo.setAction(e.getAction());
            vo.setActorKind(e.getActorKind() == null ? null : e.getActorKind().name());
            vo.setActorLabel(e.getUser() == null ? e.getActorLabel() : e.getUser().getUsername());
            vo.setCreated(e.getCreated());
            vo.setReason(e.getReason());
            vo.setArchivePath(e.getArchivePath());
            vo.setDetail(e.getDetail());
            out.add(vo);
        }
        return out;
    }

    @jakarta.inject.Inject
    org.niord.core.publication.series.IssuePreviewService previews;

    /**
     * Whether any language's preview predates the current member set -- or is
     * absent -- for a series that renders a document. The issue's own stamp moves
     * on every edit and every curation, so it is what "current" is read against.
     */
    private boolean previewStale(PublicationIssue issue) {
        if (issue.getSeries() == null || issue.getSeries().getReportId() == null) {
            return false;
        }
        for (PublicationIssueDesc desc : issue.getDescs()) {
            if (previews.isStale(issue, desc.getLang(), issue.getUpdated())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate a preview of the open issue as it stands, per language.
     *
     * The same render publish performs, into the preview store rather than the
     * repository. Publishing with regenerate=false afterwards promotes exactly
     * these bytes.
     *
     * NO href IN THE RESPONSE. The document endpoint below is role-guarded and the
     * caller's token lives in memory, so a URL handed over here is only openable by
     * a request that carries the bearer -- which a top-level navigation does not.
     * An address in the payload reads as "put this in a link", and the link 401s.
     * The client composes the address it already knows and fetches it the way it
     * fetches everything else.
     */
    @POST
    @Path("/issue/{publicId}/preview")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public List<Map<String, Object>> generatePreview(@PathParam("publicId") String publicId) {
        PublicationIssue issue = required(publicId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (org.niord.core.publication.series.IssuePreviewService.Preview p : publishService.preview(issue.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lang", p.lang());
            row.put("renderedAt", p.renderedAt().getTime());
            out.add(row);
        }
        return out;
    }

    /** The newest preview of one language, as a document. */
    @GET
    @Path("/issue/{publicId}/preview/{lang}")
    @Produces("application/pdf")
    @RolesAllowed("admin")
    public jakarta.ws.rs.core.Response preview(@PathParam("publicId") String publicId,
                                               @PathParam("lang") String lang) throws Exception {
        PublicationIssue issue = required(publicId);
        org.niord.core.publication.series.IssuePreviewService.Preview p = previews.newest(issue, lang)
                .orElseThrow(() -> new IssueLifecycleService.TransitionRefusedException("NO_PREVIEW",
                        "no preview has been generated for language " + lang));
        byte[] bytes = java.nio.file.Files.readAllBytes(p.path());
        return jakarta.ws.rs.core.Response.ok(bytes)
                .header("Content-Disposition", "inline; filename=\"" + p.path().getFileName() + "\"")
                .header("Cache-Control", "no-store")
                .build();
    }

    /**
     * I15. The release rail.
     *
     * FOR A CUT-OFF, and the caller may name it. Half these rows are answers about
     * one instant -- which issue is the predecessor, whether the stamp falls inside
     * the neighbour bracket, which members the query returns at it -- so a rail
     * computed for NOW while the dialog is offering to publish at a past instant
     * describes a release nobody is about to make. The dialog sends the instant it
     * is showing, and the same instant reaches the publish; absent means now.
     */
    @GET
    @Path("/issue/{publicId}/publish-checklist")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> publishChecklist(@PathParam("publicId") String publicId,
                                                @QueryParam("allowFuture") boolean allowFuture,
                                                @QueryParam("cutoff") Long cutoff) {
        PublicationIssue issue = required(publicId);
        Date proposed = cutoff == null ? new Date() : new Date(cutoff);
        PublishChecklistService.Checklist result =
                checklist.compute(issue, proposed, allowFuture, previewStale(issue));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PublishChecklistService.CheckRow r : result.rows()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", r.code());
            row.put("severity", r.severity().name());
            row.put("passed", r.passed());
            row.put("acknowledgeable", r.acknowledgeable());
            // The warning code the publish gate compares against, said by the row
            // rather than mapped by every client. The rail names a condition and
            // the acknowledgement travels as the resolver's warning code, and the
            // two are deliberately different strings -- a client translating one
            // into the other by hand gets a refusal for a code nobody ticked.
            row.put("acknowledgeCode", r.acknowledgeCode());
            row.put("detail", r.detail());
            rows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("canPublish", result.canPublish());
        out.put("blockingCodes", result.blockingCodes());
        return out;
    }

    // ------------------------------------------------------------------ actions

    /** I16. Publish. */
    @PUT
    @Path("/issue/{publicId}/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> publish(@PathParam("publicId") String publicId,
                                       Map<String, Object> params) {
        PublicationIssue issue = required(publicId);

        @SuppressWarnings("unchecked")
        List<String> acknowledged = params == null ? List.of()
                : (List<String>) params.getOrDefault("acknowledgedWarnings", List.of());
        boolean regenerate = params == null || Boolean.TRUE.equals(params.getOrDefault("regenerate", true));

        // The chosen cut-off, if any: the end of the content period, which the
        // admin may place in the past (a week published late, a gap recovered)
        // and never in the future -- a future cut-off would freeze the list
        // before its window closed. Absent means now. The publication moment is
        // always now and is not the caller's to set.
        Date cutoff = null;
        Object raw = params == null ? null : params.get("cutoff");
        if (raw instanceof Number n) {
            cutoff = new Date(n.longValue());
        } else if (raw instanceof String s && !s.isBlank()) {
            cutoff = new Date(Long.parseLong(s.trim()));
        }
        if (cutoff != null && cutoff.after(new Date())) {
            throw new IssueLifecycleService.TransitionRefusedException("CUTOFF_IN_FUTURE",
                    "a cut-off cannot lie in the future: the content period has not closed yet");
        }

        IssuePublishService.PublishResult result = publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(regenerate, Set.copyOf(acknowledged),
                        userService.currentUser(), cutoff));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publicId", publicId);
        out.put("stampedAt", result.stampedAt().getTime());
        out.put("memberCount", result.memberCount());
        out.put("unacknowledgedWarnings", result.unacknowledgedWarnings());
        out.put("successorId", result.successorId());
        return out;
    }

    /**
     * I17. Amend: the same decision, taken again, at the same address.
     *
     * The cut-off is not a parameter here and cannot be. An amend replaces the
     * document a citation already points at; re-taking the instant its content
     * was decided would quietly turn a correction into a different publication.
     */
    @PUT
    @Path("/issue/{publicId}/amend")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> amend(@PathParam("publicId") String publicId,
                                     Map<String, Object> params) {
        PublicationIssue issue = required(publicId);

        @SuppressWarnings("unchecked")
        List<String> acknowledged = params == null ? List.of()
                : (List<String>) params.getOrDefault("acknowledgedWarnings", List.of());
        boolean regenerate = params == null || Boolean.TRUE.equals(params.getOrDefault("regenerate", true));
        String reason = params == null ? null : String.valueOf(params.getOrDefault("reason", ""));

        IssuePublishService.AmendResult result = publishService.amend(issue.getId(),
                new IssuePublishService.AmendRequest(regenerate, Set.copyOf(acknowledged),
                        userService.currentUser(), reason));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publicId", publicId);
        out.put("stampedAt", result.stampedAt() == null ? null : result.stampedAt().getTime());
        out.put("memberCount", result.memberCount());
        out.put("unacknowledgedWarnings", result.unacknowledgedWarnings());
        out.put("archivePaths", result.archivePaths());
        return out;
    }

    /**
     * The new-edition action, which is what gives supersedes a write path.
     *
     * A mid-year re-issue is not an edit of the edition it replaces: the old one
     * stays exactly as it was published, and the new one is LINKED to it here,
     * where the link cannot be forgotten. The old edition stays current until
     * the new one is published -- that publish closes the old window at its own
     * stamp, so the two meet exactly and the site never shows two current
     * editions, nor none.
     */
    @POST
    @Path("/issue/{publicId}/new-edition")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo newEdition(@PathParam("publicId") String publicId,
                                               Map<String, Object> params) {
        PublicationIssue predecessor = required(publicId);

        Date intervalFrom = null;
        Object raw = params == null ? null : params.get("intervalFrom");
        if (raw instanceof Number n) {
            intervalFrom = new Date(n.longValue());
        } else if (raw instanceof String s && !s.isBlank()) {
            intervalFrom = new Date(Long.parseLong(s.trim()));
        }

        PublicationIssue edition = lifecycle.newEdition(predecessor, intervalFrom, userService.currentUser());
        em.flush();
        return required(edition.getPublicId()).toVo(SystemPublicationIssueVo.class);
    }

    /** I18 and I19. */
    @PUT
    @Path("/issue/{publicId}/retire")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo retire(@PathParam("publicId") String publicId,
                                           @QueryParam("reason") String reason) {
        return lifecycle.retire(required(publicId), userService.currentUser(), reason)
                .toVo(SystemPublicationIssueVo.class);
    }

    @PUT
    @Path("/issue/{publicId}/reactivate")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo reactivate(@PathParam("publicId") String publicId,
                                               @QueryParam("reason") String reason) {
        return lifecycle.reactivate(required(publicId), userService.currentUser(), reason)
                .toVo(SystemPublicationIssueVo.class);
    }

    /** I9. Delete, guarded. */
    @DELETE
    @Path("/issue/{publicId}")
    @RolesAllowed("admin")
    public void delete(@PathParam("publicId") String publicId) {
        lifecycle.deleteIssue(required(publicId), null);
    }

    /**
     * I8. Edit an OPEN issue: its names, its interval, its report parameters.
     *
     * The one thing an admin could not do. An issue's name is minted at create
     * from the series' pattern over a PROVISIONAL interval start -- the lifecycle
     * service says so in as many words, "a suggested name, not final; an admin
     * may override it before then" -- and there was no way to. Likewise the
     * interval: a recovered period is created from a bound somebody worked out,
     * and correcting it meant deleting the issue and creating it again.
     *
     * The document fields are deliberately not accepted here. A file and a link
     * have their own endpoints, which archive, guard file-name collisions and
     * audit; two write paths to one field is how they come to disagree.
     */
    @PUT
    @Path("/issue/{publicId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo update(@PathParam("publicId") String publicId,
                                           UpdateIssueRequest request) {
        PublicationIssue issue = required(publicId);
        editService.update(issue, editOf(request), userService.currentUser());
        em.flush();
        return required(publicId).toVo(SystemPublicationIssueVo.class);
    }

    /**
     * What an edit may change, and nothing more.
     *
     * Every field is optional and absent means "leave it alone", so a caller
     * renaming an issue does not have to send the interval back correctly. A form
     * that round-trips a field in order to change a different one will eventually
     * round-trip a stale value.
     */
    public record UpdateIssueRequest(Map<String, String> names,
                                     Long intervalFrom,
                                     Long intervalTo,
                                     Map<String, Object> reportParams,
                                     IssueCriteriaVo criteriaOverride,
                                     Boolean clearCriteriaOverride) {
    }

    /**
     * The wire shape as the service's own. Epoch millis in, Date out.
     *
     * `clearCriteriaOverride` exists because null is a meaningful value for the
     * override and absent is a different one: absent means "leave it alone", as
     * for every other field, and the flag is the only way to say "go back to
     * inheriting the series".
     */
    static IssueEditService.IssueEdit editOf(UpdateIssueRequest request) {
        if (request == null) {
            return null;
        }
        return new IssueEditService.IssueEdit(
                request.names(),
                request.intervalFrom() == null ? null : new Date(request.intervalFrom()),
                request.intervalTo() == null ? null : new Date(request.intervalTo()),
                request.reportParams(),
                request.criteriaOverride(),
                Boolean.TRUE.equals(request.clearCriteriaOverride()));
    }

    // ------------------------------------------------------------------ document

    /**
     * Uploads the document for one language.
     *
     * IssueFileService has carried the whole rule -- archive-before-replace, the
     * distinct-file-name guard, the sticky flag that stops the next publish
     * regenerating over a correction -- since C6, and none of it was reachable:
     * there was no endpoint. An UPLOADED_FILE issue therefore had no way to
     * receive a file, which is the entire content mode.
     *
     * Legal on a PUBLISHED issue as well as an OPEN one, because that is the
     * post-publish correction path the service documents. This adds no rules of
     * its own; an endpoint that re-implements them is a second set of them.
     */
    @POST
    @Path("/issue/{publicId}/file/{lang}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo uploadFile(@PathParam("publicId") String publicId,
                                               @PathParam("lang") String lang,
                                               MultipartFormDataInput input) throws Exception {
        PublicationIssue issue = required(publicId);

        Map<String, InputStream> files = WebUtils.getMultipartInputFiles(input);
        if (files.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("NO_FILE",
                    "the upload carried no file");
        }
        // One file per language, so a request carrying several is ambiguous rather
        // than generous: silently taking whichever the map iterated first would
        // publish a document nobody chose.
        if (files.size() > 1) {
            throw new IssueLifecycleService.TransitionRefusedException("TOO_MANY_FILES",
                    "one language holds one document; " + files.size() + " files were uploaded");
        }

        Map.Entry<String, InputStream> file = files.entrySet().iterator().next();
        String fileName = safeFileName(file.getKey());
        byte[] bytes;
        try (InputStream in = file.getValue()) {
            bytes = in.readAllBytes();
        }

        fileService.upload(issue, lang, fileName, bytes, userService.currentUser());
        em.flush();
        return required(publicId).toVo(SystemPublicationIssueVo.class);
    }

    /**
     * The uploaded file's name, with any path stripped.
     *
     * A browser is not the only thing that posts here, and a name carrying `..`
     * or an absolute path would be resolved against the issue's repository folder
     * and write outside it. The name is data from the request, so it is treated
     * as data.
     */
    static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IssueLifecycleService.TransitionRefusedException("NO_FILE_NAME",
                    "the uploaded file carried no name");
        }
        String bare = fileName.replace('\\', '/');
        int slash = bare.lastIndexOf('/');
        if (slash >= 0) {
            bare = bare.substring(slash + 1);
        }
        bare = bare.trim();
        if (bare.isEmpty() || ".".equals(bare) || "..".equals(bare)) {
            throw new IssueLifecycleService.TransitionRefusedException("BAD_FILE_NAME",
                    "'" + fileName + "' does not name a file");
        }
        return bare;
    }

    /** Clears one language's uploaded file, returning it to generated content. */
    @DELETE
    @Path("/issue/{publicId}/file/{lang}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo clearFile(@PathParam("publicId") String publicId,
                                              @PathParam("lang") String lang) {
        fileService.clear(required(publicId), lang, userService.currentUser());
        em.flush();
        return required(publicId).toVo(SystemPublicationIssueVo.class);
    }

    /**
     * Sets or clears one language's external link.
     *
     * An EXTERNAL_LINK issue's link IS its document. Nothing could set it, so
     * every such issue resolved to nothing -- the link-shaped equivalent of an
     * upload endpoint that did not exist. A blank body clears it.
     */
    @PUT
    @Path("/issue/{publicId}/link/{lang}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SystemPublicationIssueVo setLink(@PathParam("publicId") String publicId,
                                            @PathParam("lang") String lang,
                                            Map<String, String> body) {
        fileService.setLink(required(publicId), lang,
                body == null ? null : body.get("link"), userService.currentUser());
        em.flush();
        return required(publicId).toVo(SystemPublicationIssueVo.class);
    }

    // ------------------------------------------------------------------ curation

    /** I12, I13, I14. Curation requires the curate permission, not merely edit rights. */
    @PUT
    @Path("/issue/{publicId}/overrides/include")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"publication-curate", "admin"})
    public void includeMember(@PathParam("publicId") String publicId, Map<String, Object> body) {
        curate(publicId, body, OverrideKind.INCLUDE);
    }

    @PUT
    @Path("/issue/{publicId}/overrides/exclude")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"publication-curate", "admin"})
    public void excludeMember(@PathParam("publicId") String publicId, Map<String, Object> body) {
        curate(publicId, body, OverrideKind.EXCLUDE);
    }

    /**
     * The curation decisions that STAND on this issue, include and exclude alike.
     *
     * The exclusions are why it exists. An excluded message is not a member, so
     * nothing in the member list can carry a "withdraw this decision" button for
     * it, and the only other record is the audit trail -- which says what
     * happened rather than what stands. An exclude followed by a clear leaves two
     * entries and no decision, and a screen reading the trail as a state shows a
     * withdrawn exclusion as though it were still in force.
     *
     * Curator tier, matching the writes it describes: it carries the author and
     * the reason, which is the admin-only half of a why-line.
     */
    @GET
    @Path("/issue/{publicId}/overrides")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"publication-curate", "admin"})
    public List<IssueOverrideVo> overrides(@PathParam("publicId") String publicId) {
        return memberList.standingDecisions(required(publicId));
    }

    /**
     * I14. Withdraw a curation decision.
     *
     * The reason travels as a query parameter for the same shape as retire and
     * reactivate: this is a decision about one named thing, and a body carrying
     * only a reason reads as a form when it is an action.
     */
    @DELETE
    @Path("/issue/{publicId}/overrides/{messageUid}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"publication-curate", "admin"})
    public void clearOverride(@PathParam("publicId") String publicId,
                              @PathParam("messageUid") String messageUid,
                              @QueryParam("reason") String reason) {
        curation.clear(required(publicId), messageUid, userService.currentUser(), reason);
    }

    /**
     * One decision, whether it names one message or a selection.
     *
     * Both shapes are accepted -- `messageUid` for a single row's button and
     * `messageUids` for a selection -- and both take the same all-or-nothing
     * path, so a caller cannot get partial application by choosing a shape.
     */
    private void curate(String publicId, Map<String, Object> body, OverrideKind kind) {
        PublicationIssue issue = required(publicId);
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));

        List<String> uids = new ArrayList<>();
        Object many = body == null ? null : body.get("messageUids");
        if (many instanceof List<?> list) {
            list.forEach(uid -> uids.add(String.valueOf(uid)));
        }
        Object one = body == null ? null : body.get("messageUid");
        if (uids.isEmpty() && one != null) {
            uids.add(String.valueOf(one));
        }

        curation.curate(issue, uids, kind, userService.currentUser(), reason);
    }

    private PublicationIssue required(String publicId) {
        PublicationIssue issue = issueService.findByPublicId(publicId);
        if (issue == null) {
            throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_FOUND",
                    "no issue with public id " + publicId);
        }
        return issue;
    }
}
