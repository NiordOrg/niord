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

import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Business interface for accessing publication categories
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class PublicationCategoryService extends BaseService {

    @Inject
    private Logger log;


    /**
     * Returns the category with the given category ID
     * @param categoryId the category ID
     * @return the category with the given category ID or null if not found
     */
    /**
     * The category with this id, or null when there is none.
     *
     * NoResultException ONLY. Catching Exception here turned every failure into
     * "no such category" -- including the auto-flush that a query triggers, so a
     * caller that had mutated an entity first got told its perfectly good category
     * did not exist. The absence of a row is an answer; anything else is a fault,
     * and a fault reported as an answer is the harder bug of the two.
     */
    public PublicationCategory findByCategoryId(String categoryId) {
        try {
            return em.createNamedQuery("PublicationCategory.findByCategoryId", PublicationCategory.class)
                    .setParameter("categoryId", categoryId)
                    .getSingleResult();
        } catch (NoResultException absent) {
            return null;
        }
    }


    /**
     * Returns the list of publication categories
     * @return the list of publication categories
     */
    public List<PublicationCategory> getPublicationCategories() {
        return em.createNamedQuery("PublicationCategory.findAll", PublicationCategory.class)
            .getResultList();
    }


    /**
     * Updates the publication category from the category template
     * @param category the publication category to update
     * @return the updated publication category
     */
    @Transactional
    public PublicationCategory updatePublicationCategory(PublicationCategory category) {
        PublicationCategory original = findByCategoryId(category.getCategoryId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing publication category "
                    + category.getId());
        }

        // Copy the publication data
        original.updatePublicationCategory(category);

        return saveEntity(original);
    }


    /**
     * Creates a new publication category based on the publication category template
     * @param category the publication category to create
     * @return the created publication category
     */
    @Transactional
    public PublicationCategory createPublicationCategory(PublicationCategory category) {
        if (!category.isNew()) {
            throw new IllegalArgumentException("Cannot create publication category with existing ID "
                    + category.getId());
        }

        return saveEntity(category);
    }


    /**
     * Finds or creates a publication category based on the publication category template
     * @param categoryTemplate the publication category to find or create
     * @return the publication category
     */
    public PublicationCategory findOrCreatePublicationCategory(PublicationCategory categoryTemplate) {
        if (categoryTemplate == null) {
            return null;
        }

        PublicationCategory category = findByCategoryId(categoryTemplate.getCategoryId());
        if (category == null) {
            category = createPublicationCategory(categoryTemplate);
        }
        return category;
    }


    /**
     * Deletes the publication category with the given ID
     * @param categoryId the id of the publication category to delete
     */
    @Transactional
    public boolean deletePublicationCategory(String categoryId) {

        PublicationCategory category = findByCategoryId(categoryId);
        if (category != null) {
            remove(category);
            return true;
        }
        return false;
    }


    // -------------------------------------------------- the admin CRUD surface

    /**
     * The categories in the order the public page shows them, bounded.
     *
     * The bound is always applied, never treated as optional: the endpoint this
     * serves is anonymous, so a missing or zero limit meaning "everything" hands
     * anybody an unbounded read of the table.
     */
    public List<PublicationCategory> listByPriority(int limit) {
        return em.createQuery(
                        "SELECT c FROM PublicationCategory c ORDER BY c.priority ASC, c.categoryId ASC",
                        PublicationCategory.class)
                .setMaxResults(limit)
                .getResultList();
    }


    /**
     * The category with this id, refusing rather than returning null.
     *
     * The counterpart to findByCategoryId, for the callers that cannot proceed
     * without one. They would otherwise each turn the null into their own
     * refusal, and a not-found reported four different ways is four things a
     * client has to recognise.
     */
    public PublicationCategory requireByCategoryId(String categoryId) {
        PublicationCategory category = findByCategoryId(categoryId);
        if (category == null) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_NOT_FOUND",
                    "no category with id " + categoryId);
        }
        return category;
    }


    /**
     * Creates a category under a new id, refusing a duplicate.
     *
     * categoryId is what a series stores and what an import upserts on, so two
     * rows sharing one is not a near-miss: whichever the next lookup happens to
     * return decides which section of the public page a publication lands in.
     */
    @Transactional
    public PublicationCategory createUnderNewId(PublicationCategory category) {
        if (findByCategoryId(category.getCategoryId()) != null) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_ID_TAKEN",
                    "a category with id '" + category.getCategoryId() + "' already exists");
        }
        PublicationCategory saved = saveEntity(category);
        // Flushed here so a constraint the entity violates surfaces as this
        // call's failure, rather than at the end of the request where it can no
        // longer be attributed to the save that caused it.
        em.flush();
        return saved;
    }


    /** Persists an edited category. */
    @Transactional
    public PublicationCategory save(PublicationCategory category) {
        return saveEntity(category);
    }


    /**
     * Deletes a category, refused while anything still points at it.
     *
     * BOTH publication models are counted, because both still store this row.
     * Counting only the newer side would let a category be deleted out from
     * under the publications the legacy list is still serving, and the failure
     * would surface as a missing section on the public page rather than as a
     * refusal here.
     */
    @Transactional
    public void deleteUnreferenced(String categoryId) {
        PublicationCategory category = requireByCategoryId(categoryId);

        Long series = em.createQuery(
                        "SELECT COUNT(s) FROM PublicationSeries s WHERE s.category = :c", Long.class)
                .setParameter("c", category).getSingleResult();
        Long publications = em.createQuery(
                        "SELECT COUNT(p) FROM Publication p WHERE p.category = :c", Long.class)
                .setParameter("c", category).getSingleResult();
        if (series + publications > 0) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_IN_USE",
                    series + " series and " + publications + " publication(s) still belong to '"
                            + categoryId + "'. Move them to another category first.");
        }
        remove(category);
    }

}
