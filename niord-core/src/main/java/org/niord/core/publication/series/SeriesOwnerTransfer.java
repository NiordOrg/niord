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
     * NOTHING STAMPED CHANGES. The zone follows the owner, and it is read when a
     * cut-off is decided rather than when one is displayed -- so future cut-offs
     * move and the archive does not. Re-reading a published issue's stamp in a new
     * zone would silently renumber editions somebody has already cited.
     */
    public static void moveTo(PublicationSeries series, Domain target) {
        if (series == null || target == null) {
            return;
        }
        series.setDomain(target);
        series.getAvailableDomains().removeIf(
                d -> d != null && d.getDomainId() != null
                        && d.getDomainId().equals(target.getDomainId()));
    }
}
