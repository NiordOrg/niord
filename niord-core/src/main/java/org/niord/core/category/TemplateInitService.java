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

package org.niord.core.category;

import io.quarkus.runtime.StartupEvent;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Arrays;

import static org.niord.core.category.StandardParamType.STANDARD_PARAM_TYPES;

/**
 * Checks that standard parameter types have been defined
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class TemplateInitService extends BaseService {

    @Inject
    Logger log;

    @Inject
    TemplateExecutionService templateExecutionService;


    /** Called when the web application boots up **/
    @Transactional
    void init(@Observes StartupEvent ev) {
        Arrays.stream(STANDARD_PARAM_TYPES)
                .forEach(this::checkCreateStandardParamType);
    }


    /** Creates the standard parameter type with the given name if it does not exist **/
    private void checkCreateStandardParamType(String name) {

        try {
            if (templateExecutionService.getParamTypeByName(name) == null) {
                StandardParamType paramType = new StandardParamType(name);
                saveEntity(paramType);
                log.info("Created standard parameter type " + name);
            }
        } catch (Exception e) {
            log.error("Error creating parameter type " + name, e);
        }
    }

}
