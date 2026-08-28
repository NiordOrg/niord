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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may write a publication series, decided by the domain the caller is in.
 *
 * Three cases and they are the whole rule: your own domain writes, another
 * domain does not, and a series belonging to no domain writes from anywhere. The
 * third is the one worth a test of its own, because it is the case somebody
 * "tightens" later on the assumption it was an oversight -- and tightening it
 * strands every shared publication, since there is no domain to switch to in
 * order to gain the right.
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
        d.setDomainId("dom-" + UUID.randomUUID().toString().substring(0, 8));
        d.setName("Test domain");
        d.setTimeZone("Europe/Copenhagen");
        em.persist(d);
        return d;
    }

    private PublicationSeries series(Domain domain) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
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

        assertTrue(PublicationDomainGuard.writable(null, mine),
                "a series with no domain is visible from every domain, so it is writable "
                        + "from every domain; there is no domain to switch to");
        assertTrue(PublicationDomainGuard.writable(null, null),
                "and a domainless series stays writable even by a caller in no domain, or "
                        + "the shared publications become editable only in the database");

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

    /** A series with no domain is writable by whoever is asking. */
    @Test
    @Transactional
    public void aDomainlessSeriesIsWritableByAnyone() {
        Domain caller = domain();
        PublicationSeries s = series(null);

        domainService.setDomainForCurrentThread(caller.getDomainId());
        assertTrue(guard.isWritable(s));
        guard.assertWritable(s);

        domainService.removeDomainForCurrentThread();
        assertTrue(guard.isWritable(s), "and from no domain at all");
        guard.assertWritable(s);
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
