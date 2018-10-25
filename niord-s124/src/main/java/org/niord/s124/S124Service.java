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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.niord.core.NiordApp;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.message.MainType;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static java.util.Collections.*;


/**
 * Service for converting Niord navigational warnings to S-124 GML
 * <p>
 * NB: Currently the service is at a proof-of-concept stage, what with S-124 still under development.
 * <p>
 * The S-124 XSD area based on the format used by the STM-project at
 * http://stmvalidation.eu/schemas/ ("Area Exchange Format")
 */
@Stateless
public class S124Service {

    private Logger log;

    private NiordApp app;

    private S124GmlValidator s124GmlValidator;
    private MessageService messageService;

    @SuppressWarnings("unused")
    public S124Service() {
    }

    @Inject
    public S124Service(Logger log, NiordApp app, S124GmlValidator s124GmlValidator, MessageService messageService) {
        this.log = log;
        this.app = app;
        this.s124GmlValidator = s124GmlValidator;
        this.messageService = messageService;
    }

    /**
     * Find messages matching the supplied parameters and generate GML XML compliant with S-124 for these.
     *
     * @param id       The message id to process.
     * @param status   Process messages with this status.
     * @param wkt      Processes messages matching this well-known-text.
     * @param language Generate GML with texts in this language.
     * @return Strings with XML GML compliant with S-124.
     */
    public List<String> generateGML(Integer id, Integer status, String wkt, String language) {
        List<Message> messages = findMessages(id, status, wkt);
        List<String> gmls = generateGML(messages, language);
        return gmls;
    }

    /**
     * Generates S-124 compliant GML for the supplies messages
     *
     * @param messages  the messages to convert to GML
     * @param _language the language
     * @return the generated GML
     */
    public List<String> generateGML(List<Message> messages, String _language) {
        final String language = app.getLanguage(_language); // Ensure we use a valid lang

        if (messages.isEmpty())
            return EMPTY_LIST;

        List<ImmutablePair<SystemMessageVo, FeatureCollectionVo[]>> valueObjects = toValueObjects(messages, language);
        List<String> gmls = toGmlStrings(valueObjects, language);

        return gmls;
    }

    private List<ImmutablePair<SystemMessageVo, FeatureCollectionVo[]>> toValueObjects(List<Message> messages, String language) {
        final Instant start = Instant.now();

        List<ImmutablePair<SystemMessageVo, FeatureCollectionVo[]>> valueObjects = new LinkedList<>();

        messages.forEach(message -> {
            // Validate the message
            if (message.getMainType() == MainType.NM)
                log.error("S-124 does not currently support Notices to Mariners T&P :-( " + message.getUid());
            else if (message.getNumber() == null)
                log.error("S-124 does not currently support un-numbered navigational warnings :-( " + message.getUid());
            else {
                SystemMessageVo msg = message.toVo(SystemMessageVo.class, Message.MESSAGE_DETAILS_FILTER);
                msg.sort(language);

                valueObjects.add(new ImmutablePair<>(msg, message.toGeoJson()));
            }
        });

        final Instant finish = Instant.now();
        log.info("Spent {} msecs converting JPA objects to value objects.", Duration.between(start, finish).toMillis());

        return valueObjects;
    }

    private List<String> toGmlStrings(List<ImmutablePair<SystemMessageVo, FeatureCollectionVo[]>> valueObjects, String language) {
        List<String> gmls = Collections.synchronizedList(new LinkedList<>());

        final AtomicLong accumulatedConversionDuration = new AtomicLong();
        final AtomicLong accumulatedValidationDuration = new AtomicLong();
        final AtomicLong accumulatedToStringDuration = new AtomicLong();

        valueObjects.parallelStream().forEach(vo -> {
            S124ModelToGmlConverter modelToGmlConverter = new S124ModelToGmlConverter();

            final SystemMessageVo messageVo = vo.left;
            final FeatureCollectionVo[] featureCollectionVos = vo.right;

            try {
                long start = System.nanoTime();
                JAXBElement<DatasetType> dataSet = modelToGmlConverter.toGml(messageVo, featureCollectionVos, language);
                accumulatedConversionDuration.addAndGet(System.nanoTime() - start);

                start = System.nanoTime();
                validateAgainstSchema(dataSet);
                accumulatedValidationDuration.addAndGet(System.nanoTime() - start);

                start = System.nanoTime();
                String gml = modelToGmlConverter.toString(dataSet);
                gmls.add(gml);
                accumulatedToStringDuration.addAndGet(System.nanoTime() - start);

                log.debug(format("Message %s (%s) included in GML output", messageVo.getShortId(), messageVo.getId()));
            } catch (RuntimeException e) {
                log.warn(format("Message %s (%s) not included in GML output", messageVo.getShortId(), messageVo.getId()));
                if (valueObjects.size() == 1) {
                    log.debug(e.getMessage(), e);
                    throw e;
                } else {
                    log.error(format("%s [%s]", e.getMessage(), e.getClass().getName()));
                }
            }
        });

        log.info("Spent a total of {} CPU-msecs converting Niord model to GML", (long) (accumulatedConversionDuration.get() / 1e6));
        log.info("Spent a total of {} CPU-msecs validating GML against S-124 XSDSchema", (long) (accumulatedValidationDuration.get() / 1e6));
        log.info("Spent a total of {} CPU-msecs converting GML to Strings", (long) (accumulatedToStringDuration.get() / 1e6));

        return gmls;
    }

    /**
     * Find messages based on id, status or wkt.
     *
     * @param id     search for messages matching this id.
     * @param status search for messages matching this status.
     * @param wkt    search for messages matching this well-known-text.
     * @return A list of matching messages
     */
    private List<Message> findMessages(Integer id, Integer status, String wkt) {
        List<Message> messages = null;

        if (id != null) {
            String messageId = format("NW-%03d-17", id);  // TODO how to map integer to DMA NW shortId format NW-015-17?

            Message message = messageService.resolveMessage(messageId);
            messages = message != null ? singletonList(message) : emptyList();
        } else if (wkt != null) {
            MessageSearchParams params = new MessageSearchParams().extent(wkt);
            if (params.getExtent() == null)
                throw new IllegalStateException(format("Could not parse WKT \"%s\"", wkt));

            messages = messageService.search(params).getData();
        }

        return messages;
    }

    /**
     * Validate JAXB DataSet element against built-in S-124 XSD's
     *
     * @param dataSet the Dataset to validate
     */
    private void validateAgainstSchema(JAXBElement<DatasetType> dataSet) {
        try {
            String id = dataSet.getValue().getId();
            List<S124GmlValidator.ValidationError> validationErrors = s124GmlValidator.validateAgainstSchema(dataSet);

            if (validationErrors.isEmpty())
                log.debug("{}: No schema validation errors found.", id);
            else
                validationErrors.forEach(err -> log.warn(format("Schema validation error: %8s: %s: %s", err.getType(), id, err.getMessage())));
        } catch (JAXBException e) {
            log.error(e.getMessage(), e);
        }
    }

}