/*
 * Copyright 2023 GLA UK Research and Development Directive
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
package org.niord.core.aton;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.repo.RepositoryService;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports an AtoN search result as a Zip archive.
 */
@RequestScoped
public class AtonExportService {

    @Inject
    Logger log;

    @Inject
    RepositoryService repositoryService;

    /**
     * Exports the messages search result to the output stream
     * @param result the search result
     * @param os the output stream
     */
    @Transactional
    public void export(PagedSearchResultVo<AtonNodeVo> result, OutputStream os) {

        long t0 = System.currentTimeMillis();
        try {
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(os));

            // Write the messages file to the Zip file
            log.debug("Adding aton.json to zip archive");
            out.putNextEntry(new ZipEntry("aton.json"));
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
            String atons = mapper.writeValueAsString(result);
            IOUtils.write(atons, out, "utf-8");
            out.closeEntry();

            out.flush();
            out.close();
            log.info("Created Zip export archive in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Exception ex) {
            throw new WebApplicationException("Error generating ZIP archive for AtoNs", ex);
        }
    }

}
