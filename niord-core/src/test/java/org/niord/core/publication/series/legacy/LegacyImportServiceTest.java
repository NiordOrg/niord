package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationDesc;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.model.publication.PublicationType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5.6. The dry run writes nothing, and one bad row aborts the whole import.
 *
 * @QuarkusTest with a database, because the two claims being made are both about
 * the database: that S18 does not touch it, and that S19 leaves it untouched when
 * it refuses. Neither can be shown without one.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class LegacyImportServiceTest {

    @Inject
    LegacyImportService importService;

    @Inject
    EntityManager em;

    /** Row counts of everything the importer can write. */
    private Map<String, Long> census() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String entity : List.of("PublicationSeries", "PublicationIssue", "IssueMember",
                "PublicationCategory", "IssueOverride", "IssueAuditEntry")) {
            out.put(entity, em.createQuery("SELECT COUNT(e) FROM " + entity + " e", Long.class)
                    .getSingleResult());
        }
        return out;
    }

    @Transactional
    Publication legacyTemplate(String title, String filter) {
        Publication p = new Publication();
        p.setPublicationId(UUID.randomUUID().toString());
        p.setMainType(PublicationMainType.TEMPLATE);
        p.setType(PublicationType.NONE);
        p.setStatus(PublicationStatus.ACTIVE);
        p.setRepoPath("publications/test/" + UUID.randomUUID());
        p.setMessageTagFilter(filter);
        // A series without a category is refused: the category decides which
        // section of the public page it lands in, and whether it lands at all.
        p.setCategory(probeCategory());

        PublicationDesc d = new PublicationDesc();
        d.setLang("en");
        d.setTitle(title);
        p.setDescs(new java.util.ArrayList<>(List.of(d)));
        d.setEntity(p);

        em.persist(p);
        em.flush();
        return p;
    }

    /** A category the probe templates can belong to, created once. */
    @Transactional
    PublicationCategory probeCategory() {
        PublicationCategory existing = em.createQuery(
                        "SELECT c FROM PublicationCategory c WHERE c.categoryId = :id",
                        PublicationCategory.class)
                .setParameter("id", "import-probe")
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("import-probe");
        c.setPriority(999);
        c.setPublish(false);
        em.persist(c);
        em.flush();
        return c;
    }

    @Transactional
    Publication legacyPublication(String title, Publication template) {
        Publication p = new Publication();
        p.setPublicationId(UUID.randomUUID().toString());
        p.setMainType(PublicationMainType.PUBLICATION);
        p.setType(PublicationType.NONE);
        p.setStatus(PublicationStatus.ACTIVE);
        p.setRepoPath("publications/test/" + UUID.randomUUID());
        p.setTemplate(em.find(Publication.class, template.getId()));

        PublicationDesc d = new PublicationDesc();
        d.setLang("en");
        d.setTitle(title);
        p.setDescs(new java.util.ArrayList<>(List.of(d)));
        d.setEntity(p);

        em.persist(p);
        em.flush();
        return p;
    }

    /**
     * Removes EVERYTHING the import wrote, not just this test's own rows.
     *
     * run() imports the whole estate by design, so a cleanup scoped to the two
     * probe rows leaves every other imported issue behind -- and the next run
     * then refuses with ALREADY_IMPORTED, which is the guard working correctly
     * against a mess this test made. Identified by legacyPublicationId /
     * legacyTemplateId, which only the importer sets.
     *
     * Deleted in foreign-key order: the child rows first, or MySQL refuses.
     */
    @Transactional
    void cleanUpImported() {
        // Detach first: a bulk DELETE bypasses the persistence context, so anything
        // still managed here would be flushed back afterwards as a stale update.
        em.flush();
        em.clear();
        em.createQuery("DELETE FROM IssueMember m WHERE m.issue.legacyPublicationId IS NOT NULL")
                .executeUpdate();
        em.createQuery("DELETE FROM IssueAuditEntry a WHERE a.issue.legacyPublicationId IS NOT NULL")
                .executeUpdate();
        em.createQuery("DELETE FROM PublicationIssueDesc d WHERE d.entity.legacyPublicationId IS NOT NULL")
                .executeUpdate();
        em.createQuery("DELETE FROM PublicationIssue i WHERE i.legacyPublicationId IS NOT NULL")
                .executeUpdate();
        em.createQuery("DELETE FROM PublicationSeriesDesc d WHERE d.entity.legacyTemplateId IS NOT NULL")
                .executeUpdate();
        em.createQuery("DELETE FROM PublicationSeries s WHERE s.legacyTemplateId IS NOT NULL")
                .executeUpdate();
        em.flush();
    }

    @Transactional
    void remove(String publicationId) {
        em.createQuery("SELECT p FROM Publication p WHERE p.publicationId = :id", Publication.class)
                .setParameter("id", publicationId)
                .getResultStream().findFirst().ifPresent(em::remove);
        em.flush();
    }

    /**
     * S18 leaves the database exactly as it found it.
     *
     * Asserted as a census of every table the importer can write, taken before
     * and after. The dry run never opens a write -- it is not a transaction that
     * rolls back -- so this is a check that the design held, not that the rollback
     * worked.
     */
    @Test
    public void theDryRunWritesNothing() {
        Map<String, Long> before = census();

        LegacyImportReportVo report = importService.dryRun();

        assertTrue(report.isDryRun());
        assertEquals(before, census(),
                "S18 must leave the database byte-identical; an admin asking what WOULD happen has not "
                        + "consented to anything happening");
    }

    /**
     * The dry run and the real run agree about what would happen.
     *
     * Not "the report is non-empty" -- that passes on a placeholder. Two runs
     * back to back must reach the same verdict and name the same offenders, which
     * is the only property that makes a preview worth reading. Compared as sets
     * because order is not part of the promise.
     */
    @Test
    public void theDryRunAgreesWithTheRealRunAboutWhatWouldHappen() {
        Publication offender = legacyTemplate("Agreement Probe", "msg.type == Type.NOPE");
        try {
            LegacyImportReportVo dry = importService.dryRun();
            LegacyImportReportVo real = importService.run();

            assertTrue(dry.isDryRun());
            assertFalse(real.isDryRun());
            assertEquals(dry.isWouldSucceed(), real.isWouldSucceed(),
                    "a preview that disagrees with the thing it previews is worse than no preview");
            assertEquals(
                    dry.getProblems().stream().map(x -> x.getCode() + "/" + x.getPublicationId())
                            .collect(java.util.stream.Collectors.toSet()),
                    real.getProblems().stream().map(x -> x.getCode() + "/" + x.getPublicationId())
                            .collect(java.util.stream.Collectors.toSet()),
                    "both runs must name the same offenders");
        } finally {
            remove(offender.getPublicationId());
        }
    }

    /**
     * One unknown filter aborts the WHOLE run, and nothing is written.
     *
     * The offending row is a template carrying a filter no publication in the
     * estate stores. A partial import is the failure this prevents: the rows that
     * landed would look correct, nothing would mark them as a partial set, and
     * finding the gap means comparing 1,077 rows by hand.
     */
    @Test
    public void oneUnknownFilterAbortsEverythingAndWritesNothing() {
        Publication offender = legacyTemplate("Import Abort Probe",
                "msg.status == Status.DRAFT && msg.type == Type.NONEXISTENT");
        try {
            Map<String, Long> before = census();

            LegacyImportReportVo report = importService.run();

            assertFalse(report.isWouldSucceed(), "an unknown filter must refuse the whole run");
            assertEquals(before, census(), "a refused import writes nothing at all");

            assertTrue(report.getProblems().stream()
                            .anyMatch(p -> offender.getPublicationId().equals(p.getPublicationId())),
                    "the report must name the publication it refused");
        } finally {
            remove(offender.getPublicationId());
        }
    }

    /**
     * The report names the id, the title and the verbatim filter.
     *
     * All three, because an admin has to find the row in the legacy UI (title),
     * be certain which one it is (id), and know what to do about it (the filter,
     * spelled exactly as stored -- an abbreviated echo would send them looking
     * for a string that is not there).
     */
    @Test
    public void theReportNamesTheIdTheTitleAndTheVerbatimFilter() {
        String filter = "msg.status == Status.DRAFT && data.phase == 'nope'";
        Publication offender = legacyTemplate("Verbatim Probe", filter);
        try {
            LegacyImportReportVo report = importService.dryRun();

            LegacyImportReportVo.ProblemVo problem = report.getProblems().stream()
                    .filter(p -> offender.getPublicationId().equals(p.getPublicationId()))
                    .findFirst().orElseThrow(() -> new AssertionError("the offender was not reported"));

            assertEquals("Verbatim Probe", problem.getTitle());
            assertTrue(problem.getDetail().contains(filter),
                    "the filter must be echoed verbatim: " + problem.getDetail());
        } finally {
            remove(offender.getPublicationId());
        }
    }

    /**
     * Every offender is reported, not just the first.
     *
     * An admin fixing them one build at a time is the failure mode a full report
     * exists to prevent.
     */
    @Test
    public void everyOffenderIsReportedNotJustTheFirst() {
        Publication a = legacyTemplate("Probe A", "msg.status == Status.DRAFT");
        Publication b = legacyTemplate("Probe B", "msg.type == Type.NOPE");
        try {
            LegacyImportReportVo report = importService.dryRun();

            long named = report.getProblems().stream()
                    .filter(p -> a.getPublicationId().equals(p.getPublicationId())
                            || b.getPublicationId().equals(p.getPublicationId()))
                    .count();
            assertEquals(2, named, "both offenders must appear in one report");
        } finally {
            remove(a.getPublicationId());
            remove(b.getPublicationId());
        }
    }

    /**
     * The WRITE path: apply() actually persists a series, its issue and its category.
     *
     * Planned from a CONTROLLED pair of rows rather than the whole estate. This
     * database is shared with every other suite, and its legacy Publication rows
     * are their fixtures -- most carry no repoPath -- so an estate-wide plan over
     * it can never come back clean, and the write path would never be reached.
     * Production always plans the whole estate; that is what makes "fail rather
     * than import a partial estate" mean anything.
     */
    @Test
    @Transactional
    public void applyWritesTheSeriesItsIssueAndItsCategory() {
        Publication template = legacyTemplate("Write Path Probe", null);
        Publication issueRow = legacyPublication("Write Path Probe 1", template);
        try {
            LegacyImportService.Plan plan =
                    importService.planFrom(List.of(template), List.of(issueRow));

            assertTrue(plan.isClean(), "the controlled pair should plan cleanly; problems: "
                    + plan.report().getProblems().stream()
                            .map(x -> x.getCode() + " " + x.getDetail()).toList());

            importService.apply(plan);
            em.flush();

            PublicationSeries written = em.createQuery(
                            "SELECT s FROM PublicationSeries s WHERE s.legacyTemplateId = :id",
                            PublicationSeries.class)
                    .setParameter("id", template.getPublicationId())
                    .getSingleResult();
            assertEquals("write-path-probe", written.getSeriesId(),
                    "the seriesId is authored from the title, never adopted from the legacy UUID");
            assertEquals(SeriesStatus.DRAFT, written.getStatus(),
                    "an imported series is a translation, reviewed before it is active");
            assertEquals("import-probe", written.getCategory().getCategoryId(),
                    "the series must carry its category -- the column is NOT NULL and the category "
                            + "decides which section of the public page it lands in");

            PublicationIssue issue = em.createQuery(
                            "SELECT i FROM PublicationIssue i WHERE i.publicId = :id",
                            PublicationIssue.class)
                    .setParameter("id", issueRow.getPublicationId())
                    .getSingleResult();
            assertEquals(issueRow.getPublicationId(), issue.getLegacyPublicationId(),
                    "id-space continuity: the legacy id IS the publicId");
            assertEquals(issueRow.getRepoPath(), issue.getRepoPath(), "paths travel verbatim (R6)");
            assertNotNull(issue.getSnapshotTimeRelation(), "B5.4a2's header must be written");
            assertNotNull(issue.getCutoffStampedAt(), "B5.4b must have recovered a cut-off");
        } finally {
            cleanUpImported();
        }
    }

    /**
     * A second plan over rows already imported refuses rather than colliding.
     *
     * Without this the re-run fails on the publicId unique constraint PART WAY
     * THROUGH, and the admin sees a raw constraint violation instead of a sentence
     * saying the estate is already there.
     */
    @Test
    @Transactional
    public void asecondImportRefusesInsteadOfCollidingOnTheUniqueKey() {
        Publication template = legacyTemplate("Rerun Probe", null);
        Publication issueRow = legacyPublication("Rerun Probe 1", template);
        try {
            importService.apply(importService.planFrom(List.of(template), List.of(issueRow)));
            em.flush();

            LegacyImportService.Plan again =
                    importService.planFrom(List.of(template), List.of(issueRow));

            assertFalse(again.isClean(), "a re-run must refuse");
            assertTrue(again.report().getProblems().stream()
                            .anyMatch(x -> "ALREADY_IMPORTED".equals(x.getCode())),
                    "and must say the estate is already imported");
        } finally {
            cleanUpImported();
        }
    }

    // There is deliberately no "the estate in this database plans cleanly" test.
    // The legacy Publication rows here belong to other suites and most carry no
    // repoPath, so such a test would assert a property of ambient fixtures rather
    // than of the importer -- and would fail whenever another suite added a row.
}
