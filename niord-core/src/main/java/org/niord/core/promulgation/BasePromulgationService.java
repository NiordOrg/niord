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
import javax.enterprise.event.Observes;
import javax.inject.Inject;

/**
 * Base class for the different types of promulgation services, such as NavtexPromulgationService, etc.
 * <p>
 * NB: Subclasses must annotated with @ApplicationContext and handle @Observes StartupEvent events to
 *     properly register the promulgation service with the PromulgationManager
 */
public abstract class BasePromulgationService extends BaseService {

    @Inject
    public Logger log;

    @Inject
    public PromulgationManager promulgationManager;

    @Inject
    public DomainService domainService;

    @Inject
    public NiordApp app;


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
    public void onLoadSystemMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException {
    }


    /**
     * When a message is created as a copy of another message, let the registered promulgation services
     * check up on promulgations.
     * @param message the message created as a copy of another message
     * @param type the promulgation type
     */
    public void onCopyMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException {
    }


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


    /***************************************/
    /** Generating promulgations          **/
    /***************************************/


    /**
     * Manually generates a message promulgation record for the given type and message based on the contents of the message.
     * The other way to generate message promulgations is by executing a template.
     * @param message the message template to generate a promulgation for
     * @param type the promulgation type
     * @return the promulgation
     */
    public BaseMessagePromulgationVo generateMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        return null;
    }


    /**
     * Called when a message promulgation has been generated, either manually by calling {@code generateMessagePromulgation}
     * or by executing a template.
     * Override this method to perform any clean-up of the generated message promulgation.
     * @param message the message template to generate a promulgation for
     * @param type the promulgation type
     */
    public void messagePromulgationGenerated(SystemMessageVo message, PromulgationType type) throws PromulgationException {
    }


    /**
     * Resets the message promulgation record for the given type and message.
     * May e.g. be called before generating the promulgations by executing a template.
     * @param message the message template to reset the promulgation for
     * @param type the promulgation type
     */
    public void resetMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
    }

}
