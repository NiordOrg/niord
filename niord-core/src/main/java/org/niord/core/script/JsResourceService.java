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

import jdk.nashorn.api.scripting.JSObject;
import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageService.AdjustmentType;
import org.niord.core.service.BaseService;
import org.niord.core.util.CdiUtils;
import org.niord.model.message.MainType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Main interface for accessing and processing persisted JavaScript resource.
 * <p>
 * The JavaScripts will support loading of other JavaScripts using the <code>load()</code> command
 * and prefixing the path with the "niord" scheme. Example:
 * <pre>
 *     load('niord:templates/tmpl/common.js')
 * </pre>
 * Inspiration from https://bugs.openjdk.java.net/secure/attachment/54881/LoaderTest.java
 */
@Stateless
public class JsResourceService extends BaseService {

    private static final Class<?>[] SCRIPT_CLASSES  = {
            CdiUtils.class, Status.class, Type.class, MainType.class, MessagePartType.class, AdjustmentType.class
    };
    public static final String NIORD_LOAD_SCHEME    = "niord:";
    public static final String EM_PROPERTY          = "em";
    public static final String MSG_SERVICE_PROPERTY = "messageService";

    @Inject
    ScriptResourceService resourceService;

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;

    @Inject
    Logger log;


    /************************************/
    /** JavaScript resource loading    **/
    /************************************/


    /**
     * Fetches the script resource with the given path
     * @param path the path of the script to fetch
     * @return the script resource or null if not found
     **/
    public ScriptResource findScriptResource(String path) {

        ScriptResource scriptResource = resourceService.findByPath(path);
        if (scriptResource == null) {
            scriptResource = checkLoadScriptFromClassPath(path);
        }
        return scriptResource;
    }


    /**
     * Checks if the template exists in the class-path and loads it if it does
     * @param path the path of the script to fetch
     * @return the newly persisted script resource or null if not found
     */
    private ScriptResource checkLoadScriptFromClassPath(String path) {

        ScriptResource scriptResource = resourceService.readScriptResourceFromClassPath(path);
        if (scriptResource != null) {
            try {
                return resourceService.createScriptResource(scriptResource);
            } catch (Exception ignored) {
            }
        }
        return null;
    }


    /************************************/
    /** JavaScript resource execution  **/
    /************************************/


    /**
     * Create a new JavaScript resource builder.
     * The builder must be populated and processed within the current transaction.
     * @return a new JavaScript resource builder
     */
    public JsResourceBuilder newJsResourceBuilder() {
        return new JsResourceBuilder(this);
    }


