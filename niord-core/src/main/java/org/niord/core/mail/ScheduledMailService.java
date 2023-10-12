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

import io.quarkus.arc.Lock;
import io.quarkus.scheduler.Scheduled;
import org.apache.commons.lang.StringUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.model.BaseEntity;
import org.niord.core.service.BaseService;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.util.TimeUtils;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.niord.core.settings.Setting.Type.Integer;

/**
 * Interface for handling schedule mails
 */
@ApplicationScoped
@Lock(Lock.Type.READ)
@SuppressWarnings("unused")
public class ScheduledMailService extends BaseService {

    @Inject
    Logger log;

    @Inject
    MailService mailService;

    @Inject
    @Setting(value = "mailMaxPerMinute", defaultValue = "10", type = Integer,
            description = "The max number of mails to send per minute")
    Integer maxMailsPerMinute;


    @Inject
    @Setting(value = "mailDeleteAfterDays", defaultValue = "30", type = Integer,
            description = "Delete scheduled mails older than the given value. A non-positive value means never.")
    Integer mailDeleteAfterDays;


    /**
     * NB: Niord defines its own managed executor service to limit the number of threads,
     * and thus, the number of concurrent SMTP connections.
     *
     * Since moving to quarkus, we can use the default managed executor
     */
    @Inject
    ManagedExecutor managedExecutor;

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
    @Scheduled(cron="24 * * * * ?")
    @Lock(Lock.Type.WRITE)
    @Transactional
    void sendPendingMails() {

        // Send at most "maxMailsPerMinute" mails at a time
        List<Integer> scheduledMailIds = getPendingMails().stream()
                .limit(maxMailsPerMinute)
                .map(BaseEntity::getId)
                .collect(Collectors.toList());

        if (!scheduledMailIds.isEmpty()) {

            log.info("Processing " + scheduledMailIds.size() + " pending scheduled mails");

            List<MailSenderTask> tasks = scheduledMailIds.stream()
                    .map(id -> new MailSenderTask(mailService, id))
                    .collect(Collectors.toList());

            try {
                managedExecutor.invokeAll(tasks);
            } catch (InterruptedException e) {
                log.error("Error sending scheduled emails: " + scheduledMailIds, e);
            }
        }
    }


    /**
     * Called every day to delete old scheduled mails.
     *
     * Note to self: It would have been faster to execute a "delete from ScheduledMail where..." statement.
     * However, this will fail because of a missing mail - recipient "delete on cascade" FK constraint.
     * Using JPAs CascadeType.ALL for the relation does NOT work in this case.
     */
    @Scheduled(cron="48 28 5 * * ?")
    @Lock(Lock.Type.WRITE)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void deleteExpiredMails() {

        // If expiryDate is 0 (actually, non-positive), never delete mails
        if (mailDeleteAfterDays <= 0) {
            return;
        }

        Date expiryDate = TimeUtils.add(new Date(), Calendar.DATE, -mailDeleteAfterDays);

        List<Integer> ids = em.createNamedQuery("ScheduledMail.findExpiredMails", Integer.class)
                .setParameter("expiryDate", expiryDate)
                .getResultList();

        if (!ids.isEmpty()) {
            long t0 = System.currentTimeMillis();
            try {
                for (int x = 0; x < ids.size(); x++) {
                    ScheduledMail mail = getScheduledMail(ids.get(x));
                    if (mail != null) {
                        em.remove(mail);
                        if ((x % 50) == 0) {
                            em.flush();
                        }
                    }
                }

                log.info("Deleted " + ids.size() + " scheduled mails older than " + expiryDate
                            + " in " + (System.currentTimeMillis() - t0) + " ms");

            } catch (Exception e) {
                log.error("Failed deleting scheduled mails older than " + expiryDate);
            }
        }
    }


    /**
     * The task that actually sends the e-mail
     */
    final static class MailSenderTask implements Callable<ScheduledMail> {

        final Integer scheduledMailId;
        final MailService mailService;

        /** Constructor **/
        public MailSenderTask(MailService mailService, Integer scheduledMailId) {
            this.mailService = mailService;
            this.scheduledMailId = scheduledMailId;
        }

        /** {@inheritDoc} **/
        @Transactional
        @Override
        public ScheduledMail call() {
            try {
                //MailService mailService = CdiUtils.getBean(MailService.class);
                return  mailService.sendScheduledMail(scheduledMailId);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
