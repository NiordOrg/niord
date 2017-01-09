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

package org.niord.core.publication;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageScriptFilterService;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.core.repo.RepositoryService;
import org.niord.core.service.BaseService;
import org.niord.core.util.TimeUtils;
import org.niord.model.message.Status;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.publication.vo.PublicationMainType.PUBLICATION;
import static org.niord.core.publication.vo.PublicationMainType.TEMPLATE;
import static org.niord.core.publication.vo.PublicationStatus.*;
import static org.niord.model.message.Status.PUBLISHED;
import static org.niord.model.publication.PublicationType.*;

/**
 * Business interface for accessing publications
 */
@Stateless
@SuppressWarnings("unused")
public class PublicationService extends BaseService {

    private static final String PHASE_START_RECORDING       = "start-recording";
    private static final String PHASE_MESSAGE_STATUS_CHANGE = "msg-status-change";

    /**
     * The message tag filter is only ever evaluated for messages that is PUBLISHED or was PUBLISHED (status changes),
     * hence, the filter below will add all messages that gets published after recording starts, but not remove
     * them again.
     */
    private static final String DEFAULT_MESSAGE_TAG_FILTER
            = "data.phase == 'msg-status-change' && (msg.status == Status.PUBLISHED || data.isIncluded)";


    @Inject
    private Logger log;

    @Inject
    PublicationCategoryService publicationCategoryService;

    @Inject
    DomainService domainService;

    @Inject
    MessageService messageService;

    @Inject
    MessageTagService messageTagService;

    @Inject
    RepositoryService repositoryService;

    @Inject
    MessageScriptFilterService messageScriptFilterService;


    /**
     * Returns the publication with the given publication ID
     *
     * @param publicationId the publication ID
     * @return the publication with the given publication ID or null if not found
     */
    public Publication findByPublicationId(String publicationId) {
        try {
            return em.createNamedQuery("Publication.findByPublicationId", Publication.class)
                    .setParameter("publicationId", publicationId)
                    .getSingleResult();
        } catch (Exception ignored) {
        }
        return null;
    }


    /**
     * Returns the publications with the given database IDs
     *
     * @param ids the database IDs
     * @return the publications with the database IDs
     */
    public List<Publication> findByIds(List<Integer> ids) {

        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<Publication> publications = em.createNamedQuery("Publication.findByIds", Publication.class)
                .setParameter("ids", ids)
                .getResultList();

        // Sort the result according to the order of the publications in the ID list
        publications.sort(Comparator.comparingInt(m -> ids.indexOf(m.getId())));

        return publications;
    }


    /**
     * Returns the publications with the database IDs
     *
     * @param publicationIds the database IDs
     * @return the publications with the IDs
     */
    public List<Publication> findByPublicationIds(String... publicationIds) {

        if (publicationIds == null || publicationIds.length == 0) {
            return Collections.emptyList();
        }

        Set<String> idSet = new HashSet<>(Arrays.asList(publicationIds));
        return em.createNamedQuery("Publication.findByPublicationIds", Publication.class)
                .setParameter("publicationIds", idSet)
                .getResultList();
    }


    /**
     * Returns the publications with the given template
     *
     * @param templateId the template publication ID
     * @return the publications with the given template
     */
    public List<Publication> findByTemplateId(String templateId) {
        return em.createNamedQuery("Publication.findByTemplateId", Publication.class)
                .setParameter("templateId", templateId)
                .getResultList();
    }


    /**
     * Returns the tag IDs for the message-report publications with the given publication IDs
     *
     * @param publicationIds the publication IDs
     * @return the tag IDs for the message-report publications with the given publication IDs
     */
    public Set<String> findTagsByPublicationIds(Set<String> publicationIds) {
        if (publicationIds == null || publicationIds.isEmpty()) {
            return Collections.emptySet();
        }
        return em.createNamedQuery("Publication.findTagsByPublicationIds", String.class)
                .setParameter("publicationIds", publicationIds)
                .getResultList().stream()
                .collect(Collectors.toSet());
    }


    /**
     * Returns the message-recording publications
     *
     * @param series the message series to find recording publications fro
     * @return the message-recording publications
     */
    public List<Publication> findRecordingPublications(MessageSeries series) {
        return em.createNamedQuery("Publication.findRecordingPublications", Publication.class)
                .setParameter("series", series)
                .getResultList();
    }


