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
import org.niord.core.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.TimeZone;

/**
 * Main interface for accessing and processing Freemarker templates
 */
@Stateless
@SuppressWarnings("unused")
public class FmService {

    public static final String BUNDLE_PROPERTY = "text";
    public static final String TIME_ZONE_PROPERTY = "timeZone";

    public enum ProcessFormat { TEXT, PDF }

    @Inject
    private Logger log;

    @Inject
    NiordApp app;

    @Inject
    FmConfiguration templateConfiguration;

    @Inject
    DictionaryService dictionaryService;


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
        // More...

        // Load the resource bundle with the given language and name, and save it in the "text" data property
        ResourceBundle bundle;
        if (templateBuilder.getDictionaryNames() != null && templateBuilder.getDictionaryNames().length > 0) {
            String  language = templateBuilder.getLanguage();
            if (StringUtils.isBlank(language)) {
                language = app.getDefaultLanguage();
            }
            bundle = dictionaryService.getDictionariesAsResourceBundle(templateBuilder.getDictionaryNames(), language);
            if (bundle != null) {
                ResourceBundleModel resourceBundleModel = new ResourceBundleModel(
                        bundle,
                        new DefaultObjectWrapperBuilder(Configuration.getVersion()).build());
                templateBuilder.getData().put(BUNDLE_PROPERTY, resourceBundleModel);
            }
        }

        Domain domain = templateBuilder.getDomain();
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
        Domain domain;
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

                    log.info("Completed Freemarker text generation for " + getTemplatePath()
                            + " in " + (System.currentTimeMillis() - t0) + " ms");

                } else if (format == ProcessFormat.PDF) {

                    // Clean up the resulting html
                    Document xhtmlContent = TextUtils.cleanHtml(result);

                    // Generate PDF from the HTML
                    ITextRenderer renderer = new ITextRenderer();
                    renderer.setDocument(xhtmlContent, fmService.getBaseUri());
                    renderer.layout();
                    renderer.createPDF(out);

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

        public FmTemplateBuilder setTemplatePath(String templatePath) {
            this.templatePath = templatePath;
            return this;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public FmTemplateBuilder setData(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public FmTemplateBuilder setData(String key, Object value) {
            if (data == null) {
                data = new HashMap<>();
            }
            data.put(key, value);
            return this;
        }

        public String getLanguage() {
            return language;
        }

        public FmTemplateBuilder setLanguage(String language) {
            this.language = language;
            return this;
        }

        public Domain getDomain() {
            return domain;
        }

        public FmTemplateBuilder setDomain(Domain domain) {
            this.domain = domain;
            return this;
        }

        public String[] getDictionaryNames() {
            return dictionaryNames;
        }

        public FmTemplateBuilder setDictionaryNames(String... dictionaryNames) {
            this.dictionaryNames = dictionaryNames;
            return this;
        }
    }
}
