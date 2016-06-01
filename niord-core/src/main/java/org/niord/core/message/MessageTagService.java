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
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.model.vo.MessageTagVo.MessageTagType.DOMAIN;
import static org.niord.model.vo.MessageTagVo.MessageTagType.PRIVATE;


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

    @Inject
    DomainService domainService;

    /**
     * Returns the message tag with the given ID
     * @param tagId the tag ID
     * @return the message tag with the given tag identifier or null if not found
     */
    public MessageTag findTag(String tagId) {
        List<MessageTag> tags = findTags(tagId);
        return tags.isEmpty() ? null : tags.get(0);
    }


    /**
     * Returns the message tags with the given tag IDs
     * @param tagIds the tag IDs
     * @return the message tags with the given tag identifiers
     */
    public List<MessageTag> findTags(String... tagIds) {
        if (tagIds == null || tagIds.length == 0) {
            return Collections.emptyList();
        }

        User user = userService.currentUser();
        Domain domain = domainService.currentDomain();

        Set<String> idSet = new HashSet<>(Arrays.asList(tagIds));
        return em.createNamedQuery("MessageTag.findTagsByTagIds", MessageTag.class)
                .setParameter("tagIds", idSet)
                .getResultList()
                .stream()
                .filter(t -> validateAccess(t, user, domain))
                .sorted()
                .collect(Collectors.toList());
    }


    /** Validate that the user has access to the given tag */
    private boolean validateAccess(MessageTag tag, User user, Domain domain) {
        if (tag.getType() == PRIVATE) {
            return user != null && tag.getUser() != null && user.getId().equals(tag.getUser().getId());
        } else if (tag.getType() == DOMAIN) {
            return domain != null && tag.getDomain() != null && domain.getId().equals(tag.getDomain().getId());
        }
        return true;
    }


    /**
     * Returns all message tags for the current user
     *
     * @return the search result
     */
    public List<MessageTag> findUserTags() {
        List<MessageTag> result = new ArrayList<>();

        User user = userService.currentUser();
        if (user != null) {
            result.addAll(em.createNamedQuery("MessageTag.findByUser", MessageTag.class)
                    .setParameter("user", user)
                    .getResultList());
        }

        Domain domain = domainService.currentDomain();
        if (domain != null) {
            result.addAll(em.createNamedQuery("MessageTag.findByDomain", MessageTag.class)
                    .setParameter("domain", domain)
                    .getResultList());
        }

        result.addAll(em.createNamedQuery("MessageTag.findPublic", MessageTag.class)
                .getResultList());

        Collections.sort(result);
        return result;
    }


    /**
     * Returns the message tags which contain the message with the given ID
     * @param messageId the tag IDs
     * @return the message tags which contain the message with the given ID
     */
    public List<MessageTag> findTagsByMessageId(Integer messageId) {
        if (messageId == null) {
            return Collections.emptyList();
        }

        // Only search tags that the user has access to
        Set<String> idSet = findUserTags().stream()
                .map(MessageTag::getTagId)
                .collect(Collectors.toSet());

        return em.createNamedQuery("MessageTag.findTagsByMessageId", MessageTag.class)
                .setParameter("tagIds", idSet)
                .setParameter("messageId", messageId)
                .getResultList()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }



    /**
     * Searches for message tags matching the given term
     *
     * @param term the search term
     * @param limit the maximum number of results
     * @return the search result
     */
    public List<MessageTag> searchMessageTags(String term, int limit) {

        return findUserTags()
                .stream()
                .filter(t -> StringUtils.isBlank(term) || t.getName().toLowerCase().contains(term.toLowerCase()))
                .collect(Collectors.toList());
    }


    /**
     * Creates a new message tag from the given template
     * @param tag the new message tag
     * @return the persisted message tag
     */
    public MessageTag createMessageTag(MessageTag tag) {
        MessageTag original = findTag(tag.getTagId());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create message tag with duplicate message tag IDs"
                    + tag.getTagId());
        }

        tag.setUser(userService.currentUser());
        tag.setDomain(domainService.currentDomain());

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
    public MessageTag updateMessageTag(MessageTag tag) {
        MessageTag original = findTag(tag.getTagId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing message tag"
                    + tag.getTagId());
        }

        original.setExpiryDate(tag.getExpiryDate());
        original.setType(tag.getType());
        original.setName(tag.getName());

        // TODO: Should we override the user and domain? Ban the user changing the type?
        //tag.setUser(userService.currentUser());
        //tag.setDomain(domainService.currentDomain());

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

        MessageTag original = findTag(tagId);
        if (original != null) {
            log.info("Removing message tag " + tagId);
            remove(original);
            return true;
        }
        return false;
    }


    /**
     * Clears all messages from the message tag with the given tag ID
     * @param tagId the ID of the message tag to clear
     * @return if the message tag was deleted
     */
    public boolean clearMessageTag(String tagId) {

        MessageTag original = findTag(tagId);
        if (original != null) {
            log.info("Clearing message tag " + tagId);
            original.getMessages().clear();
            original.updateMessageCount();
            saveEntity(original);
            return true;
        }
        return false;
    }


    /**
     * Adds messages to the given tag
     * @param tagId the ID of the message tag to add the message to
     * @param messageIds the id of the messages to add
     * @return the updated message tag
     */
    public MessageTag addMessageToTag(String tagId, Set<Integer> messageIds) {
        MessageTag tag = findTag(tagId);
        if (tag == null) {
            throw new IllegalArgumentException("No message tag with ID " + tagId);
        }

        int prevMsgCnt = tag.getMessages().size();
        for (Integer messageId : messageIds) {
            Message message = getByPrimaryKey(Message.class, messageId);
            if (message != null && !tag.getMessages().contains(message)) {
                tag.getMessages().add(message);
            }
        }

        if (tag.getMessages().size() != prevMsgCnt) {
            tag.updateMessageCount();
            saveEntity(tag);
            log.info("Added " + (tag.getMessages().size() - prevMsgCnt) + " messages to tag " + tag.getName());
        }

        return tag;
    }


    /**
     * Removes messages from the given tag
     * @param tagId the ID of the message tag to remove the message from
     * @param messageIds the id of the messages to remove
     * @return if any messages was removed
     */
    public boolean removeMessageFromTag(String tagId, Set<Integer> messageIds) {
        MessageTag tag = findTag(tagId);
        if (tag == null) {
            throw new IllegalArgumentException("No message tag with ID " + tagId);
        }

        int prevMsgCnt = tag.getMessages().size();
        for (Integer messageId : messageIds) {
            Message message = getByPrimaryKey(Message.class, messageId);
            if (message != null && tag.getMessages().contains(message)) {
                tag.getMessages().remove(message);
            }
        }

        if (tag.getMessages().size() != prevMsgCnt) {
            tag.updateMessageCount();
            saveEntity(tag);
            log.info("Removed " + (prevMsgCnt - tag.getMessages().size()) + " messages from tag " + tag.getName());
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
