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
