package org.niord.core.publication.series.legacy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
     * S19. Plans, and applies only if the plan is clean.
     *
     * Transactional so that a failure DURING apply -- a constraint nobody
     * predicted -- also leaves nothing behind. The clean check is the first
     * line of defence and the transaction is the second; neither is sufficient
     * alone, because plan() cannot foresee a database-level refusal.
     */
    @Transactional
    public LegacyImportReportVo run() {
        Plan plan = plan();
        plan.report().setDryRun(false);

        if (!plan.isClean()) {
            plan.report().setWouldSucceed(false);
            log.warn("legacy import refused: {} problem(s); nothing written",
                    plan.report().getProblems().size());
            return plan.report();
        }

        apply(plan);
        plan.report().setWouldSucceed(true);
        return plan.report();
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

        planAlreadyImported(plan, templates, publications);
        planCategories(plan, templates, publications);
        Map<String, PublicationSeries> seriesByTemplate = planSeries(plan, templates);
        planIssues(plan, publications, seriesByTemplate, frozenAt);

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
        for (Publication template : templates) {
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

    private Map<String, PublicationSeries> planSeries(Plan plan, List<Publication> templates) {
        Map<String, PublicationSeries> byTemplate = new LinkedHashMap<>();
        Set<String> authored = new LinkedHashSet<>();

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

    private void planIssues(Plan plan, List<Publication> publications,
                            Map<String, PublicationSeries> seriesByTemplate, Date frozenAt) {
        Map<String, List<Publication>> byTag = MemberSnapshotImport.byTagName(publications);
        Map<String, List<Publication>> chains = chainsByTemplate(publications);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> byShape = new LinkedHashMap<>();

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

                    PublicationIssue issue =
                            LegacyIssueTranslation.translate(legacy, series, frozenAt);

                    CutoffRecovery.Recovered cutoff = CutoffRecovery.recover(
                            legacy, CutoffRecovery.nextTagCreated(chain, i), null);
                    issue.setCutoffStampedAt(cutoff.cutoff());
                    issue.setCutoffSource(cutoff.source());
                    issue.setCutoffReconstructed(cutoff.reconstructed());

                    List<IssueMember> members = MemberSnapshotImport.apply(
                            issue, legacy, memberUids(legacy), byTag);

                    plan.issues().put(legacy.getPublicationId(), issue);
                    plan.members().put(legacy.getPublicationId(), members);

                    byStatus.merge(issue.getStatus().name(), 1, Integer::sum);
                    byShape.merge(cutoff.source(), 1, Integer::sum);
                } catch (RuntimeException e) {
                    problem(plan, "ISSUE_UNTRANSLATABLE", legacy, e.getMessage());
                }
            }
        }

        plan.report().setIssuesByStatus(byStatus);
        plan.report().setIssuesByFilterShape(byShape);
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
    private List<String> memberUids(Publication legacy) {
        if (legacy.getMessageTag() == null || legacy.getMessageTag().getId() == null) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT m.uid FROM MessageTag t JOIN t.messages m WHERE t.id = :id",
                        String.class)
                .setParameter("id", legacy.getMessageTag().getId())
                .getResultList();
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

    /** Writes the plan. Only ever called with a clean one. */
    void apply(Plan plan) {
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
        for (PublicationIssue issue : plan.issues().values()) {
            em.persist(issue);
        }
        for (List<IssueMember> members : plan.members().values()) {
            members.forEach(em::persist);
        }
        em.flush();

        log.info("legacy import wrote {} series, {} issues, {} member rows",
                plan.series().size(), plan.issues().size(),
                plan.members().values().stream().mapToInt(List::size).sum());
    }

    // ----------------------------------------------------------------- helpers

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
