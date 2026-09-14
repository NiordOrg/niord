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

package org.niord.core.publication.series.legacy;

import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.series.IssueShape;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.resolve.IssueNaming;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The annuals of a compiled series that legacy never held, opened by the import.
 *
 * THE PROBLEM IT SOLVES. The legacy archive carries the accumulated annual NtM up
 * to its last hand-assembled edition and then stops, so the years since it have no
 * issue at all. A series whose newest period ended several cadence periods ago
 * reads as DORMANT, and gap detection switches itself off on a dormant series --
 * so the missing years are not even reported as missing. The alternative to
 * creating them here is creating them by hand in the go-live window, once per year
 * per rehearsal, which is exactly the kind of correction a ruling exists to stop
 * being repeated.
 *
 * A COMPILATION ONLY, AND NEVER A CADENCE. What is backfilled is keyed strictly on
 * {@link LegacyTemplateRulings#compilationFor} -- the ruling that turns a series
 * into one COMPILED_FROM_SOURCE -- and never on the series being YEARLY. The
 * reason is that a compilation's content for a year it never published is still
 * DERIVABLE: its members are the union of the source series' published issues for
 * that period, and those rows are in the archive whether or not anybody ever
 * assembled the document. A yearly whose content is not derivable has nothing to
 * backfill FROM, so an issue created for it would be an empty row claiming a
 * period nobody published -- which is why the other yearly series in the estate
 * come out of the import exactly as legacy has them.
 *
 * THE SAME SHAPE THE CREATE DIALOG WOULD PRODUCE. The bounds are the calendar
 * year's own, read in the owner's zone, and everything else -- the numbers, the
 * per-language names, the first edition -- comes from {@link IssueShape} and
 * {@link IssueLifecycleService#suggestName}, which is what an admin pressing
 * create goes through. Deriving them a second time here would make the eight rows
 * the import leaves behind differ from the ninth somebody creates next January.
 *
 * Package-visible, because opening a period nobody published is the importer's
 * privilege alone: everywhere else an issue comes into being because somebody
 * asked for it.
 */
final class CompilationBackfill {

    private CompilationBackfill() {
    }

    /**
     * The years this series should have an issue for and has none.
     *
     * From the newest year the archive already covers, exclusive, to the year
     * "now" falls in, inclusive -- both read in the series' own cut-off zone,
     * because a publication's year is the owner desk's year and not the server's.
     *
     * NOTHING AT ALL WHERE THE ARCHIVE HOLDS NOTHING. A compilation series that
     * never had an annual is not a series with a gap: it is a series whose first
     * issue is a decision nobody has taken yet, and opening one from the first year
     * its source series happens to cover would invent a publication history.
     *
     * A year the archive already covers is skipped rather than doubled. It cannot
     * happen while the newest year bounds the range from below, and it is written
     * down anyway because the rule is "one issue per year", not "everything after
     * the last one".
     *
     * @param existing the issues the import has for this series; at import time
     *                 that is every issue it has at all
     */
    static List<Integer> missingYears(PublicationSeries series,
                                      Collection<PublicationIssue> existing, Date now) {
        if (series == null || existing == null || existing.isEmpty() || now == null) {
            return List.of();
        }
        ZoneId zone = series.cutoffZone();

        Set<Integer> covered = new LinkedHashSet<>();
        for (PublicationIssue issue : existing) {
            Integer year = yearOf(issue, zone);
            if (year != null) {
                covered.add(year);
            }
        }
        if (covered.isEmpty()) {
            return List.of();
        }

        int newest = Collections.max(covered);
        int current = ZonedDateTime.ofInstant(now.toInstant(), zone).getYear();

        List<Integer> years = new ArrayList<>();
        for (int year = newest + 1; year <= current; year++) {
            if (!covered.contains(year)) {
                years.add(year);
            }
        }
        return years;
    }

    /**
     * The calendar year an issue belongs to: the year its period CLOSED in.
     *
     * The close and not the start, for the same reason the naming derives from the
     * cut-off: an edition is the edition for the year it completes, and an annual
     * whose window opens on 1 January is described by the December at the other
     * end of it. An issue with no close at all -- an unreleased row whose cut-off
     * could not be recovered -- says nothing about which year is covered and is
     * passed over rather than guessed at.
     */
    private static Integer yearOf(PublicationIssue issue, ZoneId zone) {
        Date cutoff = issue == null ? null : issue.effectiveCutoff();
        return cutoff == null ? null
                : ZonedDateTime.ofInstant(cutoff.toInstant(), zone).getYear();
    }

    /**
     * One OPEN annual for a calendar year, shaped as the create dialog shapes one.
     *
     * The period is the year itself in the owner's zone: 1 January 00:00:00.000 to
     * 31 December 23:59:59.999, which is the pair the draft proposes for a YEARLY
     * series whose cut-off falls on the period's own end. Both bounds are NOMINAL,
     * because nothing has been released yet and a nominal close is exactly what an
     * issue waiting to be published carries.
     *
     * NO MEMBERS AND NO STAMP. A compilation resolves its members when it is
     * published, from the source series' frozen rows, so freezing any here would
     * record an answer several years before the question is asked. And no
     * legacyPublicationId: there is no legacy row to point at, which is the fact
     * that makes this issue distinguishable from the seventeen the archive did
     * hold.
     */
    static PublicationIssue annualFor(PublicationSeries series, int year) {
        ZoneId zone = series.cutoffZone();

        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series);
        // Minted here and immutable for life, exactly as the create path mints it:
        // it is the id message HTML cites and the public download link carries.
        issue.setPublicId(UUID.randomUUID().toString());
        issue.setRepoPath("publications/" + issue.getPublicId());
        issue.setStatus(IssueStatus.OPEN);
        issue.setIntervalFrom(Date.from(
                ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, zone).toInstant()));
        issue.setIntervalFromSource(IntervalBoundSource.NOMINAL);
        issue.setIntervalTo(Date.from(
                ZonedDateTime.of(year, 12, 31, 23, 59, 59, 999_000_000, zone).toInstant()));
        issue.setIntervalToSource(IntervalBoundSource.NOMINAL);
        // The first edition of the period. A file-name pattern that names the
        // edition expands against this, and an issue born without one publishes as
        // "…-v-2027.pdf" -- an empty substitution rather than an unresolved token,
        // so nothing refuses it.
        issue.setEdition(IssueShape.FIRST_EDITION_TEXT);

        // One desc row per configured language from the moment of create: a
        // language the series declares and the issue has no row for has nowhere to
        // put its file name or its link.
        for (String lang : series.getLanguages()) {
            issue.createDesc(lang);
        }

        // The numbers off the issue's own close, through the static seam the import
        // already numbers every other issue through -- no persistence context, and
        // no span, because a span is a weekly publication's way of saying it
        // swallowed a period and an annual has none to swallow.
        IssueNaming.Numbers numbers = IssueShape.applyNumbers(issue, series);
        for (PublicationIssueDesc desc : issue.getDescs()) {
            String suggested = IssueLifecycleService.suggestName(series, desc.getLang(), numbers);
            if (suggested != null && !suggested.isBlank()) {
                desc.setName(suggested);
            }
        }
        return issue;
    }
}
