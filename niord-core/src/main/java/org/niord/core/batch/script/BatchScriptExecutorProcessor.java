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

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.logging.Level;

/**
 * Execute the script file
 */
@Named
public class BatchScriptExecutorProcessor extends AbstractItemHandler {

    @Inject
    EntityManager em;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        String script = (String) item;
        long t0 = System.currentTimeMillis();

        try {
            ScriptEngine jsEngine = new ScriptEngineManager()
                    .getEngineByName("Nashorn");

            Bindings bindings = new SimpleBindings();
            bindings.put("em", em);
            jsEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);


            Object result = jsEngine.eval(script);

            getLog().info("Executed script in " + (System.currentTimeMillis() - t0) + " ms:\n" + script);

            return result;
        } catch (Exception e) {
            getLog().log(Level.SEVERE, "Error executing script:\n" + script, e);
            throw new Exception("Error executing script:\n" + script, e);
        }
    }
}
