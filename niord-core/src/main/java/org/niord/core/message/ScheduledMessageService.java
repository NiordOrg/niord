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

import io.quarkus.scheduler.Scheduled;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.service.BaseService;
import org.niord.core.util.TimeUtils;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.message.MessageSearchParams.DateType.PUBLISH_FROM_DATE;
import static org.niord.core.message.MessageSearchParams.DateType.PUBLISH_TO_DATE;
import static org.niord.model.message.Status.PUBLISHED;
import static org.niord.model.message.Status.VERIFIED;

/**
 * This service sets up timers to perform regular message status checks:
 * <ul>
 *     <li>Checks for published messages that have passed the publishDateTo and expires these.</li>
 *     <li>Checks for verified messages with a publishDateFrom in the past and publishes these.</li>
 * </ul>
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class ScheduledMessageService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    DomainService domainService;

    @Inject
    MessageService messageService;


    /**
     * Called every minute to update expire published messages where publishDateTo is in the past
     */
    @Scheduled(cron="28 * * * * ?")
    void checkForExpirablePublishedMessages() {

        // We want to treat messages with timestamps within the same minute equally, so, reset the seconds
        Date now = TimeUtils.resetSeconds(new Date());

        // We make the search for expired messages domain by domain, in order to use domain sort order
        domainService.getDomains().stream()
            .filter(domain ->  !domain.getMessageSeries().isEmpty())
            .forEach(domain -> searchMessages(domain, PUBLISHED, PUBLISH_TO_DATE, null, now)
                   .forEach(m -> {
                       try {
                           log.info("System expiring message " + m.getUid());
                           messageService.updateStatus(m.getUid(), Status.EXPIRED);
                       } catch (Exception ex) {
                           log.error("Failed expiring message " + m.getUid(), ex);
                       }
                   }));
    }



    /**
     * Called every minute to publish messages with a VERIFIED status and a defined publishDateFrom in the past
     */
    @Scheduled(cron="37 * * * * ?")
    void checkForPublishableMessages() {

        // We want to treat messages with timestamps within the same minute equally, so, reset the seconds
        Date now = TimeUtils.resetSeconds(new Date());

        // We make the search for expired messages domain by domain, in order to use domain sort order
        domainService.getDomains().stream()
                .filter(domain ->  !domain.getMessageSeries().isEmpty())
                .forEach(domain -> searchMessages(domain, VERIFIED, PUBLISH_FROM_DATE, null, now)
                        .forEach(m -> {
                            try {
                                log.info("System publishing message " + m.getUid());
                                messageService.updateStatus(m.getUid(), PUBLISHED);
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
                        }));
    }


    /**
     * Searches for messages with the given status and where the publish dates are within
     * the given interval.
     * Sort the messages by domain sort order.
     * @param domain the domain
     * @param status the message status
     * @param dateType the type of date interval to search by
     * @param from the publish-from date
     * @param to the publish-to date
     * @return the messages matching the search criteria
     */
    private List<Message> searchMessages(Domain domain, Status status, MessageSearchParams.DateType dateType, Date from, Date to) {
        Set<String> seriesIds = domain.getMessageSeries().stream()
                .map(MessageSeries::getSeriesId)
                .collect(Collectors.toSet());

        MessageSearchParams params = new MessageSearchParams()
                .seriesIds(seriesIds)
                .statuses(status)
                .dateType(dateType)
                .from(from)
                .to(to)
                .checkSortByDomain(domain);

        return messageService.search(params).getData();
    }

}

