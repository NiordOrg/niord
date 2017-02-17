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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang.StringUtils;
import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.db.SpatialIntersectsPredicate;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.FeatureService;
import org.niord.core.message.MessageSearchParams.DateType;
import org.niord.core.message.MessageSearchParams.UserType;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.BaseMessagePromulgation;
import org.niord.core.promulgation.PromulgationManager;
import org.niord.core.publication.PublicationService;
import org.niord.core.repo.RepositoryService;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.model.DataFilter;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageVo;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Topic;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.niord.core.geojson.Feature.WGS84_SRID;
import static org.niord.core.message.MessageIdMatch.MatchType.*;
import static org.niord.core.message.MessageSearchParams.CommentsType.*;
import static org.niord.core.message.vo.SystemMessageSeriesVo.NumberSequenceType.MANUAL;
import static org.niord.model.search.PagedSearchParamsVo.SortOrder;

/**
 * Business interface for managing messages
 */
@Stateless
@SuppressWarnings("unused")
public class MessageService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    JMSContext jmsContext;

    @Resource(mappedName = "java:/jms/topic/MessageStatusTopic")
    Topic messageStatusTopic;

    @Inject
    UserService userService;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    MessageTagService messageTagService;

    @Inject
    PublicationService publicationService;

    @Inject
    MessageLuceneIndex messageLuceneIndex;

    @Inject
    AreaService areaService;

    @Inject
    CategoryService categoryService;

    @Inject
    ChartService chartService;

    @Inject
    RepositoryService repositoryService;

    @Inject
    DomainService domainService;

    @Inject
    FeatureService featureService;

    @Inject
    PromulgationManager promulgationManager;


    /***************************************/
    /** Message Look-up                   **/
    /***************************************/


    /**
     * Returns the message with the given uid
     *
     * @param uid the id of the message
     * @return the message with the given id or null if not found
     */
    public Message findByUid(String uid) {
        try {
            return em.createNamedQuery("Message.findByUid", Message.class)
                    .setParameter("uid", uid)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the message with the given legacy id
     *
     * @param legacyId the id of the message
     * @return the message with the given id or null if not found
     */
    public Message findByLegacyId(String legacyId) {
        try {
            return em.createNamedQuery("Message.findByLegacyId", Message.class)
                    .setParameter("legacyId", legacyId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the messages with the given short ID.
     *
     * @param shortId the short ID of the messages
     * @return the messages with the given short ID
     */
    public List<Message> findByShortId(String shortId) {
        return em.createNamedQuery("Message.findByShortId", Message.class)
                .setParameter("shortId", shortId)
                .getResultList();
    }


    /**
     * Returns the messages with the given UID or short ID.
     *
     * @param messageId the UID or short ID of the messages
     * @return the messages with the given message ID
     */
    private List<Message> findByMessageId(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return Collections.emptyList();
        }
        return em.createNamedQuery("Message.findByMessageId", Message.class)
                .setParameter("msgId", messageId.toLowerCase())
                .getResultList();
    }


    /**
     * Resolves the "best" message with the given message id, which may be either a UID,
     * or a short ID of a message.
     * If the are multiple matching messages, priority is given to a messages of the current domain.
     *
     * @param messageId the message id to resolve
     * @return the matching message or null if none is found
     */
    public Message resolveMessage(String messageId) {
        // Sanity check
        if (StringUtils.isBlank(messageId)) {
            return null;

        }

        Message message = findByUid(messageId);
        if (message != null) {
            return message;
        }

        // Check if the message ID is a short ID
        List<Message> messages = findByShortId(messageId);
        if (!messages.isEmpty()) {
            return resolveMessage(messages);
        }

        // No joy
        return null;
    }


    /** Returns a single message from the list - preferably from the current domain */
    private Message resolveMessage(List<Message> messages) {
        // Sort messages by domain and status
        messages.sort(new MessageIdMatchComparator(domainService.currentDomain()));

        // No current domain - just return the first message
        return messages.get(0);
    }


    /**
     * Returns the list of referenced messages, optionally with the given reference type and status
     *
     * @param message the message to return referenced messages for
     * @param referenceTypes optionally, the reference types
     * @param status optionally, the status of the referenced messages
     * @return the list of referenced messages
     */
    public List<Message> getReferencedMessages(Message message, Set<ReferenceType> referenceTypes, Status status) {
        return message.getReferences().stream()
                .filter(ref -> referenceTypes == null || referenceTypes.isEmpty() || referenceTypes.contains(ref.getType()))
                .map(ref -> resolveMessage(ref.getMessageId()))
                .filter(Objects::nonNull)
                .filter(msg -> status == null || status == msg.getStatus())
                .collect(Collectors.toList());
    }


    /**
     * Returns a list of message IDs (UID or shortId) that - possibly partially - matches
     * real text.
     *
     * @param lang the language to return the title in
     * @param txt the text to match
     * @param maxGroupCount the max number of matching message IDs to return.
     * @param includeText whether to include the search text as a match
     * @param includeDeleted whether to include deleted messages in the result
     * @return the search result
     */
    public List<MessageIdMatch> searchMessageIds(String lang, String txt, int maxGroupCount, boolean includeText, boolean includeDeleted) {
        List<MessageIdMatch> result = new ArrayList<>();
        if (StringUtils.isBlank(txt)) {
            return result;
        }

        // First priority is the text itself - if requested
        if (includeText) {
            result.add(new MessageIdMatch(txt, TEXT, null));
        }

        // Check for a matching UID
        Message message = findByUid(txt);
        if (message != null) {
            result.add(new MessageIdMatch(txt, UID, message, lang));
        }

        // Search shortIds
        String searchShortIdSql = "select distinct m from Message m where lower(m.shortId) like lower(:term) ";
        if (!includeDeleted) {
            searchShortIdSql += "and m.status != 'DELETED' ";
        }
        searchShortIdSql += "order by locate(lower(:sort), lower(m.shortId)) asc, m.updated desc ";
        em.createQuery(searchShortIdSql, Message.class)
                .setParameter("term", "%" + txt + "%")
                .setParameter("sort", txt)
                .setMaxResults(maxGroupCount)
                .getResultList()
                .forEach(m -> result.add(new MessageIdMatch(m.getShortId(), SHORT_ID, m, lang)));

        return  result;
    }


    /***************************************/
    /** Message Persistence               **/
    /***************************************/


    /**
     * Saves the message
     *
     * @param message the message to save
     * @return the saved message
     */
    public Message saveMessage(Message message) {
        boolean wasPersisted = message.isPersisted();

        // Please see comment on Message.onPersist()
        message.onPersist();

        // Save the message
        message = saveEntity(message);

        // Save a MessageHistory entity for the message
        saveHistory(message);

        return message;
    }


    /**
     * Creates a new message as a draft message
     *
     * @param message the template for the message to create
     * @return the new message
     */
    public Message createMessage(Message message) throws Exception {

        // Validate the message
        if (message.isPersisted()) {
            throw new Exception("Message already persisted");
        }

        // Register who originally created the message
        message.setCreatedBy(userService.currentUser());

        // Validate various required fields
        if (message.getMessageSeries() == null) {
            throw new Exception("Message series not specified");
        }

        // Substitute the message series with the persisted on
        message.setMessageSeries(messageSeriesService.findBySeriesId(message.getMessageSeries().getSeriesId()));

        if (message.getType() != null) {
            message.setMainType(message.getType().getMainType());
        }
        if (message.getMainType() != null && message.getMainType() != message.getMessageSeries().getMainType()) {
            throw new Exception("Invalid main-type for message " + message.getMainType());
        }

        // Set default status
        if (message.getStatus() == null) {
            message.setStatus(Status.DRAFT);
        }

        // Remove empty geometries
        message.getParts().stream()
                .filter(p -> p.getGeometry() != null && p.getGeometry().getFeatures().isEmpty())
                .forEach(p -> p.setGeometry(null));

        // Substitute the Area with a persisted one
        message.setAreas(persistedList(Area.class, message.getAreas()));
        message.setAreaSortOrder(areaService.computeMessageAreaSortingOrder(message));

        // Substitute the Categories with the persisted ones
        message.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        message.setCharts(chartService.persistedCharts(message.getCharts()));

        // Let promulgation services update the message promulgation data
        promulgationManager.onCreateMessage(message);

        // Persist the message
        message = saveMessage(message);
        log.info("Saved message " + message.getUid());

        em.flush();
        return message;
    }


    /**
     * Updates the given message.
     * <p>
     * Important: this function can not be used to change the status of the message.
     * For that, call {@code MessageService.updateStatus()}
     *
     * @param message the template for the message to update
     * @return the updated message
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Message updateMessage(Message message) throws Exception {

        Message original = findByUid(message.getUid());

        // Validate the message
        if (original == null) {
            throw new Exception("Message not an existing message");
        }

        // Register who last updated he message
        original.setLastUpdatedBy(userService.currentUser());

        original.setRevision(message.getRevision());

        // Validate various required fields
        if (message.getMessageSeries() == null) {
            throw new Exception("Message series not specified");
        }

        // Check that there is no attempt to update the status
        if (message.getStatus() != original.getStatus()) {
            throw new Exception("updateMessage() cannot change status");
        }

        if (message.getType() != null) {
            message.setMainType(message.getType().getMainType());
        }

        original.setMessageSeries(messageSeriesService.findBySeriesId(message.getMessageSeries().getSeriesId()));
        original.setNumber(message.getNumber());
        original.setShortId(message.getShortId());
        original.setType(message.getType());
        original.setMainType(message.getMainType());
        original.setThumbnailPath(message.getThumbnailPath());

        if (original.getMainType() != null && original.getMainType() != original.getMessageSeries().getMainType()) {
            throw new Exception("Invalid main-type for message " + original.getMainType());
        }

        // If a verified message is updated, it will become draft again
        if (original.getStatus() == Status.VERIFIED) {
            original.setStatus(Status.DRAFT);
        }

        original.setHorizontalDatum(message.getHorizontalDatum());

        // Substitute the Area with a persisted one
        original.setAreas(persistedList(Area.class, message.getAreas()));

        // Substitute the Categories with the persisted ones
        original.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        original.setCharts(chartService.persistedCharts(message.getCharts()));

        original.setPublishDateFrom(message.getPublishDateFrom());
        original.setPublishDateTo(message.getPublishDateTo());
        original.setFollowUpDate(message.getFollowUpDate());

        original.getReferences().clear();
        message.getReferences().stream()
                .map(this::updateReference)
                .filter(Objects::nonNull)
                .forEach(original::addReference);

        original.setOriginalInformation(message.getOriginalInformation());

        original.getParts().clear();
        message.getParts().stream()
                .map(this::updateMessagePart)
                .filter(Objects::nonNull)
                .forEach(original::addPart);
        original.getParts().removeIf(part -> !part.partDefined());

        // Copy the localized description data
        original.copyDescsAndRemoveBlanks(message.getDescs());
        original.setAutoTitle(message.isAutoTitle());

        original.getAttachments().clear();
        message.getAttachments().stream()
                .map(this::updateAttachment)
                .filter(Objects::nonNull)
                .forEach(original::addAttachment);

        original.setSeparatePage(message.getSeparatePage());

        // For non-published messages, compute the area sort order based on associated area and message part geometry
        if (original.getStatus().isDraft()) {
            original.setAreaSortOrder(areaService.computeMessageAreaSortingOrder(original));
        }

        // Let promulgation services update the message promulgation data
        promulgationManager.onUpdateMessage(message);
        original.getPromulgations().clear();
        message.getPromulgations().stream()
                .map(this::updatePromulgation)
                .filter(Objects::nonNull)
                .forEach(original::addPromulgation);

        // Persist the message
        saveMessage(original);
        log.info("Updated message " + original);

        em.flush();
        return original;
    }


    /** Called upon saving a message. Updates the message part **/
    private MessagePart updateMessagePart(MessagePart part) {

        part.setGeometry(featureService.updateFeatureCollection(part.getGeometry()));

        if (part.isNew()) {
            return part;
        }
        MessagePart original = getByPrimaryKey(MessagePart.class, part.getId());
        if (original != null) {
            original.updateMessagePart(part);
        }
        return original;
    }


    /** Upon saving a message, the attachment captions may have been updated **/
    private Attachment updateAttachment(Attachment attachment) {
        if (attachment.isNew()) {
            return attachment;
        }
        Attachment original = getByPrimaryKey(Attachment.class, attachment.getId());
        if (original != null) {
            original.updateAttachment(attachment);
        }
        return original;
    }


    /** Called upon saving a message. Updates the reference **/
    private Reference updateReference(Reference reference) {
        if (reference.isNew()) {
            return reference;
        }
        Reference original = getByPrimaryKey(Reference.class, reference.getId());
        if (original != null) {
            original.updateReference(reference);
        }
        return original;
    }

    /** Called upon saving a message. Updates the date interval **/
    private DateInterval updateDateInterval(DateInterval dateInterval) {
        if (dateInterval.isNew()) {
            return dateInterval;
        }
        DateInterval original = getByPrimaryKey(DateInterval.class, dateInterval.getId());
        if (original != null) {
            original.updateDateInterval(dateInterval);
        }
        return original;
    }


    /** Called upon saving a message. Updates the promulgation **/
    private BaseMessagePromulgation updatePromulgation(BaseMessagePromulgation promulgation) {
        if (promulgation.isNew()) {
            return promulgation;
        }
        BaseMessagePromulgation original = getByPrimaryKey(promulgation.getClass(), promulgation.getId());
        if (original != null) {
            original.update(promulgation);
        }
        return original;
    }


    /**
     * Updates the status of the given message
     *
     * @param uid the UID of the message
     * @param status    the status
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Message updateStatus(String uid, Status status) throws Exception {
        Date now = new Date();
        Message message = findByUid(uid);
        Status prevStatus = message.getStatus();

        // Check that a valid status transition is requested
        if (!getValidStatusTransitions(prevStatus).contains(status)) {
            throw new Exception("Invalid status transition " + prevStatus + " -> " + status);
        }

        // Register who last updated he message
        message.setLastUpdatedBy(userService.currentUser());

        // Update the status
        message.setStatus(status);

        // When published, update dates and the message series
        if (prevStatus.isDraft() && status == Status.PUBLISHED) {

            // Update the publish date needs updating
            if (message.getPublishDateFrom() == null || message.getPublishDateFrom().after(now)) {
                message.setPublishDateFrom(now);
            }

            // If no event dates are defined, add event dates based on publish start date
            message.checkEventDateIntervalsUponPublishStart();

            // Assign a new message number and short ID
            messageSeriesService.updateMessageIdsFromMessageSeries(message, true);

        } else if (status == Status.CANCELLED || status == Status.EXPIRED) {

            // Update the publish date needs updating
            if (message.getPublishDateTo() == null || message.getPublishDateTo().before(now)) {
                message.setPublishDateTo(now);
            }

            // Update or remove open-ended event date intervals based on the publish end date.
            message.checkEventDateIntervalsUponPublishEnd();
        }

        // Add or remove the message from any message-recording publication message tags
        publicationService.updateRecordingPublications(message, prevStatus);

        // Let promulgation services update the message promulgation data
        promulgationManager.onUpdateMessageStatus(message);

        message = saveMessage(message);

        // Broadcast the status change to any listener
        sendStatusUpdate(message, prevStatus);

        return message;
    }


    /**
     * Defines the set of valid status transitions from the given status
     * @param status the status to transition from
     * @return the set of valid status transitions
     */
    private Set<Status> getValidStatusTransitions(Status status) {
        switch (status) {
            case DRAFT:
                return new HashSet<>(asList(Status.VERIFIED, Status.DELETED));
            case VERIFIED:
                return new HashSet<>(asList(Status.PUBLISHED, Status.DRAFT, Status.DELETED));
            case PUBLISHED:
                return new HashSet<>(asList(Status.EXPIRED, Status.CANCELLED));
            default:
                return Collections.emptySet();
        }
    }


    /**
     * Broadcasts a JMS message to indicate that the message status has changed
     * @param message the message
     * @param prevStatus the previous status
     */
    private void sendStatusUpdate(Message message, Status prevStatus) {
        Map<String, Object> body = new HashMap<>();
        body.put("ID", message.getId());
        body.put("UID", message.getUid());
        body.put("STATUS", message.getStatus().name());
        body.put("PREV_STATUS", prevStatus.name());
        try {
            jmsContext.createProducer().send(messageStatusTopic, body);
        } catch (Exception e) {
            log.error("Failed sending JMS: " + e, e);
        }
    }


    /**
     * Creates a new un-persisted draft message template
     * @return a new message template
     */
    public Message newTemplateMessage(MainType mainType) {

        Message message = new Message();
        message.assignNewUid();
        message.setMainType(mainType);
        message.setStatus(Status.DRAFT);
        message.setAutoTitle(true);
        if (mainType == MainType.NM) {
            message.setOriginalInformation(true);
        }

        return  message;
    }


    /**
     * Returns all messages updated after the given date
     *
     * @param date     the date
     * @param maxCount the max number of entries to return
     * @return all messages updated after the given date
     */
    public List<Message> findUpdatedMessages(Date date, int maxCount) {
        return em
                .createNamedQuery("Message.findUpdateMessages", Message.class)
                .setParameter("date", date)
                .setMaxResults(maxCount)
                .getResultList();
    }


    /**
     * Computes the auto-generated message fields for the given message template
     *
     * @param message the message template to compute auto-generated message fields for
     * @return the updated message template
     */
    public Message updateAutoMessageFields(Message message) {
        if (message.isAutoTitle()) {
            message.setAreas(persistedList(Area.class, message.getAreas()));
        }
        message.updateAutoMessageFields();
        return message;
    }


    /**
     * If the associated message series is of type MANUEL with a defined shortFormat and the message
     * has an associated number, then the shortId of the message is updated.
     * @param message the message to update shortId for
     * @return the update message
     */
    public Message checkUpdateShortId(Message message) {
        if (message.getMessageSeries() != null) {
            message.setMessageSeries(messageSeriesService.findBySeriesId(message.getMessageSeries().getSeriesId()));
            if (message.getNumber() != null &&
                    message.getMessageSeries().getNumberSequenceType() == MANUAL &&
                    StringUtils.isNotBlank(message.getMessageSeries().getShortFormat())) {
                if (message.getPublishDateFrom() == null) {
                    message.setPublishDateFrom(new Date());
                }
                messageSeriesService.updateMessageIdsFromMessageSeries(message, false);
            }
        }
        return message;
    }


    /***************************************/
    /** Message Sorting                  **/
    /***************************************/


    /**
     * Generate a sorting order for the message within its associated area.
     * The area-sort value is based on the message center latitude and longitude, and the sorting type for
     * its first associated area.
     */
    public void computeMessageAreaSortingOrder(Message message) {
        double areaSortOrder = areaService.computeMessageAreaSortingOrder(message);
        message.setAreaSortOrder(areaSortOrder);
        saveMessage(message);
    }


    /**
     * Changes the area sort order of a message to position it between two other messages
     * @param uid the UID of the message to reorder
     * @param afterUid if defined, the message must come after this message
     * @param beforeUid if defined, the message must come before this message
     */
    public void changeAreaSortOrder(String uid, String afterUid, String beforeUid) {
        Message m = findByUid(uid);
        Message am = afterUid == null ? null : findByUid(afterUid);
        Message bm = beforeUid == null ? null : findByUid(beforeUid);

        if (m == null || (am == null && bm == null)) {
            throw new IllegalArgumentException("Invalid arguments uid=" + uid
                    + ", afterUid=" + afterUid + ", beforeUid=" + beforeUid);
        }

        if (am == null) {
            m.setAreaSortOrder(bm.getAreaSortOrder() - 1);
            log.info("Changed area sort order of " + uid + ": moved before " + beforeUid);
        } else if (bm == null) {
            m.setAreaSortOrder(am.getAreaSortOrder() + 1);
            log.info("Changed area sort order of " + uid + ": moved after " + afterUid);
        } else {
            m.setAreaSortOrder((am.getAreaSortOrder() + bm.getAreaSortOrder()) / 2.0);
            log.info("Changed area sort order of " + uid + ": moved between " + afterUid + " and " + beforeUid);
        }
    }


    /***************************************/
    /** Message Searching                 **/
    /***************************************/


    /**
     * Main message search function
     * @param params the search parameters
     * @return the search result
     */
    public PagedSearchResultVo<Message> search(MessageSearchParams params) {

        PagedSearchResultVo<Message> result = new PagedSearchResultVo<>();

        try {

            List<Integer> pagedMsgIds = searchPagedMessageIds(params, result);

            // Fetch the cached messages
            List<Message> messages = getMessages(pagedMsgIds);
            result.setData(messages);
            result.updateSize();

        } catch (Exception e) {
            log.error("Error performing search " + params + ": " + e, e);
        }

        return result;
    }


    /**
     * Returns the message with the given IDs
     *
     * @param ids the message IDs
     * @return the message with the given IDs
     */
    private List<Message> getMessages(List<Integer> ids) {

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> messages = em.createNamedQuery("Message.findByIds", Message.class)
                .setParameter("ids", ids)
                .getResultList();

        // Sort the result according to the order of the messages in the ID list
        messages.sort(Comparator.comparingInt(m -> ids.indexOf(m.getId())));

        return messages;
    }


    /**
     * Searches out the ID's of the paged result set of messages defined by the search parameters.
     * Also fills out the total result count of the message search result.
     *
     * @param param the search parameters
     * @param result the search result to update with the total result count
     * @return the paged list of message ID's
     */
    @SuppressWarnings("all")
    List<Integer> searchPagedMessageIds(MessageSearchParams param, PagedSearchResultVo<Message> result) throws Exception {

        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = builder.createTupleQuery();

        // Select messages
        Root<Message> msgRoot = tupleQuery.from(Message.class);

        // Build the predicates based on the search parameters
        CriteriaHelper<Tuple> criteriaHelper = CriteriaHelper.initWithTupleQuery(em)
                .between(msgRoot.get("updated"), param.getUpdatedFrom(), param.getUpdatedTo());


        // Filter by dates
        if (param.getFrom() != null || param.getTo() != null) {
            DateType dateType = param.getDateType() != null
                    ? param.getDateType()
                    : DateType.PUBLISH_DATE;
            Date from = param.getFrom();
            Date to = param.getTo();
            switch (dateType) {
                case PUBLISH_DATE:
                    criteriaHelper.overlaps(msgRoot.get("publishDateFrom"), msgRoot.get("publishDateTo"), from, to);
                    break;
                case EVENT_DATE:
                    criteriaHelper.overlaps(msgRoot.get("eventDateFrom"), msgRoot.get("eventDateTo"), from, to);
                    break;
                case CREATED_DATE:
                    criteriaHelper.between(msgRoot.get("created"), from, to);
                    break;
                case UPDATED_DATE:
                    criteriaHelper.between(msgRoot.get("updated"), from, to);
                    break;
                case PUBLISH_FROM_DATE:
                    criteriaHelper.between(msgRoot.get("publishDateFrom"), from, to);
                    break;
                case PUBLISH_TO_DATE:
                    criteriaHelper.between(msgRoot.get("publishDateTo"), from, to);
                    break;
            }
        }

        // Main types and sub-types
        if (!param.getMainTypes().isEmpty()) {
            criteriaHelper.in(msgRoot.get("mainType"), param.getMainTypes());
        }
        if (!param.getTypes().isEmpty()) {
            criteriaHelper.in(msgRoot.get("type"), param.getTypes());
        }


        // Statuses
        if (!param.getStatuses().isEmpty()) {
            criteriaHelper.in(msgRoot.get("status"), param.getStatuses());
        }


        // Search the Lucene index for free text search
        if (param.requiresLuceneSearch()) {
            List<Long> ids = null;
            try {
                ids = messageLuceneIndex.searchIndex(param.getQuery(), param.getLanguage(), Integer.MAX_VALUE);
            } catch (Exception e) {
                log.warn("Error searching lucene index for query " + param.getQuery());
                ids = Collections.emptyList();
            }
            criteriaHelper.in(msgRoot.get("id"), ids);
        }


        // Message series
        if (!param.getSeriesIds().isEmpty()) {
            criteriaHelper.in(msgRoot.get("messageSeries").get("seriesId"), param.getSeriesIds());
        }


        // Filter by area, join over...
        if (!param.getAreaIds().isEmpty()) {
            Join<Message, Area> areas = msgRoot.join("areas", JoinType.LEFT);
            if (!param.getAreaIds().isEmpty()) {
                Predicate[] areaMatch = param.getAreaIds().stream()
                        .map(aid -> areaService.findByAreaId(aid))
                        .filter(Objects::nonNull)
                        .map(a -> builder.like(areas.get("lineage"), a.getLineage() + "%"))
                        .toArray(Predicate[]::new);
                criteriaHelper.add(builder.or(areaMatch));
            }
        }


        // Filter on categories
        if (!param.getCategoryIds().isEmpty()) {
            Join<Message, Category> categories = msgRoot.join("categories", JoinType.LEFT);
            Predicate[] categoryMatch = param.getCategoryIds().stream()
                    .map(cid -> categoryService.findByCategoryId(cid))
                    .filter(Objects::nonNull)
                    .map(c -> builder.like(categories.get("lineage"), c.getLineage() + "%"))
                    .toArray(Predicate[]::new);
            criteriaHelper.add(builder.or(categoryMatch));
        }


        // Filter on charts
        if (!param.getChartNumbers().isEmpty()) {
            Join<Message, Chart> charts = msgRoot.join("charts", JoinType.LEFT);
            Predicate[] chartMatch = param.getChartNumbers().stream()
                    .map(m -> builder.equal(charts.get("chartNumber"), m))
                    .toArray(Predicate[]::new);
            criteriaHelper.add(builder.or(chartMatch));
        }


        // Geometry
        if (param.getExtent() != null) {
            param.getExtent().setSRID(WGS84_SRID);
            Join<Message, MessagePart> partRoot = msgRoot.join("parts", JoinType.LEFT);
            Join<Message, FeatureCollection> fcRoot = partRoot.join("geometry", JoinType.LEFT);
            Join<FeatureCollection, Feature> fRoot = fcRoot.join("features", JoinType.LEFT);
            Predicate geomPredicate = new SpatialIntersectsPredicate(
                    criteriaHelper.getCriteriaBuilder(),
                    fRoot.get("geometry"),
                    param.getExtent());

            if (param.getIncludeNoPos() != null && param.getIncludeNoPos().booleanValue()) {
                // search for message with no geometry in addition to messages within extent
                criteriaHelper.add(builder.or(builder.equal(msgRoot.get("hasGeometry"), false), geomPredicate));
            } else {
                // Only search for messages within extent
                criteriaHelper.add(geomPredicate);
            }
        }


        // Tags
        if (!param.getTags().isEmpty()) {
            Join<Message, MessageTag> tags = msgRoot.join("tags", JoinType.LEFT);
            String[] tagIds = param.getTags().toArray(new String[param.getTags().size()]);
            Predicate[] tagMatch = messageTagService.findTags(tagIds).stream()
                    .map(t -> builder.equal(tags.get("id"), t.getId()))
                    .toArray(Predicate[]::new);
            criteriaHelper.add(builder.or(tagMatch));
        }


        // User
        if (StringUtils.isNotBlank(param.getUsername())) {
            UserType userType = param.getUserType() == null ? UserType.UPDATED_BY : param.getUserType();
            if (userType == UserType.CREATED_BY || userType == UserType.LAST_UPDATED_BY) {
                String joinCol = userType == UserType.CREATED_BY ? "createdBy" : "lastUpdatedBy";
                Join<Message, User> userRoot = msgRoot.join(joinCol, JoinType.LEFT);
                criteriaHelper.equals(userRoot.get("username"), param.getUsername());
            } else {
                Join<Message, MessageHistory> historyRoot = msgRoot.join("history", JoinType.LEFT);
                Join<MessageHistory, User> userRoot = historyRoot.join("user", JoinType.LEFT);
                criteriaHelper.equals(userRoot.get("username"), param.getUsername());
            }
        }


        // Comments
        if (param.getCommentsType() != null) {
            Join<Message, Comment> comments = msgRoot.join("comments", JoinType.LEFT);
            User user = userService.currentUser();
            Predicate own = user != null ? builder.equal(comments.get("user"), user) : null;
            Predicate exists = builder.isNotNull(comments.get("id"));
            Predicate unack = builder.isNull(comments.get("acknowledgedBy"));

            if (user != null && param.getCommentsType() == OWN) {
                criteriaHelper.add(own);
            } else if (user != null && param.getCommentsType() == OWN_UNACK) {
                criteriaHelper.add(builder.and(own, unack));
            } else if (param.getCommentsType() == ANY_UNACK) {
                criteriaHelper.add(builder.and(exists, unack));
            } else if (param.getCommentsType() == ANY) {
                criteriaHelper.add(exists);
            }
        }


        // Refenced messages
        if (StringUtils.isNotBlank(param.getMessageId())) {
            int levels = param.getReferenceLevels() == null ? 1 : param.getReferenceLevels();
            // NB: This is expensive queries - limit the levels
            levels = Math.max(0, Math.min(5, levels));
            // First, find messages referenced by the message ID
            Set<Integer> referencedIds = findReferencedMessageIds(new HashSet<>(), param.getMessageId(), levels);
            // Next, add messages referencing the message ID
            findReferencingMessageIds(referencedIds, param.getMessageId(), levels);
            criteriaHelper.in(msgRoot.get("id"), referencedIds);
        }


        // Determine the fields to fetch
        Join<Message, Area> areaRoot = null;
        Expression<?> treeSortOrder = null;
        List<Selection<?>> fields = new ArrayList<>();
        fields.add(msgRoot.get("id"));
        if (param.sortByEventDate()) {
            fields.add(msgRoot.get("eventDateFrom"));
            fields.add(msgRoot.get("eventDateTo"));
        } else if (param.sortByPublishDate()) {
            fields.add(msgRoot.get("publishDateFrom"));
            fields.add(msgRoot.get("publishDateTo"));
        } else if (param.sortByFollowUpDate()) {
            fields.add(msgRoot.get("followUpDate"));
        } else if (param.sortById()) {
            fields.add(msgRoot.get("year"));
            fields.add(msgRoot.get("number"));
            fields.add(msgRoot.get("publishDateFrom"));
        } else if (param.sortByArea()) {
            areaRoot = msgRoot.join("area", JoinType.LEFT);
            // General messages (without an associated area) should be sorted last
            treeSortOrder = builder.selectCase()
                    .when(builder.isNull(areaRoot.get("treeSortOrder")), 999999)
                    .otherwise(areaRoot.get("treeSortOrder"));
            fields.add(treeSortOrder);
            fields.add(msgRoot.get("areaSortOrder"));
            fields.add(msgRoot.get("year"));
            fields.add(msgRoot.get("number"));
        }
        Selection[] f = fields.toArray(new Selection<?>[fields.size()]);

        // Complete the query and fetch the message id's (and fields used for sorting)
        tupleQuery.multiselect(f)
                .distinct(true)
                .where(criteriaHelper.where());

        // Sort the query
        if (param.sortByEventDate()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(msgRoot.get("eventDateFrom")),
                        builder.asc(msgRoot.get("eventDateTo")),
                        builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(
                        builder.desc(msgRoot.get("eventDateFrom")),
                        builder.desc(msgRoot.get("eventDateTo")),
                        builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortByPublishDate()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(msgRoot.get("publishDateFrom")),
                        builder.asc(msgRoot.get("publishDateTo")),
                        builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(
                        builder.desc(msgRoot.get("publishDateFrom")),
                        builder.desc(msgRoot.get("publishDateTo")),
                        builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortByFollowUpDate()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(msgRoot.get("followUpDate")),
                        builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(
                        builder.desc(msgRoot.get("followUpDate")),
                        builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortById()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(msgRoot.get("year")),
                        builder.asc(msgRoot.get("number")),
                        builder.asc(msgRoot.get("publishDateFrom")),
                        builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(
                        builder.desc(msgRoot.get("year")),
                        builder.desc(msgRoot.get("number")),
                        builder.desc(msgRoot.get("publishDateFrom")),
                        builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortByArea()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(treeSortOrder),
                        builder.asc(msgRoot.get("areaSortOrder")),
                        builder.asc(msgRoot.get("year")),
                        builder.asc(msgRoot.get("number")),
                        builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(
                        builder.desc(treeSortOrder),
                        builder.desc(msgRoot.get("areaSortOrder")),
                        builder.desc(msgRoot.get("year")),
                        builder.desc(msgRoot.get("number")),
                        builder.desc(msgRoot.get("id")));
            }
        }

        // Execute the query
        List<Tuple> totalResult = em
                .createQuery(tupleQuery)
                .getResultList();

        // Register the total result
        result.setTotal(totalResult.size());

        List<Integer> msgIds = totalResult.stream()
                .map(t -> (Integer) t.get(0))
                .collect(Collectors.toList());

        // Extract and return the paged sub-list
        int startIndex = Math.min(msgIds.size(), param.getPage() * param.getMaxSize());
        int endIndex = Math.min(msgIds.size(), startIndex + param.getMaxSize());
        return msgIds.subList(startIndex, endIndex);
    }


    /**
     * Resolves the IDs of all messages referenced by the given message ID within the given number of levels
     * @param result the result
     * @param messageId the message to find referenced messages for
     * @param levels the levels
     * @return the result
     */
    private Set<Integer> findReferencedMessageIds(Set<Integer> result, String messageId, int levels) {
        for (Message message : findByMessageId(messageId)) {
            result.add(message.getId());
            if (levels > 0) {
                for (Reference reference : message.getReferences()) {
                    findReferencedMessageIds(result, reference.getMessageId(), levels - 1);
                }
            }
        }
        return result;
    }


    /**
     * Resolves the IDs of all messages referencing the given message ID within the given number of levels
     * @param result the result
     * @param messageId the message to find referencing messages for
     * @param levels the levels
     * @return the result
     */
    private Set<Integer> findReferencingMessageIds(Set<Integer> result, String messageId, int levels) {
        for (Message message : findByMessageId(messageId)) {
            findReferencingMessageIds(result, message, levels);
        }
        return result;
    }


    /**
     * Resolves the IDs of all messages referencing the given message ID within the given number of levels
     * @param result the result
     * @param message the message to find referencing messages for
     * @param levels the levels
     * @return the result
     */
    private Set<Integer> findReferencingMessageIds(Set<Integer> result, Message message, int levels) {
        result.add(message.getId());
        if (levels > 0) {
            Set<String> messageIds = new HashSet<>();
            messageIds.add(message.getUid());
            if (message.getShortId() != null) {
                messageIds.add(message.getShortId().toLowerCase());
            }
            em.createNamedQuery("Message.findByReference", Message.class)
                    .setParameter("messageIds", messageIds)
                    .getResultList()
                    .forEach(msg -> findReferencingMessageIds(result, msg, levels - 1));
        }
        return result;
    }


    /**
     * From the given list of Message UIDs, return the UIDs of the message with the separatePage flag set.
     * Used when printing messages (generating PDFs)
     * @param uids the UIDs of the messages to search
     * @return the UIDs of the message with the separatePage flag set
     */
    public Set<String> getSeparatePageUids(Set<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptySet();
        }
        return em.createNamedQuery("Message.separatePageUids", String.class)
                .setParameter("uids", uids)
                .getResultList().stream()
                .collect(Collectors.toSet());
    }

    /***************************************/
    /** Message History                   **/
    /***************************************/


    /**
     * Saves a history entity containing a snapshot of the message
     *
     * @param message the message to save a snapshot for
     */
    public void saveHistory(Message message) {

        try {
            MessageHistory hist = new MessageHistory();
            hist.setMessage(message);
            hist.setUser(userService.currentUser());
            hist.setStatus(message.getStatus());
            hist.setCreated(new Date());
            hist.setVersion(message.getVersion() + 1);

            // Create a snapshot of the message
            ObjectMapper jsonMapper = new ObjectMapper();
            jsonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false); // Use ISO-8601 format
            DataFilter dataFilter = DataFilter.get()
                    .fields("Message.details", "Message.geometry", "Message.promulgations");
            MessageVo snapshot = message.toVo(SystemMessageVo.class, dataFilter);
            hist.compressSnapshot(jsonMapper.writeValueAsString(snapshot));

            saveEntity(hist);

        } catch (Exception e) {
            log.error("Error saving a history entry for message " + message.getId(), e);
            // NB: Don't propagate errors
        }
    }

    /**
     * Returns the message history for the given message ID
     *
     * @param messageId the message ID
     * @return the message history
     */
    public List<MessageHistory> getMessageHistory(Integer messageId) {
        return em.createNamedQuery("MessageHistory.findByMessageId", MessageHistory.class)
                .setParameter("messageId", messageId)
                .getResultList();
    }


    /**
     * Returns the messages most recently edited by the current user
     * within the last month
     *
     * @param maxMessageNo max number of message to return
     * @return the messages most recently edited by the current user
     */
    public List<Message> getMostRecentlyEditedMessages(int maxMessageNo, Status... statuses) {
        User user = userService.currentUser();
        Domain domain = domainService.currentDomain();
        if (user == null || domain == null || domain.getMessageSeries().isEmpty()) {
            return Collections.emptyList();
        }
        // Only check last month
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);

        Set<Status> statusSet = new HashSet<>();
        if (statuses != null && statuses.length > 0) {
            statusSet.addAll(Arrays.asList(statuses));
        } else {
            statusSet.addAll(Arrays.asList(Status.values()));
        }

        return em.createNamedQuery("MessageHistory.findRecentChangesByUser", MessageHistory.class)
                .setParameter("user", user)
                .setParameter("date", cal.getTime())
                .setParameter("messageSeries", domain.getMessageSeries())
                .setParameter("statuses", statusSet)
                .getResultList()
                .stream()
                .map(MessageHistory::getMessage)
                .distinct()
                .limit(maxMessageNo)
                .collect(Collectors.toList());
    }


    /**
     * Returns the draft messages most recently edited by the current user
     * within the last month
     *
     * @param maxMessageNo max number of draft message to return
     * @return the draft messages most recently edited by the current user
     */
    public List<Message> getMostRecentlyEditedDrafts(int maxMessageNo) {
        return getMostRecentlyEditedMessages(maxMessageNo, Status.DRAFT, Status.VERIFIED);
    }


    /***************************************/
    /** Repo methods                      **/
    /***************************************/

    /**
     * Creates a temporary repository folder for the given message
     * @param copyToTemp whether to copy all message resources to the associated temporary directory or not
     * @param message the message
     */
    public void createTempMessageRepoFolder(SystemMessageVo message, boolean copyToTemp) throws IOException {
        repositoryService.createTempEditRepoFolder(message, copyToTemp);
    }

    /**
     * Update the message repository folder from a temporary repository folder used whilst editing the message
     * @param message the message
     */
    public void updateMessageFromTempRepoFolder(SystemMessageVo message) throws IOException {
        repositoryService.updateRepoFolderFromTempEditFolder(message);
    }
}
