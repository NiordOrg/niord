/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
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
