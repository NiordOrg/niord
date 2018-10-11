/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.s124;


import _int.iho.s124.gml.cs0._0.DatasetType;
import org.apache.commons.lang.StringUtils;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Service for converting Niord navigational warnings to S-124 GML
 * <p>
 * NB: Currently the service is at a proof-of-concept stage, what with S-124 still under development.
 * <p>
 * The S-124 XSD area based on the format used by the STM-project at
 * http://stmvalidation.eu/schemas/ ("Area Exchange Format")
 *
 * TODO: When a more mature version has been implemented, the Freemarker template execution should
 *       use the {@code FmTemplateService} for DB-backed template execution.
 */
@Stateless
public class S124Service {

    private Logger log;
    private MessageService messageService;
    private S124ModelToGmlConverter modelToGmlConverter;
    private S124GmlValidator s124GmlValidator;

    @SuppressWarnings("unused")
    public S124Service() {}

    @Inject
    public S124Service(Logger log, MessageService messageService, S124ModelToGmlConverter modelToGmlConverter, S124GmlValidator s124GmlValidator) {
        this.log = log;
        this.messageService = messageService;
        this.modelToGmlConverter = modelToGmlConverter;
        this.s124GmlValidator = s124GmlValidator;
    }

    /**
     * Generates S-124 compliant GML for the message
     * @param messageId the message
     * @param language the language
     * @return the generated GML
     */
    public List<String> generateGML(String messageId, String language) {
        List<Message> messages;

        if (isBlank(messageId))
            messages = findMostRecentPublishedMessages();
        else
            messages = asList(messageService.resolveMessage(messageId));

        log.debug("Found {} messages", messages.size());

        List<String> gmls = new ArrayList<>(messages.size());

        messages.forEach(message -> {
            try {
                JAXBElement<DatasetType> dataSet = modelToGmlConverter.toGml(message, language);
                validateAgainstSchema(dataSet);
                String gml = modelToGmlConverter.toString(dataSet);
                gmls.add(gml);
            } catch(RuntimeException e) {
                log.error(e.getMessage());
            }
        });

        return gmls;
    }

    private void validateAgainstSchema(JAXBElement<DatasetType> dataSet) {
        try {
            List<S124GmlValidator.ValidationError> validationErrors = s124GmlValidator.validateAgainstSchema(dataSet);
            validationErrors.forEach(err -> log.warn(String.format("%8s [%d:%d]: %s", err.getType(), err.getLineNumber(), err.getColumnNumber(), err.getMessage())));
        } catch (JAXBException e) {
            log.error(e.getMessage(), e);
        }
    }

    private List<Message> findMostRecentPublishedMessages() {
        List<Message> messages;
        MessageSearchParams params = new MessageSearchParams()
                .statuses(Status.PUBLISHED);
        params.maxSize(100);

        messages = messageService.search(params)
                .getData().stream()
                .filter(m -> StringUtils.isNotBlank(m.getLegacyId()))
                .collect(Collectors.toList());
        return messages;
    }

}