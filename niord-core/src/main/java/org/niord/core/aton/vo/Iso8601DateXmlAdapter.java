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
package org.niord.core.aton.vo;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * <code>Iso8601DateXmlAdapter</code> is an {@link XmlAdapter} implementation that
 * (un)marshals dates in the ISO-8601 format.
 */
public class Iso8601DateXmlAdapter extends XmlAdapter<String, Date> {

    public Iso8601DateXmlAdapter() {
    }

    /**
     * Important: SimpleDateFormat is NOT thread safe. Creating a single instance for the Iso8601DateXmlAdapter
     * will cause spurious errors.
     */
    private SimpleDateFormat newDateFormat() {
        SimpleDateFormat format  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format;
    }


    @Override
    public String marshal(Date d) throws Exception {
        if (d == null) {
            return null;
        }

        try {
            return newDateFormat().format(d);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Date unmarshal(String d) throws Exception {
        if (d == null) {
            return null;
        }

        try {
            return newDateFormat().parse(d);
        } catch (ParseException e) {
            return null;
        }
    }

}