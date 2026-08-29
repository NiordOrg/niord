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

import org.junit.jupiter.api.Test;
import org.niord.core.message.MessageTag;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.PublicationIssue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which stamp on an annual in-force row is its RELEASE, and which is somebody
 * tidying up around it.
 *
 * Such a row can carry two stamps that might be the release: when it was last
 * written, and when its member list was assembled. Both are also written by
 * things that are not releases -- a clone made a year in advance, an edit made
 * in some later year, and above all the changeover that REPLACES the edition,
 * which deactivates the outgoing row minutes after assembling the incoming one.
 *
 * The stamp that survives decides two facts at once: the publication moment, and
 * which day the cut-off falls on. Getting it wrong dates an edition to the day
 * its own replacement went out.
 */
public class AnnualInForceReleaseTest {

    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");

    private static Date at(int y, int m, int d, int h, int min) {
        return Date.from(ZonedDateTime.of(y, m, d, h, min, 0, 0, CPH).toInstant());
    }

    private static Publication row(Date updated, Date tagCreated) {
        Publication p = new Publication();
        p.setPublicationId("p-" + (updated == null ? "x" : updated.getTime()));
        p.setUpdated(updated);
        if (tagCreated != null) {
            MessageTag tag = new MessageTag();
            tag.setCreated(tagCreated);
            p.setMessageTag(tag);
        }
        return p;
    }

    /** A chain entry: what its tag was assembled at, and when its own window opens. */
    private static Publication entry(Date tagCreated, Date opens) {
        Publication p = row(null, tagCreated);
        p.setPublicationId("p-" + (tagCreated == null ? "x" : tagCreated.getTime()));
        p.setPublishDateFrom(opens);
        return p;
    }

    /** An in-force edition: one window, and no lower content bound. */
    private static PublicationIssue window(Date from, Date to) {
        PublicationIssue issue = new PublicationIssue();
        issue.setPublicFrom(from);
        issue.setPublicTo(to);
        issue.setIntervalFrom(null);
        issue.setIntervalTo(from);
        return issue;
    }

    // ------------------------------------------------------- the two candidates

    /** The row's last write, when it is the later credible stamp. */
    @Test
    public void theLastWriteWinsWhenItIsTheLaterCredibleStamp() {
        Publication p = row(at(2025, 2, 26, 12, 12), at(2025, 2, 26, 12, 11));
        assertEquals(at(2025, 2, 26, 12, 12),
                LegacyImportService.annualInForceRelease(
                        p, window(at(2025, 1, 1, 12, 10), at(2025, 12, 31, 12, 10)), null),
                "the release completes with its last credible write");
    }

    /**
     * The row's OWN tag creation, when the last write is not credible.
     *
     * The second 2022 firing edition's row was next written a year later, so
     * without this candidate it has no release stamp at all and dates to the day
     * its window opened -- four weeks before its own changeover.
     */
    @Test
    public void theOwnTagCreationWinsWhenTheLastWriteIsOutsideTheWindow() {
        Publication p = row(at(2023, 1, 2, 11, 1), at(2022, 2, 2, 14, 46));
        assertEquals(at(2022, 2, 2, 14, 46),
                LegacyImportService.annualInForceRelease(
                        p, window(at(2022, 1, 5, 7, 22), at(2022, 12, 31, 7, 22)), null),
                "the tag was assembled inside the window; the later edit was not");
    }

    /** Neither stamp credible: nothing witnessed the release. */
    @Test
    public void neitherCandidateCredibleIsNoReleaseAtAll() {
        Publication p = row(at(2024, 1, 3, 8, 6), at(2019, 5, 5, 9, 0));
        assertNull(LegacyImportService.annualInForceRelease(
                        p, window(at(2023, 1, 2, 10, 57), at(2023, 12, 31, 10, 57)), null),
                "one stamp predates the window, the other postdates it");
    }

    /** A stamp older than the edition is a leftover from whatever the row was before. */
    @Test
    public void aStampBeforeTheWindowOpensIsNotThisEditionsRelease() {
        Publication p = row(null, at(2025, 12, 30, 10, 40));
        assertNull(LegacyImportService.annualInForceRelease(
                        p, window(at(2026, 1, 1, 12, 10), at(2026, 12, 31, 12, 10)), null),
                "a tag cloned into place before the window opened witnesses nothing here");
    }

