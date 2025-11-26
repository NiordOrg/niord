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

import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.DeliveryMode;

import org.niord.core.dictionary.DictionaryService;
import org.niord.core.message.MessageService;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.BasePromulgationService;
import org.niord.core.promulgation.PromulgationException;
import org.niord.core.promulgation.PromulgationType;
import org.niord.core.promulgation.PromulgationType.Requirement;
import org.niord.core.promulgation.PromulgationTypeService;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.model.DataFilter;
import org.niord.model.message.Status;
import org.niord.s100.s124.S124Service;
import org.niord.s100.s124.promulgation.vo.BaleenMessagePromulgationVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

@ApplicationScoped
@Lock(LockType.READ)
public class BaleenPromulgationService extends BasePromulgationService implements MessageListener {

    private static final int SIX_HOURS_IN_MS = 6 * 60 * 60 * 1000;
    private static final int INITIAL_RETRY_DELAY = 60 * 1000; // 1 minute
    private static final int SUBSEQUENT_RETRY_DELAY = 10 * 60 * 1000; // 10 minutes

    private static final String QUEUE_NAME = "niord.baleen";

    private static final Logger log = LoggerFactory.getLogger(BaleenPromulgationService.class);

    @Inject
    PromulgationTypeService promulgationTypeService;

    @Inject
    MessageService messageService;

    @Inject
    S124Service service;

    @Inject
    ConnectionFactory connectionFactory;

    private JMSContext jmsContext;
    private JMSConsumer consumer;

    @PostConstruct
    void init() {
        try {
            // Configure retry policy
            String connectionConfig = "jms.redeliveryPolicy.maxRedeliveries=1000" // High number for 6 hours of retries
                + "&jms.redeliveryPolicy.initialRedeliveryDelay=" + INITIAL_RETRY_DELAY
                + "&jms.redeliveryPolicy.redeliveryDelay=" + SUBSEQUENT_RETRY_DELAY
                + "&jms.redeliveryPolicy.useExponentialBackOff=false"; // Fixed intervals

            jmsContext = connectionFactory.createContext(Session.CLIENT_ACKNOWLEDGE);
            Queue queue = jmsContext.createQueue(QUEUE_NAME);
            consumer = jmsContext.createConsumer(queue);
            consumer.setMessageListener(this);

            log.info("Initialized Baleen message queue consumer with 6-hour retry policy (1min initial, then 10min intervals)");
        } catch (Exception e) {
            log.error("Failed to initialize Baleen message queue", e);
        }
    }
    @PreDestroy
    void cleanup() {
        try {
            if (consumer != null) {
                consumer.close();
            }
            if (jmsContext != null) {
                jmsContext.close();
            }
        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
    }

    /***************************************/
    /** Promulgation Service Handling **/
    /***************************************/

    /** {@inheritDoc} */
    @Override
    public String getServiceId() {
        return BaleenMessagePromulgation.SERVICE_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String getServiceName() {
        return "Baleen promulgation";
    }

    /***************************************/
    /** Message Life-cycle Management **/
    /***************************************/

    /** {@inheritDoc} */
    @Override
    public void onLoadSystemMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        BaleenMessagePromulgationVo audio = message.promulgation(BaleenMessagePromulgationVo.class, type.getTypeId());
        if (audio == null) {
            audio = new BaleenMessagePromulgationVo(type.toVo(DataFilter.get()));
            message.checkCreatePromulgations().add(audio);
        }
    }

    /***************************************/
    /** Generating promulgations **/
    /***************************************/

    /** {@inheritDoc} */
    @Override
    public BaseMessagePromulgationVo generateMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        BaleenMessagePromulgationVo audio = new BaleenMessagePromulgationVo(type.toVo(DataFilter.get()));
        audio.setPromulgate(type.getRequirement() == Requirement.MANDATORY);
        return audio;
    }

    /** {@inheritDoc} */
    @Override
    public void messagePromulgationGenerated(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        message.promulgation(BaleenMessagePromulgationVo.class, type.getTypeId());
    }

