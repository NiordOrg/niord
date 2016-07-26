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
import org.niord.core.geojson.FeatureService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.MessageService;
import org.niord.core.message.batch.BatchMsgArchiveImportReader.ExtractedArchiveMessageVo;
import org.niord.core.settings.SettingsService;
import org.niord.model.vo.Status;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Processes imported messages.
 * <p>
 * Messages are always imported with the status "IMPORTED" and may never be used to update existing messages.
 */
@Named
public class BatchMsgArchiveImportProcessor extends AbstractItemHandler {

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

    @Inject
    FeatureService featureService;

    MessageSeries defaultMessageSeries;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        ExtractedArchiveMessageVo messageVo = (ExtractedArchiveMessageVo) item;
        Message message = new Message(messageVo.getMessage());

        // Process related message base data
        message = processMessage(message);
        if (message == null) {
           getLog().log(Level.WARNING, "Skipping existing message " + messageVo.getMessage().getId());
            return null;
        }

        return new ExtractedArchiveMessage(message, messageVo.getEditRepoPath());
    }


    /** Processes the message to ensure that related base data is created and valid */
    protected Message processMessage(Message message) throws Exception {
        String origUid = message.getUid();
        try {

            message.setId(null);
            message.setVersion(0);
            message.setCreated(null);
            message.setUpdated(null);

            // Check if we should assign a new UID
            Boolean assignNewUids = (Boolean)job.getProperties().get("assignNewUids");
            if (assignNewUids) {
                message.setLegacyId(message.getUid());
                message.assignNewUid();
            }

            // Check that an existing message with the message UID does not exist
            Message original = messageService.findByUid(message.getUid());
            if (original != null) {
                return null;
            }

            // Update the message series according to the properties
            MessageSeries messageSeries = null;
            if (message.getMessageSeries() != null) {
                messageSeries = resolveValidMessageSeries(message.getMessageSeries().getSeriesId());
            }

            // Check if we need to assign the default message series
            Boolean assignDefaultSeries = (Boolean)job.getProperties().get("assignDefaultSeries");
            if (messageSeries == null || assignDefaultSeries) {
                messageSeries = resolveDefaultMessageSeries();
            }

            // If no message series has been resolve, bail out
            if (messageSeries != null) {
                message.setMessageSeries(messageSeries);
                // Ensure that the main type and type adheres to the message series
                message.setMainType(messageSeries.getMainType());
                if (message.getType() != null && message.getType().getMainType() != message.getMainType()) {
                    message.setType(message.getMainType().anyType());
                }
            } else {
                throw new Exception("No valid message series resolved for message " + message.getUid());
            }


            // Status handling
            Boolean preserveStatus = (Boolean)job.getProperties().get("preserveStatus");
            if (!preserveStatus || message.getStatus() == Status.PUBLISHED) {
                // Force "IMPORTED" status
                message.setStatus(Status.IMPORTED);

                // Reset various fields and flags
                message.setMrn(null);
                message.setShortId(null);
                message.setPublishDate(null);
                message.setUnpublishDate(null);
            }

            // Check if we should create base data such as areas, categories and charts
            Boolean createBaseData = (Boolean)job.getProperties().get("createBaseData");

            // Make sure areas are resolved and/or created
            List<Area> areas = message.getAreas().stream()
                    .map(a -> areaService.findOrCreateArea(a, createBaseData))
                    .filter(a -> a != null)
                    .collect(Collectors.toList());
            message.setAreas(areas);

            // Make sure categories are resolved and/or created
            List<Category> categories = message.getCategories().stream()
                    .map(c -> categoryService.findOrCreateCategory(c, createBaseData))
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
            message.setCategories(categories);

            // Make sure charts are resolved and/or created
            List<Chart> charts = message.getCharts().stream()
                    .map(c -> chartService.findOrCreateChart(c, createBaseData))
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
            message.setCharts(charts);

            // Reset all geometry IDs
            if (message.getGeometry() != null) {
                message.getGeometry().setUid(null);
                featureService.assignNewFeatureUids(message.getGeometry());
            }

            // Reset all attachment IDs
            message.getAttachments().forEach(att -> att.setId(null));

            getLog().info("Processed message " + origUid);
            return message;
        } catch (Exception e) {
            getLog().severe("Failed processing batch import message " + origUid + ": " + e);
            throw e;
        }
    }


    /**
     * Resolves and caches the default message series as defined by the "seriesId" batch property
     * @return the resolved default message series
     */
    private MessageSeries resolveDefaultMessageSeries() throws Exception {
        // Check if it has already been resolved
        if (defaultMessageSeries != null) {
            return defaultMessageSeries;
        }

        // Check if it is defined by batch job properties
        String seriesId = (String)job.getProperties().get("seriesId");
        defaultMessageSeries = resolveValidMessageSeries(seriesId);

        return defaultMessageSeries;
    }


    /**
     * Resolves the given message series, if it is valid. Otherwise, returns null
     *
     * @param seriesId the message series to resolve
     * @return the resolved valid message series
     */
    @SuppressWarnings("unchecked")
    private MessageSeries resolveValidMessageSeries(String seriesId) {

        if (StringUtils.isBlank(seriesId)) {
            return null;
        }

        // Get list of valid message series for the user executing the batch job
        // This list was resolved in the MessageRestService.checkBatchJob() method.
        Collection<String> validMessageSeries = (Collection<String>)job.getProperties().get("validMessageSeries");

        // Validate that the series id is indeed valid
        if (!validMessageSeries.contains(seriesId)) {
            return null;
        }

        return messageSeriesService.findBySeriesId(seriesId);
    }


    /** Encapsulates a Message and the path to the temp repo where the archive was extracted */
    public static class ExtractedArchiveMessage {
        Message message;
        String editRepoPath;

        public ExtractedArchiveMessage(Message message, String editRepoPath) {
            this.message = message;
            this.editRepoPath = editRepoPath;
        }

        public Message getMessage() {
            return message;
        }

        public String getEditRepoPath() {
            return editRepoPath;
        }
    }

}
