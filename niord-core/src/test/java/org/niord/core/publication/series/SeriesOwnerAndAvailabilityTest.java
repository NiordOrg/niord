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

import org.junit.jupiter.api.Test;
import org.niord.core.domain.Domain;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner and the availability list, as rules rather than as a schema.
 *
 * ONE COLUMN USED TO ANSWER TWO QUESTIONS -- which desk administers a publication,
 * and where it may be cited -- and a null answered the second by giving up the
 * first. Everything here is about keeping the two apart: the owner is single and
 * required, availability is separate and never grants a write, and the two forms
 * of the visible-from rule (the JPQL fragment and the in-memory one) have to agree
 * case by case.
 *
 * No database. These are decisions about values, and a test that needed a
 * container to read them is a test nobody re-reads when the rule is questioned.
 */
public class SeriesOwnerAndAvailabilityTest {

    private static Domain domain(String id) {
        Domain d = new Domain();
        d.setDomainId(id);
        d.setName(id);
        d.setTimeZone("Europe/Copenhagen");
        d.setActive(true);
        return d;
    }

    private static PublicationSeries series(String ownerId, SeriesAvailability availability,
                                            Domain... shared) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("probe");
        s.setDomain(domain(ownerId));
        s.setAvailability(availability);
        for (Domain d : shared) {
            s.getAvailableDomains().add(d);
        }
        return s;
    }

    // -------------------------------------------------------- the visible-from rule

    /** The owner sees its own publication whatever it is shared as. */
    @Test
    public void theOwnerAlwaysSeesIt() {
        assertTrue(SeriesVisibility.visibleFrom(series("niord-nm", SeriesAvailability.OWNER_ONLY),
                "niord-nm"));
    }

    /** And nobody else does, when it is the owner's alone. */
    @Test
    public void ownerOnlyIsInvisibleElsewhere() {
        assertFalse(SeriesVisibility.visibleFrom(series("niord-nm", SeriesAvailability.OWNER_ONLY),
                "niord-fa"),
                "OWNER_ONLY reached another domain; the weekly edition of one authority would "
                        + "then be citable as if it were everybody's");
    }

    /** ALL_DOMAINS is what a null owner used to buy, said in the field that means it. */
    @Test
    public void allDomainsReachesEverywhere() {
        PublicationSeries s = series("niord-annex", SeriesAvailability.ALL_DOMAINS);
        assertTrue(SeriesVisibility.visibleFrom(s, "niord-nm"));
        assertTrue(SeriesVisibility.visibleFrom(s, "niord-fa"));
    }

    /** A named domain is reached; one that is not named is not. */
    @Test
    public void selectedDomainsReachesExactlyWhatItNames() {
        PublicationSeries s = series("niord-annex", SeriesAvailability.SELECTED_DOMAINS,
                domain("niord-nm"));

        assertTrue(SeriesVisibility.visibleFrom(s, "niord-nm"), "the named domain must see it");
        assertFalse(SeriesVisibility.visibleFrom(s, "niord-fa"),
                "an unnamed domain must not; otherwise 'selected' selects nothing");
    }

    /**
     * An INACTIVE domain in the list is ignored.
     *
     * A domain that has been switched off is not a desk anybody is sitting at, and
     * a stale row naming one would keep a publication reachable from a place that
     * no longer exists -- which reads on the editor as a sharing setting that does
     * something and does not.
     */
    @Test
    public void anInactiveSharedDomainIsIgnored() {
        Domain switchedOff = domain("niord-fa");
        switchedOff.setActive(false);

        assertFalse(SeriesVisibility.visibleFrom(
                        series("niord-annex", SeriesAvailability.SELECTED_DOMAINS, switchedOff),
                        "niord-fa"),
                "a switched-off domain still reached the publication");
    }

    /** No domain named is no narrowing asked for: the estate answers. */
    @Test
    public void namingNoDomainNarrowsNothing() {
        assertTrue(SeriesVisibility.visibleFrom(
                series("niord-nm", SeriesAvailability.OWNER_ONLY), null));
        assertTrue(SeriesVisibility.visibleFrom(
                series("niord-nm", SeriesAvailability.OWNER_ONLY), "  "));
    }

    /**
     * The query fragment names exactly the parameters the binder fills.
     *
     * A clause appended without its bindings is a request-time failure rather than
     * a compile-time one, and this surface has had that bug once already.
     */
    @Test
    public void theClauseAndTheBindingsAgree() {
        String clause = SeriesVisibility.clause("s", "d");
        java.util.Map<String, Object> bindings = new java.util.LinkedHashMap<>();
        SeriesVisibility.bind(bindings, "niord-nm");

        java.util.regex.Matcher named =
                java.util.regex.Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)").matcher(clause);
        java.util.Set<String> used = new java.util.LinkedHashSet<>();
        while (named.find()) {
            used.add(named.group(1));
        }
        assertEquals(bindings.keySet(), used,
                "the visible-from fragment names parameters the binder does not fill, or fills "
                        + "parameters it does not name -- either way a query built from it fails "
                        + "at request time");
        assertTrue(clause.contains("EXISTS"),
                "the sharing test must be an EXISTS: a join would multiply a shared publication by "
                        + "the number of domains it is shared with, and the picker pages over this");
    }

    // ------------------------------------------------------------------ the wire

    /** availability and the list survive entity -> VO. */
    @Test
    public void theSharingSettingsReachTheWire() {
        PublicationSeries s = series("niord-annex", SeriesAvailability.SELECTED_DOMAINS,
                domain("niord-nm"), domain("niord-fa"));

        SystemPublicationSeriesVo vo = s.toVo(SystemPublicationSeriesVo.class);
        assertEquals("SELECTED_DOMAINS", vo.getAvailability());
        assertEquals(java.util.List.of("niord-nm", "niord-fa"), vo.getAvailableDomainIds());
        assertEquals("niord-annex", vo.getDomainId());
    }

    /**
     * The owner is never in the list on the wire.
     *
     * It is already the strongest form of "visible from here". Carrying it twice
     * would let an editor tick and untick a box that changes nothing, and a client
     * echoing back what it read would store a row the next read filters out -- so
     * the list would round-trip to something different from what was saved.
     */
    @Test
    public void theOwnerIsNeverOnTheSharingList() {
        PublicationSeries s = series("niord-annex", SeriesAvailability.SELECTED_DOMAINS,
                domain("niord-annex"), domain("niord-nm"));

        assertEquals(java.util.List.of("niord-nm"),
                s.toVo(SystemPublicationSeriesVo.class).getAvailableDomainIds());
    }

    /** An omitted availability keeps what is stored, like the kind and the cut-off default. */
    @Test
    public void anOmittedAvailabilityIsUnchangedRatherThanCleared() {
        PublicationSeries stored = series("niord-nm", SeriesAvailability.ALL_DOMAINS);
        SystemPublicationSeriesVo vo = stored.toVo(SystemPublicationSeriesVo.class);
        vo.setAvailability(null);

        stored.updateFromVo(vo);
        assertEquals(SeriesAvailability.ALL_DOMAINS, stored.getAvailability(),
                "a client written before this field existed would otherwise narrow every "
                        + "publication it saved");
    }

    /** The defaults follow the content mode, and one answer serves every caller. */
    @Test
    public void theDefaultFollowsTheContentMode() {
        assertEquals(SeriesAvailability.OWNER_ONLY,
                SeriesAvailability.defaultFor(ContentMode.GENERATED_FROM_QUERY),
                "a generated series is assembled from one desk's messages over its calendar");
        for (ContentMode mode : new ContentMode[]{ContentMode.UPLOADED_FILE,
                ContentMode.EXTERNAL_LINK, ContentMode.NONE}) {
            assertEquals(SeriesAvailability.ALL_DOMAINS, SeriesAvailability.defaultFor(mode),
                    mode + " is a reference other desks cite, which is what it was before this "
                            + "field existed");
        }
    }

    // ------------------------------------------------------------------ transfer

    /** A caller who is admin in both ends may move it. */
    @Test
    public void adminInBothDomainsMayMoveIt() {
        PublicationSeries s = series("niord-nm", SeriesAvailability.OWNER_ONLY);
        Domain target = domain("niord-annex");

        SeriesOwnerTransfer.assertTransferable(s, target, true);
        SeriesOwnerTransfer.moveTo(s, target);

        assertSame(target, s.getDomain());
    }

    /** And one who is not an admin in the target is refused, by name. */
    @Test
    public void notAnAdminInTheTargetIsRefusedNamingIt() {
        PublicationDomainGuard.NotInDomainException e =
                assertThrows(PublicationDomainGuard.NotInDomainException.class,
                        () -> SeriesOwnerTransfer.assertTransferable(
                                series("niord-nm", SeriesAvailability.OWNER_ONLY),
                                domain("niord-annex"), false));

        assertEquals(PublicationDomainGuard.NOT_IN_DOMAIN, e.code());
        assertTrue(e.getMessage().contains("niord-annex"),
                "the refusal must name WHICH side is missing: switching domain fixes one of them "
                        + "and cannot fix the other");
    }

    /** An inactive target is refused: it is a desk nobody is sitting at. */
    @Test
    public void anInactiveTargetIsRefused() {
        Domain target = domain("niord-annex");
        target.setActive(false);

        assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> SeriesOwnerTransfer.assertTransferable(
                        series("niord-nm", SeriesAvailability.OWNER_ONLY), target, true));
    }

    /** Moving it where it already is is refused rather than silently accepted. */
    @Test
    public void movingItNowhereIsRefused() {
        assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> SeriesOwnerTransfer.assertTransferable(
                        series("niord-nm", SeriesAvailability.OWNER_ONLY), domain("niord-nm"), true));
    }

    /**
     * The target leaves the sharing list when it becomes the owner.
     *
     * Owning it is the strongest form of the same claim, and the read filters the
     * owner out of the list -- so a row left behind would make the stored list and
     * the list the editor shows disagree, with no control to correct it.
     */
    @Test
    public void theNewOwnerLeavesTheSharingList() {
        Domain target = domain("niord-annex");
        PublicationSeries s = series("niord-nm", SeriesAvailability.SELECTED_DOMAINS,
                domain("niord-annex"), domain("niord-fa"));

        SeriesOwnerTransfer.moveTo(s, target);

        assertEquals(java.util.List.of("niord-fa"),
                s.getAvailableDomains().stream().map(Domain::getDomainId).toList(),
                "the new owner was left on the list it is now the owner of");
    }
}
