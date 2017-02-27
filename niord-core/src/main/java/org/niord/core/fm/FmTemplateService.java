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

package org.niord.core.fm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import freemarker.ext.beans.ResourceBundleModel;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.fm.pdf.HtmlToPdfRenderer;
import org.niord.core.fm.vo.FmTemplateVo;
import org.niord.core.service.BaseService;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.niord.core.settings.Setting.Type.Boolean;
import static org.niord.core.settings.Setting.Type.Password;


/**
 * Main interface for accessing and processing Freemarker report templates
 */
@SuppressWarnings("unused")
@Stateless
public class FmTemplateService extends BaseService {

    public static final String LANGUAGE_PROPERTY    = "language";
    public static final String LANGUAGES_PROPERTY   = "languages";
    public static final String BUNDLE_PROPERTY      = "text";
    public static final String TIME_ZONE_PROPERTY   = "timeZone";

    public enum ProcessFormat { TEXT, PDF }

    @Inject
    @Setting(value = "pdfEncryptionEnabled", description = "Whether PDF reports should be encrypted or not",
            defaultValue = "false", type = Boolean)
    Boolean pdfEncryptionEnabled;

    @Inject
    @Setting(value = "pdfEncryptionPassword", description = "The PDF reports encryption password",
            defaultValue = "Samuel Pepys started the 1666 fire", type = Password)
    String pdfEncryptionPassword;

    @Inject
    DictionaryService dictionaryService;

    @Inject
    DomainService domainService;

    @Inject
    UserService userService;

    @Inject
    NiordApp app;

    @Inject
    Logger log;


    /**
     * Saves the Freemarker template
     *
     * @param template the Freemarker template to save
     * @return the saved template
     */
    public FmTemplate saveFmTemplate(FmTemplate template) {
        boolean wasPersisted = template.isPersisted();

        // Save the template
        template = saveEntity(template);

        // Save a FmTemplateHistory entity for the template
        saveFmTemplateHistory(template);

        return template;
    }


    /**
     * Returns the Freemarker template with the given ID, or null if not found
     * @param id the template id
     * @return the Freemarker template with the given ID, or null if not found
     */
    public FmTemplate findById(Integer id) {
        return getByPrimaryKey(FmTemplate.class, id);
    }


