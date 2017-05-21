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
package org.niord.web.api;

import io.swagger.jaxrs.config.BeanConfig;
import org.niord.core.NiordApp;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

/**
 * Bootstraps swagger.
 * Link https://github.com/swagger-api/swagger-core/wiki/Swagger-Core-RESTEasy-2.X-Project-Setup
 * <p>
 * This will make the swagger definition available at "/rest/swagger.json"
 */
@WebServlet(name = "SwaggerConfigServlet", loadOnStartup = 1)
public class SwaggerConfigServlet extends HttpServlet {

    @Inject
    Logger log;

    @Inject
    NiordApp app;

    /** Initialize Swagger */
    @Override
    public void init(ServletConfig servletConfig) {
        try {
            super.init(servletConfig);
            BeanConfig beanConfig = new BeanConfig();
            beanConfig.setVersion("1.0.0");
            beanConfig.setBasePath("/rest");
            beanConfig.setResourcePackage("org.niord.web.api,org.niord.model.vo,org.niord.model.vo.geojson,org.niord.s124");
            beanConfig.setScan(true);
            log.info("Initialized Swagger: " + app.getBaseUri() +  "/rest/swagger.json");
        } catch (ServletException e) {
            log.error("Error initializing Swagger", e);
        }
    }
}
