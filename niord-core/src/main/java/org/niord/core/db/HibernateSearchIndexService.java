/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.core.db;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.util.common.SearchException;
import org.niord.core.aton.AtonNode;
import org.slf4j.Logger;

/**
 * Launches the Hibernate Search index
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class HibernateSearchIndexService {

    @Inject
    private Logger log;

    @Inject
    EntityManager entityManager;

    /** Called upon application startup */
    @Transactional
    void init(@Observes StartupEvent ev) {
        // In order not to stall webapp deployment, wait 2 seconds starting the search index
        generateFullTextIndexes();
    }

    /**
     * Creates the full text indexes
     */
    private void generateFullTextIndexes() {
        log.info("Start Hibernate Search indexer");

        long t0 = System.currentTimeMillis();
        SearchSession searchSession = Search.session(entityManager);

        // Create a mass indexer
        MassIndexer indexer = searchSession.massIndexer( AtonNode.class )
                .threadsToLoadObjects( 7 );

        // And perform the indexing
        try {
            indexer.startAndWait();
        } catch (InterruptedException | SearchException e) {
            log.error("Error indexing AtoNs", e);
        }
    }
}
