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
package org.niord.core.batch.script;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.settings.SettingsService;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.logging.Level;

/**
 * Updates the values of the settings
 */
@Dependent
@Named("batchScriptExecutorWriter")
public class BatchScriptExecutorWriter extends AbstractItemHandler {

    @Inject
    SettingsService settingsService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        for (Object i : items) {
            getLog().log(Level.INFO, "Result: " + i);
        }
    }
}
