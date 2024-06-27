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
package org.niord.s124;

import java.util.List;

import org.junit.Test;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.s124madame.stuff.MyMapper;
import org.niord.s124madame.stuff.S124DatasetInfo;
import org.niord.s124madame.stuff.S124Utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import _int.iho.s124._1.Dataset;

public class S124NewTest {

    @Test
    public void testGenerateS124() throws Exception {

        String language = "en";

        ObjectMapper objectMapper = new ObjectMapper();
        SystemMessageVo message = objectMapper.readValue(getClass().getResource("/message.json"), SystemMessageVo.class);

        message.sort(language);

        Message msg = new Message(message);

        Dataset dataset = MyMapper.map(new S124DatasetInfo("1", "DMA", List.of(msg)), msg);

        String result = S124Utils.marshalS124(dataset);

        System.out.println(result);
    }
}
