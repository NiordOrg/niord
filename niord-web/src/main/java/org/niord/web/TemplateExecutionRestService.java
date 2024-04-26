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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.aton.LightCharacterParser;
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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for executing message templates.
 *
 * Message templates are actually defined by categories of type "TEMPLATE".
 */
@Path("/templates")
@RequestScoped
@Transactional
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
    public List<ParamTypeVo> getParamTypes(@QueryParam("lang") String lang) {

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
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-param-types")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String importParameterTypes(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "param-type-import");
    }


    /** Returns the parameter type with the given id */
    @GET
    @Path("/parameter-type/{id}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public ParamTypeVo getParamType(
            @PathParam("id") Integer id,
            @QueryParam("lang") String lang) {

        ParamTypeVo type = templateExecutionService.getParamType(id)
                .toVo(DataFilter.get());

        if (StringUtils.isNotBlank(lang) && type instanceof ListParamTypeVo) {
            ListParamTypeVo listType = (ListParamTypeVo)type;
            if  (listType.getValues() != null) {
                listType.getValues().forEach(t -> t.sortDescs(lang));
            }
        }

        return type;
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
            message = messageRestService.getSystemMessage(executeTemplate.getMessageId(), null);
        }

        // Resolve the category to use
        Category category = (executeTemplate.getCategory() != null) ? new Category(executeTemplate.getCategory()) : null;
        if (category == null && executeTemplate.getCategoryId() != null) {
            category = categoryService.getCategoryDetails(executeTemplate.getCategoryId());
        }

        return templateExecutionService.executeTemplate(category, message, executeTemplate.getTemplateParams());
    }


    /**
     * *******************************************
     * Field Validation
     * *******************************************
     */


    /** Validate the light character */
    @POST
    @Path("/validate-light-character")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public NgRemoveValidateResponse validateLightCharacter(NgRemoveValidateParam param) throws Exception {
        NgRemoveValidateResponse response = new NgRemoveValidateResponse();
        response.setValue(param.getValue());
        try {
            LightCharacterParser.getInstance().parse(param.getValue());
            response.setIsValid(true);
        } catch (Exception e) {
            response.setIsValid(false);
        }
        return response;
    }


    /** Validate the call sign */
    @POST
    @Path("/validate-call-sign")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public NgRemoveValidateResponse validateCallSign(NgRemoveValidateParam param) throws Exception {
        NgRemoveValidateResponse response = new NgRemoveValidateResponse();
        response.setValue(param.getValue().toUpperCase());
        response.setIsValid(LightCharacterParser.getInstance().telephonyCodeValid(param.getValue()));
        return response;
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


    /** Parameter class used by the generic ng-remove-validate directive */
    public static class NgRemoveValidateParam implements IJsonSerializable {
        String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }


    /** Response class used by the generic ng-remove-validate directive */
    public static class NgRemoveValidateResponse implements IJsonSerializable {
        String value;
        boolean valid;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean getIsValid() {
            return valid;
        }

        public void setIsValid(boolean valid) {
            this.valid = valid;
        }
    }
}

