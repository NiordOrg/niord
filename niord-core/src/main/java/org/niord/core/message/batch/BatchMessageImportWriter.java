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

import org.niord.core.message.Message;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the NWs to the database
 */
@Named
public class BatchMessageImportWriter extends AbstractMessageImportWriter {

    @Inject
    MessageService messageService;

    @Inject
    MessageSeriesService messageSeriesService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();

        MessageTag tag = getMessageTag();

        for (Object i : items) {
            Message message = (Message)i;

            if (message.isNew()) {
                messageService.createMessage(message);
            } else {
                messageService.saveMessage(message);
            }


            // Add the message to any tags specified by the message series
            messageSeriesService.updateMessageTagsFromMessageSeries(message);

            // Add the message to the tag
            tag = checkAddMessageToTag(message, tag);
        }

        // Update and save the message tag
        saveMessageTag(tag);

        getLog().info(String.format("Persisted %d messages in %d ms", items.size(), System.currentTimeMillis() - t0));
    }

}
