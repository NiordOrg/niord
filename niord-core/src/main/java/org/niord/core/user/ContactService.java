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

package org.niord.core.user;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.service.BaseService;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.List;

/**
 * Defines the API for accessing contacts
 */
@Stateless
@SuppressWarnings("unused")
public class ContactService extends BaseService {

    @Inject
    private Logger log;


    /**
     * Searches for contacts matching the search criteria
     * @param params the search params to match
     * @return the matching contacts
     */
    public PagedSearchResultVo<Contact> searchContacts(ContactSearchParams params) {

        long t0 = System.currentTimeMillis();

        PagedSearchResultVo<Contact> result = new PagedSearchResultVo<>();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // First compute the total number of matching contacts
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Contact> countContactRoot = countQuery.from(Contact.class);

        countQuery.select(cb.count(countContactRoot))
                .where(buildQueryPredicates(cb, countQuery, countContactRoot, params))
                .orderBy(cb.asc(cb.lower(countContactRoot.get("email"))));

        result.setTotal(em.createQuery(countQuery).getSingleResult());


        // Then, extract the current page of matches
        CriteriaQuery<Contact> query = cb.createQuery(Contact.class);
        Root<Contact> contactRoot = query.from(Contact.class);
        query.select(contactRoot)
                .where(buildQueryPredicates(cb, query, contactRoot, params))
                .orderBy(cb.asc(cb.lower(contactRoot.get("email"))));

        List<Contact> contacts = em.createQuery(query)
                .setMaxResults(params.getMaxSize())
                .setFirstResult(params.getPage() * params.getMaxSize())
                .getResultList();
        result.setData(contacts);
        result.updateSize();

        log.info("Search [" + params + "] returned " + result.getSize() + " of " + result.getTotal() + " in "
                + (System.currentTimeMillis() - t0) + " ms");

        return result;
    }


    /** Helper function that translates the search parameters into predicates */
    private <T> Predicate[] buildQueryPredicates(CriteriaBuilder cb, CriteriaQuery<T> query, Root<Contact> contactRoot, ContactSearchParams params) {

        // Build the predicate
        CriteriaHelper<T> criteriaHelper = new CriteriaHelper<>(cb, query);

        // Match name
        if (StringUtils.isNotBlank(params.getName())) {
            String nameMatch = "%" + params.getName().toLowerCase() + "%";
            Predicate namePredicate = cb.or(
                    cb.like(contactRoot.get("firstName"), nameMatch),
                    cb.like(contactRoot.get("lastName"), nameMatch),
                    cb.like(contactRoot.get("email"), nameMatch)
            );
            criteriaHelper.add(namePredicate);
        }

        // More filter options to come...

        return criteriaHelper.where();
    }


    /** Returns all contacts **/
    public List<Contact> getAllContacts() {
        return getAll(Contact.class);
    }


    /**
     * Returns the contact with the given ID, or null if not found.
     * @param id the ID of the contact
     * @return the contact with the given ID
     */
    public Contact findById(Integer id) {
        return getByPrimaryKey(Contact.class, id);
    }


    /**
     * Returns the contact with the given email address, or null if not found.
     * @param email the email address of the contact
     * @return the contact with the given email address
     */
    public Contact findByEmail(String email) {
        try {
            return em.createNamedQuery("Contact.findByEmail", Contact.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Creates a new contact from the given template
     * @param contact the Contact template
     * @return the newly created contact
     */
    public Contact createContact(Contact contact) {
        Contact original = findByEmail(contact.getEmail());
        if (original != null) {
            throw new IllegalArgumentException("Contact with email " + contact.getEmail() + " already exists");
        }

        return saveEntity(contact);
    }


    /**
     * Imports a new contact from the given template in a new transaction
     * @param contact the Contact template
     * @return the newly created contact
     * @noinspection all
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Contact importContact(Contact contact) {
        return createContact(contact);
    }


    /**
     * Updates an existing contact from the given template
     * @param contact the Contact template
     * @return the newly created contact
     */
    public Contact updateContact(Contact contact) {
        Contact original = findById(contact.getId());
        if (original == null) {
            throw new IllegalArgumentException("Contact with ID " + contact.getId() + " does not exists");
        }

        original.setEmail(contact.getEmail());
        original.setFirstName(contact.getFirstName());
        original.setLastName(contact.getLastName());
        original.setLanguage(contact.getLanguage());

        return saveEntity(original);
    }


    /**
     * Deletes the contact with the given ID
     * @param id the ID of the contact to delete
     * @return if the contact was deleted
     * @noinspection all
     */
    public boolean deleteContact(Integer id) {
        Contact original = findById(id);
        if (original != null) {
            remove(original);
            return true;
        }
        return false;
    }

}
