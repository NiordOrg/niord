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
package org.niord.core.settings.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Objects;

/**
 * Filters settings that need to be a added or updated
 */
@Dependent
@Named("batchSettingsImportProcessor")
public class BatchSettingsImportProcessor extends AbstractItemHandler {

    @Inject
    SettingsService settingsService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        Setting setting = (Setting) item;

        // Look up original. Will actually create any new setting
        Object value = settingsService.get(setting);

        if (!Objects.equals(value, setting.getValue())) {

            // When you create exports from the Settings Admin page, passwords will have no value.
            // So, if this setting is an empty password, ignore it.
            if (setting.getType() == Setting.Type.Password && setting.getValue() == null) {
                return null;
            }

            // Update the setting value
            getLog().info("Update setting value for " + setting.getKey());
            return setting;
        }

        return null;
    }
}
