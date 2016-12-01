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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.NiordApp;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationSearchParams;
import org.niord.core.publication.PublicationService;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.core.repo.RepositoryService;
import org.niord.core.user.TicketService;
import org.niord.core.user.UserService;
import org.niord.core.util.TextUtils;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.publication.PublicationDescVo;
import org.niord.model.publication.PublicationType;
import org.niord.model.publication.PublicationVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * REST interface for accessing publications.
 */
@Path("/publications")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class PublicationRestService extends AbstractBatchableRestService {

    // See http://stackoverflow.com/questions/8092244/regex-to-extract-filename
    private static final Pattern FILE_NAME_HEADER_PATTERN = Pattern.compile("(?<=filename=\").*?(?=\")");

    @Inject
    Logger log;

    @Inject
    PublicationService publicationService;

    @Inject
    UserService userService;

    @Inject
    RepositoryService repositoryService;

    @Inject
    TicketService ticketService;

    @Inject
    NiordApp app;

    /**
     * Searches publications based on the given search parameters
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<PublicationVo> searchPublications(
            @QueryParam("lang") String lang,
            @QueryParam("domain") String domain,
            @QueryParam("category") String category,
            @QueryParam("messagePublication") MessagePublication messagePublication,
            @QueryParam("mainType") @DefaultValue("PUBLICATION") PublicationMainType mainType,
            @QueryParam("type") PublicationType type,
            @QueryParam("status") PublicationStatus status,
            @QueryParam("title") @DefaultValue("") String title,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        PublicationSearchParams params = new PublicationSearchParams()
                .language(lang)
                .mainType(mainType)
                .type(type)
                .status(status)
                .domain(domain)
                .category(category)
                .messagePublication(messagePublication)
                .title(title);
        params.maxSize(limit);

        DataFilter dataFilter = DataFilter.get().lang(lang);

        return publicationService.searchPublications(params).stream()
                .map(p -> p.toVo(PublicationVo.class, dataFilter))
                .collect(Collectors.toList());
    }


    /**
     * Searches publications based on the given search parameters - returns details information for each publication
     */
    @GET
    @Path("/search-details")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"editor"})
    @GZIP
    @NoCache
    public List<SystemPublicationVo> searchSystemPublications(
            @QueryParam("lang") String lang,
            @QueryParam("domain") String domain,
            @QueryParam("category") String category,
            @QueryParam("messagePublication") MessagePublication messagePublication,
            @QueryParam("mainType") @DefaultValue("PUBLICATION") PublicationMainType mainType,
            @QueryParam("type") PublicationType type,
            @QueryParam("status") PublicationStatus status,
            @QueryParam("title") @DefaultValue("") String title,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        PublicationSearchParams params = new PublicationSearchParams()
                .language(lang)
                .mainType(mainType)
                .type(type)
                .status(status)
                .domain(domain)
                .category(category)
                .messagePublication(messagePublication)
                .title(title);
        params.maxSize(limit);

        DataFilter dataFilter = DataFilter.get().lang(lang);

        return publicationService.searchPublications(params).stream()
                .map(p -> p.toVo(SystemPublicationVo.class, dataFilter))
                .collect(Collectors.toList());
    }


    /**
     * Returns all publications up to the given limit
     */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<PublicationVo> getAllPublications(
            @QueryParam("lang") String lang,
            @QueryParam("limit") @DefaultValue("1000") int limit) {

        PublicationSearchParams params = new PublicationSearchParams()
                .language(lang);
        params.maxSize(limit);

        DataFilter dataFilter = DataFilter.get().lang(lang);

        return publicationService.searchPublications(params).stream()
                .map(p -> p.toVo(PublicationVo.class, dataFilter))
                .collect(Collectors.toList());
    }


    /**
     * Returns a system-model version of the publication, which has been associated with a temporary
     * repository folder for new changes.
     *
     * @param publication the publication get an system-model version of
     * @return the system-model version of the publication
     */
    private SystemPublicationVo toSystemPublication(Publication publication) throws IOException {
        SystemPublicationVo publicationVo = publication
                .toVo(SystemPublicationVo.class, DataFilter.get());

        // Create a temporary repository folder for the publication
        publicationService.createTempPublicationRepoFolder(publicationVo);

        return publicationVo;
    }


    /**
     * Returns the publication with the given ID
     */
    @GET
    @Path("/publication/{publicationId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public PublicationVo getPublication(@PathParam("publicationId") String publicationId) throws Exception {
        return publicationService.findByPublicationId(publicationId)
                .toVo(PublicationVo.class, DataFilter.get());
    }


    /**
     * Returns the editable publication with the given ID
     */
    @GET
    @Path("/editable-publication/{publicationId}")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    @GZIP
    @NoCache
    public SystemPublicationVo getSystemPublication(@PathParam("publicationId") String publicationId) throws Exception {
        Publication publication = publicationService.findByPublicationId(publicationId);
        return toSystemPublication(publication);
    }


    /**
     * Creates a new publication template with a temporary repository path
     *
     * @return the new publication template
     */
    @GET
    @Path("/new-publication-template")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"admin"})
    public SystemPublicationVo newTemplatePublication(
            @QueryParam("mainType") PublicationMainType mainType) throws Exception {

        log.info("Creating new publication template");

        // Create a new template publication
        Publication publication = publicationService.newTemplatePublication(mainType);

        return toSystemPublication(publication);
    }


    /**
     * Creates a new publication copy template with a temporary repository path
     *
     * @return the new publication copy template
     */
    @GET
    @Path("/copy-publication-template/{publicationId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed({"admin"})
    public SystemPublicationVo copyPublicationTemplate(
            @PathParam("publicationId") String publicationId,
            @QueryParam("nextIssue") boolean nextIssue) throws Exception {

        log.info("Creating copy of publication " + publicationId);

        Publication publication = publicationService.findByPublicationId(publicationId);
        if (publication == null) {
            return null;
        }

        // Create a system model version of the publication
        SystemPublicationVo editPublication = toSystemPublication(publication);

        // Assign a new ID and repoPath
        editPublication.assignNewId();

        // If nextIssue is requested, update accordingly
        if (nextIssue) {
            editPublication.nextIssue();
        }

        // Reset various fields
        editPublication.setStatus(PublicationStatus.DRAFT);
        editPublication.setMessageTag(null);
        editPublication.setRevision(0);
        if (editPublication.getDescs() != null && editPublication.getType() == PublicationType.MESSAGE_REPORT) {
            editPublication.getDescs().forEach(desc -> {
                desc.setLink(null);
                desc.setFileName(null);
            });
        }

        return editPublication;
    }


    /**
     * Creates a new publication
     */
    @POST
    @Path("/publication/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    @GZIP
    @NoCache
    public SystemPublicationVo createPublication(SystemPublicationVo publication) throws Exception {

        log.info("Creating publication " + publication);

        // Point embedded links to the message repository folder
        publication.toPublicationRepo();

        // Persist the publication
        Publication pub = publicationService.createPublication(new Publication(publication));

        // Copy resources from the temporary editing folder to the repository folder
        publication.setPublicationId(publication.getPublicationId());
        publicationService.updatePublicationFromTempRepoFolder(publication);

        return pub.toVo(SystemPublicationVo.class, DataFilter.get());
    }


    /**
     * Updates an existing publication
     */
    @PUT
    @Path("/publication/{publicationId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    @GZIP
    @NoCache
    public SystemPublicationVo updatePublication(
            @PathParam("publicationId") String publicationId,
            SystemPublicationVo publication) throws Exception {

        if (!Objects.equals(publicationId, publication.getPublicationId())) {
            throw new WebApplicationException(400);
        }

        // Point embedded links to the message repository folder
        publication.toPublicationRepo();

        log.info("Updating publication " + publicationId);
        Publication pub = publicationService.updatePublication(new Publication(publication));

        // Copy resources from the temporary editing folder to the repository folder
        publicationService.updatePublicationFromTempRepoFolder(publication);

        return pub.toVo(SystemPublicationVo.class, DataFilter.get());
    }


    /**
     * Deletes an existing publication
     */
    @DELETE
    @Path("/publication/{publicationId}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    @GZIP
    @NoCache
    public void deletePublication(@PathParam("publicationId") String publicationId) throws Exception {
        log.info("Deleting publication " + publicationId);
        publicationService.deletePublication(publicationId);
    }


    /**
     * Updates the statuses of a publication
     *
     * @param update the status updates
     * @return the updated publication
     */
    @PUT
    @Path("/update-status")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    @RolesAllowed("admin")
    public SystemPublicationVo updatePubliationStatuses(UpdatePublicationStatusParam update) throws Exception {

        log.info("Updating status " + update);

        try {
            return publicationService
                    .updateStatus(update.getPublicationId(), update.getStatus())
                    .toVo(SystemPublicationVo.class, DataFilter.get());

        } catch (Exception e) {
            log.error("Error updating status " + e.getMessage(), e);
            throw new WebApplicationException(e.getMessage(), 400);
        }
    }



    /**
     * Generates a publication report based on the PDF print parameters passed along
     *
     *
     * @param request the servlet request
     * @return the updated publication descriptor
     */
    @POST
    @Path("/generate-publication-report/{folder:.+}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed("admin")
    public PublicationDescVo generatePublicationReport(
            @PathParam("folder") String path,
            PublicationDescVo desc,
            @Context HttpServletRequest request) throws Exception {

        URL url = new URL(
                app.getBaseUri()
                + "/rest/message-reports/report.pdf?"
                + request.getQueryString()
                + "&ticket=" + ticketService.createTicket());

        // Validate that the path is a temporary repository folder path
        java.nio.file.Path folder = checkCreateTempRepoPath(path);

        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            httpConn.disconnect();
            log.error("Error creating publication report " + request.getQueryString());
            throw new WebApplicationException("Error creating publication report: " + request.getQueryString(), 500);
        }

        // If the file name has not been specified in the descriptor, extract it from the
        // "Content-Disposition" header of the report connection
        String fileName = desc.getFileName();
        if (StringUtils.isBlank(fileName)) {
            String disposition = httpConn.getHeaderField("Content-Disposition");
            if (StringUtils.isNotBlank(disposition)) {
                Matcher regexMatcher = FILE_NAME_HEADER_PATTERN.matcher(disposition);
                if (regexMatcher.find()) {
                    fileName = regexMatcher.group();
                }
            }
        }
        fileName = StringUtils.defaultIfBlank(fileName, "publication.pdf");

        File destFile = folder.resolve(fileName).toFile();
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destFile));
             InputStream is = httpConn.getInputStream()) {
            IOUtils.copy(is, out);
        } catch (IOException ex) {
            log.error("Error generating publication report " + destFile, ex);
            throw new WebApplicationException("Error generating publication report: " + destFile, 500);
        }
        httpConn.disconnect();

        desc.setFileName(fileName);
        desc.setLink(repositoryService.getRepoUri(destFile.toPath()));

        log.info("Generated publication report at destination " + destFile);

        return desc;
    }


    /**
     * Uploads a publication file
     *
     * @param request the servlet request
     * @return the updated publication descriptor
     */
    @POST
    @Path("/upload-publication-file/{folder:.+}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed("admin")
    public PublicationDescVo uploadPublicationFile(@PathParam("folder") String path, @Context HttpServletRequest request) throws Exception {

        FileItemFactory factory = RepositoryService.newDiskFileItemFactory(servletContext);
        ServletFileUpload upload = new ServletFileUpload(factory);

        List<FileItem> items = upload.parseRequest(request);

        // Get hold of the first uploaded publication file
        FileItem fileItem = items.stream()
                .filter(item -> !item.isFormField())
                .findFirst()
                .orElseThrow(() -> new WebApplicationException("No uploaded publication file", 400));

        // Check for the associated publication desc record
        PublicationDescVo desc = items.stream()
                .filter(item -> item.isFormField() && "data".equals(item.getFieldName()))
                .map(item -> {
                    try {
                        return new ObjectMapper().
                                readValue(item.getString("UTF-8"), PublicationDescVo.class);
                    } catch (Exception ignored) {
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new WebApplicationException("No publication descriptor found", 400));

        // Validate that the path is a temporary repository folder path
        java.nio.file.Path folder = checkCreateTempRepoPath(path);

        String fileName = StringUtils.defaultIfBlank(
                desc.getFileName(),
                Paths.get(fileItem.getName()).getFileName().toString()); // NB: IE includes the path in item.getName()!

        File destFile = folder.resolve(fileName).toFile();
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destFile))) {
            IOUtils.copy(fileItem.getInputStream(), out);
        } catch (IOException ex) {
            log.error("Error creating publication file " + destFile, ex);
            throw new WebApplicationException("Error creating destination file: " + destFile, 500);
        }

        desc.setFileName(fileName);
        desc.setLink(repositoryService.getRepoUri(destFile.toPath()));

        log.info("Copied publication file " + fileItem.getName() + " to destination " + destFile);

        return desc;
    }


    /**
     * Checks that the path is a temp repo path and create the associated folder if it does not exist
     * @param path the path to check
     * @return the file path to the folder
     */
    private java.nio.file.Path checkCreateTempRepoPath(String path) {

        // Validate that the path is a temporary repository folder path
        java.nio.file.Path folder = repositoryService.validateTempRepoPath(path);

        if (Files.notExists(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                log.error("Error creating publication folder " + folder, e);
                throw new WebApplicationException("Invalid publication folder: " + path, 403);
            }
        }
        return folder;
    }

    /**
     * Returns all publications for export purposes
     *
     * If not called via Ajax, pass a ticket request parameter along, which
     * can be requested via Ajax call to /rest/tickets/ticket?role=admin
     */
    @GET
    @Path("/export")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<PublicationVo> exportPublications(
            @QueryParam("lang") String lang) {

        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole("admin")) {
            throw new WebApplicationException(403);
        }

        PublicationSearchParams params = new PublicationSearchParams().language(lang);
        DataFilter dataFilter = DataFilter.get().lang(lang);

        // TODO: Sort templates first - not by title
        return publicationService.searchPublications(params).stream()
                .map(p -> p.toVo(SystemPublicationVo.class, dataFilter))
                .sorted(publicationTitleComparator(lang))
                .collect(Collectors.toList());
    }


    /**
     * Imports an uploaded Publications json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-publications")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importPublications(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "publication-import");
    }


    /** Returns a publication name comparator **/
    private Comparator<PublicationVo> publicationTitleComparator(String lang) {
        return (p1, p2) -> {
            String n1 = p1.getDesc(lang) != null ? p1.getDesc(lang).getTitle() : null;
            String n2 = p2.getDesc(lang) != null ? p2.getDesc(lang).getTitle() : null;
            return TextUtils.compareIgnoreCase(n1, n2);
        };
    }

    /***************************
     * Helper classes
     ***************************/

    /** Encapsulates a status change for a publication */
    @SuppressWarnings("unused")
    public static class UpdatePublicationStatusParam implements IJsonSerializable {
        String publicationId;
        PublicationStatus status;

        @Override
        public String toString() {
            return "{publicationId='" + publicationId + "', status=" + status + "}";
        }

        public String getPublicationId() {
            return publicationId;
        }

        public void setPublicationId(String publicationId) {
            this.publicationId = publicationId;
        }

        public PublicationStatus getStatus() {
            return status;
        }

        public void setStatus(PublicationStatus status) {
            this.status = status;
        }
    }

}