    // ----------------------------------------------------------- the withdrawal

    /**
     * A write at or after the replacement's tag creation is the WITHDRAWAL.
     *
     * The 2022 firing pair, to the minute: the incoming edition's tag was created
     * 14:46:50 and the outgoing row was last written 14:52:17. Believed, that
     * write dates the outgoing edition to 2 February -- the day its replacement
     * went out, four weeks after the outgoing edition itself did.
     */
    @Test
    public void aWriteAtOrAfterTheReplacementIsTheWithdrawal() {
        Date replacedAt = at(2022, 2, 2, 14, 46);
        Publication p = row(at(2022, 2, 2, 14, 52), at(2022, 1, 5, 11, 12));

        assertEquals(at(2022, 1, 5, 11, 12),
                LegacyImportService.annualInForceRelease(
                        p, window(at(2022, 1, 5, 7, 22), at(2022, 12, 31, 7, 22)), replacedAt),
                "the withdrawal is rejected and the edition's own tag stands as its release");

        // AT the replacement instant, not merely after it: one action assembles
        // the incoming edition and touches the outgoing one, and the two stamps
        // can land on the same millisecond.
        Publication same = row(replacedAt, null);
        assertNull(LegacyImportService.annualInForceRelease(
                        same, window(at(2022, 1, 5, 7, 22), at(2022, 12, 31, 7, 22)), replacedAt),
                "a write at the very instant of the replacement is part of it");
    }

    // ---------------------------------------------------------------- the ceiling

    /**
     * The window's own end is the ceiling where the row states one.
     *
     * An edit made in a later year is not this edition's release however
     * plausible its timestamp looks on its own.
     */
    @Test
    public void theWindowEndIsTheCeilingWhereTheRowStatesOne() {
        Publication p = row(at(2024, 1, 3, 8, 6), null);
        assertNull(LegacyImportService.annualInForceRelease(
                        p, window(at(2023, 1, 2, 10, 57), at(2023, 12, 31, 10, 57)), null));
    }

    /**
     * With NO window end, the replacement moment is the ceiling -- and with
     * neither, there is no ceiling at all.
     *
     * The content interval must not be used to stand in here. An in-force
     * edition's interval ENDS at its window open, so a ceiling read from it
     * would collapse the whole test to "the stamp equals the window open" and no
     * real release would ever pass.
     */
    @Test
    public void withNoWindowEndTheReplacementIsTheCeilingAndOtherwiseThereIsNone() {
        Date from = at(2022, 1, 5, 7, 22);
        Publication p = row(at(2022, 6, 1, 9, 0), null);

        assertEquals(at(2022, 6, 1, 9, 0),
                LegacyImportService.annualInForceRelease(p, window(from, null), null),
                "no window end and no replacement: the stamp stands on the floor alone");

        assertEquals(at(2022, 6, 1, 9, 0),
                LegacyImportService.annualInForceRelease(
                        p, window(from, null), at(2022, 9, 1, 9, 0)),
                "inside the replacement ceiling");

        assertNull(LegacyImportService.annualInForceRelease(
                        p, window(from, null), at(2022, 3, 1, 9, 0)),
                "past the replacement ceiling");

        // The interval end is the window open for this shape; if it were read as
        // a ceiling this stamp could not survive.
        assertEquals(from, window(from, null).getIntervalTo(),
                "the premise: an in-force edition's interval ends where its window opens");
    }

    /** No window at all is nothing to judge a stamp against. */
    @Test
    public void withoutAWindowThereIsNothingToBelieve() {
        Publication p = row(at(2021, 1, 15, 8, 10), at(2021, 1, 6, 11, 31));
        assertNull(LegacyImportService.annualInForceRelease(p, window(null, null), null));
    }

    // --------------------------------------------------- when the edition ended

    private static List<Publication> chain(Publication... rows) {
        return new ArrayList<>(List.of(rows));
    }

