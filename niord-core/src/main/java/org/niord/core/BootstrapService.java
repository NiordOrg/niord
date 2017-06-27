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

package org.niord.core;

import org.niord.core.domain.Domain;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

/**
 * In case Niord has been started up on a fresh database
 * this service will ensure that there is a "niord-client-master" domain
 * present, that may be used for bootstrapping and setting up the system.
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class BootstrapService extends BaseService {

    @Inject
    Logger log;

    @PostConstruct
    void init() {

        // If no domains have been defined (fresh database), create
        // a Master domain that can be used whilst setting up the system
        if (count(Domain.class) == 0) {
            Domain domain = new Domain();
            domain.setDomainId("niord-client-master");
            domain.setName("Master");
            em.persist(domain);
            log.info("Created Master domain");
        }

        handleUpgrade();
    }

    /**
     * Use for e.g. DB updates. Once all systems have been updated, this method should be empty
     */
    @SuppressWarnings("all")
    private void handleUpgrade() {
        try {

            int updates = em.createNativeQuery(
                    "update PromulgationType c set c.requirement = 'OPTIONAL' where c.requirement is null or c.requirement = ''")
                    .executeUpdate();
            em.flush();
            log.info("*** UPDATED REQUIREMENT OF " + updates + " PROMULGATION TYPES");

        } catch (Exception e) {
            log.error("Error executing SQL ", e);
        }
    }
}
