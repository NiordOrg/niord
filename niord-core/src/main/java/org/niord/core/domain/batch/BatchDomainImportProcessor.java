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
package org.niord.core.domain.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.model.vo.DomainVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters domains that need to be a added or updated
 */
@Named
public class BatchDomainImportProcessor extends AbstractItemHandler {

    @Inject
    DomainService domainService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        DomainVo domainVo = (DomainVo) item;
        Domain domain = new Domain(domainVo);

        // Look up any existing domain with the same client ID
        Domain orig = domainService.findByClientId(domain.getClientId());

        if (orig == null) {
            // Persist new domain
            getLog().info("Persisting new domain " + domain);
            return domain;

        } else if (orig.hasChanged(domain)) {
            // Update original
            getLog().info("Updating domain " + orig.getClientId());
            orig.updateDomain(domainVo);
            return orig;
        }

        // No change, ignore...
        getLog().info("Ignoring unchanged domain " + orig.getClientId());
        return null;
    }
}
