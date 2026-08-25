package org.niord.core.publication.series.replay;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageTag;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;
import org.niord.core.publication.series.legacy.EstateSlice;
import org.niord.core.publication.series.legacy.LegacyCriteriaTranslation;
import org.niord.core.publication.series.legacy.LegacyIssueTranslation;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The archive, replayed locally: import the interval, resolve it, diff it.
 *
 * This is the loop that used to require a deploy. Every interval defect so far
 * was found by pushing, re-importing 1,077 rows on the test environment and
 * reading the shadow diff -- a twenty-minute round trip, gated on somebody
 * pressing deploy, for what is usually a one-line question. The bugs live at the
 * interval boundaries, and boundaries only need consecutive releases of one
 * cadenced series with their real publish dates.
 *
 * The slice carries those. Release times are the archive's own, so the twenty-
 * to-thirty-minute lag between a nominal cut-off and the release that actually
 * closed it reproduces exactly -- which is the window two separate defects hid
 * in.
 *
 * `updated` is reconstructed from the recovered cut-off. That is not a guess:
 * stage 1 of the recovery cascade IS the legacy `updated` column, and it decided
 * 975 of the estate's 1,077 rows, so for those the reconstruction is exact.
 *
 * WHAT THIS CANNOT SHOW. A frozen member records the message as it was at
 * freeze, and the live archive has moved on: about three quarters of the members
 * a real replay calls missing are now CANCELLED or EXPIRED. Locally they are all
 * still PUBLISHED, so a local replay reads greener than the real one. That decay
 * is a property of time passing rather than of any code, it is uniform, and no
 * fix changes it -- so measuring it is the one thing still worth a deploy.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.publication.series.replay.LocalEstateReplayTest#runnable",
        disabledReason = "needs MySQL and the harvested estate slice")
public class LocalEstateReplayTest {

    /**
     * The series to replay, and how deep.
     *
     * Twenty-four by default -- six months of weeklies, enough boundaries to catch
     * an interval fault, fast enough to belong in the normal suite. Override to
     * sweep the whole archive when hunting something:
     *
     *   -Dniord.replay.series=weekly-ntm-p-t -Dniord.replay.releases=500
     */
    private static final String SERIES =
            System.getProperty("niord.replay.series", "weekly-ntm");
    private static final int RELEASES =
            Integer.getInteger("niord.replay.releases", 24);

    /** Generous: a deep sweep is thousands of inserts plus a resolution per release. */
    private static final int REPLAY_TIMEOUT_SECONDS =
            Integer.getInteger("niord.replay.timeout", 1800);

    public static boolean runnable() {
        return org.niord.core.DatabaseAvailable.isAvailable() && EstateSlice.available();
    }

    @Inject
    EntityManager em;

    @Inject
    ShadowDiffService shadowDiff;

    /**
     * A run of consecutive real releases reproduces itself.
     *
     * The assertion is deliberately about the SHAPE rather than an exact count.
     * Locally every member is still PUBLISHED, so the resolver can find all of
     * them, and what remains to go wrong is the interval: a member that falls
     * outside the period its own issue claims, or one that falls inside two
     * adjacent periods at once. Both are interval defects and both have shipped.
     */
    /**
     * Seeding and diffing carry their own budget rather than the platform default.
     *
     * A deep sweep seeds thousands of messages and then resolves each release, and
     * that does not fit in the default transaction -- the first 150-release run
     * died at sixty seconds with ARJUNA016102, the same shape as the shadow-diff
     * sweep hitting 240s on the server. A test that cannot go deep is a test that
     * only ever checks the shallow case.
     */
    @Test
    public void consecutiveRealReleasesResolveToWhatTheyFroze() {
        QuarkusTransaction.requiringNew().timeout(REPLAY_TIMEOUT_SECONDS).run(this::replay);
    }

