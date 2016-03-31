/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.settings.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Objects;

/**
 * Filters settings that need to be a added or updated
 */
@Named
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
            // Update the setting value
            getLog().info("Update setting value for " + setting.getKey());
            return setting;
        }

        return null;
    }
}
