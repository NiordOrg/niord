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
package org.niord.core.mailinglist;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.service.BaseService;
import org.niord.core.user.Contact;
import org.niord.core.user.ContactService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import java.util.Date;
import java.util.List;

/**
 * Business interface for accessing Niord mailingLists
 */
@Stateless
@SuppressWarnings("unused")
public class MailingListService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    UserService userService;

    @Inject
    ContactService contactService;


    /**
     * Returns the mailing list with the given mailing list id
     *
     * @param mailingListId the mailing list id of the mailingList
     * @return the mailing list with the given id or null if not found
     */
    public MailingList findByMailingListId(String mailingListId) {
        try {
            return em.createNamedQuery("MailingList.findByMailingListId", MailingList.class)
                    .setParameter("mailingListId", mailingListId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Searches for mailing lists matching the given search params
     *
     * @param params the search params
     * @return the search result
     */
    @SuppressWarnings("all")
    public List<MailingList> searchMailingLists(MailingListSearchParams params) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<MailingList> mailingListQuery = cb.createQuery(MailingList.class);

        Root<MailingList> mailingListRoot = mailingListQuery.from(MailingList.class);

        // Build the predicate
        CriteriaHelper<MailingList> criteriaHelper = new CriteriaHelper<>(cb, mailingListQuery);

        // Match ID
        criteriaHelper.equals(mailingListRoot.get("mailingListId"), params.getMailingListId());

        // Match the name
        if (StringUtils.isNotBlank(params.getName())) {
            Join<MailingList, MailingListDesc> descs = mailingListRoot.join("descs", JoinType.LEFT);
            criteriaHelper.like(descs.get("name"), params.getName());
            criteriaHelper.equals(descs.get("lang"), params.getLanguage());
        }

        // Filter by the username associated with the mailing list
        if (StringUtils.isNotBlank(params.getUsername())) {
            Join<MailingList, User> userJoin = mailingListRoot.join("users", JoinType.LEFT);
            criteriaHelper.equalsIgnoreCase(userJoin.get("username"), params.getUsername());
        }

        // Filter by the contact associated with the mailing list
        if (StringUtils.isNotBlank(params.getContactEmail())) {
            Join<MailingList, Contact> contactJoin = mailingListRoot.join("contacts", JoinType.LEFT);
            criteriaHelper.equalsIgnoreCase(contactJoin.get("email"), params.getContactEmail());
        }
        
        // Complete the query
        mailingListQuery.select(mailingListRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(cb.asc(mailingListRoot.get("mailingListId")));

        // Execute the query and update the search result
        return em.createQuery(mailingListQuery)
                .setMaxResults(params.getMaxSize())
                .getResultList();
    }


    /**
     * Looks up a mailing list
     *
     * @param id the id of the mailing list
     * @return the mailing list
     */
    public MailingList getMailingList(Integer id) {
        return getByPrimaryKey(MailingList.class, id);
    }


    /**
     * Updates the mailing list from the mailing list template.
     *
     * Important: the list of recipients (users and contacts) are not updated. Use {@code updateMailingListRecipients}
     * for this.
     *
     * @param mailingList the mailing list to update
     * @return the updated mailing list
     */
    public MailingList updateMailingList(MailingList mailingList) {
        final MailingList original = findByMailingListId(mailingList.getMailingListId());
        if (original == null) {
            throw new IllegalArgumentException("Mailing list " + mailingList.getMailingListId() + " does not exists");
        }

        original.setActive(mailingList.isActive());
        original.copyDescsAndRemoveBlanks(mailingList.getDescs());
        original.getTriggers().clear();
        mailingList.getTriggers().forEach(original::addTrigger);

        // Compute next execution for scheduled triggers
        original.checkComputeNextScheduledExecutions();

        return saveEntity(original);
    }


    /**
     * Updates the mailing list recipients from the mailing list template
     *
     * @param mailingList the mailing list to update
     * @return the updated mailing list
     * @noinspection all
     */
    public MailingList updateMailingListRecipients(MailingList mailingList) {
        MailingList original = findByMailingListId(mailingList.getMailingListId());
        if (original == null) {
            throw new IllegalArgumentException("Mailing list " + mailingList.getMailingListId() + " does not exists");
        }

        // Replace related entities with persisted ones
        original.setUsers(userService.persistedUsers(mailingList.getUsers()));
        original.setContacts(persistedList(Contact.class, mailingList.getContacts()));

        original = saveEntity(original);

        return original;
    }


    /**
     * Creates a new mailing list based on the mailing list template
     * @param mailingList the mailing list to create
     * @return the created mailing list
     */
    public MailingList createMailingList(MailingList mailingList) {
        MailingList original = findByMailingListId(mailingList.getMailingListId());
        if (original != null) {
            throw new IllegalArgumentException("Mailing list " + mailingList.getMailingListId() + " already exists");
        }

        // Replace related entities with persisted ones
        mailingList.setUsers(userService.persistedUsers(mailingList.getUsers()));
        mailingList.setContacts(persistedList(Contact.class, mailingList.getContacts()));

        // Compute next execution for scheduled triggers
        mailingList.checkComputeNextScheduledExecutions();

        return saveEntity(mailingList);
    }


    /**
     * Deletes the mailing list
     * @param mailingListId the id of the mailing list to delete
     * @noinspection all
     */
    public boolean deleteMailingList(String mailingListId) {

        MailingList mailingList = findByMailingListId(mailingListId);
        if (mailingList != null) {
            remove(mailingList);
            log.debug("Removed mailingList " + mailingListId);
            return true;
        }
        return false;
    }


    /**
     * Returns the list of pending scheduled mailing list triggers
     * @return the list of pending scheduled mailing list triggers
     */
    public List<MailingListTrigger> findPendingScheduledTriggers() {
        return em.createNamedQuery("MailingListTrigger.findPendingScheduledTriggers", MailingListTrigger.class)
                .setParameter("time", new Date())
                .getResultList();
    }


    /**
     * Returns the list of status change mailing list triggers matching the given status
     * @param status the status to find triggers for
     * @return the list of status change mailing list triggers
     */
    public List<MailingListTrigger> findStatusChangeTriggers(Status status) {
        return em.createNamedQuery("MailingListTrigger.findStatusChangeTriggers", MailingListTrigger.class)
                .setParameter("status", status)
                .getResultList();
    }



}
