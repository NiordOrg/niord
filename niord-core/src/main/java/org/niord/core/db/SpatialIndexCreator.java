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

import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.util.List;

/**
 * Creates the spatial indexes needed.
 * Cannot be accommodated with usual JPA annotations, methinks.
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class SpatialIndexCreator extends BaseService {

    @Inject
    private Logger log;


    /**
     * Checks and creates spatial indexes
     */
    @PostConstruct
    private void createSpatialIndexes() {

        try {
            if (!hasIndex("Feature", "feature_geometry_index")) {
                createSpatialIndex("Feature", "geometry", "feature_geometry_index");
                log.info("Created spatial index on Feature.geometry");
            }

            if (!hasIndex("AtonNode", "aton_node_geometry")) {
                createSpatialIndex("AtonNode", "geometry", "aton_node_geometry");
                log.info("Created spatial index on AtonNode.geometry");
            }
        } catch (Exception e) {
            log.error("Failed checking/creating spatial indexes", e);
        }
    }


    /** Creates a spatial index for the given table and column */
    private void createSpatialIndex(String table, String column, String indexName) {
        String sql = String.format("alter table %s add spatial index %s (%s)", table, indexName, column);
        em.createNativeQuery(sql).executeUpdate();
    }


    /** Checks if the given table has an index with the given name */
    private boolean hasIndex(String table, String indexName) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em
                .createNativeQuery("SHOW INDEX FROM " + table)
                .getResultList();
        for (Object[] row : rows) {
            if (indexName.equals(row[2])) {
                return true;
            }
        }

        return false;
    }
}
