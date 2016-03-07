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
package org.niord.core.aton.batch;

import org.niord.core.aton.AtonNode;
import org.niord.core.aton.AtonService;
import org.niord.core.batch.AbstractItemHandler;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the AtoNs to the database
 */
@Named
public class BatchAtonImportWriter extends AbstractItemHandler {

    @Inject
    AtonService atonService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            AtonNode aton = (AtonNode) i;
            atonService.saveEntity(aton);
        }
        getLog().info(String.format("Persisted %d AtoNs in %d s", items.size(), (System.currentTimeMillis() - t0) / 1000L));
    }
}
