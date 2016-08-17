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
