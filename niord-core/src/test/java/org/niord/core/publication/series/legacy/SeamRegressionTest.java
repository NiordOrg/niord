package org.niord.core.publication.series.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The holiday seams of the two weekly series, pinned.
 *
 * Seven times in the archive a weekly release was skipped over a holiday and
 * the next issue carried two weeks -- "EfS uge 15-16", "uge 51-52", "uge 52 -
 * 2024 og uge 1 - 2025". Those double-week issues are template-less rows or
 * throwaway clones in legacy, and the importer files them into the weekly series
 * they belong to by ruling. That filing is correct today and must stay correct
 * while the cut-off rule underneath it changes.
 *
 * So this test asserts the part that is NOT allowed to move: for every
 * publication in a seam window, which series it lands in, what it is called,
 * its status, its public window verbatim, and the size of the member list the
 * legacy tag holds. It deliberately does NOT assert intervals or cut-offs --
 * those are what the cut-off rule corrects, and their expected values are
 * asserted separately once the rule has landed.
 *
 * Driven from the CAPTURED estate: fixtures/legacy-estate/seams.json lists the
 * 83 rows, generated from publications.json and members.json rather than typed.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SeamRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    LegacyImportService importService;

    /** One row of the fixture: what the estate holds and where it must land. */
    record Expected(String seam, String publicationId, String seriesId, boolean orphan, String name,
                    IssueStatus status, Long publicFrom, Long publicTo, Integer tagMessageCount) {
    }

    static List<Expected> seams() {
        try (InputStream in = SeamRegressionTest.class.getResourceAsStream("/fixtures/legacy-estate/seams.json")) {
            JsonNode root = MAPPER.readTree(in);
            List<Expected> out = new ArrayList<>();
            for (JsonNode seam : root) {
                for (JsonNode p : seam.get("publications")) {
                    out.add(new Expected(
                            seam.get("seam").asText(),
                            p.get("publicationId").asText(),
                            p.get("seriesId").asText(),
                            p.get("orphan").asBoolean(),
                            p.get("name").asText(),
                            IssueStatus.valueOf(p.get("status").asText()),
                            longOrNull(p.get("publicFrom")),
                            longOrNull(p.get("publicTo")),
                            p.hasNonNull("tagMessageCount") ? p.get("tagMessageCount").asInt() : null));
                }
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("seams.json could not be read", e);
        }
    }

    private static Long longOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asLong();
    }

    private static Long millis(Date d) {
        return d == null ? null : d.getTime();
    }

    private static String daName(PublicationIssue issue) {
        for (PublicationIssueDesc d : issue.getDescs()) {
            if ("da".equals(d.getLang())) {
                return d.getName();
            }
        }
        return issue.getDescs().isEmpty() ? null : issue.getDescs().get(0).getName();
    }

    /**
     * Every seam row lands where the archive says it belongs, named and dated as
     * legacy named and dated it.
     *
     * One assertion per fact rather than one per row, and all failures collected
     * before failing, because a change to the chain order tends to move several
     * rows at once and the useful message is the whole displacement, not its
     * first symptom.
     */
    @Test
    public void theDoubleWeekIssuesAndTheirNeighboursImportUnchanged() {
        List<Expected> expected = seams();
        assertTrue(expected.size() >= 80, "the seam fixture is truncated: " + expected.size());

        LegacyImportService.Plan plan = importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());

        List<String> failures = new ArrayList<>();
        for (Expected e : expected) {
            PublicationIssue issue = plan.issues().get(e.publicationId());
            String who = e.seam() + " / " + e.name() + " (" + e.publicationId() + ")";
            if (issue == null) {
                failures.add(who + ": not in the plan at all");
                continue;
            }
            String seriesId = issue.getSeries() == null ? null : issue.getSeries().getSeriesId();
            if (!e.seriesId().equals(seriesId)) {
                failures.add(who + ": filed under " + seriesId + ", expected " + e.seriesId()
                        + (e.orphan() ? " (an orphan the grouping ruling places)" : ""));
            }
            if (!e.name().equals(daName(issue))) {
                failures.add(who + ": named " + daName(issue));
            }
            if (e.status() != issue.getStatus()) {
                failures.add(who + ": status " + issue.getStatus() + ", expected " + e.status());
            }
            if (!java.util.Objects.equals(e.publicFrom(), millis(issue.getPublicFrom()))) {
                failures.add(who + ": publicFrom " + issue.getPublicFrom() + " is not the legacy window start");
            }
            if (!java.util.Objects.equals(e.publicTo(), millis(issue.getPublicTo()))) {
                failures.add(who + ": publicTo " + issue.getPublicTo() + " is not the legacy window end");
            }
        }

        assertTrue(failures.isEmpty(), "the seams moved:\n  " + String.join("\n  ", failures));
    }

    /**
     * The fixture pins the ESTATE too: the legacy tag behind each seam row holds
     * the member count the fixture recorded. A re-captured estate that changed a
     * double-week issue's contents would fail here rather than silently shifting
     * what the other assertions are measured against.
     */
    @Test
    public void theLegacyTagsBehindTheSeamsHoldTheRecordedMembers() {
        JsonNode members;
        try (InputStream in = SeamRegressionTest.class.getResourceAsStream("/fixtures/legacy-estate/members.json")) {
            members = MAPPER.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("members.json could not be read", e);
        }

        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (Expected e : seams()) {
            if (e.tagMessageCount() == null) {
                continue; // a row with no tag has nothing to count
            }
            JsonNode tag = members.get(e.publicationId());
            int actual = tag == null ? -1 : tag.path("messageCount").asInt(-1);
            if (actual != e.tagMessageCount()) {
                failures.add(e.seam() + " / " + e.name() + ": tag holds " + actual + ", fixture says "
                        + e.tagMessageCount());
            }
            checked++;
        }
        assertTrue(checked >= 75, "too few tagged seam rows checked: " + checked);
        assertTrue(failures.isEmpty(), "the estate behind the seams changed:\n  " + String.join("\n  ", failures));
    }
}
