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

package org.niord.core.fm;

import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.List;


/**
 * Main interface for accessing and processing Freemarker report templates
 */
@SuppressWarnings("unused")
public class FmTemplateService extends BaseService {

    @Inject
    Logger log;


    /**
     * Returns the template with the given ID, or null if not found
     * @param id the template id
     * @return the template with the given ID, or null if not found
     */
    public FmTemplate findById(Integer id) {
        return getByPrimaryKey(FmTemplate.class, id);
    }


    /**
     * Returns the template with the given path, or null if not found
     * @param path the template path
     * @return the template with the given path, or null if not found
     */
    public FmTemplate findByPath(String path) {
        try {
            return em.createNamedQuery("FmTemplate.findByPath", FmTemplate.class)
                    .setParameter("path", path)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all templates
     * @return all templates
     */
    public List<FmTemplate> findAll() {
        return em.createNamedQuery("FmTemplate.findAll", FmTemplate.class)
                .getResultList();
    }


    /**
     * Creates a new template based on the template parameter
     * @param template the template to create
     * @return the created template
     */
    public FmTemplate createTemplate(FmTemplate template) {
        FmTemplate original = findByPath(template.getPath());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create template with duplicate path " + template.getPath());
        }

        return saveEntity(template);
    }


    /**
     * Updates the template data from the template parameter
     * @param template the template to update
     * @return the updated template
     */
    public FmTemplate updateTemplate(FmTemplate template) {
        FmTemplate original = findById(template.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing template " + template.getPath());
        }

        // Copy the template data
        original.setPath(template.getPath());
        original.setTemplate(template.getTemplate());

        return saveEntity(original);
    }


    /**
     * Deletes the template with the given path
     * @param id the ID of the template to delete
     */
    public boolean deleteTemplate(Integer id) {

        FmTemplate template = findById(id);
        if (template != null) {
            remove(template);
            return true;
        }
        return false;
    }

}
