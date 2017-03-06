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
package org.niord.core.area.batch;

import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.area.vo.SystemAreaVo;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.model.DataFilter;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters areas that need to be a added or updated
 */
@Named
public class BatchAreaImportProcessor extends AbstractItemHandler {

    @Inject
    AreaService areaService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        SystemAreaVo areaVo = (SystemAreaVo) item;

        DataFilter filter = DataFilter.get()
                .fields("geometry", "parent");
        Area area = new Area(areaVo, filter);

        getLog().info("Creating or updating area " + area);
        return areaService.importArea(area, true, true);
    }
}
