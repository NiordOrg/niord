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
package org.niord.web;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.category.StandardParamType;
import org.niord.core.category.TemplateExecutionService;
import org.niord.core.category.vo.ListParamTypeVo;
import org.niord.core.category.vo.ParamTypeVo;
import org.niord.core.category.vo.StandardParamTypeVo;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for executing message templates.
 *
 * Message templates are actually defined by categories of type "TEMPLATE".
 */
@Path("/templates")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class TemplateExecutionRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    CategoryService categoryService;

    @Inject
    TemplateExecutionService templateExecutionService;

    @Inject
    MessageRestService messageRestService;


    /**
     * *******************************************
     * parameter types functionality
     * *******************************************
     */

    /**
     * Returns the parameter types
     *
     * @param lang the language return data for
     * @return the parameter types
     */
    @GET
    @Path("/parameter-types")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<ParamTypeVo> getParamTypes(@QueryParam("lang") @DefaultValue("en") String lang) {

        DataFilter filter = DataFilter.get().lang(lang);

        return templateExecutionService.getParamTypes().stream()
                .map(pt -> pt.toVo(filter))
                .collect(Collectors.toList());
    }


    /**
     * Returns the parameter types with a few changes:
     * <ul>
     *     <li>Resets all IDs to make them suitable for export/import</li>
     *     <li>Removes standard parameter types, as these are managed by the system</li>
     * </ul>
     *
     * @return the parameter types
     */
    @GET
    @Path("/parameter-types/export")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<ParamTypeVo> exportParamTypes() {

        List<ParamTypeVo> result = getParamTypes(null);

        // Remove standard parameter types
        result.removeIf(pt -> pt instanceof StandardParamTypeVo);

        // Reset all IDs
        result.forEach(pt -> pt.setId(null));
        result.stream()
            .filter(pt -> pt instanceof ListParamTypeVo)
            .map(pt -> (ListParamTypeVo)pt)
            .flatMap(pt -> pt.getValues().stream())
            .forEach(v -> {
                v.setId(null);
                v.setDescs(null);
                v.setAtonFilter(null);
            });

        return result;
    }


    /**
     * Imports an uploaded parameter types json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-param-types")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importParameterTypes(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "param-type-import");
    }


    /** Returns the parameter type with the given id */
    @GET
    @Path("/parameter-type/{id}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public ParamTypeVo getParamType(@PathParam("id") Integer id) {

        return templateExecutionService.getParamType(id)
                .toVo(DataFilter.get());
    }


    /** Creates a new parameter type */
    @POST
    @Path("/parameter-type/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public ParamTypeVo createParamType(ParamTypeVo paramType) throws Exception {
        if (paramType instanceof StandardParamTypeVo) {
            throw new WebApplicationException("Illegal parameter type", 400);
        }
        log.info("Creating parameter type " + paramType);
        return templateExecutionService.createParamType(paramType.toEntity())
                .toVo(DataFilter.get());
    }


    /** Updates an existing parameter type */
    @PUT
    @Path("/parameter-type/{id}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public ParamTypeVo updateParamType(@PathParam("id") Integer id, ParamTypeVo paramType) throws Exception {
        if (!Objects.equals(id, paramType.getId())) {
            throw new WebApplicationException(400);
        }
        if (paramType instanceof StandardParamTypeVo) {
            throw new WebApplicationException("Illegal parameter type", 400);
        }

        log.info("Updating parameter type " + paramType);
        return templateExecutionService.updateParamType(paramType.toEntity())
                .toVo(DataFilter.get());
    }


    /** Deletes an existing parameter type */
    @DELETE
    @Path("/parameter-type/{id}")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    @GZIP
    @NoCache
    public void deleteParamType(@PathParam("id") Integer id) throws Exception {
        if (templateExecutionService.getParamType(id) instanceof StandardParamType) {
            throw new WebApplicationException("Illegal parameter type", 400);
        }
        log.info("Deleting parameter type " + id);
        templateExecutionService.deleteParamType(id);
    }


    /**
     * *******************************************
     * Template Execution
     * *******************************************
     */

    /** Executes a message template category on the given message ID */
    @PUT
    @Path("/execute")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.USER)
    @GZIP
    @NoCache
    public SystemMessageVo applyTemplate(ExecuteTemplateVo executeTemplate) throws Exception {

        if (!executeTemplate.valid()) {
            throw new WebApplicationException("No proper message specified", 400);
        }

        // Resolve the message to use
        SystemMessageVo message = executeTemplate.getMessage();
        if (message == null) {
            // NB: Access to the message is checked:
            message = messageRestService.getSystemMessage(executeTemplate.getMessageId());
        }

        // Resolve the category to use
        Category category = (executeTemplate.getCategory() != null) ? new Category(executeTemplate.getCategory()) : null;
        if (category == null && executeTemplate.getCategoryId() != null) {
            category = categoryService.getCategoryDetails(executeTemplate.getCategoryId());
        }

        return templateExecutionService.executeTemplate(category, message, executeTemplate.getTemplateParams());
    }


    /**
     * ******************
     * Helper classes
     * *******************
     */

    /** Encapsulates the parameters used for moving an category to a new parent category */
    @SuppressWarnings("unused")
    public static class ExecuteTemplateVo implements IJsonSerializable {
        SystemMessageVo message;
        String messageId;
        SystemCategoryVo category;
        Integer categoryId;
        List templateParams;

        public boolean valid() {
            return (message != null || StringUtils.isNotBlank(messageId));
        }

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

        public Integer getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Integer categoryId) {
            this.categoryId = categoryId;
        }

        public List getTemplateParams() {
            return templateParams;
        }

        public void setTemplateParams(List templateParams) {
            this.templateParams = templateParams;
        }
    }
}

