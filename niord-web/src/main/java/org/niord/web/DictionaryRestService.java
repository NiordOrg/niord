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

import org.apache.commons.lang3.StringEscapeUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.dictionary.DictionaryEntry;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.dictionary.vo.DictionaryEntryVo;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing charts.
 */
@Path("/dictionaries")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class DictionaryRestService {

    @Inject
    Logger log;

    @Inject
    DictionaryService dictionaryService;


    /** Returns the dictionaries with the given name */
    @GET
    @Path("/names")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<String> getDictionaryNames() {
        return dictionaryService.getDictionaryNames();
    }


    /** Returns the dictionaries with the given name */
    @GET
    @Path("/dictionary/{name}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public DictionaryVo getDictionary(@PathParam("name") String name) {
        return dictionaryService.getCachedDictionary(name);
    }


    /** Returns the dictionary entries with the given name */
    @GET
    @Path("/dictionary/{name}/entries")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<DictionaryEntryVo> getDictionaryEntries(@PathParam("name") String name) {
        return dictionaryService.getCachedDictionary(name)
                .getEntries()
                .values()
                .stream()
                .sorted(Comparator.comparing(e -> e.getKey().toLowerCase()))
                .collect(Collectors.toList());
    }


    /** Exports the dictionary as a text file */
    @GET
    @Path("/dictionary/{name}_{lang}.properties")
    @Produces("text/plain;charset=UTF-8")
    @GZIP
    @NoCache
    public String getDictionaryEntries(@PathParam("name") String name, @PathParam("lang") String lang) {
        DictionaryVo dict = dictionaryService.getCachedDictionary(name);
        if (dict == null) {
            throw new WebApplicationException(404);
        }

        StringBuilder result = new StringBuilder();
        dict.getEntries()
                .values()
                .stream()
                .filter(e -> e.getDesc(lang) != null && e.getDesc(lang).descDefined())
                .sorted(Comparator.comparing(e2 -> e2.getKey().toLowerCase()))
                .forEach(entry ->
                        result.append(entry.getKey())
                        .append(" = ")
                        .append(encodeValue(entry.getDesc(lang).getValue()))
                        .append("\n"));

        return result.toString();
    }


    /** Encodes the value as a property file value **/
    private String encodeValue(String value) {
        return StringEscapeUtils.escapeEcmaScript(value);
    }


    /** Creates a new dictionary entry */
    @POST
    @Path("/dictionary/{name}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    public DictionaryEntryVo createDictionary(@PathParam("name") String name, DictionaryEntryVo entryVo) throws Exception {
        DictionaryEntry entry = new DictionaryEntry(entryVo);
        log.info("Creating dictionary entry " + entryVo);
        return dictionaryService.createEntry(name, entry).toVo(DataFilter.get());
    }


    /** Updates a dictionary entry */
    @PUT
    @Path("/dictionary/{name}/{key}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.SYSADMIN)
    public DictionaryEntryVo updateDictionary(@PathParam("name") String name, @PathParam("key") String key, DictionaryEntryVo entryVo) throws Exception {
        if (!Objects.equals(key, entryVo.getKey())) {
            throw new WebApplicationException(400);
        }
        DictionaryEntry entry = new DictionaryEntry(entryVo);
        log.info("Updating dictionary entry " + entry);
        return dictionaryService.updateEntry(name, entry).toVo(DataFilter.get());
    }


    /** Deletes the given dictionary entry */
    @DELETE
    @Path("/dictionary/{name}/{key}")
    @RolesAllowed(Roles.SYSADMIN)
    public boolean deleteDictionary(@PathParam("name") String name, @PathParam("key") String key) throws Exception {
        log.info("Deleting dictionary entry " + key);
        return dictionaryService.deleteEntry(name, key);
    }


    /** Reload all dictionary values from the resource bundles */
    @PUT
    @Path("/reload-resource-bundles")
    @Produces("text/plain")
    @RolesAllowed(Roles.SYSADMIN)
    public String reloadDictionariesFromResourceBundles() throws Exception {

        long t0 = System.currentTimeMillis();

        // Load default resource bundles into dictionaries - override existing values
        dictionaryService.loadDefaultResourceBundles(true);

        return String.format("Resource bundles loaded in %d ms", (System.currentTimeMillis() - t0));
    }


}
