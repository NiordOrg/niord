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

package org.niord.core;

import org.junit.Test;
import org.niord.core.message.Message;
import org.niord.core.message.MessageScriptFilterEvaluator;
import org.niord.model.message.Status;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.niord.model.message.Type.TEMPORARY_NOTICE;

/**
 * Testing the MessageTagFilterEvaluator
 */
public class MessageScriptFilterEvaluatorTest {

    @Test
    public void testMessageTagFilter() throws Exception {

        Message msg = new Message();
        msg.setStatus(Status.PUBLISHED);
        msg.setType(TEMPORARY_NOTICE);

        String filter1 = "(msg.type == 'TEMPORARY_NOTICE' || msg.type == 'PRELIMINARY_NOTICE') && msg.status == 'PUBLISHED'";
        String filter2 = "msg.status == 'DRAFT'";

        assertTrue(new MessageScriptFilterEvaluator(filter1).includeMessage(msg));
        assertFalse(new MessageScriptFilterEvaluator(filter2).includeMessage(msg));

    }
}
