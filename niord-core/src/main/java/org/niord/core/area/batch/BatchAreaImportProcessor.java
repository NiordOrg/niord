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
package org.niord.core.area.batch;

import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.model.DataFilter;
import org.niord.model.vo.AreaVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters charts that need to be a added or updated
 */
@Named
public class BatchAreaImportProcessor extends AbstractItemHandler {

    @Inject
    AreaService areaService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        AreaVo areaVo = (AreaVo) item;

        DataFilter filter = DataFilter.get().fields("geometry", "parent");
        Area area = new Area(areaVo, filter);

        getLog().info("Creating or updating area " + area);
        return areaService.findOrCreateArea(area);
    }
}
