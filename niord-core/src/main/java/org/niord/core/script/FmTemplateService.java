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

package org.niord.core.script;

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
import org.niord.core.script.directive.MultiResourceBundleModel;
import org.niord.core.script.pdf.HtmlToPdfRenderer;
import org.niord.core.service.BaseService;
import org.niord.core.settings.annotation.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static org.niord.core.settings.Setting.Type.Boolean;
import static org.niord.core.settings.Setting.Type.Password;


/**
 * Main interface for accessing and processing Freemarker report templates
 */
@RequestScoped
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
    ScriptResourceService resourceService;

    @Inject
    NiordApp app;

    @Inject
    Logger log;



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

        // Load the resource bundle(s) with the given language and name, and save it in the "text" data property
        // If a language is specified, only add the resource bundle for that language.
        // If no language is specified, add the resource bundles for all supported languages.
        if (templateBuilder.getDictionaryNames() != null && templateBuilder.getDictionaryNames().length > 0) {

            List<String> languages = new ArrayList<>();
            if (StringUtils.isNotBlank(templateBuilder.getLanguage())) {
                // Add resource bundle for selected language
                languages.add(app.getLanguage(templateBuilder.getLanguage()));

                String  language = app.getLanguage(templateBuilder.getLanguage());
                templateBuilder.getData().put(LANGUAGE_PROPERTY, language);
                templateBuilder.getData().put(LANGUAGES_PROPERTY, app.getLanguages(language));
            } else {
                // Add resource bundles for all supported languages
                languages.addAll(Arrays.asList(app.getLanguages()));

                templateBuilder.getData().put(LANGUAGES_PROPERTY, app.getLanguages());
            }

            List<ResourceBundle> bundles = languages.stream()
                    .map(lang -> dictionaryService.getDictionariesAsResourceBundle(templateBuilder.getDictionaryNames(), lang))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!bundles.isEmpty()) {
                MultiResourceBundleModel resourceBundleModel =  new MultiResourceBundleModel(
                        bundles,
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
        FmTemplateLoader templateLoader = new FmTemplateLoader(resourceService, true);
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

        public Map<String, Object> checkCreateData() {
            if (data == null) {
                data = new HashMap<>();
            }
            return data;
        }

        public FmTemplateBuilder data(Map<String, Object> data) {
            checkCreateData().putAll(data);
            return this;
        }

        public FmTemplateBuilder data(String key, Object value) {
            checkCreateData().put(key, value);
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
