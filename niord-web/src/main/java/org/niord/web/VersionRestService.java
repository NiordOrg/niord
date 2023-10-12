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

import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.NiordApp;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * An extremely simple REST endpoint that returns the build version of the Niord back-end
 */
@Path("/version")
public class VersionRestService {

    @Inject
    NiordApp niordApp;


    /** Returns the build-version of the Niord back-end **/
    @GET
    @Path("/build-version")
    @Produces("text/plain")
    @NoCache
    public String buildVersion() {
        return niordApp.getBuildVersion();
    }

}
