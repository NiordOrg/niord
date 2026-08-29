/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.core.publication.series.legacy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.CutoffDefault;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.IssueShape;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.report.FmReportService;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The legacy importer: plan it all, then write it all, or write none of it.
 *
 * TWO PHASES, NOT ONE PASS. plan() reads every legacy row, translates it, and
 * collects every problem it finds without touching the database. apply() writes
 * what plan() produced. The dry run is plan() alone, which is why it can promise
 * to leave the database byte-identical -- it does not write and roll back, it
 * never opens a write at all.
 *
 * THE WHOLE IMPORT FAILS RATHER THAN IMPORTING A PARTIAL ESTATE. Not a warning,
 * not a skip. A half-imported archive is worse than none: the rows that landed
 * look correct, nothing marks them as a partial set, and the only way to find out
 * what is missing is to compare 1,077 rows by hand. So plan() reports EVERY
 * offender rather than stopping at the first -- an admin fixing them one build at
 * a time is the failure mode a full report exists to prevent.
 *
 * A REFERENCED DOMAIN, REPORT OR MESSAGE SERIES THAT DOES NOT EXIST FAILS ITS ROW.
 * Silently nulling it is a hazard, not a feature: a series with no domain resolves
 * against the whole corpus instead of one, and nothing about the row says so. A
 * referenced CATEGORY is auto-created instead, and that asymmetry is deliberate --
 * a category is a label with a priority, and inventing one is recoverable by
 * editing it; inventing a scope is not.
 *
 * THE THREE ENTRY POINTS ARE NOT_SUPPORTED, AND THAT ANNOTATION IS LOAD-BEARING.
 * {@link BaseService} carries a class-level {@code @Transactional}, and
 * {@code jakarta.transaction.Transactional} is {@code @Inherited} -- so without a
 * method-level binding of its own, every public method here is wrapped by the
 * REQUIRED interceptor on the container's DEFAULT transaction budget. The
 * estate-scale work then commits happily through the hand-opened transaction
 * below, the ambient one the interceptor opened is reaped long before the method
 * returns, and its commit throws a CHECKED RollbackException on the way out --
 * which the generated subclass cannot declare, so it surfaces as
 * ArcUndeclaredThrowableException and the caller reads 500 over a database that
 * has the whole archive in it. That is the worst answer available: an operator
 * seeing it re-runs a cutover that worked. Measured on the deployed test backend
 * at 784s against a 240s default.
 *
 * NOT_SUPPORTED suppresses the inherited binding (a method-level binding of an
 * annotation type replaces the class-level one rather than adding to it), so the
 * only transaction any of these three runs in is the one it opens itself, with
 * the budget it chose.
 */
@ApplicationScoped
public class LegacyImportService extends BaseService {

    @Inject
    Logger log;

    @Inject
    PublicationCategoryService categoryService;

    @Inject
    DomainService domainService;

    @Inject
    FmReportService reportService;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    org.niord.core.publication.series.IssueAuditService audit;

    /** Everything one import run would write, plus everything wrong with it. */
    public static class Plan {

        private final LegacyImportReportVo report = new LegacyImportReportVo();
        private final List<PublicationSeries> series = new ArrayList<>();
        private final Map<String, PublicationIssue> issues = new LinkedHashMap<>();
        private final Map<String, List<IssueMember>> members = new LinkedHashMap<>();
        private final Map<String, PublicationCategory> categoriesToCreate = new LinkedHashMap<>();
        private final Map<String, String> categoryOfSeries = new LinkedHashMap<>();

        public LegacyImportReportVo report() {
            return report;
        }

        public List<PublicationSeries> series() {
            return series;
        }

        public Map<String, PublicationIssue> issues() {
            return issues;
        }

        public Map<String, List<IssueMember>> members() {
            return members;
        }

        /** categoryId -> the legacy row it was seen on, which carries the flags to copy. */
        public Map<String, PublicationCategory> categoriesToCreate() {
            return categoriesToCreate;
        }

        /** seriesId -> categoryId, resolved to an entity only at apply time. */
        public Map<String, String> categoryOfSeries() {
            return categoryOfSeries;
        }

        public boolean isClean() {
            return report.getProblems().isEmpty();
        }
    }

