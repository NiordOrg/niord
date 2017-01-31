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

import org.niord.core.model.BaseEntity;
import org.niord.core.service.BaseService;
import org.niord.core.util.CdiUtils;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
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
