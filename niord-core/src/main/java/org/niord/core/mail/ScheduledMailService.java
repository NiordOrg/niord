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

package org.niord.core.mail;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.model.BaseEntity;
import org.niord.core.service.BaseService;
import org.niord.core.util.CdiUtils;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Interface for handling schedule mails
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class ScheduledMailService extends BaseService {

    /** The max number of mails to process at a time **/
    public static final int MAX_MAIL_PER_MINUTE = 10;

    @Inject
    Logger log;

    @Inject
    MailService mailService;

    @Resource
    ManagedExecutorService managedExecutorService;


    /**
     * Searches the filtered set of scheduled mails
     * @param params the search parameters
     * @return the search result
     */
    public PagedSearchResultVo<ScheduledMail> search(ScheduledMailSearchParams params) {

        long t0 = System.currentTimeMillis();

        PagedSearchResultVo<ScheduledMail> result = new PagedSearchResultVo<>();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // First compute the total number of matching mails
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<ScheduledMail> countMailRoot = countQuery.from(ScheduledMail.class);

        countQuery.select(cb.count(countMailRoot))
                .where(buildQueryPredicates(cb, countQuery, countMailRoot, params))
                .orderBy(cb.desc(countMailRoot.get("created")));

        result.setTotal(em.createQuery(countQuery).getSingleResult());


        // Then, extract the current page of matches
        CriteriaQuery<ScheduledMail> query = cb.createQuery(ScheduledMail.class);
        Root<ScheduledMail> mailRoot = query.from(ScheduledMail.class);
        query.select(mailRoot)
                .where(buildQueryPredicates(cb, query, mailRoot, params))
                .orderBy(cb.desc(countMailRoot.get("created")));

        List<ScheduledMail> mails = em.createQuery(query)
                .setMaxResults(params.getMaxSize())
                .setFirstResult(params.getPage() * params.getMaxSize())
                .getResultList();
        result.setData(mails);
        result.updateSize();

        log.info("Search [" + params + "] returned " + result.getSize() + " of " + result.getTotal() + " in "
                + (System.currentTimeMillis() - t0) + " ms");

        return result;
    }


    /** Helper function that translates the search parameters into predicates */
    private <T> Predicate[] buildQueryPredicates(CriteriaBuilder cb, CriteriaQuery<T> query, Root<ScheduledMail> mailRoot, ScheduledMailSearchParams params) {

       // Build the predicate
        CriteriaHelper<T> criteriaHelper = new CriteriaHelper<>(cb, query);

        // Match the recipient
        if (StringUtils.isNotBlank(params.getRecipient())) {
            Join<ScheduledMail, ScheduledMailRecipient> recipients = mailRoot.join("recipients", JoinType.LEFT);
            criteriaHelper.like(recipients.get("address"), params.getRecipient());
        }

        // Match sender
        criteriaHelper.like(mailRoot.get("sender"), params.getSender());

        // Match subject
        criteriaHelper.like(mailRoot.get("subject"), params.getSubject());

        // Match status
        criteriaHelper = criteriaHelper.equals(mailRoot.get("status"), params.getStatus());

        // Match date interval
        criteriaHelper.between(mailRoot.get("created"), params.getFrom(), params.getTo());

        return criteriaHelper.where();
    }


    /**
     * Returns the mail with the given ID and null if undefined
     * @param id the ID of the mail
     * @return the mail with the given ID and null if undefined
     */
    public ScheduledMail getScheduledMail(Integer id) {
        return getByPrimaryKey(ScheduledMail.class, id);
    }


    /**
     * Returns the pending scheduled mails
     * @return the pending scheduled mails
     */
    public List<ScheduledMail> getPendingMails() {
        return em.createNamedQuery("ScheduledMail.findPendingMails", ScheduledMail.class)
                .setParameter("date", new Date())
                .getResultList();
    }


    /**
     * Called every minute to process scheduled mails
     */
    @Schedule(persistent=false, second="24", minute="*", hour = "*")
    @Lock(LockType.WRITE)
    public void sendPendingMails() {

        // Send at most MAX_MAIL_PER_MINUTE mails at a time
        List<Integer> scheduledMailIds = getPendingMails().stream()
                .limit(MAX_MAIL_PER_MINUTE)
                .map(BaseEntity::getId)
                .collect(Collectors.toList());

        if (!scheduledMailIds.isEmpty()) {

            log.info("Processing " + scheduledMailIds.size() + " pending scheduled mails");

            List<MailSenderTask> tasks = scheduledMailIds.stream()
                    .map(MailSenderTask::new)
                    .collect(Collectors.toList());

            try {
                managedExecutorService.invokeAll(tasks);
            } catch (InterruptedException e) {
                log.error("Error sending scheduled emails: " + scheduledMailIds, e);
            }
        }
    }


    /**
     * The task that actually sends the e-mail
     */
    final static class MailSenderTask implements Callable<ScheduledMail> {

        final Integer scheduledMailId;

        /** Constructor **/
        public MailSenderTask(Integer scheduledMailId) {
            this.scheduledMailId = scheduledMailId;
        }

        /** {@inheritDoc} **/
        @Override
        public ScheduledMail call() {
            try {
                MailService mailService = CdiUtils.getBean(MailService.class);
                return  mailService.sendScheduledMail(scheduledMailId);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
