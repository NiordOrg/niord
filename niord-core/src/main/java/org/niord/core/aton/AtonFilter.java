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

package org.niord.core.aton;

import org.niord.core.aton.vo.AtonNodeVo;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.List;

/**
 * Can be used to evaluate an AtoN filter such as "aton.kv('seamark:type', 'light.*')"
 */
public class AtonFilter {

    private ScriptEngine jsEngine = new ScriptEngineManager()
            .getEngineByName("Nashorn");


    /** Private access constructor **/
    private AtonFilter() {
    }


    /**
     * Creates a new AtoN filter for the given filter expression.
     * Example: "aton.kv('seamark:type', 'light.*')"
     * @param atonFilter the AtoN filter expression
     * @return the instantiated AtoN filter
     */
    public static AtonFilter getInstance(String atonFilter) throws ScriptException {

        // Considerations: Various documentation suggests that the ScriptEngine is indeed threadsafe.
        // However, shared state is not isolated, so, setting the parameters (msg) as
        // script engine state and evaluating the filter directly would not work correctly.
        // Instead, we wrap the filter in a function and call that function.

        String jsFilter = "function matchesAton(aton) { return " + atonFilter + "; }";
        AtonFilter filter = new AtonFilter();
        filter.jsEngine.eval(jsFilter);
        return filter;
    }


    /**
     * Checks if the AtoN matches the AtoN filter
     * @param aton the AtoN to check
     * @return if the AtoN matches the AtoN filter
     */
    public boolean matches(AtonNodeVo aton) {
        try {
            Invocable filterFunction = (Invocable)jsEngine;
            return  (Boolean) filterFunction.invokeFunction("matchesAton", aton);
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Checks if all AtoNs match the AtoN filter
     * @param atons the AtoNs to check
     * @return if all AtoNs match the AtoN filter
     */
    public boolean matches(List<AtonNodeVo> atons) {
        return atons.stream()
                .allMatch(this::matches);
    }
}
