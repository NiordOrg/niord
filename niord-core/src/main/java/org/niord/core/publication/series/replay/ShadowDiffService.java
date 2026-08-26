package org.niord.core.publication.series.replay;

import io.quarkus.scheduler.Scheduled;

import io.quarkus.narayana.jta.QuarkusTransaction;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.EffectiveCriteria;
import org.niord.core.publication.series.MemberResolutionService;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.slf4j.Logger;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * B6.2. After each legacy release, would the new engine have produced the same
 * member list?
 *
 * This is the verification loop productized. B6.1's replay answers the question
 * once, backwards, over the imported archive; this answers it forwards, one
 * release at a time, and that is what accumulates the evidence B6.3 reports and
 * B7.1 waits on: TWO CONSECUTIVE GREEN WEEKS PER SERIES.
 *
 * <h2>Why the results are stored rather than recomputed</h2>
 *
 * Because re-resolving last month's week today answers a different question.
 * {@code type} is mutable and unversioned -- twelve messages are
 * PERMANENT_NOTICE now and were P or T when their issue went out -- so a later
 * recomputation drifts by exactly the amount the archive has aged. The diff has
 * to be taken while the release is fresh, and kept.
 *
 * <h2>What it does not do</h2>
 *
 * It never writes to a publication, an issue or a member list. A shadow-diff
 * that could change what it measures is not a measurement. Its only output is
 * its own row.
 */
@ApplicationScoped
public class ShadowDiffService {

    @Inject
    Logger log;

    @Inject
    EntityManager em;

    @Inject
    MemberResolutionService resolver;

    /**
     * Hourly, at 07 past.
     *
     * Frequent enough that a release is diffed while it is fresh, cheap enough
     * to be uninteresting: the query finds nothing on almost every run, because
     * the thing it waits for happens weekly. The offset is arbitrary but not
     * zero -- the top of the hour is where every other scheduled job in this
     * system already is.
     */
    @Scheduled(cron = "07 7 */1 * * ?")
    void scheduled() {
        try {
            int diffed = runOnce();
            if (diffed > 0) {
                log.info("shadow-diff: compared {} new legacy release(s)", diffed);
            }
        } catch (RuntimeException e) {
            // A diagnostic that takes the scheduler down with it stops being a
            // diagnostic. Logged and swallowed; the next run tries again, and
            // the missing rows are visible as a gap in the series' evidence.
            log.error("shadow-diff run failed; will retry on the next tick", e);
        }
    }

    /**
     * How many releases one invocation will compare.
     *
     * Bounded because the sweep is no longer cheap. It used to be: before the
     * skip fix every release resolved instantly to "nothing imported", and the
     * whole estate finished in seconds. Now each one runs a real member
     * resolution against the message table, and 1,033 of those do not fit in one
     * transaction -- the first attempt died on the 240s platform timeout with
     * every comparison rolled back.
     */
    public static final int DEFAULT_BATCH = 250;

    /** Seconds one release's comparison may take. Generous; a single resolution is milliseconds. */
    static final int DIFF_TIMEOUT_SECONDS = 120;

    /**
     * Diffs releases that have no comparison at their current stamp.
     *
     * ONE TRANSACTION PER RELEASE, and deliberately not one around the sweep.
     * This is a diagnostic: each comparison is independent, and partial progress
     * is worth keeping. Wrapping the sweep would mean losing 1,032 good
     * comparisons because the 1,033rd was slow -- which is what happened, and is
     * exactly backwards for something whose job is to accumulate evidence.
     *
     * A release that throws is logged and stepped over for the same reason. One
     * unresolvable operand must not stop the estate from being measured.
     *
     * @return how many rows were written
     */
    public int runOnce() {
        return runOnce(DEFAULT_BATCH);
    }

    public int runOnce(int max) {
        List<String> todo = QuarkusTransaction.requiringNew()
                .call(() -> undiffedReleases().stream()
                        .limit(Math.max(0, max))
                        .map(Publication::getPublicationId)
                        .toList());

        int written = 0;
        for (String publicationId : todo) {
            try {
                QuarkusTransaction.requiringNew()
                        .timeout(DIFF_TIMEOUT_SECONDS)
                        .run(() -> diffById(publicationId));
                written++;
            } catch (RuntimeException e) {
                log.warn("shadow-diff failed for release {}; stepping over it", publicationId, e);
            }
        }
        return written;
    }

