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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.publication.TestIds;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may write a publication series, decided by the domain the caller is in.
 *
 * Three cases and they are the whole rule: your own domain writes, another
 * domain does not, and a publication belonging to NO domain writes from
 * nowhere. The third is the one worth a test of its own, and it is the one that
 * reversed: while a null owner meant "visible from every domain", refusing it
 * stranded every shared publication, because there was no domain to switch to.
 * Sharing has its own field now, so an ownerless row is an anomaly rather than a
 * state -- and letting an ordinary write touch it would let whichever admin
 * opened the form adopt the publication by saving it. The transfer endpoint is
 * how such a row is claimed, deliberately and with a reason.
 *
 * Driven through the REAL thread-local the request filter sets, not through a
 * stub, so what is exercised is the same path a request takes.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class PublicationDomainGuardTest {

    @Inject
    PublicationDomainGuard guard;

    @Inject
    DomainService domainService;

    @Inject
    EntityManager em;

    @AfterEach
    public void clearDomain() {
        // The domain is a THREAD-local and the runner reuses threads, so a test
        // that left one set would decide the next test's answer.
        domainService.removeDomainForCurrentThread();
    }

    // ------------------------------------------------------------------ fixtures

    private Domain domain() {
        Domain d = new Domain();
        d.setDomainId(TestIds.domain());
        d.setName("Test domain");
        d.setTimeZone("Europe/Copenhagen");
        em.persist(d);
        return d;
    }

    private PublicationSeries series(Domain domain) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.DRAFT);
        s.setDomain(domain);
        return s;
    }

    // --------------------------------------------------------- the pure decision

    /**
     * The rule as arithmetic, with no container in it.
     *
     * Stated separately from the injected form because the three lines that
     * decide this are the whole feature, and a test that has to stand a request
     * up to read them is a test nobody re-reads when the rule is questioned.
     */
    @Test
    public void theDecisionIsTheTwoDomainsAndNothingElse() {
        Domain mine = new Domain();
        mine.setDomainId("mine");
        Domain theirs = new Domain();
        theirs.setDomainId("theirs");

        assertTrue(PublicationDomainGuard.writable(mine, mine),
                "an admin in the series' own domain must be able to write it");
        assertFalse(PublicationDomainGuard.writable(theirs, mine),
                "a series belonging to another domain is not this caller's to write");

        // AN OWNERLESS ROW IS WRITABLE BY NOBODY, and this pair used to assert the
        // opposite. The old reasoning -- a domainless publication is visible
        // everywhere, so a shared publication nobody can edit is worse than one
        // anybody can -- belonged to the model where a null domain MEANT
        // "everywhere". Sharing has its own field now, so a null owner is an
        // anomaly rather than a state, and waving it through would let the first
        // admin who opened the form adopt the publication by saving it.
        assertFalse(PublicationDomainGuard.writable(null, mine),
                "an ownerless publication was writable, so an ordinary save could adopt it -- "
                        + "and taking responsibility for a publication is supposed to be an act "
                        + "with a name on it, not a side effect of editing a title");
        assertFalse(PublicationDomainGuard.writable(null, null),
                "and no more so for a caller sitting at no desk at all");

        assertFalse(PublicationDomainGuard.writable(mine, null),
                "a caller sitting at no desk at all is refused; otherwise omitting the "
                        + "domain header would be a way round the whole guard");
    }

    // ------------------------------------------------------------ through a request

    /** The caller's own domain writes. */
    @Test
    @Transactional
    public void ownDomainMayWrite() {
        Domain d = domain();
        PublicationSeries s = series(d);
        domainService.setDomainForCurrentThread(d.getDomainId());

        assertTrue(guard.isWritable(s));
        guard.assertWritable(s);
    }

    /** Another domain's series is refused, with the code the client branches on. */
    @Test
    @Transactional
    public void anotherDomainIsRefusedWithTheCode() {
        Domain owner = domain();
        Domain caller = domain();
        PublicationSeries s = series(owner);
        domainService.setDomainForCurrentThread(caller.getDomainId());

        assertFalse(guard.isWritable(s));
        PublicationDomainGuard.NotInDomainException e = assertThrows(
                PublicationDomainGuard.NotInDomainException.class,
                () -> guard.assertWritable(s));
        assertEquals(PublicationDomainGuard.NOT_IN_DOMAIN, e.code(),
                "the refusal must carry the catalogued code; a client that has to match on "
                        + "the message text cannot tell this apart from a missing role, and the "
                        + "two have different remedies");
        assertTrue(e.getMessage().contains(owner.getDomainId()),
                "the message names the domain to switch to, or the admin has to guess");
    }

    /** An issue inherits its series' domain -- it has none of its own. */
    @Test
    @Transactional
    public void anIssueIsScopedByItsSeries() {
        Domain owner = domain();
        Domain caller = domain();
        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series(owner));

        domainService.setDomainForCurrentThread(caller.getDomainId());
        assertThrows(PublicationDomainGuard.NotInDomainException.class,
                () -> guard.assertWritable(issue));

        domainService.removeDomainForCurrentThread();
        domainService.setDomainForCurrentThread(owner.getDomainId());
        guard.assertWritable(issue);
    }

    /**
     * A publication with no owner is writable by nobody, through this guard.
     *
     * The refusal is what makes claiming one deliberate. An ownerless row is
     * reachable through the transfer endpoint, which skips the source check for
     * exactly this case, still demands admin in the target, and records who took
     * it and why -- so the row is not stranded, it is claimed rather than
     * absorbed by the next save of an unrelated field.
     */
    @Test
    @Transactional
    public void anOwnerlessSeriesIsWritableByNobody() {
        Domain caller = domain();
        PublicationSeries s = series(null);

        domainService.setDomainForCurrentThread(caller.getDomainId());
        assertFalse(guard.isWritable(s), "an ordinary save could adopt an ownerless publication");
        assertThrows(PublicationDomainGuard.NotInDomainException.class,
                () -> guard.assertWritable(s));

        domainService.removeDomainForCurrentThread();
        assertFalse(guard.isWritable(s), "and no more so from no domain at all");
    }

    /** A create may name the caller's domain, or none, and nobody else's. */
    @Test
    @Transactional
    public void aBodyMayOnlyAssignTheCallersOwnDomain() {
        Domain mine = domain();
        Domain theirs = domain();
        domainService.setDomainForCurrentThread(mine.getDomainId());

        guard.assertMayAssign(null, "a series");
        guard.assertMayAssign("  ", "a series");
        guard.assertMayAssign(mine.getDomainId(), "a series");

        PublicationDomainGuard.NotInDomainException e = assertThrows(
                PublicationDomainGuard.NotInDomainException.class,
                () -> guard.assertMayAssign(theirs.getDomainId(), "a series"));
        assertEquals(PublicationDomainGuard.NOT_IN_DOMAIN, e.code());
    }

    /**
     * A missing series is NOT a domain refusal.
     *
     * The endpoints look the row up and answer their own SERIES_NOT_FOUND. If the
     * guard threw here instead, a plain typo in an id would come back as "wrong
     * domain" and send whoever is debugging it to switch domains and try again.
     */
    @Test
    @Transactional
    public void aMissingRowIsLeftToTheCaller() {
        Domain caller = domain();
        domainService.setDomainForCurrentThread(caller.getDomainId());

        guard.assertWritable((PublicationSeries) null);
        guard.assertWritable((PublicationIssue) null);
        assertTrue(guard.isWritable(null));
    }
}
