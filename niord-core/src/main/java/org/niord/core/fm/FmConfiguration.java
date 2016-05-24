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
package org.niord.core.fm;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

/**
 * A singleton wrapper of a Freemarker configuration
 */
@Singleton
public class FmConfiguration {

    Configuration cfg;

    /**
     * Initializes the Freemarker configuration
     */
    @PostConstruct
    public void init() {
        cfg = new Configuration(Configuration.VERSION_2_3_23);
        cfg.setLocalizedLookup(true);
        cfg.setClassForTemplateLoading(getClass(), "/");
        cfg.setTemplateUpdateDelayMilliseconds(0);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
    }

    /**
     * Returns a reference to the Freemarker configuration
     * @return a reference to the Freemarker configuration
     */
    public Configuration getConfiguration() {
        return cfg;
    }
}
