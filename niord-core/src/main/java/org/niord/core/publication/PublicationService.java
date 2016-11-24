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
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import java.util.List;

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

    /**
     * Returns the publication with the given publication ID
     * @param publication the publication ID
     * @return the publication with the given publication ID or null if not found
     */
    public Publication findByPublicationId(String publication) {
        try {
            return em.createNamedQuery("Publication.findByPublicationId", Publication.class)
                    .setParameter("publicationId", publication)
                    .getSingleResult();
        } catch (Exception ignored) {
        }
        return null;
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
        if (StringUtils.isNotBlank(params.getCategory())) {
            Join<Publication, PublicationCategory> typeJoin = publicationRoot.join("category", JoinType.LEFT);
            criteriaHelper.equals(typeJoin.get("categoryId"), params.getCategory());
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

        // Complete the query
        publicationQuery.select(publicationRoot)
                .distinct(true)
                .where(criteriaHelper.where());

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
     * Updates the publication data from the publication template
     * @param publication the publication to update
     * @return the updated publication
     */
    public Publication updatePublication(Publication publication) {
        Publication original = findByPublicationId(publication.getPublicationId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing publication "
                    + publication.getId());
        }

        // Copy the publication data
        original.updatePublication(publication);

        // Substitute the publication category with the persisted on
        original.setCategory(publicationCategoryService.findByCategoryId(original.getCategory().getCategoryId()));

        // Substitute the domain with the persisted on
        if (original.getDomain() != null) {
            original.setDomain(domainService.findByDomainId(original.getDomain().getDomainId()));
        }

        return saveEntity(original);
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

        // Substitute the publication category with the persisted on
        publication.setCategory(publicationCategoryService.findByCategoryId(publication.getCategory().getCategoryId()));

        // Substitute the domain with the persisted on
        if (publication.getDomain() != null) {
            publication.setDomain(domainService.findByDomainId(publication.getDomain().getDomainId()));
        }

        return saveEntity(publication);
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
