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

import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Business interface for accessing publication categories
 */
@RequestScoped
@SuppressWarnings("unused")
public class PublicationCategoryService extends BaseService {

    @Inject
    private Logger log;


    /**
     * Returns the category with the given category ID
     * @param categoryId the category ID
     * @return the category with the given category ID or null if not found
     */
    public PublicationCategory findByCategoryId(String categoryId) {
        try {
            return em.createNamedQuery("PublicationCategory.findByCategoryId", PublicationCategory.class)
                    .setParameter("categoryId", categoryId)
                    .getSingleResult();
        } catch (Exception ignored) {
        }
        return null;
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

}
