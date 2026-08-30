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
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationSearchParams;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;
import org.niord.model.publication.PublicationType;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which issues a domain-scoped publication search can find.
 *
 * The adapter had no test at all, which is how most of the catalogue came to be
 * silently unfindable from every domain-scoped search: the comparison was an
 * inner one, so a publication that was meant to be reachable from everywhere was
 * not merely unmatched but unreturnable under any value.
 *
 * The rule it answers now is VISIBLE FROM, not ownership: a publication is found
 * from a domain that owns it, from every domain when it is shared with all of
 * them, and from the domains it names. The admin lists ask a different question
 * -- who OWNS it -- and are narrowed elsewhere; a publication shared with a desk
 * is read-only there, so listing it for administration would offer an editor a
 * row every control on which answers 403.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class PublicationSearchAdapterTest {

    private static final String SEEDED_BY = "search-adapter-fixture";

    @Inject
    PublicationSearchAdapter adapter;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    EntityManager em;

    /**
     * Takes the fixtures away again.
     *
     * The same discipline ShadowDiffTest and LocalEstateReplayTest carry, and for
     * the reason CutoverPreflightTest demonstrated: a check that reads the whole
     * schema -- and on a real deployment the whole schema IS the estate -- reports
     * a test's leftovers as findings about production data.
     *
     * Deletion order matters. The desc tables are FK children and Hibernate does
     * not cascade a bulk DELETE, and PublicationSeries_languages is an
     * @ElementCollection with no entity to delete through.
     */
    @AfterEach
    @Transactional
    public void removeWhatWasSeeded() {
        List<Integer> seriesIds = em.createQuery(
                        "SELECT s.id FROM PublicationSeries s WHERE s.importSource = :src", Integer.class)
                .setParameter("src", SEEDED_BY).getResultList();
        if (seriesIds.isEmpty()) {
            return;
        }
        List<Integer> issueIds = em.createQuery(
                        "SELECT i.id FROM PublicationIssue i WHERE i.series.id IN :series", Integer.class)
                .setParameter("series", seriesIds).getResultList();

        if (!issueIds.isEmpty()) {
            em.createQuery("DELETE FROM IssueMember m WHERE m.issue.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
            em.createQuery("DELETE FROM IssueOverride o WHERE o.issue.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
            em.createQuery("DELETE FROM IssueAuditEntry a WHERE a.issue.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
            em.createQuery("DELETE FROM PublicationIssueDesc d WHERE d.entity.id IN :issues")
                    .setParameter("issues", issueIds).executeUpdate();
        }
        em.createQuery("DELETE FROM PublicationIssue i WHERE i.series.id IN :series")
                .setParameter("series", seriesIds).executeUpdate();
        em.createQuery("DELETE FROM PublicationSeriesDesc d WHERE d.entity.id IN :series")
                .setParameter("series", seriesIds).executeUpdate();
        em.createNativeQuery(
                        "DELETE FROM PublicationSeries_languages WHERE PublicationSeries_id IN (:series)")
                .setParameter("series", seriesIds).executeUpdate();
        // The availability list, for the same reason: a join table with no entity
        // to delete through, and a foreign key into the series about to go.
        em.createNativeQuery(
                        "DELETE FROM PublicationSeries_AvailableDomain WHERE series_id IN (:series)")
                .setParameter("series", seriesIds).executeUpdate();
        em.createQuery("DELETE FROM PublicationSeries s WHERE s.id IN :series")
                .setParameter("series", seriesIds).executeUpdate();
    }

    // ------------------------------------------------------------------ fixture

    private Domain domain(String domainId) {
        Domain d = em.createQuery("SELECT d FROM Domain d WHERE d.domainId = :id", Domain.class)
                .setParameter("id", domainId).getResultStream().findFirst().orElse(null);
        if (d == null) {
            d = new Domain();
            d.setDomainId(domainId);
            d.setName(domainId);
            em.persist(d);
        }
        return d;
    }

    /** An issue on a series owned by the given domain and shared with nobody. */
    private PublicationIssue issueOn(Domain domain) {
        return issueOn(domain, ContentMode.GENERATED_FROM_QUERY);
    }

    /** An issue whose series is owned by one domain and shared as stated. */
    private PublicationIssue issueOn(Domain owner, SeriesAvailability availability,
                                     Domain... shared) {
        PublicationIssue issue = issueOn(owner, ContentMode.GENERATED_FROM_QUERY);
        PublicationSeries s = issue.getSeries();
        s.setAvailability(availability);
        for (Domain d : shared) {
            s.getAvailableDomains().add(d);
        }
        em.flush();
        return issue;
    }

    /** An issue on a series publishing the given kind of content. */
    private PublicationIssue issueOn(Domain domain, ContentMode contentMode) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setImportSource(SEEDED_BY);
        s.setContentMode(contentMode);
        s.setCadence(SeriesCadence.WEEKLY);
        // A time predicate and a liveness flag belong to the query-backed shape
        // and to nothing else, so the fixture only carries them where a real
        // series may.
        if (contentMode == ContentMode.GENERATED_FROM_QUERY) {
            s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
            s.setAliveAtCutoff(false);
        }
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.setDomain(domain);
        s.getLanguages().add("da");
        s.createDesc("da").setName("Adapter fixture");
        em.persist(s);

        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);

        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, u);
        em.flush();
        return i;
    }

    private boolean finds(List<PublicationIssue> hits, PublicationIssue issue) {
        return hits.stream().anyMatch(h -> h.getId().equals(issue.getId()));
    }

    // ---------------------------------------------------------------- assertions

    /**
     * A publication shared with every domain is findable from every domain.
     *
     * This is what a null owner used to buy, said in the field that means it. It
     * is the case that matters most: most of the citation-only catalogue is
     * ALL_DOMAINS, and getting it backwards hides a publication from every editor
     * who cites it while nothing about the empty result looks wrong.
     */
    @Test
    @Transactional
    public void asharedPublicationIsFoundFromAnyDomain() {
        PublicationIssue shared = issueOn(domain("niord-annex"), SeriesAvailability.ALL_DOMAINS);

        assertTrue(finds(adapter.search(new PublicationSearchParams().domain("niord-nm")), shared),
                "ALL_DOMAINS was dropped from a domain-scoped search");
        assertTrue(finds(adapter.search(new PublicationSearchParams().domain("niord-fa")), shared),
                "and from every other domain too -- 'everywhere' is not 'one of them'");
    }

    /** A series scoped to a domain is found from that domain. */
    @Test
    @Transactional
    public void aseriesInADomainIsFoundFromIt() {
        PublicationIssue scoped = issueOn(domain("niord-nm"));

        assertTrue(finds(adapter.search(new PublicationSearchParams().domain("niord-nm")), scoped));
    }

    /**
     * And NOT from another one. The sharing branch must not have widened everything.
     *
     * Without this the rule reads as "domain filtering still works", when it could
     * equally have become "domain filtering does nothing".
     */
    @Test
    @Transactional
    public void aseriesInADomainIsNotFoundFromAnother() {
        PublicationIssue scoped = issueOn(domain("niord-nm"));

        assertFalse(finds(adapter.search(new PublicationSearchParams().domain("niord-fa")), scoped),
                "the domain filter stopped filtering");
    }

    /** A publication shared with a named domain is found from it, and only from it. */
    @Test
    @Transactional
    public void selectedDomainsIsFoundFromWhatItNamesAndNowhereElse() {
        Domain guest = domain("niord-nm");
        PublicationIssue shared = issueOn(domain("niord-annex"),
                SeriesAvailability.SELECTED_DOMAINS, guest);

        assertTrue(finds(adapter.search(new PublicationSearchParams().domain("niord-nm")), shared),
                "the domain the publication is shared with cannot see it");
        assertFalse(finds(adapter.search(new PublicationSearchParams().domain("niord-fa")), shared),
                "a domain it is NOT shared with can see it; 'selected' would then select nothing");
    }

    /**
     * An inactive domain on the list is ignored.
     *
     * A domain that has been switched off is not a desk anybody is sitting at, so
     * a stale row naming one must not keep a publication reachable from a place
     * that no longer exists.
     */
    @Test
    @Transactional
    public void asharedInactiveDomainDoesNotReachThePublication() {
        Domain switchedOff = domain("niord-inactive-probe");
        switchedOff.setActive(false);
        em.flush();

        PublicationIssue shared = issueOn(domain("niord-annex"),
                SeriesAvailability.SELECTED_DOMAINS, switchedOff);

        assertFalse(finds(
                        adapter.search(new PublicationSearchParams().domain("niord-inactive-probe")),
                        shared),
                "a switched-off domain still reached the publication");
    }

    /** With no domain asked for, both kinds come back. */
    @Test
    @Transactional
    public void anunscopedSearchFindsBoth() {
        PublicationIssue shared = issueOn(domain("niord-annex"), SeriesAvailability.ALL_DOMAINS);
        PublicationIssue scoped = issueOn(domain("niord-nm"));

        List<PublicationIssue> hits = adapter.search(new PublicationSearchParams());
        assertTrue(finds(hits, shared));
        assertTrue(finds(hits, scoped));
    }

    // ------------------------------------------------- the union's legacy half

    /**
     * A legacy row whose imported twin is out of scope is named as hidden.
     *
     * The legacy half of the union carries the old nullable domain column and
     * nothing else, so on its own it answers by the rule the redesign replaced.
     * That is invisible while the issue half returns the twin -- the two collide
     * by id and the merge drops the legacy row -- and becomes visible exactly when
     * the new rule HIDES the twin, at which point the legacy row is what shows the
     * publication that was just hidden.
     */
    @Test
    @Transactional
    public void atwinnedLegacyRowFollowsItsTwin() {
        // A bare UUID: the column is the legacy publication id and is 36 characters
        // wide, so a prefixed value is truncated rather than stored.
        PublicationIssue mine = issueOn(domain("niord-nm"));
        mine.setLegacyPublicationId(UUID.randomUUID().toString());
        PublicationIssue shared = issueOn(domain("niord-annex"), SeriesAvailability.ALL_DOMAINS);
        shared.setLegacyPublicationId(UUID.randomUUID().toString());
        em.flush();

        java.util.Set<String> hidden = adapter.legacyIdsHiddenFrom("niord-fa");
        assertTrue(hidden.contains(mine.getLegacyPublicationId()),
                "a legacy row whose twin belongs to another desk and is shared with nobody must "
                        + "not survive the union; it would show exactly what the new rule hid");
        assertFalse(hidden.contains(shared.getLegacyPublicationId()),
                "a legacy row whose twin is shared with every domain must not be hidden");
    }

    /** With no domain named there is nothing to hide: the estate is the answer. */
    @Test
    @Transactional
    public void anunscopedUnionHidesNoLegacyRow() {
        assertTrue(adapter.legacyIdsHiddenFrom(null).isEmpty());
        assertTrue(adapter.legacyIdsHiddenFrom("  ").isEmpty());
    }

    // ------------------------------------------------------------- the type filter

    /**
     * A type search selects the content mode it is the counterpart of.
     *
     * This half had NO type clause at all, while the other half of the union
     * applies its own stored type column -- so one parameter meant two things in
     * one result set. The message editor's publication field sends
     * type=MESSAGE_REPORT and was served every issue in the estate, uploaded
     * annexes and external links included.
     *
     * All four asked for by name, because a mapping that is total on one side and
     * partial on the other looks correct from whichever side is tested.
     */
    @Test
    @Transactional
    public void atypeSearchSelectsTheMatchingContentMode() {
        PublicationIssue generated = issueOn(domain("niord-nm"), ContentMode.GENERATED_FROM_QUERY);
        PublicationIssue uploaded = issueOn(domain("niord-nm"), ContentMode.UPLOADED_FILE);
        PublicationIssue linked = issueOn(domain("niord-nm"), ContentMode.EXTERNAL_LINK);
        PublicationIssue none = issueOn(domain("niord-nm"), ContentMode.NONE);

        Map<PublicationType, PublicationIssue> expected = new LinkedHashMap<>();
        expected.put(PublicationType.MESSAGE_REPORT, generated);
        expected.put(PublicationType.REPOSITORY, uploaded);
        expected.put(PublicationType.LINK, linked);
        expected.put(PublicationType.NONE, none);

        for (Map.Entry<PublicationType, PublicationIssue> e : expected.entrySet()) {
            List<PublicationIssue> hits =
                    adapter.search(new PublicationSearchParams().type(e.getKey()));
            assertTrue(finds(hits, e.getValue()),
                    "?type=" + e.getKey() + " did not find the issue whose series publishes it");
            for (PublicationIssue other : expected.values()) {
                if (other != e.getValue()) {
                    assertFalse(finds(hits, other),
                            "?type=" + e.getKey() + " also returned an issue of another content mode; "
                                    + "the row would then report a type the caller did not ask for");
                }
            }
        }
    }

    /** Omitting the type narrows nothing -- one shipped picker sends none. */
    @Test
    @Transactional
    public void anomittedTypeReachesEveryContentMode() {
        List<PublicationIssue> all = List.of(
                issueOn(domain("niord-nm"), ContentMode.GENERATED_FROM_QUERY),
                issueOn(domain("niord-nm"), ContentMode.UPLOADED_FILE),
                issueOn(domain("niord-nm"), ContentMode.EXTERNAL_LINK),
                issueOn(domain("niord-nm"), ContentMode.NONE));

        List<PublicationIssue> hits = adapter.search(new PublicationSearchParams());
        for (PublicationIssue issue : all) {
            assertTrue(finds(hits, issue),
                    "an unfiltered search dropped an issue; a picker that sends no type at all would "
                            + "show nothing");
        }
    }
}
