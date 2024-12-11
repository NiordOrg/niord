/*
 * Copyright (c) 2008 Kasper Nielsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.niord.s124madame.promulgation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.niord.core.dictionary.DictionaryService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.BasePromulgationService;
import org.niord.core.promulgation.PromulgationException;
import org.niord.core.promulgation.PromulgationType;
import org.niord.core.promulgation.PromulgationType.Requirement;
import org.niord.core.promulgation.PromulgationTypeService;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.model.DataFilter;
import org.niord.model.message.Status;
import org.niord.s124madame.S124Service;
import org.niord.s124madame.promulgation.vo.BaleenMessagePromulgationVo;

import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 *
 */
@ApplicationScoped
@Lock(LockType.READ)
public class BaleenPromulgationServiceOld extends BasePromulgationService {

    @Inject
    DictionaryService dictionaryService;

    @Inject
    PromulgationTypeService promulgationTypeService;

    @Inject
    MessageService messageService;

    @Inject
    S124Service service;

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
        BaleenMessagePromulgationVo audio = message.promulgation(BaleenMessagePromulgationVo.class, type.getTypeId());

    }

    /** {@inheritDoc} */
    @Override
    public void resetMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        BaleenMessagePromulgationVo audio = message.promulgation(BaleenMessagePromulgationVo.class, type.getTypeId());
        if (audio != null) {

        }
    }

    /***************************************/
    /** Baleen promulgation              **/
    /***************************************/


    /**
     * Handle Baleen promulgation for the message
     * @param messageUid the UID of the message
     */
    public void checkPromulgateMessage(String messageUid) {
        Message message = messageService.findByUid(messageUid);

        if (message != null && message.getStatus() == Status.PUBLISHED) {
            message.getPromulgations().stream()
                    .filter(p -> p.isPromulgate() && p.promulgationDataDefined() && p.getType().isActive())
                    .filter(p -> p instanceof BaleenMessagePromulgation)
                    .map(p -> (BaleenMessagePromulgation) p)
                    .forEach(t -> promulgateMessage(message, t));
        }

    }


    /**
     * Handle Baleen promulgation for the message
     * @param message the message
     * @param messagePromulgation the Baleen message promulgation
     */
    private void promulgateMessage(Message message, BaleenMessagePromulgation messagePromulgation) {

        long t0 = System.currentTimeMillis();

        PromulgationType type = messagePromulgation.getType();
        BaleenSettings settings = getSettings(type.getTypeId());

        // Check that the settings are valid
        if (settings == null || !settings.credentialsValid()) {
            log.info("Baleen connection details has not been set up");
            return;
        }

        String msg;
        try {
            msg = service.generateGML(message, "en");
        } catch (Exception e) {
            log.error("Failed to generate S-124 for message " + message.getUid(), e);
            return;
        }

        BaleenSettings bs = getSettings("baleen");
        System.out.println(bs.accessToken);

        System.out.println(bs.accessTokenSecret);
        try {
            System.out.println("Sending message to Baleen\n" + msg);
            postXml(bs.accessToken, bs.accessTokenSecret, msg);
            log.info("Promulgated to Baleen for message " + message.getUid() +
                     " in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Exception e) {
            log.warn("Failed promulgating to Baleen for message " + message.getUid(), e);
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
     *
     * @param typeId
     *            the promulgation type
     * @return the Baleen settings for the given type or null if not found
     */
    public BaleenSettings getSettings(String typeId) {
        try {
            return em.createNamedQuery("BaleenSettings.findByType", BaleenSettings.class).setParameter("typeId", typeId).getSingleResult();
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

    /**
     *
     */
    public void promulgateAll() {
    }
}
