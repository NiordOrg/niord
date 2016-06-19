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
package org.niord.core.model;

import java.util.List;

/**
 * Can be implemented by indexed entities.
 * <p>
 * The parent entity should be tagged with either {@code @OrderColumn(name = "indexNo")}
 * or {@code @SortBy("indexNo ASC")}.
 * In the latter case, manually update the index by e.g. calling {@code IndexedEntity.updateIndexedEntities(entities);}
 * in a @PrePersist-method of the parent entity.
 */
@SuppressWarnings("unused")
public interface IndexedEntity {

    int getIndexNo();

    void setIndexNo(int indexNo);

    /**
     * Updates the indexes of a list of IndexedEntity entities
     * @param entities the list of entities to have their index updated in consecutive order
     */
    static <E extends IndexedEntity> void updateIndexedEntities(List<E> entities) {
        if (entities != null) {
            int index = 0;
            for (IndexedEntity entity : entities) {
                entity.setIndexNo(index++);
            }
        }
    }
}