    /**
     * Returns the Freemarker template with the given path, or null if not found
     * @param path the Freemarker template path
     * @return the Freemarker template with the given path, or null if not found
     */
    public FmTemplate findByPath(String path) {
        try {
            return em.createNamedQuery("FmTemplate.findByPath", FmTemplate.class)
                    .setParameter("path", path)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all Freemarker templates
     * @return all Freemarker templates
     */
    public List<FmTemplate> findAll() {
        return em.createNamedQuery("FmTemplate.findAll", FmTemplate.class)
                .getResultList();
    }


    /**
     * Returns all Freemarker template paths
     * @return all Freemarker template paths
     */
    public Set<String> findAllTemplatePaths() {
        return em.createNamedQuery("FmTemplate.findAllPaths", String.class)
                .getResultList().stream()
                .collect(Collectors.toSet());
    }


    /**
     * Creates a new Freemarker template based on the template parameter
     * @param template the template to create
     * @return the created Freemarker template
     */
    public FmTemplate createFmTemplate(FmTemplate template) {
        FmTemplate original = findByPath(template.getPath());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create Freemarker template with duplicate path " + template.getPath());
        }

        return saveFmTemplate(template);
    }


    /**
     * Updates the Freemarker template data from the template parameter
     * @param template the Freemarker template to update
     * @return the updated Freemarker template
     */
    public FmTemplate updateFmTemplate(FmTemplate template) {
        FmTemplate original = findById(template.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing Freemarker template " + template.getPath());
        }

        // Copy the template data
        original.setPath(template.getPath());
        original.setTemplate(template.getTemplate());

        return saveFmTemplate(original);
    }


    /**
     * Deletes the Freemarker template with the given path
     * @param id the ID of the Freemarker template to delete
     */
    public boolean deleteFmTemplate(Integer id) {

        FmTemplate template = findById(id);
        if (template != null) {
            // Delete all template history entries
            getFmTemplateHistory(id).forEach(this::remove);
            // Delete the actual template
            remove(template);
            return true;
        }
        return false;
    }


    /**
     * Reload all class path-based Freemarker templates from the file system
     * @return the number of Freemarker templates reloaded
     */
    public int reloadFmTemplatesFromClassPath() {

        int updates = 0;
        for (FmTemplate template : findAll()) {
            FmTemplate cpTemplate = readFmTemplateFromClassPath(template.getPath());
            if (cpTemplate != null && !Objects.equals(template.getTemplate(), cpTemplate.getTemplate())) {
                log.info("Updating Freemarker template from classpath " + template.getPath());
                template.setTemplate(cpTemplate.getTemplate());
                saveFmTemplate(template);
                updates++;
            }
        }

        return updates;
    }


    /**
     * Reads, but does not persist, a Freemarker template with the given path from the class path.
     * Returns null if none are found
     * @return the classpath Freemarker template at the given path or null if not found
     */
    FmTemplate readFmTemplateFromClassPath(String path) {

        String resourcePath = path;
        if (!resourcePath.startsWith("/")) {
            resourcePath = "/" + resourcePath;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String template = null;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            template = IOUtils.toString(in ,"UTF-8");
        } catch (Exception ignored) {
        }

        if (StringUtils.isNotBlank(template)) {
            FmTemplate fmTemplate = new FmTemplate();
            fmTemplate.setPath(path);
            fmTemplate.setTemplate(template);
            return fmTemplate;
        }
        return null;
    }


    /************************************/
    /** Freemarker Template execution  **/
    /************************************/


    /**
     * If enabled, returns the PDF encryption password to use whilst generating PDF reports.
     * Otherwise, returns null.
     * @return a PDF encryption
     */
    private String getPDFEncryptionPassword() {
        if (pdfEncryptionEnabled != null && pdfEncryptionEnabled && StringUtils.isNotBlank(pdfEncryptionPassword)) {
            return pdfEncryptionPassword;
        }
        return null;
    }


    /**
     * Create a new Freemarker template builder.
     * The builder must be populated and processed within the current transaction.
     * @return a new Freemarker template builder
     */
    public FmTemplateBuilder newFmTemplateBuilder() {
        return new FmTemplateBuilder(this);
    }


    /**
     * Constructs a Freemarker Template based on the given template builder
     * @param templateBuilder the template builder to construct a template from
     * @return the Freemarker Template
     */
    private Template constructFmTemplate(FmTemplateBuilder templateBuilder) throws IOException {

        // Standard data properties
        templateBuilder.getData().put("baseUri", app.getBaseUri());
        templateBuilder.getData().put("country", app.getCountry());
        if (!templateBuilder.getData().containsKey("draft")) {
            templateBuilder.getData().put("draft", false);
        }
        // More...

        // Load the resource bundle with the given language and name, and save it in the "text" data property
        ResourceBundle bundle;
        if (templateBuilder.getDictionaryNames() != null && templateBuilder.getDictionaryNames().length > 0) {
            String  language = app.getLanguage(templateBuilder.getLanguage());
            templateBuilder.getData().put(LANGUAGE_PROPERTY, language);
            templateBuilder.getData().put(LANGUAGES_PROPERTY, app.getLanguages(language));

            bundle = dictionaryService.getDictionariesAsResourceBundle(templateBuilder.getDictionaryNames(), language);
            if (bundle != null) {
                ResourceBundleModel resourceBundleModel = new ResourceBundleModel(
                        bundle,
                        new DefaultObjectWrapperBuilder(Configuration.getVersion()).build());
                templateBuilder.getData().put(BUNDLE_PROPERTY, resourceBundleModel);
            }
        }

        Domain domain = domainService.currentDomain();
        String timeZone = (domain != null && StringUtils.isNotBlank(domain.getTimeZone()))
                ? domain.getTimeZone()
                : TimeZone.getDefault().getID();
        templateBuilder.getData().put(TIME_ZONE_PROPERTY, timeZone);

        Locale locale = app.getLocale(templateBuilder.getLanguage());

        // Create the Freemarker template loader and configuration
        FmTemplateLoader templateLoader = new FmTemplateLoader(this, true);
        Configuration cfg = new Configuration(Configuration.getVersion());
        cfg.setLocalizedLookup(true);
        cfg.setTemplateLoader(templateLoader);
        cfg.setObjectWrapper(new NiordAppObjectWrapper(cfg.getIncompatibleImprovements()));

        return cfg.getTemplate(templateBuilder.getTemplatePath(), locale, "UTF-8");
    }


    /** Returns the base URI used to access this application */
    private String getBaseUri() {
        return app.getBaseUri();
    }


    /***************************************/
    /** Freemarker Template History       **/
    /***************************************/


    /**
     * Saves a history entity containing a snapshot of the Freemarker template
     *
     * @param template the template to save a snapshot for
     */
    public void saveFmTemplateHistory(FmTemplate template) {

        try {
            FmTemplateHistory hist = new FmTemplateHistory();
            hist.setTemplate(template);
            hist.setUser(userService.currentUser());
            hist.setCreated(new Date());
            hist.setVersion(template.getVersion() + 1);

            // Create a snapshot of the template
            ObjectMapper jsonMapper = new ObjectMapper();
            jsonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false); // Use ISO-8601 format
            FmTemplateVo snapshot = template.toVo();
            hist.compressSnapshot(jsonMapper.writeValueAsString(snapshot));

            saveEntity(hist);

        } catch (Exception e) {
            log.error("Error saving a history entry for template " + template.getId(), e);
            // NB: Don't propagate errors
        }
    }

