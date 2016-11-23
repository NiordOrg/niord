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

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

/**
 * Business interface for accessing publication types
 */
@Stateless
@SuppressWarnings("unused")
public class PublicationTypeService extends BaseService {

    @Inject
    private Logger log;


    /**
     * Returns the type with the given type ID
     * @param typeId the type ID
     * @return the type with the given type ID or null if not found
     */
    public PublicationType findByTypeId(String typeId) {
        try {
            return em.createNamedQuery("PublicationType.findByTypeId", PublicationType.class)
                    .setParameter("typeId", typeId)
                    .getSingleResult();
        } catch (Exception ignored) {
        }
        return null;
    }


    /**
     * Returns the list of publication types
     * @return the list of publication types
     */
    public List<PublicationType> getPublicationTypes() {
        return getAll(PublicationType.class);
    }


    /**
     * Updates the publication type from the type template
     * @param type the publication type to update
     * @return the updated publication type
     */
    public PublicationType updatePublicationType(PublicationType type) {
        PublicationType original = findByTypeId(type.getTypeId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing publication type "
                    + type.getId());
        }

        // Copy the publication data
        original.updatePublicationType(type);

        return saveEntity(original);
    }


    /**
     * Creates a new publication type based on the publication type template
     * @param type the publication type to create
     * @return the created publication type
     */
    public PublicationType createPublicationType(PublicationType type) {
        if (!type.isNew()) {
            throw new IllegalArgumentException("Cannot create publication type with existing ID "
                    + type.getId());
        }

        return saveEntity(type);
    }


    /**
     * Finds or creates a publication type based on the publication type template
     * @param typeTemplate the publication type to find or create
     * @return the publication type
     */
    public PublicationType findOrCreatePublicationType(PublicationType typeTemplate) {
        if (typeTemplate == null) {
            return null;
        }

        PublicationType type = findByTypeId(typeTemplate.getTypeId());
        if (type == null) {
            type = createPublicationType(typeTemplate);
        }
        return type;
    }


    /**
     * Deletes the publication type with the given ID
     * @param typeId the id of the publication type to delete
     */
    public boolean deletePublicationType(String typeId) {

        PublicationType type = findByTypeId(typeId);
        if (type != null) {
            remove(type);
            return true;
        }
        return false;
    }

}