    /**
     * Searches for publications matching the given search parameters
     *
     * @param params the search parameters
     * @return the search result
     */
    public PagedSearchResultVo<Publication> searchPublications(PublicationSearchParams params) {

        PagedSearchResultVo<Publication> result = new PagedSearchResultVo<>();

        try {

            List<Integer> pagedPublicationIds = searchPagedPublicationIds(params, result);

            // Fetch the publications
            List<Publication> publications = findByIds(pagedPublicationIds);
            result.setData(publications);
            result.updateSize();

        } catch (Exception e) {
            log.error("Error performing search " + params + ": " + e, e);
        }

        return result;
    }


    /**
     * Searches for a page of publication IDs matching the given search parameters
     * Will also update the search result with the total number of matches
     *
     * @param params the search parameters
     * @param result the result structure to update with the total number of matches
     * @return a page of publication IDs matching the given search parameters
     */
    @SuppressWarnings("all")
    public List<Integer> searchPagedPublicationIds(
            PublicationSearchParams params,
            PagedSearchResultVo<Publication> result) {


        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = cb.createTupleQuery();

        Root<Publication> publicationRoot = tupleQuery.from(Publication.class);

        // Build the predicate
        CriteriaHelper<Tuple> criteriaHelper = CriteriaHelper.initWithTupleQuery(em);

        // Match the main type
        criteriaHelper.equals(publicationRoot.get("mainType"), params.getMainType());

        // Match the file type
        criteriaHelper.equals(publicationRoot.get("type"), params.getType());

        // Match the status
        if (params.getStatuses() != null && !params.getStatuses().isEmpty()) {
            criteriaHelper.in(publicationRoot.get("status"), params.getStatuses());
        }

        // Match the title
        if (StringUtils.isNotBlank(params.getTitle())) {
            Join<Publication, PublicationDesc> descs = publicationRoot.join("descs", JoinType.LEFT);
            criteriaHelper.like(descs.get("title"), params.getTitle());

            // Optionally, match the language
            if (StringUtils.isNotBlank(params.getLanguage())) {
                criteriaHelper.equals(descs.get("lang"), params.getLanguage());
            }
        }

        // Match the category
        Join<Publication, PublicationCategory> typeJoin = publicationRoot.join("category", JoinType.LEFT);
        if (StringUtils.isNotBlank(params.getCategory())) {
            criteriaHelper.equals(typeJoin.get("categoryId"), params.getCategory());
        }

        // Match the publish flag of the category
        if (params.getPublished() != null) {
            criteriaHelper.equals(typeJoin.get("publish"), params.getPublished());
        }

        // Match the domain
        if (StringUtils.isNotBlank(params.getDomain())) {
            Join<Publication, Domain> domainJoin = publicationRoot.join("domain", JoinType.LEFT);
            criteriaHelper.add(cb.or(
                    cb.isNull(publicationRoot.get("domain")),
                    cb.equal(domainJoin.get("domainId"), params.getDomain()))
            );
        }

        // Match the message publication category
        criteriaHelper.equals(publicationRoot.get("messagePublication"), params.getMessagePublication());

        // Filter by dates
        if (params.getFrom() != null || params.getTo() != null) {
            Date from = params.getFrom(); //TimeUtils.resetTime(params.getFrom());
            Date to = params.getTo(); //TimeUtils.endOfDay(params.getTo());
            criteriaHelper.overlaps(publicationRoot.get("publishDateFrom"), publicationRoot.get("publishDateTo"), from, to);
        }

        // Compute the sort order
        List<Order> sortOrders = new ArrayList<>();
        sortOrders.add(cb.asc(typeJoin.get("priority")));
        sortOrders.add(cb.desc(publicationRoot.get("publishDateFrom")));

        // Select ID field + fields used for sorting
        List<Selection<?>> fields = new ArrayList<>();
        fields.add(publicationRoot.get("id"));
        fields.add(typeJoin.get("priority"));
        fields.add(publicationRoot.get("publishDateFrom"));
        Selection[] f = fields.toArray(new Selection<?>[fields.size()]);

        // Complete the query
        tupleQuery.multiselect(f)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(sortOrders);


        // Execute the query
        List<Tuple> totalResult = em
                .createQuery(tupleQuery)
                .getResultList();

        // Register the total result
        result.setTotal(totalResult.size());

        List<Integer> publicationIds = totalResult.stream()
                .map(t -> (Integer) t.get(0))
                .collect(Collectors.toList());

        // Extract and return the paged sub-list
        int startIndex = Math.min(publicationIds.size(), params.getPage() * params.getMaxSize());
        int endIndex = Math.min(publicationIds.size(), startIndex + params.getMaxSize());
        return publicationIds.subList(startIndex, endIndex);
    }


