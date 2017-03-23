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

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.Test;
import org.niord.core.aton.LightCharacterParser;
import org.niord.core.aton.LightCharacterService;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unit tests for the light character parser service
 */
public class LightCharacterTest  {

    private List<String> lights = Arrays.asList(
            "Fl(2) 20m 10s 12M ",
            "Gp.L.Fl(2+1)G. 5s",
            "Mo(U)",
            "Iso G 4s",
            "Gp Oc(3) W 10s 15m 10M",
            "Alt R.W.G",
            "VQ(6)+LFl"
        );

    LightCharacterParser parser = LightCharacterParser.getInstance();


    @Test
    public void normalizeLightCharacterTest() throws Exception {
        lights.forEach(l -> System.out.printf("Normalize: %s  ->  %s%n", l, parser.normalize(l)));
    }


    @Test
    public void parseLightCharacterTest() throws Exception {
        for (String l : lights) {
            System.out.printf("Parse: %s  ->  %s%n", l, parser.parse(l));
        }
    }

    @Test
    public void parseLightCharacterFormatting() throws Exception {
        Configuration cfg = new Configuration(Configuration.getVersion());
        cfg.setLocalizedLookup(true);
        cfg.setClassForTemplateLoading(LightCharacterTest.class, "/templates/aton/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.US);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        Template template = cfg.getTemplate("light-character.ftl");
        for (String l : lights) {
            Map<String, Object> data = new HashMap<>();
            data.put("lightModel", parser.parse(l));
            StringWriter result = new StringWriter();
            template.process(data, result);
            System.out.println("*** " + l + "\t\t->\t\t" + LightCharacterService.trimResult(result.toString()));
        }
    }

}
