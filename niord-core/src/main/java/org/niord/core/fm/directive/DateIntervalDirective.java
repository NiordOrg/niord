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
package org.niord.core.fm.directive;

import freemarker.core.Environment;
import freemarker.ext.beans.BeanModel;
import freemarker.ext.beans.ResourceBundleModel;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.niord.model.message.DateIntervalVo;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;

import static org.niord.core.fm.FmTemplateService.BUNDLE_PROPERTY;
import static org.niord.core.fm.FmTemplateService.TIME_ZONE_PROPERTY;

/**
 * This Freemarker directive will format a date interval
 */
@SuppressWarnings("unused")
public class DateIntervalDirective implements TemplateDirectiveModel {

    private static final String PARAM_DATE_INTERVAL = "dateInterval";

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
        ResourceBundleModel text = (ResourceBundleModel)env.getDataModel().get(BUNDLE_PROPERTY);

        // Resolve the "dateInterval" parameter
        DateIntervalVo dateInterval = null;
        BeanModel dateIntervalParam = (BeanModel)params.get(PARAM_DATE_INTERVAL);
        if (dateIntervalParam != null && dateIntervalParam.getWrappedObject() != null &&
                dateIntervalParam.getWrappedObject() instanceof DateIntervalVo) {
            dateInterval = (DateIntervalVo)dateIntervalParam.getWrappedObject();
        }

        SimpleScalar timeZoneId = (SimpleScalar)env.getDataModel().get(TIME_ZONE_PROPERTY);
        TimeZone timeZone = (timeZoneId != null)
                ? TimeZone.getTimeZone(timeZoneId.toString())
                : TimeZone.getDefault();

        try {
            String result = formatDateInterval(text.getBundle(), env.getLocale(), timeZone, dateInterval);
            env.getOut().write(result);
        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }

    /**
     * Formats the date interval as text
     * <p>
     * Keep this function in sync with DateIntervalService.translateDateInterval() in message-service.js
     *
     * @param text the resource bundle
     * @param locale the current locale
     * @param dateInterval the date interval
     * @return the formatted date interval
     */
    private String formatDateInterval(ResourceBundle text, Locale locale, TimeZone timeZone, DateIntervalVo dateInterval) throws Exception {
        if (dateInterval == null || (dateInterval.getFromDate() == null && dateInterval.getToDate() == null)) {
            return text.getString("msg.time.until_further_notice");
        }

        // TODO: Optimize based on same month and year. E.g.:
        // "3 May 2016 - 4 Jun 2016" -> "3 May - 4 Jun 2016"
        // "3 May 2016 - 4 May 2016" -> "3 - 4 May 2016"

        Date from = dateInterval.getFromDate();
        Date to = dateInterval.getToDate();
        boolean allDay = dateInterval.getAllDay() != null && dateInterval.getAllDay();

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG, locale);
        dateFormat.setTimeZone(timeZone);
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        timeFormat.setTimeZone(timeZone);
        DateFormat timeZoneFormat = new SimpleDateFormat(" z");
        timeZoneFormat.setTimeZone(timeZone);

        StringBuilder result = new StringBuilder();

        if (from != null && to != null) {
            String fromDateTxt = dateFormat.format(from);
            String toDateTxt = dateFormat.format(to);

            if (fromDateTxt.equals(toDateTxt)) {
                result.append(fromDateTxt);
                if (!allDay) {
                    String fromTimeTxt = timeFormat.format(from);
                    String toTimeTxt = timeFormat.format(to);
                    result.append(" ").append(fromTimeTxt);
                    if (!fromTimeTxt.equals(toTimeTxt)) {
                        result.append(" - ").append(toTimeTxt);
                    }
                }

            } else {
                if (allDay) {
                    // Different dates
                    result.append(fromDateTxt).append(" - ").append(toDateTxt);
                } else {
                    // Different dates
                    String fromTimeTxt = timeFormat.format(from);
                    String toTimeTxt = timeFormat.format(to);
                    result.append(fromDateTxt).append(" ").append(fromTimeTxt).append(" - ")
                            .append(toDateTxt).append(" ").append(toTimeTxt);
                }
            }

        } else if (from != null) {
            String fromDateTxt = dateFormat.format(from);
            if (!allDay) {
                fromDateTxt += " " + timeFormat.format(from);
            }
            result.append(text.getString("msg.time.from_date").replace("{{fromDate}}", fromDateTxt));

        } else if (to != null) {
            String toDateTxt = dateFormat.format(to);
            if (!allDay) {
                toDateTxt += " " + timeFormat.format(to);
            }
            result.append(text.getString("msg.time.to_date").replace("{{toDate}}", toDateTxt));
        }

        // Add the time zone
        result.append(timeZoneFormat.format(new Date()));

        return result.toString();
    }
}
