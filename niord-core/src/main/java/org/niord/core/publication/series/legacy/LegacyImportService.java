package org.niord.core.publication.series.legacy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.niord.core.domain.DomainService;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.report.FmReportService;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B5.6. The legacy importer: plan it all, then write it all, or write none of it.
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

    /** Everything one import run would write, plus everything wrong with it. */
    public static class Plan {

        private final LegacyImportReportVo report = new LegacyImportReportVo();
        private final List<PublicationSeries> series = new ArrayList<>();
        private final Map<String, PublicationIssue> issues = new LinkedHashMap<>();
        private final Map<String, List<IssueMember>> members = new LinkedHashMap<>();
        private final Set<String> categoriesToCreate = new LinkedHashSet<>();
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

        public Set<String> categoriesToCreate() {
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

    /** S18. Reads, translates, reports. Writes nothing, ever. */
    public LegacyImportReportVo dryRun() {
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

        planAlreadyImported(plan, templates, publications);
        planCategories(plan, templates, publications);
        Map<String, PublicationSeries> seriesByTemplate = planSeries(plan, templates, authored);
        planOrphanSeries(plan, publications, seriesByTemplate, authored);
        planIssues(plan, publications, seriesByTemplate, frozenAt);
        assertSeriesIdsAreUnique(plan);

        plan.report().setSeriesImported(plan.series().size());
        plan.report().setIssuesImported(plan.issues().size());
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
        for (Publication template : concat(templates, publications.stream()
                .filter(x -> x.getTemplate() == null).toList())) {
            Long seriesRows = em.createQuery(
                            "SELECT COUNT(s) FROM PublicationSeries s WHERE s.legacyTemplateId = :id",
                            Long.class)
                    .setParameter("id", template.getPublicationId())
                    .getSingleResult();
            if (seriesRows > 0) {
                problem(plan, "ALREADY_IMPORTED", template,
                        "a series already carries legacyTemplateId '" + template.getPublicationId()
                                + "'. The importer does not merge or update: re-running it against an "
                                + "archive somebody may have edited since would reconcile the two "
                                + "silently.");
            }
        }
        for (Publication legacy : publications) {
            Long issueRows = em.createQuery(
                            "SELECT COUNT(i) FROM PublicationIssue i WHERE i.publicId = :id",
                            Long.class)
                    .setParameter("id", legacy.getPublicationId())
                    .getSingleResult();
            if (issueRows > 0) {
                problem(plan, "ALREADY_IMPORTED", legacy,
                        "an issue already carries publicId '" + legacy.getPublicationId() + "'.");
            }
        }
    }

    private List<Publication> legacyRows(PublicationMainType mainType) {
        return em.createQuery(
                        "SELECT p FROM Publication p WHERE p.mainType = :mt ORDER BY p.publicationId",
                        Publication.class)
                .setParameter("mt", mainType)
                .getResultList();
    }

    /**
     * B5.2. Categories are upserted by categoryId, and a missing one is created.
     *
     * priority and publish travel verbatim: two of the five live categories carry
     * publish = false, and their publications must stay off /public/v1. Defaulting
     * that flag to true would publish four documents nobody asked to publish.
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
                plan.categoriesToCreate().add(id);
            }
        }
        plan.report().setCategoriesSeen(seen.size());
        plan.report().setCategoriesCreated(plan.categoriesToCreate().size());
    }

    private Map<String, PublicationSeries> planSeries(Plan plan, List<Publication> templates,
                                                      Set<String> authored) {
        Map<String, PublicationSeries> byTemplate = new LinkedHashMap<>();

        for (Publication template : templates) {
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
     * Files the template-less publications, per ruling B5-v as revised.
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

            // A ruled id is not auto-suffixed. B5-v names these three series in
            // words -- nm-annex-ncags and the rest -- so a clash means the ruling
            // and the estate disagree, which is a sentence for a human and not a
            // name for the importer to invent.
            if (!authored.add(e.getKey())) {
                problem(plan, "SERIES_ID_COLLISION", source,
                        "the ruled seriesId '" + e.getKey() + "' is already taken by another series. "
                                + "B5-v names this series explicitly, so the importer will not rename "
                                + "it: either the ruling or the colliding series has to change.");
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

    private void planIssues(Plan plan, List<Publication> publications,
                            Map<String, PublicationSeries> seriesByTemplate, Date frozenAt) {
        Map<String, List<Publication>> byTag = MemberSnapshotImport.byTagName(publications);
        Map<Integer, List<MemberSnapshotImport.MemberFacts>> factsByTag =
                memberFactsByTag(publications);
        Map<String, List<Publication>> chains = chainsByTemplate(publications);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> byCutoffSource = new LinkedHashMap<>();

        for (List<Publication> chain : chains.values()) {
            for (int i = 0; i < chain.size(); i++) {
                Publication legacy = chain.get(i);
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
                        // (ruling B5-v). PublicationIssue.series is NOT NULL, so
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

                    PublicationIssue issue =
                            LegacyIssueTranslation.translate(legacy, series, frozenAt);

                    CutoffRecovery.Recovered cutoff = CutoffRecovery.recover(
                            legacy, CutoffRecovery.nextTagCreated(chain, i), null);
                    issue.setCutoffStampedAt(cutoff.cutoff());
                    issue.setCutoffSource(cutoff.source());
                    issue.setCutoffReconstructed(cutoff.reconstructed());

                    List<MemberSnapshotImport.MemberFacts> facts =
                            legacy.getMessageTag() == null || legacy.getMessageTag().getId() == null
                                    ? List.of()
                                    : factsByTag.getOrDefault(legacy.getMessageTag().getId(), List.of());
                    assertMembersCanBeFrozen(plan, legacy, facts);
                    List<IssueMember> members = MemberSnapshotImport.apply(
                            issue, legacy, facts, byTag);

                    plan.issues().put(legacy.getPublicationId(), issue);
                    plan.members().put(legacy.getPublicationId(), members);

                    byStatus.merge(issue.getStatus().name(), 1, Integer::sum);
                    byCutoffSource.merge(cutoff.source(), 1, Integer::sum);
                } catch (RuntimeException e) {
                    problem(plan, "ISSUE_UNTRANSLATABLE", legacy, e.getMessage());
                }
            }
        }

        plan.report().setIssuesByStatus(byStatus);
        plan.report().setIssuesByCutoffSource(byCutoffSource);
    }

    /**
     * The issues of one template, in chain order.
     *
     * Ordered by the public window's start, because that is the order the issues
     * were released in and it is what makes "the NEXT issue's tag" mean anything.
     * Ties break on publicationId so the order is total -- an unstable sort would
     * make the cut-off cascade's stage 2 depend on which rows the database
     * happened to return first.
     */
    private Map<String, List<Publication>> chainsByTemplate(List<Publication> publications) {
        Map<String, List<Publication>> out = new LinkedHashMap<>();
        for (Publication p : publications) {
            String key = p.getTemplate() == null ? "" : p.getTemplate().getPublicationId();
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
        for (String categoryId : plan.categoriesToCreate()) {
            PublicationCategory category = new PublicationCategory();
            category.setCategoryId(categoryId);
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

    /** The title, for the report only. Never used as a key -- see B5.4a. */
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
