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
import org.niord.core.repo.RepositoryService;
import org.niord.core.service.BaseService;
import org.niord.core.user.UserService;
import org.niord.model.DataFilter;
import org.niord.model.PagedSearchResultVo;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.geojson.Feature.WGS84_SRID;
import static org.niord.core.message.MessageSearchParams.SortOrder;

/**
 * Business interface for managing messages
 */
@Stateless
@SuppressWarnings("unused")
public class MessageService extends BaseService {

    public static String MESSAGE_REPO_FOLDER = "messages";

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


    /***************************************/
    /** Message methods                   **/
    /***************************************/


    /**
     * Returns the message with the given id
     *
     * @param id the id of the message
     * @return the message with the given id or null if not found
     */
    public Message findById(Integer id) {
        return getByPrimaryKey(Message.class, id);
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
     * Resolves the message with the given message id, which may be either a database id,
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

        } else if (StringUtils.isNumeric(messageId)) {
            return findById(Integer.valueOf(messageId));
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
        if (message.getMessageSeries() == null) {
            throw new Exception("Message series not specified");
        }
        if (message.getType() == null) {
            throw new Exception("Missing Message type");
        }
        if (message.getMainType() == null) {
            message.setMainType(message.getType().getMainType());
        }
        message.onPersist();

        // Set default status
        if (message.getStatus() == null) {
            message.setStatus(Status.DRAFT);
        }

        // Substitute the message series with the persisted on
        if (message.getMessageSeries() != null) {
            message.setMessageSeries(messageSeriesService.findBySeriesId(message.getMessageSeries().getSeriesId()));
        }

        // Substitute the Area with a persisted one
        message.setAreas(persistedList(Area.class, message.getAreas()));

        // Substitute the Categories with the persisted ones
        message.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        message.setCharts(chartService.persistedCharts(message.getCharts()));

        // Update title lines
        updateMessageTitle(message);

        // Persist the message
        message = saveMessage(message);
        log.info("Saved message " + message);

        em.flush();
        return message;
    }


    /**
     * Updates the given message
     *
     * @param message the template for the message to update
     * @return the updated message
     */
    public Message updateMessage(Message message) throws Exception {

        Message original = findById(message.getId());

        // Validate the message
        if (original == null) {
            throw new Exception("Message not an existing message");
        }

        original.setMessageSeries(null);
        if (message.getMessageSeries() != null) {
            original.setMessageSeries(messageSeriesService.findBySeriesId(message.getMessageSeries().getSeriesId()));
        }
        original.setNumber(message.getNumber());
        original.setMrn(message.getMrn());
        original.setShortId(message.getShortId());
        original.setType(message.getType());
        original.setMainType(message.getMainType());
        original.setStatus(message.getStatus());

        // Substitute the Area with a persisted one
        original.setAreas(persistedList(Area.class, message.getAreas()));

        // Substitute the Categories with the persisted ones
        original.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        original.setCharts(chartService.persistedCharts(message.getCharts()));

        original.setHorizontalDatum(message.getHorizontalDatum());
        original.setGeometry(message.getGeometry());

        original.setPublishDate(message.getPublishDate());
        original.setCancellationDate(message.getCancellationDate());

        original.setDateIntervals(message.getDateIntervals().stream()
            .map(di -> di.isNew() ? di : getByPrimaryKey(DateInterval.class, di.getId()))
            .collect(Collectors.toList()));
        original.onPersist();

        original.setReferences(message.getReferences().stream()
            .map(r -> r.isNew() ? r : getByPrimaryKey(Reference.class, r.getId()))
            .collect(Collectors.toSet()));

        original.getAtonUids().clear();
        original.getAtonUids().addAll(message.getAtonUids());

        original.setOriginalInformation(message.getOriginalInformation());

        // Copy the area data
        original.copyDescsAndRemoveBlanks(message.getDescs());

        // Update title lines
        updateMessageTitle(original);

        // Persist the message
        original = saveMessage(original);
        log.info("Updated message " + original);

        em.flush();
        return original;
    }


