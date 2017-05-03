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

package org.niord.core.mailinglist;

import org.niord.core.message.Message;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Can be used to evaluate a message filter such as
 * <pre>
 *     msg.promulgation('navtex').promulgate && msg.promulgation('navtex').useTransmitter('Baltico')
 * </pre>
 */
public class MessageFilter {

    private ScriptEngine jsEngine = new ScriptEngineManager()
            .getEngineByName("Nashorn");


    /** Private access constructor **/
    private MessageFilter() {
    }


    /**
     * Creates a new message filter for the given filter expression.
     * @param messageFilter the message filter expression
     * @return the instantiated message filter
     */
    public static MessageFilter getInstance(String messageFilter) throws ScriptException {

        // Considerations: Various documentation suggests that the ScriptEngine is indeed threadsafe.
        // However, shared state is not isolated, so, setting the parameters (msg) as
        // script engine state and evaluating the filter directly would not work correctly.
        // Instead, we wrap the filter in a function and call that function.

        String jsFilter = "function matchesMessage(msg) { return " + messageFilter + "; }";
        MessageFilter filter = new MessageFilter();
        filter.jsEngine.eval(jsFilter);
        return filter;
    }


    /**
     * Checks if the message matches the message filter
     * @param message the message to check
     * @return if the message matches the message filter
     */
    public boolean matches(Message message) {
        try {
            Invocable filterFunction = (Invocable)jsEngine;
            return  (Boolean) filterFunction.invokeFunction("matchesMessage", message);
        } catch (Exception e) {
            return false;
        }
    }
}
