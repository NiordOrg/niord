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
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business interface for accessing publications
 */
@Stateless
@SuppressWarnings("unused")
public class PublicationService extends BaseService {

    @Inject
    private Logger log;


    /**
     * Returns the publication with the given ID
     * @param id the ID
     * @return the publication with the given ID or null if not found
     */
    public Publication findById(Integer id) {
        return getByPrimaryKey(Publication.class, id);
    }


    /**
     * Searches for publications matching the given term
     *
     * @param term the search term
     * @param lang the search language
     * @param inactive whether to include inactive publications as well as active
     * @param limit the maximum number of results
     * @return the search result
     */
    public List<Publication> searchPublications(String lang, String term, boolean inactive, int limit) {

        term = StringUtils.defaultIfBlank(term, "");

        Set<Boolean> activeFlag = new HashSet<>();
        activeFlag.add(Boolean.TRUE);
        if (inactive) {
            activeFlag.add(Boolean.FALSE);
        }

        return em
                .createNamedQuery("Publication.searchPublications", Publication.class)
                .setParameter("active", activeFlag)
                .setParameter("lang", lang)
                .setParameter("term", "%" + term.toLowerCase() + "%")
                .getResultList()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }


    /**
     * Returns the publication with the given name or null if not found
     *
     * @param lang the search language
     * @param name the search name
     * @return the publication with the given name or null if not found
     */
    public Publication findByName(String lang, String name) {

        if (StringUtils.isNotBlank(name)) {
            try {
                return em
                        .createNamedQuery("Publication.findByName", Publication.class)
                        .setParameter("lang", lang)
                        .setParameter("name", name.toLowerCase())
                        .getSingleResult();
            } catch (Exception ignored) {
            }
        }
        return null;
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
        Publication original = findById(publication.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing publication "
                    + publication.getId());
        }

        // Copy the publication data
        original.setActive(publication.isActive());
        original.setInternal(publication.isInternal());
        original.setMessagePublicationLink(publication.isMessagePublicationLink());
        original.getDescs().clear();
        original.copyDescsAndRemoveBlanks(publication.getDescs());

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

        return saveEntity(publication);
    }


    /**
     * Deletes the publication with the given ID
     * @param id the id of the publication to delete
     */
    public boolean deletePublication(Integer id) {

        Publication publication = findById(id);
        if (publication != null) {
            remove(publication);
            return true;
        }
        return false;
    }

}
