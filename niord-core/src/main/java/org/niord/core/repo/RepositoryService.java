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
package org.niord.core.repo;

import io.quarkus.arc.Lock;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.user.Roles;
import org.niord.core.util.WebUtils;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.niord.core.settings.Setting.Type;

/**
 * A repository service.<br>
 * Streams files from the repository and facilitates uploading files to the repository.
 * <p>
 *     The repository is public in as much as everybody can download all files.<br>
 *
 *     However only "admin" users can upload files to the entire repository.<br>
 *
 *     Registered users, with the "user" role, can upload files to a sub-root of
 *     the repository, the {@code repoTempRoot}. Files upload to the this part
 *     of the repository will be deleted after 24 hours.
 * </p>
 */
@jakarta.ws.rs.Path("/repo")
@ApplicationScoped
@Lock(Lock.Type.READ)
@PermitAll
@SuppressWarnings("unused")
public class RepositoryService {

    @Inject
    @Setting(value="repoRootPath", defaultValue="${niord.home}/repo", description="The root directory of the Niord repository")
    Path repoRoot;

    @Inject
    @Setting(value="repoCacheTimeout", defaultValue="5", description="Cache timeout of repo files in minutes", type=Type.Integer)
    Integer cacheTimeout;

    @Inject
    Logger log;

    @Inject
    FileTypes fileTypes;

    @Inject
    ThumbnailService thumbnailService;

    /**
     * Initializes the repository
     */
    @PostConstruct
    public void init() {

        // Create the repo root directory
        if (!Files.exists(getRepoRoot())) {
            try {
                Files.createDirectories(getRepoRoot());
            } catch (IOException e) {
                log.error("Error creating repository dir " + getRepoRoot(), e);
            }
        }

        // Create the repo "temp" root directory
        if (!Files.exists(getTempRepoRoot())) {
            try {
                Files.createDirectories(getTempRepoRoot());
            } catch (IOException e) {
                log.error("Error creating repository dir " + getTempRepoRoot(), e);
            }
        }
    }

    /**
     * Returns the repository root
     * @return the repository root
     */
    public Path getRepoRoot() {
        return repoRoot;
    }

    /**
     * Returns the repository "temp" root
     * @return the repository "temp" root
     */
    public Path getTempRepoRoot() {
        return getRepoRoot().resolve("temp");
    }


    /**
     * Creates a URI from the repo file
     * @param repoFile the repo file
     * @return the URI for the file
     */
    public String getRepoUri(Path repoFile) {
        Path filePath = getRepoRoot().relativize(repoFile);
        return "/rest/repo/file/" + WebUtils.encodeURI(filePath.toString().replace('\\', '/'));
    }

    /**
     * Creates a path from the repo file relative to the repo root
     * @param repoFile the repo file
     * @return the path for the file
     */
    public String getRepoPath(Path repoFile) {
        Path filePath = getRepoRoot().relativize(repoFile);
        return filePath.toString().replace('\\', '/');
    }

    /**
     * Creates two levels of sub-folders within the {@code rootFolder} based on
     * a MD5 hash of the {@code target}.
     * If the sub-folder does not exist, it is created.
     *
     * @param rootFolder the root folder within the repository root
     * @param target the target name used for the hash
     * @param includeTarget whether to create a sub-folder for the target or not
     * @return the sub-folder associated with the target
     */
    public Path getHashedSubfolder(String rootFolder, String target, boolean includeTarget) throws IOException {
        byte[] bytes = target.getBytes("utf-8");

        // MD5 hash the ID
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("This should never happen");
        }
        md.update(bytes);
        bytes = md.digest();
        String hash = String.valueOf(Integer.toHexString(bytes[0] & 0xff));
        while (hash.length() < 2) {
            hash = "0" + hash;
        }

        Path folder = getRepoRoot();

        // Add the root folder
        if (StringUtils.isNotBlank(rootFolder)) {
            folder = folder.resolve(rootFolder);
        }

        // Add two hashed sub-folder levels
        folder = folder
                .resolve(hash.substring(0, 1))
                .resolve(hash.substring(0, 2));

        // Check if we should create a sub-folder for the target as well
        if (includeTarget) {
            folder = folder.resolve(target);
        }

