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

package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;

/**
 * Who may WRITE a publication series, and the issues under it.
 *
 * An admin works inside one domain at a time -- the domain the client names on
 * every request -- and the rest of the admin surface already scopes its writes
 * that way: an editor may only change a message whose series belongs to the
 * domain they are currently in, and the strongest role does not lift that,
 * because the check is about WHICH DESK the caller is sitting at, not about how
 * much they are trusted. Publications were the one admin surface that did not
 * ask, so an admin of one authority could retire another authority's weekly
 * edition -- a change the public reads -- from a screen that gave no hint the
 * series was not theirs.
 *
 * THE RULE, in two halves.
 *
 * A series that NAMES a domain is that domain's to write. The domain is not
 * decoration on a series: it carries the timezone every cut-off is read in and
 * it decides which desk is accountable for what the series publishes, so
 * "belongs to" is a real claim and this is what enforces it.
 *
 * A series with NO domain is writable by ANY admin. That half is deliberate and
 * it is not an oversight to be tightened later. A domainless series is visible
 * from every domain -- that is what a null domain MEANS here -- and a shared
 * publication nobody can edit is worse than one anybody can: there is no domain
 * to switch to in order to gain the right, so the alternative is a series that
 * is permanently read-only through the UI and only reachable by editing the
 * database.
 *
 * READS ARE NOT SCOPED. Every admin may look at every series, because the list
 * screens, the pickers and the citation surfaces all cross domains by design and
 * a caller who cannot see a series cannot be told why their write was refused.
 * This is a write guard and nothing else.
 *
 * The refusal is a 403 carrying {@link #NOT_IN_DOMAIN}, so a client can hide the
 * action rather than offer a button that always fails.
 */
@ApplicationScoped
public class PublicationDomainGuard {

    /**
     * The wire code for a write aimed at another domain's series.
     *
     * A code rather than a bare 403 because the client has to tell this apart
     * from "you are not an admin": the first is fixed by switching domain, the
     * second cannot be fixed by the caller at all, and offering the wrong remedy
     * sends somebody to ask for a role they already have.
     */
    public static final String NOT_IN_DOMAIN = "NOT_IN_DOMAIN";

    @Inject
    DomainService domainService;

    /** A write aimed at a series the caller's current domain does not own. */
    public static class NotInDomainException extends PublicationException {

        public NotInDomainException(String message) {
            super(NOT_IN_DOMAIN, message);
        }
    }

    // ------------------------------------------------------------ the decision

    /**
     * The rule itself, as a pure function of the two domains.
     *
     * Separated from the lookup so it can be read, and tested, without a request
     * or a container -- the decision is three lines and the interesting part is
     * which three.
     *
     * @param seriesDomain  the domain the series names, or null when it names none
     * @param currentDomain the caller's current domain, or null when they named none
     */
    public static boolean writable(Domain seriesDomain, Domain currentDomain) {
        if (seriesDomain == null) {
            // Visible from everywhere, therefore writable from everywhere. See
            // the class note: there is no domain to switch to.
            return true;
        }
        if (currentDomain == null) {
            // A caller sitting at no desk at all. Refused rather than waved
            // through: the alternative would make the whole guard optional for
            // anybody who simply omits the header.
            return false;
        }
        return seriesDomain.getDomainId() != null
                && seriesDomain.getDomainId().equals(currentDomain.getDomainId());
    }

    // -------------------------------------------------------------- assertions

    /** Whether the caller may write this series, for a screen deciding what to offer. */
    public boolean isWritable(PublicationSeries series) {
        return series == null || writable(series.getDomain(), domainService.currentDomain());
    }

    /**
     * Refuse a write on a series outside the caller's domain.
     *
     * A null series is passed through untouched. The caller looks the series up
     * and answers its own SERIES_NOT_FOUND; turning a missing series into a
     * domain refusal here would hand back the wrong diagnosis for a typo.
     */
    public void assertWritable(PublicationSeries series) {
        if (series == null) {
            return;
        }
        Domain current = domainService.currentDomain();
        if (!writable(series.getDomain(), current)) {
            throw new NotInDomainException(refusal(series.getSeriesId(),
                    series.getDomain(), current));
        }
    }

    /**
     * Refuse a write on an issue whose series is outside the caller's domain.
     *
     * An issue has no domain of its own -- it inherits the series', which is the
     * only place the accountability is recorded, and re-deciding it per issue
     * would let the two disagree.
     */
    public void assertWritable(PublicationIssue issue) {
        if (issue == null) {
            return;
        }
        assertWritable(issue.getSeries());
    }

    /**
     * Refuse a body that hands a series to a domain the caller is not in.
     *
     * Needed on top of the series check for create and for update. On create
     * there is no stored series to compare against, and without this an admin
     * could author a new series straight INTO another domain; on update the
     * stored check passes and the body could still move a series the caller does
     * own OUT to a domain they do not, which is the same escape run backwards.
     *
     * @param domainId the domain named by the request body, null or blank for none
     * @param what     what is being written, for the refusal message
     */
    public void assertMayAssign(String domainId, String what) {
        if (domainId == null || domainId.isBlank()) {
            // Assigning no domain is assigning something every admin may write.
            return;
        }
        Domain current = domainService.currentDomain();
        if (current == null || !domainId.equals(current.getDomainId())) {
            throw new NotInDomainException(what + " names the domain '" + domainId
                    + "', and the caller is in "
                    + (current == null ? "no domain" : "'" + current.getDomainId() + "'")
                    + ". A publication may only be placed in the domain it is being"
                    + " administered from.");
        }
    }

    /** The caller's current domain, for a caller that has to report what it skipped. */
    public Domain currentDomain() {
        return domainService.currentDomain();
    }

    private static String refusal(String seriesId, Domain seriesDomain, Domain current) {
        return "The publication series '" + seriesId + "' belongs to the domain '"
                + (seriesDomain == null ? "" : seriesDomain.getDomainId())
                + "', and the caller is in "
                + (current == null ? "no domain" : "'" + current.getDomainId() + "'")
                + ". Switch to that domain to change it.";
    }
}
