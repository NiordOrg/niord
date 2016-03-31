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
package org.niord.web;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;
import org.niord.core.user.TicketService;
import org.niord.model.IJsonSerializable;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing areas.
 */
@Path("/settings")
@Stateless
@SecurityDomain("keycloak")
@RolesAllowed({"sysadmin"})
public class SettingsRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    SettingsService settingsService;

    @Inject
    TicketService ticketService;


    /**
     * Returns all editable settings
     *
     * @return returns all editable settings
     */
    @GET
    @Path("/editable")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<SettingVo> getEditableSettings() {
        return settingsService
                .getAllEditable()
                .stream()
                .map(SettingVo::new)
                .collect(Collectors.toList());
    }


    /**
     * Returns all editable settings. Security is checked programmatically.
     *
     * @return returns all editable settings
     */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @PermitAll
    @GZIP
    @NoCache
    public List<SettingVo> getSettings(@QueryParam("ticket") String ticket) {
        // Check the ticket programmatically
        if (!ticketService.validateTicket(ticket, "sysadmin")) {
            throw new WebApplicationException(403);
        }

        return settingsService
                .getAllEditable()
                .stream()
                .map(SettingVo::new)
                .collect(Collectors.toList());
    }


    /**
     * Returns a ticket to be used in a subsequent call to export settings data
     *
     * @return a ticket
     */
    @GET
    @Path("/export-ticket")
    @Produces("text/plain")
    @RolesAllowed("admin")
    @NoCache
    public String getDownloadBatchDataFileTicket() {
        // Because of the @RolesAllowed annotation, we know that the
        // the callee has the "sysadmin" role
        return ticketService.createTicketForRoles("sysadmin");
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
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-settings")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    public String importCharts(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "settings-import");
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
            value = setting.getValue();
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
