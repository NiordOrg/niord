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
import org.niord.model.message.MessageVo;
import org.niord.model.message.Status;

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
                area = areaService.findOrCreateArea(area, true);
                if (area != null) {
                    areas.add(area);
                }
            }
            message.setAreas(areas);

            // Make sure categories are created
            List<Category> categories = new ArrayList<>();
            for (Category category : message.getCategories()) {
                category = categoryService.findOrCreateCategory(category, true);
                if (category != null) {
                    categories.add(category);
                }
            }
            message.setCategories(categories);

            // Make sure charts are created
            List<Chart> charts = new ArrayList<>();
            for (Chart chart : message.getCharts()) {
                chart = chartService.findOrCreateChart(chart, true);
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
