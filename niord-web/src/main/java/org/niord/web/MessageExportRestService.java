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

package org.niord.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.domain.DomainService;
import org.niord.core.message.MessageExportService;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.model.IJsonSerializable;
import org.niord.model.message.MessageVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * REST interface for exporting and importing message archives.
 */
@Path("/message-io")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
@SuppressWarnings("unused")
public class MessageExportRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    MessageSearchRestService messageSearchRestService;

    @Inject
    MessageExportService messageExportService;

    @Inject
    DomainService domainService;


    /**
     * Generates a ZIP archive for the message search result including attachments.
     */
    @GET
    @Path("/export.zip")
    @GZIP
    @NoCache
    public Response generateZipArchiveForSearch(@Context HttpServletRequest request) throws Exception {

        // Perform a search for at most 1000 messages
        MessageSearchParams params = MessageSearchParams.instantiate(domainService.currentDomain(), request);
        params.language(null).maxSize(1000).page(0);

        PagedSearchResultVo<MessageVo> result = messageSearchRestService.search(params);
        result.getData().forEach(m -> m.sort(params.getLanguage()));

        try {
            StreamingOutput stream = os -> messageExportService.export(result, os);

            return Response.ok(stream)
                    .type("application/zip")
                    .header("Content-Disposition", "attachment; filename=\"messages.zip\"")
                    .build();

        } catch (Exception e) {
            log.error("Error generating ZIP archive for messages", e);
            throw e;
        }
    }


    /**
     * Imports an uploaded messages zip archive
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importMessages(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "msg-archive-import");
    }


    /** {@inheritDoc} */
    @Override
    protected void checkBatchJob(String batchJobName, FileItem fileItem, Map<String, Object> params) throws Exception {

        // Check that the zip file contains a messages.json file
        if (!checkForMessagesFileInImportArchive(fileItem.getInputStream())) {
            throw new Exception("Zip archive is missing a valid messages.json entry");
        }

        // Read and validate the parameters associated with the batch job
        ImportMessagesArchiveParams batchData;
        try {
            batchData = new ObjectMapper().readValue((String)params.get("data"), ImportMessagesArchiveParams.class);
        } catch (IOException e) {
            throw new Exception("Missing batch data with tag and message series", e);
        }

        if (StringUtils.isBlank(batchData.getSeriesId())) {
            throw new Exception("Missing message series for imported NMs");
        }

        // Determine all valid message series for the current user
        Set<String> validMessageSeries = domainService.domainsWithUserRole("admin").stream()
                .flatMap(d -> d.getMessageSeries().stream())
                .map(MessageSeries::getSeriesId)
                .collect(Collectors.toSet());

        // Update parameters
        params.remove("data");
        params.put("seriesId", batchData.getSeriesId());
        if (StringUtils.isNotBlank(batchData.getTagId())) {
            params.put("tagId", batchData.getTagId());
        }
        params.put("assignNewUids", batchData.getAssignNewUids() == null ? false : batchData.getAssignNewUids());
        params.put("preserveStatus", batchData.getPreserveStatus() == null ? false : batchData.getPreserveStatus());
        params.put("assignDefaultSeries", batchData.getAssignDefaultSeries() == null ? false : batchData.getAssignDefaultSeries());
        params.put("createBaseData", batchData.getCreateBaseData() == null ? false : batchData.getCreateBaseData());
        params.put("validMessageSeries", validMessageSeries);
    }


    /** Checks for a valid "messages.xml" zip file entry **/
    private boolean checkForMessagesFileInImportArchive(InputStream in) throws Exception {
        try (ZipInputStream zipFile = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zipFile.getNextEntry()) != null) {
                if ("messages.json".equals(entry.getName())) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        PagedSearchResultVo<MessageVo> messages = mapper.readValue(
                                zipFile,
                                new TypeReference<PagedSearchResultVo<MessageVo>>() {});
                        return  messages != null;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }


    /***************************
     * Helper classes
     ***************************/

    /** Defines the parameters used when starting an import a messages zip archive */
    public static class ImportMessagesArchiveParams implements IJsonSerializable {

        Boolean assignNewUids;
        Boolean preserveStatus;
        Boolean assignDefaultSeries;
        Boolean createBaseData;
        String seriesId;
        String tagId;

        public Boolean getAssignNewUids() {
            return assignNewUids;
        }

        public void setAssignNewUids(Boolean assignNewUids) {
            this.assignNewUids = assignNewUids;
        }

        public Boolean getPreserveStatus() {
            return preserveStatus;
        }

        public void setPreserveStatus(Boolean preserveStatus) {
            this.preserveStatus = preserveStatus;
        }

        public Boolean getAssignDefaultSeries() {
            return assignDefaultSeries;
        }

        public void setAssignDefaultSeries(Boolean assignDefaultSeries) {
            this.assignDefaultSeries = assignDefaultSeries;
        }

        public Boolean getCreateBaseData() {
            return createBaseData;
        }

        public void setCreateBaseData(Boolean createBaseData) {
            this.createBaseData = createBaseData;
        }

        public String getSeriesId() {
            return seriesId;
        }

        public void setSeriesId(String seriesId) {
            this.seriesId = seriesId;
        }

        public String getTagId() {
            return tagId;
        }

        public void setTagId(String tagId) {
            this.tagId = tagId;
        }
    }

}