    /**
     * Returns the Freemarker template history for the given template ID
     *
     * @param templateId the template ID
     * @return the template history
     */
    public List<FmTemplateHistory> getFmTemplateHistory(Integer templateId) {
        return em.createNamedQuery("FmTemplateHistory.findByTemplateId", FmTemplateHistory.class)
                .setParameter("templateId", templateId)
                .getResultList();
    }


    /************************************/
    /** Freemarker Template Builder    **/
    /************************************/


    /**
     * Used by the client for building a new Freemarker Template.
     *
     * Initialize the builder by calling FmService.newFmTemplateBuilder()
     */
    @SuppressWarnings("unused")
    public static class FmTemplateBuilder {

        private static Logger log = LoggerFactory.getLogger(FmTemplateBuilder.class);

        String templatePath;
        Map<String, Object> data;
        String language;
        String[] dictionaryNames;
        FmTemplateService templateService;


        /**
         * Should only be initialized from the FmTemplateService.newFmTemplateBuilder() call
         */
        private FmTemplateBuilder(FmTemplateService templateService) {
            this.templateService = Objects.requireNonNull(templateService);
        }


        /**
         * Process the Freemarker template defined by the builder
         * @return the generated contents
         */
        public String process() throws IOException, TemplateException {

            // Check parameters
            if (StringUtils.isBlank(templatePath)) {
                throw new IllegalArgumentException("Template path must be specified");
            }
            if (data == null) {
                data = new HashMap<>();
            }

            // Process the Freemarker template
            try {
                Template fmTemplate = templateService.constructFmTemplate(this);

                StringWriter result = new StringWriter();
                fmTemplate.process(data, result);

                return result.toString();
            } catch (IOException e) {
                log.error("Failed loading freemarker template " + templatePath, e);
                throw e;
            } catch (TemplateException e) {
                log.error("Failed executing freemarker template " + templatePath, e);
                throw e;
            }
        }


        /**
         * Generates text or PDF based on the Freemarker HTML template and streams it to the output stream
         *
         * @param format the output format
         * @param out the output stream
         */
        public void process(ProcessFormat format, OutputStream out) throws Exception {

            long t0 = System.currentTimeMillis();
            try {
                // Process the template
                String result = process();

                if (format == ProcessFormat.TEXT) {
                    IOUtils.write(result, out, "UTF-8");

                    log.debug("Completed Freemarker text generation for " + getTemplatePath()
                            + " in " + (System.currentTimeMillis() - t0) + " ms");

                } else if (format == ProcessFormat.PDF) {

                    HtmlToPdfRenderer.newBuilder()
                            .baseUri(templateService.getBaseUri())
                            .html(result)
                            .encrypt(templateService.getPDFEncryptionPassword())
                            .pdf(out)
                            .build()
                            .render();

                    log.info("Completed Freemarker PDF generation for " + getTemplatePath()
                            + " in " + (System.currentTimeMillis() - t0) + " ms");
                }

            } catch (Exception e) {
                log.error("Error generating " + format + " from template " + getTemplatePath() , e);
                throw e;
            }
        }


        /*****************************************/
        /** Method-chaining Getters and Setters **/
        /*****************************************/

        public String getTemplatePath() {
            return templatePath;
        }

        public FmTemplateBuilder templatePath(String templatePath) {
            this.templatePath = templatePath;
            return this;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public FmTemplateBuilder data(Map<String, Object> data) {
            if (data == null) {
                data = new HashMap<>();
            }
            this.data.putAll(data);
            return this;
        }

        public FmTemplateBuilder data(String key, Object value) {
            if (data == null) {
                data = new HashMap<>();
            }
            data.put(key, value);
            return this;
        }

        public String getLanguage() {
            return language;
        }

        public FmTemplateBuilder language(String language) {
            this.language = language;
            return this;
        }

        public String[] getDictionaryNames() {
            return dictionaryNames;
        }

        public FmTemplateBuilder dictionaryNames(String... dictionaryNames) {
            this.dictionaryNames = dictionaryNames;
            return this;
        }
    }
}
