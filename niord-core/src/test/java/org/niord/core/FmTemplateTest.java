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

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.junit.Assert;
import org.junit.Test;
import org.niord.core.fm.vo.FmTemplateVo;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Freemarker template test
 */
public class FmTemplateTest {

    @Test
    public void testFreemarkerTemplateInclusion() throws IOException, TemplateException {

        List<FmTemplateVo> templates = Arrays.asList(
                createTemplate("path/to/test.ftl", "<#include \"../include.ftl\"> mum!"),
                createTemplate("path/include.ftl", "Hello")
        );

        StringTemplateLoader templateLoader = new StringTemplateLoader();
        templates.forEach(t -> templateLoader.putTemplate(t.getPath(), t.getTemplate()));

        Configuration cfg = new Configuration(Configuration.getVersion());
        cfg.setTemplateLoader(templateLoader);

        StringWriter result = new StringWriter();
        Template fmTemplate = cfg.getTemplate("path/to/test.ftl");

        fmTemplate.process(new HashMap<String, Object>(), result);

        Assert.assertEquals("Hello mum!", result.toString());
    }


    private FmTemplateVo createTemplate(String path, String template) {
        FmTemplateVo t = new FmTemplateVo();
        t.setPath(path);
        t.setTemplate(template);
        return t;
    }

}
