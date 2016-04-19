/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
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
            beanConfig.setResourcePackage("org.niord.web.api,org.niord.model.vo,org.niord.model.vo.geojson");
            beanConfig.setScan(true);
            log.info("Initialized Swagger: " + app.getBaseUri() +  "/rest/swagger.json");
        } catch (ServletException e) {
            log.error("Error initializing Swagger", e);
        }
    }
}
