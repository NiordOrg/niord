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

import org.hibernate.search.batchindexing.impl.SimpleIndexingProgressMonitor;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.Search;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;

/**
 * Launches the Hibernate Search index
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class HibernateSearchIndexService extends BaseService {

    @Inject
    private Logger log;

    @Resource
    TimerService timerService;

    /** Called upon application startup */
    @PostConstruct
    public void init() {
        // In order not to stall webapp deployment, wait 2 seconds starting the search index
        timerService.createSingleActionTimer(2000, new TimerConfig());
    }

    /**
     * Creates the full text indexes
     */
    @Timeout
    private void generateFullTextIndexes() {
        try {
            log.info("Start Hibernate Search indexer");

            long t0 = System.currentTimeMillis();
            FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(em);
            fullTextEntityManager.createIndexer()
                    .progressMonitor(new SimpleIndexingProgressMonitor(5000))
                    .startAndWait();
            log.info("Created Hibernate Search indexes in " + (System.currentTimeMillis() - t0) + " ms");

        } catch (Exception e) {
            log.error("Error indexing AtoNs", e);
        }
    }
}
