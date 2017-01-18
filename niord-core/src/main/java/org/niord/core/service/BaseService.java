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
package org.niord.core.service;

import org.niord.core.model.BaseEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DAO-like base class for services that work on work on {@linkplain BaseEntity}
 */
@SuppressWarnings("unused")
public abstract class BaseService {

    @Inject
    protected EntityManager em;

    /**
     * Constructor
     */
    protected BaseService() {
    }

    /**
     * Constructor
     *
     * @param entityManager the ent
     */
    protected BaseService(EntityManager entityManager) {
        this.em = entityManager;
    }

    /**
     * Returns the entity with the given id or {@code null} if none is found
     *
     * @param clazz the entity class
     * @param id the id of the entity
     * @return the entity with the given id or {@code null} if none is found
     */
    public <E extends BaseEntity> E getByPrimaryKey(Class<E> clazz, Object id) {
        try {
            return em.find(clazz, id);
        } catch (EntityNotFoundException e) {
            return null;
        }
    }

    /**
     * Removes the given entity from the database
     *
     * @param entity the entity to remove
     */
    public void remove(BaseEntity entity) {

        em.remove(em.merge(entity));
    }

    /**
     * Persists or updates the given entity
     *
     * @param entity the entity to persist or update
     * @return thed update entity
     */
    public <E extends BaseEntity> E saveEntity(E entity) {
        if (entity.isPersisted()) {
            // Update existing
            entity = em.merge(entity);
        } else {
            // Save new
            em.persist(entity);
        }
        return entity;
    }

    /**
     * Returns the first element of the list, or {@code null} if the list is empty or {@code null}
     *
     * @param list the list
     * @return the first element
     */
    public static <T> T getSingleOrNull(List<T> list) {
        return (list == null || list.size() == 0) ? null : list.get(0);
    }

    /**
     * Returns all entities with the given class
     *
     * @param entityType the class
     * @return all entities with the given class
     */
    public <E extends BaseEntity> List<E> getAll(Class<E> entityType) {
        em.clear();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> cq = cb.createQuery(entityType);
        cq.from(entityType);
        return em.createQuery(cq).getResultList();
    }

    /**
     * Counts all entities with the given class
     *
     * @param entityType the class
     * @return the number of entities with the given class
     */
    public <E extends BaseEntity> long count(Class<E> entityType) {
        em.clear();

        CriteriaBuilder qb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = qb.createQuery(Long.class);
        cq.select(qb.count(cq.from(entityType)));
        return em.createQuery(cq).getSingleResult();
    }


    /**
     * Returns a list of persisted entities
     * @param entityType the class
     * @param entities the list of entities to look up persisted entities for
     * @return the list of corresponding persisted entities
     */
    public <E extends BaseEntity> List<E> persistedList(Class<E> entityType, List<E> entities) {
        return entities.stream()
                .map(e -> getByPrimaryKey(entityType, e.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of persisted entities for the given IDs
     * @param entityType the class
     * @param ids the list of IDs to look up persisted entities for
     * @return the list of corresponding persisted entities
     */
    public <ID extends Serializable, E extends BaseEntity<ID>> List<E> persistedListForIds(Class<E> entityType, List<ID> ids) {
        return ids.stream()
                .map(id -> getByPrimaryKey(entityType, id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Checks if two entities have the same ID, catering with null parameters
     * @param e1 the first entity
     * @param e2 the second entity
     * @return if two entities have the same ID, catering with null parameters
     */
    public static <E extends BaseEntity> boolean sameEntities(E e1, E e2) {
        if (e1 == null && e2 == null) {
            return true;
        } else if (e1 == null || e2 == null) {
            return false;
        }
        return e1.getId().equals(e2.getId());
    }


    /**
     * Checks if two lists of entities are identical by matching IDs
     * @param l1 the first list of entities
     * @param l2 the second list of entities
     * @param sameOrder if the order is significant
     * @return if two lists of entities are identical by matching IDs
     */
    public static <E extends BaseEntity> boolean sameEntities(List<E> l1, List<E> l2, boolean sameOrder) {
        if (l1 == null && l2 == null) {
            return true;
        } else if (l1 == null || l2 == null || l1.size() != l2.size()) {
            return false;
        }
        if (sameOrder) {
            for (int x = 0; x < l1.size(); x++) {
                if (!sameEntities(l1.get(x), l2.get(x))) {
                    return false;
                }
            }
            return true;
        } else {
            Set<Object> ids = l1.stream()
                    .map(BaseEntity::getId)
                    .collect(Collectors.toSet());
            return l2.stream()
                    .allMatch(e -> ids.contains(e.getId()));
        }
    }

}
