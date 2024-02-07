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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
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
 * REST interface for accessing areas.
 */
@Path("/settings")
@RequestScoped
@Transactional
@RolesAllowed(Roles.SYSADMIN)
public class SettingsRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    SettingsService settingsService;

    @Inject
    UserService userService;


    /**
     * Returns all editable settings. Security is checked programmatically.
     * Either the user must pass a ticket along, or else, usual security checks applies.
     *
     * The ticket can be requested via Ajax call to /rest/tickets/ticket?role=sysadmin
     *
     * @return returns all editable settings
     */
    @GET
    @Path("/editable-settings")
    @Produces("application/json;charset=UTF-8")
    @PermitAll // Checked programmatically
    @GZIP
    @NoCache
    public List<SettingVo> getSettings() {
        // If a ticket is defined, check if programmatically
        if (!userService.isCallerInRole(Roles.SYSADMIN)) {
            log.info("Could not access Settings " + userService.currentUser());
            log.info("Current roles " + userService.currentUserRoles());
            throw new WebApplicationException(403);
        }

        return settingsService
                .getAllEditable()
                .stream()
                .map(SettingVo::new)
                .collect(Collectors.toList());
    }


    @GET
    @Path("/setting/{key}")
    @Produces("application/json")
    public SettingVo getSetting(@PathParam("key") String key) throws Exception {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("Invalid empty key");
        }
        log.info("Getting setting " + key);
        SettingVo setting = new SettingVo();
        setting.setKey(key);
        setting.setValue(settingsService.get(key.trim()));
        return setting;
    }


    @PUT
    @Path("/setting/{key}")
    @Consumes("application/json")
    @Produces("application/json")
    public SettingVo updateSetting(@PathParam("key") String key, SettingVo settingVo) throws Exception {
        if (!Objects.equals(key, settingVo.getKey())) {
            throw new WebApplicationException(400);
        }
        Setting setting = settingVo.toEntity();
        log.info("Updating setting " + setting);
        return new SettingVo(settingsService.set(setting));
    }


    /**
     * Imports an uploaded settings json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-settings")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    public String importCharts(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "settings-import");
    }

    /**
     * ******************
     * Helper classes
     * *******************
     */

    /**
     * VO for settings
     */
    public static class SettingVo implements IJsonSerializable {

        String key;
        Object value;
        String description;
        Setting.Type type;

        /**
         * Constructor
         */
        public SettingVo() {
        }

        /**
         * Constructor
         */
        public SettingVo(Setting setting) {
            key = setting.getKey();
            if (setting.getType() != Setting.Type.Password) {
                value = setting.getValue();
            }
            description = setting.getDescription();
            type = setting.getType();
        }

        /**
         * Converts the VO to an entity
         */
        public Setting toEntity() {
            Setting setting = new Setting();
            setting.setKey(key);
            setting.setValue(value);
            setting.setDescription(description);
            setting.setType(type);
            return setting;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Setting.Type getType() {
            return type;
        }

        public void setType(Setting.Type type) {
            this.type = type;
        }
    }
}
