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

import org.apache.commons.lang3.StringEscapeUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.dictionary.DictionaryEntry;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.dictionary.vo.DictionaryEntryVo;
import org.niord.core.dictionary.vo.DictionaryVo;
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
                .sorted((e1, e2) -> e1.getKey().toLowerCase().compareTo(e2.getKey().toLowerCase()))
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
                .sorted((e1, e2) -> e1.getKey().toLowerCase().compareTo(e2.getKey().toLowerCase()))
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
    @RolesAllowed({"sysadmin"})
    public DictionaryEntryVo createArea(@PathParam("name") String name, DictionaryEntryVo entryVo) throws Exception {
        DictionaryEntry entry = new DictionaryEntry(entryVo);
        log.info("Creating dictionary entry " + entryVo);
        return dictionaryService.createEntry(name, entry).toVo(DataFilter.get());
    }


    /** Updates a dictionary entry */
    @PUT
    @Path("/dictionary/{name}/{key}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"sysadmin"})
    public DictionaryEntryVo updateArea(@PathParam("name") String name, @PathParam("key") String key, DictionaryEntryVo entryVo) throws Exception {
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
    @RolesAllowed({"sysadmin"})
    public boolean deleteArea(@PathParam("name") String name, @PathParam("key") String key) throws Exception {
        log.info("Deleting dictionary entry " + key);
        return dictionaryService.deleteEntry(name, key);
    }

}