    /**
     * Evaluates a JavaScript resource based on the given resource builder
     * @param resourceBuilder the resource builder to construct the JavaScript resource from
     * @return the JavaScript resource
     */
    private Object evaluate(JsResourceBuilder resourceBuilder) throws Exception {

        // Look up the requested JavaScript
        ScriptResource script = findScriptResource(resourceBuilder.getResourcePath());
        if (script == null || script.getType() != ScriptResource.Type.JS) {
            throw new IllegalArgumentException("Invalid JavaScript resource path: " + resourceBuilder.getResourcePath());
        }


        // Construct and configure a Nashorn JavaScript Engine.
        // The JavaScripts will support loading of other JavaScripts using the "load()" command
        // and prefixing the path with the "niord" scheme. Example: load('niord:templates/tmpl/common.js')
        // Inspiration from https://bugs.openjdk.java.net/secure/attachment/54881/LoaderTest.java
        try {
            ScriptEngine jsEngine = new ScriptEngineManager()
                    .getEngineByName("Nashorn");

            // Make the entity manager available to the script as "em"
            Bindings bindings = new SimpleBindings();
            bindings.put(EM_PROPERTY, em);
            bindings.put(MSG_SERVICE_PROPERTY, messageService);

            // Add other bindings from the builder data map
            resourceBuilder.getData().entrySet().forEach(e -> bindings.put(e.getKey(), e.getValue()));
            jsEngine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE); // NB: Custom load only works with global scope!

            // Get original load function
            final JSObject origLoadFn = (JSObject)jsEngine.get("load");

            // Get global. Not really necessary as we could use null too, just for completeness.
            final JSObject thisRef = (JSObject)jsEngine.eval("(function() { return this; })()");

            // Define a new "load" function
            final Function<Object, Object> newLoadFn = (source) -> {
                if (source instanceof String) {
                    final String strSource = (String)source;
                    if (strSource.startsWith(NIORD_LOAD_SCHEME)) {
                        // handle "niord:" scheme
                        String path = strSource.substring(NIORD_LOAD_SCHEME.length());

                        Object customSource = createCustomSource(path);
                        if (customSource != null) {
                            return origLoadFn.call(thisRef, customSource);
                        }
                    }
                }
                // Fall back to original load for everything else
                return origLoadFn.call(thisRef, source);
            };

            // Replace built-in load with our load
            jsEngine.put("load", newLoadFn);

            // Evaluate the JavaScript
            String javaScript = updateScript(script.getContent());
            return jsEngine.eval(javaScript);

        } catch (Exception e) {
            log.error("Error executing script:\n" + resourceBuilder.getResourcePath(), e);
            throw new Exception("Error executing script:\n" + resourceBuilder.getResourcePath(), e);
        }
    }


    /**
     * A custom source must define a "name" and a "script" property
     */
    public Object createCustomSource(final String path) {
        ScriptResource script = findScriptResource(path);
        if (script != null && script.getType() == ScriptResource.Type.JS) {
            final Map<String, String> sourceMap = new HashMap<>();
            sourceMap.put("name", path);
            sourceMap.put("script", script.getContent());
            return sourceMap;
        }
        return null;
    }


    /**
     * Appends a prefix to a the JavaScript with predefined variables.
     * Example "var CdiUtils = Java.type('org.niord.core.util.CdiUtils');"
     */
    public String updateScript(String script) {

        String prefix = Arrays.stream(SCRIPT_CLASSES)
                .map(type -> String.format("var %s = Java.type('%s');%n", type.getSimpleName(), type.getCanonicalName()))
                .collect(Collectors.joining());

        // Add script imports
        return prefix + script;
    }

    /************************************/
    /** JavaScript Resource Builder    **/
    /************************************/


    /**
     * Used by the client for building a new JavaScript resource.
     *
     * Initialize the builder by calling JsResourceService.newJsResourceBuilder()
     */
    @SuppressWarnings("unused")
    public static class JsResourceBuilder {

        private static Logger log = LoggerFactory.getLogger(JsResourceBuilder.class);

        String resourcePath;
        Map<String, Object> data = new HashMap<>();
        JsResourceService resourceService;


        /**
         * Should only be initialized from the JsResourceService.newJsResourceBuilder() call
         */
        private JsResourceBuilder(JsResourceService resourceService) {
            this.resourceService = Objects.requireNonNull(resourceService);
        }


        /**
         * Evaluates the JavaScript resource defined by the builder
         */
        public Object evaluate() throws Exception {

            // Check parameters
            if (StringUtils.isBlank(resourcePath)) {
                throw new IllegalArgumentException("Template path must be specified");
            }

            if (data == null) {
                data = new HashMap<>();
            }

            // Evaluate the JavaScript resource
            return resourceService.evaluate(this);
        }


        /*****************************************/
        /** Method-chaining Getters and Setters **/
        /*****************************************/

        public String getResourcePath() {
            return resourcePath;
        }

        public JsResourceBuilder resourcePath(String resourcePath) {
            this.resourcePath = resourcePath;
            return this;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public JsResourceBuilder data(Map<String, Object> data) {
            this.data.putAll(data);
            return this;
        }

        public JsResourceBuilder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }
    }
}
