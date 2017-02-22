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

package org.niord.core;


import org.junit.Assert;
import org.junit.Test;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.PromulgationTypeVo;
import org.niord.core.promulgation.vo.TwitterMessagePromulgationVo;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

/**
 * Various message template tests
 */
public class MessageTemplateTest {

    @Test
    public void testJavaScriptFieldUpdate() throws Exception {

        ScriptEngine jsEngine = new ScriptEngineManager()
                .getEngineByName("Nashorn");

        SystemMessageVo msg = new SystemMessageVo();

        PromulgationTypeVo type = new PromulgationTypeVo();
        type.setTypeId("twitter");

        TwitterMessagePromulgationVo twitter = new TwitterMessagePromulgationVo(type);
        twitter.setTweet("tweet start ");
        msg.checkCreatePromulgations().add(twitter);


        // Make the entity manager available to the script as "em"
        Bindings bindings = new SimpleBindings();
        bindings.put("msg", msg);
        bindings.put("result", "tweet end");
        jsEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        String script = "msg.promulgation('twitter').tweet += result;";
        jsEngine.eval(script);

        Assert.assertEquals("tweet start tweet end", twitter.getTweet());
    }


}