    /**
     * Updates the status of the given message
     *
     * @param messageId the id of the message
     * @param status    the status
     */
    public Message updateStatus(Integer messageId, Status status) {
        Message message = findById(messageId);
        Status prevStatus = message.getStatus();

        if ((prevStatus == Status.DRAFT || prevStatus == Status.VERIFIED) && status == Status.PUBLISHED) {
            messageSeriesService.updateMessageSeriesIdentifiers(message, true);
        }

        message.setStatus(status);
        message = saveMessage(message);
        return message;
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
     * Updates the title line of the message based on area and subject
     * TODO: Introduce attribute to flag if title is manually or automatically filled out
     * @param message the message to update the title line for
     */
    public void updateMessageTitle(Message message) {
        message.getDescs().forEach(desc -> {
            if (StringUtils.isBlank(desc.getTitle())) {
                StringBuilder title = new StringBuilder();
                if (!message.getAreas().isEmpty()) {
                    title.append(Area.computeAreaTitlePrefix(message.getAreas(), desc.getLang()));
                }
                if (StringUtils.isNotBlank(desc.getVicinity())) {
                    title.append(" ").append(desc.getVicinity());
                    if (!desc.getVicinity().endsWith(".")) {
                        title.append(".");
                    }
                }
                if (StringUtils.isNotBlank(desc.getSubject())) {
                    title.append(" ").append(desc.getSubject());
                    if (!desc.getSubject().endsWith(".")) {
                        title.append(".");
                    }
                }
                desc.setTitle(title.toString().trim());
            }
        });
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
     * @param id the id of the message to reorder
     * @param afterId if defined, the message must come after this message
     * @param beforeId if defined, the message must come before this message
     */
    public void changeAreaSortOrder(Integer id, Integer afterId, Integer beforeId) {
        Message m = findById(id);
        Message am = afterId == null ? null : findById(afterId);
        Message bm = beforeId == null ? null : findById(beforeId);

        if (m == null || (am == null && bm == null)) {
            throw new IllegalArgumentException("Invalid arguments id=" + id
                    + ", afterId=" + afterId + ", beforeId=" + beforeId);
        }

        if (am == null) {
            m.setAreaSortOrder(bm.getAreaSortOrder() - 1);
            log.info("Changed area sort order of " + id + ": moved before " + beforeId);
        } else if (bm == null) {
            m.setAreaSortOrder(am.getAreaSortOrder() + 1);
            log.info("Changed area sort order of " + id + ": moved after " + afterId);
        } else {
            m.setAreaSortOrder((am.getAreaSortOrder() + bm.getAreaSortOrder()) / 2.0);
            log.info("Changed area sort order of " + id + ": moved between " + afterId + " and " + beforeId);
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
                .between(msgRoot.get("startDate"), param.getFrom(), param.getTo())
                .between(msgRoot.get("updated"), param.getUpdatedFrom(), param.getUpdatedTo());


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
            List<Long> ids = messageLuceneIndex.searchIndex(param.getQuery(), param.getLanguage(), Integer.MAX_VALUE);
            criteriaHelper.in(msgRoot.get("id"), ids);
        }


        // Message series
        if (!param.getSeriesIds().isEmpty()) {
            criteriaHelper.in(msgRoot.get("messageSeries").get("seriesId"), param.getSeriesIds());
        }


        // Filter by area, join over...
        Join<Message, Area> areaRoot = null;
        if (!param.getAreaIds().isEmpty() || param.sortByArea()) {
            areaRoot = msgRoot.join("areas", JoinType.LEFT);
            Join<Message, Area> areas = areaRoot;
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


        // Determine the fields to fetch
        List<Selection<?>> fields = new ArrayList<>();
        fields.add(msgRoot.get("id"));
        if (param.sortByDate()) {
            fields.add(msgRoot.get("startDate"));
        } else if (param.sortById()) {
            fields.add(msgRoot.get("mrn"));
        } else if (param.sortByArea()) {
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
                tupleQuery.orderBy(builder.asc(msgRoot.get("startDate")), builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(builder.desc(msgRoot.get("startDate")), builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortById()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(builder.asc(msgRoot.get("mrn")), builder.asc(msgRoot.get("id")));
            } else {
                tupleQuery.orderBy(builder.desc(msgRoot.get("mrn")), builder.desc(msgRoot.get("id")));
            }
        } else if (param.sortByArea()) {
            if (param.getSortOrder() == SortOrder.ASC) {
                tupleQuery.orderBy(builder.asc(areaRoot.get("treeSortOrder")), builder.asc(msgRoot.get("areaSortOrder")));
            } else {
                tupleQuery.orderBy(builder.desc(areaRoot.get("treeSortOrder")), builder.desc(msgRoot.get("areaSortOrder")));
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
            MessageVo snapshot = message.toVo(DataFilter.get().fields(DataFilter.GEOMETRY, "Message.details"));
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
     * Returns the repository folder for the given message
     * @param id the id of the message
     * @return the associated repository folder
     */
    public java.nio.file.Path getMessageRepoFolder(Integer id) throws IOException {
        return  repositoryService.getHashedSubfolder(MESSAGE_REPO_FOLDER, String.valueOf(id), true);
    }

    /**
     * Returns the repository folder for the given message
     * @param message the message
     * @return the associated repository folder
     */
    public java.nio.file.Path getMessageRepoFolder(Message message) throws IOException {
        return  getMessageRepoFolder(message.getId());
    }

    /**
     * Returns the repository file for the given message file
     * @param message the message
     * @param name the file name
     * @return the associated repository file
     */
    public java.nio.file.Path getMessageFileRepoPath(Message message, String name) throws IOException {
        return  getMessageRepoFolder(message).resolve(name);
    }

    /**
     * Returns the repository URI for the message folder
     * @param message the message
     * @return the associated repository URI
     */
    public String getMessageFolderRepoPath(Message message) throws IOException {
        return repositoryService.getRepoPath(getMessageRepoFolder(message));
    }

    /**
     * Returns the repository URI for the given message file
     * @param message the message
     * @param name the file name
     * @return the associated repository URI
     */
    public String getMessageFileRepoUri(Message message, String name) throws IOException {
        java.nio.file.Path file = getMessageRepoFolder(message).resolve(name);
        return repositoryService.getRepoUri(file);
    }

}
