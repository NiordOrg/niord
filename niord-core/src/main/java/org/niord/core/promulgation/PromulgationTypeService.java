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
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.inject.Inject;
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
     * Returns the list of active promulgation types for the current domain
     * @return the list of active promulgation types
     */
    public List<PromulgationType> getActivePromulgationTypes() {

        Domain domain = domainService.currentDomain();

        if (domain == null) {
            return em.createNamedQuery("PromulgationType.findActive", PromulgationType.class)
                    .getResultList();
        } else {
            return em.createNamedQuery("PromulgationType.findActiveByDomain", PromulgationType.class)
                    .setParameter("domainId", domain.getDomainId())
                    .getResultList();
        }
    }


    /**
     * Returns the list of promulgation types for the given message
     * @return the list of promulgation types for the given message
     */
    public List<PromulgationType> getPromulgationTypes(SystemMessageVo message) {

        if (message.getPromulgations() == null) {
            return Collections.emptyList();
        }

        Set<String> typeIds = message.getPromulgations().stream()
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
    public PromulgationType updatePromulgationType(PromulgationType type) {

        PromulgationType original = getPromulgationType(type.getTypeId());
        if (original == null) {
            throw new IllegalArgumentException("Non-existing promulgation type " + type.getTypeId());
        }

        log.info("Updating promulgation type " + type.getTypeId());

        original.setName(type.getName());
        original.setActive(type.isActive());
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
    public boolean deletePromulgationType(String typeId) {
        PromulgationType original = getPromulgationType(typeId);
        if (original != null) {
            remove(original);
            return true;
        }
        return false;
    }
}
