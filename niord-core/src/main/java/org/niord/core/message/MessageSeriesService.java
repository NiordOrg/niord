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
package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.vo.SystemMessageSeriesVo.NumberSequenceType;
import org.niord.core.sequence.DefaultSequence;
import org.niord.core.sequence.Sequence;
import org.niord.core.sequence.SequenceService;
import org.niord.core.service.BaseService;
import org.niord.core.util.TimeUtils;
import org.niord.model.message.Type;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.message.vo.MessageTagVo.MessageTagType.PUBLIC;

/**
 * Business interface for managing message series
 */
@Stateless
public class MessageSeriesService extends BaseService {

    public static final String NUMBER_SEQUENCE_TYPE_YEARLY = "MESSAGE_SERIES_NUMBER_%s_%d";
    public static final String NUMBER_SEQUENCE_TYPE_CONTINUOUS = "MESSAGE_SERIES_NUMBER_%s";

    @Inject
    private Logger log;

    @Inject
    SequenceService sequenceService;

    @Inject
    DomainService domainService;

    @Inject
    MessageTagService messageTagService;

    @Inject
    NiordApp app;

    /**
     * Returns the message series with the given series identifier
     * @param seriesId the series identifier
     * @return the message series with the given series identifier or null if not found
     */
    public MessageSeries findBySeriesId(String seriesId) {
        try {
            return em.createNamedQuery("MessageSeries.findBySeriesId", MessageSeries.class)
                    .setParameter("seriesId", seriesId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all message series with the given series IDs
     * @param seriesIds the series IDs of the message series to look up
     * @return the list of all message series with the given series IDs
     */
    public List<MessageSeries> findByIds(String... seriesIds) {
        Set<String> serIds = new HashSet<>(Arrays.asList(seriesIds));
        return em.createNamedQuery("MessageSeries.findBySeriesIds", MessageSeries.class)
                .setParameter("seriesIds", serIds)
                .getResultList();
    }


    /**
     * Returns a list of persisted message series based on a list of template message series
     * @param series the list of message series to look up persisted series for
     * @return the list of corresponding persisted series
     */
    public List<MessageSeries> persistedMessageSeries(List<MessageSeries> series) {
        return series.stream()
                .map(ms -> findBySeriesId(ms.getSeriesId()))
                .filter(ms -> ms != null)
                .collect(Collectors.toList());
    }


    /**
     * Returns all message series
     * @return the list of all message series
     */
    public List<MessageSeries> getAllMessageSeries() {
        return getAll(MessageSeries.class);
    }


    /**
     * Searches for message series matching the given term
     *
     * @param term the search term
     * @param domainId  if defined restrict the search to the given domain
     * @param limit the maximum number of results
     * @return the search result
     */
    public List<MessageSeries> searchMessageSeries(String term, String domainId, int limit) {

        // Optionally, filter by the domains associated with the current domain
        Domain d = (domainId != null) ? domainService.findByDomainId(domainId) : null;
        Set<String> domainSeriesIds = d == null
                ? null
                : d.getMessageSeries().stream()
                    .map(MessageSeries::getSeriesId)
                    .collect(Collectors.toSet());

        if (StringUtils.isNotBlank(term)) {
            return em
                    .createNamedQuery("MessageSeries.searchMessageSeries", MessageSeries.class)
                    .setParameter("term", "%" + term + "%")
                    .setMaxResults(limit)
                    .getResultList()
                    .stream()
                    .filter(s -> domainSeriesIds == null || domainSeriesIds.contains(s.getSeriesId()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }


    /**
     * Creates a new message series from the given template
     * @param series the new message series
     * @return the persisted message series
     */
    public MessageSeries createMessageSeries(MessageSeries series) {
        MessageSeries original = findBySeriesId(series.getSeriesId());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create message series with duplicate message series IDs"
                    + series.getSeriesId());
        }

        log.info("Creating new message series " + series.getSeriesId());
        return saveEntity(series);
    }


    /**
     * Updates an existing message series from the given template
     * @param series the message series to update
     * @return the persisted message series
     */
    public MessageSeries updateMessageSeries(MessageSeries series) {
        MessageSeries original = findBySeriesId(series.getSeriesId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update message series non-existing message series IDs"
                    + series.getSeriesId());
        }

        original.setMainType(series.getMainType());
        original.setShortFormat(series.getShortFormat());
        original.setNumberSequenceType(series.getNumberSequenceType() != null
            ? series.getNumberSequenceType() : null);
        original.getPublishTagFormats().clear();
        original.getPublishTagFormats().addAll(series.getPublishTagFormats());
        original.getEditorFields().clear();
        original.getEditorFields().addAll(series.getEditorFields());

        log.info("Updating message series " + series.getSeriesId());
        return saveEntity(original);
    }


    /**
     * Deletes the message series with the given Series ID
     * @param seriesId the message series to delete
     * @return if the message was deleted
     */
    public boolean deleteMessageSeries(String seriesId) {

        // TODO: Check if there are message with the given message series
        MessageSeries original = findBySeriesId(seriesId);
        if (original != null) {
            log.info("Removing message series " + seriesId);
            remove(original);
            return true;
        }
        return false;
    }

    /****************************/
    /** Message ID Generation  **/
    /****************************/

    /**
     * Returns the sequence associated with the given message series and year
     * @param seriesId the message series ID
     * @param year the year
     * @return the sequence associated with the given message series and year
     */
    private Sequence sequenceForSeries(String seriesId, int year, int initialValue) {

        // Validate that seriesId designates a valid messaage series
        MessageSeries messageSeries = findBySeriesId(seriesId);
        if (messageSeries == null) {
            throw new IllegalArgumentException("Invalid message series " + seriesId);
        }

        // Check that the year is "sensible"
        if (year < 1700 || year > 2100) {
            throw new IllegalArgumentException("Invalid year " + year);
        }

        String sequenceKey
                = messageSeries.getNumberSequenceType() == NumberSequenceType.CONTINUOUS
                ? String.format(NUMBER_SEQUENCE_TYPE_CONTINUOUS, seriesId)
                : String.format(NUMBER_SEQUENCE_TYPE_YEARLY, seriesId, year);

        return new DefaultSequence(sequenceKey, initialValue);
    }


    /**
     * Returns (but does not bump) the next message number for the given message series and year
     *
     * @param seriesId the message series ID
     * @param year      the year
     * @return the next series identifier number
     */
    public Integer getNextMessageNumber(String seriesId, int year) {
        Sequence sequence = sequenceForSeries(seriesId, year, 1);
        return (int) sequenceService.peekNextValue(sequence);
    }


    /**
     * Resets the next message number for the given message series and year
     *
     * @param seriesId the message series ID
     * @param year      the year
     * @param nextNumber the next number for the message series
     */
    public void setNextMessageNumber(String seriesId, int year, int nextNumber) {
        Sequence sequence = sequenceForSeries(seriesId, year, nextNumber);
        sequenceService.resetNextValue(sequence);
    }


    /**
     * Computes (but does not update) the next message number for the given message series and year
     * by looking at the numbers already assigned.
     *
     * @param seriesId the message series ID
     * @param year      the year
     * @return the computed next series identifier number
     */
    public Integer computeNextMessageNumber(String seriesId, int year) {
        MessageSeries series = findBySeriesId(seriesId);
        Date fromDate = TimeUtils.getDate(year, 0, 1);
        Date toDate = TimeUtils.endOfDay(TimeUtils.getDate(year, 11, 31));
        return em.createNamedQuery("Message.maxNumberInPeriod", Integer.class)
                .setParameter("series", series)
                .setParameter("fromDate", fromDate)
                .setParameter("toDate", toDate)
                .getSingleResult() + 1;
    }


    /**
     * Creates a new message number specific for the given message series and year
     *
     * @param seriesId the message series ID
     * @param year      the year
     * @return the new series identifier number
     */
    public Integer newMessageNumber(String seriesId, int year) {
        Sequence sequence = sequenceForSeries(seriesId, year, 1);
        return (int) sequenceService.nextValue(sequence);
    }


    /**
     * Updates the message with (optionally) a new message number and a short ID
     * specified by the associated message series
     *
     * @param message the message to (optionally) update with a new message number and short ID
     * @param assignMessageNumber whether to assign a new message number or not
     */
    public void updateMessageIdsFromMessageSeries(Message message, boolean assignMessageNumber) {

        MessageSeries messageSeries = message.getMessageSeries();
        if (messageSeries == null) {
            throw new IllegalArgumentException("Message series must be specified");
        }

        // First, update the message series number and short ID
        NumberSequenceType type = messageSeries.getNumberSequenceType();
        if (type == NumberSequenceType.YEARLY || type == NumberSequenceType.CONTINUOUS) {

            Date publishDate = message.getPublishDateFrom();

            if (publishDate == null) {
                throw new IllegalArgumentException("Publish date must be specified");
            }
            int year = TimeUtils.getCalendarField(publishDate, Calendar.YEAR);

            // If requested assign a new message number
            if (assignMessageNumber && message.getNumber() == null) {
                message.setNumber(newMessageNumber(messageSeries.getSeriesId(), year));
            }

            // Assign the short ID by resolving the short ID format of the message series
            FormatResolver formatResolver = new FormatResolver(message, app);
            message.setShortId(formatResolver.resolveFormat(messageSeries.getShortFormat()));

        } else if (type == NumberSequenceType.MANUAL) {
            // If the short ID is assigned manually, check validity
            if (StringUtils.isBlank(message.getShortId())) {
                throw new IllegalArgumentException("Message must be assigned a short ID");
            }
        }

    }


    /**
     * Assign the message to the message tags defined by the publishTagFormat list of the message series
     * @param message the message to update
     */
    public void updateMessageTagsFromMessageSeries(Message message) {

        MessageSeries messageSeries = message.getMessageSeries();
        if (messageSeries == null) {
            throw new IllegalArgumentException("Message series must be specified");
        }

        // Next, if the message has been published, update the message tag association.
        // we include cancelled and expired states in order to handle import of old messages.
        if (message.getStatus().isPublic()) {
            FormatResolver formatResolver = new FormatResolver(message, app);
            message.getMessageSeries()
                    .getPublishTagFormats().stream()
                    .map(formatResolver::resolveFormat)
                    .map(this::findOrCreatePublicMessageTag)
                    .filter(tag -> !tag.getMessages().contains(message))
                    .forEach(tag -> {
                        tag.getMessages().add(message);
                        log.debug("Added message to tag with name " + tag.getName());
                        tag.updateMessageCount();
                    });
        }
    }


    /**
     * Finds or creates a public tag with the given name
     * @param name the tag name
     * @return the tag
     */
    private MessageTag findOrCreatePublicMessageTag(String name) {
        List<MessageTag> tags = messageTagService.findTagsByTypeAndName(PUBLIC, name);
        if (tags.isEmpty()) {
            MessageTag tag = new MessageTag();
            tag.checkAssignTagId();
            tag.setName(name);
            tag.setType(PUBLIC);
            return saveEntity(tag);
        } else {
            return tags.get(0);
        }
    }


    /**
     * Utility class that will resolve a format string containing tokens such as
     * "${year}", "${number-3-digits}", etc.
     */
    public static class FormatResolver {

        private Map<String, String> params = new HashMap<>();

        /** Constructor **/
        public FormatResolver(Message message, NiordApp app) {

            Date publishDate = message.getPublishDateFrom();
            if (publishDate == null) {
                throw new IllegalArgumentException("Publish date must be specified");
            }
            int year = TimeUtils.getCalendarField(publishDate, Calendar.YEAR);
            int week = TimeUtils.getCalendarField(publishDate, Calendar.WEEK_OF_YEAR);

            params.put("${year-2-digits}", String.valueOf(year).substring(2));
            params.put("${year}", String.valueOf(year));
            params.put("${week}", String.format("%02d", week));
            params.put("${week-2-digits}", String.format("%02d", week));
            params.put("${number}", message.getNumber() == null ? "" : String.valueOf(message.getNumber()));
            params.put("${number-3-digits}", message.getNumber() == null ? "" : String.format("%03d", message.getNumber()));
            params.put("${main-type}", message.getType().getMainType().toString());
            params.put("${main-type-lower}", message.getType().getMainType().toString().toLowerCase());
            params.put("${country}", app.getCountry());
            params.put("${id}", message.getId() == null ? "" : String.valueOf(message.getId()));
            params.put("${legacy-id}", message.getLegacyId() == null ? "" : String.valueOf(message.getLegacyId()));
            params.put("${t-or-p}", getNmTOrPToken(message));
        }

        /**
         * Resolves the format by expanding tokens such as "${year}", "${number-3-digits}", etc.
         * @param format the format to expand
         * @return the result
         */
        public String resolveFormat(String format) {

            String result = format;
            if (StringUtils.isNotBlank(format)) {
                for (Map.Entry<String, String> e : params.entrySet()) {
                    result = result.replace(e.getKey(), e.getValue()).trim();
                }
            }
            return result;
        }


        /** Returns the NM T or P token to be used in the short ID for the message */
        private String getNmTOrPToken(Message message) {
            if (message.getType() == Type.TEMPORARY_NOTICE) {
                return "(T)";
            } else if (message.getType() == Type.PRELIMINARY_NOTICE) {
                return "(P)";
            }
            return "";
        }
    }
}
