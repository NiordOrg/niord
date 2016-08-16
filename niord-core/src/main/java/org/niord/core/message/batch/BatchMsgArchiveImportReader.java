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
package org.niord.core.message.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.repo.RepositoryService;
import org.niord.core.message.vo.PagedSearchResultVo;
import org.niord.model.vo.MessageVo;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Imports a list of messages from a zip file containing a messages.json file along with message attachments.
 * <p>
 * Please note, the actual msg-archive-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the PagedSearchResultVo<MessageVo> class. Example:
 * <pre>
 * {
 *   "data": [{
 *     "repoPath": "messages/d66c91f5/3ec5/4ce1-8d90-4b2e364b5048",
 *      "messageSeries": {
 *      "seriesId": "dma-nm"
 *      },
 *      "number": 1276,
 *      "mrn": "urn:mrn:iho:nm:dk:dma:2015:1276",
 *      "shortId": "NM-1276-15",
 *      "mainType": "NM",
 *      "type": "PERMANENT_NOTICE",
 *      "status": "PUBLISHED",
 *      ...
 *    },
 *    ...
 *  ],
 *  "total": 20,
 *  "size": 20,
 *  "description": "Language: da, Domain: true, Statuses: [PUBLISHED], Series ID's: [dma-nm]"
 * }
 * </pre>
 */
@Named
public class BatchMsgArchiveImportReader extends AbstractItemHandler {

    List<ExtractedArchiveMessageVo> messages;
    int messageNo = 0;

    @Inject
    RepositoryService repositoryService;


    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Validate that we have access to the "seriesId" properties
        if (StringUtils.isBlank((String)job.getProperties().get("seriesId"))) {
            getLog().severe("Missing seriesId batch property");
            throw new Exception("Missing seriesId batch property");
        }

        // Extract the zip archive and get hold of the messages
        messages = extractMessageArchive();

        if (prevCheckpointInfo != null) {
            messageNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + messages.size() + " messages from index " + messageNo);
    }


    /** Extracts the message archive and reads in the batch import messages */
    protected List<ExtractedArchiveMessageVo> extractMessageArchive() throws Exception {

        // Default implementation reads the messages from a message.json batch file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Extract the archive into a temporary repository path
        String tempArchiveRepoPath = repositoryService.getNewTempDir().getPath();
        Path dest = repositoryService.getRepoRoot().resolve(tempArchiveRepoPath);
        extractMessageArchive(path, dest);
        getLog().info("Extracted message archive to " + dest);

        // Fetch the messages.json from the root of the extracted archive
        Path messageFilePath = dest.resolve("messages.json");
        if (!Files.exists(messageFilePath)) {
            getLog().log(Level.SEVERE, "No valid messages.json file found in the archive");
            throw new Exception("No valid messages.json file found in the archive");
        }

        // Read the messages.json file
        PagedSearchResultVo<MessageVo> messages;
        try {
            ObjectMapper mapper = new ObjectMapper();
            messages = mapper.readValue(
                    messageFilePath.toFile(),
                    new TypeReference<PagedSearchResultVo<MessageVo>>() {});
        } catch (IOException e) {
            getLog().log(Level.SEVERE, "Invalid messages.json file");
            throw new Exception("Invalid messages.json file");
        }

        // Wrap the messages as ExtractedArchiveMessageVo with the "editRepoPath" pointing to the extracted archive
        return messages.getData().stream()
                .map(m -> new ExtractedArchiveMessageVo(m, tempArchiveRepoPath + "/" + m.getRepoPath()))
                .collect(Collectors.toList());
    }


    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (messageNo < messages.size()) {

            // For every 5 read messages, update the progress
            if (messageNo % 5 == 0) {
                updateProgress((int)(100.0 * messageNo / messages.size()));
            }

            getLog().info("Reading message no " + messageNo);
            return messages.get(messageNo++);
        }
        return null;
    }


    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return messageNo;
    }


    /** Utility method that extracts the given zip file to a given destination **/
    @SuppressWarnings("all")
    private void extractMessageArchive(Path path, Path destination) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(destination.toFile(),  entry.getName());
                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else {
                    entryDestination.getParentFile().mkdirs();
                    try (InputStream in = zipFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }
    }


    /** Encapsulates a MessageVo and the path to the temp repo where the archive was extracted */
    public static class ExtractedArchiveMessageVo {
        MessageVo message;
        String editRepoPath;

        public ExtractedArchiveMessageVo(MessageVo message, String editRepoPath) {
            this.message = message;
            this.editRepoPath = editRepoPath;
        }

        public MessageVo getMessage() {
            return message;
        }

        public String getEditRepoPath() {
            return editRepoPath;
        }
    }
}
