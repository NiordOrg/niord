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

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;

/**
 * An extremely simple REST endpoint that can be used to monitor the basic dead-or-alive state of the Niord service
 */
@Path("/monitor")
public class MonitorRestService {

    @Inject
    protected EntityManager em;


    /** Can be used to see if Niord is running at all **/
    @GET
    @Path("/ping")
    @Produces("text/plain")
    public String ping() {
        return "pong";
    }


    /** Verifies that Niord is running and that we have a valid DB connection **/
    @GET
    @Path("/db")
    @Produces("text/plain")
    public String db() {

        try {
            // Just check that the database exists
            em.createNativeQuery("select 1")
                .getSingleResult();

            return "success";

        } catch (Exception ex) {
            throw new WebApplicationException("Error accessing database: " + ex.getMessage(), 500);
        }
    }

}
