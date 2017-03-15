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

import org.niord.core.NiordApp;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

/**
 * Base class for the different types of promulgation services, such as NavtexPromulgationService, etc.
 * <p>
 * NB: Subclasses must annotated with @Singleton and @Startup to properly register the promulgation service
 *     with the PromulgationManager
 */
public abstract class BasePromulgationService extends BaseService {

    @Inject
    Logger log;

    @Inject
    PromulgationManager promulgationManager;

    @Inject
    DomainService domainService;

    @Inject
    NiordApp app;


    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/


    /**
     * Registers the promulgation service with the promulgation manager
     */
    @PostConstruct
    public void init() {
        promulgationManager.registerPromulgationService(this);
    }


    /**
     * Returns a ID of the promulgation service. Must be unique
     * @return a ID of the promulgation service
     */
    public abstract String getServiceId();


    /**
     * Returns a name of the promulgation service
     * @return a name of the promulgation service
     */
    public abstract String getServiceName();


    /**
     * Returns the language configured for the promulgation type
     * @return the language configured for the promulgation type
     */
    public String getLanguage(PromulgationType type) {
        String lang = type.getLanguage();
        return app.getLanguage(lang);
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/


    /**
     * Updates and adds promulgations to the message value object
     * @param message the message value object
     * @param type the promulgation type
     */
    public abstract void onLoadSystemMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException;


    /**
     * Prior to creating a new message, let the registered promulgation services check up on promulgations.
     * Default implementation does nothing.
     * @param message the message about to be created
     * @param type the promulgation type
     */
    public void onCreateMessage(Message message, PromulgationType type) throws PromulgationException {
    }


    /**
     * Prior to updating an existing message, let the registered promulgation services check up on promulgations.
     * Default implementation does nothing.
     * @param message the message about to be updated
     * @param type the promulgation type
     */
    public void onUpdateMessage(Message message, PromulgationType type) throws PromulgationException {
    }


    /**
     * Prior to changing status of an existing message, let the registered promulgation services check up on promulgations.
     * Default implementation does nothing.
     * @param message the message about to be updated
     * @param type the promulgation type
     */
    public void onUpdateMessageStatus(Message message, PromulgationType type) throws PromulgationException {
    }


    /**
     * Generates a message promulgation record for the given type and message
     * @param message the message template to generate a promulgation for
     * @param type the promulgation type
     * @return the promulgation
     */
    public BaseMessagePromulgationVo generateMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        return null;
    }


    /**
     * Resets the message promulgation record for the given type and message
     * @param message the message template to reset the promulgation for
     * @param type the promulgation type
     */
    public void resetMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
    }
}
