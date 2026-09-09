/*
 * Copyright 2026 Danish Emergency Management Agency.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.web.publication;

import jakarta.annotation.security.RolesAllowed;
import org.niord.core.user.Roles;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryDesc;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssuePublicWindowService;
import org.niord.core.publication.series.IssuePublicationMapping;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationDomainGuard;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.SeriesIdSlug;
import org.niord.core.publication.series.PublicationIssueService;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesDesc;
import org.niord.core.publication.series.PublicationSeriesService;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesAvailability;
import org.niord.core.publication.series.SeriesAvailabilityResolver;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.StaleVersionGuard;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;
import org.niord.core.publication.vo.MessagePublication;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    PublicationDomainGuard domainGuard;

    // The same resolver the full series editor uses. A one-off is a series,
    // so a sharing list it accepted and that form refused would be two
    // definitions of a valid publication.
    @Inject
    SeriesAvailabilityResolver availabilityResolver;

    @Inject
    IssueLifecycleService lifecycle;

    // The public period: the rule, the write and the audit entry, together. An
    // unaudited change to what the public can read is the one kind this feature
    // does not have.
    @Inject
    IssuePublicWindowService windowService;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    // A one-off may be query-backed like any other series, so its criteria get the
    // same C-4 treatment: an operand naming nothing is refused on the save rather
    // than discovered at the resolve.
    @Inject
    org.niord.core.publication.series.criteria.PublicationOperandResolver operands;

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
     * THREE INDEPENDENT FACTS, NEVER ONE DOT. What the public can read is decided
     * by the STATUS (`issueStatus`: being assembled, released, withdrawn), the
     * PUBLIC PERIOD (`publicFrom` and `publicTo`, both hand-set and both
     * editable), and the CATEGORY (`categoryPublish`, with `categoryName` to name
     * it). They are separate on the wire because they are separate decisions:
     * folded into one flag, "prepared for next month", "withdrawn" and "filed
     * under a category that is on no public site" are the same word, and the
     * screen can name neither the reason nor the control that changes it.
     */
    public static class OneOffVo {
        public SystemPublicationSeriesVo series;
        /**
         * Whether the publication is on the public site right now.
         *
         * @deprecated the three facts below say everything this said and say it
         *         separately, which is the point. "Active" folded a status, a
         *         period and a category flag into one dot, so a publication that
         *         was prepared for next month, one that was withdrawn and one
         *         filed under a category that is on no public site all read the
         *         same -- off, with nothing saying which. Kept for one release so
         *         a consumer that still reads it is not broken by the change that
         *         replaced it; the screens compute the sentence from issueStatus,
         *         publicFrom, publicTo and categoryPublish, and no client of this
         *         API reads this field any more. It goes at the next release.
         */
        @Deprecated
        public boolean active;
        public String issuePublicId;
        /**
         * The ISSUE's revision, for the two actions addressed at the issue.
         *
         * Withdrawing and re-publishing are status transitions, and the endpoints
         * that take them are the issue's own -- they version-check against the
         * issue, not against the series, and this response is the only place a
         * one-off screen learns that number. Without it those two calls go out
         * unguarded, which is last-write-wins on the one action that takes a
         * document off the public site.
         *
         * `series.version` stays the token for the SAVE and for the public period:
         * those are written through this resource, which checks the series.
         */
        public Integer issueVersion;
        /**
         * OPEN, PUBLISHED or RETIRED: the publication's status, and one of the
         * three independent facts the screens state separately.
         *
         * Draft, Published and Withdrawn in the words a reader uses. RETIRED is
         * gone from the public site, history included, while the file stays at its
         * address so a message that cites it still resolves.
         *
         * The SERIES' status is not repeated here: it travels as `series.status`,
         * and a second copy beside it would be a second answer to one question.
         */
        public String issueStatus;
        /**
         * Whether the publication's CATEGORY is exposed on the public site.
         *
         * The public listing is category.publish AND the issue published AND the
         * window open, and the first of those three is not on the series VO --
         * only the categoryId is. A screen that fetched the category list and
         * re-derived it would hold a second definition of visibility, free to
         * disagree with the sentence beside it, and would have to tell "the flag
         * is false" apart from "the list has not arrived yet" while the two look
         * identical. So the flag travels with the publication it describes.
         */
        public boolean categoryPublish;
        /**
         * The category's name in the requested language, for the sentence that
         * says a publication is on no public site.
         *
         * "Not public" on its own invites the reader to look for the setting that
         * turns it on; there is none on this form, because the decision belongs to
         * the category. Naming the category is what turns the sentence into
         * something actionable, and only this response knows which category the
         * publication is filed under and what it is called at once.
         *
         * The requested language, or the first one the category HAS -- the same
         * fallback every other localized read applies, because a category with no
         * name in the asked-for language would otherwise render as a blank
         * parenthesis.
         */
        public String categoryName;
        /**
         * When the publication becomes public, or null while nobody has decided.
         *
         * Hand-set, and editable after release: a publication can be prepared with
         * the day it goes public already chosen, released today, and stay off the
         * public site until that day. A null here on a released publication is a
         * state the API refuses, precisely because it would be invisible with
         * nothing saying why.
         */
        public Long publicFrom;
        /**
         * When the publication stops being on the public site, or null for open-ended.
         *
         * The state an admin cannot see from the other two: a series ACTIVE and an
         * issue PUBLISHED with a period that closed in 2017 is off the public site,
         * and until this field existed nothing on the screen said so.
         */
        public Long publicTo;
        /** Per language, for an EXTERNAL_LINK publication. */
        public List<LangText> links = new ArrayList<>();
        /** Per language, read-only: what was uploaded, if anything. */
        public List<LangText> fileNames = new ArrayList<>();
        /** False while the publication still needs its bytes before it can go live. */
        public boolean publishable;
        /**
         * Why the publication is being taken off the public list.
         *
         * Write-only, and required only when `active` turns false. Retiring
         * withdraws a document people may be reading and may have cited, so the
         * trail has to say why in words -- the same rule the issue's own retire
         * carries, because it IS that action.
         */
        public String reason;
    }

    // -------------------------------------------------------------------- read

    /**
     * The dashboard list: every publication whose kind is ONE_OFF.
     *
     * `domain` narrows by OWNER, exactly as the series lists do, and not by who
     * may cite it. A one-off shared with this desk is read-only here -- every
     * control on the row would answer 403 -- so it belongs in the citation dialog
     * and not in an administration list.
     *
     * Omitting the parameter returns the estate. Not for a control in the admin
     * area, which has none and should not: a one-off is administered in its owner
     * domain and listed nowhere else. It is for the readers that really do span
     * the installation -- the export and the scripts built on it, and the
     * estate-wide cut-over panels, which must not be cut down to one desk.
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    @NoCache
    @RolesAllowed(Roles.ADMIN)
    public List<OneOffVo> list(@QueryParam("domain") String domain,
                               @QueryParam("lang") String lang) {
        String owner = domain == null || domain.isBlank() ? null : domain.trim();
        return seriesService.findAll().stream()
                .filter(s -> s.getKind() == SeriesKind.ONE_OFF)
                .filter(s -> owner == null
                        || (s.getDomain() != null && owner.equals(s.getDomain().getDomainId())))
                .map(s -> toVo(s, lang))
                .toList();
    }

    /**
     * One publication, for the screen that shows exactly one.
     *
     * The detail page used to reach its publication by asking for the ESTATE and
     * keeping a single row out of it -- 6,456 B and 113 ms to render one document,
     * measured 2026-09-07 on a deployment where a request that does almost nothing
     * costs 0.13-0.18 s. The measurement is repeatable: the "one-off detail"
     * scenario of the client repository's scripts/perf/publications-requests.mjs
     * replays that page and prints every request it makes. It went through the
     * list because `active` is not a field on the series: toVo folds series
     * ACTIVE, issue PUBLISHED and the public window into the one dot the screen
     * shows, and any of the three being off is invisible from the other two. A
     * page that re-derived it would be a second definition of visibility, free to
     * disagree with the list the publication was reached from -- so the fold gets
     * a route of its own rather than a copy on the client.
     *
     * UNSCOPED, like the list called with no `domain`. A deep link into a one-off
     * has to resolve from whichever desk the reader is sitting at, and narrowing
     * by owner here would answer "no such publication" for one that exists and is
     * open on somebody else's screen.
     *
     * A series that is not a one-off is answered SERIES_NOT_FOUND -- a 404 -- and
     * not the save's SERIES_NOT_ONE_OFF 400. This collection IS the one-off
     * publications: the list filters the estate by kind, so a weekly series' id
     * names nothing in it, and 404 is the same answer the list already gives by
     * leaving the row out. The 400 belongs to the SAVE, where the caller holds a
     * form that would force cadence NONE onto a scheduled series and has to be
     * told what it is about to do rather than that the series is missing.
     */
    @GET
    @Path("/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    @NoCache
    @RolesAllowed(Roles.ADMIN)
    public OneOffVo get(@PathParam("seriesId") String seriesId,
                        @QueryParam("lang") String lang) {
        PublicationSeries series = seriesService.findBySeriesId(seriesId);
        if (series == null || series.getKind() != SeriesKind.ONE_OFF) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_FOUND",
                    "no one-off publication with id " + seriesId);
        }
        return toVo(series, lang);
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
    @RolesAllowed(Roles.ADMIN)
    @DomainScoped
    public OneOffVo create(OneOffVo request, @QueryParam("lang") String lang) {
        SystemPublicationSeriesVo vo = seriesOf(request);
        // Checked on the body: there is no stored series to compare against on a
        // create, so what is refused is authoring straight into another domain.
        domainGuard.assertMayAssign(vo.getDomainId(), "The new one-off publication");

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
        // A one-off is almost always a document or a link -- a reference other
        // desks cite -- so silence means shared everywhere, which is what these
        // publications were before the field existed. The editor pre-fills the
        // same value; this is what a script that omits it gets.
        if (vo.getAvailability() == null || vo.getAvailability().isBlank()) {
            series.setAvailability(SeriesAvailability.defaultFor(series.getContentMode()));
        }
        forceOneOffShape(series);
        requireCategory(series);
        refuseHardRules(series);
        refuseDanglingOperands(series);

        PublicationSeries saved = seriesService.create(series);
        PublicationIssue issue = lifecycle.create(saved, new Date(), IntervalBoundSource.MANUAL, null);
        applyLinks(issue, request);

        if (request.active) {
            activate(saved);
            flushBeforePublish();
            publishIfComplete(saved, issue);
        }
        return toVo(saved, lang);
    }

    /**
     * FLUSHED before publishing, and this is load-bearing.
     *
     * IssuePublishService re-reads the issue with LockModeType.PESSIMISTIC_WRITE.
     * Taking a row lock on an issue this same transaction has only just persisted,
     * while its descs still hold unflushed changes, fails with "Row was updated or
     * deleted by another transaction" -- naming a conflict with a transaction that
     * does not exist. Writing the rows first gives the lock something consistent
     * to read.
     *
     * The sibling of the flush in IssueCurationService: both are places where
     * Hibernate has to be told to catch up before somebody else reads the row.
     */
    private void flushBeforePublish() {
        em.flush();
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
    @RolesAllowed(Roles.ADMIN)
    @DomainScoped
    @VersionChecked
    public OneOffVo update(@PathParam("seriesId") String seriesId, OneOffVo request,
                           @QueryParam("lang") String lang) {
        PublicationSeries series = required(seriesId);
        SystemPublicationSeriesVo vo = seriesOf(request);

        // An ownerless row is not adopted by a save, and this comes first because
        // it is a more useful answer than the domain refusal that would follow.
        // The same rule the full series editor applies, from the same method: a
        // one-off is a series, and a claim that had to go through the transfer
        // action on one form and not the other would be no rule at all.
        PublicationSeriesRestService.refuseOwnerlessSave(series);

        // Both ends: the stored domain says whether this one-off is the caller's,
        // and the body's says where they are moving it. This endpoint also
        // retires and reactivates the issue underneath, so it is the whole
        // one-off surface and not only a save.
        domainGuard.assertWritable(series);
        domainGuard.assertMayAssign(vo.getDomainId(), "The one-off publication '" + seriesId + "'");

        // Then whose revision this is -- after the domain refusal, which is the
        // more useful answer for a caller at the wrong desk.
        //
        // The SERIES' revision, and one token for both rows. A one-off is a series
        // and its single issue edited through one form: they are only ever written
        // together, from here, so a second token for the issue would be a second
        // thing to get wrong with nothing to gain -- and this endpoint's own save
        // is what moves the series revision on either way.
        StaleVersionGuard.check(series, vo.getVersion());

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

        // THE DOT IS A SWITCH, AND IT TURNS ON A TRANSITION RATHER THAN ON A VALUE.
        //
        // `active` folds series ACTIVE, issue PUBLISHED and an open public window
        // into the one control the editor renders, so every save carries it back --
        // a rename, a new link, a changed category. Reading the requested value as
        // an instruction is then wrong in both directions: a publication whose
        // window ran out reports active:false, so a plain rename was refused for a
        // missing reason, and a rename that supplied one would have RETIRED the
        // issue as a side effect of the rename.
        //
        // So what happens is decided from the difference between what is stored and
        // what is asked for, folded with the same predicate the read reports -- and
        // it is decided HERE, before anything is written, so the save underneath
        // cannot move the state the decision was made against.
        ActiveAction activeAction =
                activeAction(series, onlyIssue(series), request.active, new Date());

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
        refuseHardRules(series);
        refuseDanglingOperands(series);

        PublicationSeries saved = seriesService.update(series);

        PublicationIssue issue = onlyIssue(saved);
        if (issue != null) {
            applyLinks(issue, request);
            issueService.update(issue);
        }

        // Decided before the save, carried out after it, so that the refusals the
        // save itself raises -- an operand naming nothing, a rule the document
        // breaks -- are answered first: they are about what the admin just typed.
        switch (activeAction) {
            case REACTIVATE_ISSUE -> lifecycle.reactivate(issue, userService.currentUser(), null);
            case ACTIVATE_SERIES -> {
                activate(saved);
                flushBeforePublish();
                publishIfComplete(saved, issue);
            }
            case REFUSE_WINDOW_CLOSED -> throw new PublicWindowClosedException(issue.getPublicTo());
            case RETIRE_ISSUE -> {
                // The reason is demanded here rather than inside retire so the refusal
                // names the field the form actually has. Same code, same bounds.
                IssueLifecycleService.requireReason(request.reason,
                        "taking this publication off the public list removes a document people may have "
                                + "cited; it must say why");
                lifecycle.retire(issue, userService.currentUser(), request.reason);
            }
            case NOTHING -> {
                // The dot already says what it is being asked to say. This is what
                // an ordinary save is, and it must not move the publication.
            }
        }
        return toVo(saved, lang);
    }

    /**
     * The publication's public period, both ends, set or cleared.
     *
     * ITS OWN ACTION BECAUSE IT IS ITS OWN DECISION. The period is what puts a
     * document on the public site and what takes it off, and for a one-off nothing
     * else writes it -- there is no successor whose release would cap it and no
     * cadence to derive it from. Folding it into the save would make "put this
     * back on the list" and "decide when this stops being current" the same
     * gesture, and the save would then have to guess an end for a publication
     * somebody meant to run indefinitely.
     *
     * BOTH ENDS TOGETHER, because they are one interval. Two endpoints moving one
     * end each can be used to describe a period that ends before it starts, and
     * neither call would be the wrong one -- so the pair is validated as a pair,
     * and the audit entry names all four values for the same reason.
     *
     * A START IN THE FUTURE IS ORDINARY. It is how a publication is prepared: give
     * it the day it goes public, release it now, and it stays PUBLISHED and off
     * the public site until the day arrives. Nothing runs on that day; the public
     * listing simply asks whether the period covers the instant being read at.
     *
     * ONE-OFFS ONLY, and the refusal is not a formality. A cadenced series' issue
     * has its period closed by the issue that succeeds it, so clearing an end
     * there leaves two uncapped issues and hands the public download site two
     * current editions of the same publication.
     */
    @PUT
    @Path("/{seriesId}/public-window")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    @DomainScoped
    @VersionChecked
    public OneOffVo setPublicWindow(@PathParam("seriesId") String seriesId,
                                    PublicWindowRequest request,
                                    @QueryParam("lang") String lang) {
        PublicationSeries series = seriesService.findBySeriesId(seriesId);
        if (series == null) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_FOUND",
                    "no publication series with id " + seriesId);
        }
        // 409 rather than the save's 400: the caller named a real series and the
        // request is well formed, and it is the series' KIND that makes the action
        // wrong. Reclassifying the series makes the same request correct.
        if (series.getKind() != SeriesKind.ONE_OFF) {
            throw new IssueLifecycleService.TransitionRefusedException("NOT_ONE_OFF",
                    seriesId + " is a " + series.getKind() + " series. The public window of a "
                            + "scheduled publication's issue is closed by the issue that succeeds "
                            + "it, so setting it here would leave the public site with two current "
                            + "editions.");
        }
        // The caller's desk, before anything is written.
        domainGuard.assertWritable(series);
        StaleVersionGuard.check(series, request == null ? null : request.version());

        PublicationIssue issue = onlyIssue(series);
        if (issue == null) {
            throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_FOUND",
                    "'" + seriesId + "' has no issue yet, so there is no public period to set");
        }

        // The rule, the write and the trail live together in the core service: the
        // validation says which periods are describable and the audit entry says
        // which one was chosen, and a reader of the history is asking about
        // exactly that pair. It also puts them where a test with a database can
        // reach them, which this module has no harness for.
        //
        // AND THE SERIES' REVISION MOVES THERE TOO, for the same reason. The check
        // above is made against the series -- one revision covers both rows on this
        // surface -- while the period is written on the ISSUE, so a bump left to
        // this endpoint would be a bump the core write could be reached without.
        // The response below carries the moved counter, which is the token the form
        // composes its next write against.
        windowService.set(issue, instant(request == null ? null : request.publicFrom()),
                instant(request == null ? null : request.publicTo()), userService.currentUser());

        return toVo(series, lang);
    }

    private static Date instant(Long epochMillis) {
        return epochMillis == null ? null : new Date(epochMillis);
    }

    /**
     * The public period's two ends, and the revision the caller composed them against.
     *
     * Both are epoch millis and both may be null, and null is a VALUE on either
     * side rather than an omission: a null end is open-ended -- the shape four of
     * the five archived one-offs have -- and a null start means nobody has decided
     * yet, which only a publication still being assembled may say.
     *
     * `version` is the SERIES' revision, matching the save: a one-off is a series
     * and its single issue edited through one form, and a second token for the
     * issue would be a second thing to get wrong with nothing to gain.
     */
    public record PublicWindowRequest(Long publicFrom, Long publicTo, Integer version) {
    }

    /**
     * The publication is off the public site because its window ran out.
     *
     * Carries the end date on the wire, because the whole point of the refusal is
     * that it names the state the toggle could not see -- and a client parsing it
     * back out of the sentence would break the first time the wording improved.
     */
    public static class PublicWindowClosedException
            extends org.niord.core.publication.series.PublicationException {

        private final Date publicTo;

        public PublicWindowClosedException(Date publicTo) {
            super("PUBLIC_WINDOW_CLOSED",
                    "this publication's public window ended on " + publicTo + ", so it is off the "
                            + "public site whatever the active toggle says. Putting it back is a "
                            + "decision about the window: set or clear its end.");
            this.publicTo = publicTo;
        }

        public Date publicTo() {
            return publicTo;
        }
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

        // ABSENT LEAVES THE OWNER ALONE. It used to CLEAR it, because a null owner
        // meant "visible from every domain" -- which is what four of these
        // publications were. Reachability is availability's job now, and a one-off
        // has an owning desk like everything else, so there is nothing for a blank
        // to express and clearing would only produce a row S-20a refuses.
        if (vo.getDomainId() != null && !vo.getDomainId().isBlank()) {
            Domain domain = domainService.findByDomainId(vo.getDomainId());
            if (domain == null) {
                throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                        "no domain " + vo.getDomainId());
            }
            series.setDomain(domain);
        }

        availabilityResolver.apply(series, vo);
    }

    /**
     * The links from the body, onto the issue's descs.
     *
     * Package-private so what a save does and does not store can be asserted: the
     * body it receives is the body this endpoint just handed out.
     */
    static void applyLinks(PublicationIssue issue, OneOffVo request) {
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
            String value = link.value == null || link.value.isBlank() ? null : link.value.trim();
            if (isDerivedAddress(desc, value)) {
                // THE VO COMING BACK, not a link somebody typed.
                //
                // `links` reports a fetchable address, so a file-backed desc
                // reports the repository URL derived from its storage path. The
                // detail page sends the body back unchanged when it activates or
                // deactivates -- that is a transition and not an edit -- and
                // storing the derived value would turn it into an explicit link
                // that outlives the file it names: replace the document and the
                // path moves while the frozen link keeps pointing at the old one.
                continue;
            }
            desc.setLink(value);
        }
    }

    /**
     * Whether the value is this desc's own derived address rather than a link.
     *
     * Only when the desc has no link of its own: with one stored, the derived
     * address IS that link and writing it back is a no-op that keeps saying so.
     */
    private static boolean isDerivedAddress(PublicationIssueDesc desc, String value) {
        return value != null
                && (desc.getLink() == null || desc.getLink().isBlank())
                && value.equals(IssuePublicationMapping.linkOf(desc));
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

    /**
     * An operand that names nothing is refused on the save, active or not.
     *
     * The same rule the full series editor applies, and it has to be the same one:
     * a document this form accepted and that one refused would be two definitions
     * of a valid series, differing only by which screen it was typed on.
     */
    /**
     * The rules a draft may not break either, refused on the save that carries them.
     *
     * The same gate the full series editor applies. Two of them stand behind NOT
     * NULL columns -- the owning domain and the sharing setting -- so without this
     * a one-off missing either would not be saved incomplete; it would die inside
     * the flush with a message naming a Java field, on a form that has no control
     * by that name.
     */
    private static void refuseHardRules(PublicationSeries series) {
        List<SeriesValidator.FieldError> hard = SeriesValidator.hardRules(series);
        if (!hard.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    hard.size() + " rule(s) fail: " + hard, hard);
        }
    }

    private void refuseDanglingOperands(PublicationSeries series) {
        List<SeriesValidator.FieldError> dangling = SeriesValidator.danglingOperands(series, operands);
        if (!dangling.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    dangling.size() + " criteria operand(s) name nothing: " + dangling, dangling);
        }
    }

    private void activate(PublicationSeries series) {
        List<SeriesValidator.FieldError> errors =
                SeriesValidator.validateForActivation(series, null, operands);
        if (!errors.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    errors.size() + " rule(s) fail: " + errors);
        }
        series.setStatus(SeriesStatus.ACTIVE);
        seriesService.update(series);
    }

    @Inject
    org.niord.core.user.UserService userService;

    /**
     * Publish when there is something to publish.
     *
     * An uploaded publication with no bytes yet is left OPEN: the checklist
     * would refuse it, and refusing here would turn "you still need to attach
     * the file" into an error on save. An uploaded one that HAS its bytes is
     * left alone by the release's render step -- the file is sticky and no
     * report is configured -- so the same call serves both kinds.
     *
     * The one-off surface has no release rail to acknowledge resolution
     * warnings on, so it acknowledges them all; the publish audit still records
     * what the resolution raised. A one-off is almost always an uploaded file or
     * a link, where nothing resolves and nothing can warn.
     */
    private void publishIfComplete(PublicationSeries series, PublicationIssue issue) {
        if (issue == null || !isPublishable(series, issue)) {
            return;
        }
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, userService.currentUser(), null));
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
            // Room for the suffix, so the disambiguator is never the part that
            // gets cut -- a truncated one reintroduces the collision it exists
            // to break.
            String suffix = "-" + n;
            String candidate = truncate(proposed, SeriesIdSlug.MAX_SERIES_ID - suffix.length()) + suffix;
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
     * Delegated to the shared minting, because the legacy import mints into the
     * SAME namespace and the two used to disagree about where the fold happens.
     * A series id is immutable after create, so the disagreement would not have
     * surfaced as a conflict -- it would have produced two series meant to be one.
     */
    private static String slug(String text) {
        String cleaned = SeriesIdSlug.fold(text);
        return truncate(cleaned.isEmpty() ? "publication" : cleaned, SeriesIdSlug.MAX_SERIES_ID);
    }

    private static String truncate(String text, int max) {
        return SeriesIdSlug.fit(text, max);
    }

    private OneOffVo toVo(PublicationSeries series, String lang) {
        OneOffVo vo = new OneOffVo();
        vo.series = series.toVo(SystemPublicationSeriesVo.class);

        PublicationIssue issue = onlyIssue(series);
        if (issue != null) {
            vo.issuePublicId = issue.getPublicId();
            vo.issueVersion = issue.getVersion();
            vo.issueStatus = issue.getStatus() == null ? null : issue.getStatus().name();
            vo.publicFrom = issue.getPublicFrom() == null ? null : issue.getPublicFrom().getTime();
            vo.publicTo = issue.getPublicTo() == null ? null : issue.getPublicTo().getTime();
            fillIssueDescs(issue, vo);
            vo.publishable = isPublishable(series, issue);
        }

        vo.categoryPublish = series.getCategory() != null && series.getCategory().isPublish();
        vo.categoryName = categoryNameOf(series.getCategory(), lang);

        vo.active = isActive(series, issue, new Date());
        return vo;
    }

    /**
     * The category's name, in the requested language or the first it HAS.
     *
     * The fallback is the same one every other localized read applies, and it is
     * not a nicety here: the name is used in the sentence that explains why a
     * publication is on no public site, and a blank one leaves the reader with an
     * empty parenthesis and nowhere to go.
     *
     * Package-private and static so the projection can be asserted without a
     * database.
     */
    static String categoryNameOf(PublicationCategory category, String lang) {
        if (category == null || category.getDescs() == null || category.getDescs().isEmpty()) {
            return null;
        }
        for (PublicationCategoryDesc desc : category.getDescs()) {
            if (lang != null && lang.equals(desc.getLang()) && desc.getName() != null) {
                return desc.getName();
            }
        }
        return category.getDescs().get(0).getName();
    }

    /**
     * The three states that decide whether the public can read this publication,
     * folded into the one dot the screen shows.
     *
     * ONE DEFINITION, shared by the read that REPORTS the dot and the save that
     * decides what turning it means. Two copies would let a publication read as
     * off on the screen while the save that turns it off decides it was on, and
     * the disagreement would surface as a save that refuses to rename a document.
     *
     * Package-private and static so it can be asserted without a database: what
     * `active` means is the whole contract of the toggle.
     */
    static boolean isActive(PublicationSeries series, PublicationIssue issue, Date now) {
        return series != null
                && series.getStatus() == SeriesStatus.ACTIVE
                && issue != null
                && issue.getStatus() == IssueStatus.PUBLISHED
                && (issue.getPublicFrom() == null || !issue.getPublicFrom().after(now))
                && (issue.getPublicTo() == null || issue.getPublicTo().after(now));
    }

    /** What the save has to DO about the active dot, once the two states are compared. */
    enum ActiveAction {
        /** The dot already says what it is being asked to say. */
        NOTHING,
        /** Off because the issue was retired: put it back. */
        REACTIVATE_ISSUE,
        /** Off because the series was never activated: activate it, and publish if it can. */
        ACTIVATE_SERIES,
        /** Off because the public window ran out, which this toggle does not own. */
        REFUSE_WINDOW_CLOSED,
        /** On, and asked to come off: that is the issue's own retire. */
        RETIRE_ISSUE
    }

    /**
     * The transition a save's active dot asks for, decided from the difference.
     *
     * A TRANSITION, NOT A VALUE, and that distinction is the bug this method
     * exists to hold shut. The editor sends the whole publication back on every
     * save, so `active` arrives on a rename exactly as it does on a deliberate
     * flip -- and for a publication whose window ran out it arrives as false,
     * because that is what the read reported. Acting on the value alone made an
     * ordinary rename either a refusal for a missing reason or, with one supplied,
     * a retire nobody asked for.
     *
     * Package-private and static so every case can be asserted without a database
     * or a server: this endpoint's transitions are what it is for.
     */
    static ActiveAction activeAction(PublicationSeries series, PublicationIssue issue,
                                     boolean requested, Date now) {
        if (requested == isActive(series, issue, now)) {
            return ActiveAction.NOTHING;
        }
        if (!requested) {
            // Off, and it was on -- so the issue is PUBLISHED by the fold above.
            // Off is the issue's own retire, with its own reason and its own audit
            // entry; nothing else takes a document off the public site.
            return issue != null && issue.getStatus() == IssueStatus.PUBLISHED
                    ? ActiveAction.RETIRE_ISSUE
                    : ActiveAction.NOTHING;
        }
        if (issue != null && issue.getStatus() == IssueStatus.RETIRED) {
            return ActiveAction.REACTIVATE_ISSUE;
        }
        if (series.getStatus() != SeriesStatus.ACTIVE) {
            return ActiveAction.ACTIVATE_SERIES;
        }
        if (onlyThePublicWindowIsClosed(series, issue)) {
            // NEITHER BRANCH ABOVE MATCHES, AND THAT USED TO BE SILENCE. The series
            // is active and the issue is published; what is off is the public
            // window, which this toggle does not own. Re-opening it puts a document
            // back on the public site, which is the decision publicTo exists to
            // protect -- so it is its own action, on its own endpoint, rather than
            // a side effect of a dot.
            return ActiveAction.REFUSE_WINDOW_CLOSED;
        }
        // On, and not live for a reason none of the branches owns: an uploaded
        // publication still waiting for its bytes. Saying so is the upload's job.
        return ActiveAction.NOTHING;
    }

    /**
     * Whether this publication's ONLY closed state is a public window that ran out.
     *
     * The case the activate toggle could not answer. Series ACTIVE, issue
     * PUBLISHED, and a window that ended years ago: neither branch of the toggle
     * matches -- there is no retired issue to reactivate and no inactive series to
     * activate -- so the save used to return 200 having changed nothing, and the
     * screen redrew the same "not active" dot.
     */
    private static boolean onlyThePublicWindowIsClosed(PublicationSeries series, PublicationIssue issue) {
        if (series.getStatus() != SeriesStatus.ACTIVE
                || issue == null || issue.getStatus() != IssueStatus.PUBLISHED) {
            return false;
        }
        Date to = issue.getPublicTo();
        return to != null && !to.after(new Date());
    }

    /**
     * The per-language rows for the issue underneath: an address, and a file name.
     *
     * `links` carries an ADDRESS A CLIENT CAN FETCH, not the desc's link column.
     * A one-off whose document was uploaded here has no link column at all -- it
     * has a storage path -- so reading the column straight left every natively
     * uploaded publication with a blank address, and the list with nothing to
     * point its title at, while the imported rows beside it linked fine because
     * those carry their address verbatim in the column.
     *
     * The rule is the one rule: an explicit link wins, otherwise the repository
     * URL that serves the file. It belongs to the mapping and is shared with the
     * public list and the citation picker, so all three name the same document.
     *
     * A row per desc either way. A publication that is a reference and nothing
     * else -- no file, no link -- still has a language and a title to show, and
     * its address is simply absent.
     *
     * Package-private and static so the projection can be asserted without a
     * database: what an address resolves to is the whole point of the field.
     */
    static void fillIssueDescs(PublicationIssue issue, OneOffVo vo) {
        for (PublicationIssueDesc d : issue.getDescs()) {
            vo.links.add(new LangText(d.getLang(), IssuePublicationMapping.linkOf(d)));
            vo.fileNames.add(new LangText(d.getLang(), d.getFileName()));
        }
    }
}
