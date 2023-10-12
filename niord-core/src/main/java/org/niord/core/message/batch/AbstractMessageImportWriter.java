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
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;

/**
 * Base class for message import writers. Adds support for adding messages to tags based on the
 * "tagId" batch property
 */
public abstract class AbstractMessageImportWriter extends AbstractItemHandler {

    @Inject
    MessageTagService messageTagService;

    /**
     * If the message is not already added to the tag, add it.
     * @param message the message to add
     * @param tag the tag, or null if undefined.
     */
    protected MessageTag checkAddMessageToTag(Message message, MessageTag tag) {
        if (tag != null && !tag.getMessages().stream().anyMatch(m -> m.getId().equals(message.getId()))) {
            tag.getMessages().add(message);
            message.getTags().add(tag);
        }
        return tag;
    }


    /**
     * Updates the message tag and persists it
     * @param tag the tag, or null if undefined.
     */
    @Transactional
    protected MessageTag saveMessageTag(MessageTag tag) {
        if (tag != null) {
            tag.updateMessageCount();
            tag = messageTagService.saveEntity(tag);
            getLog().info(String.format("Updated tag '%s' with messages", tag.getTagId()));
        }
        return tag;
    }


    /** Looks up and caches the message tag associated with the batch job */
    protected MessageTag getMessageTag() throws IOException {

        String tagId = (String)job.getProperties().get("tagId");
        if (StringUtils.isBlank(tagId)) {
            return null;
        }

        return messageTagService.findTag(job.getDomain(), tagId);
    }
}