    /**
     * Discard every stored comparison, so the ordinary sweep recomputes them.
     *
     * A run is keyed on the LEGACY inputs. The comparison depends on two more
     * things that key cannot see -- the imported side, and the diff logic itself --
     * so when either changes, every stored verdict is stale and nothing reselects
     * it. This is the way out, and it is deliberately a SEPARATE operation.
     *
     * The first attempt was a force flag on the sweep, which was wrong twice over:
     * it conflated discarding a verdict with selecting a release, and the
     * selection half could not page -- every batch re-read the same first rows and
     * the sweep never advanced. Resetting and then sweeping composes the two
     * operations that each already work.
     *
     * It DISCARDS the accumulated green-week evidence B7.1 counts, which is why it
     * is explicit, never automatic, and named for what it does.
     *
     * Joins the caller's transaction rather than opening its own. The only caller
     * is the REST layer, which is already transactional, and a new transaction
     * would block on locks the caller holds rather than doing the work.
     *
     * @return how many runs were discarded
     */
    @Transactional
    public int reset() {
        return em.createQuery("DELETE FROM ShadowDiffRun r").executeUpdate();
    }

    /** How many releases still have no comparison. Lets a caller drive the sweep to completion. */
    public int remaining() {
        return QuarkusTransaction.requiringNew().call(() -> undiffedReleases().size());
    }

    /** Re-reads the release inside its own transaction: the list was read in another. */
    void diffById(String publicationId) {
        Publication release = em.createQuery(
                        "SELECT p FROM Publication p WHERE p.publicationId = :id", Publication.class)
                .setParameter("id", publicationId)
                .getResultStream().findFirst().orElse(null);
        if (release != null) {
            diff(release);
        }
    }

    /**
     * Releases this stamp has no COMPARISON for.
     *
     * Keyed on the stamp rather than on existence, so a retire-and-republish is
     * picked up as the second release it is. Restricted to publications that
     * carry a tag: without one there is nothing recorded to diff against, and
     * the comparison would be against absence.
     *
     * A SKIP does not settle a release, and that clause is load-bearing. Every
     * skip reason is a fact about the IMPORTED side -- no series, no membership
     * semantics, a file replaced by hand -- while the key is the LEGACY side, and
     * the imported side changes completely every time the archive is re-imported.
     * Treating a skip as final caches an answer about state that has since been
     * replaced.
     *
     * It happened: one scheduled tick fired while an undo had emptied the estate,
     * wrote NO_IMPORTED_SERIES against all 1,077 releases, and -- because a frozen
     * archive's `updated` never changes again -- permanently excluded every one of
     * them from ever being compared. The cutover precondition counts green weeks,
     * so it could never have accumulated one.
     */
    List<Publication> undiffedReleases() {
        return em.createQuery(
                        "SELECT p FROM Publication p "
                                + "WHERE p.messageTag IS NOT NULL "
                                + "AND p.publishDateFrom IS NOT NULL "
                                + "AND NOT EXISTS ("
                                + "  SELECT r FROM ShadowDiffRun r "
                                + "  WHERE r.legacyPublicationId = p.publicationId "
                                + "  AND r.legacyUpdatedAt = p.updated "
                                + "  AND r.skipReason IS NULL) "
                                + "ORDER BY p.updated", Publication.class)
                .getResultList();
    }

