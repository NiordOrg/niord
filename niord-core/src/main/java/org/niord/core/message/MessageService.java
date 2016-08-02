/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
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
import org.niord.core.repo.RepositoryService;
import org.niord.core.service.BaseService;
import org.niord.core.user.UserService;
import org.niord.core.util.TimeUtils;
import org.niord.model.DataFilter;
import org.niord.model.PagedSearchResultVo;
import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.niord.core.geojson.Feature.WGS84_SRID;
import static org.niord.core.message.MessageIdMatch.MatchType.*;
import static org.niord.core.message.MessageSearchParams.SortOrder;

/**
 * Business interface for managing messages
 */
@Stateless
@SuppressWarnings("unused")
public class MessageService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    UserService userService;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    MessageTagService messageTagService;

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
     * Returns the messages with the given MRN. Really, there should only be at most one matching
     * message, but message series may not have been defined properly...
     *
     * @param mrn the MRN of the messages
     * @return the messages with the given MRN
     */
    public List<Message> findByMrn(String mrn) {
        return em.createNamedQuery("Message.findByMrn", Message.class)
                .setParameter("mrn", mrn)
                .getResultList();
    }


    /**
     * Returns the messages with the given short ID.
     *
     * @param shortId the short ID of the messages
     * @return the messages with the given MRN
     */
    public List<Message> findByShortId(String shortId) {
        return em.createNamedQuery("Message.findByShortId", Message.class)
                .setParameter("shortId", shortId)
                .getResultList();
    }


    /**
     * Returns the messages with the given UID, short ID or MRN.
     *
     * @param messageId the UID, short ID or MRN of the messages
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
     * or a short ID or an MRN of a message.
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

        // Check if the message ID is an MRN
        List<Message> messages = findByMrn(messageId);
        if (!messages.isEmpty()) {
            return resolveMessage(messages);
        }

        // Check if the message ID is a short ID
        messages = findByShortId(messageId);
        if (!messages.isEmpty()) {
            return resolveMessage(messages);
        }

        // No joy
        return null;
    }


    /** Returns a single message from the list - preferably from the current domain */
    private Message resolveMessage(List<Message> messages) {
        // Check if any of the messages belong to the current domain
        Domain currentDomain = domainService.currentDomain();
        if (currentDomain != null) {
            Set<String> currentMessageSeries = currentDomain.getMessageSeries().stream()
                    .map(MessageSeries::getSeriesId)
                    .collect(Collectors.toSet());
            return messages.stream()
                    .filter(m -> m.getMessageSeries() != null)
                    .filter(m -> currentMessageSeries.contains(m.getMessageSeries().getSeriesId()))
                    .findFirst()
                    .orElse(messages.get(0)); // No message for the current domain - return the first
        }

        // No current domain - just return the first message
        return messages.get(0);
    }


    /**
     * Returns a list of message IDs (UID, MRN or shortId) that - possibly partially - matches
     * real text.
     *
     * @param lang the language to return the title in
     * @param txt the text to match
     * @param maxGroupCount the max number of matching message IDs to return.
     * @param includeText whether to include the search text as a match
     * @return the search result
     */
    public List<MessageIdMatch> searchMessageIds(String lang, String txt, int maxGroupCount, boolean includeText) {
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
        // NB: This idiotic way of forming the query is to avoid Intellij wrongly flagging errors :-(
        String searchShortIdSql = "select distinct m from Message m where lower(m.shortId) like lower(:term) ";
        String searchShortIdOrderSql = "order by locate(lower(:sort), lower(m.shortId)) asc ";
        em.createQuery(searchShortIdSql + searchShortIdOrderSql, Message.class)
                .setParameter("term", "%" + txt + "%")
                .setParameter("sort", txt)
                .setMaxResults(maxGroupCount)
                .getResultList()
                .forEach(m -> result.add(new MessageIdMatch(m.getShortId(), SHORT_ID, m, lang)));

        // Search MRNs
        // NB: This idiotic way of forming the query is to avoid Intellij wrongly flagging errors :-(
        String searchMrnSql = "select distinct m from Message m where lower(m.mrn) like lower(:term) ";
        String searchMrnOrderSql =  "order by locate(lower(:sort), lower(m.mrn)) asc ";
        em.createQuery(searchMrnSql + searchMrnOrderSql, Message.class)
                .setParameter("term", "%" + txt + "%")
                .setParameter("sort", txt)
                .setMaxResults(maxGroupCount)
                .getResultList()
                .forEach(m -> result.add(new MessageIdMatch(m.getMrn(), MRN, m, lang)));

        return  result;
    }


    /***************************************/
    /** Message Persistence               **/
    /***************************************/


    /**
     * Saves the message and evicts the message from the cache
     *
     * @param message the message to save
     * @return the saved message
     */
    public Message saveMessage(Message message) {
        boolean wasPersisted = message.isPersisted();

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

        // Substitute the Area with a persisted one
        message.setAreas(persistedList(Area.class, message.getAreas()));
        message.setAreaSortOrder(areaService.computeMessageAreaSortingOrder(message));

        // Substitute the Categories with the persisted ones
        message.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        message.setCharts(chartService.persistedCharts(message.getCharts()));

        // Persist the message
        message = saveMessage(message);
        log.info("Saved message " + message);

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
    public Message updateMessage(Message message) throws Exception {

        Message original = findByUid(message.getUid());

        // Validate the message
        if (original == null) {
            throw new Exception("Message not an existing message");
        }

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
        original.setMrn(message.getMrn());
        original.setShortId(message.getShortId());
        original.setType(message.getType());
        original.setMainType(message.getMainType());

        if (original.getMainType() != null && original.getMainType() != original.getMessageSeries().getMainType()) {
            throw new Exception("Invalid main-type for message " + original.getMainType());
        }

        // If a verified message is updated, it will become draft again
        if (original.getStatus() == Status.VERIFIED) {
            original.setStatus(Status.DRAFT);
        }

        // Substitute the Area with a persisted one
        original.setAreas(persistedList(Area.class, message.getAreas()));
        original.setAreaSortOrder(areaService.computeMessageAreaSortingOrder(original));

        // Substitute the Categories with the persisted ones
        original.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        original.setCharts(chartService.persistedCharts(message.getCharts()));

        original.setHorizontalDatum(message.getHorizontalDatum());
        original.setGeometry(featureService.updateFeatureCollection(message.getGeometry()));

        original.setPublishDate(message.getPublishDate());
        original.setUnpublishDate(message.getUnpublishDate());

        original.getDateIntervals().clear();
        message.getDateIntervals().stream()
                .map(this::updateDateInterval)
                .filter(r -> r != null)
                .forEach(original::addDateInterval);

        original.getReferences().clear();
        message.getReferences().stream()
                .map(this::updateReference)
                .filter(r -> r != null)
                .forEach(original::addReference);

        original.getAtonUids().clear();
        original.getAtonUids().addAll(message.getAtonUids());

        original.setOriginalInformation(message.getOriginalInformation());

        // Copy the localized description data
        original.copyDescsAndRemoveBlanks(message.getDescs());
        original.setAutoTitle(message.isAutoTitle());

        original.getAttachments().clear();
        message.getAttachments().stream()
                .map(this::updateAttachment)
                .filter(a -> a != null)
                .forEach(original::addAttachment);

        // Persist the message
        saveMessage(original);
        log.info("Updated message " + original);

        em.flush();
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
        Status currentStatus = message.getStatus();

        // Check that a valid status transition is requested
        if (!getValidStatusTransitions(currentStatus).contains(status)) {
            throw new Exception("Invalid status transition " + currentStatus + " -> " + status);
        }

        // When published, update the message series
        if ((currentStatus == Status.DRAFT || currentStatus == Status.VERIFIED) && status == Status.PUBLISHED) {
            message.setPublishDate(now);
            messageSeriesService.updateMessageSeriesIdentifiers(message, true);
        } else if (status == Status.CANCELLED || status == Status.EXPIRED) {
            message.setUnpublishDate(now);
        }

        message.setStatus(status);
        message = saveMessage(message);
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
            case IMPORTED:
                return new HashSet<>(asList(Status.DRAFT, Status.DELETED));
            case PUBLISHED:
                return new HashSet<>(asList(Status.EXPIRED, Status.CANCELLED));
            default:
                return Collections.emptySet();
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
        message.setGeometry(new FeatureCollection());
        message.setAutoTitle(true);
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
     * Computes the title lines for the given message template
     *
     * @param message the message template to compute the title line for
     * @return the updated message template
     */
    public Message computeTitleLine(Message message) {
        message.setAreas(persistedList(Area.class, message.getAreas()));
        message.setAutoTitle(true);
        message.updateMessageTitle();
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

        // TODO: Caching

        List<Message> messages = em.createNamedQuery("Message.findByIds", Message.class)
                .setParameter("ids", ids)
                .getResultList();

        // Sort the result according to the order of the messages in the ID list
        messages.sort((m1, m2) -> ids.indexOf(m1.getId()) - ids.indexOf(m2.getId()));

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
            Date from = TimeUtils.resetTime(param.getFrom());
            Date to = TimeUtils.endOfDay(param.getTo());
            switch (dateType) {
                case PUBLISH_DATE:
                    criteriaHelper.overlaps(msgRoot.get("publishDate"), msgRoot.get("unpublishDate"), from, to);
                    break;
                case CREATED_DATE:
                    criteriaHelper.between(msgRoot.get("created"), from, to);
                    break;
                case ACTIVE_DATE:
                    criteriaHelper.overlaps(msgRoot.get("startDate"), msgRoot.get("endDate"), from, to);
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
                log.warn("Error searching lucene index for query " + param.getQuery() + ": " + e);
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
                Predicate[] areaMatch = areaService.getAreaDetails(param.getAreaIds()).stream()
                        .map(a -> builder.like(areas.get("lineage"), a.getLineage() + "%"))
                        .toArray(Predicate[]::new);
                criteriaHelper.add(builder.or(areaMatch));
            }
        }


        // Filter on categories
        if (!param.getCategoryIds().isEmpty()) {
            Join<Message, Category> categories = msgRoot.join("categories", JoinType.LEFT);
            Predicate[] categoryMatch = categoryService.getCategoryDetails(param.getCategoryIds()).stream()
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
            Join<Message, FeatureCollection> fcRoot = msgRoot.join("geometry", JoinType.LEFT);
            Join<FeatureCollection, Feature> fRoot = fcRoot.join("features", JoinType.LEFT);
            Predicate geomPredicate = new SpatialIntersectsPredicate(
                    criteriaHelper.getCriteriaBuilder(),
                    fRoot.get("geometry"),
                    param.getExtent());

            if (param.getIncludeGeneral() != null && param.getIncludeGeneral().booleanValue()) {
                // search for message with no geometry in addition to messages within extent
                criteriaHelper.add(builder.or(builder.isNull(msgRoot.get("geometry")), geomPredicate));
            } else {
                // Only search for messages within extent
                criteriaHelper.add(geomPredicate);
            }
        }


        // AtoN UIDs
        if (!param.getAtonUids().isEmpty()) {
            Join<Message, String> atonRoot = msgRoot.join("atonUids", JoinType.LEFT);
            criteriaHelper.add(atonRoot.in(param.getAtonUids()));
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
        Path<Area> areaRoot = null;
        List<Selection<?>> fields = new ArrayList<>();
        fields.add(msgRoot.get("id"));
        if (param.sortByDate()) {
            fields.add(msgRoot.get("startDate"));
        } else if (param.sortById()) {
            fields.add(msgRoot.get("publishDate"));
        } else if (param.sortByArea()) {
            areaRoot = msgRoot.get("area");
            fields.add(areaRoot.get("treeSortOrder"));
            fields.add(msgRoot.get("areaSortOrder"));
        }
        Selection[] f = fields.toArray(new Selection<?>[fields.size()]);

        // Complete the query and fetch the message id's (and fields used for sorting)
        tupleQuery.multiselect(f)
                .distinct(true)
                .where(criteriaHelper.where());

        // Sort the query
        if (param.sortByDate()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(msgRoot.get("startDate")),
                        builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(builder.desc(
                        msgRoot.get("startDate")),
                        builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortById()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(msgRoot.get("publishDate")),
                        builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(
                        builder.desc(msgRoot.get("publishDate")),
                        builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortByArea()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(
                        builder.asc(areaRoot.get("treeSortOrder")),
                        builder.asc(msgRoot.get("areaSortOrder")),
                        builder.desc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(
                        builder.desc(areaRoot.get("treeSortOrder")),
                        builder.desc(msgRoot.get("areaSortOrder")),
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
            if (message.getMrn() != null) {
                messageIds.add(message.getMrn().toLowerCase());
            }
            em.createNamedQuery("Message.findByReference", Message.class)
                    .setParameter("messageIds", messageIds)
                    .getResultList()
                    .forEach(msg -> findReferencingMessageIds(result, msg, levels - 1));
        }
        return result;
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
            MessageVo snapshot = message.toEditableVo(DataFilter.get().fields("Message.details", "Message.geometry"));
            hist.setSnapshot(jsonMapper.writeValueAsString(snapshot));

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


    /***************************************/
    /** Repo methods                      **/
    /***************************************/

    /**
     * Creates a temporary repository folder for the given message
     * @param message the message
     */
    public void createTempMessageRepoFolder(EditableMessageVo message) throws IOException {

        String editRepoPath = repositoryService.getNewTempDir().getPath();
        message.setEditRepoPath(editRepoPath);

        // For existing messages, copy the existing message repo to the new repository
        if (message.getId() != null) {
            java.nio.file.Path srcPath = repositoryService.getRepoRoot().resolve(message.getRepoPath());
            java.nio.file.Path dstPath = repositoryService.getRepoRoot().resolve(editRepoPath);
            if (Files.exists(srcPath)) {
                log.debug("Copy message folder " + srcPath + " to temporary message folder " + dstPath);
                FileUtils.copyDirectory(srcPath.toFile(), dstPath.toFile(), true);
            }
        }
    }

    /**
     * Update the message repository folder from a temporary repository folder used whilst editing the message
     * @param message the message
     */
    public void updateMessageFromTempRepoFolder(EditableMessageVo message) throws IOException {

        if (message.getId() != null && StringUtils.isNotBlank(message.getEditRepoPath())) {

            java.nio.file.Path srcPath = repositoryService.getRepoRoot().resolve(message.getEditRepoPath());
            java.nio.file.Path dstPath = repositoryService.getRepoRoot().resolve(message.getRepoPath());
            if (Files.exists(srcPath)) {
                log.debug("Syncing temporary message folder " + srcPath + " with message folder " + dstPath);
                FileUtils.deleteDirectory(dstPath.toFile());
                FileUtils.copyDirectory(srcPath.toFile(), dstPath.toFile(), true);
            }
        }
    }


}