    /**
     * A RE-EDITION taking over during this window is the replacement.
     *
     * The 2022 firing pair: both windows open on 5 January, and the second
     * edition was assembled on 2 February. That is what ends the first one.
     */
    @Test
    public void aReEditionOpeningInsideThisWindowIsTheReplacement() {
        Date opens = at(2022, 1, 5, 7, 22);
        Date closes = at(2022, 12, 31, 7, 22);
        List<Publication> c = chain(
                entry(at(2022, 1, 5, 11, 12), opens),
                entry(at(2022, 2, 2, 14, 46), opens));   // same window, assembled four weeks later
        assertEquals(at(2022, 2, 2, 14, 46),
                CutoffRecovery.replacedAt(c, 0, opens, closes));
    }

    /**
     * NEXT PERIOD is not a replacement, however early its list was assembled.
     *
     * The 2027 firing row's tag was created on 2 January 2026 -- the instant the
     * 2026 edition was released. Read as that edition's replacement it rejects
     * the edition's own release stamp and leaves it with no publication moment
     * at all. An edition ends where the next one's WINDOW opens, not where
     * somebody first assembled a list for it.
     */
    @Test
    public void theNextPeriodsRowIsNotAReplacement() {
        Date opens = at(2026, 1, 1, 12, 10);
        Date closes = at(2026, 12, 31, 12, 10);
        List<Publication> c = chain(
                entry(at(2025, 12, 30, 10, 40), opens),
                entry(at(2026, 1, 2, 9, 42), at(2027, 1, 1, 12, 10)));  // next year's window
        assertNull(CutoffRecovery.replacedAt(c, 0, opens, closes),
                "a tag assembled for next year's edition does not end this year's");
    }

    /**
     * A neighbour whose tag predates this edition is SKIPPED, and the scan goes
     * on to the first one that could be the replacement.
     *
     * Between the two 2022 firing editions sits a row whose title never had its
     * year substituted, carrying a tag created in January 2020. Taken as the
     * bound it rejects every stamp on the outgoing edition -- including that
     * edition's own tag, which is its release.
     */
    @Test
    public void aNeighbourWhoseTagPredatesThisEditionIsSkipped() {
        Date opens = at(2022, 1, 5, 7, 22);
        Date closes = at(2022, 12, 31, 7, 22);
        List<Publication> c = chain(
                entry(at(2022, 1, 5, 11, 12), opens),   // the outgoing edition
                entry(at(2020, 1, 8, 11, 39), opens),   // the row with the unsubstituted title
                entry(at(2022, 2, 2, 14, 46), opens));  // the incoming edition
        assertEquals(at(2022, 2, 2, 14, 46),
                CutoffRecovery.replacedAt(c, 0, opens, closes),
                "the 2020 tag cannot be the moment a 2022 edition was replaced");
    }

    /** The newest edition of a chain has not been replaced. */
    @Test
    public void theNewestEditionHasNoReplacement() {
        List<Publication> c = chain(entry(at(2025, 2, 26, 12, 11), at(2025, 1, 1, 12, 10)));
        assertNull(CutoffRecovery.replacedAt(c, 0, at(2025, 1, 1, 12, 10), at(2025, 12, 31, 12, 10)));
        assertNull(CutoffRecovery.replacedAt(c, 0, null, null), "and no window is no question");
    }

    /** The EARLIEST qualifying tag bounds it, not merely the next one in order. */
    @Test
    public void theEarliestQualifyingTagIsTheBound() {
        Date opens = at(2022, 1, 5, 7, 22);
        Date closes = at(2022, 12, 31, 7, 22);
        List<Publication> c = chain(
                entry(at(2022, 1, 5, 11, 12), opens),
                entry(at(2022, 6, 1, 9, 0), at(2022, 6, 1, 8, 0)),
                entry(at(2022, 3, 1, 9, 0), at(2022, 3, 1, 8, 0)));
        assertEquals(at(2022, 3, 1, 9, 0),
                CutoffRecovery.replacedAt(c, 0, opens, closes));
    }

    /**
     * With no stated window end there is no span to open inside, so any later
     * tag made after this window opened stands as the bound.
     */
    @Test
    public void withNoWindowEndAnyLaterTagAfterTheOpenBounds() {
        Date opens = at(2022, 1, 5, 7, 22);
        List<Publication> c = chain(
                entry(at(2022, 1, 5, 11, 12), opens),
                entry(at(2022, 2, 2, 14, 46), at(2027, 1, 1, 12, 10)));
        assertEquals(at(2022, 2, 2, 14, 46),
                CutoffRecovery.replacedAt(c, 0, opens, null));
    }
}
