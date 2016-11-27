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
import org.niord.core.message.MessageTagService;
import org.niord.core.repo.RepositoryService;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

import static org.niord.core.publication.vo.PublicationMainType.PUBLICATION;
import static org.niord.core.publication.vo.PublicationMainType.TEMPLATE;

/**
 * Business interface for accessing publications
 */
@Stateless
@SuppressWarnings("unused")
public class PublicationService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    PublicationCategoryService publicationCategoryService;

    @Inject
    DomainService domainService;

    @Inject
    MessageTagService messageTagService;

    @Inject
    RepositoryService repositoryService;


    /**
     * Returns the publication with the given publication ID
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
     * Returns the publications with the given template
     * @param templateId the template publication ID
     * @return the publications with the given template
     */
    public List<Publication> findByTemplateId(String templateId) {
        return em.createNamedQuery("Publication.findByTemplateId", Publication.class)
                .setParameter("templateId", templateId)
                .getResultList();
    }


    /**
     * Searches for publications matching the given search parameters
     *
     * @param params the search parameters
     * @return the search result
     */
    @SuppressWarnings("all")
    public List<Publication> searchPublications(PublicationSearchParams params) {


        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Publication> publicationQuery = cb.createQuery(Publication.class);

        Root<Publication> publicationRoot = publicationQuery.from(Publication.class);

        // Build the predicate
        CriteriaHelper<Publication> criteriaHelper = new CriteriaHelper<>(cb, publicationQuery);

        // Match the main type
        criteriaHelper.equals(publicationRoot.get("mainType"), params.getMainType());

        // Match the file type
        criteriaHelper.equals(publicationRoot.get("type"), params.getType());

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

        // Compute the sort order
        List<Order> sortOrders = new ArrayList<>();
        sortOrders.add(cb.asc(typeJoin.get("priority")));
        sortOrders.add(cb.desc(publicationRoot.get("publishDateFrom")));

        // Complete the query
        publicationQuery.select(publicationRoot)
                .where(criteriaHelper.where())
                .orderBy(sortOrders);

        // Execute the query and update the search result
        return em.createQuery(publicationQuery)
                .setMaxResults(params.getMaxSize())
                .getResultList();
    }


    /**
     * Returns the list of publications
     * @return the list of publications
     */
    public List<Publication> getPublications() {
        return getAll(Publication.class);
    }


    /**
     * Saves the publication and returns the persisted publication
     * @param publication the publication to save
     * @return the persisted publication
     */
    private Publication savePublication(Publication publication) {

        // Update the repository path
        if (publication.getMainType() == PUBLICATION) {
            String repoPath = String.format(
                    "%s/%s/%s",
                    Publication.PUBLICATION_REPO_FOLDER,
                    publication.getCategory().getCategoryId(),
                    publication.getPublicationId());
            publication.setRepoPath(repoPath);
        }

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

        //Substitute related entities with the persisted ones
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

        //Substitute related entities with the persisted ones
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

}
