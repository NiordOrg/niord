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
package org.niord.s124.old;


import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.niord.core.NiordApp;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.model.message.MainType;
import org.niord.model.message.ReferenceVo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Service for converting Niord navigational warnings to S-124 GML
 * <p>
 * NB: Currently the service is at a proof-of-concept stage, what with S-124 still under development.
 * <p>
 * The S-124 XSD area based on the format used by the STM-project at
 * http://stmvalidation.eu/schemas/ ("Area Exchange Format")
 *
 * TODO: When a more mature version has been implemented, the Freemarker template execution should
 *       use the {@code FmTemplateService} for DB-backed template execution.
 */
@ApplicationScoped
public class S124Service {

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;

    /**
     * Generates S-124 compliant GML for the message
     * @param messageId the message
     * @param language the language
     * @return the generated GML
     */
    public String generateGML(String messageId, String language) throws Exception {

        Message message = messageService.resolveMessage(messageId);

        // Validate the message
        if (message == null) {
            throw new IllegalArgumentException("Message not found " + messageId);
        } else if (message.getMainType() == MainType.NM) {
            throw new IllegalArgumentException("Sadly, S-124 does not currently support Notices to Mariners T&P :-(");
        } else if (message.getNumber() == null) {
            throw new IllegalArgumentException("Sadly, S-124 does not currently support un-numbered navigational warnings :-(");
        }

        // Ensure we use a valid language
        language = app.getLanguage(language);

        SystemMessageVo msg = message.toVo(
                SystemMessageVo.class,
                Message.MESSAGE_DETAILS_FILTER);
        msg.sort(language);

        Map<String, Object> data = new HashMap<>();
        data.put("msg", msg);
        data.put("language", language);

        double[] bbox = GeoJsonUtils.computeBBox(message.toGeoJson());
        if (bbox != null) {
            data.put("bbox", bbox);
        }

        data.put("references", referencedMessages(msg, language));

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setTemplateLoader(new ClassTemplateLoader(getClass(), "/templates/gml"));

        StringWriter result = new StringWriter();
        Template fmTemplate = cfg.getTemplate("generate-s124.ftl");


        fmTemplate.process(data, result);
        return result.toString();
    }


    /**
     * Returns resolved message references
     * @param message the message to return resolved message references for
     * @return the resolved message references
     */
    private List<MessageReferenceVo> referencedMessages(SystemMessageVo message, String language) {
        List<MessageReferenceVo> result = new ArrayList<>();
        if (message.getReferences() != null) {
            for (ReferenceVo ref : message.getReferences()) {
                try {
                    Message refMsg = messageService.resolveMessage(ref.getMessageId());
                    if (refMsg != null && refMsg.getMainType() == MainType.NW && refMsg.getNumber() != null) {
                        SystemMessageVo msg = refMsg.toVo(
                                SystemMessageVo.class,
                                Message.MESSAGE_DETAILS_FILTER);
                        msg.sort(language);
                        result.add(new MessageReferenceVo(msg, ref));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }


    /**
     * Utility class used for message references, including the referenced message
     */
    @SuppressWarnings("unused")
    public static class MessageReferenceVo extends ReferenceVo {

        SystemMessageVo msg;

        public MessageReferenceVo() {
        }

        public MessageReferenceVo(SystemMessageVo msg, ReferenceVo reference) {
            this.msg = msg;
            setMessageId(reference.getMessageId());
            setType(reference.getType());
            setDescs(reference.getDescs());
        }

        public SystemMessageVo getMsg() {
            return msg;
        }

        public void setMsg(SystemMessageVo msg) {
            this.msg = msg;
        }
    }
}