    /**
     * S18. Reads, translates, reports. Writes nothing, ever.
     *
     * ITS OWN TRANSACTION, for the same reason run() has one: planning reads the
     * whole legacy estate and takes as long as it takes. Inheriting the caller's
     * transaction meant inheriting the caller's BUDGET -- and the default is far
     * short of what a full estate needs, so the reaper aborted the transaction
     * mid-read and the commit afterwards failed with "the transaction is not
     * active". Read-only work should not be able to fail that way.
     */
    // Its own transaction and no other; see the class comment on transactions.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public LegacyImportReportVo dryRun() {
        return QuarkusTransaction.requiringNew()
                .timeout(IMPORT_TIMEOUT_SECONDS)
                .call(this::dryRunInTransaction);
    }

    private LegacyImportReportVo dryRunInTransaction() {
        Plan plan = plan();
        plan.report().setDryRun(true);
        plan.report().setWouldSucceed(plan.isClean());
        return plan.report();
    }

    /**
     * How long the import is allowed to hold its transaction, in seconds.
     *
     * The default is 240s, and the import needs about 250 -- so it died twice at
     * 240.2s and 240.8s, which reads like a data defect and is not one.
     *
     * IT CANNOT BE MADE MUCH FASTER, and the reason is structural. Every id in
     * this system comes from one shared single-row hibernate_sequence with an
     * increment of 1, so each of the ~65,000 rows costs a synchronous
     * SELECT ... FOR UPDATE plus an UPDATE before its insert can even be queued.
     * Measured at 5.4ms per allocation against a local database with no network
     * in between, and 3.4ms against the deployed one: 65,347 rows is 220s of
     * round trips and nothing else. JDBC insert batching does not help, because
     * the allocations are serial no matter how the inserts are grouped.
     *
     * The alternatives were weighed and rejected. Raising the sequence increment
     * changes id allocation for EVERY entity in the system, and production's
     * sequence is mid-count with the AngularJS frontend still reading from it.
     * Reserving a block and assigning ids by hand means bypassing @GeneratedValue
     * for one table. Splitting into several transactions gives up all-or-nothing,
     * and a half-imported archive is worse than none: the rows that landed look
     * correct and nothing marks them as partial.
     *
     * So the operation is simply long, and the budget says so. 30 minutes is far
     * more than the ~5 it needs, because the cost of a timeout is another cutover
     * window and the cost of a generous ceiling is nothing at all.
     */
    static final int IMPORT_TIMEOUT_SECONDS = 1800;

    /**
     * S19. Plans, and applies only if the plan is clean.
     *
     * Transactional so that a failure DURING apply -- a constraint nobody
     * predicted -- also leaves nothing behind. The clean check is the first
     * line of defence and the transaction is the second; neither is sufficient
     * alone, because plan() cannot foresee a database-level refusal.
     *
     * The transaction is opened by hand rather than by @Transactional because
     * that annotation cannot carry a timeout, and this one operation needs a
     * budget the rest of the application must not get.
     */
    // Its own transaction and no other; see the class comment on transactions.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public LegacyImportReportVo run() {
        return QuarkusTransaction.requiringNew()
                .timeout(IMPORT_TIMEOUT_SECONDS)
                .call(this::runInTransaction);
    }

    private LegacyImportReportVo runInTransaction() {
        long startedAt = System.nanoTime();

        Plan plan = plan();
        plan.report().setDryRun(false);

        if (!plan.isClean()) {
            plan.report().setWouldSucceed(false);
            log.warn("legacy import refused: {} problem(s); nothing written",
                    plan.report().getProblems().size());
            return plan.report();
        }

        long planned = System.nanoTime();
        apply(plan);
        long finished = System.nanoTime();

        int rows = plan.series().size() + plan.issues().size()
                + plan.members().values().stream().mapToInt(List::size).sum();

        // Logged as rows and seconds because the two failures before this one
        // were diagnosed by arithmetic on a stopwatch: 240.25s against a 240s
        // budget is a timeout, and 37s is not. Whoever runs this next should not
        // have to reconstruct that from a curl timing.
        log.info("legacy import wrote {} rows in {}s (plan {}s, write {}s) of a {}s budget",
                rows,
                seconds(finished - startedAt),
                seconds(planned - startedAt),
                seconds(finished - planned),
                IMPORT_TIMEOUT_SECONDS);

        plan.report().setWouldSucceed(true);
        return plan.report();
    }

    private static String seconds(long nanos) {
        return String.format("%.1f", nanos / 1_000_000_000.0);
    }

    /**
     * S20. Deletes exactly what the import wrote, and nothing else.
     *
     * The import is deliberately not idempotent -- it refuses rather than
     * merging -- which means a re-run is impossible until the previous attempt is
     * gone. Until this existed the only way to get there was hand-written DELETE
     * statements against the database, and the first time anybody would need them
     * is in a production cutover window with the clock running. That is the worst
     * possible moment to be composing SQL against a live archive.
     *
     * Scoped by importSource, so it can only ever touch rows this importer
     * created. A series an admin authored by hand has no importSource and is
     * invisible to this.
     *
     * REFUSED once the archive is public. Before the cutover flip an imported series is DRAFT
     * with publicAuthority = LEGACY and nobody can see it, so deleting it costs
     * nothing. After the flip, those same rows ARE the public list, and undoing
     * the import would withdraw published editions from under their readers. The
     * check is on the data rather than on a flag somebody remembers to set.
     */
    // Its own transaction and no other; see the class comment on transactions.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public UndoReport undo() {
        return QuarkusTransaction.requiringNew()
                .timeout(IMPORT_TIMEOUT_SECONDS)
                .call(this::undoInTransaction);
    }

    /** What the undo removed, or why it refused. */
    public record UndoReport(boolean deleted, int series, int issues, int members, int descs,
                             List<String> refusals) {
    }

    private UndoReport undoInTransaction() {
        List<PublicationSeries> imported = em.createQuery(
                        "SELECT s FROM PublicationSeries s WHERE s.importSource = :src",
                        PublicationSeries.class)
                .setParameter("src", importSource())
                .getResultList();

        if (imported.isEmpty()) {
            return new UndoReport(false, 0, 0, 0, 0,
                    List.of("nothing to undo: no series carries importSource '"
                            + importSource() + "'"));
        }

        // Every reason to refuse, not the first -- an admin clearing the way for a
        // re-run needs to know everything standing in it.
        List<String> refusals = new ArrayList<>();
        for (PublicationSeries series : imported) {
            if (series.getPublicAuthority() != PublicAuthority.LEGACY) {
                refusals.add(series.getSeriesId() + " has publicAuthority "
                        + series.getPublicAuthority() + ": its issues are being served to the "
                        + "public, and undoing the import would withdraw published editions");
            }
            if (series.getStatus() != SeriesStatus.DRAFT) {
                refusals.add(series.getSeriesId() + " is " + series.getStatus()
                        + ", not DRAFT: somebody has taken it out of review, so it is no longer "
                        + "just what the importer left behind");
            }
        }
        if (!refusals.isEmpty()) {
            return new UndoReport(false, 0, 0, 0, 0, refusals);
        }

        // Children first: IssueMember and the issues are both FK-bound to what is
        // about to go, and a bulk delete does not cascade the way a remove() does.
        List<Integer> seriesIds = imported.stream().map(PublicationSeries::getId).toList();

        // Detach everything before the bulk deletes.
        //
        // A bulk delete goes straight to the database and leaves the persistence
        // context holding entities for rows that no longer exist. Any one of them
        // that Hibernate still thinks is dirty produces an UPDATE at commit against
        // a deleted row, which fails as an optimistic-lock error naming a series the
        // undo had just read. That is exactly what happened once criteria became
        // non-null: the document had no value equality, so every series carrying one
        // looked dirty on every flush.
        //
        // The equality is fixed at the source. This stays because the hazard belongs
        // to the pattern rather than to that one bug -- bulk-deleting rows the
        // context is holding is unsafe whatever made them dirty.
        em.flush();
        em.clear();

        // Descs before their parents, for the same reason: a desc row owns the
        // foreign key, and a bulk delete does not cascade the way remove() does.
        int issueDescs = em.createQuery(
                        "DELETE FROM PublicationIssueDesc d WHERE d.entity IN "
                                + "(SELECT i FROM PublicationIssue i WHERE i.series.id IN :ids)")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        int seriesDescs = em.createQuery(
                        "DELETE FROM PublicationSeriesDesc d WHERE d.entity.id IN :ids")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        int members = em.createQuery(
                        "DELETE FROM IssueMember m WHERE m.issue IN "
                                + "(SELECT i FROM PublicationIssue i WHERE i.series.id IN :ids)")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        // Curation decisions taken on an imported issue since the import ran.
        // An override owns a foreign key to the issue, so leaving one behind
        // fails the issue delete below with a constraint violation naming a table
        // nobody was thinking about -- and this undo is the only escape hatch the
        // cutover window has, so it must not be the thing that breaks in it.
        int overrides = em.createQuery(
                        "DELETE FROM IssueOverride o WHERE o.issue IN "
                                + "(SELECT i FROM PublicationIssue i WHERE i.series.id IN :ids)")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        // The trail the import wrote, and anything written against these issues
        // and series since: an audit entry owns a foreign key to the row it
        // describes, and the rows are about to go.
        em.createQuery(
                        "DELETE FROM IssueAuditEntry a WHERE a.issue IN "
                                + "(SELECT i FROM PublicationIssue i WHERE i.series.id IN :ids)")
                .setParameter("ids", seriesIds)
                .executeUpdate();
        em.createQuery("DELETE FROM IssueAuditEntry a WHERE a.series.id IN :ids")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        int issues = em.createQuery(
                        "DELETE FROM PublicationIssue i WHERE i.series.id IN :ids")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        // The configured language list, which lives in its own collection table
        // with a foreign key back to the series. JPQL cannot address it -- it is
        // not an entity -- and a bulk delete of the owner does not cascade the way
        // remove() would, so the rows survive and the series delete below fails
        // on the constraint.
        em.createNativeQuery(
                        "DELETE FROM PublicationSeries_languages WHERE PublicationSeries_id IN (:ids)")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        int series = em.createQuery(
                        "DELETE FROM PublicationSeries s WHERE s.id IN :ids")
                .setParameter("ids", seriesIds)
                .executeUpdate();

        // An earlier importer built descs without attaching them, so they were
        // written with a null entity_id: unreachable from any series or issue, and
        // therefore invisible to every delete above. They are swept here because
        // undo means the database is back as it was, and a row nothing can reach
        // is still a row somebody has to explain later. Both tables belong to this
        // feature alone, so a null parent in them means exactly this and nothing else.
        int orphanedDescs = em.createQuery(
                        "DELETE FROM PublicationIssueDesc d WHERE d.entity IS NULL")
                .executeUpdate()
                + em.createQuery(
                        "DELETE FROM PublicationSeriesDesc d WHERE d.entity IS NULL")
                .executeUpdate();

        int descs = issueDescs + seriesDescs + orphanedDescs;

        log.warn("legacy import undone: {} series, {} issues, {} members, {} overrides, {} descs"
                        + " deleted (of which {} descs were orphaned by an earlier import)",
                series, issues, members, overrides, descs, orphanedDescs);
        return new UndoReport(true, series, issues, members, descs, List.of());
    }

    // ---------------------------------------------------------------- planning

    /** Builds the whole import in memory. Never writes. */
    public Plan plan() {
        return planFrom(legacyRows(PublicationMainType.TEMPLATE),
                        legacyRows(PublicationMainType.PUBLICATION));
    }

    /**
     * Plans a given set of rows.
     *
     * Split from plan() so the reading and the planning are separable. Production
     * always plans the WHOLE estate -- that is what makes "fail rather than import
     * a partial estate" meaningful -- but a test needs to plan a controlled pair
     * of rows, because a shared database carries other suites' fixtures and an
     * estate-wide plan over those can never come back clean.
     *
     * Package-visible rather than public: this is a seam for testing the write
     * path, not a second way for callers to import half an archive.
     */
    Plan planFrom(List<Publication> templates, List<Publication> publications) {
        Plan plan = new Plan();
        Date frozenAt = new Date();

        // ONE namespace for every seriesId, seeded with the ids the database
        // already holds. Three routines author into it -- templates, the ruled
        // shared series, the one-offs -- and until they shared this set each
        // checked only itself, so a name used by two of them passed planning and
        // failed against the unique key mid-write.
        Set<String> authored = seriesIdsAlreadyTaken();

        // Timed per phase and logged once. The plan runs inside a transaction
        // with a budget, and "the plan took too long" is only actionable when the
        // log says which phase did.
        long t0 = System.nanoTime();
        planAlreadyImported(plan, templates, publications);
        planCategories(plan, templates, publications);
        long t1 = System.nanoTime();
        Map<String, PublicationSeries> seriesByTemplate = planSeries(plan, templates, authored);
        long t2 = System.nanoTime();
        planOrphanSeries(plan, publications, seriesByTemplate, authored);
        long t3 = System.nanoTime();
        // After BOTH passes, because a ruling can name a series either pass
        // produced -- and for a long time this ran inside planSeries, so the two
        // orphan-grouped annex series it names never received one.
        applyDomainRulings(plan);
        // After the orphan pass, because a redirect destination may be a series
        // only that pass produces -- and before planIssues, which files by this map.
        resolveTemplateRedirects(plan, templates, seriesByTemplate);
        long t4 = System.nanoTime();
        planIssues(plan, publications, seriesByTemplate, frozenAt);
        long t5 = System.nanoTime();
        // After planIssues, because the kind of a cadence-less series is decided
        // by how many issues it turned out to have.
        applySeriesKinds(plan);

        // After the series exist and before the report is built: the document is
        // scoped by what the archive actually drew from, which is a fact about
        // the tagged messages rather than about any one series row.
        planSeriesCriteria(plan, templates, publications, seriesByTemplate);
        long t6 = System.nanoTime();
        assertSeriesIdsAreUnique(plan);
        log.info("plan over {} templates and {} publications: categories {} ms, series {} ms, "
                        + "orphans {} ms, rulings {} ms, issues {} ms, criteria {} ms, total {} ms",
                templates.size(), publications.size(),
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000,
                (t4 - t3) / 1_000_000, (t5 - t4) / 1_000_000, (t6 - t5) / 1_000_000,
                (t6 - t0) / 1_000_000);

        plan.report().setSeriesImported(plan.series().size());
        plan.report().setIssuesImported(plan.issues().size());
        // How many publications this import leaves unactivatable, counted rather
        // than assumed. A series with no domain has no timezone, so S-20 will
        // refuse it -- and the number belongs in the report an admin reads after
        // the run, not only in a log nobody opens.
        plan.report().setSeriesWithoutDomain(
                (int) plan.series().stream().filter(s -> s.getDomain() == null).count());
        return plan;
    }

    /**
     * Refuses to import something that is already imported.
     *
     * The importer is deliberately NOT idempotent -- it does not skip, merge or
     * update, because all three would quietly reconcile a re-run against an
     * archive somebody may have edited since. But without this check a re-run
     * fails on the publicId unique constraint PART WAY THROUGH, and what the
     * admin sees is a raw constraint violation rather than a sentence telling
     * them the estate is already there. The transaction still protects the data;
     * this protects the explanation.
     *
     * Reported as a problem rather than thrown, so a re-run answers the same way
     * every other refusal does: the full report, naming every row.
     */
    private void planAlreadyImported(Plan plan, List<Publication> templates,
                                     List<Publication> publications) {
        // Two set-based lookups, not one query per row. This ran a COUNT per
        // template and per publication -- ~3,800 round trips over a full estate --
        // and at the ~20 ms a round trip costs across a container boundary that
        // was 75 of a 76-second plan, most of the way to the transaction budget.
        List<Publication> seriesSources = concat(templates, publications.stream()
                .filter(x -> x.getTemplate() == null).toList());
        Set<String> takenSeries = idsPresent("SELECT s.legacyTemplateId FROM PublicationSeries s "
                + "WHERE s.legacyTemplateId IN :ids", seriesSources);
        for (Publication template : seriesSources) {
            if (takenSeries.contains(template.getPublicationId())) {
                problem(plan, "ALREADY_IMPORTED", template,
                        "a series already carries legacyTemplateId '" + template.getPublicationId()
                                + "'. The importer does not merge or update: re-running it against an "
                                + "archive somebody may have edited since would reconcile the two "
                                + "silently.");
            }
        }

        Set<String> takenIssues = idsPresent("SELECT i.publicId FROM PublicationIssue i "
                + "WHERE i.publicId IN :ids", publications);
        for (Publication legacy : publications) {
            if (takenIssues.contains(legacy.getPublicationId())) {
                problem(plan, "ALREADY_IMPORTED", legacy,
                        "an issue already carries publicId '" + legacy.getPublicationId() + "'.");
            }
        }
    }

    /** The ids among these rows that the query finds, fetched in bounded chunks. */
    private Set<String> idsPresent(String jpql, List<Publication> rows) {
        Set<String> out = new LinkedHashSet<>();
        List<String> ids = rows.stream().map(Publication::getPublicationId).toList();
        for (int from = 0; from < ids.size(); from += 500) {
            List<String> chunk = ids.subList(from, Math.min(from + 500, ids.size()));
            out.addAll(em.createQuery(jpql, String.class).setParameter("ids", chunk).getResultList());
        }
        return out;
    }

    private List<Publication> legacyRows(PublicationMainType mainType) {
        return em.createQuery(
                        "SELECT p FROM Publication p WHERE p.mainType = :mt ORDER BY p.publicationId",
                        Publication.class)
                .setParameter("mt", mainType)
                .getResultList();
    }

    /**
     * Categories are upserted by categoryId, and a missing one is created.
     *
     * priority and publish travel verbatim: two of the five live categories carry
     * publish = false, and their publications must stay off /public/v1.
     *
     * IN PRACTICE THIS NEVER CREATES ANYTHING, and that is structural rather than
     * lucky. A legacy publication holds its category by foreign key, so a category
     * it references necessarily exists as a row, and findByCategoryId resolves it.
     * categoriesCreated is therefore 0 against any real estate -- worth knowing
     * before somebody reads that 0 as a sign the step did not run.
     *
     * The branch stays because the acceptance names it and because the cost of
     * being wrong is asymmetric: a category conjured with the wrong publish flag
     * either hides publications from the public site or exposes ones that were
     * deliberately withheld, and neither announces itself.
     */
    private void planCategories(Plan plan, List<Publication> templates, List<Publication> publications) {
        Set<String> seen = new LinkedHashSet<>();
        for (Publication p : concat(templates, publications)) {
            if (p.getCategory() == null || p.getCategory().getCategoryId() == null) {
                continue;
            }
            String id = p.getCategory().getCategoryId();
            if (!seen.add(id)) {
                continue;
            }
            if (categoryService.findByCategoryId(id) == null) {
                plan.categoriesToCreate().put(id, p.getCategory());
            }
        }
        plan.report().setCategoriesSeen(seen.size());
        plan.report().setCategoriesCreated(plan.categoriesToCreate().size());
    }

    /**
     * Supplies the domain the template does not carry.
     *
     * Two of the twelve templates name no domain, and so did every publication
     * imported from them -- thirteen series with no domain, and therefore no
     * timezone, because the domain is the only source of one. A series with none
     * falls back to hardcoded UTC, which is precisely what "never use a timezone
     * that is not from the domain settings" rules out.
     *
     * It cannot be inferred from legacy, where a null domain means "applies
     * everywhere" -- that is a visibility answer to a timezone question. So it is
     * a ruling, recorded in LegacyTemplateRulings, and applied HERE rather than in
     * the pure translation because resolving a domainId to a Domain needs a
     * persistence context.
     *
     * Only fills a gap; a template that names its own domain keeps it. A ruling
     * that silently overrode real data would be a second source of truth.
     */
    /**
     * Every series the plan produced, template-derived or orphan-grouped.
     *
     * A SINGLE PASS OVER THE FINISHED SET, deliberately. This used to run inside
     * planSeries, which only ever sees series a TEMPLATE produced -- so the two
     * rulings naming orphan-grouped series (nm-annex-ncags, nm-annex-ice-service)
     * could not apply, and both came out domainless on every import.
     *
     * It was invisible because the deployed estate had those domains set BY HAND
     * on 2026-08-26, and the ruling was written down at the same time as the thing
     * that would reproduce them. Only an import from nothing could show that it
     * did not -- which is what the reseed rehearsal is for.
     */
    private void applyDomainRulings(Plan plan) {
        for (PublicationSeries series : plan.series()) {
            applyDomainRuling(series);
        }
    }

    private void applyDomainRuling(PublicationSeries series) {
        if (series.getDomain() != null) {
            return;
        }
        String domainId = LegacyTemplateRulings.domainFor(series.getSeriesId());
        if (domainId == null) {
            return;
        }
        Domain domain = domainService.findByDomainId(domainId);
        if (domain == null) {
            // NOT a problem, which would refuse the whole import. The ruling names
            // a domain this installation does not have -- a stale ruling, or an
            // installation that genuinely lacks it -- and the consequence is
            // exactly today's state: the series lands with no domain, stays DRAFT,
            // and S-20 refuses to activate it with a message that says why. That
            // is proportionate. Failing 1,077 issues over one absent domain is not.
            //
            // It is still counted, in seriesWithoutDomain, because a silent log is
            // indistinguishable from nothing having been checked.
            log.warn("import ruling files series '{}' under domain '{}', which does not exist here",
                    series.getSeriesId(), domainId);
            return;
        }
        series.setDomain(domain);
    }

    /**
     * One series per template -- except where a template is not a series.
     *
     * TWO PASSES, and the order is forced. A redirected template's editions are
     * filed under a series some OTHER template produces, so every real series has
     * to exist before any redirect can be resolved. A single pass would work only
     * while the destination happened to come first in the list.
     *
     * A redirected template produces NO series of its own. Its id still lands in
     * the returned map, pointing at the destination -- that map is what planIssues
     * files by, so the editions follow without it knowing anything about the
     * ruling.
     */
    private Map<String, PublicationSeries> planSeries(Plan plan, List<Publication> templates,
                                                      Set<String> authored) {
        Map<String, PublicationSeries> byTemplate = new LinkedHashMap<>();
        List<Publication> redirected = new ArrayList<>();

        for (Publication template : templates) {
            if (LegacyTemplateRulings.destinationFor(template.getPublicationId()) != null) {
                redirected.add(template);
                continue;
            }
            try {
                String seriesId = LegacySeriesTranslation.authorSeriesId(template, authored);
                PublicationSeries series =
                        LegacySeriesTranslation.translate(template, seriesId, importSource());
                LegacySeriesTranslation.assertReportSettingsAreComplete(
                        series, template.getPublicationId());
                assertReferencesResolve(plan, template, series);
                planCategoryOf(plan, template, series);

                plan.series().add(series);
                byTemplate.put(template.getPublicationId(), series);
            } catch (LegacySeriesTranslation.ImportRefusedException e) {
                problem(plan, e.getCode(), template, e.getMessage());
            } catch (RuntimeException e) {
                problem(plan, "SERIES_UNTRANSLATABLE", template, e.getMessage());
            }
        }

        return byTemplate;
    }

    /**
     * Files each redirected template's editions under the series that owns them.
     *
     * RUN AFTER planOrphanSeries, NOT INSIDE planSeries, and the difference is the
     * whole reason this is its own step. A destination is not necessarily produced
     * by the template pass: nm-annex-ncags is authored by the ORPHAN pass, from the
     * eleven template-less NCAGS annexes. Resolving inside planSeries could only
     * see template-derived series, so the ruling reported RULED_DESTINATION_MISSING
     * -- and because the import is all-or-nothing, that one problem refused the
     * entire import of 1,077 issues.
     *
     * It was invisible to every test here, because they all inspect plan.series(),
     * which is read at the END, after the orphan pass has added the destination.
     * theplanIsCleanOverTheCapturedEstate is the assertion that catches it, and it
     * is the one that was missing.
     */
    private void resolveTemplateRedirects(Plan plan, List<Publication> templates,
                                          Map<String, PublicationSeries> byTemplate) {
        Map<String, PublicationSeries> bySeriesId = new LinkedHashMap<>();
        for (PublicationSeries s : plan.series()) {
            bySeriesId.put(s.getSeriesId(), s);
        }

        for (Publication template : templates) {
            String destinationId = LegacyTemplateRulings.destinationFor(template.getPublicationId());
            if (destinationId == null) {
                continue;
            }
            PublicationSeries destination = bySeriesId.get(destinationId);
            if (destination == null) {
                // The ruling names a series this import did not produce at all.
                // Reported rather than silently dropped: dropping it would lose the
                // template's editions entirely, which is worse than the extra
                // series the ruling was trying to remove.
                problem(plan, "RULED_DESTINATION_MISSING", template,
                        "the ruling files this template's editions under series '" + destinationId
                                + "', which this import did not create");
                continue;
            }
            byTemplate.put(template.getPublicationId(), destination);
        }
    }

    /**
     * Records which category a series belongs to, without resolving it yet.
     *
     * PublicationSeries.category is NOT NULL, and a category the estate references
     * may not exist until apply() creates it -- so planning cannot hold an entity
     * here. It holds the ID, and apply() attaches the managed row once the
     * categories are in place.
     *
     * A template with NO category at all is refused rather than defaulted. The
     * category decides which section of the public page a publication appears in
     * and whether it appears at all (two of the five carry publish = false), so
     * picking one on the admin's behalf publishes something into a section
     * nobody chose.
     */
    private void planCategoryOf(Plan plan, Publication template, PublicationSeries series) {
        String categoryId = template.getCategory() == null
                ? null : template.getCategory().getCategoryId();

        if (categoryId == null || categoryId.isBlank()) {
            problem(plan, "CATEGORY_MISSING", template,
                    "the template has no publication category. The category decides which section of "
                            + "the public page this appears in, and whether it appears at all, so it "
                            + "cannot be chosen on the admin's behalf.");
            return;
        }
        plan.categoryOfSeries().put(series.getSeriesId(), categoryId);
    }

    /**
     * Files the template-less publications: they are eight series, not thirty-nine.
     *
     * 39 rows, and they are NOT 39 series. 11 NCAGS annexes are one series, 8
     * ice-service annexes are another, the 4 Danish List of Lights editions a
     * third, and the 10 double-week issues belong to the weekly series they were
     * hand-assembled out of. Only 6 are genuinely standalone. So the import
     * creates 3 shared series and 6 one-offs, and files 10 issues onto series
     * that already exist.
     *
     * Keyed into the same map as the templates, under the PUBLICATION's id, so
     * planIssues has one place to look regardless of how a row got there.
     */
    private void planOrphanSeries(Plan plan, List<Publication> publications,
                                  Map<String, PublicationSeries> seriesByTemplate,
                                  Set<String> authored) {
        List<Publication> orphans = publications.stream()
                .filter(p -> p.getTemplate() == null)
                .toList();
        if (orphans.isEmpty()) {
            return;
        }

        Map<String, List<Publication>> shared = new LinkedHashMap<>();
        List<Publication> ownSeries = new ArrayList<>();

        for (Publication orphan : orphans) {
            LegacyOrphanGrouping.Placement place = LegacyOrphanGrouping.placeOf(orphan);
            switch (place.kind()) {
                case EXISTING_SERIES -> {
                    // Filed onto a series translated from a template. If that
                    // template failed to translate the issue has nowhere to go,
                    // and planIssues reports it rather than inventing a home.
                    PublicationSeries destination = seriesByTemplate.get(place.seriesId());
                    if (destination != null) {
                        seriesByTemplate.put(orphan.getPublicationId(), destination);
                    }
                }
                case SHARED_SERIES ->
                        shared.computeIfAbsent(place.seriesId(), k -> new ArrayList<>()).add(orphan);
                case OWN_SERIES -> ownSeries.add(orphan);
            }
        }

        // Shared series first: their ids are ruled, so they claim their names
        // before the one-offs author around what is left.
        planSharedOrphanSeries(plan, shared, seriesByTemplate, authored);
        planStandaloneOrphanSeries(plan, ownSeries, seriesByTemplate, authored);
    }

    /** One series per group, configured from the group's newest member. */
    private void planSharedOrphanSeries(Plan plan, Map<String, List<Publication>> shared,
                                        Map<String, PublicationSeries> seriesByTemplate,
                                        Set<String> authored) {
        for (Map.Entry<String, List<Publication>> e : shared.entrySet()) {
            List<Publication> group = e.getValue();
            Publication source = LegacyOrphanGrouping.configurationSource(group);
            LegacyOrphanGrouping.Placement place = LegacyOrphanGrouping.placeOf(source);

            // A hand-authored id is not auto-suffixed. These series -- the NCAGS
            // and ice-service annexes and their siblings -- are named explicitly
            // rather than derived, so a clash means the naming and the estate
            // disagree, which is a sentence for a human and not a name for the
            // importer to invent.
            if (!authored.add(e.getKey())) {
                problem(plan, "SERIES_ID_COLLISION", source,
                        "the hand-authored seriesId '" + e.getKey() + "' is already taken by another "
                                + "series. This id was chosen explicitly rather than derived, so the "
                                + "importer will not rename it: either the chosen name or the "
                                + "colliding series has to change.");
                continue;
            }

            try {
                PublicationSeries series =
                        LegacySeriesTranslation.translate(source, e.getKey(), importSource());
                LegacySeriesTranslation.assertReportSettingsAreComplete(
                        series, source.getPublicationId());
                assertReferencesResolve(plan, source, series);

                // The ruling names the category for the annex series; otherwise it
                // is whatever the publications themselves carry.
                if (place.categoryId() != null) {
                    plan.categoryOfSeries().put(series.getSeriesId(), place.categoryId());
                } else {
                    planCategoryOf(plan, source, series);
                }

                plan.series().add(series);
                group.forEach(member ->
                        seriesByTemplate.put(member.getPublicationId(), series));
            } catch (LegacySeriesTranslation.ImportRefusedException ex) {
                problem(plan, ex.getCode(), source, ex.getMessage());
            } catch (RuntimeException ex) {
                problem(plan, "ORPHAN_SERIES_UNTRANSLATABLE", source, ex.getMessage());
            }
        }
    }

    /** The six that really are one-offs, each with its own authored id. */
    private void planStandaloneOrphanSeries(Plan plan, List<Publication> standalone,
                                            Map<String, PublicationSeries> seriesByTemplate,
                                            Set<String> authored) {
        if (standalone.isEmpty()) {
            return;
        }
        Map<String, String> ids =
                LegacySeriesTranslation.authorOrphanSeriesIds(standalone, authored);
        authored.addAll(ids.values());

        for (Publication orphan : standalone) {
            try {
                PublicationSeries series = LegacySeriesTranslation.translate(
                        orphan, ids.get(orphan.getPublicationId()), importSource());
                LegacySeriesTranslation.assertReportSettingsAreComplete(
                        series, orphan.getPublicationId());
                assertReferencesResolve(plan, orphan, series);
                planCategoryOf(plan, orphan, series);

                plan.series().add(series);
                seriesByTemplate.put(orphan.getPublicationId(), series);
            } catch (LegacySeriesTranslation.ImportRefusedException e) {
                problem(plan, e.getCode(), orphan, e.getMessage());
            } catch (RuntimeException e) {
                problem(plan, "ORPHAN_SERIES_UNTRANSLATABLE", orphan, e.getMessage());
            }
        }
    }

    /**
     * Give every query-backed series a criteria document.
     *
     * Without one the series cannot be activated (S-1), so the shadow diff skips
     * all of its releases and no green week can ever be recorded. The document is
     * a PROPOSAL: the series lands DRAFT so an admin reviews it, and the shadow
     * diff is what checks it against the frozen members release by release.
     *
     * A series with no evidence gets NO document rather than an unscoped one. An
     * unscoped document resolves over every message in the system, and an issue
     * that silently contains everything is worse than a series that refuses to
     * activate until somebody looks at it.
     */
    private void planSeriesCriteria(Plan plan, List<Publication> templates,
                                    List<Publication> publications,
                                    Map<String, PublicationSeries> seriesByTemplate) {

        Map<String, Set<String>> scopeByTemplate = messageSeriesByTemplate(publications);
        Map<String, Publication> templateById = new LinkedHashMap<>();
        for (Publication t : concat(templates, publications)) {
            templateById.putIfAbsent(t.getPublicationId(), t);
        }

        int written = 0;
        int unscoped = 0;
        for (Map.Entry<String, PublicationSeries> e : seriesByTemplate.entrySet()) {
            PublicationSeries series = e.getValue();
            if (series.getContentMode() != ContentMode.GENERATED_FROM_QUERY) {
                // Only a query-backed series has criteria at all (S-1 refuses one
                // on anything else).
                continue;
            }

            Publication template = templateById.get(e.getKey());
            if (template == null) {
                continue;
            }

            IssueCriteriaVo doc;
            try {
                doc = LegacyCriteriaTranslation.translate(
                        LegacyFilterTranslator.translate(template.getMessageTagFilter()),
                        scopeByTemplate.getOrDefault(e.getKey(), Set.of()));
            } catch (RuntimeException ex) {
                problem(plan, "SERIES_CRITERIA_UNTRANSLATABLE", template, ex.getMessage());
                continue;
            }

            if (doc == null) {
                // Named rather than silent: the series will refuse to activate, and
                // an admin reading the report should learn it here rather than from
                // a validation error weeks later.
                unscoped++;
                log.info("no criteria for series {}: the archive shows no message series to "
                        + "scope it by, so it will not activate until one is authored",
                        series.getSeriesId());
                continue;
            }
            series.setCriteria(doc);
            written++;
        }

        plan.report().setSeriesCriteriaWritten(written);
        plan.report().setSeriesWithoutCriteria(unscoped);
    }

    /**
     * The message series each template's archive actually drew from.
     *
     * ONE query over the whole estate. Read off the tagged messages because the
     * scope is nowhere else: the legacy filter does not state it, the imported
     * series carries no domain, and legacy scoped implicitly by which recorder
     * wrote the tag. What the tag contained is the only surviving evidence.
     *
     * Keyed on the TEMPLATE where there is one and on the publication itself
     * where there is not, matching how seriesByTemplate is keyed.
     */
    private Map<String, Set<String>> messageSeriesByTemplate(List<Publication> publications) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (publications.isEmpty()) {
            return out;
        }

        em.createQuery(
                        "SELECT p.publicationId, t.publicationId, m.messageSeries.seriesId "
                                + "FROM Publication p LEFT JOIN p.template t "
                                + "JOIN p.messageTag tag JOIN tag.messages m "
                                + "WHERE m.messageSeries IS NOT NULL",
                        Object[].class)
                .getResultStream()
                .forEach(r -> {
                    String key = r[1] != null ? (String) r[1] : (String) r[0];
                    out.computeIfAbsent(key, k -> new LinkedHashSet<>()).add((String) r[2]);
                });
        return out;
    }

    private void planIssues(Plan plan, List<Publication> publications,
                            Map<String, PublicationSeries> seriesByTemplate, Date frozenAt) {
        Map<String, List<Publication>> byTag = MemberSnapshotImport.byTagName(publications);
        Map<Integer, List<MemberSnapshotImport.MemberFacts>> factsByTag =
                memberFactsByTag(publications);
        Map<String, List<Publication>> chains = chainsBySeries(publications, seriesByTemplate);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> byCutoffSource = new LinkedHashMap<>();

        for (List<Publication> chain : chains.values()) {
            // Carried forward rather than looked up: at iteration i the previous
            // issue has already been translated AND had its cut-off recovered, so
            // its effective close is known and is what this period opens at.
            Date previousCutoff = null;
            // Siblings -- rows released at the same instant, a withdrawal and its
            // replacement -- describe ONE period. They open where the row before
            // the pair closed, and the chain moves on past the pair as a whole.
            Date siblingsOpenAt = null;
            Date siblingsCloseAt = null;
            Date previousRelease = null;

            for (int i = 0; i < chain.size(); i++) {
                Publication legacy = chain.get(i);
                boolean sibling = isSibling(previousRelease, legacy.getPublishDateFrom());
                if (!sibling) {
                    siblingsOpenAt = previousCutoff;
                    siblingsCloseAt = null;
                }
                previousRelease = legacy.getPublishDateFrom();
                try {
                    // repoPath is where the bytes live, and it is NOT NULL on the
                    // issue. Every one of the 1,077 production rows has one.
                    // Fabricating a path for a row that does not would point the
                    // archive at nothing and record that as fact, so this refuses
                    // instead -- the same answer as every other missing reference.
                    if (legacy.getRepoPath() == null || legacy.getRepoPath().isBlank()) {
                        problem(plan, "REPO_PATH_MISSING", legacy,
                                "the publication has no repoPath, so there is no location to carry "
                                        + "verbatim (R6) and nothing for filePath to be contained by.");
                        continue;
                    }

                    PublicationSeries series = legacy.getTemplate() == null
                            ? null : seriesByTemplate.get(legacy.getTemplate().getPublicationId());

                    if (series == null) {
                        // A template-less publication is its own one-off series
                        // (the template-less publications are eight series). PublicationIssue.series is NOT NULL, so
                        // it must belong to something, and one series per orphan
                        // infers nothing about which of them are really the same
                        // publication -- that is a judgement for the DRAFT review.
                        series = seriesByTemplate.get(legacy.getPublicationId());
                    }
                    if (series == null) {
                        problem(plan, "NO_SERIES_FOR_PUBLICATION", legacy,
                                "no series was translated for this publication and none could be "
                                        + "authored from it either.");
                        continue;
                    }

                    // Where the previous issue actually closed is when this one's
                    // content period opened; the chain is already ordered. A sibling
                    // opens where the row BEFORE its pair closed, not where its twin did.
                    PublicationIssue issue = LegacyIssueTranslation.translate(
                            legacy, series, frozenAt, sibling ? siblingsOpenAt : previousCutoff);

                    CutoffRecovery.Recovered cutoff = recoverCutoff(issue, legacy, series, chain, i);
                    // A released row that ends up with no cut-off is carried
                    // through and NAMED. The alternative to naming it is not
                    // naming it: this import runs once, in one window, and a row
                    // nobody was told about is a row nobody goes back to. It is a
                    // note rather than a problem because refusing the whole estate
                    // over one undated archive row is not proportionate.
                    if (cutoff.cutoff() == null && issue.getStatus() != IssueStatus.OPEN) {
                        note(plan, "CUTOFF_NOT_RECOVERABLE", legacy,
                                "this row carries neither a start date nor a credible release stamp, "
                                        + "so its content period has no end that can be believed. It "
                                        + "imports without one rather than with an invented date; set "
                                        + "the cut-off by hand if the archive needs it.");
                    }
                    issue.setCutoffStampedAt(cutoff.cutoff());
                    issue.setCutoffSource(cutoff.source());
                    issue.setCutoffReconstructed(cutoff.reconstructed());
                    // The release action's own moment, kept apart from the cut-off and
                    // only where a stage actually witnessed it. A nominal close or a
                    // window boundary is not a moment anybody pressed publish.
                    issue.setPublishedAt(publishedAtOf(cutoff, legacy, issue, series, chain, i));

                    List<MemberSnapshotImport.MemberFacts> facts =
                            legacy.getMessageTag() == null || legacy.getMessageTag().getId() == null
                                    ? List.of()
                                    : factsByTag.getOrDefault(legacy.getMessageTag().getId(), List.of());
                    assertMembersCanBeFrozen(plan, legacy, facts);
                    List<IssueMember> members = MemberSnapshotImport.apply(
                            issue, legacy, facts, byTag);

                    plan.issues().put(legacy.getPublicationId(), issue);
                    plan.members().put(legacy.getPublicationId(), members);

                    // Only advance on a real close. A row that produced none must
                    // not reset the chain to null and orphan the next interval --
                    // the last known close is still the better answer. Within a
                    // sibling pair the published row's close wins over the withdrawn
                    // one's, so the issue after the pair chains from the replacement.
                    if (issue.effectiveCutoff() != null) {
                        if (siblingsCloseAt == null || issue.getStatus() == IssueStatus.PUBLISHED) {
                            siblingsCloseAt = issue.effectiveCutoff();
                        }
                        previousCutoff = siblingsCloseAt;
                    }

                    byStatus.merge(issue.getStatus().name(), 1, Integer::sum);
                    byCutoffSource.merge(cutoff.source(), 1, Integer::sum);
                } catch (RuntimeException e) {
                    problem(plan, "ISSUE_UNTRANSLATABLE", legacy, e.getMessage());
                }
            }
        }

        closeSupersededIssues(plan, publications);
        applyNominalSchedules(plan);
        numberIssues(plan);

        plan.report().setIssuesByStatus(byStatus);
        plan.report().setIssuesByCutoffSource(byCutoffSource);
    }

    /**
     * Gives every imported series the nominal schedule S-5 and S-7 require.
     *
     * The legacy model had no schedule to copy, so a translated series arrived
     * without one -- and a series with a cadence and no weekday or time cannot be
     * activated. Every imported weekly series was therefore correct in all other
     * respects and refused, on two fields nobody had the data to fill in by hand.
     *
     * The data was in the archive: a series that released weekly for years has
     * stated its schedule several hundred times, and the recovered cut-offs are
     * that record. Derived AFTER the issues are built, because that is when the
     * record exists.
     *
     * Only what each shape is allowed to carry. S-5 gives a weekday to a WEEKLY
     * cadence and to nothing else; S-4 gives a first interval to a tiling series
     * and to nothing else, so an in-force series is left without one on purpose.
     * Nothing is overwritten -- a value already set is a decision.
     */
    /**
     * What KIND each series is, decided once from the estate being imported.
     *
     * THE ISSUE COUNT IS USED HERE AND NOWHERE ELSE. "cadence = NONE and at
     * most one issue" is a rule about an archive that is already written, not a
     * rule the running system applies -- so it is spent here, on data whose
     * shape is known, and the answer is stored. Recomputing it later would make
     * a publication change kind underneath whoever added the second issue.
     *
     * The distinction it draws is real and was previously invisible: eleven
     * NCAGS editions, eight ice-service notices and four editions of Dansk
     * Fyrliste have no cadence and are unmistakably series, while five other
     * publications have no cadence because they were published once and stopped.
     */
    private void applySeriesKinds(Plan plan) {
        Map<PublicationSeries, Integer> issueCounts = new IdentityHashMap<>();
        for (PublicationIssue issue : plan.issues().values()) {
            if (issue.getSeries() != null) {
                issueCounts.merge(issue.getSeries(), 1, Integer::sum);
            }
        }

        int oneOffs = 0;
        int unscheduled = 0;
        for (PublicationSeries series : plan.series()) {
            if (series.getCadence() != null && series.getCadence() != SeriesCadence.NONE) {
                series.setKind(SeriesKind.SCHEDULED);
                continue;
            }
            if (issueCounts.getOrDefault(series, 0) > 1) {
                series.setKind(SeriesKind.UNSCHEDULED);
                unscheduled++;
            } else {
                series.setKind(SeriesKind.ONE_OFF);
                oneOffs++;
            }
        }
        log.info("series kinds: {} one-off, {} unscheduled, {} scheduled",
                oneOffs, unscheduled, plan.series().size() - oneOffs - unscheduled);
    }

    /**
     * Numbers every imported issue the way a natively created one is numbered.
     *
     * LAST, because it reads the cut-off and the cut-off is the last thing to
     * settle: the recovery cascade runs per row, the chain walk decides where each
     * period opened, and the supersession pass closes what a successor took over.
     * An issue numbered before any of that is numbered from a bound that later
     * moved.
     *
     * THROUGH THE SHAPE THE NATIVE PATH USES, not a copy of it. The week comes
     * from the ISO week the cut-off falls in, the year from whichever year the
     * series is numbered by, and a window that swallowed more than one period
     * carries the pair -- and those three rules had exactly one implementation
     * before this and must keep having one. Imported issues previously carried a
     * year and no week at all, which reads on the wire as an issue that belongs to
     * a year but to none of its weeks.
     *
     * NAMES ARE NOT TOUCHED. An imported name is what the archive called the
     * edition, and it is cited by that name in message HTML that is already
     * public; re-deriving over it would rewrite the archive to match a pattern
     * nobody applied at the time.
     *
     * An issue with no cut-off -- an OPEN row that was never released, or an
     * archived one whose release instant could not be believed -- is left
     * unnumbered, which is the same answer the native path gives.
     */
    private void numberIssues(Plan plan) {
        for (PublicationIssue issue : plan.issues().values()) {
            IssueShape.applyNumbers(issue, issue.getSeries());
        }
    }

    private void applyNominalSchedules(Plan plan) {
        for (PublicationSeries series : plan.series()) {
            List<PublicationIssue> issues = plan.issues().values().stream()
                    .filter(i -> i.getSeries() == series)
                    .toList();
            if (issues.isEmpty()) {
                continue;
            }

            ZoneId zone = series.cutoffZone();
            List<Date> cutoffs = issues.stream().map(PublicationIssue::effectiveCutoff).toList();

            boolean hasCadence = series.getCadence() != null
                    && series.getCadence() != SeriesCadence.NONE;
            if (hasCadence && series.getNominalCutoffTime() == null) {
                series.setNominalCutoffTime(NominalSchedule.timeOfDayOf(cutoffs, zone));
            }
            if (series.getCadence() == SeriesCadence.WEEKLY
                    && series.getNominalCutoffDay() == null) {
                series.setNominalCutoffDay(NominalSchedule.weekdayOf(cutoffs, zone));
            }
            boolean monthly = series.getCadence() == SeriesCadence.MONTHLY
                    || series.getCadence() == SeriesCadence.YEARLY;
            if (monthly && series.getNominalCutoffDayOfMonth() == null) {
                series.setNominalCutoffDayOfMonth(NominalSchedule.dayOfMonthOf(cutoffs, zone));
            }
            if (series.getCadence() == SeriesCadence.YEARLY
                    && series.getNominalCutoffMonth() == null) {
                series.setNominalCutoffMonth(NominalSchedule.monthOf(cutoffs, zone));
            }
            if (series.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL
                    && series.getFirstIssueStartsAt() == null) {
                series.setFirstIssueStartsAt(NominalSchedule.firstIntervalStartOf(
                        issues.stream().map(PublicationIssue::getIntervalFrom).toList()));
            }
        }
    }

    /**
     * Closes every imported issue that a later one supersedes.
     *
     * I-18 says one series serves one current issue. The legacy model had no
     * such rule -- each publication stood alone, so nothing ever needed to end,
     * and 15 rows across the three grouped series carry no publishDateTo at all.
     * Standalone that was harmless. Grouped, it is four Danish List of Lights
     * editions all claiming to be current, which is the archive forking in
     * public. The grouping ruling is what made it visible; the data was always
     * like this.
     *
     * An issue closes where its successor opens. That is the only end date
     * available that is not invented: it comes from a row that exists and says
     * when it took over. A LEGACY publishDateTo is never overwritten -- the 2017
     * NCAGS edition really did end on 23 December and the nine-day gap before
     * 2018 is real data, not an artefact to tidy away (ruling, Rasmus
     * 2026-08-24).
     *
     * Ordered by publicFrom, then by the legacy row's updated stamp, then by
     * publicationId. The middle key is load-bearing rather than decorative:
     * three NCAGS rows share 2023-01-04 and two share 2026-01-07, and the one
     * that should stay open is the one legacy marks ACTIVE -- which is also the
     * most recently updated, and is NOT the one publicationId order would pick.
     *
     * Applied to every imported series rather than only the grouped ones,
     * because the invariant is about series with two current issues and not
     * about how a series came to exist. Where a series already has exactly one
     * open issue this changes nothing.
     */
    private void closeSupersededIssues(Plan plan, List<Publication> publications) {
        Map<String, Publication> legacyById = new LinkedHashMap<>();
        for (Publication p : publications) {
            legacyById.put(p.getPublicationId(), p);
        }

        Map<String, List<String>> bySeries = new LinkedHashMap<>();
        for (Map.Entry<String, PublicationIssue> e : plan.issues().entrySet()) {
            PublicationSeries series = e.getValue().getSeries();
            if (series != null) {
                bySeries.computeIfAbsent(series.getSeriesId(), k -> new ArrayList<>())
                        .add(e.getKey());
            }
        }

        Comparator<String> order = Comparator
                .comparing((String id) -> stamp(plan.issues().get(id).getPublicFrom()))
                .thenComparing(id -> stamp(legacyById.containsKey(id)
                        ? legacyById.get(id).getUpdated() : null))
                .thenComparing(id -> id);

        int closed = 0;
        for (List<String> group : bySeries.values()) {
            group.sort(order);

            for (int i = 0; i < group.size() - 1; i++) {
                PublicationIssue issue = plan.issues().get(group.get(i));
                if (issue.getPublicTo() != null) {
                    continue;
                }
                Date successorOpens = plan.issues().get(group.get(i + 1)).getPublicFrom();

                // Only a successor that actually states when it opened can close
                // anything, and it cannot close a window before that window began.
                if (successorOpens == null || issue.getPublicFrom() == null
                        || successorOpens.before(issue.getPublicFrom())) {
                    continue;
                }
                issue.setPublicTo(successorOpens);
                closed++;
            }
        }

        if (closed > 0) {
            log.info("closed {} superseded issue(s) at their successor's start; "
                    + "legacy end dates left untouched", closed);
        }
    }

    /** Null-safe key for ordering. A missing date sorts first, never last. */
    private static long stamp(Date d) {
        return d == null ? Long.MIN_VALUE : d.getTime();
    }

    /**
     * The cut-off of one issue, by the shape of its series.
     *
     * A cut-off is the end of the content period -- a fact about the content --
     * and the release action is a separate fact recorded as publishedAt. For a
     * weekly release the two are minutes apart, so the release stamp is the
     * cut-off whenever it is credible as one: after the period opened and no
     * more than a day past its nominal close. Outside that it is an edit, and the
     * nominal close is the honest answer. For a yearly issue the two can be a
     * year apart, and the public window -- which for every yearly row in the
     * estate is 1 January to 31 December -- is the content period: an in-force
     * list is decided on the day it takes effect, an accumulated list where its
     * window closes.
     *
     * RETIRED counts as released: it was published and then withdrawn, so a
     * release instant exists. OPEN is the one that never had one.
     */
    // Package-visible so the branch decisions can be asserted on one row. Reaching
    // them through plan() means reading the whole legacy estate to ask a question
    // about a single archive entry, and the estate a shared database happens to
    // hold is not a fixture.
    static CutoffRecovery.Recovered recoverCutoff(PublicationIssue issue, Publication legacy,
                                                          PublicationSeries series,
                                                          List<Publication> chain, int i) {
        boolean released = issue.getStatus() != IssueStatus.OPEN;
        if (!released) {
            return CutoffRecovery.recover(legacy, null, null, false, CutoffRecovery.Bounds.NONE);
        }

        if (LegacyIssueTranslation.isYearly(legacy, series)) {
            boolean inForce = series != null
                    && series.getTimeRelation() == org.niord.core.publication.series.resolve.TimeRelation.IN_FORCE_AT_CUTOFF;
            // An in-force annual is decided at the END of a day, because the
            // changeover is a day's work -- and it is the LATER of the day its
            // window opens and the day it was released, because the window is
            // opened partway through that sitting on some editions and named
            // nominally at the turn of the year on others, while the sitting
            // happens weeks afterwards. An accumulated one is decided at the
            // instant its window closes, which is a boundary rather than a
            // working day.
            //
            // The release stamp passed here is the one this import already
            // believes enough to record as the publication moment. Deciding the
            // day off a stamp the row would not be credited with releasing at
            // would be two credibility rules for one fact.
            return inForce
                    ? CutoffRecovery.forAnnualInForce(issue.getPublicFrom(),
                            annualInForceRelease(legacy, issue, CutoffRecovery.replacedAt(
                                    chain, i, issue.getPublicFrom(), issue.getPublicTo())),
                            series.cutoffZone())
                    : CutoffRecovery.fromPublicWindow(issue.getIntervalTo());
        }

        if (LegacyIssueTranslation.isCadenced(legacy, series)) {
            // The tiling and the weekly in-force shapes alike: the release stamp
            // must sit within a day after the nominal close, else the close itself.
            Date nominalClose = legacy.getPublishDateFrom();

            // A HALF-DATED ROW HAS NOTHING TO CHECK A STAMP AGAINST, so it gets
            // no stamp at all.
            //
            // The believability bounds are built from the period's open and its
            // nominal close. With neither -- an archived row that carries no start
            // date, on a series whose issues have no lower bound -- every bound is
            // null and the test degenerates to "yes": the cascade then adopts
            // whatever timestamp it finds first, and one archived row imported a
            // cut-off two years and three months before the publication existed,
            // which made it the oldest issue of its archive forever afterwards.
            // This import is one-way, so an invented date is not a display bug.
            if (nominalClose == null && issue.getIntervalFrom() == null) {
                return new CutoffRecovery.Recovered(null, CutoffRecovery.MANUAL, true);
            }

            return CutoffRecovery.recoverOrNominal(
                    legacy, CutoffRecovery.nextTagCreated(chain, i), null, true,
                    CutoffRecovery.Bounds.release(issue.getIntervalFrom(), nominalClose,
                            CutoffRecovery.RELEASE_LEAD_MS, CutoffRecovery.RELEASE_SLACK_MS),
                    nominalClose);
        }

        // A one-off: the interval is its window, and the original bounds apply.
        return CutoffRecovery.recover(legacy, CutoffRecovery.nextTagCreated(chain, i), null, true,
                new CutoffRecovery.Bounds(issue.getIntervalFrom(), issue.getIntervalTo()));
    }

    /**
     * When the release action ran, where the row says so credibly.
     *
     * A stage that witnessed the release is the answer. Where the cut-off came
     * from the calendar instead, the row's last-write stamp is accepted only if
     * it falls inside the issue's own public window.
     *
     * AN ANNUAL IN-FORCE EDITION IS THE EXCEPTION AND IT IS NOT A SPECIAL CASE
     * OF CREDIBILITY. Its cut-off is the END of a day rather than a stamp, so
     * reading the release moment off the cut-off would report every such edition
     * as released at 23:59:59.999 -- a time nobody worked at, on a row whose
     * actual stamp is sitting right there. The stamp is the answer, and it is the
     * same stamp that decided which day the cut-off falls on.
     */
    private static Date publishedAtOf(CutoffRecovery.Recovered cutoff, Publication legacy,
                                      PublicationIssue issue, PublicationSeries series,
                                      List<Publication> chain, int i) {
        if (issue.getStatus() == IssueStatus.OPEN) {
            return null;
        }
        if (series != null
                && CutoffDefault.isAnnualInForce(series.getCadence(), series.getTimeRelation())) {
            return annualInForceRelease(legacy, issue, CutoffRecovery.replacedAt(
                    chain, i, issue.getPublicFrom(), issue.getPublicTo()));
        }
        if (CutoffRecovery.witnessesTheRelease(cutoff)) {
            return cutoff.cutoff();
        }
        return releaseStampInWindow(legacy, issue);
    }

    /**
     * The row's last-write stamp, where it is credible as this issue's release.
     *
     * Credible means inside the issue's own public window: the span during which
     * this edition was the current one. A write before the window opened belongs
     * to whatever the row was before it became this edition, and one after it
     * closed is an edit made in some other year.
     *
     * The shapes whose cut-off is an instant use this. An annual in-force edition
     * has a different question to answer and {@link #annualInForceRelease} is
     * where it is answered.
     */
    private static Date releaseStampInWindow(Publication legacy, PublicationIssue issue) {
        Date updated = legacy.getUpdated();
        Date from = issue.getPublicFrom();
        // An open end date is not "still current": the derived content end -- the
        // last day of an accumulated year -- is the latest a release could be.
        Date to = issue.getPublicTo() != null ? issue.getPublicTo() : issue.getIntervalTo();
        if (updated == null || from == null || updated.before(from)) {
            return null;
        }
        return to == null || !updated.after(to) ? updated : null;
    }

    /**
     * When an ANNUAL IN-FORCE edition was released, from the two stamps a row of
     * this shape can carry.
     *
     * ONE RULE, TWO READERS. It is what the publication moment is recorded from,
     * and it is what decides WHICH DAY the cut-off falls on. Those two must not
     * disagree: an edition dated by a stamp it is not credited with being
     * released at would be claiming a day nothing witnessed.
     *
     * TWO CANDIDATES. The row's last-write time, and the row's own tag creation
     * -- the moment its member list was assembled. Either can be the one that
     * survives: the 2025 firing edition was written last (12:12) after its tag
     * was built (12:11), while the second 2022 edition's row was next written a
     * year later and only its tag creation falls inside its own window. The LATER
     * credible one wins, because the release completes with its last credible
     * write.
     *
     * CREDIBLE MEANS THREE THINGS.
     *
     * Not before the window opened. A stamp older than the edition belongs to
     * whatever the row was beforehand -- typically a clone of the year before,
     * and three rows in the archive carry a tag made a full year early.
     *
     * Not after the ceiling. That is the window's own end where the row states
     * one; where it does not, the moment this edition was REPLACED stands in for
     * it. Falling back to the content interval instead would be no ceiling at
     * all for this shape: an in-force edition's interval ends AT its window
     * open, so the test would collapse to "the stamp equals the window open".
     *
     * And before the edition was replaced, where it was -- see
     * {@link CutoffRecovery#replacedAt}, which is deliberately narrow: only a
     * re-edition taking over DURING this window replaces it, never next year's
     * row. The changeover is one sitting: it assembles the incoming edition and
     * deactivates the outgoing one minutes later, so a write at or after the
     * incoming tag's creation is this edition's withdrawal rather than its
     * release. Measured on the 2022 firing pair -- incoming tag 14:46:50,
     * outgoing row last written 14:52:17 -- and believing that write dated the
     * outgoing edition to the day its replacement went out, which sorted the
     * pair backwards.
     *
     * Null when neither candidate is credible: nothing witnessed the release, and
     * the cut-off falls back to the day the window opened.
     */
    // Package-visible so the three credibility clauses can be asserted directly.
    // Reaching them through plan() means reading the whole estate to ask a
    // question about one row's two timestamps.
    static Date annualInForceRelease(Publication legacy, PublicationIssue issue, Date replacedAt) {
        Date from = issue.getPublicFrom();
        if (from == null) {
            return null;
        }
        Date ceiling = issue.getPublicTo() != null ? issue.getPublicTo() : replacedAt;
        Date tagCreated = legacy.getMessageTag() == null ? null : legacy.getMessageTag().getCreated();

        Date best = null;
        for (Date candidate : new Date[] { legacy.getUpdated(), tagCreated }) {
            if (candidate == null || candidate.before(from)) {
                continue;
            }
            if (ceiling != null && candidate.after(ceiling)) {
                continue;
            }
            if (replacedAt != null && !candidate.before(replacedAt)) {
                continue;
            }
            if (best == null || candidate.after(best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Whether two releases are the same release seen twice.
     *
     * Five minutes, the same agreement window the cascade uses: a withdrawal and
     * its replacement are written by one action, and rows minutes apart were
     * released together. Rows hours apart were not.
     */
    private static boolean isSibling(Date previousRelease, Date release) {
        return previousRelease != null && release != null
                && Math.abs(release.getTime() - previousRelease.getTime()) <= CutoffRecovery.AGREEMENT_WINDOW_MS;
    }

    /**
     * The issues of one SERIES, in chain order.
     *
     * Keyed by the series each publication resolves to -- its template's, or the
     * one the orphan grouping and the template redirects filed it under -- not by
     * its legacy template. The double-week issues are template-less rows filed
     * into the weekly series by ruling, and chaining by template put them in a
     * chain of their own: "EfS uge 17 - 2017" opened where "uge 14" closed, and
     * "uge 15-16" between them opened where some unrelated orphan had.
     *
     * Ordered by the public window's start, because that is the order the issues
     * were released in and it is what makes "the NEXT issue's tag" mean anything.
     * Ties break on publicationId so the order is total -- an unstable sort would
     * make the cut-off cascade's stage 2 depend on which rows the database
     * happened to return first.
     */
    private Map<String, List<Publication>> chainsBySeries(List<Publication> publications,
                                                          Map<String, PublicationSeries> seriesByTemplate) {
        Map<String, List<Publication>> out = new LinkedHashMap<>();
        for (Publication p : publications) {
            PublicationSeries series = p.getTemplate() == null
                    ? null : seriesByTemplate.get(p.getTemplate().getPublicationId());
            if (series == null) {
                series = seriesByTemplate.get(p.getPublicationId());
            }
            // A row that resolves to no series chains alone; planIssues reports it.
            String key = series != null ? "series:" + series.getSeriesId() : "none:" + p.getPublicationId();
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        Comparator<Publication> order = Comparator
                .comparing((Publication p) -> p.getPublishDateFrom() == null
                        ? new Date(0) : p.getPublishDateFrom())
                .thenComparing(Publication::getPublicationId);
        out.values().forEach(chain -> chain.sort(order));
        return out;
    }

    /**
     * The uids the locked tag holds, by the STORED JOIN.
     *
     * Never a lookup by tag name: seven names are shared, two of them by three
     * publications, so a name lookup returns the wrong tag for at least ten rows
     * and cannot tell that it did.
     */
    /**
     * Every tag's members, in ONE query.
     *
     * This used to run per publication: 1,077 queries, each joining MessageTag to
     * its messages, and the plan took 41 seconds of a 240-second transaction
     * budget it shares with the write. One query keyed on the tag ids costs a
     * single round trip.
     *
     * Keyed on tag ID rather than name, for the same reason everything else here
     * is: seven tag names are shared, two of them by three publications, so a
     * name lookup returns the wrong tag for at least ten rows and cannot tell.
     */
    private Map<Integer, List<MemberSnapshotImport.MemberFacts>> memberFactsByTag(
            List<Publication> publications) {

        List<Integer> tagIds = publications.stream()
                .map(Publication::getMessageTag)
                .filter(tag -> tag != null && tag.getId() != null)
                .map(tag -> tag.getId())
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<MemberSnapshotImport.MemberFacts>> out = new LinkedHashMap<>();
        em.createQuery(
                        "SELECT t.id, m.uid, m.shortId, m.mainType, m.type, m.status, "
                                + "m.publishDateFrom, m.publishDateTo "
                                + "FROM MessageTag t JOIN t.messages m WHERE t.id IN :ids",
                        Object[].class)
                .setParameter("ids", tagIds)
                .getResultStream()
                .forEach(r -> out.computeIfAbsent((Integer) r[0], k -> new ArrayList<>())
                        .add(new MemberSnapshotImport.MemberFacts(
                                (String) r[1],
                                (String) r[2],
                                r[3] == null ? null : r[3].toString(),
                                r[4] == null ? null : r[4].toString(),
                                r[5] == null ? null : r[5].toString(),
                                (Date) r[6],
                                (Date) r[7])));
        return out;
    }

    /**
     * A member whose message cannot fill the frozen caption fails at PLAN time.
     *
     * frozenMainType, frozenType and frozenStatus are NOT NULL. The first version
     * of this importer set none of them and died on the constraint -- AFTER the
     * dry run had reported the whole estate clean, because plan() never persists
     * and so could not see it. Checking here is what makes that class of failure
     * visible to a dry run instead of to a 500 on the real thing.
     */
    private void assertMembersCanBeFrozen(Plan plan, Publication legacy,
                                          List<MemberSnapshotImport.MemberFacts> members) {
        for (MemberSnapshotImport.MemberFacts facts : members) {
            if (!facts.isComplete()) {
                problem(plan, "MEMBER_CANNOT_BE_FROZEN", legacy,
                        "message '" + facts.uid() + "' has no " + facts.missing()
                                + ", and a frozen member row cannot be written without it. The row is "
                                + "what lets a retired issue still be read years later.");
            }
        }
    }

    /**
     * A referenced domain, report or message series must exist.
     *
     * Checked at PLAN time so the report names every offender at once. Nulling
     * any of them silently would leave a series that resolves against a scope
     * nobody chose, and no field on the row would record that it happened.
     */
    private void assertReferencesResolve(Plan plan, Publication legacy, PublicationSeries series) {
        if (legacy.getDomain() != null && legacy.getDomain().getDomainId() != null
                && domainService.findByDomainId(legacy.getDomain().getDomainId()) == null) {
            problem(plan, "DOMAIN_NOT_FOUND", legacy,
                    "domain '" + legacy.getDomain().getDomainId() + "' does not exist. Importing the "
                            + "series without it would leave it resolving against the whole corpus.");
        }
        if (series.getReportId() != null
                && reportService.findByReportId(series.getReportId()) == null) {
            problem(plan, "REPORT_NOT_FOUND", legacy,
                    "report '" + series.getReportId() + "' does not exist, so this series could never "
                            + "generate its PDF.");
        }
        if (legacy.getDomain() != null && legacy.getDomain().getMessageSeries() != null) {
            legacy.getDomain().getMessageSeries().forEach(ms -> {
                if (ms.getSeriesId() != null
                        && messageSeriesService.findBySeriesId(ms.getSeriesId()) == null) {
                    problem(plan, "MESSAGE_SERIES_NOT_FOUND", legacy,
                            "message series '" + ms.getSeriesId() + "' does not exist.");
                }
            });
        }
    }

    // ---------------------------------------------------------------- applying

    /**
     * How many rows to accumulate before flushing.
     *
     * Without a flush, Hibernate holds all ~13,000 entities and dirty-checks the
     * whole set every time it does flush. With one, the work stays linear and the
     * SQL leaves in batches instead of one enormous burst at commit.
     */
    private static final int FLUSH_EVERY = 500;

    /**
     * Writes the plan. Only ever called with a clean one.
     *
     * BATCHED, because the unbatched version did not finish. The estate is 21
     * series, 1,077 issues and ~10,200 member rows -- about 13,000 inserts -- and
     * sending them one statement at a time took roughly 200 seconds on top of a
     * 41-second plan, against a 240-second transaction timeout
     * (quarkus.transaction-manager.default-transaction-timeout in niord-dk). The
     * import died at 240.25s having written nothing.
     *
     * Raising the timeout was the other option and is the worse one: it would
     * leave a one-way operation running for minutes with no way to tell a slow
     * import from a stuck one. Making the write fast is the fix; the timeout stays
     * as the backstop it was meant to be.
     *
     * The JDBC batch size is set on THIS session rather than in application
     * configuration, because it is this operation that needs it -- a global batch
     * size would change how every other write in the system behaves.
     */
    void apply(Plan plan) {
        em.unwrap(org.hibernate.Session.class).setJdbcBatchSize(FLUSH_EVERY);
        for (Map.Entry<String, PublicationCategory> e : plan.categoriesToCreate().entrySet()) {
            PublicationCategory source = e.getValue();
            PublicationCategory category = new PublicationCategory();
            category.setCategoryId(e.getKey());

            // priority and publish travel verbatim. A bare new category defaults
            // publish to FALSE, and two of the five live categories really are
            // false -- so defaulting would be right by accident for those two and
            // silently wrong for the other three, hiding 1,085 publications from
            // /public/v1. Copying is the only version that is right on purpose.
            category.setPriority(source.getPriority());
            category.setPublish(source.isPublish());
            source.getDescs().forEach(d -> category.addDesc(
                    new org.niord.core.publication.PublicationCategoryDesc(d.toVo())));

            categoryService.findOrCreatePublicationCategory(category);
        }

        for (PublicationSeries series : plan.series()) {
            String categoryId = plan.categoryOfSeries().get(series.getSeriesId());
            PublicationCategory category = categoryService.findByCategoryId(categoryId);
            if (category == null) {
                // Cannot happen on a clean plan -- planCategories created it and
                // planCategoryOf refused a series without one -- so if it does,
                // something upstream changed and silence would persist a series
                // pointing at nothing.
                throw new IllegalStateException("category '" + categoryId + "' vanished between plan "
                        + "and apply for series '" + series.getSeriesId() + "'");
            }
            series.setCategory(category);
            em.persist(series);
        }
        int written = 0;
        for (PublicationIssue issue : plan.issues().values()) {
            em.persist(issue);
            // The one honest entry an imported issue carries: it was imported,
            // by nobody, from a named legacy row. No fabricated publish, no
            // fabricated overrides -- the trail starts where this system's
            // knowledge of the issue does.
            audit.imported(issue, "legacy publication " + issue.getLegacyPublicationId());
            if (++written % FLUSH_EVERY == 0) {
                em.flush();
            }
        }
        em.flush();

        // The member rows are the bulk of it -- ten of every eleven inserts.
        //
        // NOT cleared between batches: an IssueMember holds a reference to its
        // issue, and clearing would detach every issue persisted so far, so the
        // next member would attach to a detached parent. Flushing alone bounds the
        // SQL without touching the identity map, which is the part that has to
        // stay intact.
        written = 0;
        for (List<IssueMember> members : plan.members().values()) {
            for (IssueMember member : members) {
                em.persist(member);
                if (++written % FLUSH_EVERY == 0) {
                    em.flush();
                }
            }
        }
        em.flush();

        log.info("legacy import wrote {} series, {} issues, {} member rows",
                plan.series().size(), plan.issues().size(),
                plan.members().values().stream().mapToInt(List::size).sum());
    }

    // ----------------------------------------------------------------- helpers

    /**
     * The seriesIds the database already holds.
     *
     * The importer is not the only thing that creates series -- an admin can
     * author one by hand, and the test environment already carries two. Planning
     * against an empty namespace assumes the importer is alone in it, which is
     * true exactly once and false every time after.
     */
    private Set<String> seriesIdsAlreadyTaken() {
        return new LinkedHashSet<>(em.createQuery(
                "SELECT s.seriesId FROM PublicationSeries s", String.class).getResultList());
    }

    /**
     * Belt and braces: no two planned series share an id, and none reuses one.
     *
     * The authoring routines now share a namespace, so this should never fire.
     * It stays because of HOW the collision it guards against was found: as
     * "Duplicate entry for key PublicationSeries.UK_..." thrown by MySQL a third
     * of the way into the write, from a dry run that had just reported problems: []
     * over the same estate. A dry run that cannot see a class of failure is worse
     * than no dry run, because it is believed. This check costs one pass over
     * twenty-one rows and moves that failure back into the report.
     */
    private void assertSeriesIdsAreUnique(Plan plan) {
        Set<String> taken = seriesIdsAlreadyTaken();
        Set<String> planned = new LinkedHashSet<>();

        for (PublicationSeries series : plan.series()) {
            String id = series.getSeriesId();
            if (!planned.add(id)) {
                problem(plan, "SERIES_ID_COLLISION", series.getLegacyTemplateId(), id,
                        "two planned series both claim the seriesId '" + id + "'. The unique key "
                                + "would refuse the second one mid-write, leaving the report to say "
                                + "the import succeeded up to the row that did not.");
            } else if (taken.contains(id)) {
                problem(plan, "SERIES_ID_COLLISION", series.getLegacyTemplateId(), id,
                        "a series with seriesId '" + id + "' already exists. The importer does not "
                                + "adopt or overwrite an existing series -- that would merge an "
                                + "imported archive into something somebody else authored.");
            }
        }
    }

    /** For a problem that belongs to a planned series rather than a legacy row. */
    private void problem(Plan plan, String code, String legacyId, String title, String detail) {
        plan.report().getProblems().add(
                new LegacyImportReportVo.ProblemVo(code, legacyId, title, detail));
    }

    private void problem(Plan plan, String code, Publication legacy, String detail) {
        plan.report().getProblems().add(new LegacyImportReportVo.ProblemVo(
                code, legacy.getPublicationId(), titleOf(legacy), detail));
    }

    /**
     * A row that imports, carrying a decision somebody should read.
     *
     * Separate from problem() because a problem refuses the estate. Anything that
     * belongs on this list would otherwise be invisible: the estate is too large
     * to read row by row, and the run happens once.
     */
    private void note(Plan plan, String code, Publication legacy, String detail) {
        plan.report().getNotes().add(new LegacyImportReportVo.ProblemVo(
                code, legacy.getPublicationId(), titleOf(legacy), detail));
    }

    /** The title, for the report only. Never used as a key: titles are not stable, and an issue keyed on one is an issue that moves when somebody edits a heading. */
    private static String titleOf(Publication p) {
        if (p.getDescs() == null) {
            return null;
        }
        return p.getDescs().stream()
                .map(d -> d.getTitle())
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse(null);
    }

    private static String importSource() {
        return "legacy-publications";
    }

    private static List<Publication> concat(List<Publication> a, List<Publication> b) {
        List<Publication> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
