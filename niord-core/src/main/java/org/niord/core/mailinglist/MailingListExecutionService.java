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
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.niord.core.mail.IMailable;
import org.niord.core.mail.ScheduledMail;
import org.niord.core.mail.ScheduledMailRecipient;
import org.niord.core.mailinglist.vo.MailingListReportVo;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTokenExpander;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.model.DescEntity;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.JsResourceService;
import org.niord.core.script.ScriptResource;
import org.niord.core.service.BaseService;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.niord.core.mail.ScheduledMailRecipient.RecipientType.TO;
import static org.niord.core.script.ScriptResource.Type.FM;

/**
 * Handles execution of mailing list triggers
 */
@RequestScoped
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
    DictionaryService dictionaryService;


    /***************************************/
    /** Status change triggers            **/
    /***************************************/


    /**
     * Executes the status change mailing list trigger for the given message
     *
     * @param triggerId the ID of the status change mailing list trigger to execute
     * @param messageUid the message UID to execute the trigger for
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void executeStatusChangeTrigger(Integer triggerId, String messageUid) throws Exception {

        // Look up the trigger
        MailingListTrigger trigger = getByPrimaryKey(MailingListTrigger.class, triggerId);
        if (trigger == null || trigger.getType() != TriggerType.STATUS_CHANGE) {
            throw new IllegalArgumentException("Invalid trigger " + triggerId);
        }

        // Look up the message
        Message message = messageService.findByUid(messageUid);
        if (message == null) {
            throw new IllegalArgumentException("Invalid message " + messageUid);
        }


        executeStatusChangeTrigger(trigger, message, true);
    }


    /**
     * Executes the status change mailing list trigger for the given message
     *
     * The trigger may be an actual persisted entity or a template trigger user for testing.
     * In the latter case, set "persist" to false to prevent the mails being persisted.
     *
     * @param trigger the status change mailing list trigger to execute
     * @param message the message to execute the trigger for
     * @param persist whether to persist the mails or not
     */
    public List<ScheduledMail> executeStatusChangeTrigger(MailingListTrigger trigger, Message message, boolean persist) throws Exception {

        if (trigger.getType() != TriggerType.STATUS_CHANGE) {
            throw new IllegalAccessException("Must be a status-change trigger");
        }

        List<ScheduledMail> mails = new ArrayList<>();

        // Check that one or more language variants have been defined
        List<String> languages = trigger.getDescs().stream()
                .map(DescEntity::getLang)
                .collect(Collectors.toList());
        if (languages.isEmpty()) {
            return mails;
        }

        // In no Freemarker script resources have been defined, bail
        if (trigger.getScriptResourcePaths().isEmpty() ||
                trigger.getScriptResourcePaths().stream().noneMatch(p -> ScriptResource.path2type(p) == FM)) {
            log.debug("No Freemarker template defined for trigger " + trigger.getId());
            return mails;
        }

        // If a message filter is defined for the trigger, see if the message qualifies
        if (StringUtils.isNotBlank(trigger.getMessageFilter())) {
            MessageFilter filter = MessageFilter.getInstance(trigger.getMessageFilter());
            if (!filter.matches(message)) {
                log.debug(String.format("Message %s not matching trigger message filter: %s",
                        message.getUid(),
                        trigger.getMessageFilter()));
                return mails;
            }
        }


        // Create mails language by language
        for (String language : languages) {
            ScheduledMail mail = createMailTemplate(trigger, language, languages, message);

            // First check if there are any recipients for the language
            if (mail.getRecipients().isEmpty()) {
                continue;
            }

            String html = executeScriptResources(trigger, Collections.singletonList(message), language);
            mail.setHtmlContents(html);

            // All a copy of the mail for each recipient
            mails.addAll(mail.splitByRecipient());
        }

        // Persist the mails
        if (persist) {
            persistMails(mails);
        }
        return mails;
    }


    /***************************************/
    /** Scheduled triggers                **/
    /***************************************/


    /**
     * Executes the scheduled mailing list trigger for the given message
     * @param triggerId the ID of the scheduled mailing list trigger to execute
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void executeScheduledTrigger(Integer triggerId) throws Exception {

        MailingListTrigger trigger = getByPrimaryKey(MailingListTrigger.class, triggerId);
        if (trigger == null || trigger.getType() != TriggerType.SCHEDULED) {
            throw new IllegalArgumentException("Invalid trigger " + triggerId);
        }

        executeScheduledTrigger(trigger, true);
    }


    /**
     * Computes the next scheduled execution of the given trigger
     * @param triggerId the ID of the scheduled mailing list trigger to execute
     */
    @Transactional
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void computeNextScheduledExecution(Integer triggerId) {

        MailingListTrigger trigger = getByPrimaryKey(MailingListTrigger.class, triggerId);
        if (trigger != null && trigger.getType() == TriggerType.SCHEDULED) {
            try {
                trigger.checkComputeNextScheduledExecution();
                saveEntity(trigger);
            } catch (Exception e) {
                log.error("Failed computing next scheduled execution for trigger " + triggerId);
            }
        }
    }


    /**
     * Executes the scheduled mailing list trigger for the given message.
     *
     * The trigger may be an actual persisted entity or a template trigger user for testing.
     * In the latter case, set "persist" to false to prevent the mails being persisted.
     *
     * @param trigger the scheduled mailing list trigger to execute
     * @param persist whether to persist the mails or not
     */
    @Transactional
    public List<ScheduledMail> executeScheduledTrigger(MailingListTrigger trigger, boolean persist) throws Exception {

        if (trigger.getType() != TriggerType.SCHEDULED) {
            throw new IllegalAccessException("Must be a scheduled trigger");
        }

        List<ScheduledMail> mails = new ArrayList<>();

        // Check that one or more language variants have been defined
        List<String> languages = trigger.getDescs().stream()
                .map(DescEntity::getLang)
                .collect(Collectors.toList());
        if (languages.isEmpty()) {
            return mails;
        }

        // In no Freemarker script resources have been defined, bail
        if (trigger.getScriptResourcePaths().isEmpty() ||
                trigger.getScriptResourcePaths().stream().noneMatch(p -> ScriptResource.path2type(p) == FM)) {
            log.debug("No Freemarker template defined for trigger " + trigger.getId());
            return mails;
        }

        // If no message query is defined for the trigger
        if (StringUtils.isBlank(trigger.getMessageQuery())) {
            log.debug("No mail query defined for trigger " + trigger.getId());
            return mails;
        }

        // Perform the message search
        MessageSearchParams params = MessageSearchParams.instantiate(null, trigger.getMessageQuery());
        params.maxSize(Integer.MAX_VALUE);
        PagedSearchResultVo<Message> messageResult = messageService.search(params);

        // Create mails language by language
        for (String language : languages) {
            ScheduledMail mail = createMailTemplate(trigger, language, languages, null);

            // First check if there are any recipients for the language
            if (mail.getRecipients().isEmpty()) {
                continue;
            }

            String html = executeScriptResources(trigger, messageResult.getData(), language);
            mail.setHtmlContents(html);

            // All a copy of the mail for each recipient
            mails.addAll(mail.splitByRecipient());
        }

        // Persist the mails
        if (persist) {
            persistMails(mails);
        }
        return mails;
    }


    /***************************************/
    /** Mailing List Reports              **/
    /***************************************/

    /**
     * Returns the list of mailing list reports, i.e. the scheduled triggers that can
     * be executed by end-users as reports.
     *
     * @param lang the language
     * @return the list of mailing list reports
     */
    public List<MailingListReportVo> getMailingListReports(String lang) {

        return em.createNamedQuery("MailingListTrigger.findMailingListReports", MailingListTrigger.class)
                .getResultList()
                .stream()
                .map(t -> new MailingListReportVo(t, app.getLanguage(lang)))
                .collect(Collectors.toList());
    }


    /**
     * Executes the given mailing list report, i.e. the scheduled triggers that can
     * be executed by end-users as reports.
     *
     * @param triggerId the ID of the trigger (mailing list report) to execute
     * @param lang the language
     * @return the resulting HTML
     */
    public String executeMailingListReport(Integer triggerId, String lang) throws Exception {

        MailingListTrigger trigger = getByPrimaryKey(MailingListTrigger.class, triggerId);
        if (trigger == null || trigger.getType() != TriggerType.SCHEDULED || trigger.getPublicReport() != Boolean.TRUE) {
            throw new IllegalArgumentException("Trigger " + triggerId + " cannot be used as a public report");
        }

        // Perform the message search
        MessageSearchParams params = MessageSearchParams.instantiate(null, trigger.getMessageQuery());
        params.maxSize(Integer.MAX_VALUE);
        PagedSearchResultVo<Message> messageResult = messageService.search(params);


        // Make sure that the language is defined for the trigger
        lang = app.getLanguage(lang);
        Set<String> languages = trigger.getDescs().stream()
                .map(DescEntity::getLang)
                .collect(Collectors.toSet());
        if (!languages.contains(lang) && !languages.isEmpty()) {
            lang = languages.iterator().next();
        }

        // Execute the script resources associated with the trigger
        return executeScriptResources(
                trigger,
                messageResult.getData(),
                lang);
    }


    /***************************************/
    /** Common for all trigger types      **/
    /***************************************/


    /**
     * Creates a template mail for the mailing list trigger, with instantiated recipients and subject
     *
     * @param trigger the mailing list trigger to create a mail template for
     * @param language the current language
     * @param languages all language versions
     * @param message optionally, a message when the mails is created for a single message
     * @return the mail template
     **/
    private ScheduledMail createMailTemplate(MailingListTrigger trigger, String language, List<String> languages, Message message) {

        // Compute the default language
        String defaultLanguage = app.getLanguage("en");
        if (languages.size() == 1 || (languages.size() > 1 && !languages.contains(defaultLanguage))) {
            defaultLanguage = app.getLanguage(languages.get(0));
        }
        boolean isDefaultLanguage = defaultLanguage.equals(language);

        ScheduledMail mail = new ScheduledMail();

        // Create the mail subject
        MailingListTriggerDesc desc = trigger.getDesc(language);
        String subject = StringUtils.defaultString(desc.getSubject());

        // Check if we need to replace any tokens in the subject, like "${short-id}", "${title}", etc.
        subject = expandSubject(message, language, languages, subject);
        mail.setSubject(subject);

        // Get all recipients matching the given language
        List<IMailable> recipients = new ArrayList<>();
        recipients.addAll(trigger.getMailingList().getUsers().stream()
                .filter(r -> (r.getLanguage() == null && isDefaultLanguage) ||
                            languages.size() == 1 ||
                            language.equalsIgnoreCase(r.getLanguage()))
                .collect(Collectors.toList()));
        recipients.addAll(trigger.getMailingList().getContacts().stream()
                .filter(r -> (r.getLanguage() == null && isDefaultLanguage) ||
                        languages.size() == 1 ||
                        language.equalsIgnoreCase(r.getLanguage()))
                .collect(Collectors.toList()));

        recipients.forEach(r -> mail.addRecipient(new ScheduledMailRecipient(TO, r.computeInternetAddress())));

        return mail;
    }


    /** Check if we need to replace any tokens in the subject, like "${short-id}", "${title}", etc. **/
    private String expandSubject(Message message, String language, List<String> languages, String subject) {

        DictionaryVo mailDict = dictionaryService.getCachedDictionary("mail");
        String shortFormat = mailDict.value(language, "mail.date_format.short");
        String longFormat = mailDict.value(language, "mail.date_format.long");
        Date today = new Date();

        return MessageTokenExpander.getInstance(message, languages, language)
                .token("${date-short}", new SimpleDateFormat(shortFormat).format(today))
                .token("${date-long}", new SimpleDateFormat(longFormat).format(today))
                .expandTokens(subject);
    }


    /** Saves the list of scheduled mail templates **/
    private void persistMails(List<ScheduledMail> mails) {
        for (int x = 0; x < mails.size(); x++) {
            saveEntity(mails.get(x));
            if ((x + 1) % 20 == 0) {
                em.flush();
            }
        }
    }


    /***************************************/
    /** Template Execution                **/
    /***************************************/


    /**
     * Executes the script resources associated with a mailing list trigger and return the resulting HTML.
     * <p>
     * The list of script resources should be defined by an optional list of JavaScript resources
     * and a single freemarker template that actually generates the HTML for the mail.
     *
     * @param trigger the mailing list trigger to execute
     * @param messages  the messages to apply the template to
     * @param language the language of the template execution
     * @return the resulting html generated in the templates
     */
    public String executeScriptResources(MailingListTrigger trigger, List<Message> messages, String language) throws Exception {

        // Sanity check
        if (trigger.getScriptResourcePaths().isEmpty()) {
            return null;
        }

        // Adjust the message prior to executing the template
        List<SystemMessageVo> msgs = messages.stream()
                .map(m -> m.toVo(SystemMessageVo.class, Message.MESSAGE_DETAILS_AND_PROMULGATIONS_FILTER))
                .collect(Collectors.toList());
        msgs.forEach(m -> m.sort(language));

        // Create context data to use with Freemarker templates and JavaScript updates
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("languages", app.getLanguages());
        contextData.put("language", language);
        contextData.put("messages", msgs);
        if (msgs.size() == 1) {
            contextData.put("message", msgs.get(0));
        }
        contextData.put("params", new HashMap<>());

        StringBuilder result = new StringBuilder();

        // Execute the script resources one by one
        for (String scriptResourcePath : trigger.getScriptResourcePaths()) {

            ScriptResource.Type type = ScriptResource.path2type(scriptResourcePath);
            if (type == ScriptResource.Type.JS) {
                // JavaScript update
                evalJavaScriptResource(contextData, scriptResourcePath);
            } else if (type == FM) {
                // Freemarker Template update
                String html = applyFreemarkerTemplate(contextData, scriptResourcePath, language);
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
     * @param language the language of the template execution
     */
    private String applyFreemarkerTemplate(
            Map<String, Object> contextData,
            String scriptResourcePath,
            String language) throws IOException, TemplateException {

        // Run the associated Freemarker template to get a result in the "FieldTemplates" format
        return templateService.newFmTemplateBuilder()
                .templatePath(scriptResourcePath)
                .data(contextData)
                .language(language)
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
