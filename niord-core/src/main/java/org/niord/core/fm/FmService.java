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
package org.niord.core.fm;

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
import org.niord.core.fm.vo.FmReportVo;
import org.niord.core.service.BaseService;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.niord.core.settings.Setting.Type.Boolean;
import static org.niord.core.settings.Setting.Type.Password;

/**
 * Main interface for accessing and processing Freemarker templates
 */
@Stateless
@SuppressWarnings("unused")
public class FmService extends BaseService {

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
    Logger log;

    @Inject
    NiordApp app;

    @Inject
    FmConfiguration templateConfiguration;

    @Inject
    DictionaryService dictionaryService;

    @Inject
    DomainService domainService;


    /***************************************/
    /** Report methods                    **/
    /***************************************/


    /** Upon start-up, check that the standard report is defined **/
    @PostConstruct
    private void init() {
        getStandardReport();
        getDraftReport();
    }


    /**
     * Returns the standard report used for generating PDFs from message lists
     * @return the standard report used for generating PDFs from message lists
     */
    public FmReport getStandardReport() {
        // Check that the standard report is defined
        FmReport report = findByReportId("standard");
        if (report == null) {
            report = new FmReport();
            report.setReportId("standard");
            report.setName("Standard");
            report.setTemplatePath("/templates/messages/message-list-pdf.ftl");
            try {
                em.persist(report);
                log.info("Created standard report");
            } catch (Exception e) {
                log.error("Failed creating standard report", e);
            }
        }
        return report;
    }

    /**
     * Returns the draft report used for generating PDFs from message lists
     * @return the draft report used for generating PDFs from message lists
     */
    public FmReport getDraftReport() {
        // Check that the standard report is defined
        FmReport report = findByReportId("draft");
        if (report == null) {
            report = new FmReport();
            report.setReportId("draft");
            report.setName("Draft");
            report.setTemplatePath("/templates/messages/message-list-pdf.ftl");
            report.getProperties().put("draft", true);
            try {
                em.persist(report);
                log.info("Created draft report");
            } catch (Exception e) {
                log.error("Failed draft standard report", e);
            }
        }
        return report;
    }

    /**
     * Returns the report with the given report ID, or null if not found
     * @param reportId the report ID
     * @return the report with the given report ID, or null if not found
     */
    public FmReport findByReportId(String reportId) {
        try {
            return em.createNamedQuery("FmReport.findByReportId", FmReport.class)
                    .setParameter("reportId", reportId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the report with the given report ID, or the standard report if not found
     * @param reportId the report ID
     * @return the report with the given report ID, or the standard report if not found
     */
    public FmReport getReport(String reportId) {
        FmReport report = (StringUtils.isNotBlank(reportId)) ? findByReportId(reportId) : null;
        if (report == null) {
            report = getStandardReport();
        }
        return report;
    }


    /**
     * Returns the reports available in the current domain
     * @return the reports available in the current domain
     */
    public List<FmReport> getReports() {
        List<FmReport> reports = new ArrayList<>();

        // First get the public reports
        reports.addAll(em.createNamedQuery("FmReport.findPublicReports", FmReport.class)
                .getResultList());

        // If a current domain is defined, add the domain specific reports
        Domain domain = domainService.currentDomain();
        if (domain != null) {
            reports.addAll(em.createNamedQuery("FmReport.findReportsByDomain", FmReport.class)
                    .setParameter("domain", domain)
                    .getResultList());

        }

        return reports;
    }


    /**
     * Expand report parameter values with the format ${week}, ${year}, etc.
     * @param report the report to expand parameter values for
     * @return the updated report
     */
    public FmReportVo expandReportParams(FmReportVo report) {
        if (report.getProperties() != null && !report.getProperties().isEmpty()) {
            Date today = new Date();
            int year = TimeUtils.getCalendarField(today, Calendar.YEAR);
            int week = TimeUtils.getCalendarField(today, Calendar.WEEK_OF_YEAR);

            Map<String, String> replacementValues = new HashMap<>();
            replacementValues.put("${year-2-digits}", String.valueOf(year).substring(2));
            replacementValues.put("${year}", String.valueOf(year));
            replacementValues.put("${week}", String.format("%02d", week));
            replacementValues.put("${week-2-digits}", String.format("%02d", week));

            report.setParams(report.getParams().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> expandReportParamValue(e.getValue(), replacementValues)
                    )));
        }
        return report;
    }


    /** Expands the given parameter value if it has the format ${week}, ${year}, etc. **/
    private Object expandReportParamValue(Object value, Map<String, String> replacementValues) {
        if (value != null && value instanceof String) {
            String str = (String)value;
            for (Map.Entry<String, String> e : replacementValues.entrySet()) {
                str = str.replace(e.getKey(), e.getValue()).trim();
            }
            return str;
        }
        return value;
    }


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


    /***************************************/
    /** Template methods                  **/
    /***************************************/

    /**
     * Create a new Freemarker template builder.
     * The builder must be populated and processed within the current transaction.
     * @return a new Freemarker template builder
     */
    public FmTemplateBuilder newTemplateBuilder() {
        return new FmTemplateBuilder(this);
    }


    /**
     * Constructs a Freemarker Template based on the given template builder
     * @param templateBuilder the template builder to construct a template from
     * @return the Freemarker Template
     */
    private Template constructFreemarkerTemplate(FmTemplateBuilder templateBuilder) throws IOException {

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
        Configuration conf = templateConfiguration.getConfiguration();
        return conf.getTemplate(templateBuilder.getTemplatePath(), locale, "UTF-8");
    }


    /** Returns the base URI used to access this application */
    private String getBaseUri() {
        return app.getBaseUri();
    }


    /**
     * Used by the client for building a new Freemarker Template.
     *
     * Initialize the builder by calling FmService.newTemplateBuilder()
     */
    @SuppressWarnings("unused")
    public static class FmTemplateBuilder {

        private static Logger log = LoggerFactory.getLogger(FmTemplateBuilder.class);

        String templatePath;
        Map<String, Object> data;
        String language;
        String[] dictionaryNames;
        FmService fmService;


        /**
         * Should only be initialized from the FmService.newTemplateBuilder() call
         */
        private FmTemplateBuilder(FmService fmService) {
            this.fmService = Objects.requireNonNull(fmService);
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
                Template fmTemplate = fmService.constructFreemarkerTemplate(this);

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
                            .baseUri(fmService.getBaseUri())
                            .html(result)
                            .encrypt(fmService.getPDFEncryptionPassword())
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
