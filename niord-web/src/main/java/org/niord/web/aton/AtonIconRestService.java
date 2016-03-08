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
package org.niord.web.aton;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.aton.AtonNode;
import org.niord.core.repo.RepositoryService;
import org.niord.model.vo.aton.AtonNodeVo;
import org.slf4j.Logger;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates and caches AtoN icons
 */
@javax.ws.rs.Path("/aton-icon")
@Startup
@Singleton
public class AtonIconRestService {

    static final String OVERVIEW_ICON_REPO = "aton_icons";
    static final int OVERVIEW_ICON_HEIGHT = 90;
    static final int OVERVIEW_ICON_WIDTH = 60;
    static final double OVERVIEW_ICON_SCALE = 0.3;

    @Inject
    Logger log;

    @Inject
    RepositoryService repositoryService;


    @POST
    @javax.ws.rs.Path("/svg")
    @Consumes("application/json;charset=UTF-8")
    @Produces("image/svg+xml")
    @GZIP
    @NoCache
    public Response createSvgForAton(
            @QueryParam("width") @DefaultValue("100") int width,
            @QueryParam("height") @DefaultValue("100") int height,
            @QueryParam("scale") @DefaultValue("0.4") double scale,
            AtonNodeVo aton) throws Exception {

        long t0 = System.currentTimeMillis();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtonIconRenderer.renderIcon(
                aton,
                "svg",
                out,
                width,       // width
                height,      // height
                width/2,     // x
                height/2,    // y
                scale        // scale
        );

        log.info("Generated AtoN SVG in " + (System.currentTimeMillis() - t0) + " ms");

        return Response
                .ok(out.toByteArray())
                .build();
    }



    @GET
    @javax.ws.rs.Path("/overview")
    @NoCache
    public Response getAtonOverviewIcon(@Context HttpServletRequest request) throws Exception {

        long t0 = System.currentTimeMillis();

        String type = request.getParameter("seamark:type");
        if (StringUtils.isBlank(type)) {
            return Response
                    .temporaryRedirect(new URI("/img/aton/aton.png"))
                    .build();
        }

        // Prepare an AtoN to use as a template for icon construction
        AtonNode aton = new AtonNode();

        // Construct a repository path to the icon
        Path path = repositoryService
                .getRepoRoot()
                .resolve(OVERVIEW_ICON_REPO);

        path = addParam(aton, path, request, "seamark:type");
        path = addParam(aton, path, request, "seamark:" + type + ":category");
        path = addParam(aton, path, request, "seamark:" + type + ":shape");
        path = addParam(aton, path, request, "seamark:" + type + ":colour");
        path = addParam(aton, path, request, "seamark:" + type + ":colour_pattern");
        path = addParam(aton, path, request, "seamark:topmark:shape");
        path = addParam(aton, path, request, "seamark:topmark:colour");
        path = addParam(aton, path, request, "seamark:light:character");
        path = addParam(aton, path, request, "seamark:light:colour");

        // And the actual icon file
        path = path.resolve("aton_icon_" + OVERVIEW_ICON_WIDTH + "x" + OVERVIEW_ICON_HEIGHT + ".png");

        if (!Files.isRegularFile(path)) {
            checkCreateParentDirs(path);

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                AtonIconRenderer.renderIcon(
                        aton.toVo(),
                        "png",
                        out,
                        OVERVIEW_ICON_WIDTH,            // width
                        OVERVIEW_ICON_HEIGHT,           // height
                        OVERVIEW_ICON_WIDTH / 3,        // x
                        2 * OVERVIEW_ICON_HEIGHT / 3,   // y
                        OVERVIEW_ICON_SCALE             // scale
                );

                log.info("Generated AtoN PNG " + path + " in " + (System.currentTimeMillis() - t0) + " ms");
            }
        }

        // Redirect to the icon
        String iconUri = repositoryService.getRepoUri(path);
        return Response
                .temporaryRedirect(new URI("../" + iconUri))
                .build();
    }


    /**
     * If the parameter is well-defined, add a sub-folder to the path
     *
     * @param aton an AtoN template to update with the parameter tags
     * @param path the path to update
     * @param request the servlet request
     * @param param the param to check for
     * @return the potentially updated path
     */
    private Path addParam(AtonNode aton, Path path, HttpServletRequest request, String param) {
        String val = request.getParameter(param);
        if (StringUtils.isNotBlank(val)) {
            aton.updateTag(param, val);
            path = path.resolve(escape(val));
        }
        return path;
    }


    /** Escape naughty file name characters **/
    private String escape(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }


    /**
     * Ensures that parent directories are created
     * @param file the file whose parent directories will be created
     */
    private void checkCreateParentDirs(Path file) throws IOException {
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
    }

}

