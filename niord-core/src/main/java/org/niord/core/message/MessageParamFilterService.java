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
package org.niord.core.message;

import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business interface for managing persisted message parameter filters
 */
@RequestScoped
@SuppressWarnings("unused")
public class MessageParamFilterService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    UserService userService;


    /**
     * Returns the message filters with the given identifier
     * @param id the message filter
     * @return the message filters with the given identifier or null if not found
     */
    public MessageParamFilter findById(Integer id) {
        User user = userService.currentUser();

        // Look up the given filter
        MessageParamFilter filter = getByPrimaryKey(MessageParamFilter.class, id);

        // Check that the current user is the owner of the filter
        if (filter != null && !user.getId().equals(filter.getUser().getId())) {
            throw new IllegalArgumentException("User " + user.getUsername() + " does now own filter " + id);
        }
        return filter;
    }


    /**
     * Returns the message filter for the current user with the given name
     * @param name the name of the message filter to look up
     * @return the matching message filter or null
     */
    public MessageParamFilter findByCurrentUserAndName(String name) {
        User user = userService.currentUser();

        try {
            return em.createNamedQuery("MessageParamFilter.findByUserAndName", MessageParamFilter.class)
                    .setParameter("user", user)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all message filters for the current user with the given IDs
     * @param ids the message filter IDs of the message filters to look up
     * @return the list of all message filters for the current user with the given IDs
     */
    public List<MessageParamFilter> findByCurrentUserAndIds(Integer... ids) {
        User user = userService.currentUser();

        Set<Integer> filterIds = new HashSet<>(Arrays.asList(ids));
        return em.createNamedQuery("MessageParamFilter.findByUserAndIds", MessageParamFilter.class)
                .setParameter("user", user)
                .setParameter("ids", filterIds)
                .getResultList();
    }


    /**
     * Returns all message filters for the current user
     * @return the list of all message filters for the current user
     */
    public List<MessageParamFilter> getMessageFiltersForUser() {
        User user = userService.currentUser();
        return em.createNamedQuery("MessageParamFilter.findByUser", MessageParamFilter.class)
                .setParameter("user", user)
                .getResultList();
    }


    /**
     * Creates a new message filter for the current user from the given template.
     * If a filter with the given name already exists, update this filter instead.
     * @param filter the new message filter
     * @return the persisted message filter
     */
    @Transactional
    public MessageParamFilter createOrUpdateMessageFilter(MessageParamFilter filter) {
        MessageParamFilter original;

        // Search for an existing filter with the same ID or name
        if (filter.getId() != null) {
            original = findById(filter.getId());
            if (original == null) {
                throw new IllegalArgumentException("Invalid filter id  " + filter.getId());
            }
        } else {
            original = findByCurrentUserAndName(filter.getName());
        }

        // Update an existing filter
        if (original != null) {
            original.setName(filter.getName());
            original.setParameters(filter.getParameters());
            return saveEntity(original);
        }

        // Create a new filter
        filter.setUser(userService.currentUser());
        return saveEntity(filter);
    }


    /**
     * Deletes the message filter for the current user with the given ID
     * @param id the message filter to delete
     * @return if the message filter was deleted
     */
    @Transactional
    public boolean deleteMessageFilter(Integer id) {
        User user = userService.currentUser();

        MessageParamFilter original = findById(id);
        if (original != null) {
            log.info("Removing message filter " + id);
            remove(original);
            return true;
        }
        return false;
    }

}
