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
package org.niord.model.vo.aton;

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

    private final SimpleDateFormat format;

    public Iso8601DateXmlAdapter() {
        format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @Override
    public String marshal(Date d) throws Exception {
        if (d == null) {
            return null;
        }

        try {
            return format.format(d);
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
            return format.parse(d);
        } catch (ParseException e) {
            return null;
        }
    }

}