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

import jakarta.persistence.EntityManager;
import org.niord.core.domain.Domain;

/**
 * The domain a test fixture's publication belongs to.
 *
 * Every publication names exactly one owner -- the desk that lists it,
 * administers it, and supplies the timezone its cut-offs are read in -- and the
 * column is NOT NULL, so a fixture without one no longer describes a state the
 * system can be in. It used to: a null owner meant "visible from every domain",
 * and most fixtures left it out because they were about something else entirely.
 *
 * ONE SHARED ROW, found or created. A domain per fixture would leave hundreds of
 * them behind in a database several suites share, and the pre-flight -- which
 * reads the whole schema -- would then report them as findings about the estate.
 *
 * A REAL TIMEZONE, because the owner is the only source of one and S-20 refuses a
 * cadenced series whose domain carries a zone nothing can parse. A fixture that
 * borrowed a blank one would fail activation for a reason having nothing to do
 * with what it was testing.
 */
public final class TestOwnerDomain {

    /** Named so a row left behind in a shared database is obviously a fixture's. */
    public static final String DOMAIN_ID = "test-owner-domain";

    private TestOwnerDomain() {
    }

    /** The shared owner, created on first use. */
    public static Domain of(EntityManager em) {
        Domain existing = em.createQuery(
                        "SELECT d FROM Domain d WHERE d.domainId = :id", Domain.class)
                .setParameter("id", DOMAIN_ID)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        Domain domain = detached();
        em.persist(domain);
        return domain;
    }

    /**
     * The same domain, unpersisted, for a test with no persistence context.
     *
     * The validator reads the id and the zone and nothing else, so a detached row
     * answers every rule that asks about an owner.
     */
    public static Domain detached() {
        Domain domain = new Domain();
        domain.setDomainId(DOMAIN_ID);
        domain.setName("Test owner domain");
        domain.setTimeZone("Europe/Copenhagen");
        domain.setActive(true);
        return domain;
    }
}
