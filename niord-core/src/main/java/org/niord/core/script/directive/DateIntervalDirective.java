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
import freemarker.ext.beans.BeanModel;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang.StringUtils;
import org.niord.core.util.NavWarnDateFormatter;
import org.niord.core.util.NavWarnDateFormatter.Format;
import org.niord.model.message.DateIntervalVo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.niord.core.script.FmTemplateService.BUNDLE_PROPERTY;
import static org.niord.core.script.FmTemplateService.TIME_ZONE_PROPERTY;

/**
 * This Freemarker directive will format a date interval
 */
@SuppressWarnings("unused")
public class DateIntervalDirective implements TemplateDirectiveModel {

    private static final String PARAM_DATE_INTERVAL     = "dateInterval";
    private static final String PARAM_DATE_INTERVALS    = "dateIntervals";
    private static final String PARAM_FORMAT            = "format";
    private static final String PARAM_TIME_ZONE         = "tz";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params,
                        TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {

        // Fetch the resource bundle
        MultiResourceBundleModel text = (MultiResourceBundleModel)env.getDataModel().get(BUNDLE_PROPERTY);

        // Resolve the "dateInterval" parameter
        DateIntervalVo dateInterval = null;
        BeanModel dateIntervalParam = (BeanModel)params.get(PARAM_DATE_INTERVAL);
        if (dateIntervalParam != null && dateIntervalParam.getWrappedObject() != null &&
                dateIntervalParam.getWrappedObject() instanceof DateIntervalVo) {
            dateInterval = (DateIntervalVo)dateIntervalParam.getWrappedObject();
        }

        List<DateIntervalVo> dateIntervals  = null;
        BeanModel dateIntervalsParam = (BeanModel)params.get(PARAM_DATE_INTERVALS);
        if (dateIntervalsParam != null && dateIntervalsParam.getWrappedObject() != null) {
            dateIntervals = (List<DateIntervalVo>)dateIntervalParam.getWrappedObject();
        }

        // Get the "format" parameter
        Format format = Format.PLAIN;
        SimpleScalar formatModel = (SimpleScalar)params.get(PARAM_FORMAT);
        if (formatModel != null) {
            if ("navtex".equalsIgnoreCase(formatModel.getAsString())) {
                format = Format.NAVTEX;
            } else if ("html".equalsIgnoreCase(formatModel.getAsString())) {
                format = Format.HTML;
            }
        }

        // For the time zone, first check for "tz" parameter, next check for "timeZone" data, lastly pick default.
        SimpleScalar timeZoneParam = (SimpleScalar)params.get(PARAM_TIME_ZONE);
        boolean showTimeZone = timeZoneParam != null && StringUtils.isNotBlank(timeZoneParam.getAsString());
        SimpleScalar timeZoneModel = showTimeZone
                ? timeZoneParam
                : (SimpleScalar)env.getDataModel().get(TIME_ZONE_PROPERTY);
        String timeZoneId = (timeZoneModel != null)
                ? timeZoneModel.toString()
                : TimeZone.getDefault().getID();

        NavWarnDateFormatter formatter = NavWarnDateFormatter.newDateFormatter(
                text.getResourceBundle(),
                format,
                env.getLocale(),
                timeZoneId,
                showTimeZone);


        try {
            if (dateInterval != null) {
                String result = formatter.formatDateInterval(dateInterval);
                env.getOut().write(result);
            } else if (dateIntervals != null) {
                String result = formatter.formatDateIntervals(dateIntervals);
                env.getOut().write(result);
            }
        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }
}