    /** Diffs one release and records the result, whatever it is. */
    ShadowDiffRun diff(Publication release) {
        // Any earlier SKIP for this release is discarded rather than kept beside
        // the new row. A skip recorded no evidence, so nothing is lost -- and
        // without this a release nothing can compare would accumulate one row per
        // scheduler tick, forever. Real comparisons are never touched: they are
        // the evidence the cutover decision is made from.
        em.createQuery("DELETE FROM ShadowDiffRun r WHERE r.legacyPublicationId = :id "
                        + "AND r.skipReason IS NOT NULL")
                .setParameter("id", release.getPublicationId())
                .executeUpdate();

        ShadowDiffRun run = new ShadowDiffRun();
        run.setLegacyPublicationId(release.getPublicationId());
        run.setLegacyUpdatedAt(release.getUpdated());
        run.setComparedAt(new Date());
        run.setDelta(Set.of(), Set.of());

        PublicationSeries series = seriesFor(release);
        run.setSeriesId(series == null ? null : series.getSeriesId());

        String skip = skipReasonFor(release, series);
        if (skip != null) {
            // A skipped release is still green: nothing diverged, because
            // nothing was comparable. The skipReason is what stops that being
            // read as evidence -- B6.3 counts green weeks, and a week nobody
            // could compare is not one of them.
            run.setSkipReason(skip);
            em.persist(run);
            return run;
        }

        // The interval the ISSUE claims, not one re-derived here.
        //
        // This used to bound the resolution with publishDateFrom -- the NOMINAL
        // release time -- and that is not when membership was frozen. The release
        // action runs a little after the nominal bound, typically twenty to thirty
        // minutes, and it sweeps up everything published up to the moment it runs.
        // Measured: every still-PUBLISHED member the diff reported missing had been
        // published 20-35 minutes AFTER the nominal bound, and was in the tag.
        //
        // Re-deriving the interval also made this a second source of truth for it.
        // The issue now carries a correct one, so the honest comparison is what the
        // issue says it covers against what the tag recorded.
        PublicationIssue imported = importedIssue(release);
        Date cutoff = imported != null && imported.effectiveCutoff() != null
                ? imported.effectiveCutoff()
                : release.getPublishDateFrom();
        // The issue's lower bound VERBATIM, including when it is null.
        //
        // A null bound is an answer, not a gap: Interval says so itself -- "null
        // when there is no lower bound: the first issue of a series, and every
        // IN_FORCE_AT_CUTOFF issue, which never has one". Falling back to the
        // previous stamp invents a one-week window for an issue that has no lower
        // bound by definition.
        //
        // Mostly this only corrects what the run RECORDS. Membership is resolved by
        // MemberResolutionService, which applies a lower bound only under
        // PUBLISHED_IN_INTERVAL -- an in-force resolution never reads it, so the
        // fabricated bound was usually harmless as well as wrong.
        //
        // It stops being harmless when a release on an in-force series carries a
        // blank filter: criteriaFor classifies by the RELEASE, so that one resolves
        // as PUBLISHED_IN_INTERVAL, reads the bound, and a fabricated week silently
        // replaces "everything still standing".
        Date from = imported != null
                ? imported.getIntervalFrom()
                : previousCutoff(series, cutoff);
        run.setIntervalFrom(from);
        run.setCutoffAt(cutoff);

        // An interval that does not run forwards contains nothing, and Interval
        // refuses to be built from one. Three issues in the estate are like this --
        // two 2017 weeklies and a list-of-lights edition -- where the recovered
        // close lands exactly on the open.
        //
        // Named rather than thrown. The sweep catches a throw and steps over the
        // release, so the comparison is simply absent and the release stays
        // permanently unsettled; a skip says what happened and is counted.
        if (from != null && !from.before(cutoff)) {
            run.setSkipReason("EMPTY_INTERVAL");
            em.persist(run);
            return run;
        }

        Set<String> recorded = taggedMessageUids(release);

        // An EMPTY tag is the same as no tag: there is nothing recorded to compare
        // against, and the query result would be reported as entirely extra.
        //
        // undiffedReleases already refuses a release with no tag for exactly this
        // reason -- "the comparison would be against absence" -- and a tag that
        // exists but holds nothing is the same absence wearing a row. Nine of the
        // eleven NCAGS annex editions are like this: link publications with no
        // membership, whose interval is a three-year VISIBILITY window rather than a
        // content period, so anything published in those years resolves into them.
        if (recorded.isEmpty()) {
            run.setSkipReason("EMPTY_TAG");
            em.persist(run);
            return run;
        }
        Set<String> resolved = resolver
                .resolve(criteriaFor(release, series), new Interval(from, cutoff))
                .members();

        Set<String> missing = new LinkedHashSet<>(recorded);
        missing.removeAll(resolved);
        Set<String> extra = new LinkedHashSet<>(resolved);
        extra.removeAll(recorded);
        run.setDelta(missing, extra);

        em.persist(run);
        return run;
    }

    /** The imported series this release belongs to, or null. */
    private PublicationSeries seriesFor(Publication release) {
        String templateId = release.getTemplate() == null
                ? release.getPublicationId()
                : release.getTemplate().getPublicationId();

        return em.createQuery(
                        "SELECT s FROM PublicationSeries s WHERE s.legacyTemplateId = :t",
                        PublicationSeries.class)
                .setParameter("t", templateId)
                .getResultStream().findFirst().orElse(null);
    }

