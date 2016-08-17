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
        cfg = new Configuration(Configuration.getVersion());
        cfg.setLocalizedLookup(true);
        cfg.setClassForTemplateLoading(getClass(), "/");
        cfg.setTemplateUpdateDelayMilliseconds(0);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
        cfg.setObjectWrapper(new NiordAppObjectWrapper(cfg.getIncompatibleImprovements()));
    }

    /**
     * Returns a reference to the Freemarker configuration
     * @return a reference to the Freemarker configuration
     */
    public Configuration getConfiguration() {
        return cfg;
    }
}
