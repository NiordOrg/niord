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
package org.niord.core.message.batch;

import org.apache.commons.lang.StringUtils;
import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.MessageService;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

/**
 * Processes imported messages.
 * <p>
 * Messages are always imported with the status "IMPORTED" and may never be used to update existing messages.
 */
@Named
public class BatchMessageImportProcessor extends AbstractItemHandler {

    private static final Setting DEFAULT_SERIES_ID = new Setting("batchMessageSeriesId")
            .description("Default message series ID to use for imported messages")
            .cached(true)
            .editable(true)
            .web(false);


    @Inject
    protected MessageService messageService;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    SettingsService settingsService;

    @Inject
    AreaService areaService;

    @Inject
    CategoryService categoryService;

    @Inject
    ChartService chartService;

    MessageSeries defaultMessageSeries;


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        MessageVo messageVo = (MessageVo) item;
        Message message = new Message(messageVo);

        // Process related message base data
        processMessage(message);

        return message;
    }


    /** Processes the message to ensure that related base data is created and valid */
    protected Message processMessage(Message message) throws Exception {
        try {
            if (message.getStatus() == null) {
                message.setStatus(Status.IMPORTED);
            }

            // Check that a message series is defined
            if (message.getMessageSeries() == null) {
                message.setMessageSeries(resolveDefaultMessageSeries());
            }

            if (message.getNumber() != null && message.getMrn() == null) {
                messageSeriesService.updateMessageSeriesIdentifiers(message, false);
            }

            // Make sure areas are created
            List<Area> areas = new ArrayList<>();
            for (Area area : message.getAreas()) {
                area = areaService.findOrCreateArea(area);
                if (area != null) {
                    areas.add(area);
                }
            }
            message.setAreas(areas);

            // Make sure categories are created
            List<Category> categories = new ArrayList<>();
            for (Category category : message.getCategories()) {
                category = categoryService.findOrCreateCategory(category);
                if (category != null) {
                    categories.add(category);
                }
            }
            message.setCategories(categories);

            // Make sure charts are created
            List<Chart> charts = new ArrayList<>();
            for (Chart chart : message.getCharts()) {
                chart = chartService.findOrCreateChart(chart);
                if (chart != null) {
                    charts.add(chart);
                }
            }
            message.setCharts(charts);

            getLog().info("Processed message " + message);
            return message;

        } catch (Exception e) {
            getLog().severe("Failed processing batch import message: " + e);
            throw e;
        }
    }

    /**
     * If the message series is not defined for the message, it may either have been defined
     * by the "seriesId" batch property, or the "batchMessageSeriesId" system setting
     * @return the resolved message series
     */
    private MessageSeries resolveDefaultMessageSeries() throws Exception {
        // Check if it has already been resolved
        if (defaultMessageSeries != null) {
            return defaultMessageSeries;
        }

        // Check if it is defined by batch job properties
        String seriesId = (String)job.getProperties().get("seriesId");
        if (StringUtils.isNotBlank(seriesId)) {
            defaultMessageSeries = messageSeriesService.findBySeriesId(seriesId);
        }

        // If not resolved from batch job properties, check if defined by system setting
        if (defaultMessageSeries == null) {
            seriesId = settingsService.getString(DEFAULT_SERIES_ID);
            if (StringUtils.isNotBlank(seriesId)) {
                defaultMessageSeries = messageSeriesService.findBySeriesId(seriesId);
            }
        }

        // If no message series has been resolved, bail out
        if (defaultMessageSeries == null) {
            throw new Exception("No default message series resolved");
        }

        return defaultMessageSeries;
    }
}
