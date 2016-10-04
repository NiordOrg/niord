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
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.util.Date;

/**
 * This service sets up timers to perform regular message status checks:
 * <ul>
 *     <li>Checks for published messages that have passed the publishDateTo and expires these.</li>
 *     <li>Checks for verified messages with a publishDateFrom in the past and publishes these.</li>
 * </ul>
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class ScheduledMessageService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    MessageService messageService;

    /**
     * Called every minute to update expire published messages where publishDateTo is in the past
     */
    @Schedule(persistent = false, second = "27", minute = "*", hour = "*")
    public void checkForExpirablePublishedMessages() {

        // TODO: UTC handling
        Date now = new Date();

        em.createNamedQuery("Message.findExpirableMessages", Message.class)
                .setParameter("now", now)
                .getResultList()
                .forEach(m -> {
                    try {
                        log.info("System expiring message " + m.getUid());
                        messageService.updateStatus(m.getUid(), Status.EXPIRED);
                    } catch (Exception ex) {
                        log.error("Failed expiring message " + m.getUid(), ex);
                    }
                });
    }



    /**
     * Called every minute to publish messages with a VERIFIED status and a defined publishDateFrom in the past
     */
    @Schedule(persistent = false, second = "37", minute = "*", hour = "*")
    public void checkForPublishableMessages() {

        // TODO: UTC handling
        Date now = new Date();

        em.createNamedQuery("Message.findPublishableMessages", Message.class)
                .setParameter("now", now)
                .getResultList()
                .forEach(m -> {
                    try {
                        log.info("System publishing message " + m.getUid());
                        messageService.updateStatus(m.getUid(), Status.PUBLISHED);
                    } catch (Exception ex) {
                        log.error("Failed publishing message " + m.getUid(), ex);

                        // Change status to DRAFT so we do not fail on the same message every minute
                        try {
                            log.warn("System changing status to draft of message " + m.getUid());
                            messageService.updateStatus(m.getUid(), Status.DRAFT);
                        } catch (Exception e) {
                            log.error("Failed changing status to DRAFT of message " + m.getUid(), e);
                        }
                    }
                });
    }
}

