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
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which issues a domain-scoped publication search can find.
 *
 * The adapter had no test at all, which is how a series with NO domain came to be
 * silently unfindable: `s.domain.domainId = :domain` is an INNER comparison, so a
 * null domain does not merely fail to match -- the row cannot be returned by that
 * query under any value.
 *
 * That is not a hypothetical. Thirteen of the twenty-three series in the deployed
 * estate carry no domain, because the legacy templates they were imported from
 * carry none either, and legacy reads a null domain as "applies everywhere"
 * (Publication.findRecordingPublications: "p.domain is null or :series member of
 * p.domain.messageSeries"). So most of the catalogue was disappearing from every
 * domain-scoped search.
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

    /** An issue on a series with the given domain, or with none when null. */
    private PublicationIssue issueOn(Domain domain) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setImportSource(SEEDED_BY);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
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
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
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
     * A series with no domain is findable from every domain.
     *
     * Legacy's meaning of a null domain, preserved: it applies everywhere rather
     * than nowhere. Getting this backwards hides a publication from the editors
     * who maintain it, and nothing about the empty result looks wrong.
     */
    @Test
    @Transactional
    public void aseriesWithNoDomainIsFoundFromAnyDomain() {
        PublicationIssue unscoped = issueOn(null);

        assertTrue(finds(adapter.search(new PublicationSearchParams().domain("niord-nm")), unscoped),
                "a series with no domain was dropped from a domain-scoped search; legacy reads a "
                        + "null domain as applying everywhere");
        assertTrue(finds(adapter.search(new PublicationSearchParams().domain("niord-fa")), unscoped),
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
     * And NOT from another one. The null branch must not have widened everything.
     *
     * Without this the fix reads as "domain filtering still works", when it could
     * equally have become "domain filtering does nothing".
     */
    @Test
    @Transactional
    public void aseriesInADomainIsNotFoundFromAnother() {
        PublicationIssue scoped = issueOn(domain("niord-nm"));

        assertFalse(finds(adapter.search(new PublicationSearchParams().domain("niord-fa")), scoped),
                "the domain filter stopped filtering");
    }

    /** With no domain asked for, both kinds come back. */
    @Test
    @Transactional
    public void anunscopedSearchFindsBoth() {
        PublicationIssue unscoped = issueOn(null);
        PublicationIssue scoped = issueOn(domain("niord-nm"));

        List<PublicationIssue> hits = adapter.search(new PublicationSearchParams());
        assertTrue(finds(hits, unscoped));
        assertTrue(finds(hits, scoped));
    }
}