    /**
     * Returns the list of publications
     * @return the list of publications
     */
    public List<Publication> getPublications() {
        return getAll(Publication.class);
    }


    /**
     * Creates a new template publication
     * @param mainType the main type of publication
     * @return a new template publication
     */
    public Publication newTemplatePublication(PublicationMainType mainType) {
        Publication publication = new Publication();
        publication.setMainType(mainType);
        publication.assignNewPublicationId();
        publication.setType(LINK);
        publication.setLanguageSpecific(true);
        publication.setEdition(Publication.DEFAULT_EDITION);
        return publication;
    }


    /**
     * Saves the publication and returns the persisted publication
     * @param publication the publication to save
     * @return the persisted publication
     */
    private Publication savePublication(Publication publication) {

        // Update the publication ID and repoPath
        publication.checkPublicationId();

        return saveEntity(publication);
    }


    /**
     * Updates the publication data from the publication template
     * @param publication the publication to update
     * @return the updated publication
     */
    public Publication updatePublication(Publication publication) {
        Publication original = findByPublicationId(publication.getPublicationId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing publication "
                    + publication.getPublicationId());
        }

        // Substitute related entities with the persisted ones
        updateRelatedEntities(publication);

        // Copy the publication data
        original.updatePublication(publication);

        // If this is a template, update all publications based on this template
        if (original.getMainType() == TEMPLATE) {
            findByTemplateId(original.getPublicationId()).forEach(this::updateFromTemplate);
        } else if (original.getTemplate() != null) {
            // Let the template override any changes
            updateFromTemplate(original);
        }

