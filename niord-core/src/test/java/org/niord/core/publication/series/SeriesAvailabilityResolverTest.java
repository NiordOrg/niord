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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.domain.Domain;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one resolution of a sharing list, for the three routes that write one.
 *
 * The series editor, the one-off editor and the interchange import each had a
 * copy of this, and the copies had already drifted -- which is what a rule stated
 * three times does. Everything here is asserted against the single resolver they
 * now share, so a difference between the forms is impossible rather than merely
 * unlikely.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SeriesAvailabilityResolverTest {

    @Inject
    SeriesAvailabilityResolver resolver;

    @Inject
    EntityManager em;

    private Domain domain(String suffix, boolean active) {
        String id = "avail-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Domain d = new Domain();
        d.setDomainId(id);
        d.setName(id);
        d.setTimeZone("Europe/Copenhagen");
        d.setActive(active);
        em.persist(d);
        em.flush();
        return d;
    }

    private PublicationSeries owned(Domain owner) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("avail-probe");
        s.setDomain(owner);
        s.setAvailability(SeriesAvailability.SELECTED_DOMAINS);
        return s;
    }

    private static SystemPublicationSeriesVo sharing(String availability, String... ids) {
        SystemPublicationSeriesVo vo = new SystemPublicationSeriesVo();
        vo.setAvailability(availability);
        vo.getAvailableDomainIds().addAll(List.of(ids));
        return vo;
    }

    private static List<String> idsOf(PublicationSeries s) {
        return s.getAvailableDomains().stream().map(Domain::getDomainId).toList();
    }

    // ------------------------------------------------------- inactive is tolerated

    /**
     * A domain that has been switched off stays on the list, and survives a save.
     *
     * IT USED TO BE REFUSED, and that was worse than it looks. The refusal fires
     * on any save of the publication, not on the edit that added the domain -- so
     * switching a domain off made every publication ever shared with it
     * unsaveable, and an admin editing a NAME in another domain entirely got a
     * refusal about a domain they have nothing to do with. The only way out was to
     * find and untick it, which loses the sharing silently when the domain comes
     * back.
     *
     * Nothing is reachable through an inactive domain in any case: the visible-from
     * predicate ignores it. The row costs nothing while the domain is off and works
     * again when it returns.
     */
    @Test
    @Transactional
    public void aninactiveDomainIsKeptRatherThanRefused() {
        Domain owner = domain("owner", true);
        Domain switchedOff = domain("off", false);
        PublicationSeries s = owned(owner);

        resolver.apply(s, sharing("SELECTED_DOMAINS", switchedOff.getDomainId()));

        assertEquals(List.of(switchedOff.getDomainId()), idsOf(s),
                "an inactive domain was dropped or refused; switching a domain off would then "
                        + "make every publication shared with it unsaveable");
    }

    /** And it round-trips: what was saved is what the editor reads back. */
    @Test
    @Transactional
    public void aninactiveDomainSurvivesTheRoundTrip() {
        Domain owner = domain("owner", true);
        Domain switchedOff = domain("off", false);
        PublicationSeries s = owned(owner);

        resolver.apply(s, sharing("SELECTED_DOMAINS", switchedOff.getDomainId()));
        SystemPublicationSeriesVo read = s.toVo(SystemPublicationSeriesVo.class);

        assertEquals(List.of(switchedOff.getDomainId()), read.getAvailableDomainIds(),
                "the inactive domain vanished on the way back to the editor, so the admin cannot "
                        + "see what the publication is shared with and cannot untick it");
    }

    // ------------------------------------------------------------- unknown refused

    /** An id naming nothing is refused: no screen could show it and no predicate match it. */
    @Test
    @Transactional
    public void anunknownDomainIsRefused() {
        PublicationSeries s = owned(domain("owner", true));

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> resolver.apply(s, sharing("SELECTED_DOMAINS", "no-such-domain-at-all")));

        assertEquals("SERIES_INVALID", e.code());
        assertTrue(e.getMessage().contains("no-such-domain-at-all"),
                "the refusal must name the id the client sent: " + e.getMessage());
    }

    // ------------------------------------------------------- dedupe, owner stripped

    @Test
    @Transactional
    public void theListIsDeduplicatedAndTheOwnerIsStripped() {
        Domain owner = domain("owner", true);
        Domain guest = domain("guest", true);
        PublicationSeries s = owned(owner);

        resolver.apply(s, sharing("SELECTED_DOMAINS",
                guest.getDomainId(), owner.getDomainId(), guest.getDomainId(), "  "));

        assertEquals(List.of(guest.getDomainId()), idsOf(s),
                "the owner is already the strongest form of 'visible from here', and a duplicate "
                        + "row would make 'shared with three domains' and 'shared with two' "
                        + "indistinguishable from a count");
    }

    // ------------------------------------------------------- the omission semantics

    /**
     * An omitted availability changes NEITHER the setting nor the list.
     *
     * Every client written before this field existed omits it. Emptying the list
     * there would un-share a publication on the next save of an unrelated field --
     * a rename would silently narrow it, and nothing in the response would say so.
     */
    @Test
    @Transactional
    public void anomittedAvailabilityKeepsBothTheSettingAndTheList() {
        Domain owner = domain("owner", true);
        Domain guest = domain("guest", true);
        PublicationSeries s = owned(owner);
        s.getAvailableDomains().add(guest);

        SystemPublicationSeriesVo silent = new SystemPublicationSeriesVo();
        silent.setAvailability(null);
        // A body that carries no availability but DOES carry a list is still
        // silent: the two travel together, and half of a setting is not a setting.
        silent.getAvailableDomainIds().add("no-such-domain-at-all");

        resolver.apply(s, silent);

        assertEquals(SeriesAvailability.SELECTED_DOMAINS, s.getAvailability());
        assertEquals(List.of(guest.getDomainId()), idsOf(s),
                "an old client saving a name change emptied the sharing list it never mentioned");
    }

    /**
     * A present availability makes the list the whole truth: absent means empty.
     *
     * Unticking the last domain is something an admin means, and there has to be a
     * way to say it -- so once the setting is on the wire, so is the list it goes
     * with.
     */
    @Test
    @Transactional
    public void apresentAvailabilityMakesTheListAFullRepresentation() {
        Domain owner = domain("owner", true);
        Domain guest = domain("guest", true);
        PublicationSeries s = owned(owner);
        s.getAvailableDomains().add(guest);

        SystemPublicationSeriesVo narrowed = new SystemPublicationSeriesVo();
        narrowed.setAvailability("OWNER_ONLY");

        resolver.apply(s, narrowed);

        assertTrue(idsOf(s).isEmpty(),
                "an admin who unticked the last domain was left sharing the publication with it");
    }
}
