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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.TwitterMessagePromulgationVo;

import java.io.IOException;

/**
 * Promulgation tests
 */
public class PromulgationTest {

    @Test
    public void testJsonDeserialization() throws IOException {

        TwitterMessagePromulgationVo data = new TwitterMessagePromulgationVo();
        data.setTweet("Stein Bagger som president!");

        ObjectMapper mapper = new ObjectMapper();
        //mapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, "@type");
        String json = mapper.writeValueAsString(data);

        System.out.println("JSON " + json);

        BaseMessagePromulgationVo result = mapper.readValue(json, BaseMessagePromulgationVo.class);

        Assert.assertEquals(TwitterMessagePromulgationVo.class, result.getClass());
    }

}
