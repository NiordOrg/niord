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

import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.BasePromulgationVo;
import org.niord.core.promulgation.vo.PromulgationServiceDataVo;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for the different types of promulgation services, such as NavtexPromulgationService, etc.
 * <p>
 *     NB: Subclasses must annotated with @Singleton and @Startup and call registerPromulgationService() in a @PostConstruct
 *     to properly register the promulgation service.
 * </p>
 */
@SuppressWarnings("unused")
public abstract class BasePromulgationService extends BaseService {

    @Inject
    Logger log;

    @Inject
    PromulgationManager promulgationManager;

    @Inject
    DomainService domainService;

    /**
     * Should be called from sub-classes in a @PostConstruct to register the promulgation service
     * Registers the promulgation service with the promulgation manager
     */
    public void registerPromulgationService() {

        // Loads or creates the promulgation service instance
        PromulgationServiceData serviceData = getPromulgationServiceData(true);

        promulgationManager.registerPromulgationService(serviceData.toVo(getClass()));
    }


    /**
     * Returns or creates the associated promulgation service data. Give sub-classes chance to override.
     *
     * @return the associated promulgation service data
     */
    protected PromulgationServiceData getPromulgationServiceData(boolean create) {
        PromulgationServiceData serviceData = null;
        try {
            serviceData = em.createNamedQuery("PromulgationServiceData.findByType", PromulgationServiceData.class)
                    .setParameter("type", getType())
                    .getSingleResult();
        } catch (Exception e) {
            if (create) {
                serviceData = new PromulgationServiceData();
                serviceData.setType(getType());
                serviceData.setActive(false);
                serviceData.setPriority(getDefaultPriority());
                em.persist(serviceData);
                log.info("Created new promulgation service " + serviceData.getType());
            }
        }
        return serviceData;
    }


    /**
     * Returns a type key for the promulgation service. Must be unique
     *
     * @return a type key for the promulgation service
     */
    public abstract String getType();


    /**
     * Returns a default priority of the promulgation service. Used for sorting the promulgation services.
     *
     * @return a default priority of the promulgation service
     */
    public abstract int getDefaultPriority();


    /**
     * Updates and adds promulgations to the message value object
     *
     * @param message the message value object
     */
    public abstract void onLoadSystemMessage(SystemMessageVo message) throws PromulgationException;


    /**
     * Prior to creating a new message, let the registered promulgation services check up on promulgations.
     * Default implementation does nothing.
     *
     * @param message the message about to be created
     */
    public void onCreateMessage(Message message) throws PromulgationException {
    }


    /**
     * Prior to updating an existing message, let the registered promulgation services check up on promulgations.
     * Default implementation does nothing.
     *
     * @param message the message about to be updated
     */
    public void onUpdateMessage(Message message) throws PromulgationException {
    }


    /**
     * Prior to changing status of an existing message, let the registered promulgation services check up on promulgations.
     * Default implementation does nothing.
     *
     * @param message the message about to be updated
     */
    public void onUpdateMessageStatus(Message message) throws PromulgationException {
    }


    /**
     * Generates a message promulgation record for the given type and message
     *
     * @param message the message template to generate a promulgation for
     * @return the promulgation
     */
    public BasePromulgationVo generateMessagePromulgation(SystemMessageVo message) throws PromulgationException {
        return null;
    }


    /**
     * Updates the active status, priority or domains of a promulgation service
     *
     * @return the active status, priority or domains of a promulgation service
     */
    public PromulgationServiceDataVo updatePromulgationService(PromulgationServiceDataVo serviceData) {

        PromulgationServiceData original = getPromulgationServiceData(false);
        if (!original.getType().equals(serviceData.getType())) {
            throw new IllegalArgumentException("Invalid promulgation service " + serviceData);
        }

        original.setActive(serviceData.isActive());
        original.setPriority(serviceData.getPriority());

        // Update list of domains
        List<Domain> domains = serviceData.getDomains().stream()
                .map(Domain::new)
                .collect(Collectors.toList());
        original.setDomains(domainService.persistedDomains(domains));

        return saveEntity(original).toVo(getClass());
    }
}

