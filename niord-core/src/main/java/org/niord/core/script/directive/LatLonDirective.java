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

    private static final Map<String, Format> formatterCache = new ConcurrentHashMap<>();

    static {
        // Pre-populate formatter cache
        formatterCache.put("dec-1", LATLON_DEC_1);
        formatterCache.put("dec-2", LATLON_DEC_2);
        formatterCache.put("dec-3", LATLON_DEC_3);
        formatterCache.put("navtex-1", LATLON_NAVTEX_1);
        formatterCache.put("navtex-2", LATLON_NAVTEX_2);
        formatterCache.put("navtex-3", LATLON_NAVTEX_3);
    }

    private static final String PARAM_LAT               = "lat";
    private static final String PARAM_LON               = "lon";
    private static final String PARAM_DECIMALS          = "decimals";
    private static final String PARAM_SEPARATOR         = "separator";
    private static final String PARAM_FORMAT            = "format";


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
        SimpleNumber decimalModel = (SimpleNumber)params.get(PARAM_DECIMALS);
        SimpleScalar separatorModel = (SimpleScalar)params.get(PARAM_SEPARATOR);
        SimpleScalar formatModel = (SimpleScalar) params.get(PARAM_FORMAT);


        if (latModel == null && lonModel == null) {
            throw new TemplateModelException("The 'lat' and/or 'lon' parameter must be specified");
        }

        try {
            String separator = separatorModel == null ? " - " : separatorModel.getAsString();
            Double lat = (latModel == null) ? null : latModel.getAsNumber().doubleValue();
            Double lon = (lonModel == null) ? null : lonModel.getAsNumber().doubleValue();


            // The code may or may not contain the decimals as a suffix, e.g. "dec-1".
            String code = "dec";
            if (formatModel != null) {
                code = formatModel.getAsString().toLowerCase();
            }

            // Check if we should extract the number of decimals from the code
            // Example: "dec-1" -> code="dec" and codeDecimals=1
            Integer codeDecimals = null;
            if (code.matches("\\w+-\\d+"))  {
                String[] codeParts = code.split("-");
                code = codeParts[0];
                codeDecimals = Integer.parseInt(codeParts[1]);
            }
            int decimals = getDecimals(code, codeDecimals, decimalModel);

            // Resolve the position format to use
            Format format = resolvePositionFormat(env, code, decimals);

            boolean htmlWrap = "dec".equals(code);

            // Override separator for "navtex" and "audio"
            if ("navtex".equals(code)) {
                separator = " ";
            } else if ("audio".equals(code)) {
                separator = " ";
            }

            // Emit latitude and/or longitude
            if (lat != null) {
                env.getOut().write(PositionUtils.formatLat(env.getLocale(), format, lat));
            }
            if (lon != null) {
                if (lat != null) {
                    env.getOut().write(separator);
                }
                env.getOut().write(PositionUtils.formatLon(env.getLocale(), format, lon));
            }
        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }


    /**
     * Look up or construct a position format the format code and number of decimal
     * @param env the current environment
     * @param code the format code, e.g. "dec", "audio" or "navtex"
     * @param decimals the number of decimals
     * @return the format
     */
    public synchronized Format resolvePositionFormat(Environment env, String code, int decimals) throws TemplateModelException {

        Format format = null;

        // Handle language independent "sec", "dec" and "navtex" first
        String cacheKey = code + "-" + decimals;

        if ("sec".equals(code)) {
            return LATLON_SEC;
        } else if ("dec".equals(code) || "navtex".equals(code)) {
            format = formatterCache.get(cacheKey);
        } else if ("audio".equals(code)) {
            Locale locale = env.getLocale();
            cacheKey += "_" + locale.getLanguage();

            format = formatterCache.get(cacheKey);
            if (format != null) {
                return format;
            }

            // Fetch the resource bundle
            MultiResourceBundleModel text = (MultiResourceBundleModel)env.getDataModel().get(BUNDLE_PROPERTY);
            format = PositionUtils.getAudioFormat(text.getResourceBundle(), decimals);
            formatterCache.put(cacheKey, format);
        }

        // Fall back to a high-precision format
        if (format == null) {
            format = LATLON_DEC_3;
        }

        return format;
    }


    /**
     * Returns the decimals based on the format code and decimal parameters
     * @param code the format code, e.g. "dec", "audio" or "navtex"
     * @param codeDecimals Optionally, the decimals that was specified as a suffix to the code, e.g. "dec-1"
     * @param decimalModel the decimals specified as parameters to the directive
     * @return the number of decimals to use
     */
    private int getDecimals(String code, Integer codeDecimals, SimpleNumber decimalModel) {
        // A "decimal" parameter trumps all
        if (decimalModel != null) {
            return decimalModel.getAsNumber().intValue();
        }

        // Next priority is if decimals was specified as part of the code, as in "dec-1"
        if (codeDecimals != null) {
            return codeDecimals;
        }

        // Lastly, depending on the code, pick a default decimal value
        if ("sec".equals(code)) {
            return 2;
        } else if ("dec".equals(code)) {
            return 3;
        }
        return 1;
    }

}