        // Create the folder if it does not exist
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }
        return folder;
    }


    /**
     * Streams the file specified by the path
     * @param path the path
     * @param request the servlet request
     * @return the response
     */
    @GET
    @jakarta.ws.rs.Path("/file/{file:.+}")
    public Response streamFile(@PathParam("file") String path,
                               @Context Request request) throws IOException {

        Path f = repoRoot.resolve(path);

        if (Files.notExists(f) || Files.isDirectory(f)) {
            log.warn("Failed streaming file: " + f);
            return Response
                    .status(HttpServletResponse.SC_NOT_FOUND)
                    .entity("File not found: " + path)
                    .build();
        }

        // Set expiry to cacheTimeout minutes
        Date expirationDate = new Date(System.currentTimeMillis() + 1000L * 60L * cacheTimeout);

        String mt = fileTypes.getContentType(f);

        // Check for an ETag match
        EntityTag etag = new EntityTag("" + Files.getLastModifiedTime(f).toMillis() + "_" + Files.size(f), true);
        Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
        if (responseBuilder != null) {
            // Etag match
            log.trace("File unchanged. Return code 304");
            return responseBuilder
                    .expires(expirationDate)
                    .build();
        }

        log.trace("Streaming file: " + f);
        return Response
                .ok(f.toFile(), mt)
                .expires(expirationDate)
                .tag(etag)
                .build();
    }

    /**
     * Deletes the file specified by the path
     * @param path the path
     * @return the response
     */
    @DELETE
    @jakarta.ws.rs.Path("/file/{file:.+}")
    @Produces("text/plain")
    @RolesAllowed(Roles.EDITOR)
    public Response deleteFile(@PathParam("file") String path) throws IOException {

        Path f = repoRoot.resolve(path);

        if (Files.notExists(f) || Files.isDirectory(f)) {
            log.warn("Failed deleting non-existing file: " + f);
            return Response
                    .status(HttpServletResponse.SC_NOT_FOUND)
                    .entity("File not found: " + path)
                    .build();
        }

        Files.delete(f);
        log.info("Deleted file " + f);

        return Response
                .ok("File deleted " + f)
                .build();
    }

    /**
     * Returns the thumbnail to use for the file specified by the path
     * @param path the path
     * @param size the icon size, either 32, 64 or 128
     * @return the thumbnail to use for the file specified by the path
     */
    @GET
    @jakarta.ws.rs.Path("/thumb/{file:.+}")
    public Response getThumbnail(@PathParam("file") String path,
                                 @QueryParam("size") @DefaultValue("64") int size) throws IOException, URISyntaxException {

        IconSize iconSize = IconSize.getIconSize(size);
        Path f = repoRoot.resolve(path);

        if (Files.notExists(f) || Files.isDirectory(f)) {
            log.warn("Failed streaming file: " + f);
            return Response
                    .status(HttpServletResponse.SC_NOT_FOUND)
                    .entity("File not found: " + path)
                    .build();
        }

        // Check if we can generate a thumbnail for image files
        String thumbUri;
        Path thumbFile = thumbnailService.getThumbnail(f, iconSize);
        if (thumbFile != null) {
            thumbUri = "../" + getRepoUri(thumbFile);
        } else {
            // Fall back to file type icons
            thumbUri = "../" + fileTypes.getIcon(f, iconSize);
        }

        log.trace("Redirecting to thumbnail: " + thumbUri);
        return Response
                .temporaryRedirect(new URI(thumbUri))
                .build();
    }

    /**
     * Returns a list of files in the folder specified by the path
     * @param path the path
     * @return the list of files in the folder specified by the path
     */
    @GET
    @jakarta.ws.rs.Path("/list/{folder:.+}")
    @Produces("application/json;charset=UTF-8")
    @NoCache
    public List<RepoFileVo> listFiles(@PathParam("folder") String path) throws IOException {

        List<RepoFileVo> result = new ArrayList<>();
        Path folder = repoRoot.resolve(path);

        if (Files.exists(folder) && Files.isDirectory(folder)) {

            // Filter out directories, hidden files, thumbnails and map images
            DirectoryStream.Filter<Path> filter = file ->
                    Files.isRegularFile(file) &&
                    !file.getFileName().toString().startsWith(".") &&
                    !file.getFileName().toString().matches(".+_thumb_\\d{1,3}\\.\\w+") && // Thumbnails
                    !file.getFileName().toString().matches("map_\\d{1,3}\\.png"); // Map image

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, filter)) {
                stream.forEach(f -> {
                    RepoFileVo vo = new RepoFileVo();
                    vo.setName(f.getFileName().toString());
                    vo.setPath(WebUtils.encodeURI(path + "/" + f.getFileName().toString()));
                    vo.setDirectory(Files.isDirectory(f));
                    try {
                        vo.setUpdated(new Date(Files.getLastModifiedTime(f).toMillis()));
                        vo.setSize(Files.size(f));
                    } catch (Exception e) {
                        log.trace("Error reading file attribute for " + f);
                    }
                    result.add(vo);
                });
            }
        }
        return result;
    }

    /**
     * Handles upload of files
     *
     * @param path the folder to upload to
     * @param input the multi-part input request
     */
    @POST
    @jakarta.ws.rs.Path("/upload/{folder:.+}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.EDITOR)
    public List<String> uploadFile(@PathParam("folder") String path, MultipartInput input) throws IOException {

        Path folder = repoRoot.resolve(path);

        if (Files.exists(folder) && !Files.isDirectory(folder)) {
            log.warn("Failed streaming file to folder: " + folder);
            throw new WebApplicationException("Invalid upload folder: " + path, 403);

        } else if (Files.notExists(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                log.error("Error creating repository folder " + folder, e);
                throw new WebApplicationException("Invalid upload folder: " + path, 403);
            }
        }

        List<String> result = new ArrayList<>();

        for (Map.Entry<String, InputStream> file : WebUtils.getMultipartInputFiles(input).entrySet()) {
            // Argh - IE includes the path in the item.getName()!
            String fileName = Paths.get(file.getKey()).getFileName().toString();
            File destFile = getUniqueFile(folder, fileName).toFile();
            log.info("File " + fileName + " is uploaded to " + destFile);
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destFile))) {
                InputStream in = new BufferedInputStream(file.getValue());
                byte[] buffer = new byte[1024];
                int len = in.read(buffer);
                while (len != -1) {
                    out.write(buffer, 0, len);
                    len = in.read(buffer);
                }
                out.flush();
            }

            // Return the repo-relative path as a result
            result.add(Paths.get(path, destFile.getName()).toString());
        }

        return result;
    }

    /**
     * Returns a unique file name in the given folder.
     * If the given file name is not unique, a new is constructed
     * by adding a number to the file name
     * @param folder the folder
     * @param name the file name
     * @return the new unique file
     */
    private Path getUniqueFile(Path folder, String name) {
        Path file = folder.resolve(name);
        if (Files.exists(file)) {
            for (int x = 2; true; x++) {
                String fileName =
                        FilenameUtils.removeExtension(name) +
                        " " + x + "." +
                        FilenameUtils.getExtension(name);
                file = folder.resolve(fileName);
                if (!Files.exists(file)) {
                    break;
                }
            }
        }
        return file;
    }

    /**
     * Returns a new unique "temp" directory. Please note, the directory has not yet been created.
     *
     * @return a new unique "temp" directory
     */
    @GET
    @jakarta.ws.rs.Path("/new-temp-dir")
    @Produces("application/json;charset=UTF-8")
    public RepoFileVo getNewTempDir() {

        // Construct a unique directory name
        String name = UUID.randomUUID().toString();// test "f823b7d5-9559-4a76-b3a3-6d32f2bf55f2";

        RepoFileVo dir = new RepoFileVo();
        dir.setName(name);
        dir.setPath(WebUtils.encodeURI("temp/" + name));
        dir.setDirectory(true);
        return dir;
    }

    /**
     * Handles upload of files to the "temp" repo root.
     * Only the "user" role is required to upload to the "temp" root
     *
     * @param path the folder to upload to
     * @param input the multi-part input request
     */
    @POST
    @jakarta.ws.rs.Path("/upload-temp/{folder:.+}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.EDITOR)
    public List<String> uploadTempFile(@PathParam("folder") String path, MultipartInput input) throws IOException {

        // Check that the specified folder is indeed under the "temp" root
        validateTempRepoPath(path);

        return uploadFile(path, input);
    }


    /**
     * Resolves the relative repository path as a temporary repository path
     * and returns the full path to it.
     * @param path the path to resolve and validate as a temporary repository path
     * @return the full path
     */
    public Path validateTempRepoPath(String path) {
        // Validate that the path is a temporary repository folder path
        Path folder = getRepoRoot().resolve(path);
        if (!folder.toAbsolutePath().startsWith(getTempRepoRoot().toAbsolutePath())) {
            log.warn("Failed streaming file to temp root folder: " + folder);
            throw new WebApplicationException("Invalid upload folder: " + path, 403);
        }
        return folder;
    }

    /**
     * Every hour, check the repo "temp" root, and delete old files and folders
     */
    @Scheduled(cron="50 22 * * * ?")
    void cleanUpTempRoot() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        File[] files = getTempRepoRoot().toFile().listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(f -> checkDeletePath(f, cal.getTime()));
        }
    }

    /**
     * Recursively delete one day old files and folders
     * @param file the current root file or folder
     * @param date the expiry date
     */
    private void checkDeletePath(File file, Date date) {
        if (FileUtils.isFileOlder(file, date)) {
            log.debug("Deleting expired temp file or folder: " + file);
            FileUtils.deleteQuietly(file);
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                Arrays.asList(files).forEach(f -> checkDeletePath(f, date));
            }
        }
    }

    /**
     * Moves the repository from the repoPath the the newRepoPath.
     * If the directory specified by the repoPath does not exists, false is returned.
     * @param repoPath the repository path
     * @param newRepoPath the new repository path
     */
    public boolean moveRepoFolder(String repoPath, String newRepoPath) throws IOException {
        Path from = getRepoRoot().resolve(repoPath);
        Path to = getRepoRoot().resolve(newRepoPath);
        if (Files.exists(from)) {
            FileUtils.copyDirectory(from.toFile(), to.toFile());
            return true;
        }
        return false;
    }


    /**
     * Creates a temporary repository folder for the given repository-backed value object
     * @param vo the message
     * @param copyToTemp whether to copy all resources to the associated temporary directory or not
     */
    public void createTempEditRepoFolder(IRepoBackedVo vo, boolean copyToTemp) throws IOException {

        String editRepoPath = getNewTempDir().getPath();
        vo.setEditRepoPath(editRepoPath);

        if (copyToTemp) {

            // For existing messages, copy the existing message repo to the new repository
            if (StringUtils.isNotBlank(vo.getRepoPath())) {
                Path srcPath = getRepoRoot().resolve(vo.getRepoPath());
                Path dstPath = getRepoRoot().resolve(editRepoPath);
                if (Files.exists(srcPath)) {
                    log.info("Copy folder " + srcPath + " to temporary folder " + dstPath);
                    FileUtils.copyDirectory(srcPath.toFile(), dstPath.toFile(), true);
                }
            }

            // Point any embedded links and images to the temporary repository folder
            vo.rewriteRepoPath(vo.getRepoPath(), vo.getEditRepoPath());
        }
    }


    /**
     * Copy new files from the temporary edit-repo path to the actual repo folder associated with the value object
     * @param vo the value object to update
     */
    public void updateRepoFolderFromTempEditFolder(IRepoBackedVo vo) throws IOException {

        if (vo != null && StringUtils.isNotBlank(vo.getRepoPath()) && StringUtils.isNotBlank(vo.getEditRepoPath())) {

            Path srcPath = getRepoRoot().resolve(vo.getEditRepoPath());
            Path dstPath = getRepoRoot().resolve(vo.getRepoPath());
            String revision = String.valueOf(vo.getRevision());

            if (Files.exists(srcPath)) {

                // Case 1: If this is a new publication, copy the entire directory
                if (!Files.exists(dstPath)) {
                    log.info("Syncing folder " + srcPath + " with " + dstPath);
                    FileUtils.copyDirectory(srcPath.toFile(), dstPath.toFile(), true);

                    // Case 2: Copy the latest revision sub-folder back to the source folder
                } else if (Files.exists(srcPath.resolve(revision))) {
                    log.info("Syncing revision " + revision + " of folder " + srcPath + " with folder " + dstPath);
                    FileUtils.copyDirectory(srcPath.resolve(revision).toFile(), dstPath.resolve(revision).toFile(), true);
                } else {
                    log.info("No new revision files to sync");
                }
            }
        }
    }
}
