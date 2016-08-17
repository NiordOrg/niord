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
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;

/**
 * Persists the NWs to the database
 */
@Named
public class BatchMessageImportWriter extends AbstractItemHandler {

    @Inject
    MessageService messageService;

    @Inject
    MessageTagService messageTagService;

    MessageTag tag = null;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();

        MessageTag tag = checkCreateMessageTag();

        for (Object i : items) {
            Message message = (Message)i;

            if (message.isNew()) {
                messageService.createMessage(message);
            } else {
                messageService.saveMessage(message);
            }

            // Add the message to the tag
            if (tag != null && !tag.getMessages().stream().anyMatch(m -> m.getId().equals(message.getId()))) {
                tag.getMessages().add(message);
                message.getTags().add(tag);
            }
        }

        if (tag != null) {
            tag.updateMessageCount();
            messageService.saveEntity(tag);
            getLog().info(String.format("Updated tag '%s' with messages", tag.getTagId()));
        }

        getLog().info(String.format("Persisted %d messages in %d ms", items.size(), System.currentTimeMillis() - t0));
    }


    /** Looks up or creates the message tag associated with the batch job */
    protected MessageTag checkCreateMessageTag() throws IOException {
        // Has it been resolved already
        if (tag != null) {
            return tag;
        }

        String tagId = (String)job.getProperties().get("tagName");
        if (StringUtils.isBlank(tagId)) {
            return null;
        }

        tag = messageTagService.findTag(tagId);
        return tag;
    }
}
