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

package org.niord.core.mailinglist;

import org.slf4j.Logger;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.MapMessage;
import javax.jms.MessageListener;

/**
 * Used for listening for message status updates via JMS
 */
@MessageDriven(
        name = "MailingListStatusChangeMDB",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "java:/jms/topic/MessageStatusTopic"),
                @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
        })
@SuppressWarnings("unused")
public class MailingListMessageListener implements MessageListener {

    @Inject
    Logger log;

    @Inject
    MailingListExecutionService mailingListExecutionService;


    /**
     * {@inheritDoc}
     */
    @Override
    public void onMessage(javax.jms.Message message) {

        try {
            MapMessage msg = (MapMessage) message;

            log.debug("Received " + msg.getString("STATUS") + " message status update for UID: " + msg.getString("UID"));
            mailingListExecutionService.checkStatusChangeMailingListExecution(msg.getString("UID"));

        } catch (Throwable e) {
            log.error("Failed processing JMS message " + message, e);
        }
    }
}
