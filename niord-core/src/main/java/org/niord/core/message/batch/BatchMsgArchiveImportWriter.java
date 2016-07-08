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
import org.niord.core.message.EditableMessageVo;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;
import org.niord.core.message.batch.BatchMsgArchiveImportProcessor.ExtractedArchiveMessage;
import org.niord.model.DataFilter;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;

/**
 * Persists the messages to the database and updates the attachments.
 * <p>
 * NB: Since copying attachments in the repository is not really transactional,
 * the batch job file, "msg-archive-import.xml" is only processing one message
 * at a time before committing. Not fast but more secure.
 */
@Named
public class BatchMsgArchiveImportWriter extends AbstractItemHandler {

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
            ExtractedArchiveMessage extractedMsg = (ExtractedArchiveMessage)i;
            Message message = extractedMsg.getMessage();

            if (message.isNew()) {
                messageService.createMessage(message);
            } else {
                messageService.updateMessage(message);
            }


            // Add the message to the tag
            if (tag != null && !tag.getMessages().stream().anyMatch(m -> m.getId().equals(message.getId()))) {
                tag.getMessages().add(message);
                message.getTags().add(tag);
            }


            // Since saving the message did not cause an error, copy attachments.
            // NB: There may still be an exception when the transaction is committed,
            // so, to minimize the risk of inconsistency, the batch job only processes
            // one message at a time before committing.
            EditableMessageVo editableMessageVo = message.toEditableVo(DataFilter.get());
            editableMessageVo.setEditRepoPath(extractedMsg.getEditRepoPath());
            messageService.updateMessageFromTempRepoFolder(editableMessageVo);
        }

        if (tag != null) {
            tag.updateMessageCount();
            messageService.saveEntity(tag);
            getLog().info(String.format("Updated tag '%s' with messages", tag.getTagId()));
        }

        getLog().info(String.format("Persisted %d messages in %d ms", items.size(), System.currentTimeMillis() - t0));
    }


    /** Looks up the message tag associated with the batch job */
    protected MessageTag checkCreateMessageTag() throws IOException {
        // Has it been resolved already
        if (tag != null) {
            return tag;
        }

        String tagId = (String)job.getProperties().get("tagId");
        if (StringUtils.isBlank(tagId)) {
            return null;
        }

        tag = messageTagService.findTag(tagId);
        return tag;
    }
}
