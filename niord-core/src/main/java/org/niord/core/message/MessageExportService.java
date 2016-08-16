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
package org.niord.core.message;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.niord.core.conf.TextResource;
import org.niord.core.repo.RepositoryService;
import org.niord.model.search.PagedSearchResultVo;
import org.niord.model.message.AttachmentVo;
import org.niord.model.message.MessageVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports a message search result as a Zip archive including attachments
 */
@Stateless
public class MessageExportService {

    @Inject
    Logger log;

    @Inject
    RepositoryService repositoryService;

    // The SQL for finding expired, published message is just too cumbersome for JPQL :-(
    @Inject
    @TextResource("/export-messages.html")
    String messagesPreviewHtmlFile;

    /**
     * Exports the messages search result to the output stream
     * @param result the search result
     * @param os the output stream
     */
    public void export(PagedSearchResultVo<MessageVo> result, OutputStream os) {

        long t0 = System.currentTimeMillis();
        try {
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(os));

            // Write the messages file to the Zip file
            log.debug("Adding messages.json to zip archive");
            out.putNextEntry(new ZipEntry("messages.json"));
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
            String messages = mapper.writeValueAsString(result);
            IOUtils.write(messages, out, "utf-8");
            out.closeEntry();

            // Write the messages-preview.html file into the archive.
            // Can be used to preview the messages by someone unzipping the archive
            String html = messagesPreviewHtmlFile.replace("<<MESSAGES-JSON>>", StringEscapeUtils.escapeJavaScript(messages));
            log.debug("Adding messages-preview.html to zip archive");
            out.putNextEntry(new ZipEntry("messages-preview.html"));
            IOUtils.write(html, out, "utf-8");
            out.closeEntry();


            // Write the message attachments to the Zip file
            Set<String> folderCache = new HashSet<>();
            result.getData().stream()
                    .filter(m -> m.getAttachments() != null && !m.getAttachments().isEmpty())
                    .forEach(m -> exportAttachments(m, out, folderCache));

            out.flush();
            out.close();
            log.info("Created Zip export archive in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Exception e) {
            throw new WebApplicationException("Error generating ZIP archive for messages", e);
        }
    }


    /**
     * Copies all attachment files associated with the message to the zip output stream.
     * Ignores all attachments that causes errors, so the result may be incomplete.
     * @param message the message
     * @param out the zip output stream
     */
    private void exportAttachments(MessageVo message, ZipOutputStream out, Set<String> folderCache) {

        String attachmentFolderPath = message.getRepoPath() + "/attachments/";
        Path folder = repositoryService
                .getRepoRoot()
                .resolve(attachmentFolderPath);
        if (!Files.exists(folder)) {
            return;
        }
        addParentFolders(out, folderCache, attachmentFolderPath);

        for (AttachmentVo att : message.getAttachments()) {
            Path file = folder.resolve(att.getFileName());
            if (Files.exists(file)) {
                String attachmentPath = attachmentFolderPath + att.getFileName();
                try {
                    log.debug("Adding file " + attachmentPath + " to zip archive");
                    out.putNextEntry(new ZipEntry(attachmentPath));
                    FileUtils.copyFile(file.toFile(), out);
                    out.closeEntry();
                } catch (IOException e) {
                    log.warn("Skipping attachments for message " + message.getId() + ": " + e);
                    return;
                }
            } else {
                log.warn("Skipping attachment with no file " + att.getFileName() + " for message " + message.getId());
            }
        }
    }


    /** Adds folders to the zip archive that have not already been added **/
    private void addParentFolders(ZipOutputStream out, Set<String> folderCache, String folder) {
        StringBuilder parentFolder = new StringBuilder();
        for (String path : folder.split("/")) {
            parentFolder.append(path).append("/");
            if (!folderCache.contains(parentFolder.toString())) {
                try {
                    log.debug("Adding folder " + parentFolder.toString() + " to zip archive");
                    out.putNextEntry(new ZipEntry(parentFolder.toString()));
                    out.closeEntry();
                    folderCache.add(parentFolder.toString());
                } catch (IOException ignored) {
                    log.warn("Failed adding folder " + parentFolder.toString() + " to zip archive");
                }
            }
        }
    }
}
