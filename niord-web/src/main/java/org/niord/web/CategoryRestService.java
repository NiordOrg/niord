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

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.category.Category;
import org.niord.core.category.CategorySearchParams;
import org.niord.core.category.CategoryService;
import org.niord.core.category.CategoryType;
import org.niord.core.category.TemplateExecutionService;
import org.niord.core.category.vo.SystemCategoryVo;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST interface for accessing categories.
 */
@Path("/categories")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class CategoryRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    CategoryService categoryService;

    @Inject
    TemplateExecutionService templateExecutionService;

    @Inject
    MessageRestService messageRestService;


    /**
     * Searches for categories matching the given name in the given language
     *
     * @param lang  the language
     * @param name  the search name
     * @param domain  if defined, restricts the search to the categories of the given domain
     * @param limit the maximum number of results
     * @return the search result
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemCategoryVo> searchCategories(
            @QueryParam("lang") String lang,
            @QueryParam("name") String name,
            @QueryParam("type") CategoryType type,
            @QueryParam("ancestorId") Integer ancestorId,
            @QueryParam("domain") @DefaultValue("false") String domain,
            @QueryParam("inactive") @DefaultValue("false") boolean inactive,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        return searchCategories(lang, name, type, ancestorId, domain, inactive, limit, null);
    }


    /**
     * Searches for categories matching the given name in the given language.
     *
     * This version of the search function extends the functionality with support
     * for filtering by AtoNs
     *
     * @param lang  the language
     * @param name  the search name
     * @param domain  if defined, restricts the search to the categories of the given domain
     * @param limit the maximum number of results
     * @return the search result
     */
    @PUT
    @Path("/search")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemCategoryVo> searchCategories(
            @QueryParam("lang") String lang,
            @QueryParam("name") String name,
            @QueryParam("type") CategoryType type,
            @QueryParam("ancestorId") Integer ancestorId,
            @QueryParam("domain") String domain,
            @QueryParam("inactive") @DefaultValue("false") boolean inactive,
            @QueryParam("limit") @DefaultValue("100") int limit,
            List<AtonNodeVo> atons) {

        DataFilter filter = DataFilter.get()
                .fields(DataFilter.PARENT)
                .lang(lang);

        CategorySearchParams params = new CategorySearchParams();
        params.language(lang)
                .type(type)
                .name(name)
                .ancestorId(ancestorId)
                .domain(domain)
                .inactive(inactive)
                .atons(atons)
                .maxSize(limit);

        log.info(String.format("Searching for categories %s", params));

        return categoryService.searchCategories(params).stream()
                .map(c -> c.toVo(SystemCategoryVo.class, filter))
                .collect(Collectors.toList());
    }


    /** Returns the categories with the given IDs */
    @GET
    @Path("/search/{categoryIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemCategoryVo> searchCategoryIds(@PathParam("categoryIds") String categoryIds,
                                      @QueryParam("lang") @DefaultValue("en") String lang,
                                      @QueryParam("limit") @DefaultValue("1000") int limit) {

        log.debug(String.format("Searching for categories ids=%s, lang=%s, limit=%d", categoryIds, lang, limit));

        Set<Integer> ids = Arrays.stream(categoryIds.split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.PARENT);

        return categoryService.getCategoryDetails(ids).stream()
                .map(c -> c.toVo(SystemCategoryVo.class, filter))
                .limit(limit)
                .collect(Collectors.toList());
    }


    /** Returns all categories via a list of hierarchical root categories */
    @GET
    @Path("/category-roots")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemCategoryVo> getCategoryRoots(@QueryParam("lang") String lang) {

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.CHILDREN);

        return categoryService.getCategoryTree().stream()
                .map(c -> c.toVo(SystemCategoryVo.class, filter))
                .collect(Collectors.toList());
    }


    /** Returns all categories via a list of hierarchical root categories */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SystemCategoryVo> getAll() {

        DataFilter filter = DataFilter.get()
                .fields(DataFilter.CHILDREN);

        return categoryService.getCategoryTree().stream()
                .map(c -> c.toVo(SystemCategoryVo.class, filter))
                .collect(Collectors.toList());
    }


    /** Returns the category with the given ID */
    @GET
    @Path("/category/{categoryId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public SystemCategoryVo getCategory(@PathParam("categoryId") Integer categoryId) throws Exception {
        log.debug("Getting category " + categoryId);
        Category category = categoryService.getCategoryDetails(categoryId);
        // Return the category without parent and child categories
        return category == null ? null : category.toVo(SystemCategoryVo.class, DataFilter.get());
    }


    /** Creates a new category */
    @POST
    @Path("/category/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    public SystemCategoryVo createCategory(SystemCategoryVo categoryVo) throws Exception {
        Category category = new Category(categoryVo);
        log.info("Creating category " + category);
        Integer parentId = (categoryVo.getParent() == null) ? null : categoryVo.getParent().getId();
        return categoryService.createCategory(category, parentId).toVo(SystemCategoryVo.class, DataFilter.get());
    }


    /** Updates an category */
    @PUT
    @Path("/category/{categoryId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    public SystemCategoryVo updateCategory(@PathParam("categoryId") Integer categoryId, SystemCategoryVo categoryVo) throws Exception {
        if (!Objects.equals(categoryId, categoryVo.getId())) {
            throw new WebApplicationException(400);
        }
        Category category = new Category(categoryVo);
        log.info("Updating category " + category);
        return categoryService.updateCategoryData(category).toVo(SystemCategoryVo.class, DataFilter.get());
    }


    /** Deletes the given category */
    @DELETE
    @Path("/category/{categoryId}")
    @RolesAllowed(Roles.SYSADMIN)
    public boolean deleteCategory(@PathParam("categoryId") Integer categoryId) throws Exception {
        log.info("Deleting category " + categoryId);
        return categoryService.deleteCategory(categoryId);
    }


    /** Move an category to a new parent category **/
    @PUT
    @Path("/move-category")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    public boolean moveCategory(MoveCategoryVo moveCategoryVo) throws Exception {
        log.info("Moving category " + moveCategoryVo.getCategoryId() + " to " + moveCategoryVo.getParentId());
        return categoryService.moveCategory(moveCategoryVo.getCategoryId(), moveCategoryVo.getParentId());
    }


    /**
     * Imports an uploaded Categories json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-categories")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importCategories(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "category-import");
    }



    /***************************************/
    /** Template Execution                **/
    /***************************************/


    /** Executes a message template category on the given message ID */
    @PUT
    @Path("/execute")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.USER)
    @GZIP
    @NoCache
    public SystemMessageVo applyTemplate(ExecuteTemplateVo executeTemplate) throws Exception {


        SystemMessageVo message = executeTemplate.getMessage();
        if (message == null) {
            if (StringUtils.isBlank(executeTemplate.getMessageId())) {
                throw new WebApplicationException("No message specified", 400);
            }

            // NB: Access to the message is checked:
            message = messageRestService.getSystemMessage(executeTemplate.getMessageId());
        }
        return templateExecutionService.executeTemplate(
                new Category(executeTemplate.getCategory()),
                message);
    }


    /**
     * ******************
     * Helper classes
     * *******************
     */

    /** Encapsulates the parameters used for moving an category to a new parent category */
    @SuppressWarnings("unused")
    public static class MoveCategoryVo implements IJsonSerializable {
        Integer categoryId, parentId;

        public Integer getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Integer categoryId) {
            this.categoryId = categoryId;
        }

        public Integer getParentId() {
            return parentId;
        }

        public void setParentId(Integer parentId) {
            this.parentId = parentId;
        }
    }


    /** Encapsulates the parameters used for moving an category to a new parent category */
    @SuppressWarnings("unused")
    public static class ExecuteTemplateVo implements IJsonSerializable {
        SystemMessageVo message;
        String messageId;
        SystemCategoryVo category;

        public SystemMessageVo getMessage() {
            return message;
        }

        public void setMessage(SystemMessageVo message) {
            this.message = message;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public SystemCategoryVo getCategory() {
            return category;
        }

        public void setCategory(SystemCategoryVo category) {
            this.category = category;
        }
    }
}

