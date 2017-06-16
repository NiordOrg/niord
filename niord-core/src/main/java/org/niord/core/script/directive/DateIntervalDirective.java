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
import freemarker.template.DefaultListAdapter;
import freemarker.template.SimpleDate;
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.apache.commons.lang.StringUtils;
import org.niord.core.util.NavWarnDateFormatter;
import org.niord.core.util.NavWarnDateFormatter.Format;
import org.niord.model.message.DateIntervalVo;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.niord.core.script.FmTemplateService.BUNDLE_PROPERTY;
import static org.niord.core.script.FmTemplateService.TIME_ZONE_PROPERTY;

/**
 * This Freemarker directive will format a date interval(s) according to Navigational Warning standards.
 * It can also format a single date.
 */
@SuppressWarnings("unused")
public class DateIntervalDirective implements TemplateDirectiveModel {

    private static final String PARAM_DATE_INTERVAL     = "dateInterval";
    private static final String PARAM_DATE_INTERVALS    = "dateIntervals";
    private static final String PARAM_DATE              = "date";
    private static final String PARAM_FORMAT            = "format";
    private static final String PARAM_TIME_ZONE         = "tz";
    private static final String PARAM_CAP_FIRST         = "capFirst";

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
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

        // Alternatively, resolve the "dateIntervals" parameter
        List<DateIntervalVo> dateIntervals  = null;
        DefaultListAdapter dateIntervalsParam = (DefaultListAdapter)params.get(PARAM_DATE_INTERVALS);
        if (dateIntervalsParam != null && dateIntervalsParam.getWrappedObject() != null) {
            dateIntervals = (List<DateIntervalVo>)dateIntervalsParam.getWrappedObject();
        }

        // Alternatively, resolve the "date" parameter
        Date date = null;
        Object dateParam = params.get(PARAM_DATE);
        if (dateParam != null) {
            if (dateParam instanceof SimpleDate) {
                date = ((SimpleDate)dateParam).getAsDate();
            } else if (dateParam instanceof SimpleNumber) {
                date = new Date(((SimpleNumber)dateParam).getAsNumber().longValue());
            }
        }

        // Make sure that either "dateInterval" or "dateIntervals" has been specified
        if (dateInterval == null && dateIntervals == null && date == null) {
            throw new TemplateModelException("The 'date', 'dateInterval' or 'dateIntervals' parameter must be specified");
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
        SimpleScalar envTimeZone = (SimpleScalar)env.getDataModel().get(TIME_ZONE_PROPERTY);
        boolean showTimeZone = timeZoneParam != null && StringUtils.isNotBlank(timeZoneParam.getAsString());
        SimpleScalar timeZoneModel = showTimeZone
                ? timeZoneParam
                : envTimeZone;
        String timeZoneId = (timeZoneModel != null)
                ? timeZoneModel.toString()
                : TimeZone.getDefault().getID();
        String allDayTimeZoneId = (envTimeZone != null)
                ? envTimeZone.toString()
                : TimeZone.getDefault().getID();

        NavWarnDateFormatter formatter = NavWarnDateFormatter.newDateFormatter(
                text.getResourceBundle(),
                format,
                env.getLocale(),
                timeZoneId,
                allDayTimeZoneId,
                showTimeZone);

        // Get the capFirst parameter, i.e. whether or not to capitalize the first character
        TemplateBooleanModel capFirstModel = (TemplateBooleanModel)params.get(PARAM_CAP_FIRST);
        boolean capFirst = capFirstModel != null && capFirstModel.getAsBoolean();

        try {
            String result;

            // Generate the single date, date interval or list of date intervals
            if (date != null) {
                result = formatter.formatDate(date);
            } else if (dateInterval != null) {
                result = formatter.formatDateInterval(dateInterval);
            } else {
                result = formatter.formatDateIntervals(dateIntervals);
            }

            // Check if we need to capitalize the result
            if (capFirst) {
                result = StringUtils.capitalize(result);
            }

            env.getOut().write(result);

        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }
}
