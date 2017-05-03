/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.mailinglist;

import freemarker.template.TemplateException;
import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.JsResourceService;
import org.niord.core.script.ScriptResource;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Asynchronous;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.niord.core.script.ScriptResource.Type.FM;

/**
 * Handles execution of mailing list triggers
 */
public class MailingListExecutionService extends BaseService {

    @Inject
    Logger log;

    @Inject
    NiordApp app;

    @Inject
    MessageService messageService;

    @Inject
    FmTemplateService templateService;

    @Inject
    JsResourceService javaScriptService;

    @Inject
    MailingListService mailingListService;


    /***************************************/
    /** Status change triggers            **/
    /***************************************/


    /**
     * Handle mailing list execution for the message. Called from the MailingListMessageListener MDB listener.
     * @param messageUid the UID of the message
     */
    @Asynchronous
    public void checkStatusChangeMailingListExecution(String messageUid) {

        long t0 = System.currentTimeMillis();

        Message message = messageService.findByUid(messageUid);

        List<MailingListTrigger> triggers = mailingListService.findStatusChangeTriggers(message.getStatus());
        log.info("Found %d status change listeners for %s in status %s",
                triggers.size(),
                messageUid,
                message.getStatus());

        for (MailingListTrigger trigger : triggers) {
            try {
                executeStatusChangeTrigger(trigger, message);
            } catch (Exception e) {
                log.error("Error executing status-change mailing-list trigger " + trigger.getId(), e);
            }
        }
        log.info("Executed %d status change listeners for %s in status %s in %d ms",
                triggers.size(),
                messageUid,
                message.getStatus(),
                System.currentTimeMillis() - t0);
    }


    /**
     * Executes the mailing list trigger for the given message
     * @param trigger the mailing list trigger to execute
     * @param message the message to execute the trigger for
     */
    public void executeStatusChangeTrigger(MailingListTrigger trigger, Message message) throws Exception {

        // In no Freemarker script resources have been defined, bail
        if (trigger.getScriptResourcePaths().isEmpty() ||
                trigger.getScriptResourcePaths().stream().noneMatch(p -> ScriptResource.path2type(p) == FM)) {
            log.debug("No Freemarker template defined for trigger " + trigger.getId());
            return;
        }

        // If a message filter is defined for the trigger, see if the message qualifies
        if (StringUtils.isNotBlank(trigger.getMessageFilter())) {
            MessageFilter filter = MessageFilter.getInstance(trigger.getMessageFilter());
            if (!filter.matches(message)) {
                log.debug("Message %s not matching trigger message filter: %s",
                        message.getUid(),
                        trigger.getMessageFilter());
                return;
            }
        }

        String html = executeScriptResources(trigger, message);


    }


    /***************************************/
    /** Scheduled triggers                **/
    /***************************************/



    /**
     * *******************************************
     * Template Execution
     * *******************************************
     */

    /**
     * Executes the script resources associated with a mailing list trigger and return the resulting HTML.
     * <p>
     * The list of script resources should be defined by an optional list of JavaScript resources
     * and a single freemarker template that actually generates the HTML for the mail.
     *
     * @param trigger the mailing list trigger to execute
     * @param message  the message to apply the template to
     * @return the resulting message
     */
    public String executeScriptResources(MailingListTrigger trigger, Message message) throws Exception {

        // Sanity check
        if (trigger.getScriptResourcePaths().isEmpty()) {
            return null;
        }

        // Adjust the message prior to executing the template
        SystemMessageVo msg = message.toVo(SystemMessageVo.class, Message.MESSAGE_DETAILS_AND_PROMULGATIONS_FILTER);

        // Create context data to use with Freemarker templates and JavaScript updates
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("languages", app.getLanguages());
        contextData.put("message", msg);

        StringBuilder result = new StringBuilder();

        // Execute the script resources one by one
        for (String scriptResourcePath : trigger.getScriptResourcePaths()) {

            ScriptResource.Type type = ScriptResource.path2type(scriptResourcePath);
            if (type == ScriptResource.Type.JS) {
                // JavaScript update
                evalJavaScriptResource(contextData, scriptResourcePath);
            } else if (type == FM) {
                // Freemarker Template update
                String html = applyFreemarkerTemplate(contextData, scriptResourcePath);
                result.append(html);
            }
        }

        return result.toString();
    }


    /**
     * Executes the Freemarker template at the given path and updates the message accordingly
     *
     * @param contextData        the context data to use in the Freemarker template
     * @param scriptResourcePath the path to the Freemarker template
     */
    private String applyFreemarkerTemplate(
            Map<String, Object> contextData,
            String scriptResourcePath) throws IOException, TemplateException {

        // Run the associated Freemarker template to get a result in the "FieldTemplates" format
        return templateService.newFmTemplateBuilder()
                .templatePath(scriptResourcePath)
                .data(contextData)
                .dictionaryNames("message", "mail")
                .process();
    }


    /**
     * Evaluates the JavaScript resource at the given path
     *
     * @param contextData        the context data to use in the Freemarker template
     * @param scriptResourcePath the path to the Freemarker template
     */
    private void evalJavaScriptResource(Map<String, Object> contextData, String scriptResourcePath) throws Exception {

        javaScriptService.newJsResourceBuilder()
                .resourcePath(scriptResourcePath)
                .data(contextData)
                .evaluate();
    }
}
