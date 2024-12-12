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

package org.niord.s100.s124.promulgation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;

/**
 * Used for listening for message status updates via JMS
 */

@SuppressWarnings("unused")
@ApplicationScoped
public class BaleenMessageListener implements Runnable {

    @Inject
    Logger log;

    @Inject
    BaleenPromulgationService baleenPromulgationService;


    @ConfigProperty(name = "niord.jms.topic.messagestatustopic", defaultValue = "messageStatus")
    String messageStatusTopic;

    @Inject
    ConnectionFactory connectionFactory;

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
            System.out.println("Checking Baleen message " + msg.getString("UID"));

            // HACK because we cannot get XA transactions working with Quarkus.
            // 10 seconds should be enough to make sure that the DB has been updated
            // In the original JDBC transaction
            Thread.sleep(10000);

            // Check if this is a published message
            if (Status.PUBLISHED.name().equals(msg.getString("STATUS"))) {
                log.info("Received PUBLISHED message status update for UID: " + msg.getString("UID"));

                baleenPromulgationService.checkPromulgateMessage(msg.getString("UID"));
            }
        } catch (Throwable e) {
            log.error("Failed processing JMS message " + message, e);
        }
    }
}
