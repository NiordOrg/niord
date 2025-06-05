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
package org.niord.s100.s124;

import java.util.List;

import org.niord.core.NiordApp;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.model.message.MainType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.Dataset;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service for converting Niord navigational warnings to S-124 GML
 */
@ApplicationScoped
public class S124Service {

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;

    /**
     * Generates S-124 compliant GML for the message
     *
     * @param message
     *            the message
     * @param language
     *            the language
     * @return the generated GML
     */
    public String generateGML(Message message, String language) throws Exception {
        // Validate the message
        if (message.getMainType() == MainType.NM) {
            throw new IllegalArgumentException("Sadly, S-124 does not currently support Notices to Mariners T&P :-(");
        } else if (message.getNumber() == null) {
            throw new IllegalArgumentException("Sadly, S-124 does not currently support un-numbered navigational warnings :-(");
        }

        // Ensure we use a valid language
        language = app.getLanguage(language);

        S124DatasetInfo di = new S124DatasetInfo("D", app.getOrganisation(), List.of(message));

        Dataset dataset = S124Mapper.map(di, message);

        // Convert to XML
        String result = S124Utils.marshalS124(dataset);

        // Pretty print is not needed since JAXB already formats the output
        // result = S124Utils.prettyPrint(result);

        return result;
    }


    /**
     * Generates S-124 compliant GML for the message
     *
     * @param messageId
     *            the message
     * @param language
     *            the language
     * @return the generated GML
     */
    public String generateGML(String messageId, String language) throws Exception {
        Message message = messageService.resolveMessage(messageId);
        if (message == null) {
            throw new IllegalArgumentException("Message not found " + messageId);
        }
        return generateGML(message, language);
    }

//    /**
//     * Returns resolved message references
//     *
//     * @param message
//     *            the message to return resolved message references for
//     * @return the resolved message references
//     */
//    List<MessageReferenceVo> referencedMessages(SystemMessageVo message, String language) {
//        List<MessageReferenceVo> result = new ArrayList<>();
//        if (message.getReferences() != null) {
//            for (ReferenceVo ref : message.getReferences()) {
//                try {
//                    Message refMsg = messageService.resolveMessage(ref.getMessageId());
//                    if (refMsg != null && refMsg.getMainType() == MainType.NW && refMsg.getNumber() != null) {
//                        SystemMessageVo msg = refMsg.toVo(SystemMessageVo.class, Message.MESSAGE_DETAILS_FILTER);
//                        msg.sort(language);
//                        result.add(new MessageReferenceVo(msg, ref));
//                    }
//                } catch (Exception ignored) {}
//            }
//        }
//        return result;
//    }
//
//    /**
//     * Utility class used for message references, including the referenced message
//     */
//    public static class MessageReferenceVo extends ReferenceVo {
//
//        SystemMessageVo msg;
//
//        public MessageReferenceVo() {}
//
//        public MessageReferenceVo(SystemMessageVo msg, ReferenceVo reference) {
//            this.msg = msg;
//            setMessageId(reference.getMessageId());
//            setType(reference.getType());
//            setDescs(reference.getDescs());
//        }
//
//        public SystemMessageVo getMsg() {
//            return msg;
//        }
//
//        public void setMsg(SystemMessageVo msg) {
//            this.msg = msg;
//        }
//    }
}