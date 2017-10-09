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

import org.niord.core.model.BaseEntity;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The sole purpose of this class is to periodically check and execute pending scheduled mailing list triggers.
 * <p>
 * The code is executed in a separate class (rather than from the {@code MailingListExecutionService})
 * to better control transaction handling.<br>
 * With this model, if one mailing list execution fails, it will not roll back a single transaction
 * for all mailing lists.
 */
@Stateless
@SuppressWarnings("unused")
public class MailingListSchedulerService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    MailingListService mailingListService;

    @Inject
    MailingListExecutionService mailingListExecutionService;

    /**
     * Checks every minute if there are pending scheduled mailing list triggers.
     */
    @Schedule(persistent = false, second = "37", minute = "*/1", hour = "*/1")
    public void checkExecuteScheduledTriggers() {

        long t0 = System.currentTimeMillis();

        List<Integer> pendingTriggers = mailingListService.findPendingScheduledTriggers().stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toList());

        if (!pendingTriggers.isEmpty()) {
            log.debug("Processing " + pendingTriggers.size() + " scheduled triggers");
            for (Integer triggerId : pendingTriggers) {
                try {
                    // NB: These functions each require a new transaction
                    mailingListExecutionService.executeScheduledTrigger(triggerId);
                    mailingListExecutionService.computeNextScheduledExecution(triggerId);
                } catch (Exception e) {
                    log.error("Failed executing scheduled mailing list trigger " + triggerId + ": " + e);
                }
            }
            log.debug(String.format("Executed %d scheduled mailing list triggers in %d ms",
                    pendingTriggers.size(),
                    System.currentTimeMillis() - t0));
        }
    }
}
