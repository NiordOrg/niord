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

package org.niord.core.script;

import freemarker.cache.TemplateLoader;
import org.niord.core.util.CdiUtils;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A version of the Freemarker StringTemplateLoader that loads templates lazily from the ScriptResource database table.
 */
public class FmTemplateLoader implements TemplateLoader {

    // All templates that have actually been loaded during execution
    private final Map<String, ScriptResource> templates = new HashMap<>();

    // All existing paths in the ScriptResource table
    private final Set<String> existingTemplatePaths;

    // All checked paths that do not exist in the ScriptResource table - used when loading from class-path
    private final Set<String> nonExistingTemplatePaths = new HashSet<>();

    private final ScriptResourceService resourceService;
    private boolean loadFromClassPath;


    /** Constructor **/
    public FmTemplateLoader(ScriptResourceService resourceService, boolean loadFromClassPath) {
        this.resourceService = resourceService;
        this.existingTemplatePaths = resourceService.findAllScriptResourcePaths();
        this.loadFromClassPath = loadFromClassPath;
    }


    /** {@inheritDoc} **/
    @Override
    public void closeTemplateSource(Object template) {
    }


    /** {@inheritDoc} **/
    @Override
    public ScriptResource findTemplateSource(String path) {

        if (existingTemplatePaths.contains(path)) {
            if (!templates.containsKey(path)) {
                templates.put(path, resourceService.findByPath(path));
            }
        } else if (loadFromClassPath && !nonExistingTemplatePaths.contains(path)) {
            checkLoadTemplateFromClassPath(path);
        }
        return templates.get(path);
    }


    /**
     * Checks if the template exists in the class-path and loads it if it does
     * @param path the path to check
     */
    private void checkLoadTemplateFromClassPath(String path) {

        ScriptResource scriptResource = resourceService.readScriptResourceFromClassPath(path);
        if (scriptResource != null) {
            try {
                // NB: we cannot use resourceService.createScriptResource() since this method may be
                // called outside the transaction where the template loader was instantiated
                ScriptResourceService rs = CdiUtils.getBean(ScriptResourceService.class);
                rs.createScriptResource(scriptResource);
                templates.put(path, scriptResource);
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
        return ((ScriptResource)template).getUpdated().getTime();
    }


    /** {@inheritDoc} **/
    @Override
    public Reader getReader(Object temlplate, String encoding) {
        return new StringReader(((ScriptResource)temlplate).getContent());
    }
}
