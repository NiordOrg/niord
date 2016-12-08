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

package org.niord.core.area;

import org.niord.core.area.vo.SystemAreaVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.service.BaseService;
import org.niord.core.util.TimeUtils;
import org.niord.model.DataFilter;
import org.niord.model.message.AreaType;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.niord.model.search.PagedSearchParamsVo.SortOrder.ASC;

/**
 * Business interface for accessing Niord firing areas
 */
@Stateless
@SuppressWarnings("unused")
public class FiringAreaService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    AreaService areaService;


    /**
     * Returns any existing firing period with the given area and date interval. Return null if none is found
     * @param area the area
     * @param firingPeriodFromDate the from date
     * @param firingPeriodToDate the to date
     * @return any existing firing period with the given area and date interval
     */
    public FiringPeriod findFiringPeriod(Area area, Date firingPeriodFromDate, Date firingPeriodToDate) {
        return em.createNamedQuery("FiringPeriod.findByAreaAndInterval", FiringPeriod.class)
                .setParameter("area", area)
                .setParameter("fromDate", TimeUtils.resetSeconds(firingPeriodFromDate))
                .setParameter("toDate", TimeUtils.resetSeconds(firingPeriodToDate))
                .getResultList().stream()
                .findFirst()
                .orElse(null);
    }


    /**
     * Fetches all firing areas and their firing periods on the specified date
     * @param date the date
     * @param query a search query
     * @param areaIds area subtrees to search
     * @param inactive whether to include inactive areas or not
     * @param lang the language to filter areas by
     * @return all firing areas and their firing periods on the specified date
     */
    public List<SystemAreaVo> getFiringPeriodsForDate(Date date, String query, Set<Integer> areaIds, boolean inactive, String lang) {

        Set<Boolean> activeSet = inactive
            ? new HashSet<>(Arrays.asList(TRUE, FALSE))
            : Collections.singleton(TRUE);

        // Find the firing periods
        List<FiringPeriod> fps = em.createNamedQuery("FiringPeriod.findByDateInterval", FiringPeriod.class)
                .setParameter("active", activeSet)
                .setParameter("fromDate", TimeUtils.resetTime(date))
                .setParameter("toDate", TimeUtils.endOfDay(date))
                .getResultList();


        // Look up all firing areas
        DataFilter filter = DataFilter.get()
                .fields(DataFilter.PARENT, DataFilter.DETAILS)
                .lang(lang);


        AreaSearchParams param = new AreaSearchParams()
                .name(query)
                .areaIds(areaIds)
                .inactive(inactive)
                .type(AreaType.FIRING_AREA);
        param.sortBy("TREE_ORDER").sortOrder(ASC);

        List<SystemAreaVo> result = areaService.searchAreas(param).stream()
                .map(a -> a.toVo(SystemAreaVo.class, filter))
                .collect(Collectors.toList());

        Map<Integer, SystemAreaVo> areaLookup = result.stream()
                .collect(Collectors.toMap(SystemAreaVo::getId, Function.identity()));

        // Group the firing periods by area
        for (FiringPeriod fp : fps) {
            SystemAreaVo area = areaLookup.get(fp.getArea().getId());
            if (area != null) {
                area.checkCreateFiringPeriods().add(fp.toVo());
            }
        }

        return result;
    }


    /**
     * Fetches the firing area and its firing periods on the specified date, and null if not found
     * @param areaId the id of the area
     * @param date the date
     * @param lang the language to filter areas by
     * @return the firing area and its firing periods on the specified date date, and null if not found
     */
    public SystemAreaVo getFiringAreaPeriodsForDate(Integer areaId, Date date, String lang) {

        return getFiringPeriodsForDate(date, null, null, true, lang).stream()
                .filter(a -> Objects.equals(a.getId(), areaId))
                .findFirst()
                .orElse(null);
    }


    /**
     * Creates a new firing period based on the given template
     * @param templatePeriod the template firing period
     * @return the persisted firing period
     */
    public FiringPeriod addFiringPeriod(FiringPeriod templatePeriod) {
        // Validation
        if (templatePeriod.isPersisted()) {
            throw new IllegalArgumentException("Cannot create existing firing period");
        }
        if (!templatePeriod.firingPeriodDefined()) {
            throw new IllegalArgumentException("Firing period must defined area, from- and to-dates");
        }

        return saveEntity(templatePeriod);
    }


    /**
     * Updates firing period based on the given template
     * @param templatePeriod the template firing period
     * @return the persisted firing period
     */
    public FiringPeriod updateFiringPeriod(FiringPeriod templatePeriod) {
        // Validation
        if (templatePeriod.isNew()) {
            throw new IllegalArgumentException("Cannot update non-existing firing period");
        }
        if (!templatePeriod.firingPeriodDefined()) {
            throw new IllegalArgumentException("Firing period must defined area, from- and to-dates");
        }

        FiringPeriod originalFp = getByPrimaryKey(FiringPeriod.class, templatePeriod.getId());
        originalFp.updateFiringPeriod(templatePeriod);

        return saveEntity(originalFp);
    }


    /**
     * Deletes the firing period with the given ID
     * @param id the firing period ID
     */
    public boolean deleteFiringPeriod(Integer id) {

        FiringPeriod firingPeriod = getByPrimaryKey(FiringPeriod.class, id);
        if (firingPeriod != null) {
            remove(firingPeriod);
            return true;
        }
        return false;
    }


    /**
     * Updates the list of firing periods for the area at the given date
     * @param firingArea the area to update
     * @param date the date to update the schedule for
     * @param lang the language to filter areas by
     * @return the updated area
     */
    public SystemAreaVo updateFiringPeriodsForArea(SystemAreaVo firingArea, Date date, String lang) {

        // Fetch the current schedule for the area
        SystemAreaVo currentArea = getFiringAreaPeriodsForDate(firingArea.getId(), date, lang);
        if (currentArea == null) {
            throw new WebApplicationException("No firing area " + firingArea.getId(), 400);
        }

        Area area = areaService.getAreaDetails(firingArea.getId());
        List<FiringPeriod> updatedFps = firingArea.checkCreateFiringPeriods().stream()
                .map(fp -> new FiringPeriod(area, fp))
                .collect(Collectors.toList());

        // Handle new firing periods
        updatedFps.stream()
                .filter(BaseEntity::isNew)
                .forEach(this::addFiringPeriod);

        // Handle updated firing periods
        updatedFps.stream()
                .filter(BaseEntity::isPersisted)
                .forEach(this::updateFiringPeriod);

        // Create a look-up of all updated (i.e. existing firing periods)
        Set<Integer> updatedFpIds = updatedFps.stream()
                .filter(BaseEntity::isPersisted)
                .map(BaseEntity::getId)
                .collect(Collectors.toSet());

        // Handle deleted firing periods
        currentArea.checkCreateFiringPeriods().stream()
                .filter(fp -> !updatedFpIds.contains(fp.getId()))
                .forEach(fp -> deleteFiringPeriod(fp.getId()));

        // Return the updated firing area
        return getFiringAreaPeriodsForDate(firingArea.getId(), date, lang);
    }
}