    private void replay() {
        List<EstateSlice.Issue> slice = newestReleases();
        assertTrue(slice.size() >= 3, "the slice needs a run of releases to have boundaries at all");

        Seeded seeded = seed(slice);

        int totalMissing = 0;
        int totalExtra = 0;
        int compared = 0;
        List<String> worst = new ArrayList<>();
        List<String> unexplained = new ArrayList<>();
        Set<String> shared = EstateSlice.issuesSharingAMemberSet();

        for (EstateSlice.Issue issue : slice) {
            Publication release = seeded.releases.get(issue.publicId());
            if (release == null) {
                continue;
            }
            ShadowDiffRun run = shadowDiff.diff(release);
            if (run.getSkipReason() != null) {
                continue;
            }
            compared++;
            totalMissing += run.missing().size();
            totalExtra += run.extra().size();
            if (!run.isGreen()) {
                String detail = describe(issue, run, seeded);
                worst.add(detail);
                if (!shared.contains(issue.publicId())) {
                    unexplained.add(detail);
                }
            }
        }

        assertTrue(compared >= 3, "nothing was compared; the slice did not seed a usable estate");

        // Reported rather than silently passed: the numbers are the point of the
        // harness, and a run that compared everything and found nothing is a
        // different fact from one that compared three.
        System.out.println("[LOCAL REPLAY] " + SERIES + ": compared=" + compared
                + " missing=" + totalMissing + " extra=" + totalExtra
                + " red=" + worst.size() + " (" + (worst.size() - unexplained.size())
                + " explained by a reused tag)");
        worst.forEach(w -> System.out.println("[LOCAL REPLAY]   " + w));

        // Every disagreement must be explained by a REUSED TAG, and nothing else.
        //
        // Eight groups of issues in the estate share an identical member set --
        // two of them three ways. They are the New Year turnover editions: legacy
        // could not vary one issue, so a throwaway template was created, one
        // edition published from it, and the tag reused. Two issues claiming the
        // same members means at least one of them is wrong about its own content,
        // and no interval logic can reconcile that.
        //
        // Asserted this way round on purpose. "Ignore the known-bad ones" hides
        // the next real fault among them; "every red is a shared tag" fails the
        // moment a red appears that tag reuse does not explain.
        assertTrue(unexplained.isEmpty(),
                "a release disagreed with its own frozen membership for a reason other than a "
                        + "reused tag. Where the offenders sit relative to the interval says which "
                        + "bound is wrong -- before the open is the lower one, after the close the "
                        + "upper. Offenders: " + unexplained);
    }

    /**
     * A red release, said in enough detail to act on.
     *
     * The counts alone never identify the fault. Where the offending members sit
     * relative to the interval does: before the open is a lower-bound fault, after
     * the close an upper-bound one, and inside means the interval is right and
     * something else selected wrongly.
     */
    private String describe(EstateSlice.Issue issue, ShadowDiffRun run, Seeded seeded) {
        StringBuilder b = new StringBuilder(issue.publicId().substring(0, 8))
                .append(" missing=").append(run.missing().size())
                .append(" extra=").append(run.extra().size())
                .append("  interval ").append(run.getIntervalFrom())
                .append(" -> ").append(run.getCutoffAt());

        for (String uid : run.missing().stream().limit(3).toList()) {
            Date at = seeded.publishedAt.get(uid);
            b.append("\n        MISSING published ").append(at).append(where(at, run));
        }
        for (String uid : run.extra().stream().limit(3).toList()) {
            Date at = seeded.publishedAt.get(uid);
            b.append("\n        EXTRA   published ").append(at).append(where(at, run));
        }
        return b.toString();
    }

    private static String where(Date at, ShadowDiffRun run) {
        if (at == null) {
            return "  (no publish date)";
        }
        if (run.getIntervalFrom() != null && at.before(run.getIntervalFrom())) {
            return "  BEFORE the open";
        }
        if (run.getCutoffAt() != null && !at.before(run.getCutoffAt())) {
            return "  AFTER the close";
        }
        return "  inside";
    }

    // ------------------------------------------------------------------ seeding

    private record Seeded(Map<String, Publication> releases, PublicationSeries series,
                          Map<String, Date> publishedAt) {
    }

    private List<EstateSlice.Issue> newestReleases() {
        List<EstateSlice.Issue> all = EstateSlice.issuesOf(SERIES);
        return all.size() <= RELEASES ? all : all.subList(all.size() - RELEASES, all.size());
    }