    /**
     * Why this release cannot be compared, or null if it can.
     *
     * The C6 check reaches through to the IMPORTED issue for this publication,
     * because that is where a hand-uploaded file is recorded. A file somebody
     * replaced by hand was never generated from a member list, so "reproducible
     * from the member list" is not a property it has, and diffing it would
     * manufacture a divergence out of a document nobody generated.
     */
    private String skipReasonFor(Publication release, PublicationSeries series) {
        if (series == null) {
            return "NO_IMPORTED_SERIES";
        }
        if (series.getContentMode() != ContentMode.GENERATED_FROM_QUERY
                || series.getCriteria() == null) {
            return "NO_MEMBERSHIP_SEMANTICS";
        }
        if (release.getPublishDateFrom() == null) {
            return "NO_CUTOFF";
        }

        PublicationIssue imported = importedIssue(release);

        if (imported != null && imported.getDescs() != null
                && imported.getDescs().stream().anyMatch(PublicationIssueDesc::isFileSourceSticky)) {
            return "FILE_REPLACED_BY_HAND";
        }

        // An issue somebody tailored is not comparable either, for the same
        // reason and with the same shape as the line above: a criteriaOverride is
        // a deliberate deviation from what the series selects, so it deviates
        // from what LEGACY selected by construction. Diffing it would manufacture
        // a divergence out of an intentional act -- and because the cutover gate
        // is "two consecutive green weeks per series", one curated week would
        // otherwise hold a series back from cutover with nothing wrong with it.
        //
        // Skipped rather than diffed against the override. Resolving the
        // effective document would answer "does the new engine reproduce a
        // decision legacy never made", which is not a question about the engine.
        //
        // hasOwnCriteria, NOT isOverridden. An imported issue's criteriaSnapshot
        // differs from its series' criteria as a matter of course -- the importer
        // records what each release actually selected, and a series spanning two
        // legacy filter eras carries one setting while its issues need the other.
        // Nobody tailored those. Keying on the comparison rather than on the
        // stored override skipped the ENTIRE imported estate, and the replay test
        // caught it by reporting that nothing had been compared at all.
        if (EffectiveCriteria.hasOwnCriteria(imported)) {
            return "CRITERIA_OVERRIDDEN";
        }
        return null;
    }

    /**
     * Where this release's window opens: the previous release's cut-off.
     *
     * Read from the imported issues AND from earlier shadow runs, taking the
     * later of the two. The archive supplies the chain up to the import, and the
     * shadow runs continue it afterwards -- neither alone spans the changeover,
     * and the first release after an import would otherwise have no predecessor
     * and resolve over an unbounded window.
     */
    /** The issue this legacy release was imported as, or null. */
    private PublicationIssue importedIssue(Publication release) {
        return em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.legacyPublicationId = :id",
                        PublicationIssue.class)
                .setParameter("id", release.getPublicationId())
                .getResultStream().findFirst().orElse(null);
    }

    /**
     * The fallback lower bound, for a release with no imported issue to ask.
     *
     * Kept because the issue is the better answer only when there IS one; this is
     * what the diff used for every release before the issues carried a correct
     * interval of their own.
     */
    private Date previousCutoff(PublicationSeries series, Date before) {
        Date fromIssues = em.createQuery(
                        "SELECT MAX(i.cutoffStampedAt) FROM PublicationIssue i "
                                + "WHERE i.series = :s AND i.cutoffStampedAt < :before", Date.class)
                .setParameter("s", series).setParameter("before", before)
                .getSingleResult();

        Date fromRuns = em.createQuery(
                        "SELECT MAX(r.cutoffAt) FROM ShadowDiffRun r "
                                + "WHERE r.seriesId = :s AND r.cutoffAt < :before", Date.class)
                .setParameter("s", series.getSeriesId()).setParameter("before", before)
                .getSingleResult();

        if (fromIssues == null) {
            return fromRuns;
        }
        if (fromRuns == null) {
            return fromIssues;
        }
        return fromIssues.after(fromRuns) ? fromIssues : fromRuns;
    }

    /**
     * The criteria to resolve with.
     *
     * timeRelation and aliveAtCutoff come from THIS RELEASE'S OWN legacy filter,
     * not from the series row -- the same rule B5.4a2 established for imported
     * issues. A series that spans both the blank/sticky era and the phase era
     * carries one setting on the series and needs the other on 122 of its
     * issues, and a live release is simply the newest of those.
     */
    private ResolvedCriteria criteriaFor(Publication release, PublicationSeries series) {
        LegacyFilterTranslator.Translation t =
                LegacyFilterTranslator.translate(release.getMessageTagFilter());

        return CriteriaResolver.resolve(
                series.getCriteria(),
                t.timeRelation() != null ? t.timeRelation() : series.getTimeRelation(),
                t.aliveAtCutoff(),
                CriteriaResolver.NO_DOMAINS);
    }

    /** What legacy actually recorded, keyed on uid. */
    private Set<String> taggedMessageUids(Publication release) {
        if (release.getMessageTag() == null || release.getMessageTag().getId() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(em.createQuery(
                        "SELECT m.uid FROM MessageTag t JOIN t.messages m WHERE t.id = :id",
                        String.class)
                .setParameter("id", release.getMessageTag().getId())
                .getResultList());
    }

    // ------------------------------------------------------------------ reads

    /** Every run, newest first. */
    public List<ShadowDiffRun> all() {
        return em.createNamedQuery("ShadowDiffRun.all", ShadowDiffRun.class).getResultList();
    }

    /** One series' runs, newest first. */
    public List<ShadowDiffRun> forSeries(String seriesId) {
        return em.createNamedQuery("ShadowDiffRun.bySeries", ShadowDiffRun.class)
                .setParameter("seriesId", seriesId)
                .getResultList();
    }
}
