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

import java.time.Instant;
import java.time.ZoneId;

/**
 * When a tag row is too late to have been in the document the issue printed.
 *
 * ONE RULE, TWO READERS. The importer leaves such a row out and the import check
 * reports the ones that got through, and those two answers have to be the same
 * answer -- a check stricter than the importer reports every fresh estate as
 * dirty, and a check looser than the importer never catches anything. Stating it
 * once is what keeps a freshly imported estate reading zero.
 *
 * WHY A ROW CANNOT BE PRINTED LATE. The import copies a legacy publication's
 * message tag verbatim into the issue's frozen members, and a tag is a
 * hand-maintained list that goes on being edited after the document has gone
 * out. A notice published months after the sitting was never on the page: the
 * frozen snapshot is the archive's record of what the issue contained, and a
 * compilation drawing on it would announce the message in a week nobody could
 * have read it in.
 *
 * WHY THE GRACE IS FOURTEEN DAYS. Most imported issues carry a RECONSTRUCTED
 * cut-off -- recovered from an update stamp, from the nominal close of the
 * period or from the public window -- and a reconstructed instant can fall days
 * before the sitting it stands for. An annual firing-area list cut on 1 January
 * lists notices stamped on 2 January; a double week cut on its update stamp
 * carries rows three to fourteen days later. Those rows really were in the
 * document, and the cut-off is what is wrong about them, so the rule is drawn
 * wide enough that a reconstruction cannot turn a genuine member into a
 * discarded one. Beyond two weeks no reconstruction explains the gap and the row
 * is somebody else's notice filed under the nearest tag name.
 *
 * The boundary is a whole calendar day in the series' cut-off zone, extended by
 * the grace: the notices a publication announces are stamped in the same sitting,
 * minutes either side of the publication's own timestamp, so hours are not the
 * measure and a fixed multiple of 24 hours would cut a day in half.
 */
public final class LateMemberRule {

    /**
     * How long after the issue's reference instant a member row is still credible.
     *
     * Fourteen rather than a handful of days because the reference instant is
     * itself reconstructed for most of the estate. See the class comment.
     */
    public static final int GRACE_DAYS = 14;

    private LateMemberRule() {
    }

    /**
     * True when this publish date is too late for the issue to have printed the row.
     *
     * Late means AFTER THE END OF THE DAY that reference + GRACE_DAYS falls in,
     * read in the series' cut-off zone. Midnight of the day after is already too
     * late; anything earlier that day is not.
     *
     * A null reference keeps the row. An issue with neither a cut-off nor a
     * publish stamp states no instant to measure against, and dropping members on
     * the strength of an instant nobody has would discard an archive to enforce a
     * rule that was never evaluated. A null publish date keeps the row for the
     * same reason: there is nothing to compare.
     *
     * A null zone falls back to UTC, which is what a series with no domain
     * answers anyway -- such a series is already invalid and refused elsewhere,
     * and this is here so that judging a row cannot throw.
     */
    public static boolean publishedAfter(Instant reference, ZoneId zone, Instant published) {
        if (reference == null || published == null) {
            return false;
        }
        ZoneId readIn = zone == null ? ZoneId.of("UTC") : zone;
        Instant tooLate = reference.atZone(readIn).toLocalDate()
                .plusDays(GRACE_DAYS + 1L)
                .atStartOfDay(readIn)
                .toInstant();
        return !published.isBefore(tooLate);
    }
}
