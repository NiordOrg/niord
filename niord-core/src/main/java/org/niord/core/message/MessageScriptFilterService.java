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

import io.quarkus.arc.Lock;
import org.apache.commons.lang.StringUtils;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains and caches a list of MessageScriptFilterEvaluator used for evaluating message inclusion
 * based on a JavaScript filter.
 * <p>
 * Example:
 * "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) && msg.status == Status.PUBLISHED"
 */
@ApplicationScoped
@Lock(Lock.Type.READ)
@SuppressWarnings("unused")
public class MessageScriptFilterService extends BaseService {

    @Inject
    private Logger log;

    // Cache of MessageTagFilterEvaluator
    final Map<String, MessageScriptFilterEvaluator> filters = new ConcurrentHashMap<>();


    /**
     * Check if the message is included in the filter or not
     * @param filter the filter
     * @param message the message to check
     * @return if the message is included in the filter or not
     */
    public boolean includeMessage(String filter, Message message, Object data) {

        filter = StringUtils.defaultIfBlank(filter, "");

        // Look up or create the evaluator for the filter
        MessageScriptFilterEvaluator evaluator = filters.get(filter);
        if (evaluator == null) {
            synchronized (filters) {
                evaluator  = filters.get(filter);
                if (evaluator == null) {
                    try {
                        evaluator = new MessageScriptFilterEvaluator(filter);
                        log.info("instantiated message script filter " + filter);
                    } catch (Exception ex) {
                        evaluator = MessageScriptFilterEvaluator.EXCLUDE_ALL;
                        log.error("Error instantiating message script filter " + filter, ex);
                    }
                    filters.put(filter, evaluator);
                }
            }
        }

        return evaluator.includeMessage(message, data);
    }

}

