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

package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.TypedQuery;
import org.niord.core.publication.series.replay.ShadowDiffService;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistence for publication series.
 *
 * Deliberately thin. Everything expressible as a pure function -- membership,
 * criteria validation, criteria resolution -- lives outside any service and is
 * tested without a database. What is left here is the part that genuinely needs
 * one.
 *
 * TRANSACTION DEMARCATION, FOR EVERY SERVICE IN THIS PACKAGE. It comes from
 * BaseService, which carries a class-level @Transactional; that annotation is
 * @Inherited, so every service extending it is demarcated without saying so
 * again. Repeating it on the subclass changes nothing and only invites a reader
 * to conclude that the classes without it are somehow different. Say something
 * about transactions here ONLY where the demarcation departs from that default
 * -- a method that must run outside a transaction, or one that opens its own --
 * and say why at that method.
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class PublicationSeriesService extends BaseService {

    @Inject
    IssueAuditService audit;

    @Inject
    ShadowDiffService shadowDiff;

    /** Looks a series up by its human-authored, stable id. */
    public PublicationSeries findBySeriesId(String seriesId) {
        return em.createQuery("SELECT s FROM PublicationSeries s WHERE s.seriesId = :seriesId",
                        PublicationSeries.class)
                .setParameter("seriesId", seriesId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public List<PublicationSeries> findAll() {
        return em.createNamedQuery("PublicationSeries.findAllOrdered", PublicationSeries.class)
                .getResultList();
    }

    public List<PublicationSeries> findByStatus(SeriesStatus status) {
        return em.createQuery("SELECT s FROM PublicationSeries s WHERE s.status = :status ORDER BY s.seriesId",
                        PublicationSeries.class)
                .setParameter("status", status)
                .getResultList();
    }

    /**
     * Every series whose public reads are answered by the given model.
     *
     * Status-blind on purpose. The public adapter does not read the series status
     * either -- what it reads is this column -- so a series retired after a
     * cutover keeps serving the public from the new half. A rollback that skipped
     * it would leave exactly the rows nobody is watching pointed at the model
     * being rolled back.
     */
    public List<PublicationSeries> findByPublicAuthority(PublicAuthority authority) {
        return em.createQuery(
                        "SELECT s FROM PublicationSeries s WHERE s.publicAuthority = :authority "
                                + "ORDER BY s.seriesId", PublicationSeries.class)
                .setParameter("authority", authority)
                .getResultList();
    }

    /**
     * How often a series comes out, as the order its strip is drawn in.
     *
     * The SAME order the dashboard reads its cards in -- the weeklies an editor
     * works every week first, the annuals last, the cadence-less ones after those.
     *
     * Ranked in Java rather than in SQL because the cadence is persisted as a
     * native MySQL ENUM, so ORDER BY on the column orders by whatever the column
     * orders by. That happens to agree today and would stop agreeing the first
     * time a constant is inserted rather than appended.
     */
    private static final List<SeriesCadence> CADENCE_ORDER = List.of(
            SeriesCadence.DAILY, SeriesCadence.WEEKLY, SeriesCadence.MONTHLY,
            SeriesCadence.YEARLY, SeriesCadence.NONE);

    /**
     * The series one desk's period strip is drawn for.
     *
     * The SAME set the admin list shows -- narrowed to the ones with a calendar,
     * to the ones without, or neither: the strip and the card grid are two halves
     * of one screen, and a server that picked a different set would leave cards
     * with no strip and strips with no card. The rule is written here, once,
     * rather than copied out of the frontend -- where it lives today -- because
     * the two drifting apart is a per-desk failure nothing reports.
     *
     * The reading ORDER already spans both kinds: CADENCE_ORDER ends at NONE, so
     * an unnarrowed read comes back weeklies first and the calendar-less series
     * last, which is the order the dashboard draws its two card groups in.
     *
     * Narrowed by OWNER, which is the admin list's rule and not the picker's. A
     * publication is listed in its owner domain and nowhere else -- that is the
     * whole point of having an owner -- so a series another desk owns is absent
     * here even where it is shared and citable from this one. A strip reaching it
     * would arrive with no card to hang on.
     *
     * @param cadenced true for the series with a schedule, false for the ones
     *                 without -- the dashboard asks each a different question --
     *                 and NULL for both in one read. Both is what a dashboard
     *                 drawing both kinds of card actually wants: asking twice
     *                 pays the round trip twice for one question, and the two
     *                 answers are then a union assembled on the client, where
     *                 the cap below cannot see it
     * @param limit    the most series one strip request answers for. A SLICE, not
     *                 a refusal: a desk owning more than this wants the strips it
     *                 can have, where a named-series request rightly refuses
     *                 because the CALLER chose the list. Applied to the UNION when
     *                 both kinds are asked for, so the bound stays a bound on the
     *                 answer rather than one per half
     */
    public List<PublicationSeries> findTimelineSeries(String domainId, Boolean cadenced, int limit) {
        if (domainId == null || domainId.isBlank()) {
            return List.of();
        }
        // Selected WITHOUT the descs fetch join, for the same reason the issue
        // strip's own query is two queries: a collection fetch makes Hibernate
        // page in memory, and the descs are primed below on the rows that survive
        // the cap. That also removes the lazy load per series the naming patterns
        // used to trigger, one round trip each.
        TypedQuery<PublicationSeries> query = em.createQuery(
                        "SELECT s FROM PublicationSeries s WHERE s.domain.domainId = :domain "
                                + "AND s.kind <> :oneOff "
                                + "AND (s.status = :active OR (s.status = :draft AND s.importSource IS NULL))"
                                // No cadence predicate at all when both kinds are wanted --
                                // rather than the pair of reads concatenated -- so the sort and
                                // the cap below see one list and the cap bounds the answer.
                                + (cadenced == null
                                        ? ""
                                        : " AND s.cadence " + (cadenced ? "<>" : "=") + " :none"),
                        PublicationSeries.class)
                .setParameter("domain", domainId.trim())
                .setParameter("oneOff", SeriesKind.ONE_OFF)
                .setParameter("active", SeriesStatus.ACTIVE)
                .setParameter("draft", SeriesStatus.DRAFT);
        if (cadenced != null) {
            query.setParameter("none", SeriesCadence.NONE);
        }
        List<PublicationSeries> found = query.getResultList();

        List<PublicationSeries> ordered = new ArrayList<>(found);
        // Ties break on the stable id rather than on the localized name the cards
        // sort by. Below the cap the order is invisible -- the page looks each
        // series up by id -- and above it the two sides may keep different fifties;
        // a card whose strip did not arrive renders without one, which is what it
        // already does for a series beyond the frontend's own slice.
        ordered.sort(Comparator
                .comparingInt((PublicationSeries s) -> CADENCE_ORDER.indexOf(
                        s.getCadence() == null ? SeriesCadence.NONE : s.getCadence()))
                .thenComparing(PublicationSeries::getSeriesId,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<PublicationSeries> out = limit > 0 && ordered.size() > limit
                ? new ArrayList<>(ordered.subList(0, limit)) : ordered;
        if (!out.isEmpty()) {
            em.createQuery("SELECT DISTINCT s FROM PublicationSeries s LEFT JOIN FETCH s.descs "
                            + "WHERE s.id IN :ids", PublicationSeries.class)
                    .setParameter("ids", out.stream().map(PublicationSeries::getId).toList())
                    .getResultList();
        }
        return out;
    }

    /**
     * Persists a series, dropping desc rows that carry no defined content.
     *
     * The blank-desc filter is not tidiness. A desc row whose name is blank but
     * which carries a format string round-trips to nothing under the legacy
     * descDefined() rule, and the citation text on it is silently lost -- so such
     * a row must never reach the database in the first place.
     */
    public PublicationSeries create(PublicationSeries series) {
        removeBlankDescs(series);
        // saveEntity, not persist: whether a write is an insert or a merge is
        // decided in one place in the codebase, keyed off whether the entity has
        // an id. Deciding it again here is a second answer to a question with one
        // right one, and the case it gets wrong -- a detached-but-persisted entity
        // -- is one persist rejects outright.
        return saveEntity(series);
    }

    /**
     * Whether any issue of this series has ever been published.
     *
     * S-18 turns on it: a citation lives in whichever message field the series was
     * configured to use, so once an issue is out, moving the channel makes every
     * existing citation unfindable. RETIRED counts -- it was published and the
     * citations it wrote are still in the messages.
     */
    public boolean hasPublishedIssue(PublicationSeries series) {
        if (series == null || series.getId() == null) {
            return false;
        }
        Long n = em.createNamedQuery("PublicationIssue.countReleasedBySeries", Long.class)
                .setParameter("series", series)
                .setParameter("openStatus", IssueStatus.OPEN)
                .getSingleResult();
        return n != null && n > 0;
    }

    /**
     * How many issues each series has released, for a whole list, in ONE query.
     *
     * The admin list shows every series at once and each row needs the number, so
     * asking per row is a query per series on a screen that already renders in a
     * single pass. Keyed on seriesId rather than on the surrogate id because that
     * is what the value object carries out to the client.
     *
     * The predicate is the same one {@link #hasPublishedIssue} uses -- anything
     * that is not OPEN, which today is PUBLISHED and RETIRED. Written as one
     * expression in two places rather than two expressions, because a count that
     * disagreed with the boolean would show an editor a locked control beside a
     * count of zero.
     *
     * A series with no released issue is absent from the result of a GROUP BY, so
     * callers read it through {@link #publishedIssueCountOf} and get 0 rather than
     * null: the editor distinguishes "none yet" from "not asked", and a missing
     * key must not be able to read as the latter.
     */
    public Map<String, Integer> publishedIssueCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<Object[]> rows = em.createNamedQuery("PublicationIssue.countReleasedPerSeries", Object[].class)
                .setParameter("openStatus", IssueStatus.OPEN)
                .getResultList();
        for (Object[] row : rows) {
            out.put((String) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }

    /** One series' released-issue count, 0 where the grouped query returned no row. */
    public static int publishedIssueCountOf(Map<String, Integer> counts, String seriesId) {
        Integer n = counts == null ? null : counts.get(seriesId);
        return n == null ? 0 : n;
    }

    /** The same count for a single series, for the one-series reads. */
    public int publishedIssueCount(PublicationSeries series) {
        if (series == null || series.getId() == null) {
            return 0;
        }
        Long n = em.createNamedQuery("PublicationIssue.countReleasedBySeries", Long.class)
                .setParameter("series", series)
                .setParameter("openStatus", IssueStatus.OPEN)
                .getSingleResult();
        return n == null ? 0 : n.intValue();
    }

    public PublicationSeries update(PublicationSeries series) {
        removeBlankDescs(series);
        checkMessagePublicationImmutable(series);
        return saveEntity(series);
    }

    /**
     * The status transition, validated and audited.
     *
     * DRAFT is where a series is assembled, and it is reachable only before the
     * series has ever been active. Going back to it afterwards would put a
     * publication that has issues, citations and readers into the state whose
     * whole meaning is "not finished yet", and every rule that guards an ACTIVE
     * series -- the domain its cut-offs are read in, the report it renders --
     * would stop applying to something the public is still reading.
     *
     * Leaving ACTIVE takes a publication away from editors and readers and asks
     * why. Entering it -- the first activation, or a reinstatement -- restores
     * the state it was already in, and a confirm is enough.
     */
    public PublicationSeries transition(PublicationSeries series, SeriesStatus target, String reason,
                                        User actor) {
        SeriesStatus from = series.getStatus();
        if (from == target) {
            return series;
        }
        if (target == SeriesStatus.DRAFT) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_STATUS_TRANSITION",
                    "'" + series.getSeriesId() + "' is " + from + " and cannot go back to DRAFT: a series "
                            + "that has been active has issues and citations behind it, and DRAFT means "
                            + "the opposite. Retire it instead -- that is the state for a publication "
                            + "that has stopped.");
        }
        String trimmed = null;
        if (from == SeriesStatus.ACTIVE) {
            trimmed = IssueLifecycleService.requireReason(reason,
                    "moving '" + series.getSeriesId() + "' from " + from + " to " + target
                            + " changes what editors may cite and what the site lists; it must say why");
        } else if (reason != null && !reason.isBlank()) {
            trimmed = reason.trim();
        }

        series.setStatus(target);

        // The derived fields first, because this path never passes through a save
        // and would otherwise validate a row against a rule nothing here can put
        // right. A series that says it comes out every week but still carries
        // "next issue created by hand" fails S-8, and the status body carries no
        // fields at all -- so the remedy for a refusal here would be to go and save
        // the series unchanged, which is exactly what this does. It also keeps
        // "check rules" and the activate button answering the same question: the
        // report validates a candidate built through updateFromVo, which normalises.
        series.normaliseDerivedFields();

        // S-17: ACTIVE is what puts a series in the picker, so it may not be
        // incomplete. A DRAFT is allowed to be.
        List<SeriesValidator.FieldError> errors = SeriesValidator.validateForActivation(series, null);
        if (target == SeriesStatus.ACTIVE && !errors.isEmpty()) {
            // The entity is managed: a refusal that left ACTIVE on it would be
            // flushed by whatever the caller does next.
            series.setStatus(from);
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    errors.size() + " rule(s) fail: " + errors, errors);
        }

        PublicationSeries saved = update(series);
        audit.series(saved, actor, target == SeriesStatus.ACTIVE ? AuditAction.SERIES_ACTIVATED : AuditAction.SERIES_RETIRED,
                trimmed);
        return saved;
    }

    /**
     * Which model serves this series to the public.
     *
     * The single irreversible-feeling step of the cutover, and the reason it is
     * an action of its own rather than a field on a save: flipping authority
     * changes what every anonymous reader sees, and it must not be reachable by
     * an admin editing a name. Both directions are audited, and flipping BACK is
     * a first-class action -- a rollback nobody has rehearsed is not a rollback.
     *
     * The precondition is the shadow diff's own answer: two consecutive green
     * comparisons by release order, or a series that cannot be compared at all
     * and is exempt by rule. `force` exists because a precondition that cannot
     * be overridden gets worked around in the database instead, where nothing is
     * recorded -- so it is allowed, it demands a reason, and the audit entry says
     * it was forced.
     */
    public PublicationSeries setPublicAuthority(PublicationSeries series, PublicAuthority target,
                                                boolean force, String reason, User actor) {
        String trimmed = IssueLifecycleService.requireReason(reason,
                "changing which model serves '" + series.getSeriesId() + "' to the public must say why");

        PublicAuthority from = series.getPublicAuthority();
        if (target == PublicAuthority.NEW && from != PublicAuthority.NEW) {
            if (series.getStatus() != SeriesStatus.ACTIVE) {
                throw new IssueLifecycleService.TransitionRefusedException("SERIES_NOT_ACTIVE",
                        "'" + series.getSeriesId() + "' is " + series.getStatus() + ". A series serves "
                                + "the public only once it is active.");
            }
            ShadowDiffService.Readiness readiness =
                    ShadowDiffService.readinessOf(shadowDiff.forSeries(series.getSeriesId()), series);
            if (!readiness.ready() && !force) {
                throw new IssueLifecycleService.TransitionRefusedException("NOT_READY_FOR_CUTOVER",
                        "'" + series.getSeriesId() + "' has " + readiness.consecutiveGreen()
                                + " consecutive green comparison(s) of " + readiness.runs() + " run(s), "
                                + readiness.skipped() + " skipped. The precondition is "
                                + ShadowDiffService.REQUIRED_GREEN_RELEASES + ", or a series with no "
                                + "membership to compare. Pass force with a reason to flip anyway.");
            }
        }

        series.setPublicAuthority(target);
        PublicationSeries saved = update(series);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", from == null ? null : from.name());
        detail.put("to", target.name());
        detail.put("forced", force);
        audit.seriesAuthority(saved, actor, detail, trimmed);
        return saved;
    }

    /**
     * messagePublication may not change once a published issue exists.
     *
     * The message-to-publication relation lives ONLY as publication="<id>" inside
     * stored message HTML, and messagePublication decides which field that HTML
     * is written into: "publication" or "internalPublication". Flip it after
     * citations exist and every one of them becomes unfindable -- it is sitting
     * in the other field -- while re-applying the citation appends a second copy
     * rather than finding the first. There is no endpoint that removes a
     * citation, so nothing can undo it.
     *
     * Enforced here rather than in a resource because it is a property of the
     * series, and a rule that lives in one endpoint is a rule the next endpoint
     * will not have.
     */
    private void checkMessagePublicationImmutable(PublicationSeries series) {
        if (series.getId() == null) {
            return;
        }

        // COMMIT flush mode on purpose: the incoming series is usually the
        // MANAGED instance with the new value already on it, so an auto-flush
        // would write the change and then compare it with itself.
        List<MessagePublication> stored = em.createQuery(
                        "SELECT s.messagePublication FROM PublicationSeries s WHERE s.id = :id",
                        MessagePublication.class)
                .setParameter("id", series.getId())
                .setFlushMode(FlushModeType.COMMIT)
                .getResultList();

        if (stored.isEmpty() || Objects.equals(stored.get(0), series.getMessagePublication())) {
            return;
        }

        Long published = em.createQuery(
                        "SELECT COUNT(i) FROM PublicationIssue i "
                                + "WHERE i.series.id = :id AND i.status <> :open", Long.class)
                .setParameter("id", series.getId())
                .setParameter("open", IssueStatus.OPEN)
                .setFlushMode(FlushModeType.COMMIT)
                .getSingleResult();

        if (published > 0) {
            throw new IssueLifecycleService.TransitionRefusedException("MESSAGE_PUBLICATION_IMMUTABLE",
                    "messagePublication cannot change from " + stored.get(0) + " to "
                            + series.getMessagePublication() + ": " + published + " issue(s) of this "
                            + "series have been released, and any citation into them lives in the "
                            + "field the old value selected. Changing it makes those citations "
                            + "unfindable, and nothing can remove them.");
        }
    }

    private void removeBlankDescs(PublicationSeries series) {
        if (series.getDescs() != null) {
            series.getDescs().removeIf(d -> !d.descDefined());
        }
    }
}
