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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niord.core.message.MessageService;
import org.niord.core.model.BaseEntity;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;

/**
 * Used for listening for message status updates via JMS
 */
@SuppressWarnings("unused")
@ApplicationScoped
public class MailingListMessageListener implements Runnable {

    @Inject
    Logger log;

    @Inject
    MailingListService mailingListService;

    @Inject
    MailingListExecutionService mailingListExecutionService;

    @Inject
    MessageService messageService;

    @Inject
    ConnectionFactory connectionFactory;

    @ConfigProperty(name = "niord.jms.topic.messagestatustopic", defaultValue = "messageStatus")
    String messageStatusTopic;
    
    private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

    void onStart(@Observes StartupEvent ev) {
        scheduler.submit(this);
    }

    void onStop(@Observes ShutdownEvent ev) {
        scheduler.shutdown();
    }

    @Override
    public void run() {
        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            JMSConsumer consumer = context.createConsumer(context.createTopic(messageStatusTopic));
            while (true) {
                Message message = consumer.receive();
                if (message == null) return;
                onMessage(message);
            }
        } 
    }

    /**
     * {@inheritDoc}
     */
    public void onMessage(jakarta.jms.Message message) {
        try {
            MapMessage msg = (MapMessage) message;

            String uid = msg.getString("UID");
            Status status = Status.valueOf(msg.getString("STATUS"));

            // HACK because we cannot get XA transactions working with Quarkus.
            // 10 seconds should be enough to make sure that the DB has been updated
            // In the original JDBC transaction
            Thread.sleep(10000);
            
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

        // Don't send emails for cancellation warning messages
        org.niord.core.message.Message message = messageService.findByUid(messageUid);
        if (message != null && messageService.isCancellationMessage(message)) {
            log.debug("Skipping mailing list execution for cancellation warning message: " + messageUid);
            return;
        }

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
