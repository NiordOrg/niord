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
import org.niord.core.promulgation.vo.PromulgationServiceDataVo;
import org.niord.core.util.CdiUtils;
import org.slf4j.Logger;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.naming.NamingException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the list of PromulgationServices, such as MailingListPromulgationService, NavtexPromulgationService, etc.
 * <p>
 * The PromulgationManager facilitates a plug-in architecture where all promulgation services should start by
 * calling {@code registerPromulgationService()}, typically in a @PostConstruct method.
 * <p>
 * Subsequently, the PromulgationManager serves as an interface between the MessageService the list of
 * promulgation services.
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class PromulgationManager {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;


    Map<String, PromulgationServiceDataVo> services = new ConcurrentHashMap<>();

    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/

    /**
     * Registers the promulgation service
     * @param serviceData the promulgation service to register
     */
    public void registerPromulgationService(PromulgationServiceDataVo serviceData) {
        services.put(serviceData.getType(), serviceData);
        log.info("Registered promulgation service " + serviceData.getType());
    }


    /**
     * Returns all promulgation service data entities
     * @return all promulgation service data entities
     */
    public List<PromulgationServiceDataVo> getAllPromulgationServices() {
        return services.values().stream()
                .sorted()
                .collect(Collectors.toList());
    }


    /**
     * Updates the active status, priority or domains of a promulgation service
     * @return the active status, priority or domains of a promulgation service
     */
    public PromulgationServiceDataVo updatePromulgationService(PromulgationServiceDataVo serviceData) throws Exception {
        BasePromulgationService service = instantiatePromulgationService(serviceData.getType());
        serviceData = service.updatePromulgationService(serviceData);

        // Update the cached version
        services.put(serviceData.getType(), serviceData);

        return serviceData;
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/

    /**
     * Updates the new message template with a promulgation by the registered promulgation services
     * @param message the new message template
     */
    public void onNewTemplateMessage(Message message) {
        instantiatePromulgationServices(true).forEach(p -> p.onNewTemplateMessage(message));
    }


    /**
     * Prior to creating a new message, let the registered promulgation services check up on promulgations.
     * @param message the message about to be created
     */
    public void onCreateMessage(Message message) {
        // Let promulgation services check up on their own promulgations
        instantiatePromulgationServices(false).forEach(p -> p.onCreateMessage(message));
    }


    /**
     * Prior to updating an existing message, let the registered promulgation services check up on promulgations.
     * @param message the message about to be updated
     */
    public void onUpdateMessage(Message message) {
        // Let promulgation services check up on their own promulgations
        instantiatePromulgationServices(false).forEach(p -> p.onUpdateMessage(message));
    }


    /**
     * Prior to changing status of an existing message, let the registered promulgation services check up on promulgations.
     * @param message the message about to be updated
     */
    public void onUpdateMessageStatus(Message message) {
        // Let promulgation services check up on their own promulgations
        instantiatePromulgationServices(false).forEach(p -> p.onUpdateMessageStatus(message));
    }


    /**
     * Generates a message promulgation record for the given type and message
     * @param type the type of promulgation to generate
     * @param message the message template to generate a promulgation for
     * @return the promulgation
     */
    public BasePromulgation<?> generateMessagePromulgation(String type, Message message) {
        return instantiatePromulgationService(type).generateMessagePromulgation(message);
    }


    /***************************************/
    /** Utility function                  **/
    /***************************************/


    /**
     * Returns the list of instantiated promulgation services sorted by priority
     * @param onlyActive whether to include all, or only active promulgation services
     * @return the list of promulgation services
     */
    private List<BasePromulgationService> instantiatePromulgationServices(boolean onlyActive) {

        Domain domain = domainService.currentDomain();

        return services.values().stream()
                .filter(p -> p.isActive() || !onlyActive)
                .filter(p -> domain == null ||
                             p.getDomains().stream().anyMatch(d -> d.getDomainId().equals(domain.getDomainId())))
                .sorted()
                .map(s -> instantiatePromulgationService(s.getServiceClass()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Instantiates the promulgation service bean for the promulgation type.
     * Returns null if the class could not be instantiated
     * @param type the promulgation service type
     */
    private BasePromulgationService instantiatePromulgationService(String type) {
        return instantiatePromulgationService(services.get(type).getServiceClass());
    }


    /**
     * Instantiates the promulgation service bean for the given class.
     * Returns null if the class could not be instantiated
     * @param promulgationServiceClass the promulgation service class
     */
    private <T extends BasePromulgationService> T instantiatePromulgationService(Class<T> promulgationServiceClass) {
        try {
            return CdiUtils.getBean(promulgationServiceClass);
        } catch (NamingException e) {
            log.warn("Could not instantiate promulgation service for class " + promulgationServiceClass);
            return null;
        }
    }

}
