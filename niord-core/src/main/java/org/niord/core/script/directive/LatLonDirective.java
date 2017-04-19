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
package org.niord.core.script.directive;

import freemarker.core.Environment;
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.niord.core.util.PositionUtils;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.niord.core.script.FmTemplateService.BUNDLE_PROPERTY;
import static org.niord.core.util.PositionFormatter.*;

/**
 * This Freemarker directive will format latitude and/or longitude
 */
@SuppressWarnings("unused")
public class LatLonDirective implements TemplateDirectiveModel {

    private static final Map<Locale, Format> formatterCache = new ConcurrentHashMap<>();

    private static final String PARAM_LAT               = "lat";
    private static final String PARAM_LON               = "lon";
    private static final String PARAM_SEPARATOR         = "separator";
    private static final String PARAM_FORMAT            = "format";


    /**
     * Construct a format for audio positions
     * @param env the current environment
     * @return the format
     */
    public synchronized Format getAudioFormat(Environment env) throws TemplateModelException {

        Locale locale = env.getLocale();
        Format format = formatterCache.get(locale);
        if (format != null) {
            return format;
        }

        // Fetch the resource bundle
        MultiResourceBundleModel text = (MultiResourceBundleModel)env.getDataModel().get(BUNDLE_PROPERTY);
        format = PositionUtils.getAudioFormat(text.getResourceBundle());
        formatterCache.put(locale, format);
        return format;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params,
                        TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {

        SimpleNumber latModel = (SimpleNumber)params.get(PARAM_LAT);
        SimpleNumber lonModel = (SimpleNumber)params.get(PARAM_LON);
        SimpleScalar separatorModel = (SimpleScalar)params.get(PARAM_SEPARATOR);
        SimpleScalar wrapHtmlModel = (SimpleScalar)params.get(PARAM_SEPARATOR);


        if (latModel == null && lonModel == null) {
            throw new TemplateModelException("The 'lat' and/or 'lon' parameter must be specified");
        }

        try {
            String separator = separatorModel == null ? " - " : separatorModel.getAsString();
            Double lat = (latModel == null) ? null : latModel.getAsNumber().doubleValue();
            Double lon = (lonModel == null) ? null : lonModel.getAsNumber().doubleValue();

            boolean htmlWrap = true;
            Format format = LATLON_DEC;
            SimpleScalar formatModel = (SimpleScalar) params.get(PARAM_FORMAT);
            if (formatModel != null) {
                String code = formatModel.getAsString().toLowerCase();
                if ("dec".equals(code) || "dec-3".equals(code)) {
                    format = LATLON_DEC;
                } else if ("dec-1".equals(code)) {
                    format = LATLON_DEC_1;
                } else if ("dec-2".equals(code)) {
                    format = LATLON_DEC_2;
                } else if ("sec".equals(code)) {
                    format = LATLON_SEC;
                } else if ("navtex".equals(code)) {
                    format = LATLON_NAVTEX;
                    // Override separator
                    separator = " ";
                    htmlWrap = false;
                } else if ("audio".equals(code)) {
                    format = getAudioFormat(env);
                    // Override separator
                    separator = " - ";
                    htmlWrap = false;
                }
            }

            if (lat != null) {
                env.getOut().write(PositionUtils.formatLat(env.getLocale(), format, lat, htmlWrap));
            }
            if (lon != null) {
                if (lat != null) {
                    env.getOut().write(separator);
                }
                env.getOut().write(PositionUtils.formatLon(env.getLocale(), format, lon, htmlWrap));
            }
        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }

}
