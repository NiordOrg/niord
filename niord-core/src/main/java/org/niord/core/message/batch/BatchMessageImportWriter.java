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
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

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
            messageService.createMessage(message);

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

        Properties props = job.readProperties();
        String tagId = props.getProperty("tagName");
        if (StringUtils.isBlank(tagId)) {
            return null;
        }

        tag = messageTagService.findByUserAndTagId(job.getUser(), tagId);
        return tag;
    }
}