        return savePublication(original);
    }


    /**
     * Creates a new publication based on the publication template
     * @param publication the publication to create
     * @return the created publication
     */
    public Publication createPublication(Publication publication) {
        if (!publication.isNew()) {
            throw new IllegalArgumentException("Cannot create publication with existing ID "
                    + publication.getId());
        }

        // Substitute related entities with the persisted ones
        updateRelatedEntities(publication);

        // If the publication has an associated template, update from the template
        if (publication.getMainType() == PUBLICATION && publication.getTemplate() != null) {
            updateFromTemplate(publication);
        }

        return savePublication(publication);
    }


    /** Substitute related entities with the persisted ones **/
    private void updateRelatedEntities(Publication publication) {
        // Substitute the template with the persisted on
        if (publication.getTemplate() != null) {
            publication.setTemplate(findByPublicationId(publication.getTemplate().getPublicationId()));
        }

        // Substitute the publication category with the persisted on
        if (publication.getCategory() != null) {
            publication.setCategory(publicationCategoryService.findByCategoryId(publication.getCategory().getCategoryId()));
        }

        // Substitute the domain with the persisted on
        if (publication.getDomain() != null) {
            publication.setDomain(domainService.findByDomainId(publication.getDomain().getDomainId()));
        }

        // Substitute the message tag with the persisted on
        if (publication.getMessageTag() != null) {
            publication.setMessageTag(messageTagService.findTag(publication.getMessageTag().getTagId()));
        }
    }


    /** Updates the publication from its template */
    private void updateFromTemplate(Publication publication) {
        if (publication.getMainType() == PUBLICATION && publication.getTemplate() != null) {
            PublicationTemplateUpdateCtx ctx = new PublicationTemplateUpdateCtx(publication, messageTagService);
            publication.updateFromTemplate(ctx);
        }
    }


    /**
     * Deletes the publication with the given ID
     * @param publicationId the id of the publication to delete
     */
    public boolean deletePublication(String publicationId) {

        Publication publication = findByPublicationId(publicationId);
        if (publication != null) {
            remove(publication);
            return true;
        }
        return false;
    }


    /**
     * Updates the status of the publication
     * @param publicationId the ID of the publication
     * @param status the new status
     * @return the update publication
     */
    public Publication updateStatus(String publicationId, PublicationStatus status) throws Exception {

        Publication pub = findByPublicationId(publicationId);
        if (pub == null) {
            throw new IllegalArgumentException("Non-existing publication " + publicationId);
        }

        // Validate that the status change is valid
        PublicationStatus curStatus = pub.getStatus();
        boolean valid = false;
        switch (status) {
            case DRAFT:
                valid = curStatus == RECORDING && pub.getMainType() == PUBLICATION && pub.getType() == MESSAGE_REPORT;
                break;
            case RECORDING:
                valid = curStatus == DRAFT && pub.getMainType() == PUBLICATION && pub.getType() == MESSAGE_REPORT
                        && pub.getMessageTag() != null;
                break;
            case ACTIVE:
                valid = curStatus != ACTIVE
                        && (pub.getMainType() == TEMPLATE || pub.getType() == NONE ||
                            pub.getDescs().stream().allMatch(d -> StringUtils.isNotBlank(d.getLink())));
                break;
            case INACTIVE:
                valid = curStatus == ACTIVE;
                break;
        }

        if (!valid) {
            throw new Exception("Invalid state transition " + curStatus + " -> " + status);
        }

        // Update the status
        pub.setStatus(status);
        log.info("Updating status for " + publicationId + ": " + curStatus + " -> " + status);

        // If the status is ACTIVE or INACTIVE, lock any associated message tag
        if ((pub.getStatus() == ACTIVE || pub.getStatus() == INACTIVE) && pub.getMessageTag() != null) {
            pub.getMessageTag().setLocked(true);
        }

        // If the status is RECORDING, check if any published messages should be assigned to the associated message tag
        if (pub.getStatus() == RECORDING) {
            startRecordingPublication(pub);
        }

        return savePublication(pub);
    }


    /**
     * When a publication enters the RECORDING status, check if any of the currently published messages
     * should be assigned to the associated message tag.
      * @param publication the publication
     */
    private void startRecordingPublication(Publication publication) {

        // Sanity checks
        if (publication.getStatus() != RECORDING || publication.getMessageTag() == null ||
                StringUtils.isBlank(publication.getMessageTagFilter())) {
            return;
        }

        Set<String> seriesIds = publication.getDomain() != null
                ? publication.getDomain().getMessageSeries().stream().map(MessageSeries::getSeriesId).collect(Collectors.toSet())
                : Collections.EMPTY_SET;

        // Look up all published messages
        MessageSearchParams params = new MessageSearchParams()
                .seriesIds(seriesIds)
                .statuses(PUBLISHED);
        List<Message> messages = messageService.search(params).getData();

        // For each message, check if it should be included in the associated message tag
        messages.forEach(m -> checkMessageForRecordingPublication(publication, m, PHASE_START_RECORDING));
    }


    /**
     * Check if the message should be assigned to the associated message tag of the recording publication
     * @param publication the publication
     * @param message the message
     * @param phase the phase
     */
    private void checkMessageForRecordingPublication(Publication publication, Message message, String phase) {

        MessageTag tag = publication.getMessageTag();
        boolean isIncluded = tag.getMessages().contains(message);

        // "data" parameter for the message tag filter function
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase);
        data.put("isIncluded", isIncluded);

        // Determine the message tag filter to test
        String messageTagFilter = StringUtils.defaultIfBlank(
                publication.getMessageTagFilter(),
                DEFAULT_MESSAGE_TAG_FILTER);

        // Check if the message should be included in the associated message tag
        boolean includeMessage = messageScriptFilterService.includeMessage(messageTagFilter, message, data);

        if (includeMessage && !isIncluded) {
            tag.getMessages().add(message);
            tag.updateMessageCount();
            log.info("Added message " + message.getUid() + " to tag: " + tag.getName());
        } else if (!includeMessage && isIncluded) {
            tag.getMessages().remove(message);
            tag.updateMessageCount();
            log.info("Removed message " + message.getUid() + " from tag: " + tag.getName());
        }
    }


    /**
     * Will update all message tags for publications in the RECORDING status
     * @param message the message to update recording publications for
     * @param prevStatus the previous status
     */
    public void updateRecordingPublications(Message message, Status prevStatus) {

        // Only process message that either enters or leaves the PUBLISHED status
        if (message.getStatus() != PUBLISHED && prevStatus != PUBLISHED) {
            return;
        }

        // Find publications that are recording messages, and check them against their filter
        for (Publication publication : findRecordingPublications(message.getMessageSeries())) {
            checkMessageForRecordingPublication(publication, message, PHASE_MESSAGE_STATUS_CHANGE);
        }
    }


    /***************************************/
    /** Repo methods                      **/
    /***************************************/

    /**
     * Creates a temporary repository folder for the given publication
     * @param publication the publication
     */
    public void createTempPublicationRepoFolder(SystemPublicationVo publication) throws IOException {
        repositoryService.createTempEditRepoFolder(publication, false);
    }

    /**
     * Update the publication repository folder from a temporary repository folder used whilst editing the publication
     * @param publication the publication
     */
    public void updatePublicationFromTempRepoFolder(SystemPublicationVo publication) throws IOException {
        if (publication.getType() == REPOSITORY || publication.getType() == MESSAGE_REPORT) {
            repositoryService.updateRepoFolderFromTempEditFolder(publication);
        }
    }
}