    /**
     * Rebuilds the slice as rows: messages, tags, legacy publications, and the
     * imported issues the real translation produces from them.
     */
    private Seeded seed(List<EstateSlice.Issue> slice) {
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId("dma-nm-" + UUID.randomUUID().toString().substring(0, 8));
        ms.setMainType(MainType.NM);
        em.persist(ms);

        PublicationSeries series = importedSeries(ms.getSeriesId());
        Publication template = template(series);

        Map<String, Message> byUid = new LinkedHashMap<>();
        Map<String, Publication> releases = new LinkedHashMap<>();
        // Keyed on the LOCAL uid, which is what the diff reports.
        Map<String, Date> publishedAt = new LinkedHashMap<>();
        Date previousCutoff = null;

        for (EstateSlice.Issue issue : slice) {
            MessageTag tag = new MessageTag();
            tag.setTagId(UUID.randomUUID().toString());
            tag.setName("slice-" + issue.publicId().substring(0, 8));

            for (EstateSlice.Member m : issue.members()) {
                Message msg = byUid.computeIfAbsent(m.uid(), uid -> {
                    Message x = new Message();
                    x.setUid(UUID.randomUUID().toString());
                    x.setMessageSeries(ms);
                    x.setMainType(MainType.NM);
                    // The archive's own type where it is one this model knows.
                    x.setType(typeOf(m.type()));
                    // Locally everything is still PUBLISHED -- see the class comment.
                    x.setStatus(Status.PUBLISHED);
                    x.setPublishDateFrom(m.publishFrom());
                    x.setPublishDateTo(m.publishTo());
                    em.persist(x);
                    return x;
                });
                publishedAt.put(msg.getUid(), msg.getPublishDateFrom());
                tag.getMessages().add(msg);
            }
            em.persist(tag);

            Publication release = new Publication();
            release.setPublicationId(UUID.randomUUID().toString());
            release.setTemplate(template);
            release.setMessageTag(tag);
            release.setStatus(PublicationStatus.ACTIVE);
            release.setPublishDateFrom(issue.publicFrom());
            release.setPublishDateTo(issue.publicTo());
            release.setMessageTagFilter(null);
            // Reconstructed from the recovered stamp; stage 1 of the cascade IS
            // this column, and it decided 975 of the estate's 1,077 rows.
            release.setUpdated(issue.cutoffStampedAt());
            em.persist(release);

            PublicationIssue imported = LegacyIssueTranslation.translate(
                    release, series, new Date(), previousCutoff);
            imported.setPublicId(UUID.randomUUID().toString());
            imported.setLegacyPublicationId(release.getPublicationId());
            imported.setRepoPath("slice/" + release.getPublicationId().substring(0, 8));
            imported.setCutoffStampedAt(issue.cutoffStampedAt());
            em.persist(imported);

            if (imported.effectiveCutoff() != null) {
                previousCutoff = imported.effectiveCutoff();
            }
            releases.put(issue.publicId(), release);
        }

        em.flush();
        return new Seeded(releases, series, publishedAt);
    }

    private static Type typeOf(String legacy) {
        try {
            return legacy == null ? Type.TEMPORARY_NOTICE : Type.valueOf(legacy);
        } catch (IllegalArgumentException e) {
            return Type.TEMPORARY_NOTICE;
        }
    }

    private Publication template(PublicationSeries series) {
        Publication t = new Publication();
        t.setPublicationId(UUID.randomUUID().toString());
        t.setMessageTagFilter(null);
        em.persist(t);
        em.flush();
        series.setLegacyTemplateId(t.getPublicationId());
        em.flush();
        return t;
    }

    private PublicationSeries importedSeries(String messageSeriesId) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("slice-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("slice-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.DRAFT);
        s.setImportSource("legacy-slice");
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.AUTO_ON_PUBLISH);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setCategory(c);
        s.setCriteria(LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""), List.of(messageSeriesId)));
        em.persist(s);
        em.flush();
        return s;
    }
}
