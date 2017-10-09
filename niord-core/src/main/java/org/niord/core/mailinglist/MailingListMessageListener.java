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

import org.niord.core.model.BaseEntity;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.MapMessage;
import javax.jms.MessageListener;
import java.util.List;
import java.util.stream.Collectors;

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
    MailingListService mailingListService;

    @Inject
    MailingListExecutionService mailingListExecutionService;


    /**
     * {@inheritDoc}
     */
    @Override
    public void onMessage(javax.jms.Message message) {

        try {
            MapMessage msg = (MapMessage) message;

            String uid = msg.getString("UID");
            Status status = Status.valueOf(msg.getString("STATUS"));

            log.debug("Received " + status + " message status update for UID: " + uid);
            checkStatusChangeMailingListExecution(uid, status);

        } catch (Throwable e) {
            log.error("Failed processing JMS message " + message, e);
        }
    }


    /**
     * Handle mailing list execution for the message. Called from the MailingListMessageListener MDB listener.
     *
     * @param messageUid the UID of the message
     * @param status the message status
     */
    public void checkStatusChangeMailingListExecution(String messageUid, Status status) {

        long t0 = System.currentTimeMillis();

        List<Integer> triggerIds = mailingListService.findStatusChangeTriggers(status).stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toList());

        log.debug(String.format("Found %d status change triggers for %s in status %s",
                triggerIds.size(),
                messageUid,
                status));

        for (Integer triggerId : triggerIds) {
            try {
                // NB: This function requires a new transaction
                mailingListExecutionService.executeStatusChangeTrigger(triggerId, messageUid);
            } catch (Exception e) {
                log.error("Error executing status-change mailing-list trigger " + triggerId, e);
            }
        }
        log.debug(String.format("Executed %d status change triggers for %s in status %s in %d ms",
                triggerIds.size(),
                messageUid,
                status,
                System.currentTimeMillis() - t0));
    }

}
