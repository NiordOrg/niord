/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.promulgation;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.service.BaseService;
import org.niord.model.message.Type;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the promulgation types.
 * <p>
 * Niord sports a plug-in architecture where promulgation services (such as MailingListPromulgationService,
 * NavtexPromulgationService, etc.) all register with the {@linkplain PromulgationManager}.
 * <p>
 * Each promulgation service is associated with a list of promulgation types, as defined by the
 * {@linkplain PromulgationType} class.
 * As an example, there may be "NAVTEX-DK" and "NAVTEX-GL" types managed by NAVTEX promulgation service.
 * <p>
 * In turn, messages are associated with a list of {@linkplain BaseMessagePromulgation}-derived entities that
 * are each tied to a promulgation type.
 */
@ApplicationScoped
public class PromulgationTypeService extends BaseService {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;


    /**
     * Returns all promulgation types
     * @return all promulgation types
     */
    public List<PromulgationType> getAll() {
        return em.createNamedQuery("PromulgationType.findAll", PromulgationType.class)
                .getResultList();
    }


    /**
     * Returns all active promulgation types
     * @return all active promulgation types
     */
    public List<PromulgationType> getActive() {
        return em.createNamedQuery("PromulgationType.findActive", PromulgationType.class)
                .getResultList();
    }


    /**
     * Returns the list of active promulgation types for the current domain
     * @param messageType optionally specify a message type that must be matched by the promulgation types
     * @return the list of active promulgation types
     */
    @SuppressWarnings("all")
    public List<PromulgationType> getActivePromulgationTypes(Type messageType) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PromulgationType> typeQuery = cb.createQuery(PromulgationType.class);
        Root<PromulgationType> typeRoot = typeQuery.from(PromulgationType.class);
        CriteriaHelper<PromulgationType> criteriaHelper = new CriteriaHelper<>(cb, typeQuery);

        // Only active promulgation types
        criteriaHelper.add(cb.equal(typeRoot.get("active"), true));

        // Filter on domain
        Domain domain = domainService.currentDomain();
        if (domain != null) {
            Join<PromulgationType, Domain> domainJoin = typeRoot.join("domains", JoinType.LEFT);
            criteriaHelper.equals(domainJoin.get("domainId"), domain.getDomainId());
        }

        // If specified, filter on message type
        /**
         * Hibernate seems to be buggy in its handling of element-collections, so, we filter in code. See e.g.
         * https://hibernate.atlassian.net/browse/HHH-6686
         *
        if (messageType != null) {
            Expression<Collection<Type>> types = typeRoot.get("messageTypes");
            criteriaHelper.add(cb.or(
                    cb.isEmpty(types),
                    cb.isMember(messageType, types)));
        }
         **/

        // Complete the query
        typeQuery.select(typeRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(cb.asc(typeRoot.get("priority")));

        // Execute the query and update the search result
        return em.createQuery(typeQuery)
                .getResultList().stream()

                // See Hibernate comment above - filter on message types in code for now
                .filter(pt -> messageType == null ||
                        pt.getMessageTypes().isEmpty() ||
                        pt.getMessageTypes().contains(messageType))
                .collect(Collectors.toList());
    }


    /**
     * Returns the list of active promulgation types for the given service ID and message
     * @param serviceId the promulgation service ID
     * @param message the message
     * @return the list of active promulgation types
     */
    @SuppressWarnings("all")
    public List<PromulgationType> getActivePromulgationTypes(String serviceId, Message message) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PromulgationType> typeQuery = cb.createQuery(PromulgationType.class);
        Root<PromulgationType> typeRoot = typeQuery.from(PromulgationType.class);
        CriteriaHelper<PromulgationType> criteriaHelper = new CriteriaHelper<>(cb, typeQuery);

        // Only active promulgation types
        criteriaHelper.add(cb.equal(typeRoot.get("active"), true));

        // Filter on promulgation service ID
        criteriaHelper.add(cb.equal(typeRoot.get("serviceId"), serviceId));

        // Filter on domain and message series
        Join<PromulgationType, Domain> domainJoin = typeRoot.join("domains", JoinType.LEFT);
        Join<PromulgationType, MessageSeries> seriesJoin = domainJoin.join("messageSeries", JoinType.LEFT);
        criteriaHelper.equals(seriesJoin.get("seriesId"), message.getMessageSeries().getSeriesId());

