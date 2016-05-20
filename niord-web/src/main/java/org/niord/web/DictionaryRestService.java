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

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.dictionary.vo.DictionaryEntryVo;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;
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
                .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
                .collect(Collectors.toList());
    }

}
