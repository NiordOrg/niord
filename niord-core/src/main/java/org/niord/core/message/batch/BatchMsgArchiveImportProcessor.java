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

import org.niord.core.area.AreaService;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.ChartService;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.MessageService;
import org.niord.core.message.batch.BatchMsgArchiveImportReader.ExtractedArchiveMessageVo;
import org.niord.core.settings.SettingsService;

import javax.inject.Inject;
import javax.inject.Named;

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


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        ExtractedArchiveMessageVo messageVo = (ExtractedArchiveMessageVo) item;
        Message message = new Message(messageVo.getMessage());

        // Process related message base data
        processMessage(message);

        return null; // new ExtractedArchiveMessage(message, messageVo.getEditRepoPath());
    }


    /** Processes the message to ensure that related base data is created and valid */
    protected Message processMessage(Message message) throws Exception {
        try {
            System.out.println("XXXXX " + message);
            return null;
        } catch (Exception e) {
            getLog().severe("Failed processing batch import message: " + e);
            throw e;
        }
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