        // If specified, filter on message type
        /**
         * Hibernate seems to be buggy in its handling of element-collections, so, we filter in code. See e.g.
         * https://hibernate.atlassian.net/browse/HHH-6686
         *
        Expression<Collection<Type>> types = typeRoot.get("messageTypes");
        criteriaHelper.add(cb.or(
                cb.isEmpty(types),
                cb.isMember(message.getType(), types)));
         **/

        // Complete the query
        typeQuery.select(typeRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(cb.asc(typeRoot.get("priority")));

        // Execute the query and update the search result
        return em.createQuery(typeQuery)
                .getResultList().stream()

                // See Hibernate comment above - filter on message types in code for now
                .filter(pt -> pt.getMessageTypes().isEmpty() ||
                        pt.getMessageTypes().contains(message.getType()))
                .collect(Collectors.toList());
    }


    /**
     * Returns the list of promulgation types for the given message
     * @return the list of promulgation types for the given message
     */
    public List<PromulgationType> getPromulgationTypes(SystemMessageVo message) {
        return getPromulgationTypes(message, false);
    }


    /**
     * Returns the list of promulgation types for the given message
     * @return the list of promulgation types for the given message
     */
    public List<PromulgationType> getPromulgationTypes(SystemMessageVo message, boolean onlyPromulgating) {

        if (message.getPromulgations() == null) {
            return Collections.emptyList();
        }

        Set<String> typeIds = message.getPromulgations().stream()
                .filter(p -> p.isPromulgate() || !onlyPromulgating)
                .map(p -> p.getType().getTypeId())
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            return em.createNamedQuery("PromulgationType.findByTypeIds", PromulgationType.class)
                    .setParameter("typeIds", typeIds)
                    .getResultList();
        }
    }


    /**
     * Returns the promulgation type with the given ID. Returns null if not found
     * @param typeId the promulgation type ID
     * @return the promulgation type with the given ID
     */
    public PromulgationType getPromulgationType(String typeId) {
        try {
            return  em.createNamedQuery("PromulgationType.findByTypeId", PromulgationType.class)
                    .setParameter("typeId", typeId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Creates a new promulgation type
     * @param type the template for the new promulgation type
     * @return the newly created promulgation type
     */
    @Transactional
    public PromulgationType createPromulgationType(PromulgationType type) {
        PromulgationType original = getPromulgationType(type.getTypeId());
        if (original != null) {
            throw new IllegalArgumentException("Promulgation type already exists: " + type.getTypeId());
        }

        log.info("Creating new promulgation type " + type.getTypeId());

        // Update the domain references with the persisted ones
        type.setDomains(domainService.persistedDomains(type.getDomains()));

        return saveEntity(type);
    }


    /**
     * Updates the given promulgation type
     * @param type the template for the updated promulgation type
     * @return the updated promulgation type
     */
    @Transactional
    public PromulgationType updatePromulgationType(PromulgationType type) {

        PromulgationType original = getPromulgationType(type.getTypeId());
        if (original == null) {
            throw new IllegalArgumentException("Non-existing promulgation type " + type.getTypeId());
        }

        log.info("Updating promulgation type " + type.getTypeId());

        original.setName(type.getName());
        original.setActive(type.isActive());
        original.setRequirement(type.getRequirement());
        original.setPriority(type.getPriority());
        original.setLanguage(StringUtils.defaultIfBlank(type.getLanguage(), null));

        // Update the domain references with the persisted ones
        original.setDomains(domainService.persistedDomains(type.getDomains()));

        original.getMessageTypes().clear();
        original.getMessageTypes().addAll(type.getMessageTypes());

        original.getScriptResourcePaths().clear();
        original.getScriptResourcePaths().addAll(type.getScriptResourcePaths());

        return saveEntity(original);
    }


    /**
     * Deletes theh promulgation type with the given ID
     * @param typeId the ID of the promulgation type to delete
     * @return if the promulgation type was deleted
     * @noinspection all
     */
    @Transactional
    public boolean deletePromulgationType(String typeId) {
        PromulgationType original = getPromulgationType(typeId);
        if (original != null) {
            remove(original);
            return true;
        }
        return false;
    }
}
