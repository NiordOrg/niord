/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Business interface for managing message tags.
 *
 * Message tags can either be tied to a user or be shared, so the set of tags
 * that a specific user can work on, is her own tags + the shared tags.
 */
@Stateless
@SuppressWarnings("unused")
public class MessageTagService extends BaseService {

    @Inject
    Logger log;

    @Inject
    UserService userService;

    /**
     * Returns the message tag with the given ID
     * @param tagId the tag ID
     * @return the message tag with the given tag identifier or null if not found
     */
    public MessageTag findByUserAndTagId(String tagId) {
        List<MessageTag> tags = findByUserAndTagIds(tagId);
        return tags.isEmpty() ? null : tags.get(0);
    }


    /**
     * Returns the message tags with the given tag IDs for the current user
     * @param tagIds the tag IDs
     * @return the message tags with the given tag identifiers
     */
    public List<MessageTag> findByUserAndTagIds(String... tagIds) {
        User user = userService.currentUser();
        Set<String> idSet = new HashSet<>(Arrays.asList(tagIds));
        if (user == null) {
            return em.createNamedQuery("MessageTag.findSharedByTagIds", MessageTag.class)
                    .setParameter("tagIds", idSet)
                    .getResultList();
        } else {
            return em.createNamedQuery("MessageTag.findByUserAndTagIds", MessageTag.class)
                    .setParameter("user", user)
                    .setParameter("tagIds", idSet)
                    .getResultList();
        }
    }


    /**
     * Searches for message tags matching the given term
     *
     * @param term the search term
     * @param limit the maximum number of results
     * @return the search result
     */
    public List<MessageTag> searchMessageSeries(String term, int limit) {

        if (StringUtils.isNotBlank(term)) {
            User user = userService.currentUser();
            if (user == null) {
                return em
                        .createNamedQuery("MessageTag.searchSharedMessageTags", MessageTag.class)
                        .setParameter("term", "%" + term + "%")
                        .setMaxResults(limit)
                        .getResultList();
            } else {
                return em
                        .createNamedQuery("MessageTag.searchMessageTagsByUser", MessageTag.class)
                        .setParameter("term", "%" + term + "%")
                        .setParameter("user", user)
                        .setMaxResults(limit)
                        .getResultList();
            }
        }
        return Collections.emptyList();
    }


    /**
     * Creates a new message tag from the given template
     * @param tag the new message tag
     * @return the persisted message tag
     */
    public MessageTag createMessageTag(MessageTag tag) {
        List<MessageTag> originals = findByUserAndTagIds(tag.getTagId());
        if (!originals.isEmpty()) {
            throw new IllegalArgumentException("Cannot create message tag with duplicate message tag IDs"
                    + tag.getTagId());
        }

        tag.setUser(userService.currentUser());

        // Replace the messages with the persisted messages
        tag.setMessages(persistedList(Message.class, tag.getMessages()));

        log.info("Creating new message tag " + tag.getTagId());
        return saveEntity(tag);
    }


    /**
     * Updates an existing message tag from the given template
     * @param tag the message tag to update
     * @return the persisted message tag
     */
    public MessageTag updateMessageSeries(MessageTag tag) {
        MessageTag original = findByUserAndTagId(tag.getTagId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing message tag"
                    + tag.getTagId());
        }

        original.setExpiryDate(tag.getExpiryDate());

        // Replace the messages with the persisted messages
        original.setMessages(persistedList(Message.class, tag.getMessages()));

        log.info("Updating message tag " + original.getTagId());
        return saveEntity(original);
    }


    /**
     * Deletes the message tag with the given tag ID
     * @param tagId the ID of the message tag to delete
     * @return if the message tag was deleted
     */
    public boolean deleteMessageTag(String tagId) {

        MessageTag original = findByUserAndTagId(tagId);
        if (original != null) {
            log.info("Removing message tag " + tagId);
            remove(original);
            return true;
        }
        return false;
    }


    /**
     * Every hour, expired message tags will be removed
     */
    @Schedule(persistent=false, second="22", minute="22", hour="*/1")
    private void removeExpiredMessageTags() {
        List<MessageTag> expiredTags = em.createNamedQuery("MessageTag.findExpiredMessageTags", MessageTag.class)
                .getResultList();
        if (!expiredTags.isEmpty()) {
            expiredTags.forEach(this::remove);
            log.info("Removed " + expiredTags + " expired message tags");
        }
    }
}
