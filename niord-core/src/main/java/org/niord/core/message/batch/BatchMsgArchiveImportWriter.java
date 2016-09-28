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
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTag;
import org.niord.core.message.batch.BatchMsgArchiveImportProcessor.ExtractedArchiveMessage;
import org.niord.core.message.vo.EditableMessageVo;
import org.niord.model.DataFilter;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the messages to the database and updates the attachments.
 * <p>
 * NB: Since copying attachments in the repository is not really transactional,
 * the batch job file, "msg-archive-import.xml" is only processing one message
 * at a time before committing. Not fast but more secure.
 */
@Named
public class BatchMsgArchiveImportWriter extends AbstractMessageImportWriter {

    @Inject
    MessageService messageService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();

        MessageTag tag = getMessageTag();

        for (Object i : items) {
            ExtractedArchiveMessage extractedMsg = (ExtractedArchiveMessage)i;
            Message message = extractedMsg.getMessage();

            // Persist the message
            messageService.createMessage(message);

            // Add the message to the tag
            tag = checkAddMessageToTag(message, tag);


            // Since saving the message did not cause an error, copy attachments.
            // NB: There may still be an exception when the transaction is committed,
            // so, to minimize the risk of inconsistency, the batch job only processes
            // one message at a time before committing.
            EditableMessageVo editableMessageVo = message.toEditableVo(DataFilter.get().fields(DataFilter.DETAILS));
            editableMessageVo.setEditRepoPath(extractedMsg.getEditRepoPath());
            messageService.updateMessageFromTempRepoFolder(editableMessageVo);
        }

        // Update and save the message tag
        saveMessageTag(tag);

        getLog().info(String.format("Persisted %d messages in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
