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

import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;

/**
 * Interface for handling schedule mails
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class ScheduledMailService extends BaseService {

    /** The max number of mails to process at a time **/
    public static final int MAX_MAIL_SEND_NO = 100;

    @Inject
    Logger log;

    @Inject
    MailService mailService;


    /**
     * Returns the pending scheduled mails
     * @return the pending scheduled mails
     */
    public List<ScheduledMail> getPendingMails() {
        return em.createNamedQuery("ScheduledMail.findPendingMails", ScheduledMail.class)
                .setParameter("date", new Date())
                .setMaxResults(MAX_MAIL_SEND_NO)
                .getResultList();
    }


    /**
     * Called every minute to process scheduled mails
     */
    @Schedule(persistent=false, second="24", minute="*")
    public void sendPendingMails() {
        log.info("Pending mails " + getPendingMails().size());
    }
}