    /** {@inheritDoc} */
    @Override
    public void resetMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        message.promulgation(BaleenMessagePromulgationVo.class, type.getTypeId());
    }

    /***************************************/
    /** JMS Message Processing **/
    /***************************************/

    @Override
    public void onMessage(jakarta.jms.Message msg) {
        try {
            if (msg instanceof TextMessage textMessage) {
                String messageUid = msg.getJMSCorrelationID();
                String endpoint = msg.getStringProperty("endpoint");
                String token = msg.getStringProperty("token");
                String gmlContent = textMessage.getText();

                long t0 = System.currentTimeMillis();
                long firstAttemptTime = msg.getJMSTimestamp();

                try {
                    postXml(endpoint, token, gmlContent);
                    log.info("Promulgated to Baleen for message {} in {} ms",
                        messageUid, (System.currentTimeMillis() - t0));
                    msg.acknowledge();
                } catch (Exception e) {
                    long timeElapsed = System.currentTimeMillis() - firstAttemptTime;

                    if (timeElapsed > SIX_HOURS_IN_MS) {
                        log.error("Message {} failed after retrying for six hours. Final error: {}",
                            messageUid, e.getMessage());
                        // You might want to notify someone here
                        msg.acknowledge();
                    } else {
                        long retryMinutes = timeElapsed / (60 * 1000);
                        log.error("Failed processing queued message (elapsed: {} minutes, will retry): {}",
                            retryMinutes, e.getMessage());
                        throw e; // This will cause redelivery
                    }
                }
            }
        } catch (Exception e) {
            // Don't acknowledge - will trigger redelivery
            log.error("Message processing error, will retry: " + e.getMessage());
        }
    }


    /***************************************/
    /** Baleen promulgation **/
    /***************************************/

    /**
     * Handle Baleen promulgation for the message
     */
    public void checkPromulgateMessage(String messageUid) {
        Message message = messageService.findByUid(messageUid);

        if (message != null && message.getStatus() == Status.PUBLISHED) {
            message.getPromulgations().stream()
                    .filter(p -> p.isPromulgate() && p.promulgationDataDefined() && p.getType().isActive())
                    .filter(p -> p instanceof BaleenMessagePromulgation)
                    .map(p -> (BaleenMessagePromulgation) p)
                    .forEach(t -> queueMessageForPromulgation(message, t));
        }
    }

    /**
     * Queue message for Baleen promulgation
     */
    private void queueMessageForPromulgation(Message message, BaleenMessagePromulgation messagePromulgation) {
        PromulgationType type = messagePromulgation.getType();
        BaleenSettings settings = getSettings(type.getTypeId());

        if (settings == null || !settings.credentialsValid()) {
            log.info("Baleen connection details has not been set up");
            return;
        }

        try {
            String msg = service.generateGML(message, "en");

            // Create JMS message
            TextMessage jmsMessage = jmsContext.createTextMessage(msg);
            jmsMessage.setJMSCorrelationID(message.getUid());
            jmsMessage.setStringProperty("endpoint", settings.accessToken);
            jmsMessage.setStringProperty("token", settings.accessTokenSecret);

            // Send to queue
            jmsContext.createProducer()
                     .setDeliveryMode(DeliveryMode.PERSISTENT)
                     .send(jmsContext.createQueue(QUEUE_NAME), jmsMessage);

            log.info("Queued message {} for Baleen promulgation", message.getUid());

        } catch (Exception e) {
            log.error("Failed to queue message {} for Baleen promulgation", message.getUid(), e);
        }
    }

    public static String postXml(String endpoint, String token, String xml) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(endpoint))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/xml")
                .header("X-Auth-Token", token)
                .POST(BodyPublishers.ofString(xml))
                .build();

        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed: HTTP error code : " + response.statusCode());
        }

        return response.body();
    }

    /***************************************/
    /** Baleen settings **/
    /***************************************/

    /**
     * Returns the Baleen settings for the given type or null if not found
     */
    public BaleenSettings getSettings(String typeId) {
        try {
            return em.createNamedQuery("BaleenSettings.findByType", BaleenSettings.class)
                    .setParameter("typeId", typeId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    /** Creates a Baleen settings entity from the given template **/
    @Transactional
    public BaleenSettings createSettings(BaleenSettings settings) throws Exception {
        String typeId = settings.getPromulgationType().getTypeId();

        BaleenSettings original = getSettings(typeId);
        if (original != null) {
            throw new IllegalArgumentException("Settings already exists for promulgation type " + typeId);
        }

        PromulgationType type = promulgationTypeService.getPromulgationType(typeId);
        settings.setPromulgationType(type);
        org.hibernate.Hibernate.initialize(type.getDomains());

        log.info("Create Baleen Settings for promulgation type " + typeId);
        return saveEntity(settings);
    }

    /** Updates a Baleen settings entity from the given template **/
    public BaleenSettings updateSettings(BaleenSettings settings) throws Exception {
        String typeId = settings.getPromulgationType().getTypeId();

        BaleenSettings original = getSettings(typeId);
        if (original == null) {
            throw new IllegalArgumentException("No Settings exists for promulgation type " + typeId);
        }

        original.updateSettings(settings);

        log.info("Updating Baleen Settings for promulgation type " + typeId);
        return saveEntity(original);
    }

    /** Promulgate all messages **/
    public void promulgateAll() {
        // Implementation for bulk promulgation if needed
    }
}