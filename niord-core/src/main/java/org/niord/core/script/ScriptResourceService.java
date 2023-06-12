/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.script.vo.ScriptResourceVo;
import org.niord.core.service.BaseService;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Main interface for accessing and processing script resource, i.e. Freemarker templates and JavaScript resources
 */
@RequestScoped
public class ScriptResourceService extends BaseService {

    @Inject
    UserService userService;

    @Inject
    NiordApp app;

    @Inject
    Logger log;


    /**
     * Saves the script resource
     *
     * @param resource the script resource to save
     * @return the saved resource
     */
    @Transactional
    public ScriptResource saveScriptResource(ScriptResource resource) {

        // Update the type from the path extension
        resource.updateType();

        // Save the resource
        resource = saveEntity(resource);

        // Save a ScriptResourceHistory entity for the resource
        saveScriptResourceHistory(resource);

        return resource;
    }


    /**
     * Returns the script resource with the given ID, or null if not found
     * @param id the resource id
     * @return the script resource with the given ID, or null if not found
     */
    public ScriptResource findById(Integer id) {
        return getByPrimaryKey(ScriptResource.class, id);
    }


    /**
     * Returns the script resource with the given path, or null if not found
     * @param path the script resource path
     * @return the script resource with the given path, or null if not found
     */
    public ScriptResource findByPath(String path) {
        try {
            return em.createNamedQuery("ScriptResource.findByPath", ScriptResource.class)
                    .setParameter("path", path)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the script resource with the given path, either directly from the database or loaded from
     * the class path. Returns null if not found
     * @param path the script resource path
     * @return the script resource with the given path, or null if not found
     * @noinspection all
     */
    public ScriptResource findOrLoadFromClassPath(String path) {
        try {
            ScriptResource resource = findByPath(path);
            if (resource == null) {
                resource = readScriptResourceFromClassPath(path);
                if (resource != null) {
                    log.info("Loading script resource from classpath " + resource.getPath());
                    resource = saveScriptResource(resource);
                }
            }
            return resource;
        } catch (Exception e) {
            log.error("Error finding or loading script from resource " + path);
            return null;
        }
    }


    /**
     * Returns the script resources with the types
     * @param types the script resource types
     * @return the script resources with the given types
     */
    public List<ScriptResource> findByTypes(ScriptResource.Type... types) {

        Set<ScriptResource.Type> typeSet = Arrays.stream(types).collect(Collectors.toSet());

        return em.createNamedQuery("ScriptResource.findByTypes", ScriptResource.class)
                .setParameter("types", typeSet)
                .getResultList();
    }


    /**
     * Returns all script resources
     * @return all script resources
     */
    public List<ScriptResource> findAll() {
        return em.createNamedQuery("ScriptResource.findAll", ScriptResource.class)
                .getResultList();
    }


    /**
     * Returns all script resource paths
     * @return all script resource paths
     */
    public Set<String> findAllScriptResourcePaths() {
        return new HashSet<>(em.createNamedQuery("ScriptResource.findAllPaths", String.class)
                .getResultList());
    }


    /**
     * Creates a new script resource based on the resource parameter
     * @param resource the resource to create
     * @return the created script resource
     */
    public ScriptResource createScriptResource(ScriptResource resource) {
        ScriptResource original = findByPath(resource.getPath());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create script resource with duplicate path " + resource.getPath());
        }

        return saveScriptResource(resource);
    }


    /**
     * Updates the script resource data from the resource parameter
     * @param resource the script resource to update
     * @return the updated script resource
     */
    public ScriptResource updateScriptResource(ScriptResource resource) {
        ScriptResource original = findById(resource.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing script resource " + resource.getPath());
        }

        // Copy the resource data
        original.setType(resource.getType());
        original.setPath(resource.getPath());
        original.setContent(resource.getContent());

        return saveScriptResource(original);
    }


    /**
     * Deletes the script resource with the given path
     * @param id the ID of the script resource to delete
     * @noinspection all
     */
    @Transactional
    public boolean deleteScriptResource(Integer id) {

        ScriptResource resource = findById(id);
        if (resource != null) {
            // Delete all resource history entries
            getScriptResourceHistory(id).forEach(this::remove);
            // Delete the actual resource
            remove(resource);
            return true;
        }
        return false;
    }


    /**
     * Reload all class path-based script resources from the file system
     * @return the number of script resources reloaded
     */
    public int reloadScriptResourcesFromClassPath() {

        int updates = 0;
        for (ScriptResource resource : findAll()) {
            ScriptResource cpTemplate = readScriptResourceFromClassPath(resource.getPath());
            if (cpTemplate != null && !Objects.equals(resource.getContent(), cpTemplate.getContent())) {
                log.info("Updating script resource from classpath " + resource.getPath());
                resource.setContent(cpTemplate.getContent());
                saveScriptResource(resource);
                updates++;
            }
        }

        return updates;
    }


    /**
     * Reads, but does not persist, a script resource with the given path from the class path.
     * Returns null if none are found
     * @return the classpath script resource at the given path or null if not found
     */
    public ScriptResource readScriptResourceFromClassPath(String path) {

        String resourcePath = path;
        if (!resourcePath.startsWith("/")) {
            resourcePath = "/" + resourcePath;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String content = null;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            content = IOUtils.toString(in ,"UTF-8");
        } catch (Exception ignored) {
        }

        if (StringUtils.isNotBlank(content)) {
            ScriptResource scriptResource = new ScriptResource();
            scriptResource.setPath(path);
            scriptResource.setContent(content);
            return scriptResource;
        }
        return null;
    }


    /***************************************/
    /** Script Resource History           **/
    /***************************************/


    /**
     * Saves a history entity containing a snapshot of the script resource
     *
     * @param resource the resource to save a snapshot for
     */
    @Transactional
    public void saveScriptResourceHistory(ScriptResource resource) {

        try {
            ScriptResourceHistory hist = new ScriptResourceHistory();
            hist.setResource(resource);
            hist.setUser(userService.currentUser());
            hist.setCreated(new Date());
            hist.setVersion(resource.getVersion() + 1);

            // Create a snapshot of the resource
            ObjectMapper jsonMapper = new ObjectMapper();
            jsonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false); // Use ISO-8601 format
            ScriptResourceVo snapshot = resource.toVo();
            hist.compressSnapshot(jsonMapper.writeValueAsString(snapshot));

            saveEntity(hist);

        } catch (Exception e) {
            log.error("Error saving a history entry for resource " + resource.getId(), e);
            // NB: Don't propagate errors
        }
    }

    /**
     * Returns the script resource history for the given template ID
     *
     * @param resourceId the template ID
     * @return the template history
     */
    public List<ScriptResourceHistory> getScriptResourceHistory(Integer resourceId) {
        return em.createNamedQuery("ScriptResourceHistory.findByResourceId", ScriptResourceHistory.class)
                .setParameter("resourceId", resourceId)
                .getResultList();
    }
}
