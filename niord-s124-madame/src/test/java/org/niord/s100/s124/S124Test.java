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
package org.niord.s100.s124;


import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

public class S124Test {


    public void testGenerateS124() throws Exception {

        String language = "en";

        ObjectMapper objectMapper = new ObjectMapper();
        SystemMessageVo message = objectMapper.readValue(getClass().getResource("/message.json"), SystemMessageVo.class);

        message.sort(language);

        Map<String, Object> data = new HashMap<>();
        data.put("msg", message);

        data.put("language", language);

        double[] bbox = GeoJsonUtils.computeBBox(new Message(message).toGeoJson());
        if (bbox != null) {
            data.put("bbox", bbox);
        }

        data.put("references", new ArrayList<>());

        System.out.println("Message loaded: " + message.getId());

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setTemplateLoader(new ClassTemplateLoader(getClass(), "/templates/gml"));

        StringWriter result = new StringWriter();
        Template fmTemplate = cfg.getTemplate("generate-s124.ftl");


        fmTemplate.process(data, result);

        String pp = result.toString();// S124RestService.prettyPrint(result.toString());
        pp = pp.replaceAll("(?m)^[ \t]*\r?\n", "");
        System.out.printf(pp);
    }
}

