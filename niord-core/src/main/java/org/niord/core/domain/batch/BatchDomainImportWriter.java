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

import org.niord.core.area.Area;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.Category;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the domains to the database
 */
@Named
public class BatchDomainImportWriter extends AbstractItemHandler {

    @Inject
    DomainService domainService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Domain domain = (Domain) i;

            // Replace areas with the persisted versions
            domain.setAreas(domainService.persistedList(Area.class, domain.getAreas()));

            // Substitute the categories with the persisted ones
            domain.setCategories(domainService.persistedList(Category.class, domain.getCategories()));

            domainService.saveEntity(domain);
        }
        getLog().info(String.format("Persisted %d domains in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
