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
import org.apache.commons.lang3.StringEscapeUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.dictionary.DictionaryEntry;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.dictionary.vo.DictionaryEntryVo;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.niord.core.dictionary.vo.ExportedDictionaryVo;
import org.niord.core.user.Roles;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing charts.
 */
@Path("/dictionaries")
@RequestScoped
@Transactional
@PermitAll
public class DictionaryRestService extends AbstractBatchableRestService {

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
    public List<DictionaryEntryVo> getDictionaryEntries(
            @PathParam("name") String name,
            @QueryParam("lang") String lang) {
        List<DictionaryEntryVo> entries = dictionaryService.getCachedDictionary(name)
                .getEntries()
                .values()
                .stream()
                .sorted(Comparator.comparing(e -> e.getKey().toLowerCase()))
                .collect(Collectors.toList());

        if (StringUtils.isNotBlank(lang)) {
            entries.forEach(e -> e.sortDescs(lang));
        }
        return entries;
    }


    /** Exports the dictionary as a text file */
    @GET
    @Path("/dictionary/{name}_{lang}.properties")
    @Produces("text/plain;charset=UTF-8")
    @GZIP
    @NoCache
    public String getDictionaryEntriesAsPropertyFile(@PathParam("name") String name, @PathParam("lang") String lang) {
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


    /** Exports the dictionaries with the comma-separated names as a JSON file */
    @GET
    @Path("/dictionary/{names}.json")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<ExportedDictionaryVo> exportDictionary(@PathParam("names") String names) {

        return Arrays.stream(names.split(","))
                .map(name -> dictionaryService.getCachedDictionary(name))
                .filter(Objects::nonNull)
                .map(ExportedDictionaryVo::new)
                .collect(Collectors.toList());
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
    public DictionaryEntryVo createDictionaryEntry(@PathParam("name") String name, DictionaryEntryVo entryVo) throws Exception {
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
    public DictionaryEntryVo updateDictionaryEntry(@PathParam("name") String name, @PathParam("key") String key, DictionaryEntryVo entryVo) throws Exception {
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
    public boolean deleteDictionaryEntry(@PathParam("name") String name, @PathParam("key") String key) throws Exception {
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


    /**
     * Imports an uploaded dictionary json file
     *
     * @param input the multi-part form data input request
     * @return a status
     */
    @POST
    @Path("/upload-dictionaries")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed(Roles.ADMIN)
    public String importDictionaries(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "dictionary-import");
    }


    /**
     * *******************************************
     * AtoN matching
     * *******************************************
     */


    /** Matches an AtoN against a list of dictionary entries */
    @PUT
    @Path("/matches-aton")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.USER)
    @GZIP
    @NoCache
    public DictionaryEntryVo matchesAton(AtonDictEntryListVo params) throws Exception {

        if (params.getAton() == null || params.getValues() == null) {
            throw new WebApplicationException("No proper message specified", 400);
        }

        if (params.getValues().stream().noneMatch(d -> StringUtils.isNotBlank(d.getAtonFilter()))) {
            return null;
        }

        return dictionaryService.matchesAton(params.getAton(), params.getValues());
    }


    /**
     * ******************
     * Helper classes
     * *******************
     */

    /** Encapsulates the parameters used for matching an AtoN against a list of dictionary entries */
    @SuppressWarnings("unused")
    public static class AtonDictEntryListVo implements IJsonSerializable {
        AtonNodeVo aton;
        List<DictionaryEntryVo> values;

        public AtonNodeVo getAton() {
            return aton;
        }

        public void setAton(AtonNodeVo aton) {
            this.aton = aton;
        }

        public List<DictionaryEntryVo> getValues() {
            return values;
        }

        public void setValues(List<DictionaryEntryVo> values) {
            this.values = values;
        }
    }
}
