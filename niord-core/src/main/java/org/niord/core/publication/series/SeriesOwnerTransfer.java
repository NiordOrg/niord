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

package org.niord.core.publication.series;

import org.niord.core.domain.Domain;

/**
 * Moving a publication to another domain, as a decision rather than a request.
 *
 * SEPARATED FROM THE ENDPOINT because the interesting part is which refusals
 * exist and what each one says, and niord-web has no container tests -- so a
 * transfer whose target check lived only inside the resource would be asserted by
 * nothing. Here it is arithmetic over two domains and a boolean, and every branch
 * is reachable without a request.
 *
 * THE PERMISSION IS BOTH ENDS. The source half is the ordinary write guard: the
 * caller has to be sitting at the desk that owns the publication. The target half
 * is here, and it cannot be the same check -- the container evaluates roles
 * against the domain named in the request header, so being an admin HERE says
 * nothing at all about being one THERE.
 *
 * Either half alone is an escape. Source only lets a desk push publications it no
 * longer wants onto somebody who never accepted them; target only lets a desk help
 * itself to another authority's weekly edition. Requiring both means the person
 * who moved it is accountable at both ends, which is also the only person who can
 * explain it afterwards.
 */
public final class SeriesOwnerTransfer {

    private SeriesOwnerTransfer() {
    }

    /**
     * Whether this move may happen, refusing with the reason it may not.
     *
     * The SOURCE half is deliberately not asked here: the caller has already run
     * the ordinary write guard, whose refusal names the owning domain and tells
     * the caller to switch to it. Asking twice would produce two messages for one
     * condition and leave a reader wondering which is authoritative.
     *
     * @param target       the domain the publication is moving to, already resolved
     * @param adminInTarget whether the caller holds admin in that domain
     */
    public static void assertTransferable(PublicationSeries series, Domain target,
                                          boolean adminInTarget) {
        if (series == null || target == null) {
            return;
        }
        String targetId = target.getDomainId();
        String fromId = series.getDomain() == null ? null : series.getDomain().getDomainId();

        if (targetId != null && targetId.equals(fromId)) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "the publication is already owned by '" + targetId + "'");
        }
        if (!target.isActive()) {
            // Not merely tidy. The owner supplies the timezone every future
            // cut-off is read in and decides which admin list the publication
            // appears on -- so a switched-off domain is a desk nobody is sitting
            // at, and the publication would go quiet rather than move.
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "domain '" + targetId + "' is not active; a publication moved there would be "
                            + "administered from a desk nobody is sitting at");
        }
        if (!adminInTarget) {
            throw new PublicationDomainGuard.NotInDomainException(
                    "moving '" + series.getSeriesId() + "' to '" + targetId + "' requires admin in "
                            + "BOTH domains, and the caller is not an admin in '" + targetId
                            + "'. A publication is moved by somebody who is accountable at both "
                            + "ends.");
        }

        // S-20 AGAINST THE DOMAIN IT IS MOVING TO, asked before it moves.
        //
        // The owner is the only source of the zone a cut-off is reckoned in, so a
        // cadenced publication moved to a domain that carries no readable zone
        // would go on scheduling in whatever the server happens to be set to --
        // and it would do it silently, because TimeZone.getTimeZone answers GMT
        // for anything it does not recognise. An hour either way at the year
        // boundary is a different year printed on the cover.
        //
        // Asked ONLY of a cadenced series, exactly as S-20 asks it: a publication
        // with no cadence has no cut-off to read in any zone, and refusing its
        // transfer over a blank one would block a move that costs nothing.
        //
        // Before rather than after, so a refused transfer leaves nothing behind.
        // The alternative -- move, validate, roll back -- relies on the caller's
        // transaction actually rolling back, and this is the endpoint where the
        // audit entry has already been written by then.
        if (series.getCadence() != null && series.getCadence() != SeriesCadence.NONE
                && !SeriesValidator.isReadableZone(target.getTimeZone())) {
            throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                    "S-20: domain '" + targetId + "' carries no readable timezone ("
                            + target.getTimeZone() + "), and the owner is the only source of the "
                            + "zone this publication's cut-offs are reckoned in. Give it one before "
                            + "moving the publication there.");
        }
    }

    /**
     * What a move changed, for the entry that records it.
     *
     * The availability travels because pruning the list can change the SETTING as
     * well as its contents, and an audit entry that showed only the owner moving
     * would leave the reader unable to explain why a publication stopped being
     * shared on the same day.
     */
    public record Moved(String fromDomainId, String toDomainId,
                        SeriesAvailability availabilityBefore, SeriesAvailability availabilityAfter) {

        /** Whether pruning the target collapsed the sharing setting as well. */
        public boolean availabilityChanged() {
            return availabilityBefore != availabilityAfter;
        }
    }

    /**
     * Performs the move on an already-permitted transfer.
     *
     * THE TARGET LEAVES THE SHARING LIST. It is the owner now, which is the
     * strongest form of the same claim, and the read filters the owner out of that
     * list -- so a row left behind would make the stored list and the list the
     * editor shows disagree, and unticking a box that is not displayed is not
     * something anybody can do.
     *
     * AND IF THAT EMPTIES THE LIST, THE SETTING COLLAPSES WITH IT. Moving a
     * publication to the one domain it was shared with leaves SELECTED_DOMAINS
     * naming nobody, which S-20b refuses -- so the very next save of that
     * publication would be rejected for a state the transfer created and no
     * control on the form can fix, because an empty list renders as no rows. It
     * becomes OWNER_ONLY, which is what "shared with nobody" means, and the audit
     * entry says so.
     *
     * NOTHING STAMPED CHANGES. The zone follows the owner, and it is read when a
     * cut-off is decided rather than when one is displayed -- so future cut-offs
     * move and the archive does not. Re-reading a published issue's stamp in a new
     * zone would silently renumber editions somebody has already cited.
     */
    public static Moved moveTo(PublicationSeries series, Domain target) {
        if (series == null || target == null) {
            return null;
        }
        String fromId = series.getDomain() == null ? null : series.getDomain().getDomainId();
        SeriesAvailability before = series.getAvailability();

        series.setDomain(target);
        series.getAvailableDomains().removeIf(
                d -> d != null && d.getDomainId() != null
                        && d.getDomainId().equals(target.getDomainId()));

        if (series.getAvailability() == SeriesAvailability.SELECTED_DOMAINS
                && series.getAvailableDomains().isEmpty()) {
            series.setAvailability(SeriesAvailability.OWNER_ONLY);
        }
        return new Moved(fromId, target.getDomainId(), before, series.getAvailability());
    }
}
