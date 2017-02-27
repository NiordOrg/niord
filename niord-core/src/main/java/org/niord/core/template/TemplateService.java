/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.template;

import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.domain.DomainService;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

/**
 * Main interface for accessing and processing message templates
 */
@Stateless
@SuppressWarnings("unused")
public class TemplateService extends BaseService {

    @Inject
    CategoryService categoryService;

    @Inject
    DomainService domainService;

    @Inject
    Logger log;

    /**
     * Returns the template with the given ID, or null if not found
     * @param id the template id
     * @return the template with the given ID, or null if not found
     */
    public Template findById(Integer id) {
        return getByPrimaryKey(Template.class, id);
    }


    /**
     * Returns the templates with the given category for the current domain
     * @param categoryId the category ID
     * @return the templates with the given category for the current domain
     */
    public List<Template> findByCategoryAndCurrentDomain(Integer categoryId) {
        return em.createNamedQuery("Template.findByCategoryAndDomain", Template.class)
                .setParameter("categoryId", categoryId)
                .setParameter("domain", domainService.currentDomain())
                .getResultList();
    }


    /**
     * Returns all templates
     * @return all templates
     */
    public List<Template> findAll() {
        return em.createNamedQuery("Template.findAll", Template.class)
                .getResultList();
    }


    /**
     * Creates a new template based on the template parameter
     * @param template the template to create
     * @return the created template
     */
    public Template createTemplate(Template template) {
        if (template.isPersisted()) {
            throw new IllegalArgumentException("Cannot create existing template " + template.getId());
        }

        // Replace category with persisted entity
        Category category = categoryService.getCategoryDetails(template.getCategory().getId());
        category.getTemplates().add(template);
        template.setCategory(category);

        // Replace domains with persisted entities
        template.setDomains(domainService.persistedDomains(template.getDomains()));

        return saveEntity(template);
    }


    /**
     * Updates the template data from the template parameter
     * @param template the template to update
     * @return the updated template
     */
    public Template updateTemplate(Template template) {
        Template original = findById(template.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing template " + template.getId());
        }

        // Copy the template data
        original.setTemplatePath(template.getTemplatePath());
        original.setMessageId(template.getMessageId());
        original.copyDescsAndRemoveBlanks(template.getDescs());

        // Replace category with persisted entity
        Category category = categoryService.getCategoryDetails(template.getCategory().getId());
        if (!category.getTemplates().contains(original)) {
            original.getCategory().getTemplates().remove(original);
            category.getTemplates().add(template);
        }
        original.setCategory(category);

        // Replace domains with persisted entities
        original.setDomains(domainService.persistedDomains(template.getDomains()));

        return saveEntity(original);
    }


    /**
     * Deletes the template with the given id
     * @param id the ID of the template to delete
     */
    public boolean deleteTemplate(Integer id) {

        Template template = findById(id);
        if (template != null) {
            template.getCategory().getTemplates().remove(template);
            remove(template);
            return true;
        }
        return false;
    }

}
