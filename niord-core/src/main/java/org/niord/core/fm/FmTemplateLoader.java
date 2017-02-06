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

package org.niord.core.fm;

import freemarker.cache.TemplateLoader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.niord.core.util.CdiUtils;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A version of the Freemarker StringTemplateLoader that loads templates lazily from the FmTemplate database table.
 */
public class FmTemplateLoader implements TemplateLoader {

    // All templates that have actually been loaded during execution
    private final Map<String, FmTemplate> templates = new HashMap<>();

    // All existing paths in the FmTemplate table
    private final Set<String> existingTemplatePaths;

    // All checked paths that do not exist in the FmTemplate table - used when loading from class-path
    private final Set<String> nonExistingTemplatePaths = new HashSet<>();

    private final FmTemplateService templateService;
    private boolean loadFromClassPath;


    /** Constructor **/
    public FmTemplateLoader(FmTemplateService templateService, boolean loadFromClassPath) {
        this.templateService = templateService;
        this.existingTemplatePaths = templateService.findAllTemplatePaths();
        this.loadFromClassPath = loadFromClassPath;
    }


    /** {@inheritDoc} **/
    @Override
    public void closeTemplateSource(Object template) {
    }


    /** {@inheritDoc} **/
    @Override
    public FmTemplate findTemplateSource(String path) {

        if (existingTemplatePaths.contains(path)) {
            if (!templates.containsKey(path)) {
                templates.put(path, templateService.findByPath(path));
            }
        } else if (loadFromClassPath && !nonExistingTemplatePaths.contains(path)) {
            checkLoadFromClassPath(path);
        }
        return templates.get(path);
    }


    /**
     * Checks if the template exists in the class-path and loads it if it does
     * @param path the path to check
     */
    private void checkLoadFromClassPath(String path) {

        String resourcePath = path;
        if (!resourcePath.startsWith("/")) {
            resourcePath = "/" + resourcePath;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String template = null;
        try {
            template = IOUtils.toString(getClass().getResourceAsStream(resourcePath),"UTF-8");
        } catch (Exception ignored) {
        }

        if (StringUtils.isNotBlank(template)) {
            FmTemplate fmTemplate = new FmTemplate();
            fmTemplate.setPath(path);
            fmTemplate.setTemplate(template);
            try {
                // NB: we cannot use templateService.createTemplate() since this method may be
                // called outside the transaction where the template loader was instantiated
                FmTemplateService ts = CdiUtils.getBean(FmTemplateService.class);
                ts.createTemplate(fmTemplate);
                templates.put(path, fmTemplate);
                existingTemplatePaths.add(path);
            } catch (Exception ignored) {
            }
        } else {
            nonExistingTemplatePaths.add(path);
        }
    }


    /** {@inheritDoc} **/
    @Override
    public long getLastModified(Object template) {
        return ((FmTemplate)template).getUpdated().getTime();
    }


    /** {@inheritDoc} **/
    @Override
    public Reader getReader(Object temlplate, String encoding) {
        return new StringReader(((FmTemplate)temlplate).getTemplate());
    }
}
