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
package org.niord.core.message;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.niord.core.conf.TextResource;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.repo.RepositoryService;
import org.niord.model.message.AttachmentVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.enterprise.context.RequestScoped;
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
@RequestScoped
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
    public void export(PagedSearchResultVo<SystemMessageVo> result, OutputStream os) {

        long t0 = System.currentTimeMillis();
        try {
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(os));

            // Rewrite links in message description to remove "/rest/repo/file/" prefix
            result.getData().forEach(m -> m.rewriteRepoPath("\"/rest/repo/file/" + m.getRepoPath(), "\"" + m.getRepoPath()));

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


            // Write the message attachments and thumbnail files to the Zip file
            Set<String> folderCache = new HashSet<>();
            result.getData().stream()
                    .filter(m -> m.getAttachments() != null && !m.getAttachments().isEmpty())
                    .forEach(m -> {
                        exportAttachments(m, out, folderCache);
                        exportThumbnail(m, out, folderCache);
                    });


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
    private void exportAttachments(SystemMessageVo message, ZipOutputStream out, Set<String> folderCache) {

        // Attachment files are nested within revision folders in the message repo folder
        for (AttachmentVo att : message.getAttachments()) {
            // E.g. "messages/a/68/a68f0dae-0669-4a5a-9a40-dd8d92d73b96/4/Buoy_seal.jpg"
            String path = att.getPath();
            if (path == null || !path.startsWith(message.getRepoPath())) {
                log.warn("Skipping invalid attachment for message " + message.getId() + ": " + path);
                continue;
            }

            // E.g. "messages/a/68/a68f0dae-0669-4a5a-9a40-dd8d92d73b96/4"
            String folderPath = path.substring(0, path.lastIndexOf("/"));
            Path folder = repositoryService
                    .getRepoRoot()
                    .resolve(folderPath);
            if (!Files.isDirectory(folder)) {
                log.warn("Skipping non-existing attachments for message " + message.getId() + ": " + att.getPath());
                continue;
            }
            addParentFolders(out, folderCache, folderPath);

            // E.g. "Buoy_seal.jpg"
            Path file = folder.resolve(att.getFileName());
            if (Files.exists(file)) {
                String attachmentPath = folderPath + "/" + att.getFileName();
                try {
                    addFile(out, attachmentPath, file);
                } catch (IOException e) {
                    log.warn("Skipping attachments for message " + message.getId() + ": " + e);
                }
            } else {
                log.warn("Skipping attachment with no file " + att.getFileName() + " for message " + message.getId());
            }
        }
    }


    /**
     * Exports any custom thumbnail file associated with the message
     * @param message the message
     * @param out the zip output stream
     */
    private void exportThumbnail(SystemMessageVo message, ZipOutputStream out, Set<String> folderCache) {

        // Custom thumbnail files are nested within revision folders in the message repo folder
        String thumbnailPath = message.getThumbnailPath();
        if (StringUtils.isNotBlank(thumbnailPath) && thumbnailPath.startsWith(message.getRepoPath())) {
            // E.g. "messages/a/68/a68f0dae-0669-4a5a-9a40-dd8d92d73b96/4/custom_thumb_256.png"

            // E.g. "messages/a/68/a68f0dae-0669-4a5a-9a40-dd8d92d73b96/4"
            String folderPath = thumbnailPath.substring(0, thumbnailPath.lastIndexOf("/"));
            Path folder = repositoryService
                    .getRepoRoot()
                    .resolve(folderPath);
            if (!Files.isDirectory(folder)) {
                log.warn("Skipping non-existing thumbnail file for message " + message.getId() + ": " + thumbnailPath);
                return;
            }
            addParentFolders(out, folderCache, folderPath);

            // E.g. "custom_thumb_256.png"
            String fileName = thumbnailPath.substring(folderPath.length() + 1);
            Path file = folder.resolve(fileName);
            if (Files.exists(file)) {
                try {
                    addFile(out, thumbnailPath, file);
                } catch (IOException e) {
                    log.warn("Skipping attachments for message " + message.getId() + ": " + e);
                }
            } else {
                log.warn("Skipping non-existing thumbnail file " + thumbnailPath + " for message " + message.getId());
            }
        }
    }


    /** Adds the file to the zip archive **/
    private void addFile(ZipOutputStream out, String path, Path file) throws IOException {
        log.debug("Adding file " + path + " to zip archive");
        out.putNextEntry(new ZipEntry(path));
        FileUtils.copyFile(file.toFile(), out);
        out.closeEntry();
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
