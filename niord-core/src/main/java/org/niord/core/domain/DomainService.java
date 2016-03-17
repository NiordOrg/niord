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
package org.niord.core.domain;

import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.List;

/**
 * Provides an interface for managing application domains
 */
public class DomainService extends BaseService {

    @Inject
    private Logger log;

    /**
     * Returns the domain with the given clientId
     * @param clientId the clientId
     * @return the domain with the given clientId
     */
    public Domain findByClientId(String clientId) {
        try {
            return em
                    .createNamedQuery("Domain.findByClientId", Domain.class)
                    .setParameter("clientId", clientId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns all domains
     * @return all domains
     */
    public List<Domain> getDomains() {
        return getAll(Domain.class);
    }

}
