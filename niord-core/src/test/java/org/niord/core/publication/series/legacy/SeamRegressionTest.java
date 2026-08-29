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
     * What the cut-off rule CORRECTS at the seams, asserted once it has landed.
     *
     * Every released weekly row carries a cut-off, and it lies within a day after
     * the nominal close the public window names -- never a creation stamp from a
     * week earlier, never an edit from five days later. Along the tiling series
     * each issue opens where the one before it closed, with a withdrawal and its
     * replacement treated as one release. And each seam holds exactly one span
     * longer than a week between consecutive closes: the double week, once --
     * not twice, which is what a mis-recovered stamp produced before.
     */
    @Test
    public void theSeamsChainWithoutOverlapOrPhantomGaps() {
        LegacyImportService.Plan plan = importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());

        java.util.Map<String, List<Expected>> groups = new java.util.LinkedHashMap<>();
        for (Expected e : seams()) {
            groups.computeIfAbsent(e.seam() + " / " + e.seriesId(), k -> new ArrayList<>()).add(e);
        }

        List<String> failures = new ArrayList<>();
        for (java.util.Map.Entry<String, List<Expected>> g : groups.entrySet()) {
            List<PublicationIssue> issues = new ArrayList<>();
            for (Expected e : g.getValue()) {
                PublicationIssue issue = plan.issues().get(e.publicationId());
                if (issue != null && issue.getStatus() != IssueStatus.OPEN) {
                    issues.add(issue);
                }
            }
            issues.sort(java.util.Comparator
                    .comparing((PublicationIssue i) -> i.getPublicFrom().getTime())
                    .thenComparing(PublicationIssue::getPublicId));

            boolean tiling = g.getKey().endsWith("weekly-ntm");
            Date previousClose = null;   // the close of the previous release group
            Date groupOpen = null;       // what the current release group opened at
            Date groupRelease = null;
            Date groupClose = null;
            List<Date> closes = new ArrayList<>();

            for (PublicationIssue issue : issues) {
                String who = g.getKey() + " / " + daName(issue);
                Date cutoff = issue.getCutoffStampedAt();
                Date nominal = issue.getPublicFrom();

                if (cutoff == null || CutoffRecovery.MANUAL.equals(issue.getCutoffSource())) {
                    failures.add(who + ": no cut-off (" + issue.getCutoffSource() + ")");
                    continue;
                }
                if (cutoff.getTime() < nominal.getTime() - CutoffRecovery.RELEASE_LEAD_MS
                        || cutoff.getTime() > nominal.getTime() + CutoffRecovery.RELEASE_SLACK_MS) {
                    failures.add(who + ": cut-off " + cutoff + " is not close to the nominal close "
                            + nominal + " (" + issue.getCutoffSource() + ")");
                }

                boolean sibling = groupRelease != null
                        && Math.abs(nominal.getTime() - groupRelease.getTime()) <= CutoffRecovery.AGREEMENT_WINDOW_MS;
                if (!sibling) {
                    if (groupClose != null) {
                        closes.add(groupClose);
                    }
                    previousClose = groupClose;
                    groupOpen = previousClose;
                    groupRelease = nominal;
                    groupClose = null;
                }
                if (groupClose == null || issue.getStatus() == IssueStatus.PUBLISHED) {
                    groupClose = issue.effectiveCutoff();
                }

                // The first group of the window has its predecessor outside it.
                if (tiling && groupOpen != null
                        && !java.util.Objects.equals(millis(issue.getIntervalFrom()), millis(groupOpen))) {
                    failures.add(who + ": opens at " + issue.getIntervalFrom()
                            + " but the previous release closed at " + groupOpen);
                }
            }
            if (groupClose != null) {
                closes.add(groupClose);
            }

            int longSpans = 0;
            for (int i = 1; i < closes.size(); i++) {
                long days = (closes.get(i).getTime() - closes.get(i - 1).getTime()) / 86_400_000L;
                if (days > 10) {
                    longSpans++;
                }
            }
            int expectedSpans = g.getKey().equals("2016-turnover / weekly-ntm-p-t") ? 0 : 1;
            if (longSpans != expectedSpans) {
                failures.add(g.getKey() + ": " + longSpans + " span(s) longer than a week between closes, "
                        + "expected " + expectedSpans + " (the double week, once)");
            }
        }

        assertTrue(failures.isEmpty(), "the seams are not chained cleanly:\n  "
                + String.join("\n  ", failures));
    }

    /**
     * The week each seam row is numbered by is the week its own cut-off falls in.
     *
     * IMPORTED ISSUES USED TO CARRY A YEAR AND NO WEEK. On the wire that is an
     * issue belonging to a year but to none of its weeks, and every list that
     * labels a cell by its week fell back to printing the year on all of them.
     * The numbers now come from the same derivation a natively created issue
     * uses, off the cut-off the recovery cascade settled.
     *
     * THE CLOSING week, not the opening one. An ordinary weekly window runs
     * Wednesday to Wednesday and straddles two ISO weeks, so a row numbered from
     * its start is one week behind the archive on every single issue; a double
     * week carries the pair and is numbered for the last of them.
     */
    @Test
    public void everySeamRowIsNumberedByTheWeekItsCutoffFallsIn() {
        LegacyImportService.Plan plan = importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());

        List<String> failures = new ArrayList<>();
        int numbered = 0;
        for (Expected e : seams()) {
            PublicationIssue issue = plan.issues().get(e.publicationId());
            if (issue == null || issue.getStatus() == IssueStatus.OPEN) {
                continue;
            }
            Date cutoff = issue.effectiveCutoff();
            if (cutoff == null) {
                continue;   // an archive row whose release could not be believed
            }
            String who = e.seam() + " / " + e.name();
            java.time.ZonedDateTime end = cutoff.toInstant().atZone(issue.getSeries().cutoffZone());
            int isoWeek = end.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            int isoYear = end.get(java.time.temporal.WeekFields.ISO.weekBasedYear());

            if (issue.getWeek() == null || issue.getYear() == null) {
                failures.add(who + ": week " + issue.getWeek() + ", year " + issue.getYear()
                        + " -- a weekly issue with no week belongs to a year but to none of its weeks");
                continue;
            }
            numbered++;
            int closing = issue.getWeekTo() == null ? issue.getWeek() : issue.getWeekTo();
            if (closing != isoWeek) {
                failures.add(who + ": numbered " + issue.getWeek()
                        + (issue.getWeekTo() == null ? "" : "+" + issue.getWeekTo())
                        + " but its cut-off " + cutoff + " falls in ISO week " + isoWeek);
            }
            if (issue.getYear() != isoYear) {
                failures.add(who + ": year " + issue.getYear() + ", but the cut-off falls in ISO "
                        + "week-year " + isoYear + " -- which is the year a week-numbered issue takes");
            }
            if (issue.getWeekTo() != null && issue.getIntervalFrom() == null) {
                failures.add(who + ": carries a second week with no lower bound to have spanned it");
            }
        }

        assertTrue(numbered >= 75, "too few seam rows were numbered at all: " + numbered);
        assertTrue(failures.isEmpty(), "the seam rows are numbered wrongly:\n  "
                + String.join("\n  ", failures));
    }

    /**
     * A skipped release leaves exactly one issue per chain carrying two weeks --
     * the one whose CONTENT period absorbed the missing one.
     *
     * AND THAT IS NOT ALWAYS THE ONE THE ARCHIVE TITLED FOR A PAIR. Legacy's
     * titles name the weeks an edition would be CURRENT for, and the holiday was
     * worked around in both directions: at Easter 2017 the Good Friday release was
     * skipped and the next Friday's edition, titled "uge 15-16", really does carry
     * two weeks of notices. At Christmas the edition came out EARLY -- "EfS uge
     * 51-52 - 2017" was released on the Friday of week 51 with one week of content
     * in it, and the fortnight that followed was collected by "EfS uge 1 - 2018".
     *
     * The numbers follow the content, because that is what they are numbers of:
     * an issue's week is the week its period closed and its second week exists
     * only where the period swallowed one. Where the archive titled the other row,
     * the title is left exactly as the archive wrote it -- it is what the edition
     * is cited by in message HTML that is already public -- so at those four seams
     * the stored title and the stored numbers describe different halves of the
     * same event, on purpose.
     */
    @Test
    public void exactlyOneIssuePerSeamCarriesTheWeekThatWasSkipped() {
        LegacyImportService.Plan plan = importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());

        // Counted as distinct PAIRS rather than rows, because a withdrawal and its
        // replacement are two rows of one release and describe one period: at the
        // 2024 turn of the year "EfS uge 1 - 2025" was retired and "EfS uge 2 -
        // 2025" replaced it minutes later, and both correctly carry 1-2.
        java.util.Map<String, java.util.Set<String>> doubles = new java.util.LinkedHashMap<>();
        java.util.Set<String> groups = new java.util.LinkedHashSet<>();
        for (Expected e : seams()) {
            String group = e.seam() + " / " + e.seriesId();
            groups.add(group);
            PublicationIssue issue = plan.issues().get(e.publicationId());
            if (issue != null && issue.getWeekTo() != null) {
                doubles.computeIfAbsent(group, k -> new java.util.LinkedHashSet<>())
                        .add(issue.getWeek() + "-" + issue.getWeekTo());
            }
        }

        List<String> failures = new ArrayList<>();
        for (String group : groups) {
            // An in-force chain has no lower bound on any of its issues -- it says
            // what stood at an instant, not what accumulated since one -- so it has
            // no span to be two periods long and never carries a second week.
            int expected = group.endsWith("weekly-ntm") ? 1 : 0;
            java.util.Set<String> found = doubles.getOrDefault(group, java.util.Set.of());
            if (found.size() != expected) {
                failures.add(group + ": " + found.size() + " double week(s) " + found
                        + ", expected " + expected);
            }
        }
        assertTrue(failures.isEmpty(), "the skipped weeks are not accounted for once each:\n  "
                + String.join("\n  ", failures));
    }

    /**
     * The two seam shapes, pinned by hand off the release stamps.
     *
     * Easter 2017: released 24 and 31 March and 7 April, nothing on Good Friday
     * (14 April), then 21 April -- so the 21 April edition closes a fortnight and
     * is 15-16, which is also what the archive called it.
     *
     * Christmas 2017: released 13 and 20 December, nothing until 3 January -- so
     * the 20 December edition closes one week and is 51, while 3 January closes a
     * fortnight and is 52-1. The archive titled them "uge 51-52 - 2017" and "uge 1
     * - 2018", for the weeks each would be current.
     */
    @Test
    public void bothSeamShapesAreNumberedByTheContentTheyClose() {
        LegacyImportService.Plan plan = importService.planFrom(
                LegacyEstateFixture.templates(), LegacyEstateFixture.publications());

        record Numbered(String publicationId, String name, int week, Integer weekTo, int year) {
        }
        List<Numbered> expected = List.of(
                // released after the gap: the title and the content agree
                new Numbered("dd5b9df3-1300-483e-8c9d-5f9c012d4be6", "EfS uge 14 - 2017", 14, null, 2017),
                new Numbered("4ed6ccd8-ef28-434f-9ba6-0f81606260cc", "EfS uge 15-16 - 2017", 15, 16, 2017),
                new Numbered("31d1648b-5c81-42d2-9d95-f33e64ffc891", "EfS uge 17 - 2017", 17, null, 2017),
                // released before the gap: the fortnight lands on the issue after
                new Numbered("e47e571e-df1d-4b67-a45f-5d449a49385b", "EfS uge 51-52 - 2017", 51, null, 2017),
                new Numbered("d33772db-8412-4866-88ee-78af3b3e7ea4", "EfS uge 1 - 2018", 52, 1, 2018),
                new Numbered("fa18b04c-b228-4541-b8d9-3d8502b84344", "EfS uge 2 - 2018", 2, null, 2018),
                // the turn of the year, where the pair straddles two week-years and
                // the issue takes the year of the week it CLOSED in
                new Numbered("98257761-971f-4b82-af9e-616d676e9740", "EfS uge 2 - 2025", 1, 2, 2025));

        List<String> failures = new ArrayList<>();
        for (Numbered n : expected) {
            PublicationIssue issue = plan.issues().get(n.publicationId());
            if (issue == null) {
                failures.add(n.name() + ": not in the plan");
                continue;
            }
            String actual = issue.getWeek() + (issue.getWeekTo() == null ? "" : "-" + issue.getWeekTo())
                    + " / " + issue.getYear();
            String wanted = n.week() + (n.weekTo() == null ? "" : "-" + n.weekTo()) + " / " + n.year();
            if (!wanted.equals(actual)) {
                failures.add(n.name() + ": numbered " + actual + ", expected " + wanted);
            }
        }
        assertTrue(failures.isEmpty(), "the seam numbering moved:\n  " + String.join("\n  ", failures));
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
